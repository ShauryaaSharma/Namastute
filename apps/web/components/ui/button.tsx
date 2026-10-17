import type { ButtonHTMLAttributes } from "react";
import { cx } from "./cx";

type Variant = "primary" | "secondary" | "tonal" | "ink" | "danger" | "danger-text" | "link";
type Size = "cta" | "tap" | "pay";

// Colours and their disabled look are separate lists: two bg-* classes on one element would fight
// (in Tailwind the stylesheet's order decides, not the class order)
const COLOURS: Record<Variant, string> = {
  primary: "bg-accent text-on-accent hover:brightness-95",
  ink: "bg-fg text-bg hover:brightness-95",
  danger: "bg-danger text-on-danger hover:brightness-95",
  secondary: "border-[1.5px] border-line-strong hover:bg-surface-2",
  tonal: "bg-surface-2 hover:bg-line",
  "danger-text": "text-danger underline underline-offset-4",
  link: "text-accent-text underline underline-offset-4",
};
const DISABLED: Record<Variant, string> = {
  primary: "bg-surface-2 text-fg-disabled",
  ink: "bg-surface-2 text-fg-disabled",
  danger: "bg-surface-2 text-fg-disabled",
  tonal: "bg-surface-2 text-fg-disabled",
  secondary: "border-[1.5px] border-line text-fg-disabled",
  "danger-text": "text-fg-disabled",
  link: "text-fg-disabled",
};
const TYPE: Record<Variant, string> = {
  primary: "px-4 font-display font-bold stretch-108 text-[1.1875rem]",
  ink: "px-4 font-display font-bold stretch-108 text-[1.1875rem]",
  danger: "px-4 font-semibold",
  secondary: "px-4 font-semibold",
  tonal: "px-4 font-semibold",
  "danger-text": "px-1 font-semibold",
  link: "px-2 font-bold",
};
const SIZES: Record<Size, string> = { cta: "min-h-cta", tap: "min-h-tap", pay: "min-h-pay" };

type Props = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: Variant;
  size?: Size;
  loading?: boolean; // shows a spinner and loadingLabel, and blocks clicks
  loadingLabel?: string;
};

// Buttons from the design system (Learning Notes 70): one primary action per screen, ≥ 48px targets
export function Button({ variant = "primary", size, loading, loadingLabel, disabled, className, children, ...rest }: Props) {
  return (
    <button
      type="button"
      {...rest}
      disabled={disabled || loading}
      aria-busy={loading || undefined}
      className={cx(
        "inline-flex items-center justify-center gap-2.5 rounded-btn py-2 text-center leading-tight transition",
        TYPE[variant],
        SIZES[size ?? (variant === "primary" || variant === "ink" ? "cta" : "tap")],
        disabled && !loading ? DISABLED[variant] : COLOURS[variant],
        loading && "opacity-85",
        className,
      )}
    >
      {loading && <Spinner />}
      {loading && loadingLabel ? loadingLabel : children}
    </button>
  );
}

export function Spinner({ className }: { className?: string }) {
  return (
    <span aria-hidden className={cx("inline-block size-[1.125rem] shrink-0 animate-spin rounded-full border-[2.5px] border-current border-r-transparent", className)} />
  );
}
