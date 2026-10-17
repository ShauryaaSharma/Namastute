"use client";

import { type FormEvent, useState } from "react";
import { type ReportRequest, reportTraveller } from "@/lib/api";
import { useAction } from "@/lib/use-action";

const CATEGORIES: { value: ReportRequest["category"]; label: string }[] = [
  { value: "unsafe", label: "Unsafe behaviour" },
  { value: "harassment", label: "Harassment" },
  { value: "no_show", label: "Didn't show up" },
  { value: "fake_profile", label: "Fake profile" },
  { value: "other", label: "Something else" },
];

// Report someone you shared a group with (Learning Notes 56.3). A human reviews it; the other person is never told.
export default function ReportForm({ tripId, firstName, onClose }: { tripId: string; firstName: string; onClose: () => void }) {
  const [category, setCategory] = useState<ReportRequest["category"]>("unsafe");
  const [details, setDetails] = useState("");
  const [sent, setSent] = useState(false);
  const { busy, error, run } = useAction();

  function send(e: FormEvent) {
    e.preventDefault();
    run(async () => {
      await reportTraveller({ tripId, category, details: details.trim() || null });
      setSent(true);
    });
  }

  if (sent) {
    return (
      <p className="mt-1 text-sm" role="status">
        Thanks. We&apos;ll review your report. {firstName} won&apos;t be told who reported them.{" "}
        <button onClick={onClose} className="underline">Close</button>
      </p>
    );
  }

  return (
    <form onSubmit={send} className="mt-1 rounded border p-2" aria-label={`Report ${firstName}`}>
      <p className="font-semibold text-danger">
        In danger right now? Call <a href="tel:112" className="underline">112</a>.
      </p>
      <label htmlFor={`report-category-${tripId}`} className="mt-1 block">What happened?</label>
      <select
        id={`report-category-${tripId}`}
        value={category}
        onChange={(e) => setCategory(e.target.value as ReportRequest["category"])}
        className="rounded border p-1"
      >
        {CATEGORIES.map((c) => (
          <option key={c.value} value={c.value}>{c.label}</option>
        ))}
      </select>
      <label htmlFor={`report-details-${tripId}`} className="mt-1 block">Details (optional)</label>
      <textarea
        id={`report-details-${tripId}`}
        value={details}
        onChange={(e) => setDetails(e.target.value)}
        maxLength={500}
        className="block w-full rounded border p-1"
      />
      <button type="submit" disabled={busy} className="mt-1 rounded bg-accent px-3 py-1 text-on-accent disabled:opacity-50">
        Send report
      </button>
      <button type="button" onClick={onClose} className="ml-3 underline">Cancel</button>
      <p role="alert" aria-live="polite" className="text-danger">{error}</p>
    </form>
  );
}
