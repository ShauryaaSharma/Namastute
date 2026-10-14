"use client";

import { useEffect, useState } from "react";
import { getCurrentGroup, imHere, leaveGroup, lockGroup, type Group } from "@/lib/api";
import { plural } from "@/lib/format";
import { useAction } from "@/lib/use-action";
import FareSection from "./fare-section";
import GroupChat from "./group-chat";

// Your cab group: who's in it, lock, "I'm here", leave, the fare (FareSection) and the live chat.
// Not re-created on every refresh (version is a prop, not a key), so the chat connection stays open.
export default function GroupCard({ version, onChange }: { version: number; onChange: () => void }) {
  const [group, setGroup] = useState<Group | null>(null);
  const [reason, setReason] = useState("");
  const { busy, error, setError, run } = useAction();

  useEffect(() => {
    getCurrentGroup()
      .then(setGroup)
      .catch(() => setGroup(null));
  }, [version]);

  if (!group) return null;
  const g = group;
  const locked = g.status === "locked";
  const met = g.status === "met";
  const me = g.members.find((m) => m.you);

  function leave() {
    if (locked && !reason.trim()) {
      setError("Tell your group why you're leaving."); // the API checks too
      return;
    }
    run(async () => { await leaveGroup(g.id, reason.trim() || undefined); onChange(); });
  }

  return (
    <div className="mt-3">
      <h3 className="font-semibold">
        Your cab group
        {locked ? " · locked, the booker is booking the cab" : met ? " · you've met up" : ""}
      </h3>
      <ul className="mt-1 list-disc pl-5">
        {g.members.map((m, i) => (
          // no ids on purpose (privacy); the list never reorders while shown, so the position is a fine key
          <li key={i}>
            {m.you ? "You" : m.firstName}
            {m.booker ? " (booker)" : ""} · {m.destArea} · {plural(m.seats, "seat")}, {plural(m.bags, "bag")}
            {m.here ? " · here ✓" : ""}
          </li>
        ))}
      </ul>
      {!g.youAreBooker && !met && (
        <p className="mt-1 text-sm">
          {g.bookerUpiId ? `After the ride, pay your share to the booker's UPI ID: ${g.bookerUpiId}` : "The booker hasn't added a UPI ID yet."}
        </p>
      )}
      {(locked || met) && !me?.here && (
        <button
          onClick={() => run(async () => { await imHere(g.id); onChange(); })}
          disabled={busy}
          className="mt-2 mr-4 rounded bg-black px-3 py-1 text-white disabled:opacity-50"
        >
          I&apos;m here at the pickup
        </button>
      )}
      {met && <FareSection group={g} onChange={onChange} />}
      {g.youAreBooker && g.status === "open" && (
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
      {!met && (
        <button onClick={leave} disabled={busy} className="mt-2 underline disabled:opacity-50">
          Leave group
        </button>
      )}
      <p role="alert" aria-live="polite" className="text-red-600">{error}</p>
      <GroupChat key={g.id} groupId={g.id} onUpdated={onChange} />
    </div>
  );
}
