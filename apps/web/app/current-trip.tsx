"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { cancelTrip, getCurrentTrip, type Trip } from "@/lib/api";
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
      <p className="mt-1 text-sm">Looking for co-travellers… (matching arrives in the next update)</p>
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
