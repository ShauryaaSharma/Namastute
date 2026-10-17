"use client";

import type { Client } from "@stomp/stompjs";
import { type FormEvent, type ReactNode, useEffect, useLayoutEffect, useRef, useState } from "react";
import { cx } from "@/components/ui/cx";
import { Alert } from "@/components/ui/display";
import { type ChatMessage, connectGroup, getMessages, sendChat } from "@/lib/api";
import { formatIstTime } from "@/lib/time";

const MAX_LENGTH = 1000; // same limit as the API and the messages table
const NEAR_BOTTOM_PX = 80; // closer than this to the end counts as "reading the newest messages"

// pending = sent by me, not yet confirmed by the server; failed = still unconfirmed after SEND_TIMEOUT_MS
type Line = ChatMessage & { pending?: boolean; failed?: boolean };

// No copy back from the server in this time = refused (e.g. the group just closed) or lost (Learning Notes 64)
const SEND_TIMEOUT_MS = 10_000;

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

// Live chat for one group, plus "group changed" events that refresh the screen (onUpdated).
// onLive reports the connection to the top bar; footer (phones: the group's actions) goes under the composer.
export default function GroupChat({ groupId, onUpdated, onLive, placeholder, footer }: {
  groupId: string;
  onUpdated: () => void;
  onLive: (live: boolean | null) => void;
  placeholder: string;
  footer?: ReactNode;
}) {
  const [lines, setLines] = useState<Line[]>([]);
  const [hasOlder, setHasOlder] = useState(false);
  const [text, setText] = useState("");
  const [error, setError] = useState("");
  const client = useRef<Client | null>(null);
  const mine = useRef(new Set<string>()); // clientIds this tab sent
  const newestId = useRef(0); // for catching up after a reconnect
  const latest = useRef({ onUpdated, onLive });
  const list = useRef<HTMLDivElement>(null);
  const stick = useRef(true); // follow new messages only while the reader is at the bottom
  const fromBottom = useRef<number | null>(null); // set while older messages are added above

  useEffect(() => {
    latest.current = { onUpdated, onLive }; // use the newest callbacks without reconnecting
  });

  useEffect(() => {
    const add = (incoming: ChatMessage[]) => {
      newestId.current = Math.max(newestId.current, ...incoming.map((m) => m.id));
      setLines((old) => merge(old, incoming, mine.current));
    };
    client.current = connectGroup(groupId, {
      message: (m) => add([m]),
      updated: () => latest.current.onUpdated(),
      // Subscribed first, then fetched: anything that arrives in between is de-duplicated by id
      connected: () => {
        latest.current.onLive(true);
        const first = newestId.current === 0;
        getMessages(groupId, first ? {} : { after: newestId.current })
          .then((page) => {
            if (first) setHasOlder(page.length === 50); // a full page: there may be more
            add(page);
          })
          .catch(() => setError("Couldn't load messages. Reload the page to try again."));
      },
      disconnected: () => latest.current.onLive(false),
    });
    return () => {
      client.current?.deactivate();
      latest.current.onLive(null);
    };
  }, [groupId]);

  // After every change: keep your place when older messages were added above, else follow the newest if you were there
  useLayoutEffect(() => {
    const el = list.current;
    if (!el) return;
    if (fromBottom.current !== null) {
      el.scrollTop = el.scrollHeight - fromBottom.current;
      fromBottom.current = null;
    } else if (stick.current) {
      el.scrollTop = el.scrollHeight;
    }
  }, [lines]);

  function showOlder() {
    const oldest = lines.find((l) => !l.pending)?.id;
    if (!oldest) return;
    getMessages(groupId, { before: oldest })
      .then((page) => {
        const el = list.current;
        if (el) fromBottom.current = el.scrollHeight - el.scrollTop;
        setHasOlder(page.length === 50);
        setLines((old) => merge(old, page, mine.current));
      })
      .catch(() => setError("Couldn't load older messages."));
  }

  function send(e?: FormEvent) {
    e?.preventDefault();
    const body = text.trim();
    if (!body) return;
    if (!client.current?.connected) {
      setError("You're offline. Reconnecting…");
      return;
    }
    setError("");
    const clientId = crypto.randomUUID();
    mine.current.add(clientId);
    stick.current = true; // your own message always brings you to the bottom
    // Optimistic UI: show it right away, dashed, until the server's copy replaces it
    setLines((old) => [...old, { id: 0, kind: "text", firstName: null, body, createdAt: new Date().toISOString(), clientId, you: true, pending: true }]);
    sendChat(client.current, groupId, body, clientId);
    setTimeout(() => setLines((old) => old.map((l) => (l.pending && l.clientId === clientId ? { ...l, failed: true } : l))),
      SEND_TIMEOUT_MS);
    setText("");
  }

  return (
    // Phones: "contents" makes the section's children part of the page's column, so the composer bar sticks to the
    // bottom of the whole screen, not just of the chat (a sticky element only sticks within its parent)
    <section aria-labelledby="chat-title" className="contents lg:flex lg:min-h-0 lg:flex-1 lg:flex-col">
      <div className="flex min-h-11 items-center justify-between gap-3 lg:h-16 lg:shrink-0 lg:border-b lg:border-line lg:px-7">
        <h2 id="chat-title" className="font-display text-[1.0625rem] font-bold lg:text-title">Group chat</h2>
        {hasOlder && (
          <button type="button" onClick={showOlder} className="min-h-11 text-[0.9375rem] font-semibold text-accent-text underline underline-offset-4 dark:text-accent">
            Show older messages
          </button>
        )}
      </div>
      <div
        ref={list}
        onScroll={(e) => {
          const el = e.currentTarget;
          stick.current = el.scrollHeight - el.scrollTop - el.clientHeight < NEAR_BOTTOM_PX;
        }}
        className="flex max-h-[60dvh] min-h-48 flex-col overflow-y-auto py-2 lg:max-h-none lg:flex-1 lg:px-7 lg:py-5"
      >
        {/* mt-auto: few messages sit at the bottom, like any chat; justify-end would break scrolling up */}
        <ol role="log" aria-label="Messages" className="mt-auto flex flex-col gap-3">
          {lines.length === 0 && <li className="self-center text-small text-fg-muted">No messages yet. Say hi, and where you are.</li>}
          {lines.map((l) => <Bubble key={l.pending ? l.clientId : l.id} line={l} />)}
        </ol>
      </div>
      <div className="sticky bottom-0 z-10 -mx-4 flex flex-col gap-2.5 border-t border-line bg-bg px-4 pt-3 pb-[calc(1.5rem+env(safe-area-inset-bottom))] lg:static lg:mx-0 lg:px-7 lg:pt-4 lg:pb-5">
        {error && <Alert variant="error">{error}</Alert>}
        <form onSubmit={send} className="flex items-end gap-2">
          <label htmlFor="chat-text" className="sr-only">Message</label>
          <textarea
            id="chat-text"
            rows={1}
            value={text}
            onChange={(e) => setText(e.target.value)}
            onKeyDown={(e) => {
              // Enter sends, Shift+Enter is a new line. Never while an input method is still composing a word
              // (typing Hindi, Enter picks the word): sending then would send half a word.
              if (e.key === "Enter" && !e.shiftKey && !e.nativeEvent.isComposing) {
                e.preventDefault();
                send();
              }
            }}
            maxLength={MAX_LENGTH}
            placeholder={placeholder}
            className="field-sizing-content max-h-32 min-h-12 flex-1 resize-none rounded-3xl border-[1.5px] border-line bg-surface px-4 py-2.5 leading-[1.6] outline-none placeholder:text-fg-disabled focus:border-2 focus:border-fg focus-visible:shadow-none dark:bg-surface-2 dark:focus:border-accent lg:min-h-13 lg:py-3"
          />
          <button type="submit" aria-label="Send" className="flex size-12 shrink-0 items-center justify-center rounded-full bg-fg text-xl font-bold text-bg lg:size-13">
            ↑
          </button>
        </form>
        {footer}
      </div>
    </section>
  );
}

function Bubble({ line: l }: { line: Line }) {
  if (l.kind === "system") {
    return <li className="self-center text-center text-small italic text-fg-muted">{l.body}</li>;
  }
  const time = formatIstTime(l.createdAt);
  return (
    <li className={cx("flex max-w-[82%] flex-col gap-0.5 lg:max-w-[70%]", l.you && "items-end self-end")}>
      {!l.pending && <span className="text-[0.8125rem] font-semibold text-fg-muted">{l.you ? "You" : l.firstName} · {time}</span>}
      <p className={cx(
        "whitespace-pre-wrap break-words px-3.5 py-2.5 leading-[1.4]",
        l.you ? "rounded-[18px_4px_18px_18px]" : "rounded-[4px_18px_18px_18px] bg-surface-2",
        l.you && (l.pending ? "border-[1.5px] border-dashed border-line-strong text-fg-muted" : "bg-accent text-on-accent"),
      )}>
        {l.body}
      </p>
      {l.pending && (l.failed
        ? <span className="text-[0.8125rem] font-semibold text-danger">not sent</span>
        : <span className="text-[0.8125rem] font-medium text-fg-muted">sending…</span>)}
    </li>
  );
}
