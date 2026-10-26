// The only place the web app talks to the Namastute API (HTTP, and the chat WebSocket at the bottom).
// Holds the access token in memory, sends the refresh cookie, and refreshes once on a 401.
import { Client } from "@stomp/stompjs";
import type { components } from "./api-types";


const API_URL = process.env.NEXT_PUBLIC_API_URL ?? "http://localhost:8080";

// Response and request shapes are generated from the API's OpenAPI document (Learning Notes 52).
// Never edit api-types.ts by hand: change the Java record, run the API tests, then `pnpm api:types`.
type Schemas = components["schemas"];

export type Profile = Schemas["Profile"];
type TokenResponse = Schemas["TokenResponse"];

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
  if (typeof init.body === "string") headers.set("Content-Type", "application/json"); // FormData sets its own (with the boundary)
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

// Sends, refreshes the token once on a 401, and turns an error status into an ApiError
async function fetchOk(path: string, init: RequestInit = {}): Promise<Response> {
  let res = await send(path, init);
  if (res.status === 401 && !path.startsWith("/api/v1/auth/") && (await refreshSession())) {
    res = await send(path, init); // the access token had expired; try once more with the new one
  }
  if (!res.ok) throw await toError(res);
  return res;
}

async function request<T>(path: string, init: RequestInit = {}): Promise<T> {
  const res = await fetchOk(path, init);
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

// Where the login code goes: an email address today, a phone number again once SMS is set up (Learning Notes 88)
export type Destination = { email: string } | { phone: string };

export async function requestOtp(to: Destination): Promise<void> {
  await request<void>("/api/v1/auth/otp", { method: "POST", body: JSON.stringify(to) });
}

export async function verifyOtp(to: Destination, code: string): Promise<void> {
  const tokens = await request<TokenResponse>("/api/v1/auth/verify", {
    method: "POST",
    body: JSON.stringify({ ...to, code }),
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

export type Terminal = Schemas["TerminalView"];
export type NewTrip = Schemas["CreateTripRequest"]; // exitAt: ISO instant in UTC, see lib/time.ts
export type Trip = Schemas["TripView"];

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
    await stopPush().catch(() => {}); // never let this block logging out
    await request<void>("/api/v1/auth/logout", { method: "POST" });
  } finally {
    accessToken = null;
  }
}

// Someone already sharing in a match. Only what they may see: no phone, exact destination or UPI ID.
export type Rider = Schemas["Rider"];

// A lone traveller (1 rider) or an open group (2–3 riders) you could share a cab with
export type Match = Schemas["MatchView"];

// Best first, at most 5; only for your own active trip
export function getMatches(tripId: string): Promise<Match[]> {
  return request<Match[]>(`/api/v1/trips/${tripId}/matches`);
}

// ---- Sharing a cab: requests and groups ----

export type SentRequest = Schemas["SentView"];
export type ReceivedRequest = Schemas["RequestView"];
export type Requests = Schemas["Requests"];

export type GroupMember = Schemas["MemberView"];
export type Group = Schemas["GroupView"];
export type ShareView = Schemas["ShareView"];

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

// "I'm here" at the pickup point; 2+ members here → the group has met
export async function imHere(groupId: string): Promise<void> {
  await request<void>(`/api/v1/groups/${groupId}/here`, { method: "POST" });
}

// After the ride (Learning Notes 92): ends your own trip once the fare is in, so you can plan the next one
export async function finishRide(groupId: string): Promise<void> {
  await request<void>(`/api/v1/groups/${groupId}/done`, { method: "POST" });
}

// "I've paid": your own word, shown to the group. Namastute never sees payments.
export async function sayPaid(groupId: string): Promise<void> {
  await request<void>(`/api/v1/groups/${groupId}/paid`, { method: "POST" });
}

// Booker only: everyone's share for this fare, before confirming (nothing is saved)
export function previewFare(groupId: string, total: number): Promise<ShareView[]> {
  return request<ShareView[]>(`/api/v1/groups/${groupId}/fare/preview`, { method: "POST", body: JSON.stringify({ total }) });
}

// Booker only, once, after meeting up
export async function enterFare(groupId: string, total: number): Promise<void> {
  await request<void>(`/api/v1/groups/${groupId}/fare`, { method: "POST", body: JSON.stringify({ total }) });
}

// ---- Report and block (Learning Notes 56) ----

export type BlockView = Schemas["BlockView"];
export type ReportRequest = Schemas["ReportRequest"];

// Silent: the other person is never told; you just stop seeing each other
export async function blockTraveller(tripId: string): Promise<void> {
  await request<void>("/api/v1/blocks", { method: "POST", body: JSON.stringify({ tripId }) });
}

export function getBlocks(): Promise<BlockView[]> {
  return request<BlockView[]>("/api/v1/blocks");
}

export async function unblock(id: string): Promise<void> {
  await request<void>(`/api/v1/blocks/${id}`, { method: "DELETE" });
}

// Only someone you've shared a group with; one report per person per ride
export async function reportTraveller(report: ReportRequest): Promise<void> {
  await request<void>("/api/v1/reports", { method: "POST", body: JSON.stringify(report) });
}

export type Rating = Schemas["Rating"]; // also carries the "ID verified" badge (Learning Notes 80)
export type MyRating = Schemas["MyRating"];

// Thumbs up or down for someone from your met group: once, final (Learning Notes 62)
export async function rateTraveller(tripId: string, up: boolean): Promise<void> {
  await request<void>("/api/v1/ratings", { method: "POST", body: JSON.stringify({ tripId, up }) });
}

// Only the ratings you gave in this group
export function getMyRatings(groupId: string): Promise<MyRating[]> {
  return request<MyRating[]>(`/api/v1/groups/${groupId}/ratings`);
}

// A reason is required only when the group is locked
export async function leaveGroup(id: string, reason?: string): Promise<void> {
  await request<void>(`/api/v1/groups/${id}/leave`, { method: "POST", body: JSON.stringify({ reason }) });
}

// A token that's still valid for at least a minute (for the WebSocket, which can't refresh-and-retry like request())
export async function freshAccessToken(): Promise<string | null> {
  const exp = accessToken ? JSON.parse(atob(accessToken.split(".")[1].replace(/-/g, "+").replace(/_/g, "/"))).exp : 0;
  if (exp * 1000 - Date.now() < 60_000) await refreshSession();
  return accessToken;
}

// ---- Group chat (Learning Notes 45–46) ----

// HTTP history and the live STOMP copy are the same Java record, so one generated type covers both
export type ChatMessage = Schemas["MessageView"];

// Oldest first. before = the page older than that id; after = everything newer (catching up after a reconnect).
export function getMessages(groupId: string, cursor: { before?: number; after?: number } = {}): Promise<ChatMessage[]> {
  const q = cursor.after ? `?after=${cursor.after}` : cursor.before ? `?before=${cursor.before}` : "";
  return request<ChatMessage[]>(`/api/v1/groups/${groupId}/messages${q}`);
}

// A STOMP connection to the API that logs in, reconnects, and calls onConnect after every (re)connect
function stomp(onConnect: (client: Client) => void, onClose: () => void = () => {}): Client {
  const client = new Client({
    brokerURL: `${API_URL.replace(/^http/, "ws")}/ws`, // http → ws, https → wss
    reconnectDelay: 3000, // connections drop at airports: try again every 3 s
    // Runs before every (re)connect: the token may have expired while we were offline
    beforeConnect: async () => {
      client.connectHeaders = { Authorization: `Bearer ${await freshAccessToken()}` };
    },
    onConnect: () => onConnect(client),
    onWebSocketClose: onClose,
  });
  client.activate();
  return client;
}

// One live STOMP connection for a group: its chat and its "group changed" events. Call deactivate() to close it.
export function connectGroup(
  groupId: string,
  on: { message: (m: ChatMessage) => void; updated: () => void; connected: () => void; disconnected: () => void },
): Client {
  return stomp((client) => {
    client.subscribe(`/topic/groups.${groupId}.chat`, (frame) => on.message(JSON.parse(frame.body)));
    client.subscribe(`/topic/groups.${groupId}.updated`, () => on.updated());
    on.connected();
  }, on.disconnected);
}

export function sendChat(client: Client, groupId: string, body: string, clientId: string): void {
  client.publish({
    destination: `/app/groups/${groupId}/chat`,
    headers: { "content-type": "application/json" },
    body: JSON.stringify({ body, clientId }),
  });
}

// ---- ID verification (Learning Notes 78) ----

export type Verification = Schemas["VerificationView"];

// Where your check stands, and the gesture your next selfie must show
export function getVerification(): Promise<Verification> {
  return request<Verification>("/api/v1/verification");
}

// docType, consent, idPhoto and selfie in one multipart request
export function submitVerification(form: FormData): Promise<Verification> {
  return request<Verification>("/api/v1/verification", { method: "POST", body: form });
}

// ---- Reviewing ID checks: admins only (Learning Notes 80) ----

export type PendingCheck = Schemas["PendingView"];

export function getPendingChecks(): Promise<PendingCheck[]> {
  return request<PendingCheck[]>("/api/v1/admin/verifications");
}

// An <img> can't send our token, so the photo is fetched here and shown through a blob: URL
export async function getCheckPhoto(id: string, which: "id" | "selfie"): Promise<Blob> {
  return (await fetchOk(`/api/v1/admin/verifications/${id}/photos/${which}`)).blob();
}

export async function approveCheck(id: string, gender: "female" | "male" | "other", docLast4: string): Promise<void> {
  await request<void>(`/api/v1/admin/verifications/${id}/approve`, { method: "POST", body: JSON.stringify({ gender, docLast4 }) });
}

export async function rejectCheck(id: string, reason: string): Promise<void> {
  await request<void>(`/api/v1/admin/verifications/${id}/reject`, { method: "POST", body: JSON.stringify({ reason }) });
}

// ---- Live location at the pickup (Learning Notes 60) ----

// Sent only over STOMP, so it isn't in openapi.json: mirrors ChatService.Located in the API
export type Located = { tripId: string; sharing: boolean; lat: number | null; lng: number | null; accuracy: number | null };
export type Position = { lat: number; lng: number; accuracy: number };

// Its own connection, open only during the pickup window (the chat's connection stays as it is)
export function connectLocation(groupId: string, onLocated: (l: Located) => void): Client {
  return stomp((client) => {
    client.subscribe(`/topic/groups.${groupId}.location`, (frame) => onLocated(JSON.parse(frame.body)));
  });
}

// null = "I stopped sharing"
export function sendLocation(client: Client, groupId: string, p: Position | null): void {
  client.publish({
    destination: `/app/groups/${groupId}/location`,
    headers: { "content-type": "application/json" },
    body: JSON.stringify(p ? { lat: p.lat, lng: p.lng, accuracy: p.accuracy } : { lat: null }),
  });
}

// ---- Push notifications (Learning Notes 58) ----

// unsupported: this browser can't; install: iPhone in Safari, which can only after "Add to Home Screen";
// ask: not decided yet (show our soft-ask button); denied: blocked in the browser; on: allowed
export type PushState = "unsupported" | "install" | "ask" | "denied" | "on";

export function pushState(): PushState {
  if (!("serviceWorker" in navigator && "PushManager" in window && "Notification" in window)) {
    const iPhone = /iPhone|iPad|iPod/.test(navigator.userAgent);
    return iPhone && !window.matchMedia("(display-mode: standalone)").matches ? "install" : "unsupported";
  }
  return Notification.permission === "granted" ? "on" : Notification.permission === "denied" ? "denied" : "ask";
}

// After our soft-ask button: now the browser's own prompt
export async function enablePush(): Promise<PushState> {
  await Notification.requestPermission();
  await syncPush();
  return pushState();
}

// Makes sure this browser's subscription is saved under whoever is logged in now (run on every load once allowed)
// shortcut: a subscription made with an older VAPID key makes subscribe() fail; handle that if the keys are ever rotated
export async function syncPush(): Promise<void> {
  if (pushState() !== "on") return;
  await navigator.serviceWorker.register("/sw.js", { updateViaCache: "none" });
  const registration = await navigator.serviceWorker.ready;
  const { publicKey } = await request<Schemas["PublicKey"]>("/api/v1/push/public-key");
  const subscription =
    (await registration.pushManager.getSubscription()) ??
    (await registration.pushManager.subscribe({
      userVisibleOnly: true, // every push must show a notification: browsers don't allow silent ones
      applicationServerKey: Uint8Array.from(atob(publicKey.replace(/-/g, "+").replace(/_/g, "/")), (c) => c.charCodeAt(0)),
    }));
  await request<void>("/api/v1/push/subscriptions", { method: "POST", body: JSON.stringify(subscription.toJSON()) });
}

// On logout: a shared browser must not keep getting this person's notifications (58.6)
async function stopPush(): Promise<void> {
  const registration = "serviceWorker" in navigator ? await navigator.serviceWorker.getRegistration() : undefined;
  const subscription = await registration?.pushManager.getSubscription();
  if (subscription) {
    await request<void>(`/api/v1/push/subscriptions?endpoint=${encodeURIComponent(subscription.endpoint)}`, { method: "DELETE" });
  }
}
