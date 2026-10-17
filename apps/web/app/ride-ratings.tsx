"use client";

import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Alert, Avatar, Chip } from "@/components/ui/display";
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
  const rate = (tripId: string, up: boolean) => run(async () => { await rateTraveller(tripId, up); setGiven({ ...given, [tripId]: up }); });

  return (
    <section aria-labelledby="rate-title" className="flex flex-col gap-2 rounded-lg border border-line bg-surface p-4 dark:border-transparent">
      <h2 id="rate-title" className="font-display text-title">How was the ride?</h2>
      <p className="text-[0.9375rem] text-fg-muted">Only the total is shown to others. Nobody sees who gave what.</p>
      <ul>
        {others.map((m) => {
          const up = given[m.tripId];
          return (
            <li key={m.tripId} className="flex min-h-15 flex-wrap items-center gap-3 border-b border-line py-2 last:border-b-0">
              <Avatar name={m.firstName} size={40} />
              <b className="flex-1">{m.firstName}</b>
              {up === undefined ? (
                <span className="flex gap-2">
                  <Button variant="tonal" className="w-14 text-xl" disabled={busy} onClick={() => rate(m.tripId, true)} aria-label={`Thumbs up for ${m.firstName}`}>👍</Button>
                  <Button variant="tonal" className="w-14 text-xl" disabled={busy} onClick={() => rate(m.tripId, false)} aria-label={`Thumbs down for ${m.firstName}`}>👎</Button>
                </span>
              ) : (
                <Chip variant={up ? "here" : "muted"}>You gave {up ? "👍" : "👎"}</Chip>
              )}
              {up === false && (
                <p className="basis-full text-small text-fg-muted">If something serious happened, report {m.firstName} from the ⋯ next to their name.</p>
              )}
            </li>
          );
        })}
      </ul>
      {error && <Alert variant="error">{error}</Alert>}
    </section>
  );
}
