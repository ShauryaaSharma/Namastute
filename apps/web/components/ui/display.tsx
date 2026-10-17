import type { HTMLAttributes, ReactNode } from "react";
import { cx } from "./cx";

// ---- Alerts: error / success / info / warning, each with a leading glyph ----
const ALERTS = {
  error: ["bg-danger-soft text-danger", "!"],
  success: ["bg-success-soft text-success", "✓"],
  info: ["bg-info-soft text-info", "i"],
  warning: ["bg-accent-soft text-accent-text", "!"],
} as const;

export function Alert({ variant, children, className }: { variant: keyof typeof ALERTS; children: ReactNode; className?: string }) {
  const [colours, glyph] = ALERTS[variant];
  return (
    // Errors are announced as soon as they appear (role="alert"); the rest are read in place
    <div role={variant === "error" ? "alert" : undefined} className={cx("flex gap-2.5 rounded-[14px] px-3.5 py-3 text-[0.9375rem] font-medium leading-[1.4]", colours, className)}>
      <b aria-hidden className="font-bold">{glyph}</b>
      <span>{children}</span>
    </div>
  );
}

// ---- Avatar: the first letter of the first name. Devanagari names use the first akshara ("नेहा" → "ने") ----
export function initial(name: string): string {
  const first = name.trim().split(/\s+/)[0] ?? "";
  const grapheme = [...new Intl.Segmenter("hi", { granularity: "grapheme" }).segment(first)][0]?.segment;
  return grapheme ? grapheme.toUpperCase() : "?";
}

const AVATAR_SIZES = { 40: "size-10 text-base", 44: "size-11 text-lg", 48: "size-12 text-lg", 52: "size-[3.25rem] text-[1.375rem]" };

export function Avatar({ name, you, size = 44, className }: { name: string; you?: boolean; size?: keyof typeof AVATAR_SIZES; className?: string }) {
  return (
    <span
      aria-hidden
      className={cx(
        "flex shrink-0 items-center justify-center rounded-full pt-[0.1em] font-display font-bold",
        you ? "bg-fg text-bg" : "bg-surface-2 text-fg dark:bg-line",
        AVATAR_SIZES[size],
        className,
      )}
    >
      {initial(name)}
    </span>
  );
}

// ---- Card: flat surface; light cards get a hairline border ----
export function Card({ className, ...props }: HTMLAttributes<HTMLDivElement>) {
  return <div {...props} className={cx("rounded-lg border border-line bg-surface p-5 dark:border-transparent", className)} />;
}

// ---- Chips (pills) and status badges ----
const CHIPS = {
  neutral: "bg-surface-2 text-fg",
  muted: "bg-surface-2 text-fg-muted",
  "asked-you": "bg-accent-soft text-accent-text",
  declined: "bg-danger-soft text-danger",
  here: "bg-success-soft text-success",
  info: "bg-info-soft text-info",
  booker: "border-[1.5px] border-fg text-fg",
  you: "bg-fg font-bold text-bg",
};

export function Chip({ variant = "neutral", children, className }: { variant?: keyof typeof CHIPS; children: ReactNode; className?: string }) {
  return <span className={cx("inline-flex items-center rounded-full px-2.5 py-1 text-small font-semibold", CHIPS[variant], className)}>{children}</span>;
}

const BADGES = { open: "bg-accent text-on-accent", locked: "bg-fg text-bg", met: "bg-success text-success-soft" };
const BADGE_TEXT = { open: "Open", locked: "Locked", met: "Met up" };

export function StatusBadge({ status }: { status: keyof typeof BADGES }) {
  return (
    <span className={cx("inline-flex shrink-0 whitespace-nowrap rounded-sm px-3 py-1.5 font-display text-eyebrow uppercase tracking-[0.06em]", BADGES[status])}>
      {BADGE_TEXT[status]}
    </span>
  );
}

// ---- Eyebrow: the small uppercase line above a heading ----
export function Eyebrow({ children, className }: { children: ReactNode; className?: string }) {
  return <p className={cx("font-display text-eyebrow uppercase text-accent-text dark:text-accent", className)}>{children}</p>;
}

// ---- Empty state and loading skeleton ----
export function EmptyState({ title, children, className }: { title: ReactNode; children?: ReactNode; className?: string }) {
  return (
    <div className={cx("flex flex-col items-start gap-2 rounded-lg border-[1.5px] border-dashed border-line p-7", className)}>
      <span aria-hidden className="font-display text-[3.5rem] font-extrabold leading-none text-accent-text dark:text-accent">0</span>
      <p className="text-[1.0625rem] font-bold leading-[1.35]">{title}</p>
      {children && <p className="text-[0.9375rem] text-fg-muted">{children}</p>}
    </div>
  );
}

export function Skeleton({ round, className }: { round?: boolean; className?: string }) {
  return <span aria-hidden className={cx("block animate-pulse bg-surface-2", round ? "rounded-full" : "rounded-sm", className)} />;
}
