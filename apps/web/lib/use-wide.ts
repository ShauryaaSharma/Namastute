import { useCallback, useSyncExternalStore } from "react";

// true on screens at least `px` wide (1024 = Tailwind's lg, 1280 = xl), updated live when the window is resized.
// For layouts CSS alone can't do, like moving the one map (or the one chat) between places.
export function useWide(px = 1024): boolean {
  return useMedia(`(min-width: ${px}px)`);
}

// Any media query, e.g. "(hover: hover) and (pointer: fine)" = a mouse or trackpad, so most likely a laptop
export function useMedia(query: string): boolean {
  const subscribe = useCallback((onChange: () => void) => {
    const list = matchMedia(query);
    list.addEventListener("change", onChange);
    return () => list.removeEventListener("change", onChange);
  }, [query]);
  return useSyncExternalStore(subscribe, () => matchMedia(query).matches, () => false);
}
