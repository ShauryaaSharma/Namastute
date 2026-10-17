"use client";

import type { Client } from "@stomp/stompjs";
import dynamic from "next/dynamic";
import { useEffect, useRef, useState } from "react";
import { Button } from "@/components/ui/button";
import { Alert } from "@/components/ui/display";
import { connectLocation, type Group, type Position, sendLocation } from "@/lib/api";
import { formatIstTime } from "@/lib/time";
import type { MeetPoint } from "./pickup-map";

const PickupMap = dynamic(() => import("./pickup-map"), { ssr: false, loading: () => <div className="size-full animate-pulse bg-surface-2" /> });

const MOVED_M = 10; // send when you've moved this far...
const RESEND_MS = 15_000; // ...or this often anyway, so late arrivals see you
const STALE_MS = 120_000; // a dot nobody has updated for this long is hidden

type Dot = Position & { at: number };

// Haversine distance in metres (the API's Geo uses the same formula)
function metres(a: Position, b: Position): number {
  const rad = Math.PI / 180;
  const h = Math.sin(((b.lat - a.lat) * rad) / 2) ** 2 +
    Math.cos(a.lat * rad) * Math.cos(b.lat * rad) * Math.sin(((b.lng - a.lng) * rad) / 2) ** 2;
  return 2 * 6_371_000 * Math.asin(Math.sqrt(h));
}

// The pickup: where to meet (the map's "Meet here" pin and the terminal's note), and live location
// (Learning Notes 60): only in a locked or met group, only inside the window the API gives, only after your own tap.
// Positions go to your group and are never saved. meet must be a stable object (the map redraws when it changes).
export default function PickupLocation({ group, meet, note }: { group: Group; meet: MeetPoint; note: string }) {
  const [now, setNow] = useState(() => Date.now());
  const [others, setOthers] = useState<Record<string, Dot>>({}); // by tripId
  const [mine, setMine] = useState<Dot | null>(null);
  const [sharing, setSharing] = useState(false);
  const [error, setError] = useState("");
  const client = useRef<Client | null>(null);
  const watch = useRef<number | null>(null);
  const latest = useRef<Dot | null>(null);
  const lastSent = useRef<Dot | null>(null);

  const meId = group.members.find((m) => m.you)?.tripId;
  const from = Date.parse(group.sharingFrom);
  const until = Date.parse(group.sharingUntil);
  const active = group.status === "locked" || group.status === "met";
  const open = active && now >= from && now < until;

  // A clock: updates "20 s ago", hides stale dots, opens and closes the window on time
  useEffect(() => {
    const t = setInterval(() => setNow(Date.now()), 5000);
    return () => clearInterval(t);
  }, []);

  // Connected only while the window is open. Closing it (or leaving the page) also stops your sharing.
  useEffect(() => {
    if (!open) return;
    const c = connectLocation(group.id, (l) => {
      if (l.tripId === meId) return; // our own position comes back too
      setOthers((old) => {
        const rest = Object.fromEntries(Object.entries(old).filter(([id]) => id !== l.tripId));
        return l.sharing && l.lat !== null && l.lng !== null
          ? { ...rest, [l.tripId]: { lat: l.lat, lng: l.lng, accuracy: l.accuracy ?? 0, at: Date.now() } }
          : rest;
      });
    });
    client.current = c;
    return () => {
      if (watch.current !== null) {
        navigator.geolocation.clearWatch(watch.current);
        watch.current = null;
        if (c.connected) sendLocation(c, group.id, null);
      }
      c.deactivate();
      client.current = null;
    };
  }, [open, group.id, meId]);

  // The heartbeat: standing still produces no new positions, so repeat the last one every RESEND_MS
  useEffect(() => {
    const p = latest.current;
    if (sharing && p && (!lastSent.current || now - lastSent.current.at >= RESEND_MS)) send(p);
  }, [now, sharing]); // eslint-disable-line react-hooks/exhaustive-deps -- send only reads refs

  function send(p: Dot) {
    if (!client.current?.connected) return; // reconnecting: the next position or heartbeat goes instead
    sendLocation(client.current, group.id, p);
    lastSent.current = { ...p, at: Date.now() };
  }

  function start() {
    setError("");
    setSharing(true);
    watch.current = navigator.geolocation.watchPosition(
      (pos) => {
        const dot = { lat: pos.coords.latitude, lng: pos.coords.longitude, accuracy: Math.round(pos.coords.accuracy), at: Date.now() };
        latest.current = dot;
        setMine(dot);
        if (!lastSent.current || metres(lastSent.current, dot) >= MOVED_M || dot.at - lastSent.current.at >= RESEND_MS) send(dot);
      },
      (e) => {
        stop();
        setError(e.code === e.PERMISSION_DENIED
          ? "Location is blocked for Namastute. To share it, allow location in your browser's site settings."
          : "Couldn't find your location. Tell your group your pillar number in the chat instead.");
      },
      { enableHighAccuracy: true, maximumAge: 5000, timeout: 30_000 },
    );
  }

  function stop() {
    if (watch.current !== null) navigator.geolocation.clearWatch(watch.current);
    watch.current = null;
    latest.current = null;
    lastSent.current = null;
    setSharing(false);
    setMine(null);
    if (client.current?.connected) sendLocation(client.current, group.id, null);
  }

  const untilText = formatIstTime(group.sharingUntil);
  const visible = group.members
    .filter((m) => !m.you && others[m.tripId] && now - others[m.tripId].at < STALE_MS)
    .map((m) => ({ tripId: m.tripId, name: m.firstName, ...others[m.tripId] }));
  const dots = [...(mine ? [{ name: "You", ...mine, you: true }] : []), ...visible.map((d) => ({ ...d, you: false }))];

  return (
    <section aria-labelledby="pickup-title" className="flex flex-col gap-3">
      <h2 id="pickup-title" className="font-display text-title">Pickup</h2>
      <div className="h-[13.75rem] overflow-hidden rounded-[16px] border-[1.5px] border-line">
        <PickupMap meet={meet} dots={dots} />
      </div>
      <p className="text-[0.9375rem] leading-[1.45] text-fg-muted">{note}</p>

      {active && now < from && (
        <p className="text-[0.9375rem] text-fg-muted">
          From {formatIstTime(group.sharingFrom)} you can share your live location here, to find each other.
        </p>
      )}
      {open && (
        <div className="flex flex-col gap-3" aria-live="polite">
          {sharing ? (
            <Alert variant="info">
              Sharing your location with your group until {untilText}. Keep Namastute open.{" "}
              {mine ? `Accurate to about ${mine.accuracy} m.` : "Finding you…"}
            </Alert>
          ) : (
            <p className="text-[0.9375rem] text-fg-muted">Only your group sees it, only until {untilText}. Nothing is saved.</p>
          )}
          {sharing
            ? <Button variant="secondary" onClick={stop}>Stop sharing my location</Button>
            : <Button variant="secondary" onClick={start}>Share my live location</Button>}
          <ul className="flex flex-col">
            {visible.length === 0 && <li className="text-small text-fg-muted">No one else is sharing their location yet.</li>}
            {visible.map((d) => (
              <li key={d.tripId} className="flex min-h-11 items-center justify-between gap-3 border-b border-line last:border-b-0">
                <b>{d.name}</b>
                <span className="tnum text-small text-fg-muted">
                  {mine ? `about ${Math.round(metres(mine, d) / 10) * 10} m away (± ${Math.max(mine.accuracy, d.accuracy)} m) · ` : ""}
                  {Math.max(0, Math.round((now - d.at) / 1000))} s ago
                </span>
              </li>
            ))}
          </ul>
        </div>
      )}
      {error && <Alert variant="error">{error}</Alert>}
    </section>
  );
}
