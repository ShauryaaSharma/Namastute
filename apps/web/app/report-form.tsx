"use client";

import { type FormEvent, useState } from "react";
import { Button } from "@/components/ui/button";
import { cx } from "@/components/ui/cx";
import { Alert } from "@/components/ui/display";
import { boxClass } from "@/components/ui/field";
import { type ReportRequest, reportTraveller } from "@/lib/api";
import { useAction } from "@/lib/use-action";

const CATEGORIES: { value: ReportRequest["category"]; label: string }[] = [
  { value: "unsafe", label: "Unsafe behaviour" },
  { value: "harassment", label: "Harassment" },
  { value: "no_show", label: "Didn't show up" },
  { value: "fake_profile", label: "Fake profile" },
  { value: "other", label: "Something else" },
];
const FOCUS = "outline-none focus-visible:shadow-none focus:border-2 focus:border-fg dark:focus:border-accent";

// Report someone you shared a group with (Learning Notes 56.3). A human reviews it; the other person is never told.
// Shown inside the person's sheet, under "Call 112".
export default function ReportForm({ tripId, firstName, onSent, onCancel }: {
  tripId: string;
  firstName: string;
  onSent: () => void;
  onCancel: () => void;
}) {
  const [category, setCategory] = useState<ReportRequest["category"]>("unsafe");
  const [details, setDetails] = useState("");
  const { busy, error, run } = useAction();

  function send(e: FormEvent) {
    e.preventDefault();
    run(async () => {
      await reportTraveller({ tripId, category, details: details.trim() || null });
      onSent();
    });
  }

  return (
    <form onSubmit={send} className="flex flex-col gap-4" aria-label={`Report ${firstName}`}>
      <div className="flex flex-col gap-2">
        <label htmlFor="report-category" className="font-semibold">What happened?</label>
        <select id="report-category" value={category} onChange={(e) => setCategory(e.target.value as ReportRequest["category"])}
                className={cx(boxClass(), FOCUS, "h-cta w-full px-3 text-[1.0625rem] font-medium")}>
          {CATEGORIES.map((c) => <option key={c.value} value={c.value}>{c.label}</option>)}
        </select>
      </div>
      <div className="flex flex-col gap-2">
        <label htmlFor="report-details" className="font-semibold">Details <span className="font-normal text-fg-muted">(optional)</span></label>
        <textarea id="report-details" value={details} onChange={(e) => setDetails(e.target.value)} maxLength={500} rows={3}
                  className={cx(boxClass(), FOCUS, "w-full resize-y px-3.5 py-3 leading-[1.4]")} />
        <p className="text-[0.9375rem] text-fg-muted">A person at Namastute reads every report. {firstName} won&apos;t be told who reported them.</p>
      </div>
      {error && <Alert variant="error">{error}</Alert>}
      <div className="grid gap-2.5">
        <Button type="submit" variant="danger" size="cta" loading={busy} loadingLabel="Sending…">Send report</Button>
        <Button variant="secondary" onClick={onCancel}>Back</Button>
      </div>
    </form>
  );
}
