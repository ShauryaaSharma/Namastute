"use client";

import { useRouter } from "next/navigation";
import { type FormEvent, type ReactNode, useEffect, useState } from "react";
import { AppBar } from "@/components/app-bar";
import { Button } from "@/components/ui/button";
import { cx } from "@/components/ui/cx";
import { Alert, Card, EmptyState, Eyebrow } from "@/components/ui/display";
import { boxClass } from "@/components/ui/field";
import { approveCheck, getCheckPhoto, getPendingChecks, type PendingCheck, rejectCheck, restoreSession } from "@/lib/api";
import { formatIst } from "@/lib/time";
import { useAction } from "@/lib/use-action";

const DOC_NAMES: Record<string, string> = {
  passport: "Passport", driving_licence: "Driving licence", voter_id: "Voter ID", masked_aadhaar: "Masked Aadhaar",
};
const GENDERS = [{ value: "female", label: "Female" }, { value: "male", label: "Male" }, { value: "other", label: "Third gender" }] as const;
const REASONS = ["Photo blurry or unreadable", "Faces don't match", "Gesture not shown", "Aadhaar not masked", "ID not accepted or looks altered"];
const CHECKLIST = [
  "The same person in both photos",
  "The selfie shows the gesture asked for",
  "The ID is readable and looks genuine (not a screen, printout or edited photo)",
  "Aadhaar: only the last 4 digits are visible",
];
const FIELD = "outline-none focus-visible:shadow-none focus:border-2 focus:border-fg dark:focus:border-accent";

// The ID check queue (Learning Notes 80). Admins only: the server refuses everyone else; this page just sends them home.
export default function ReviewPage() {
  const router = useRouter();
  const [name, setName] = useState("");
  const [checks, setChecks] = useState<PendingCheck[] | null>(null);
  const [open, setOpen] = useState<string | null>(null);
  const [notice, setNotice] = useState("");

  useEffect(() => {
    restoreSession()
      .then(async (me) => {
        if (!me) return router.replace("/login");
        if (!me.admin || !me.name) return router.replace("/");
        setName(me.name);
        setChecks(await getPendingChecks());
      })
      .catch(() => router.replace("/login"));
  }, [router]);

  if (!checks) return <p role="status" className="p-6 text-fg-muted">Loading…</p>;
  const current = checks.find((c) => c.id === open);

  function decided(id: string, message: string) {
    setChecks((list) => list!.filter((c) => c.id !== id));
    setOpen(null);
    setNotice(message);
  }

  return (
    <>
      <AppBar name={name} />
      <main className="mx-auto flex w-full max-w-[30rem] flex-col gap-6 px-5 pt-3 pb-10 lg:max-w-[72rem] lg:pt-8">
        <div className="flex flex-col gap-2">
          <Eyebrow>Admin</Eyebrow>
          <h1 className="font-display text-h1 stretch-110">ID checks</h1>
        </div>
        {notice && <Alert variant="success">{notice}</Alert>}
        {current ? (
          <CheckReview key={current.id} check={current} onBack={() => setOpen(null)} onDecided={decided} />
        ) : checks.length === 0 ? (
          <EmptyState title="Nothing waiting.">New checks arrive with a notification to every admin.</EmptyState>
        ) : (
          <ul className="flex flex-col gap-2">
            {checks.map((c) => (
              <li key={c.id}>
                <Card className="flex flex-wrap items-center justify-between gap-3">
                  <span className="flex flex-col">
                    <b>{c.firstName} · {DOC_NAMES[c.docType] ?? c.docType}</b>
                    <span className="text-small text-fg-muted">Sent {formatIst(c.submittedAt)}</span>
                  </span>
                  <Button variant="secondary" onClick={() => { setNotice(""); setOpen(c.id); }}>Review</Button>
                </Card>
              </li>
            ))}
          </ul>
        )}
      </main>
    </>
  );
}

// One check: both photos, the checklist, and approve or reject. Photos live only as blob: URLs, freed when you leave.
function CheckReview({ check, onBack, onDecided }: {
  check: PendingCheck;
  onBack: () => void;
  onDecided: (id: string, message: string) => void;
}) {
  const [urls, setUrls] = useState<{ id: string; selfie: string } | null>(null);
  const [gender, setGender] = useState("");
  const [last4, setLast4] = useState("");
  const [reason, setReason] = useState("");
  const [other, setOther] = useState("");
  const approve = useAction();
  const reject = useAction();

  useEffect(() => {
    let made: { id: string; selfie: string } | null = null;
    Promise.all([getCheckPhoto(check.id, "id"), getCheckPhoto(check.id, "selfie")])
      .then(([id, selfie]) => {
        made = { id: URL.createObjectURL(id), selfie: URL.createObjectURL(selfie) };
        setUrls(made);
      })
      .catch(() => approve.setError("Couldn't load the photos. Go back and try again."));
    return () => {
      if (made) {
        URL.revokeObjectURL(made.id);
        URL.revokeObjectURL(made.selfie);
      }
    };
  }, [check.id]); // eslint-disable-line react-hooks/exhaustive-deps -- setError is stable enough; reload only per check

  function sendApprove(e: FormEvent) {
    e.preventDefault();
    if (!gender) return approve.setError("Choose the gender printed on the ID.");
    if (!/^[A-Za-z0-9]{4}$/.test(last4)) return approve.setError("Enter the last 4 letters or digits of the document number.");
    approve.run(async () => {
      await approveCheck(check.id, gender as "female" | "male" | "other", last4);
      onDecided(check.id, `${check.firstName} is verified. Their photos have been deleted.`);
    });
  }

  function sendReject(e: FormEvent) {
    e.preventDefault();
    const text = reason === "other" ? other.trim() : reason;
    if (text.length < 3) return reject.setError("Choose a reason, or write one.");
    reject.run(async () => {
      await rejectCheck(check.id, text);
      onDecided(check.id, `${check.firstName}'s check was not approved. They'll see the reason. Their photos have been deleted.`);
    });
  }

  return (
    <div className="flex flex-col gap-6">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <Button variant="link" className="px-0!" onClick={onBack}>‹ All checks</Button>
        <span className="text-fg-muted">{check.firstName} · {DOC_NAMES[check.docType] ?? check.docType} · sent {formatIst(check.submittedAt)}</span>
      </div>

      <div className="rounded-[14px] bg-accent-soft px-4 py-3.5 text-accent-text">
        <p className="text-small font-semibold">They were asked to:</p>
        <p className="font-display text-h3">{check.gesture}</p>
      </div>

      <div className="grid gap-4 lg:grid-cols-2">
        {(["id", "selfie"] as const).map((which) => (
          <figure key={which} className="flex flex-col gap-2">
            <figcaption className="font-semibold">{which === "id" ? "ID photo" : "Selfie"}</figcaption>
            {urls ? (
              // eslint-disable-next-line @next/next/no-img-element -- a private blob: URL, nothing for Next to optimise
              <img src={urls[which]} alt={which === "id" ? `${check.firstName}'s ID` : `${check.firstName}'s selfie`}
                   className="max-h-[28rem] w-full rounded-md border border-line bg-surface object-contain" />
            ) : (
              <div className="h-72 animate-pulse rounded-md bg-surface-2" />
            )}
          </figure>
        ))}
      </div>

      <Card className="flex flex-col gap-2">
        <h2 className="font-display text-h3">Check all of these</h2>
        <ul className="flex list-disc flex-col gap-1 pl-5 text-fg-muted">
          {CHECKLIST.map((item) => <li key={item}>{item}</li>)}
        </ul>
      </Card>

      <div className="grid gap-6 lg:grid-cols-2">
        <form onSubmit={sendApprove} className="flex flex-col gap-4">
          <h2 className="font-display text-h3">Approve</h2>
          <fieldset className="flex flex-col gap-2">
            <legend className="mb-2 font-semibold">Gender printed on the ID</legend>
            <div className="grid grid-cols-3 gap-2">
              {GENDERS.map((g) => (
                <Choice key={g.value} name="gender" value={g.value} checked={gender === g.value} onChange={setGender}>{g.label}</Choice>
              ))}
            </div>
          </fieldset>
          <div className="flex flex-col gap-2">
            <label htmlFor="last4" className="font-semibold">Last 4 letters or digits of the document number</label>
            <input id="last4" value={last4} maxLength={4} autoComplete="off" onChange={(e) => setLast4(e.target.value.toUpperCase())}
                   className={cx(boxClass(), FIELD, "h-cta w-40 px-3.5 font-mono text-[1.1875rem] tracking-[0.2em]")} />
          </div>
          {approve.error && <Alert variant="error">{approve.error}</Alert>}
          <Button type="submit" loading={approve.busy} loadingLabel="Approving…" disabled={reject.busy}>Approve</Button>
        </form>

        <form onSubmit={sendReject} className="flex flex-col gap-4">
          <h2 className="font-display text-h3">Reject</h2>
          <fieldset className="flex flex-col gap-2">
            <legend className="sr-only">Reason, shown to them</legend>
            {[...REASONS, "other"].map((r) => (
              <Choice key={r} name="reason" value={r} checked={reason === r} onChange={setReason}>{r === "other" ? "Other" : r}</Choice>
            ))}
          </fieldset>
          {reason === "other" && (
            <input aria-label="Reason, shown to them" value={other} maxLength={200} onChange={(e) => setOther(e.target.value)}
                   className={cx(boxClass(), FIELD, "h-cta w-full px-3.5")} />
          )}
          {reject.error && <Alert variant="error">{reject.error}</Alert>}
          <Button type="submit" variant="danger" size="cta" loading={reject.busy} loadingLabel="Rejecting…" disabled={approve.busy}>
            Reject
          </Button>
        </form>
      </div>
    </div>
  );
}

function Choice({ name, value, checked, onChange, children }: {
  name: string;
  value: string;
  checked: boolean;
  onChange: (value: string) => void;
  children: ReactNode;
}) {
  return (
    <label className={cx(
      "flex min-h-tap cursor-pointer items-center gap-2.5 rounded-md border-[1.5px] px-3 font-semibold",
      checked ? "border-accent-text bg-accent-soft dark:border-accent" : "border-line bg-surface dark:bg-surface-2",
    )}>
      <input type="radio" name={name} value={value} checked={checked} onChange={() => onChange(value)} className="size-5 accent-accent" />
      {children}
    </label>
  );
}
