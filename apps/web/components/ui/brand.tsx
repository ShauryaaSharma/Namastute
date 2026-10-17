import { cx } from "./cx";

// The app icon: न (na) on a sodium-orange tile, like an airport wayfinding sign. Pure type, no image.
export function AppIcon({ className }: { className?: string }) {
  return (
    <span
      aria-hidden
      className={cx("inline-flex shrink-0 items-center justify-center rounded-[25%] bg-accent pt-[0.12em] font-display font-extrabold leading-none text-on-accent", className)}
    >
      न
    </span>
  );
}

// "namaste" + "astute" overlap on "ast", so that's the part that's lit up
export function Wordmark({ className }: { className?: string }) {
  return (
    <span className={cx("font-display font-extrabold stretch-115 tracking-[-0.02em]", className)}>
      nam<span className="text-brand-mark">ast</span>ute
    </span>
  );
}

// Icon + wordmark: 36/22px in app headers, 40/26px on the wide login screen
export function Lockup({ large }: { large?: boolean }) {
  return (
    <span className="inline-flex items-center gap-2.5">
      <AppIcon className={large ? "size-10 text-[1.75rem]" : "size-9 text-[1.5625rem]"} />
      <Wordmark className={large ? "text-[1.625rem]" : "text-[1.375rem]"} />
    </span>
  );
}
