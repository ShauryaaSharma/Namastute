"use client";

import { type InputHTMLAttributes, type ReactNode, useState } from "react";
import { cx } from "./cx";

// The input box itself: 56px, 1.5px border; focus = 2px accent (dark) or ink (light); error = 2px danger
export function boxClass(error?: boolean) {
  return cx(
    "rounded-md bg-surface text-fg dark:bg-surface-2",
    error ? "border-2 border-danger" : "border-[1.5px] border-line",
  );
}
const FOCUS = "focus:border-2 focus:border-fg dark:focus:border-accent";
const FOCUS_WITHIN = "focus-within:border-2 focus-within:border-fg dark:focus-within:border-accent";

type FieldProps = {
  id: string;
  label: ReactNode;
  optional?: boolean;
  helper?: ReactNode;
  error?: string;
};

// Label above, helper or error below, linked for screen readers (aria-describedby)
function Field({ id, label, optional, helper, error, children }: FieldProps & { children: ReactNode }) {
  return (
    <div className="flex flex-col gap-2">
      <label htmlFor={id} className="font-semibold">
        {label}
        {optional && <span className="font-normal text-fg-muted"> (optional)</span>}
      </label>
      {children}
      {error ? (
        <p id={`${id}-note`} className="text-[0.9375rem] leading-[1.45] text-danger">⚠ {error}</p>
      ) : (
        helper && <p id={`${id}-note`} className="text-[0.9375rem] leading-[1.45] text-fg-muted">{helper}</p>
      )}
    </div>
  );
}

type TextFieldProps = FieldProps & Omit<InputHTMLAttributes<HTMLInputElement>, "id">;

export function TextField({ id, label, optional, helper, error, className, ...input }: TextFieldProps) {
  return (
    <Field id={id} label={label} optional={optional} helper={helper} error={error}>
      <input
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={error || helper ? `${id}-note` : undefined}
        {...input}
        className={cx(
          boxClass(!!error),
          !error && FOCUS,
          "h-cta w-full px-3.5 text-[1.1875rem] font-medium outline-none placeholder:text-fg-disabled focus-visible:shadow-none",
          className,
        )}
      />
    </Field>
  );
}

// "+91" segment, then the 10 digits shown grouped as "98100 43215". onChange gets digits only.
// Unused while login is by email; kept for phone login's return once SMS is set up (Learning Notes 88).
export function PhoneInput({ id, value, onChange, error, autoFocus }: {
  id: string;
  value: string;
  onChange: (digits: string) => void;
  error?: string;
  autoFocus?: boolean;
}) {
  const shown = value.length > 5 ? `${value.slice(0, 5)} ${value.slice(5)}` : value;
  return (
    <Field id={id} label={<>Phone number<span className="sr-only"> (India, +91)</span></>} error={error}>
      <div className={cx(boxClass(!!error), !error && FOCUS_WITHIN, "flex h-cta overflow-hidden")}>
        <span aria-hidden className="flex items-center border-r-[1.5px] border-line px-3.5 font-display text-lg font-bold text-fg-muted">
          +91
        </span>
        <input
          id={id}
          type="tel"
          inputMode="tel"
          autoComplete="tel-national"
          autoFocus={autoFocus}
          value={shown}
          // Autofill may bring "+91 98100 43215" or "098100…": keep the last 10 digits
          onChange={(e) => onChange(e.target.value.replace(/\D/g, "").slice(-10))}
          aria-invalid={error ? true : undefined}
          aria-describedby={error ? `${id}-note` : undefined}
          className="tnum min-w-0 flex-1 bg-transparent px-3.5 text-[1.1875rem] font-semibold tracking-[0.04em] outline-none focus-visible:shadow-none"
        />
      </div>
    </Field>
  );
}

// Six boxes driven by ONE real input (so SMS autofill, paste and screen readers all work), laid over them invisibly
export function OtpInput({ id, value, onChange, error, autoFocus }: {
  id: string;
  value: string;
  onChange: (digits: string) => void;
  error?: boolean;
  autoFocus?: boolean;
}) {
  const [focused, setFocused] = useState(false);
  const active = Math.min(value.length, 5);
  return (
    <div className="relative">
      <input
        id={id}
        aria-label="6-digit code"
        inputMode="numeric"
        autoComplete="one-time-code"
        maxLength={6}
        autoFocus={autoFocus}
        value={value}
        onChange={(e) => onChange(e.target.value.replace(/\D/g, "").slice(0, 6))}
        onFocus={() => setFocused(true)}
        onBlur={() => setFocused(false)}
        aria-invalid={error || undefined}
        className="absolute inset-0 z-10 size-full cursor-text opacity-0"
      />
      <div aria-hidden className="grid grid-cols-6 gap-2 lg:gap-2.5">
        {Array.from({ length: 6 }, (_, i) => {
          const isActive = focused && i === active && value.length < 6;
          return (
            <span
              key={i}
              className={cx(
                isActive && !error ? "rounded-md border-2 border-fg bg-surface dark:border-accent dark:bg-surface-2" : boxClass(error),
                "flex h-[3.75rem] items-center justify-center font-display text-[1.75rem] font-bold lg:h-16 lg:text-[1.875rem]",
              )}
            >
              {value[i] ?? (isActive ? <span className="h-[1.6rem] w-0.5 animate-pulse bg-fg dark:bg-accent" /> : null)}
            </span>
          );
        })}
      </div>
    </div>
  );
}
