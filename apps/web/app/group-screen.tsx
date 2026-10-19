"use client";

import Link from "next/link";
import { type ReactNode, useEffect, useMemo, useState } from "react";
import { Button } from "@/components/ui/button";
import { Segmented } from "@/components/ui/controls";
import { cx } from "@/components/ui/cx";
import { Alert, Avatar, Chip, StatusBadge } from "@/components/ui/display";
import { Sheet } from "@/components/ui/layout";
import {
  blockTraveller,
  getCurrentGroup,
  getTerminals,
  type Group,
  type GroupMember,
  imHere,
  leaveGroup,
  lockGroup,
  type Requests,
  type Terminal,
  type Trip,
} from "@/lib/api";
import { plural, ratingText, rupees } from "@/lib/format";
import { formatIstTime } from "@/lib/time";
import { useAction } from "@/lib/use-action";
import { useWide } from "@/lib/use-wide";
import FareSection, { UpiCard } from "./fare-section";
import GroupChat from "./group-chat";
import PickupLocation from "./pickup-location";
import ReportForm from "./report-form";
import { Received, Sent } from "./requests";
import RideRatings from "./ride-ratings";

const CAR_SEATS = 4; // a sedan, same as the API's MatchRanker.CAR_SEATS
const short = (terminalId: string) => terminalId.replace("DEL-", ""); // "DEL-T3" → "T3"

// Your cab group: open → locked → met (design 5, W6). Phones: one column with the chat's composer and the actions in
// a sticky area at the bottom. Laptops: group | chat | pickup panes (Learning Notes 74); from 1024 to 1279 px
// the pickup goes under the group. The chat and the map each exist once, so the layout is chosen in JavaScript.
export default function GroupScreen({ trip, requests, version, onChange, onLive }: {
  trip: Trip;
  requests: Requests;
  version: number;
  onChange: () => void;
  onLive: (live: boolean | null) => void;
}) {
  const wide = useWide();
  const threePanes = useWide(1280);
  const [group, setGroup] = useState<Group | null>(null);
  const [terminal, setTerminal] = useState<Terminal | null>(null);
  const [leaving, setLeaving] = useState(false);
  const [person, setPerson] = useState<GroupMember | null>(null); // whose "⋯" sheet is open
  const [tab, setTab] = useState<"fare" | "chat">("fare"); // laptops, once met (W7): the fare, or the chat
  const { busy, error, run } = useAction();

  useEffect(() => {
    getCurrentGroup().then(setGroup).catch(() => {}); // a failed refresh keeps what's on screen
  }, [version]);
  useEffect(() => {
    getTerminals().then((all) => setTerminal(all.find((t) => t.id === trip.terminalId) ?? null)).catch(() => {});
  }, [trip.terminalId]);

  const meet = useMemo(
    () => (terminal ? { lat: terminal.lat, lng: terminal.lng, label: `Meet here · ${short(terminal.id)}` } : null),
    [terminal],
  );

  if (!group) return <p role="status" className="p-6 text-fg-muted">Loading your group…</p>;
  const g = group;
  const me = g.members.find((m) => m.you);
  const booker = g.members.find((m) => m.booker);
  const locked = g.status === "locked";
  const met = g.status === "met";

  // ---- The pieces; placed differently on phones and laptops below ----
  const header = (
    <div className="flex items-center justify-between gap-3">
      <span className="flex min-w-0 flex-col">
        <span className="tnum text-small font-semibold text-fg-muted">
          {short(trip.terminalId)} → {trip.destArea} · meet {formatIstTime(g.meetAt)}
        </span>
        <h1 className="font-display text-[1.625rem] font-extrabold leading-[1.15] stretch-110 lg:text-[1.75rem]">Your cab group</h1>
      </span>
      <StatusBadge status={g.status} />
    </div>
  );

  const seats = g.members.reduce((n, m) => n + m.seats, 0);
  const bags = g.members.reduce((n, m) => n + m.bags, 0);
  const members = (
    <div className="flex flex-col gap-2">
      <ul aria-label="Members" className="rounded-lg border border-line bg-surface px-4 py-1 dark:border-transparent">
        {g.members.map((m) => (
          <li key={m.tripId} className="flex min-h-15 items-center gap-3 border-b border-line py-2 last:border-b-0">
            <Avatar name={m.firstName} you={m.you} size={40} />
            <span className="flex min-w-0 flex-1 flex-col">
              <span className="flex flex-wrap items-center gap-x-2 gap-y-0.5">
                <b>{m.you ? "You" : m.firstName}{m.booker && <span className="font-medium text-fg-muted"> (booker)</span>}</b>
                {!m.you && <Chip variant="muted">{ratingText(m.rating)}</Chip>}
              </span>
              <span className="text-small text-fg-muted">{m.destArea} · {plural(m.seats, "seat")} · {plural(m.bags, "bag")}</span>
            </span>
            {m.share !== null ? <b className="tnum shrink-0 font-display text-title">{rupees(m.share)}</b>
              : m.here && <Chip variant="here" className="shrink-0">here ✓</Chip>}
            {!m.you && (
              <button type="button" onClick={() => setPerson(m)} aria-label={`More for ${m.firstName}: report or block`}
                      className="-mr-2 flex size-tap shrink-0 items-center justify-center rounded-full text-xl font-bold text-fg-muted hover:bg-surface-2">
                ⋯
              </button>
            )}
          </li>
        ))}
      </ul>
      <p className="px-1 text-small text-fg-muted">{seats} of {CAR_SEATS} seats taken · {plural(bags, "bag")}</p>
    </div>
  );

  const status = locked ? (
    <p className="rounded-[16px] bg-surface-2 p-3.5 font-semibold leading-[1.4]">
      {g.youAreBooker ? "You're booking the cab. Tell the group in the chat once it's booked." : `${booker?.firstName} is booking the cab. Head to the pickup.`}
    </p>
  ) : null;

  const metPanel = met ? (
    <div className="flex flex-col gap-1.5 rounded-lg bg-success-soft px-[1.125rem] py-[1.375rem] lg:p-6">
      <p className="font-display text-[1.875rem] font-extrabold leading-[1.05] stretch-108 text-success lg:text-[2.5rem] lg:leading-none">
        You&apos;ve met up.<br className="lg:hidden" /> Have a good ride!
      </p>
      <p className="text-fg">
        {g.fareTotal !== null ? (g.youAreBooker ? "The fare is in: here's who owes you." : "The fare is in: here's your share.")
          : g.youAreBooker ? "Add the fare after the ride." : `${booker?.firstName} will add the fare after the ride.`}
      </p>
    </div>
  ) : null;

  // Once met, the fare section shows the UPI ID where it's needed: next to "Pay"
  const upi = met ? null : g.youAreBooker
    ? !g.bookerUpiId && (
      <p className="text-[0.9375rem] text-fg-muted">
        Add your UPI ID in <Link href="/profile" className="font-semibold text-accent-text underline underline-offset-4 dark:text-accent">your profile</Link> so
        your group can pay you back.
      </p>
    )
    : g.bookerUpiId
      ? <UpiCard name={booker?.firstName ?? "The booker"} upiId={g.bookerUpiId} />
      : <p className="text-[0.9375rem] text-fg-muted">{booker?.firstName} hasn&apos;t added a UPI ID yet.</p>;

  const joinRequests = (requests.received.length > 0 || requests.sent.length > 0) && (
    <div className="flex flex-col gap-6">
      <Received requests={requests.received} onChange={onChange} />
      <Sent requests={requests.sent} />
    </div>
  );

  // After meeting up: the fare (design 6 / W7) and ratings. The pickup goes: you've found each other.
  const fare = met && <FareSection group={g} onChange={onChange} />;
  const ratings = met && <RideRatings group={g} />;

  const pickup = !met && meet && (
    <PickupLocation group={g} meet={meet} note={terminal?.pickupNote ?? `${short(trip.terminalId)} arrivals, cab pickup zone. Agree on a pillar in the chat.`} />
  );

  const canBeHere = (locked || met) && !me?.here;
  const lockable = g.youAreBooker && g.status === "open";
  const actions = (lockable || canBeHere || !met || error) && (
    <div className="flex flex-col gap-2.5">
      {error && <Alert variant="error">{error}</Alert>}
      {lockable && (
        <Button loading={busy} onClick={() => run(async () => { await lockGroup(g.id); onChange(); })}>
          Lock group: I&apos;m booking the cab
        </Button>
      )}
      {(canBeHere || !met) && (
        <div className={cx("gap-2", canBeHere && !met ? "grid grid-cols-[1.6fr_1fr] lg:flex lg:flex-col" : "flex flex-col")}>
          {canBeHere && (
            <Button variant="secondary" disabled={busy} onClick={() => run(async () => { await imHere(g.id); onChange(); })}>
              I&apos;m here at the pickup
            </Button>
          )}
          {!met && <Button variant="danger-text" onClick={() => setLeaving(true)}>Leave group</Button>}
        </div>
      )}
    </div>
  );

  const chat = (
    <GroupChat
      key={g.id}
      groupId={g.id}
      onUpdated={onChange}
      onLive={onLive}
      placeholder={met ? "Message the group…" : "I'm at pillar 12…"}
      footer={wide ? undefined : actions || undefined}
    />
  );

  const sheets = (
    <>
      <LeaveSheet open={leaving} group={g} bookerName={booker?.firstName ?? "The booker"} onClose={() => setLeaving(false)} onLeft={onChange} />
      <PersonSheet person={person} onClose={() => setPerson(null)} />
    </>
  );

  if (!wide) {
    return (
      <main className="mx-auto flex w-full max-w-[30rem] flex-col gap-4 px-4">
        {header}
        {metPanel}
        {fare}
        {status}
        {members}
        {upi}
        {joinRequests}
        {ratings}
        {pickup}
        {chat}
        {sheets}
      </main>
    );
  }

  const pane = "flex min-h-0 flex-col gap-5 overflow-y-auto p-7";
  const leftPane = (extra: ReactNode) => (
    <div className="flex min-h-0 flex-col border-r border-line">
      <div className={cx(pane, "flex-1")}>
        {header}
        {status}
        {upi}
        {members}
        {joinRequests}
        {extra}
      </div>
      {actions && <div className="border-t border-line px-7 pt-4 pb-5">{actions}</div>}
    </div>
  );

  // Met (W7): the group on the left; the fare fills the rest, with the chat one tap away. The chat is only hidden,
  // never unmounted: its connection brings the live updates (like "the fare is in") to this screen.
  if (met) {
    return (
      <main className="grid h-[calc(100dvh-4rem)] grid-cols-[360px_minmax(0,1fr)] xl:grid-cols-[380px_minmax(0,1fr)]">
        {leftPane(ratings)}
        <div className="flex min-h-0 flex-col">
          <div className="flex h-16 shrink-0 items-center border-b border-line px-7">
            <Segmented label="Show" value={tab} onChange={setTab} className="w-80"
                       options={[{ value: "fare", label: "Fare" }, { value: "chat", label: "Group chat" }]} />
          </div>
          {tab === "fare" && (
            <div className="flex min-h-0 flex-1 flex-col gap-8 overflow-y-auto p-7 xl:p-10">
              {metPanel}
              <div className="w-full max-w-[60rem]">{fare}</div>
            </div>
          )}
          <div className={tab === "chat" ? "flex min-h-0 flex-1 flex-col" : "hidden"}>{chat}</div>
        </div>
        {sheets}
      </main>
    );
  }

  return (
    <main className={cx("grid h-[calc(100dvh-4rem)]", threePanes ? "grid-cols-[380px_minmax(0,1fr)_320px]" : "grid-cols-[360px_minmax(0,1fr)]")}>
      {leftPane(!threePanes && pickup)}
      <div className="flex min-h-0 flex-col">{chat}</div>
      {threePanes && (
        <div className={cx(pane, "border-l border-line px-6")}>
          {pickup}
          <Alert variant="info" className="mt-auto">Members only see first names and areas, never phone numbers.</Alert>
        </div>
      )}
      {sheets}
    </main>
  );
}

// Leaving: a simple confirm while open; once locked, a reason is required (the group sees it, Learning Notes 41)
function LeaveSheet({ open, group, bookerName, onClose, onLeft }: {
  open: boolean;
  group: Group;
  bookerName: string;
  onClose: () => void;
  onLeft: () => void;
}) {
  const [reason, setReason] = useState("");
  const { busy, error, run } = useAction();
  const locked = group.status === "locked";
  const leave = () => run(async () => { await leaveGroup(group.id, reason.trim() || undefined); onClose(); onLeft(); });

  return (
    <Sheet open={open} onClose={onClose} title={locked ? "Leave a locked group?" : "Leave this group?"}>
      <p className="mt-2 text-fg-muted">
        {!locked ? "You'll go back to looking for co-travellers. The group will see that you left."
          : group.youAreBooker ? "You're booking the cab. The group re-opens with a new booker, and sees your reason."
            : `${bookerName} may already be booking. Your group will see your reason.`}
      </p>
      {locked && (
        <div className="mt-4 flex flex-col gap-2">
          <label htmlFor="leave-reason" className="font-semibold">Why are you leaving? <span className="text-danger">(required)</span></label>
          <textarea id="leave-reason" value={reason} onChange={(e) => setReason(e.target.value)} maxLength={300} rows={3} required
                    className="min-h-24 w-full resize-y rounded-md border-[1.5px] border-line bg-surface px-3.5 py-3 leading-[1.4] outline-none focus:border-2 focus:border-fg focus-visible:shadow-none dark:bg-surface-2 dark:focus:border-accent" />
        </div>
      )}
      {error && <Alert variant="error" className="mt-4">{error}</Alert>}
      <div className="mt-6 grid gap-2.5 lg:grid-cols-2">
        <Button variant="danger" size="cta" loading={busy} loadingLabel="Leaving…" disabled={locked && !reason.trim()} onClick={leave}>
          Leave group
        </Button>
        <Button variant="secondary" className="lg:order-first lg:min-h-cta" onClick={onClose}>Stay</Button>
      </div>
    </Sheet>
  );
}

// "⋯" on a person: safety first (112), then report or block. Kept off the row so nobody feels accused.
function PersonSheet({ person, onClose }: { person: GroupMember | null; onClose: () => void }) {
  const [view, setView] = useState<"menu" | "report" | "block" | "reported" | "blocked">("menu");
  const { busy, error, run } = useAction();
  const name = person?.firstName ?? "";
  const close = () => { onClose(); setView("menu"); };

  let body: ReactNode;
  if (view === "report" && person) {
    body = <ReportForm tripId={person.tripId} firstName={name} onSent={() => setView("reported")} onCancel={() => setView("menu")} />;
  } else if (view === "block") {
    body = (
      <div className="flex flex-col gap-4">
        <p className="text-fg-muted">
          You won&apos;t see each other in matches again, and {name} won&apos;t be told. It doesn&apos;t remove anyone from this group:
          leave it if you feel unsafe.
        </p>
        {error && <Alert variant="error">{error}</Alert>}
        <div className="grid gap-2.5">
          <Button variant="danger" size="cta" loading={busy} loadingLabel="Blocking…"
                  onClick={() => person && run(async () => { await blockTraveller(person.tripId); setView("blocked"); })}>
            Block {name}
          </Button>
          <Button variant="secondary" onClick={() => setView("menu")}>Back</Button>
        </div>
      </div>
    );
  } else if (view === "reported" || view === "blocked") {
    body = (
      <div className="flex flex-col gap-4">
        <Alert variant="success">
          {view === "reported"
            ? `Thanks. We'll review your report. ${name} won't be told who reported them.`
            : `${name} is blocked. You can undo it from your profile.`}
        </Alert>
        <Button variant="secondary" onClick={close}>Close</Button>
      </div>
    );
  } else {
    body = (
      <div className="grid gap-2.5">
        <Button variant="secondary" onClick={() => setView("report")}>Report {name}</Button>
        <Button variant="secondary" onClick={() => setView("block")}>Block {name}</Button>
        <Button variant="link" onClick={close}>Close</Button>
      </div>
    );
  }

  return (
    <Sheet open={person !== null} onClose={close} title={view === "report" ? `Report ${name}` : view === "block" ? `Block ${name}?` : name}>
      <Alert variant="error" className="mt-4 mb-5">
        In danger right now? <a href="tel:112" className="font-bold underline underline-offset-4">Call 112</a>
      </Alert>
      {body}
    </Sheet>
  );
}

