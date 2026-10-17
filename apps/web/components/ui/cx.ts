// Joins class names, skipping empty ones: cx("a", busy && "b") → "a" or "a b"
export function cx(...parts: (string | false | null | undefined)[]): string {
  return parts.filter(Boolean).join(" ");
}
