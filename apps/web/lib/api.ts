// The only place the web app talks to the Namastute API.
// Holds the access token in memory, sends the refresh cookie, and refreshes once on a 401.

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export type Profile = { id: string; phone: string; name: string | null; upiId: string | null };
type TokenResponse = { accessToken: string; expiresIn: number };

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
  ) {
    super(message);
  }
}

let accessToken: string | null = null; // memory only: never localStorage (see Learning Notes 24.6)
let refreshing: Promise<boolean> | null = null;

function send(path: string, init: RequestInit = {}): Promise<Response> {
  const headers = new Headers(init.headers);
  if (init.body) headers.set("Content-Type", "application/json");
  if (accessToken) headers.set("Authorization", `Bearer ${accessToken}`);
  return fetch(`${API_URL}${path}`, { ...init, headers, credentials: "include" });
}

async function toError(res: Response): Promise<ApiError> {
  // Spring Security's own 401s have an empty body, so fall back to a generic message
  const body = await res.json().catch(() => null);
  return new ApiError(
    res.status,
    body?.error?.code ?? `HTTP_${res.status}`,
    body?.error?.message ?? "Something went wrong. Please try again.",
  );
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  let res = await send(path, init);
  if (res.status === 401 && !path.startsWith("/api/v1/auth/") && (await refreshSession())) {
    res = await send(path, init); // the access token had expired; try once more with the new one
  }
  if (!res.ok) throw await toError(res);
  return (res.status === 204 ? undefined : await res.json()) as T;
}

// Gets a new access token using the refresh cookie. Only one refresh runs at a time, in this tab and across tabs:
// reusing a refresh token logs out every device, and React dev mode / several open tabs would otherwise race.
export function refreshSession(): Promise<boolean> {
  const current = refreshing ?? refreshWithLock().finally(() => (refreshing = null));
  refreshing = current;
  return current;
}

async function refreshWithLock(): Promise<boolean> {
  return navigator.locks.request("namastute-refresh", async () => {
    const res = await fetch(`${API_URL}/api/v1/auth/refresh`, { method: "POST", credentials: "include" });
    accessToken = res.ok ? ((await res.json()) as TokenResponse).accessToken : null;
    return res.ok;
  });
}

export async function requestOtp(phone: string): Promise<void> {
  await request<void>("/api/v1/auth/otp", { method: "POST", body: JSON.stringify({ phone }) });
}

export async function verifyOtp(phone: string, code: string): Promise<void> {
  const tokens = await request<TokenResponse>("/api/v1/auth/verify", {
    method: "POST",
    body: JSON.stringify({ phone, code }),
  });
  accessToken = tokens.accessToken;
}

export function getMe(): Promise<Profile> {
  return request<Profile>("/api/v1/me");
}

// Fields left out are not changed; upiId "" removes it
export function updateMe(changes: { name?: string; upiId?: string }): Promise<Profile> {
  return request<Profile>("/api/v1/me", { method: "PATCH", body: JSON.stringify(changes) });
}

export type Terminal = { id: string; name: string; lat: number; lng: number; pickupNote: string };

export type NewTrip = {
  terminalId: string;
  flightNo: string;
  exitAt: string; // ISO instant in UTC, see lib/time.ts
  destLat: number;
  destLng: number;
  destArea: string;
  seats: number;
  bags: number;
  womenOnly: boolean;
};

export type Trip = Omit<NewTrip, "flightNo"> & {
  id: string;
  terminalName: string;
  flightNo: string | null;
  status: "searching" | "grouped" | "cancelled" | "expired" | "done";
  soloKm: number;
};

export function getTerminals(): Promise<Terminal[]> {
  return request<Terminal[]>("/api/v1/terminals");
}

export function createTrip(trip: NewTrip): Promise<Trip> {
  return request<Trip>("/api/v1/trips", { method: "POST", body: JSON.stringify(trip) });
}

// null when you have no active trip (the API answers 204 No Content)
export async function getCurrentTrip(): Promise<Trip | null> {
  return (await request<Trip | undefined>("/api/v1/trips/current")) ?? null;
}

export async function cancelTrip(id: string): Promise<void> {
  await request<void>(`/api/v1/trips/${id}`, { method: "DELETE" });
}

// On page load: memory was wiped, so use the refresh cookie (if any) to log back in silently
export async function restoreSession(): Promise<Profile | null> {
  if (!accessToken && !(await refreshSession())) return null;
  return getMe();
}

export async function logout(): Promise<void> {
  try {
    await request<void>("/api/v1/auth/logout", { method: "POST" });
  } finally {
    accessToken = null;
  }
}

// Another traveller you could share a cab with. Only what they may see: no phone, exact destination or UPI ID.
export type Match = {
  tripId: string; // opaque handle, used only to ask this traveller to share
  firstName: string;
  destArea: string;
  minutesAfterYou: number; // negative = they're out before you
  seats: number;
  bags: number;
  detourKm: number; // rounded to 0.5 km
};

// Best first, at most 5; only for your own active trip
export function getMatches(tripId: string): Promise<Match[]> {
  return request<Match[]>(`/api/v1/trips/${tripId}/matches`);
}

// ---- Sharing a cab: requests and groups ----

export type SentRequest = { id: string; toTripId: string; firstName: string; status: "pending" | "declined" };
export type ReceivedRequest = {
  id: string;
  firstName: string;
  destArea: string;
  minutesAfterYou: number;
  seats: number;
  bags: number;
};
export type Requests = { sent: SentRequest[]; received: ReceivedRequest[] };

export type GroupMember = { firstName: string; destArea: string; seats: number; bags: number; booker: boolean; you: boolean };
export type Group = {
  id: string;
  status: "open" | "locked";
  youAreBooker: boolean;
  bookerUpiId: string | null;
  members: GroupMember[];
  updates: string[]; // newest first, e.g. "Asha left: booker not answering"
};

export async function askToShare(toTripId: string): Promise<void> {
  await request<{ id: string }>("/api/v1/requests", { method: "POST", body: JSON.stringify({ toTripId }) });
}

// Requests you sent, and requests you can answer (to you, or to anyone in your open group)
export function getRequests(): Promise<Requests> {
  return request<Requests>("/api/v1/requests");
}

export async function answerRequest(id: string, answer: "accept" | "decline"): Promise<void> {
  await request<void>(`/api/v1/requests/${id}/${answer}`, { method: "POST" });
}

// null when you're not in a group (the API answers 204)
export async function getCurrentGroup(): Promise<Group | null> {
  return (await request<Group | undefined>("/api/v1/groups/current")) ?? null;
}

export async function lockGroup(id: string): Promise<void> {
  await request<void>(`/api/v1/groups/${id}/lock`, { method: "POST" });
}

// A reason is required only when the group is locked
export async function leaveGroup(id: string, reason?: string): Promise<void> {
  await request<void>(`/api/v1/groups/${id}/leave`, { method: "POST", body: JSON.stringify({ reason }) });
}
