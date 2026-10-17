import { useSyncExternalStore } from "react";

const WIDE = "(min-width: 1024px)"; // Tailwind's lg: the width where screens switch to panes

// true on laptop-sized screens, updated live when the window is resized. For layouts CSS alone can't do,
// like moving the one map between the form (phones) and its own pane (laptops).
export function useWide(): boolean {
  return useSyncExternalStore(
    (onChange) => {
      const query = matchMedia(WIDE);
      query.addEventListener("change", onChange);
      return () => query.removeEventListener("change", onChange);
    },
    () => matchMedia(WIDE).matches,
    () => false,
  );
}
