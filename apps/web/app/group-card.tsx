"use client";

import { useEffect, useState } from "react";
import { getCurrentGroup, leaveGroup, lockGroup, type Group } from "@/lib/api";
import { plural } from "@/lib/format";
import { useAction } from "@/lib/use-action";

// Your cab group: who's in it, the booker's UPI ID (members only), lock and leave
export default function GroupCard({ onChange }: { onChange: () => void }) {
  const [group, setGroup] = useState<Group | null>(null);
  const [reason, setReason] = useState("");
  const { busy, error, setError, run } = useAction();

  useEffect(() => {
    getCurrentGroup()
      .then(setGroup)
      .catch(() => setGroup(null));
  }, []);

  if (!group) return null;
  const g = group;
  const locked = g.status === "locked";

  function leave() {
    if (locked && !reason.trim()) {
      setError("Tell your group why you're leaving."); // the API checks too
      return;
    }
    run(async () => { await leaveGroup(g.id, reason.trim() || undefined); onChange(); });
  }

  return (
    <div className="mt-3">
      <h3 className="font-semibold">{locked ? "Your cab group · locked, the booker is booking the cab" : "Your cab group"}</h3>
      <ul className="mt-1 list-disc pl-5">
        {g.members.map((m, i) => (
          // no ids on purpose (privacy); the list never reorders while shown, so the position is a fine key
          <li key={i}>
            {m.you ? "You" : m.firstName}
            {m.booker ? " (booker)" : ""} · {m.destArea} · {plural(m.seats, "seat")}, {plural(m.bags, "bag")}
          </li>
        ))}
      </ul>
      {!g.youAreBooker && (
        <p className="mt-1 text-sm">
          {g.bookerUpiId ? `Pay your share to the booker's UPI ID: ${g.bookerUpiId}` : "The booker hasn't added a UPI ID yet."}
        </p>
      )}
      {g.updates.length > 0 && (
        <ul className="mt-1 text-sm" aria-label="Group updates">
          {g.updates.map((u, i) => (
            <li key={i}>{u}</li>
          ))}
        </ul>
      )}
      {g.youAreBooker && !locked && (
        <button
          onClick={() => run(async () => { await lockGroup(g.id); onChange(); })}
          disabled={busy}
          className="mt-2 mr-4 rounded bg-black px-3 py-1 text-white disabled:opacity-50"
        >
          Lock group: I&apos;m booking the cab
        </button>
      )}
      {locked && (
        <label htmlFor="leave-reason" className="mt-2 block">
          Why are you leaving? (required once the group is locked)
          <textarea
            id="leave-reason"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            maxLength={300}
            className="mt-1 block w-full rounded border p-1"
          />
        </label>
      )}
      <button onClick={leave} disabled={busy} className="mt-2 underline disabled:opacity-50">
        Leave group
      </button>
      <p role="alert" aria-live="polite" className="text-red-600">{error}</p>
    </div>
  );
}
