"use client";

import type { Client } from "@stomp/stompjs";
import { type FormEvent, useEffect, useRef, useState } from "react";
import { type ChatMessage, connectGroup, getMessages, sendChat } from "@/lib/api";
import { formatIstTime } from "@/lib/time";

const MAX_LENGTH = 1000; // same limit as the API and the messages table

type Line = ChatMessage & { pending?: boolean }; // pending = sent by me, not yet confirmed by the server

// Adds messages, dropping duplicates (same id) and replacing my grey "pending" copy when the server's copy arrives
// with the same clientId (Learning Notes 46.3). Confirmed messages are kept in id order, pending ones at the end.
function merge(lines: Line[], incoming: ChatMessage[], mine: Set<string>): Line[] {
  let next = lines;
  for (const m of incoming) {
    const message = { ...m, you: m.you || (m.clientId !== null && mine.has(m.clientId)) };
    next = [...next.filter((l) => l.id !== message.id && !(l.pending && l.clientId === message.clientId)), message];
  }
  return [...next.filter((l) => !l.pending).sort((a, b) => a.id - b.id), ...next.filter((l) => l.pending)];
}

// Live chat for one group, plus "group changed" events that refresh the card (onUpdated)
export default function GroupChat({ groupId, onUpdated }: { groupId: string; onUpdated: () => void }) {
  const [lines, setLines] = useState<Line[]>([]);
  const [live, setLive] = useState(false);
  const [hasOlder, setHasOlder] = useState(false);
  const [text, setText] = useState("");
  const [error, setError] = useState("");
  const client = useRef<Client | null>(null);
  const mine = useRef(new Set<string>()); // clientIds this tab sent
  const newestId = useRef(0); // for catching up after a reconnect
  const latestOnUpdated = useRef(onUpdated);

  useEffect(() => {
    latestOnUpdated.current = onUpdated; // use the newest callback without reconnecting
  });

  useEffect(() => {
    const add = (incoming: ChatMessage[]) => {
      newestId.current = Math.max(newestId.current, ...incoming.map((m) => m.id));
      setLines((old) => merge(old, incoming, mine.current));
    };
    client.current = connectGroup(groupId, {
      message: (m) => add([m]),
      updated: () => latestOnUpdated.current(),
      // Subscribed first, then fetched: anything that arrives in between is de-duplicated by id
      connected: () => {
        setLive(true);
        const first = newestId.current === 0;
        getMessages(groupId, first ? {} : { after: newestId.current })
          .then((page) => {
            if (first) setHasOlder(page.length === 50); // a full page: there may be more
            add(page);
          })
          .catch(() => setError("Couldn't load messages. Reload the page to try again."));
      },
      disconnected: () => setLive(false),
    });
    return () => {
      client.current?.deactivate();
    };
  }, [groupId]);

  function showOlder() {
    const oldest = lines.find((l) => !l.pending)?.id;
    if (!oldest) return;
    getMessages(groupId, { before: oldest })
      .then((page) => {
        setHasOlder(page.length === 50);
        setLines((old) => merge(old, page, mine.current));
      })
      .catch(() => setError("Couldn't load older messages."));
  }

  function send(e: FormEvent) {
    e.preventDefault();
    const body = text.trim();
    if (!body) return;
    if (!client.current?.connected) {
      setError("You're offline. Reconnecting…");
      return;
    }
    setError("");
    const clientId = crypto.randomUUID();
    mine.current.add(clientId);
    // Optimistic UI: show it right away, greyed out, until the server's copy replaces it
    setLines((old) => [...old, { id: 0, kind: "text", firstName: null, body, createdAt: new Date().toISOString(), clientId, you: true, pending: true }]);
    sendChat(client.current, groupId, body, clientId);
    setText("");
  }

  return (
    <section className="mt-3 rounded border p-2" aria-label="Group chat">
      <h3 className="font-semibold">Group chat{live ? "" : " · reconnecting…"}</h3>
      {hasOlder && (
        <button onClick={showOlder} className="text-sm underline">
          Show older messages
        </button>
      )}
      <ol className="mt-1 max-h-64 overflow-y-auto" aria-live="polite">
        {lines.map((l) => (
          <li key={l.pending ? l.clientId : l.id} className={l.kind === "system" ? "text-sm italic" : l.pending ? "opacity-50" : ""}>
            {l.kind === "system" ? (
              l.body
            ) : (
              <>
                <strong>{l.you ? "You" : l.firstName}</strong>: {l.body}
              </>
            )}{" "}
            <span className="text-xs text-gray-500">{l.pending ? "sending…" : formatIstTime(l.createdAt)}</span>
          </li>
        ))}
      </ol>
      <form onSubmit={send} className="mt-2 flex gap-2">
        <label htmlFor="chat-text" className="sr-only">
          Message
        </label>
        <input
          id="chat-text"
          value={text}
          onChange={(e) => setText(e.target.value)}
          maxLength={MAX_LENGTH}
          placeholder="I'm at pillar 12…"
          className="flex-1 rounded border p-1"
        />
        <button type="submit" className="rounded bg-black px-3 py-1 text-white">
          Send
        </button>
      </form>
      <p role="alert" aria-live="polite" className="text-red-600">{error}</p>
    </section>
  );
}
