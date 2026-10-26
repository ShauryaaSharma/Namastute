"use client";

import { type KeyboardEvent, type ReactNode, useId, useRef } from "react";
import { cx } from "./cx";

// ---- Segmented: a radio group drawn as one bar (terminal T1/T2/T3, theme) ----
export function Segmented<T extends string>({ label, options, value, onChange, className }: {
  label: string; // read by screen readers
  options: { value: T; label: ReactNode }[];
  value: T;
  onChange: (value: T) => void;
  className?: string;
}) {
  const refs = useRef<(HTMLButtonElement | null)[]>([]);
  // Arrow keys move the choice, like native radio buttons; only the chosen one is in the Tab order
  function onKey(e: KeyboardEvent, i: number) {
    const step = e.key === "ArrowRight" || e.key === "ArrowDown" ? 1 : e.key === "ArrowLeft" || e.key === "ArrowUp" ? -1 : 0;
    if (!step) return;
    e.preventDefault();
    const next = (i + step + options.length) % options.length;
    onChange(options[next].value);
    refs.current[next]?.focus();
  }
  return (
    <div
      role="radiogroup"
      aria-label={label}
      className={cx("grid gap-1 rounded-btn bg-surface-2 p-1", className)}
      style={{ gridTemplateColumns: `repeat(${options.length}, minmax(0, 1fr))` }}
    >
      {options.map((o, i) => {
        const selected = o.value === value;
        return (
          <button
            key={o.value}
            ref={(el) => { refs.current[i] = el; }}
            type="button"
            role="radio"
            aria-checked={selected}
            tabIndex={selected ? 0 : -1}
            onClick={() => onChange(o.value)}
            onKeyDown={(e) => onKey(e, i)}
            className={cx(
              "min-h-tap rounded-[10px] px-2 font-display text-[1.1875rem] font-bold",
              selected ? "bg-accent text-on-accent" : "text-fg-muted hover:text-fg",
            )}
          >
            {o.label}
          </button>
        );
      })}
    </div>
  );
}

// ---- Stepper: − value + (seats 1–3, bags 0–6) ----
export function Stepper({ label, hint, value, min, max, onChange, fewerLabel, moreLabel }: {
  label: string;
  hint?: string;
  value: number;
  min: number;
  max: number;
  onChange: (value: number) => void;
  fewerLabel: string; // "Fewer seats"
  moreLabel: string;
}) {
  const id = useId();
  const button = "flex size-tap items-center justify-center rounded-[10px] text-2xl font-semibold";
  const enabled = "bg-line text-fg";
  const disabled = "bg-surface text-fg-disabled";
  return (
    <div className="flex flex-wrap items-center justify-between gap-3">
      <span className="flex flex-col">
        <span id={id} className="font-semibold">{label}</span>
        {hint && <span className="text-small text-fg-muted">{hint}</span>}
      </span>
      <div role="group" aria-labelledby={id} className="flex items-center gap-1 rounded-btn bg-surface-2 p-1">
        <button type="button" aria-label={fewerLabel} disabled={value <= min} onClick={() => onChange(value - 1)}
                className={cx(button, value <= min ? disabled : enabled)}>−</button>
        <output aria-live="polite" className="tnum w-10 text-center font-display text-[1.375rem] font-bold">{value}</output>
        <button type="button" aria-label={moreLabel} disabled={value >= max} onClick={() => onChange(value + 1)}
                className={cx(button, value >= max ? disabled : enabled)}>+</button>
      </div>
    </div>
  );
}

// ---- Toggle: a switch. Unavailable ones (women-only, without a verified ID) sit in a dashed box. ----
export function Toggle({ label, description, checked, onChange, unavailable }: {
  label: string;
  description?: ReactNode;
  checked: boolean;
  onChange?: (checked: boolean) => void;
  unavailable?: boolean; // still readable by screen readers (aria-disabled), but can't be switched
}) {
  const id = useId();
  return (
    <div className={cx("flex items-center justify-between gap-3", unavailable && "rounded-[14px] border-[1.5px] border-dashed border-line p-3.5")}>
      <span className="flex flex-col gap-0.5">
        <span id={id} className={cx("font-semibold", unavailable && "text-fg-muted")}>{label}</span>
        {description && <span className="text-small text-fg-muted">{description}</span>}
      </span>
      <button
        type="button"
        role="switch"
        aria-checked={checked}
        aria-labelledby={id}
        aria-disabled={unavailable || undefined}
        onClick={() => !unavailable && onChange?.(!checked)}
        className={cx("flex h-8 w-14 shrink-0 items-center rounded-full p-1", checked && !unavailable ? "bg-accent" : "bg-line")}
      >
        <span className={cx("size-6 rounded-full transition-transform", checked && !unavailable ? "translate-x-6 bg-on-accent" : "bg-fg-disabled")} />
      </button>
    </div>
  );
}
