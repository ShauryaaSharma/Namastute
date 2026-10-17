"use client";

import { type ReactNode, useEffect, useId, useRef } from "react";
import { cx } from "./cx";

// ---- The sticky bottom bar on phones: the screen's one primary action, in the thumb zone.
// On wide screens it becomes a normal block at the bottom of its pane. ----
export function BottomBar({ children, bordered, className }: { children: ReactNode; bordered?: boolean; className?: string }) {
  return (
    <div
      className={cx(
        "sticky bottom-0 z-10 mt-auto flex flex-col gap-2.5 bg-bg px-5 pt-4 pb-[calc(1.75rem+env(safe-area-inset-bottom))]",
        bordered && "border-t border-line",
        "lg:static lg:mt-6 lg:border-0 lg:bg-transparent lg:p-0",
        className,
      )}
    >
      {children}
    </div>
  );
}

// ---- Sheet: a bottom sheet on phones, a centred dialog on wide screens. Built on <dialog>, which traps focus,
// makes the rest of the page inert and closes on Esc by itself. ----
export function Sheet({ open, onClose, title, children }: { open: boolean; onClose: () => void; title: ReactNode; children: ReactNode }) {
  const ref = useRef<HTMLDialogElement>(null);
  const titleId = useId();
  useEffect(() => {
    const dialog = ref.current;
    if (!dialog) return;
    if (open && !dialog.open) dialog.showModal();
    if (!open && dialog.open) dialog.close();
  }, [open]);
  return (
    <dialog
      ref={ref}
      onClose={onClose}
      aria-labelledby={titleId}
      className={cx(
        "mb-0 mt-auto w-full max-w-none rounded-t-sheet bg-surface px-5 pt-3 pb-[calc(1.75rem+env(safe-area-inset-bottom))] text-fg backdrop:bg-scrim",
        "animate-sheet-up lg:m-auto lg:w-[32.5rem] lg:animate-none lg:rounded-[24px] lg:p-7",
      )}
    >
      <span aria-hidden className="mx-auto mb-4 block h-[5px] w-10 rounded-full bg-line lg:hidden" />
      <h2 id={titleId} className="font-display text-h2 stretch-110">{title}</h2>
      {children}
    </dialog>
  );
}
