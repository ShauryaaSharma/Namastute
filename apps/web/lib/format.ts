// plural(1, "seat") → "1 seat", plural(2, "bag") → "2 bags", plural(0, "bag") → "0 bags"
export function plural(n: number, word: string): string {
  return `${n} ${word}${n === 1 ? "" : "s"}`;
}
