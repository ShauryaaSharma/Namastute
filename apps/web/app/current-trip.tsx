"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import {
  answerRequest,
  askToShare,
  cancelTrip,
  getCurrentTrip,
  getMatches,
  getRequests,
  type Match,
  type ReceivedRequest,
  type Requests,
  type SentRequest,
  type Trip,
} from "@/lib/api";
import { plural } from "@/lib/format";
import { formatIst } from "@/lib/time";
import { useAction } from "@/lib/use-action";
import GroupCard from "./group-card";

// Your active trip, or a link to plan one. undefined = still loading, null = no active trip.
export default function CurrentTrip() {
  const [trip, setTrip] = useState<Trip | null | undefined>(undefined);
  const [requests, setRequests] = useState<Requests>({ sent: [], received: [] });
  const [version, setVersion] = useState(0); // bumped after every action, so the whole card reloads
  const reload = () => setVersion((v) => v + 1);
  const { busy, error, run } = useAction();

  useEffect(() => {
    Promise.all([getCurrentTrip(), getRequests()])
      .then(([t, r]) => {
        setTrip(t);
        setRequests(r);
      })
      .catch(() => setTrip(null));
  }, [version]);

  if (trip === undefined) return null;

  if (trip === null) {
    return (
      <Link href="/trip/new" className="mt-4 inline-block rounded bg-black px-4 py-2 text-white">
        Plan a trip
      </Link>
    );
  }

  return (
    <section className="mt-4 rounded border p-4" aria-label="Your trip">
      <h2 className="font-bold">Your trip</h2>
      <p className="mt-1">
        {trip.terminalName} → {trip.destArea}
      </p>
      <p>Outside at {formatIst(trip.exitAt)}</p>
      <p>
        {plural(trip.seats, "seat")}, {plural(trip.bags, "bag")}
        {trip.flightNo ? `, flight ${trip.flightNo}` : ""} · about {trip.soloKm} km
      </p>
      {trip.status === "grouped" ? (
        <GroupCard key={version} onChange={reload} />
      ) : (
        <Matches key={version} tripId={trip.id} asked={requests.sent.map((s) => s.toTripId)} onChange={reload} />
      )}
      <Received requests={requests.received} onChange={reload} />
      <Sent requests={requests.sent} />
      {trip.status === "searching" && (
        <button
          onClick={() => run(async () => { await cancelTrip(trip.id); setTrip(null); })}
          disabled={busy}
          className="mt-2 underline disabled:opacity-50"
        >
          {busy ? "Cancelling…" : "Cancel trip"}
        </button>
      )}
      <p role="alert" aria-live="polite" className="text-red-600">{error}</p>
    </section>
  );
}

// People you could share a cab with, best first.
// shortcut: loaded once when the page opens; reload for new matches. Push notifications come later (design 3.3 step 3).
function Matches({ tripId, asked, onChange }: { tripId: string; asked: string[]; onChange: () => void }) {
  const [matches, setMatches] = useState<Match[] | null>(null);
  const [failed, setFailed] = useState(false);
  const { busy, error, run } = useAction();

  useEffect(() => {
    getMatches(tripId)
      .then(setMatches)
      .catch(() => setFailed(true));
  }, [tripId]);

  if (failed) return <p className="mt-1 text-sm text-red-600">Couldn&apos;t load matches. Reload the page to try again.</p>;
  if (matches === null) return <p className="mt-1 text-sm">Looking for co-travellers…</p>;
  if (matches.length === 0) {
    return <p className="mt-1 text-sm">No one heading your way yet. Check back closer to your exit time.</p>;
  }

  return (
    <div className="mt-2">
      <h3 className="font-semibold">People heading your way</h3>
      <ul className="mt-1 list-disc pl-5">
        {matches.map((m) => (
          <li key={m.tripId}>
            {m.firstName} · out {timing(m.minutesAfterYou)} · {m.destArea} ·{" "}
            {m.detourKm === 0 ? "no detour" : `${m.detourKm} km detour`}
            {asked.includes(m.tripId) ? (
              <span className="ml-2 text-sm">· asked</span>
            ) : (
              <button
                onClick={() => run(async () => { await askToShare(m.tripId); onChange(); })}
                disabled={busy}
                aria-label={`Ask ${m.firstName} to share a cab`}
                className="ml-2 underline disabled:opacity-50"
              >
                Ask to share
              </button>
            )}
          </li>
        ))}
      </ul>
      <p role="alert" aria-live="polite" className="text-red-600">{error}</p>
    </div>
  );
}

// Requests you can answer: to you, or (once you're in a group) to anyone in it
function Received({ requests, onChange }: { requests: ReceivedRequest[]; onChange: () => void }) {
  const { busy, error, run } = useAction();
  if (requests.length === 0) return null;

  const answer = (id: string, a: "accept" | "decline") => run(async () => { await answerRequest(id, a); onChange(); });
  return (
    <div className="mt-3">
      <h3 className="font-semibold">Want to share with you</h3>
      <ul className="mt-1 list-disc pl-5">
        {requests.map((r) => (
          <li key={r.id}>
            {r.firstName} · out {timing(r.minutesAfterYou)} · {r.destArea} · {plural(r.seats, "seat")}, {plural(r.bags, "bag")}
            <button onClick={() => answer(r.id, "accept")} disabled={busy} aria-label={`Accept ${r.firstName}`}
                    className="ml-2 underline disabled:opacity-50">
              Accept
            </button>
            <button onClick={() => answer(r.id, "decline")} disabled={busy} aria-label={`Decline ${r.firstName}`}
                    className="ml-2 underline disabled:opacity-50">
              Decline
            </button>
          </li>
        ))}
      </ul>
      <p role="alert" aria-live="polite" className="text-red-600">{error}</p>
    </div>
  );
}

function Sent({ requests }: { requests: SentRequest[] }) {
  if (requests.length === 0) return null;
  return (
    <div className="mt-3">
      <h3 className="font-semibold">Your requests</h3>
      <ul className="mt-1 list-disc pl-5">
        {requests.map((s) => (
          <li key={s.id}>
            {s.firstName} · {s.status === "pending" ? "waiting for an answer" : "declined"}
          </li>
        ))}
      </ul>
    </div>
  );
}

// 10 → "10 min after you", -5 → "5 min before you", 0 → "at the same time as you"
function timing(minutesAfterYou: number): string {
  if (minutesAfterYou === 0) return "at the same time as you";
  return `${Math.abs(minutesAfterYou)} min ${minutesAfterYou > 0 ? "after" : "before"} you`;
}
