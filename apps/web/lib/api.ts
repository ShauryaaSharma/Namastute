// The only place the web app talks to the Namastute API.
// Holds the access token in memory, sends the refresh cookie, and refreshes once on a 401.

const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

export type Profile = { id: string; phone: string; name: string | null };
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
