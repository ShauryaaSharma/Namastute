"use client";

import { useEffect, useState } from "react";
import { Button } from "@/components/ui/button";
import { Alert } from "@/components/ui/display";
import { cx } from "@/components/ui/cx";
import { enablePush, pushState, syncPush, type PushState } from "@/lib/api";
import { useAction } from "@/lib/use-action";

// The soft ask (Learning Notes 58.4): our own button first; the browser's prompt only after a tap, never on page load
export default function PushPrompt({ className }: { className?: string }) {
  const [state, setState] = useState<PushState | null>(null); // null until we're in the browser
  const { busy, error, setError, run } = useAction();

  useEffect(() => {
    const now = pushState();
    if (now === "on") syncPush().catch(() => setError("Couldn't set up notifications. Reload the page to try again."));
    setState(now); // eslint-disable-line react-hooks/set-state-in-effect -- browser-only, unknown during server rendering
  }, [setError]);

  if (state === null || state === "unsupported") return null;

  return (
    <div className={cx("flex flex-col gap-3", className)} aria-live="polite">
      {state === "ask" && (
        <div className="flex flex-wrap items-center gap-3">
          <Button variant="secondary" loading={busy} onClick={() => run(async () => setState(await enablePush()))}>
            Notify me
          </Button>
          <span className="text-fg-muted">when someone wants to share, or your group needs you</span>
        </div>
      )}
      {state === "on" && !error && <Alert variant="success">Notifications are on.</Alert>}
      {state === "denied" && (
        <Alert variant="info">Notifications are blocked for Namastute. To get them, allow notifications in your browser&apos;s site settings.</Alert>
      )}
      {state === "install" && (
        <Alert variant="info">To get notifications on iPhone, tap Share, then &quot;Add to Home Screen&quot;, and open Namastute from there.</Alert>
      )}
      {error && <Alert variant="error">{error}</Alert>}
    </div>
  );
}
