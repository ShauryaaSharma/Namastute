"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { cancelTrip, getCurrentTrip, getMatches, type Match, type Trip } from "@/lib/api";
import { formatIst } from "@/lib/time";
import { useAction } from "@/lib/use-action";

// Your active trip, or a link to plan one. undefined = still loading, null = no active trip.
export default function CurrentTrip() {
  const [trip, setTrip] = useState<Trip | null | undefined>(undefined);
  const { busy, error, run } = useAction();

  useEffect(() => {
    getCurrentTrip()
      .then(setTrip)
      .catch(() => setTrip(null));
  }, []);

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
        {trip.seats} seat{trip.seats > 1 ? "s" : ""}, {trip.bags} bag{trip.bags === 1 ? "" : "s"}
        {trip.flightNo ? `, flight ${trip.flightNo}` : ""} · about {trip.soloKm} km
      </p>
      <Matches tripId={trip.id} />
      <button
        onClick={() => run(async () => { await cancelTrip(trip.id); setTrip(null); })}
        disabled={busy}
        className="mt-2 underline disabled:opacity-50"
      >
        {busy ? "Cancelling…" : "Cancel trip"}
      </button>
      <p role="alert" aria-live="polite" className="text-red-600">{error}</p>
    </section>
  );
}

// People you could share a cab with, best first.
// shortcut: loaded once when the page opens; reload for new matches. Push notifications come later (design 3.3 step 3).
function Matches({ tripId }: { tripId: string }) {
  const [matches, setMatches] = useState<Match[] | null>(null);
  const [failed, setFailed] = useState(false);

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
        {matches.map((m, i) => (
          // no ids on purpose (privacy), and the list never reorders while shown, so the index is a fine key
          <li key={i}>
            {m.firstName} · out {timing(m.minutesAfterYou)} · {m.destArea} ·{" "}
            {m.detourKm === 0 ? "no detour" : `${m.detourKm} km detour`}
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
