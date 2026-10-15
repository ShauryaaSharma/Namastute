"use client";

import { useEffect, useState } from "react";
import { enablePush, pushState, syncPush, type PushState } from "@/lib/api";
import { useAction } from "@/lib/use-action";

// The soft ask (Learning Notes 58.4): our own button first; the browser's prompt only after a tap, never on page load
export default function PushPrompt() {
  const [state, setState] = useState<PushState | null>(null); // null until we're in the browser
  const { busy, error, setError, run } = useAction();

  useEffect(() => {
    const now = pushState();
    if (now === "on") syncPush().catch(() => setError("Couldn't set up notifications. Reload the page to try again."));
    setState(now); // eslint-disable-line react-hooks/set-state-in-effect -- browser-only, unknown during server rendering
  }, [setError]);

  if (state === null || state === "unsupported") return null;

  return (
    <div className="mt-3 text-sm" aria-live="polite">
      {state === "ask" && (
        <>
          <button
            onClick={() => run(async () => setState(await enablePush()))}
            disabled={busy}
            className="rounded border px-3 py-1 disabled:opacity-50"
          >
            Notify me
          </button>
          <span className="ml-2">when someone wants to share, or your group needs you</span>
        </>
      )}
      {state === "on" && !error && <p>Notifications are on.</p>}
      {state === "denied" && (
        <p>Notifications are blocked for Namastute. To get them, allow notifications in your browser&apos;s site settings.</p>
      )}
      {state === "install" && (
        <p>
          To get notifications on iPhone, tap Share, then &quot;Add to Home Screen&quot;, and open Namastute from there.
        </p>
      )}
      {error && <p className="text-red-600">{error}</p>}
    </div>
  );
}
