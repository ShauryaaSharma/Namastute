import type { ReactNode } from "react";
import { Lockup } from "@/components/ui/brand";
import { cx } from "@/components/ui/cx";

// Log in and profile (design W1–W3). Phones: one column, centred up to 480px. From 1024px: a split screen,
// the brand pane on the left and the form on a 560px surface on the right.
export function AuthShell({ aside, children }: { aside: ReactNode; children: ReactNode }) {
  return (
    <main className="mx-auto flex min-h-dvh w-full max-w-[30rem] flex-col lg:grid lg:max-w-none lg:grid-cols-[minmax(0,1fr)_35rem]">
      <aside className="hidden lg:flex lg:flex-col lg:justify-between lg:gap-10 lg:border-r lg:border-line lg:px-14 lg:py-12">
        <Lockup large />
        {aside}
        <span />
      </aside>
      <div className="flex flex-1 flex-col lg:justify-center lg:overflow-y-auto lg:bg-surface lg:p-12">{children}</div>
    </main>
  );
}

// "Landed at IGI? / Share the cab." — the big headline (orange is a fill on light, so its words use accent-text)
export function Headline({ large }: { large?: boolean }) {
  return (
    <h1
      className={cx(
        "font-display font-extrabold",
        large
          ? "stretch-115 text-[clamp(3.5rem,6.5vw,6rem)] leading-[0.92] tracking-[-0.03em]"
          : "stretch-112 text-display tracking-[-0.02em]",
      )}
    >
      Landed at IGI?
      <br />
      <span className="text-accent-text dark:text-accent">Share the cab.</span>
    </h1>
  );
}

export const PITCH = "Find people walking out around your time, heading your way. Split one Uber or Ola.";

const TRUST = [
  ["First name + area", "That's all anyone sees."],
  ["You book, you pay", "We never touch rides or money."],
  ["T1 · T2 · T3", "Delhi IGI arrivals only."],
];

// The left pane of the wide login screen: headline, pitch and three promises
export function LoginAside({ withTrust }: { withTrust?: boolean }) {
  return (
    <div className="flex flex-col gap-10">
      <div className="flex flex-col gap-5">
        <Headline large />
        {withTrust && <p className="max-w-[32.5rem] text-xl text-fg-muted">{PITCH}</p>}
      </div>
      {withTrust && (
        <ul className="grid max-w-[40rem] gap-5 xl:grid-cols-3">
          {TRUST.map(([title, text]) => (
            <li key={title} className="flex flex-col gap-1 border-t-2 border-accent pt-2.5">
              <b className="font-display font-bold">{title}</b>
              <span className="text-small text-fg-muted">{text}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
