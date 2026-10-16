// plural(1, "seat") → "1 seat", plural(2, "bag") → "2 bags", plural(0, "bag") → "0 bags"
export function plural(n: number, word: string): string {
  return `${n} ${word}${n === 1 ? "" : "s"}`;
}

// 301 → "₹301", 306.4 → "₹306.40" (Indian digit grouping: 1,00,000)
export function rupees(amount: number): string {
  const paise = !Number.isInteger(amount);
  return new Intl.NumberFormat("en-IN", {
    style: "currency",
    currency: "INR",
    minimumFractionDigits: paise ? 2 : 0,
    maximumFractionDigits: 2,
  }).format(amount);
}

// A ratings summary (Learning Notes 62): "92% 👍 · 12 ratings", or "New" until there are enough ratings to mean something
export function ratingText(r: { count: number; upPercent: number | null }): string {
  return r.upPercent === null ? "New" : `${r.upPercent}% 👍 · ${plural(r.count, "rating")}`;
}
