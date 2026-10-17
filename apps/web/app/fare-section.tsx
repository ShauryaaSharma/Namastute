"use client";

import { type FormEvent, useState } from "react";
import { enterFare, type Group, previewFare, type ShareView } from "@/lib/api";
import { rupees } from "@/lib/format";
import { useAction } from "@/lib/use-action";

// After meeting up (Learning Notes 50): the booker enters the real fare (preview, then confirm, once);
// everyone else sees their share and pays the booker by UPI. Namastute never touches the money.
export default function FareSection({ group, onChange }: { group: Group; onChange: () => void }) {
  const [total, setTotal] = useState("");
  const [preview, setPreview] = useState<ShareView[] | null>(null);
  const [copied, setCopied] = useState(false);
  const { busy, error, setError, run } = useAction();

  const g = group;
  const me = g.members.find((m) => m.you)!;
  const booker = g.members.find((m) => m.booker)!;

  if (g.fareTotal === null) {
    if (!g.youAreBooker) {
      return <p className="mt-2 text-sm">After the ride, {booker.firstName} enters the fare and your share appears here.</p>;
    }
    const amount = Number(total);
    const showPreview = (e: FormEvent) => {
      e.preventDefault();
      if (!(amount >= 50 && amount <= 10000)) {
        setError("Enter the fare you paid: ₹50 to ₹10,000."); // the API checks too
        return;
      }
      run(async () => setPreview(await previewFare(g.id, amount)));
    };
    return (
      <form onSubmit={showPreview} className="mt-2">
        <label htmlFor="fare" className="block">
          Fare you paid (₹)
        </label>
        <input
          id="fare"
          inputMode="decimal"
          value={total}
          onChange={(e) => {
            setTotal(e.target.value);
            setPreview(null); // a changed amount needs a fresh preview
          }}
          className="rounded border p-1"
        />
        <button type="submit" disabled={busy} className="ml-2 underline disabled:opacity-50">
          Preview shares
        </button>
        {preview && (
          <div className="mt-2">
            <ul className="list-disc pl-5">
              {preview.map((s, i) => (
                <li key={i}>
                  {s.booker ? "You" : s.firstName}: {rupees(s.share)}
                </li>
              ))}
            </ul>
            <p className="text-sm">The fare can be entered only once.</p>
            <button
              type="button"
              onClick={() => run(async () => { await enterFare(g.id, amount); onChange(); })}
              disabled={busy}
              className="mt-1 rounded bg-accent px-3 py-1 text-on-accent disabled:opacity-50"
            >
              Confirm fare {rupees(amount)}
            </button>
          </div>
        )}
        <p role="alert" aria-live="polite" className="text-danger">{error}</p>
      </form>
    );
  }

  if (g.youAreBooker) {
    return (
      <div className="mt-2">
        <p>
          Fare {rupees(g.fareTotal)} entered. Your own share: {rupees(me.share!)}
        </p>
        <ul className="list-disc pl-5">
          {g.members.filter((m) => !m.booker).map((m, i) => (
            <li key={i}>
              {m.firstName} owes you {rupees(m.share!)}
            </li>
          ))}
        </ul>
      </div>
    );
  }

  const share = me.share!;
  // UPI deep link (Learning Notes 50.4). The UPI ID is left as is: it only contains letters, digits, . _ - and @
  // (checked when saved), and some UPI apps don't decode %40. Name and note are encoded.
  const link = g.bookerUpiId
    ? `upi://pay?pa=${g.bookerUpiId}&pn=${encodeURIComponent(booker.firstName)}&am=${share.toFixed(2)}&cu=INR` +
      `&tn=${encodeURIComponent("Namastute cab")}`
    : null;
  return (
    <div className="mt-2">
      <p>
        Fare {rupees(g.fareTotal)}. Your share: <strong>{rupees(share)}</strong>, to {booker.firstName}.
      </p>
      {link ? (
        <>
          <a href={link} className="mt-1 inline-block rounded bg-accent px-3 py-1 text-on-accent">
            Pay {booker.firstName} {rupees(share)} by UPI
          </a>
          <p className="mt-1 text-sm">
            UPI ID: {g.bookerUpiId}{" "}
            <button
              onClick={() =>
                navigator.clipboard.writeText(g.bookerUpiId!).then(
                  () => setCopied(true),
                  () => setError("Couldn't copy. Select the UPI ID and copy it yourself."),
                )
              }
              className="underline"
            >
              {copied ? "Copied" : "Copy"}
            </button>
          </p>
          <p className="text-xs">Check the name your UPI app shows before paying. Namastute never sees or confirms payments.</p>
        </>
      ) : (
        <p className="text-sm">{booker.firstName} hasn&apos;t added a UPI ID yet: pay them directly.</p>
      )}
      <p role="alert" aria-live="polite" className="text-danger">{error}</p>
    </div>
  );
}
