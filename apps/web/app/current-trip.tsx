"use client";

import Link from "next/link";
import { type ReactNode, useEffect, useState } from "react";
import { Button, buttonClass } from "@/components/ui/button";
import { cx } from "@/components/ui/cx";
import { Alert, Avatar, Card, Chip, EmptyState, Eyebrow, Skeleton } from "@/components/ui/display";
import { BottomBar, Sheet } from "@/components/ui/layout";
import {
  answerRequest,
  askToShare,
  blockTraveller,
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
import { plural, ratingText } from "@/lib/format";
import { formatIstTime, istDate, istDayName } from "@/lib/time";
import { useAction } from "@/lib/use-action";
import GroupCard from "./group-card";
import PushPrompt from "./push-prompt";

// Phones: one column (trip, matches, requests). Laptops: the trip card and requests on the left,
// matches (or your group) on the right; each pane scrolls on its own (Learning Notes 72).
const PAGE =
  "mx-auto flex w-full max-w-[30rem] flex-col gap-7 px-4 pb-8 " +
  "lg:grid lg:h-[calc(100dvh-4rem)] lg:max-w-[1360px] lg:grid-cols-[360px_minmax(0,1fr)] lg:grid-rows-[auto_minmax(0,1fr)] " +
  "lg:gap-x-8 lg:gap-y-6 lg:p-8 xl:grid-cols-[400px_minmax(0,1fr)] xl:gap-x-10 xl:px-10";
const SCROLL_PANE = "lg:min-h-0 lg:overflow-y-auto lg:p-1"; // p-1: room for focus rings, which a scroll box would clip

// Your active trip, or a way to plan one. undefined = still loading, null = no active trip.
export default function CurrentTrip() {
  const [trip, setTrip] = useState<Trip | null | undefined>(undefined);
  const [requests, setRequests] = useState<Requests>({ sent: [], received: [] });
  const [version, setVersion] = useState(0); // bumped after every action, so the whole card reloads
  const reload = () => setVersion((v) => v + 1);

  useEffect(() => {
    Promise.all([getCurrentTrip(), getRequests()])
      .then(([t, r]) => {
        setTrip(t);
        setRequests(r);
      })
      .catch(() => setTrip((old) => (old === undefined ? null : old))); // a failed refresh keeps what's on screen
  }, [version]);

  // New matches and requests appear without reloading the page: every minute, and when you come back to the tab
  // (Learning Notes 64). Skipped while the tab is hidden, to save the phone's battery and data.
  useEffect(() => {
    const refresh = () => {
      if (document.visibilityState === "visible") setVersion((v) => v + 1);
    };
    const every = setInterval(refresh, 60_000);
    document.addEventListener("visibilitychange", refresh);
    return () => {
      clearInterval(every);
      document.removeEventListener("visibilitychange", refresh);
    };
  }, []);

  // "(1) Namastute": requests waiting for your answer show in the tab, even when you're in another one
  const waiting = requests.received.length;
  useEffect(() => {
    document.title = waiting ? `(${waiting}) Namastute` : "Namastute";
    return () => { document.title = "Namastute"; };
  }, [waiting]);

  if (trip === undefined) {
    return (
      <main className={PAGE} aria-busy>
        <Skeleton className="h-[7.5rem] rounded-lg!" />
        <MatchesLoading />
      </main>
    );
  }

  if (trip === null) {
    return (
      <main className="mx-auto flex min-h-[calc(100dvh-4.25rem)] w-full max-w-[30rem] flex-col px-4 lg:min-h-0 lg:pt-16">
        <Card className="flex flex-col gap-2">
          <Eyebrow>No trip yet</Eyebrow>
          <h1 className="font-display text-h1 stretch-110">Where are you headed?</h1>
          <p className="text-fg-muted">
            Tell us your terminal, when you&apos;ll be outside and the area you&apos;re going to. We&apos;ll show you people walking
            out around then, heading your way.
          </p>
        </Card>
        <BottomBar className="-mx-4 lg:mx-0">
          <Link href="/trip/new" className={buttonClass()}>Plan your trip →</Link>
        </BottomBar>
      </main>
    );
  }

  return (
    <main className={PAGE}>
      <div className="flex flex-col gap-3 lg:col-start-1 lg:row-start-1">
        <TripCard trip={trip} onCancelled={() => setTrip(null)} />
        <PushPrompt />
      </div>
      <div className={cx("lg:col-start-2 lg:row-span-2 lg:row-start-1", SCROLL_PANE)}>
        {trip.status === "grouped" ? (
          <GroupCard version={version} onChange={reload} />
        ) : (
          <Matches
            version={version}
            tripId={trip.id}
            asked={requests.sent.map((s) => s.toTripId)}
            askedYou={requests.received.map((r) => r.fromTripId)}
            onChange={reload}
          />
        )}
      </div>
      {(requests.received.length > 0 || requests.sent.length > 0) && (
        <div className={cx("flex flex-col gap-7 lg:col-start-1 lg:row-start-2 lg:gap-6", SCROLL_PANE)}>
          <Received requests={requests.received} onChange={reload} />
          <Sent requests={requests.sent} />
        </div>
      )}
    </main>
  );
}

// The orange card (ink in light mode): where and when, and Cancel behind a confirm sheet
function TripCard({ trip, onCancelled }: { trip: Trip; onCancelled: () => void }) {
  const [confirming, setConfirming] = useState(false);
  const { busy, error, run } = useAction();
  return (
    <section aria-labelledby="trip-title" className="flex flex-col gap-1.5 rounded-lg bg-fg px-[1.125rem] pt-[1.125rem] pb-3 text-bg dark:bg-accent dark:text-on-accent lg:px-[1.375rem] lg:pt-[1.375rem]">
      <p className="font-display text-eyebrow uppercase text-accent dark:text-on-accent">Your trip · {istDayName(istDate(trip.exitAt))}</p>
      <h1 id="trip-title" className="font-display text-[1.625rem] font-extrabold leading-[1.1] stretch-108 lg:text-[1.875rem] lg:leading-[1.05]">
        {trip.terminalName} → {trip.destArea}
      </h1>
      <p className="tnum font-semibold leading-[1.45]">
        Outside at {formatIstTime(trip.exitAt)} · {plural(trip.seats, "seat")}, {plural(trip.bags, "bag")}
        {trip.flightNo ? ` · ${trip.flightNo}` : ""} · about {trip.soloKm} km
      </p>
      {trip.status === "searching" && (
        <button type="button" onClick={() => setConfirming(true)} className="min-h-11 self-start font-bold underline underline-offset-4">
          Cancel trip
        </button>
      )}
      <Sheet open={confirming} onClose={() => setConfirming(false)} title="Cancel your trip?">
        <p className="mt-2 text-fg-muted">Your matches and requests for this trip go away. You can plan a new trip any time.</p>
        {error && <Alert variant="error" className="mt-4">{error}</Alert>}
        <div className="mt-6 grid gap-2.5">
          <Button variant="danger" size="cta" loading={busy} loadingLabel="Cancelling…"
                  onClick={() => run(async () => { await cancelTrip(trip.id); onCancelled(); })}>
            Cancel trip
          </Button>
          <Button variant="secondary" onClick={() => setConfirming(false)}>Keep my trip</Button>
        </div>
      </Sheet>
    </section>
  );
}

function SectionTitle({ id, children, className }: { id: string; children: ReactNode; className?: string }) {
  return <h2 id={id} className={cx("mx-1 mb-2 flex items-center gap-2.5 font-display text-h2 stretch-110", className)}>{children}</h2>;
}

function MatchesLoading() {
  return (
    <div className="flex flex-col gap-3" aria-busy>
      <p role="status" className="px-1 pt-2 font-semibold text-fg-muted">Looking for people heading your way…</p>
      {["w-2/5", "w-1/2"].map((w) => (
        <div key={w} className="flex items-center gap-3 rounded-lg bg-surface p-4">
          <Skeleton round className="size-11 shrink-0" />
          <span className="flex flex-1 flex-col gap-2">
            <Skeleton className={cx("h-3.5", w)} />
            <Skeleton className="h-3 w-4/5" />
          </span>
        </div>
      ))}
    </div>
  );
}

const CAR_SEATS = 4; // a sedan, same as the API's MatchRanker.CAR_SEATS
const ROW = "rounded-lg bg-surface p-4 lg:p-[1.125rem]";
const ROW_BORDER = "border border-line dark:border-transparent";

// Travellers and open groups you could share a cab with, best first.
// Reloaded whenever the card refreshes (version); the old list stays on screen meanwhile.
function Matches(props: { version: number; tripId: string; asked: string[]; askedYou: string[]; onChange: () => void }) {
  const { version, tripId, asked, askedYou, onChange } = props;
  const [matches, setMatches] = useState<Match[] | null>(null);
  const [failed, setFailed] = useState(false);
  const [asking, setAsking] = useState<string[]>([]); // asked on this screen, before the server has confirmed
  const [blocking, setBlocking] = useState<Match | null>(null);
  const { busy, error, run } = useAction();

  useEffect(() => {
    getMatches(tripId)
      .then((m) => {
        setMatches(m);
        setFailed(false);
      })
      .catch(() => setFailed(true));
  }, [tripId, version]);

  // Optimistic: "Asked ✓" at once; undone if the server says no (useAction then shows its message)
  function ask(m: Match) {
    setAsking((a) => [...a, m.tripId]);
    run(async () => {
      try {
        await askToShare(m.tripId);
        onChange();
      } catch (e) {
        setAsking((a) => a.filter((id) => id !== m.tripId));
        throw e;
      }
    });
  }

  const title = (
    <div className="mx-1 mb-2 flex items-baseline justify-between gap-4">
      <h2 id="matches-title" className="font-display text-h2 stretch-110 lg:text-display lg:stretch-112 lg:tracking-[-0.02em]">
        People heading your way
      </h2>
      {matches && matches.length > 0 && (
        <span className="hidden shrink-0 font-semibold text-fg-muted lg:inline">{matches.length} {matches.length === 1 ? "match" : "matches"}</span>
      )}
    </div>
  );

  let body: ReactNode;
  if (failed && matches === null) body = <Alert variant="error">Couldn&apos;t load matches. Reload the page to try again.</Alert>;
  else if (matches === null) body = <MatchesLoading />;
  else if (matches.length === 0) {
    body = (
      <EmptyState title="No one heading your way yet.">
        Check back closer to your exit time. We&apos;ll keep looking while this tab is open.
      </EmptyState>
    );
  } else {
    body = (
      <ul className="grid items-start gap-2 lg:grid-cols-2 lg:gap-4">
        {matches.map((m) => {
          const ids = m.riders.map((r) => r.tripId);
          const state = ids.some((id) => asked.includes(id)) || asking.includes(m.tripId) ? "asked"
            : ids.some((id) => askedYou.includes(id)) ? "asked-you" : "none";
          return state === "none"
            ? <MatchCard key={m.tripId} match={m} busy={busy} onAsk={() => ask(m)} onBlock={() => setBlocking(m)} />
            : <CompactRow key={m.tripId} match={m} askedYou={state === "asked-you"} />;
        })}
      </ul>
    );
  }

  const blockName = blocking?.riders[0].firstName ?? "";
  return (
    <section aria-labelledby="matches-title">
      {title}
      {body}
      {error && !blocking && <Alert variant="error" className="mt-3">{error}</Alert>}
      <Sheet open={blocking !== null} onClose={() => setBlocking(null)} title={`Block ${blockName}?`}>
        <p className="mt-2 text-fg-muted">You won&apos;t see each other in matches again, and {blockName} won&apos;t be told.</p>
        {error && <Alert variant="error" className="mt-4">{error}</Alert>}
        <div className="mt-6 grid gap-2.5">
          <Button variant="danger" size="cta" loading={busy} loadingLabel="Blocking…"
                  onClick={() => blocking && run(async () => { await blockTraveller(blocking.tripId); setBlocking(null); onChange(); })}>
            Block {blockName}
          </Button>
          <Button variant="secondary" onClick={() => setBlocking(null)}>Keep in matches</Button>
        </div>
      </Sheet>
    </section>
  );
}

function Riders({ match }: { match: Match }) {
  if (match.riders.length === 1) {
    const r = match.riders[0];
    return (
      <span className="flex min-w-0 flex-col gap-0.5">
        <span className="flex flex-wrap items-center gap-x-2 gap-y-1">
          <b className="text-title">{r.firstName}</b>
          <Chip variant="muted">{ratingText(r.rating)}</Chip>
        </span>
        <span className="text-[0.9375rem] text-fg-muted">{r.destArea}</span>
      </span>
    );
  }
  return (
    <span className="flex min-w-0 flex-col gap-0.5">
      <b className="text-[1.0625rem] leading-[1.3]">
        {match.riders.map((r, i) => (
          <span key={r.tripId}>
            {i > 0 && " + "}
            {r.firstName} <span className="font-medium text-fg-muted">({r.destArea})</span>
          </span>
        ))}
      </b>
      <span className="text-[0.9375rem] text-fg-muted">
        Group of {match.riders.length} · <b className="text-success">{plural(CAR_SEATS - match.seats, "seat")} free</b>
      </span>
    </span>
  );
}

function Avatars({ match }: { match: Match }) {
  if (match.riders.length === 1) return <Avatar name={match.riders[0].firstName} size={44} className="lg:size-12" />;
  return (
    <span className="flex shrink-0">
      {match.riders.map((r, i) => (
        <Avatar key={r.tripId} name={r.firstName} size={44} className={cx("border-[3px] border-surface lg:size-12", i > 0 && "-ml-6")} />
      ))}
    </span>
  );
}

function timingText(m: Match) {
  return m.riders.length > 1 ? `meeting ${timing(m.minutesAfterYou)}` : `out ${timing(m.minutesAfterYou)}`;
}
const detourText = (km: number) => (km === 0 ? "no detour" : `${km} km detour`);

function MatchCard({ match, busy, onAsk, onBlock }: { match: Match; busy: boolean; onAsk: () => void; onBlock: () => void }) {
  const names = match.riders.map((r) => r.firstName).join(" + ");
  return (
    <li className={cx("flex flex-col gap-3", ROW, ROW_BORDER)}>
      <div className="flex items-start gap-3">
        <Avatars match={match} />
        <Riders match={match} />
        {match.riders.length === 1 && (
          // Silent block (Learning Notes 56.2): kept small and away from "Ask to share"
          <button type="button" onClick={onBlock} aria-label={`Block ${names}`}
                  className="-mt-2 -mr-2 ml-auto min-h-tap shrink-0 px-2 text-small font-semibold text-fg-muted underline underline-offset-4">
            Block
          </button>
        )}
      </div>
      <div className="flex flex-wrap gap-1.5">
        <Chip>{timingText(match)}</Chip>
        <Chip>{detourText(match.detourKm)}</Chip>
      </div>
      <Button size="tap" className="text-[1.0625rem]" disabled={busy} onClick={onAsk} aria-label={`Ask ${names} to share a cab`}>
        Ask to share
      </Button>
    </li>
  );
}

// Already asked, or they asked you: no button, just where things stand
function CompactRow({ match, askedYou }: { match: Match; askedYou: boolean }) {
  const r = match.riders[0];
  const names = match.riders.map((x) => x.firstName).join(" + ");
  return (
    <li className={cx("flex items-center gap-3", ROW, askedYou ? "border-[1.5px] border-accent" : ROW_BORDER)}>
      <Avatars match={match} />
      <span className="flex min-w-0 flex-1 flex-col gap-0.5">
        <b className="text-[1.0625rem]">{names}</b>
        <span className="text-small text-fg-muted">
          {match.riders.length > 1 ? `Group of ${match.riders.length}` : r.destArea} · {timingText(match)} · {detourText(match.detourKm)}
        </span>
      </span>
      {askedYou ? (
        <Chip variant="asked-you" className="shrink-0 text-center leading-tight">
          <span>Asked you<span className="lg:hidden"><br />answer below ↓</span><span className="hidden lg:inline"> ←</span></span>
        </Chip>
      ) : (
        <Chip variant="muted" className="shrink-0">Asked ✓</Chip>
      )}
    </li>
  );
}

// Requests you can answer: to you, or (once you're in a group) to anyone in it
function Received({ requests, onChange }: { requests: ReceivedRequest[]; onChange: () => void }) {
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

function Sent({ requests }: { requests: SentRequest[] }) {
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
function timing(minutesAfterYou: number): string {
  if (minutesAfterYou === 0) return "at the same time as you";
  return `${Math.abs(minutesAfterYou)} min ${minutesAfterYou > 0 ? "after" : "before"} you`;
}
