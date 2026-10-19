"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { AppIcon, Wordmark } from "@/components/ui/brand";
import { Avatar } from "@/components/ui/display";
import { formatIstTime } from "@/lib/time";

// The header of every signed-in screen: the wordmark (home) and your avatar (profile).
// On laptops it's the 64px top bar, with the airport clock: times in Namastute are always India time.
// live: the group's live connection (null = none on this screen). "Live" only shows when it's true; while it's down,
// a "Reconnecting…" pill takes its place (under the header on phones).
export function AppBar({ name, live = null }: { name: string; live?: boolean | null }) {
  const [now, setNow] = useState(() => new Date().toISOString());
  useEffect(() => {
    const t = setInterval(() => setNow(new Date().toISOString()), 15_000);
    return () => clearInterval(t);
  }, []);

  return (
    <header className="mx-auto flex w-full max-w-[30rem] flex-wrap items-center justify-between gap-x-4 gap-y-2 px-5 pt-3 pb-3 lg:h-16 lg:max-w-none lg:border-b lg:border-line lg:bg-surface lg:px-8 lg:py-0 lg:dark:bg-bg">
      <Link href="/" aria-label="Namastute home" className="inline-flex items-center gap-2.5 rounded-sm">
        {/* Laptops only. "hidden" can't go on AppIcon itself: its own inline-flex would fight it (Learning Notes 71.3) */}
        <span className="hidden lg:contents"><AppIcon className="size-[2.125rem] text-2xl" /></span>
        <Wordmark className="text-[1.375rem]" />
      </Link>
      {live === false && (
        <span role="status" className="order-last flex basis-full items-center gap-2.5 rounded-full bg-surface-2 px-3.5 py-2.5 text-small font-semibold lg:order-none lg:basis-auto">
          <span aria-hidden className="size-2 shrink-0 animate-pulse rounded-full bg-accent" />
          Reconnecting… airport Wi-Fi, hang on
        </span>
      )}
      <div className="flex items-center gap-4">
        {live !== false && (
          <span className="tnum hidden items-center gap-2 font-semibold text-fg-muted lg:flex">
            {live && <span aria-hidden className="size-2 rounded-full bg-success" />}
            {live ? "Live" : "Delhi IGI"} · {formatIstTime(now)} IST
          </span>
        )}
        <Link href="/profile" aria-label="Your profile" className="rounded-full">
          <Avatar name={name} size={44} className="lg:size-10" />
        </Link>
      </div>
    </header>
  );
}
