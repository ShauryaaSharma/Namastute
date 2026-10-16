"use client";

import { useEffect, useState } from "react";
import { getMyRatings, type Group, rateTraveller } from "@/lib/api";
import { useAction } from "@/lib/use-action";

// "How was the ride?" once the group has met (Learning Notes 62): a thumbs up or down for each other member, once.
// Nobody is ever told who gave what; others only see the total.
export default function RideRatings({ group }: { group: Group }) {
  const [given, setGiven] = useState<Record<string, boolean> | null>(null); // tripId → up
  const { busy, error, run } = useAction();

  useEffect(() => {
    getMyRatings(group.id)
      .then((list) => setGiven(Object.fromEntries(list.map((r) => [r.tripId, r.up]))))
      .catch(() => setGiven({}));
  }, [group.id]);

  if (given === null) return null;
  const others = group.members.filter((m) => !m.you);

  return (
    <section className="mt-3 rounded border p-2 text-sm" aria-label="Rate your ride">
      <h4 className="font-semibold">How was the ride?</h4>
      <p>Only the total is shown to others. Nobody sees who gave what.</p>
      <ul className="mt-1">
        {others.map((m) => {
          const up = given[m.tripId];
          return (
            <li key={m.tripId}>
              {m.firstName}:{" "}
              {up === undefined ? (
                <>
                  <button onClick={() => run(async () => { await rateTraveller(m.tripId, true); setGiven({ ...given, [m.tripId]: true }); })}
                          disabled={busy} aria-label={`Thumbs up for ${m.firstName}`} className="rounded border px-2 disabled:opacity-50">
                    👍
                  </button>{" "}
                  <button onClick={() => run(async () => { await rateTraveller(m.tripId, false); setGiven({ ...given, [m.tripId]: false }); })}
                          disabled={busy} aria-label={`Thumbs down for ${m.firstName}`} className="rounded border px-2 disabled:opacity-50">
                    👎
                  </button>
                </>
              ) : up ? (
                "you gave 👍"
              ) : (
                <>you gave 👎. If something serious happened, use Report next to {m.firstName}&apos;s name.</>
              )}
            </li>
          );
        })}
      </ul>
      <p role="alert" aria-live="polite" className="text-red-600">{error}</p>
    </section>
  );
}
