// Airport times are always India time (IST, UTC+05:30), whatever time zone the user's phone is set to.
// The API stores and returns UTC instants (ISO strings ending in "Z"); conversion happens only here.

const IST_OFFSET_MINUTES = 330;

// "YYYY-MM-DDTHH:mm" in IST, `minutesFromNow` from now: the format <input type="datetime-local"> uses
export function istInputValue(minutesFromNow: number): string {
  const shifted = new Date(Date.now() + (minutesFromNow + IST_OFFSET_MINUTES) * 60_000);
  return shifted.toISOString().slice(0, 16); // the UTC digits of a time shifted by +5:30 are the IST wall-clock digits
}

// "2026-10-14T10:30" (meant as IST) → "2026-10-14T05:00:00.000Z"
export function istInputToIso(value: string): string {
  return new Date(`${value}:00+05:30`).toISOString();
}

// "2026-10-14T05:00:00Z" → "14 Oct 2026, 10:30 am"
export function formatIst(iso: string): string {
  return new Intl.DateTimeFormat("en-IN", { timeZone: "Asia/Kolkata", dateStyle: "medium", timeStyle: "short" }).format(
    new Date(iso),
  );
}

// "2026-10-14T06:37:00Z" → "12:07 pm" (India time)
export function formatIstTime(iso: string): string {
  return new Intl.DateTimeFormat("en-IN", { timeZone: "Asia/Kolkata", timeStyle: "short" }).format(new Date(iso));
}
