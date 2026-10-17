import { useCallback, useSyncExternalStore } from "react";

// true on screens at least `px` wide (1024 = Tailwind's lg, 1280 = xl), updated live when the window is resized.
// For layouts CSS alone can't do, like moving the one map (or the one chat) between places.
export function useWide(px = 1024): boolean {
  const query = `(min-width: ${px}px)`;
  const subscribe = useCallback((onChange: () => void) => {
    const list = matchMedia(query);
    list.addEventListener("change", onChange);
    return () => list.removeEventListener("change", onChange);
  }, [query]);
  return useSyncExternalStore(subscribe, () => matchMedia(query).matches, () => false);
}
