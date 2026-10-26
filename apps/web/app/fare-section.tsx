"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { type FormEvent, useState } from "react";
import { encode } from "uqr";
import { Button, buttonClass } from "@/components/ui/button";
import { cx } from "@/components/ui/cx";
import { Alert, Chip, Eyebrow } from "@/components/ui/display";
import { Sheet } from "@/components/ui/layout";
import { enterFare, finishRide, type Group, previewFare, sayPaid, type ShareView } from "@/lib/api";
import { rupees } from "@/lib/format";
import { useAction } from "@/lib/use-action";
import { useMedia } from "@/lib/use-wide";

// The big amount. One size set per use: two text-* sizes on one element would fight (Learning Notes 71.3)
const HERO = "tnum font-display font-extrabold leading-none text-accent-text dark:text-accent";
const BIG = "text-hero lg:text-[8rem]";
const SMALLER = "text-display lg:text-[4rem]"; // when several people owe the booker

// After meeting up (Learning Notes 50, design 6 / W7): the booker enters the real fare (preview, then confirm, once);
// everyone else sees their share and pays the booker by UPI. Namastute never touches the money.
export default function FareSection({ group: g, onChange }: { group: Group; onChange: () => void }) {
  const booker = g.members.find((m) => m.booker)!;
  if (g.fareTotal === null) {
    return g.youAreBooker
      ? <EnterFare group={g} onChange={onChange} />
      : <p className="text-fg-muted">After the ride, {booker.firstName} enters the fare and your share appears here.</p>;
  }
  return (
    <div className="flex flex-col gap-8">
      {g.youAreBooker ? <FareConfirmed group={g} /> : <PayBooker group={g} onChange={onChange} />}
      <Finish groupId={g.id} />
    </div>
  );
}

// After the ride (Learning Notes 92, design 6b's "Plan another trip"): ends your own trip so you can plan the next one.
// The group stays as it is for the others until it closes by itself.
function Finish({ groupId }: { groupId: string }) {
  const router = useRouter();
  const { busy, error, run } = useAction();
  return (
    <div className="flex flex-col gap-2.5 border-t border-line pt-6">
      <p className="text-small text-fg-muted">Before you go, you can rate your group under &ldquo;How was the ride?&rdquo;.</p>
      <Button
        variant="secondary"
        className="self-start"
        loading={busy}
        loadingLabel="Finishing…"
        onClick={() => run(async () => { await finishRide(groupId); router.push("/trip/new"); })}
      >
        Done · plan another trip
      </Button>
      {error && <Alert variant="error">{error}</Alert>}
    </div>
  );
}

// 6a / W7a. Whole rupees, shown grouped as you type ("1,200"); Confirm appears only after a preview of that exact amount.
function EnterFare({ group: g, onChange }: { group: Group; onChange: () => void }) {
  const [digits, setDigits] = useState("");
  const [preview, setPreview] = useState<ShareView[] | null>(null);
  const { busy, error, setError, run } = useAction();
  const amount = Number(digits);

  function showPreview(e: FormEvent) {
    e.preventDefault();
    if (!(amount >= 50 && amount <= 10000)) return setError("Enter the fare you paid: ₹50 to ₹10,000."); // the API checks too
    run(async () => setPreview(await previewFare(g.id, amount)));
  }

  return (
    <form onSubmit={showPreview} className="flex flex-col gap-5">
      <Eyebrow>You booked, so you enter it</Eyebrow>
      <h2 className="-mt-3 font-display text-h1 stretch-110 lg:text-display">What did the cab cost?</h2>
      <div className="flex flex-col gap-2">
        <label htmlFor="fare" className="font-semibold">Fare you paid (₹)</label>
        <div className="flex h-18 items-center gap-2 rounded-md border-[1.5px] border-line bg-surface px-4 focus-within:border-2 focus-within:border-fg dark:bg-surface-2 dark:focus-within:border-accent lg:h-20">
          <span aria-hidden className="font-display text-[2.25rem] font-extrabold text-fg-muted lg:text-[2.75rem]">₹</span>
          <input
            id="fare"
            inputMode="numeric"
            autoComplete="off"
            value={digits && Number(digits).toLocaleString("en-IN")}
            onChange={(e) => {
              setDigits(e.target.value.replace(/\D/g, "").slice(0, 5));
              setPreview(null); // a changed amount needs a fresh preview
              setError("");
            }}
            placeholder="0"
            className="tnum min-w-0 flex-1 bg-transparent font-display text-[2.25rem] font-extrabold outline-none placeholder:text-fg-disabled focus-visible:shadow-none lg:text-[2.75rem]"
          />
        </div>
      </div>
      <Button type="submit" variant="secondary" loading={busy && !preview} loadingLabel="Working it out…">Preview shares</Button>
      {preview && (
        <>
          <div className="rounded-lg border border-line bg-surface px-4 py-1 dark:border-transparent">
            <ul>
              {preview.map((s, i) => (
                <li key={i} className="flex min-h-13 items-center justify-between gap-3 border-b border-line last:border-b-0">
                  <span className="font-semibold">{s.booker ? "You" : s.firstName}</span>
                  <b className="tnum font-display text-title">{rupees(s.share)}</b>
                </li>
              ))}
            </ul>
            <p className="pb-3 text-small text-fg-muted">Split by distance each person rode.</p>
          </div>
          <Alert variant="warning">The fare can be entered only once. Check the amount on your ride receipt.</Alert>
          <Button loading={busy} loadingLabel="Saving…" onClick={() => run(async () => { await enterFare(g.id, amount); onChange(); })}>
            Confirm fare {rupees(amount)}
          </Button>
        </>
      )}
      {error && <Alert variant="error">{error}</Alert>}
    </form>
  );
}

// 6b: who owes the booker what. One block per person.
function FareConfirmed({ group: g }: { group: Group }) {
  const me = g.members.find((m) => m.you)!;
  const owing = g.members.filter((m) => !m.booker);
  return (
    <div className="flex flex-col gap-5">
      <Eyebrow>Fare {rupees(g.fareTotal!)} · confirmed</Eyebrow>
      {owing.map((m) => (
        <div key={m.tripId} className="-mt-2 flex flex-col gap-1">
          <p className="font-display text-h3">{m.firstName} owes you</p>
          <p className={cx(HERO, owing.length > 1 ? SMALLER : BIG)}>{rupees(m.share!)}</p>
          {m.paid && <Chip variant="here" className="mt-2 self-start">✓ {m.firstName} says they&apos;ve paid</Chip>}
        </div>
      ))}
      <p className="text-fg-muted">
        {owing.length > 1 ? "They each see" : "They see"} your UPI ID and a Pay button. Your share was {rupees(me.share!)}.
      </p>
      {!g.bookerUpiId && (
        <p className="text-fg-muted">
          Add your UPI ID in <Link href="/profile" className="font-semibold text-accent-text underline underline-offset-4 dark:text-accent">your profile</Link> so
          they can pay you by UPI.
        </p>
      )}
      <Alert variant="info">Namastute never sees or confirms payments. Check your UPI app.</Alert>
    </div>
  );
}

// 6c / W7b: your share and how to pay it. Phones open the UPI app; laptops (no UPI app) show a QR code to scan.
function PayBooker({ group: g, onChange }: { group: Group; onChange: () => void }) {
  const laptop = useMedia("(hover: hover) and (pointer: fine)");
  const [qr, setQr] = useState(false);
  const booker = g.members.find((m) => m.booker)!;
  const me = g.members.find((m) => m.you)!;
  const share = me.share!;
  // UPI deep link (Learning Notes 50.4). The UPI ID is left as is: it only contains letters, digits, . _ - and @
  // (checked when saved), and some UPI apps don't decode %40. Name and note are encoded.
  const link = g.bookerUpiId
    ? `upi://pay?pa=${g.bookerUpiId}&pn=${encodeURIComponent(booker.firstName)}&am=${share.toFixed(2)}&cu=INR` +
      `&tn=${encodeURIComponent("Namastute cab")}`
    : null;
  const payLabel = (
    <>Pay {booker.firstName} {rupees(share)} by <span className="rounded-sm border-[1.5px] border-current px-1.5 text-small">UPI</span></>
  );

  return (
    <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_25rem] xl:items-start">
      <div className="flex flex-col gap-1">
        <Eyebrow>{booker.firstName} added the fare · {rupees(g.fareTotal!)}</Eyebrow>
        <p className="mt-2 font-display text-h3">Your share</p>
        <p className={cx(HERO, BIG)}>{rupees(share)}</p>
        <p className="font-display text-h3">to {booker.firstName}</p>
      </div>
      <div className="flex flex-col gap-4">
        {link ? (
          <div className="flex flex-col gap-4 xl:rounded-lg xl:border xl:border-line xl:bg-surface xl:p-6 xl:dark:border-transparent">
            <UpiCard name={booker.firstName} upiId={g.bookerUpiId!} inCard />
            <Alert variant="warning">Check the name your UPI app shows before paying. Namastute never sees or confirms payments.</Alert>
            {laptop ? (
              <>
                <Button size="pay" onClick={() => setQr(true)}>{payLabel}</Button>
                <p className="text-small text-fg-muted">On a laptop? The button shows a UPI QR code to scan with your phone.</p>
              </>
            ) : (
              <a href={link} className={buttonClass({ size: "pay" })}>{payLabel}</a>
            )}
          </div>
        ) : (
          <p className="text-fg-muted">{booker.firstName} hasn&apos;t added a UPI ID yet: pay them directly.</p>
        )}
        <SayPaid groupId={g.id} bookerName={booker.firstName} paid={me.paid} onChange={onChange} />
      </div>
      {link && (
        <Sheet open={qr} onClose={() => setQr(false)} title={`Scan to pay ${booker.firstName} ${rupees(share)}`}>
          <p className="mt-2 text-fg-muted">Open any UPI app on your phone and scan this code.</p>
          <QrCode text={link} label={`UPI QR code to pay ${booker.firstName} ${rupees(share)}`} />
          <Alert variant="warning">Check the name your UPI app shows before paying.</Alert>
          <Button variant="secondary" className="mt-4 w-full" onClick={() => setQr(false)}>Done</Button>
        </Sheet>
      )}
    </div>
  );
}

// "I've paid": your own word, shown to the booker and the group. Namastute can't see payments (Learning Notes 92).
function SayPaid({ groupId, bookerName, paid, onChange }: { groupId: string; bookerName: string; paid: boolean; onChange: () => void }) {
  const { busy, error, run } = useAction();
  if (paid) return <Chip variant="here" className="self-start">✓ You told {bookerName} you&apos;ve paid</Chip>;
  return (
    <div className="flex flex-col gap-2">
      <Button variant="secondary" className="self-start" loading={busy} loadingLabel="Saving…"
              onClick={() => run(async () => { await sayPaid(groupId); onChange(); })}>
        I&apos;ve paid
      </Button>
      <p className="text-small text-fg-muted">Lets {bookerName} know. It&apos;s your word: Namastute can&apos;t see payments.</p>
      {error && <Alert variant="error">{error}</Alert>}
    </div>
  );
}

// A QR code drawn by us from the module grid (uqr's encode), so no HTML string is ever inserted.
// Always black on white, in both themes: that's what phone cameras read best. border 4 = the quiet zone QR needs.
function QrCode({ text, label }: { text: string; label: string }) {
  const { data } = encode(text, { ecc: "M", border: 4 });
  const n = data.length;
  const d = data.flatMap((row, y) => row.map((on, x) => (on ? `M${x} ${y}h1v1h-1z` : ""))).join("");
  return (
    <svg viewBox={`0 0 ${n} ${n}`} role="img" aria-label={label} shapeRendering="crispEdges"
         className="mx-auto my-5 block aspect-square w-full max-w-72 rounded-md bg-white">
      <path d={d} fill="#000" />
    </svg>
  );
}

// The booker's UPI ID (members only), with Copy: no typing a long ID on a phone
export function UpiCard({ name, upiId, inCard }: { name: string; upiId: string; inCard?: boolean }) {
  const [copied, setCopied] = useState(false);
  function copy() {
    navigator.clipboard.writeText(upiId).then(() => {
      setCopied(true);
      setTimeout(() => setCopied(false), 2000);
    }).catch(() => {}); // blocked clipboard: the ID is still on screen to select
  }
  return (
    <div className={cx("flex items-center justify-between gap-3 rounded-[16px] border border-line px-4 py-3.5 dark:border-transparent",
      inCard ? "bg-surface xl:bg-surface-2" : "bg-surface")}>
      <span className="flex min-w-0 flex-col gap-0.5">
        <span className="text-[0.8125rem] font-semibold text-fg-muted">{name}&apos;s UPI · members only</span>
        <b className="break-all font-mono font-semibold">{upiId}</b>
      </span>
      <Button variant="tonal" onClick={copy} className="shrink-0">
        <span aria-live="polite">{copied ? "Copied ✓" : "Copy"}</span>
      </Button>
    </div>
  );
}
