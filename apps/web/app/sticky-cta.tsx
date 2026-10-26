"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { buttonClass } from "@/components/ui/button";
import { BottomBar } from "@/components/ui/layout";

// Phones only: once the hero's button has scrolled out of view, the same button waits in thumb reach at the bottom
export default function StickyCta({ href, watch }: { href: string; watch: string }) {
  const [show, setShow] = useState(false);

  useEffect(() => {
    const target = document.getElementById(watch);
    if (!target) return;
    const observer = new IntersectionObserver(([entry]) => setShow(!entry.isIntersecting && entry.boundingClientRect.top < 0));
    observer.observe(target);
    return () => observer.disconnect();
  }, [watch]);

  if (!show) return null;
  return (
    <BottomBar bordered className="md:hidden">
      <Link href={href} className={buttonClass({ className: "w-full" })}>Find my co-travellers <span aria-hidden>→</span></Link>
    </BottomBar>
  );
}
