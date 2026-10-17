"use client";

import { Button } from "@/components/ui/button";
import { Alert, Avatar, Chip } from "@/components/ui/display";
import { answerRequest, type ReceivedRequest, type SentRequest } from "@/lib/api";
import { plural, ratingText } from "@/lib/format";
import { useAction } from "@/lib/use-action";
import { cx } from "@/components/ui/cx";
import type { ReactNode } from "react";

function SectionTitle({ id, children, className }: { id: string; children: ReactNode; className?: string }) {
  return <h2 id={id} className={cx("mx-1 mb-2 flex items-center gap-2.5 font-display text-h2 stretch-110", className)}>{children}</h2>;
}

// Requests you can answer: to you, or (once you're in a group) to anyone in it
export function Received({ requests, onChange }: { requests: ReceivedRequest[]; onChange: () => void }) {
  const { busy, error, run } = useAction();
  if (requests.length === 0) return null;

  const answer = (id: string, a: "accept" | "decline") => run(async () => { await answerRequest(id, a); onChange(); });
  return (
    <section aria-labelledby="received-title">
      <SectionTitle id="received-title" className="lg:text-[1.375rem]">
        Want to share with you
        <span className="inline-flex h-7 min-w-7 items-center justify-center rounded-full bg-accent px-2 font-sans text-[0.9375rem] font-bold text-on-accent">
          {requests.length}
        </span>
      </SectionTitle>
      <ul className="flex flex-col gap-2">
        {requests.map((r) => (
          <li key={r.id} className="flex flex-col gap-3.5 rounded-lg bg-surface-2 p-4">
            <div className="flex gap-3">
              <Avatar name={r.firstName} size={44} />
              <span className="flex min-w-0 flex-col gap-0.5">
                <span className="flex flex-wrap items-center gap-x-2 gap-y-1">
                  <b className="text-title">{r.firstName}</b>
                  <Chip variant="muted">{ratingText(r.rating)}</Chip>
                </span>
                <span className="text-[0.9375rem] text-fg-muted">
                  {r.destArea} · out {timing(r.minutesAfterYou)} · {plural(r.seats, "seat")}, {plural(r.bags, "bag")}
                </span>
              </span>
            </div>
            <div className="grid grid-cols-[1fr_1.4fr] gap-2">
              <Button variant="secondary" disabled={busy} onClick={() => answer(r.id, "decline")} aria-label={`Decline ${r.firstName}`}>
                Decline
              </Button>
              <Button size="tap" className="text-[1.0625rem]" disabled={busy} onClick={() => answer(r.id, "accept")} aria-label={`Accept ${r.firstName}`}>
                Accept
              </Button>
            </div>
          </li>
        ))}
      </ul>
      {error && <Alert variant="error" className="mt-3">{error}</Alert>}
    </section>
  );
}

export function Sent({ requests }: { requests: SentRequest[] }) {
  if (requests.length === 0) return null;
  return (
    <section aria-labelledby="sent-title">
      <h2 id="sent-title" className="mx-1 mb-1 font-display text-[1.25rem] font-bold text-fg-muted lg:text-[1.1875rem]">Your requests</h2>
      <ul>
        {requests.map((s) => (
          <li key={s.id} className="flex min-h-14 items-center justify-between gap-3 border-b border-line px-1 last:border-b-0 lg:min-h-13">
            <span className="text-[1.0625rem] font-semibold">
              {s.firstName}
              {s.status === "pending" && <span className="font-normal text-fg-muted"> · waiting for an answer</span>}
            </span>
            {s.status === "pending"
              ? <span aria-hidden className="size-2.5 shrink-0 animate-pulse rounded-full bg-accent" />
              : <Chip variant="declined">declined</Chip>}
          </li>
        ))}
      </ul>
    </section>
  );
}

// 10 → "10 min after you", -5 → "5 min before you", 0 → "at the same time as you"
export function timing(minutesAfterYou: number): string {
  if (minutesAfterYou === 0) return "at the same time as you";
  return `${Math.abs(minutesAfterYou)} min ${minutesAfterYou > 0 ? "after" : "before"} you`;
}
