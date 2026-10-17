"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { AppIcon, Wordmark } from "@/components/ui/brand";
import { Avatar } from "@/components/ui/display";
import { formatIstTime } from "@/lib/time";

// The header of every signed-in screen: the wordmark (home) and your avatar (profile).
// On laptops it's the 64px top bar, with the airport clock: times in Namastute are always India time.
export function AppBar({ name }: { name: string }) {
  const [now, setNow] = useState(() => new Date().toISOString());
  useEffect(() => {
    const t = setInterval(() => setNow(new Date().toISOString()), 15_000);
    return () => clearInterval(t);
  }, []);

  return (
    <header className="mx-auto flex w-full max-w-[30rem] items-center justify-between gap-4 px-5 pt-3 pb-3 lg:h-16 lg:max-w-none lg:border-b lg:border-line lg:px-8 lg:py-0">
      <Link href="/" aria-label="Namastute home" className="inline-flex items-center gap-2.5 rounded-sm">
        {/* Laptops only. "hidden" can't go on AppIcon itself: its own inline-flex would fight it (Learning Notes 71.3) */}
        <span className="hidden lg:contents"><AppIcon className="size-[2.125rem] text-2xl" /></span>
        <Wordmark className="text-[1.375rem]" />
      </Link>
      <div className="flex items-center gap-4">
        <span className="tnum hidden font-semibold text-fg-muted lg:inline">Delhi IGI · {formatIstTime(now)} IST</span>
        <Link href="/profile" aria-label="Your profile" className="rounded-full">
          <Avatar name={name} size={44} className="lg:size-10" />
        </Link>
      </div>
    </header>
  );
}
