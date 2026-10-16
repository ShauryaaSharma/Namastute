// Arrival rush (Learning Notes 66): TRAVELLERS people land within 2 minutes at Terminal 3, plan a trip, then
// refresh their trip, requests and matches about once a minute (like the app's auto-refresh). About a third ask
// their best match to share, and half of those asked say yes, so groups form while everyone keeps refreshing.
import http from "k6/http";
import { check, sleep } from "k6";

const API = __ENV.API || "http://api:8080/api/v1";
const TRAVELLERS = Number(__ENV.TRAVELLERS || 100);
const tokens = JSON.parse(open("./tokens.json")).rush;
const WAVE = Date.now() + 2 * 3600e3; // everyone is outside within the same half hour, two hours from now
// Expected answers at a busy airport: someone was asked twice, or got grouped meanwhile
const conflictsAreFine = http.expectedStatuses({ min: 200, max: 299 }, 404, 409);

const AREAS = [
  ["DLF Phase 3, Gurugram", 28.4936, 77.093], ["Sector 29, Gurugram", 28.4683, 77.0628],
  ["Cyber City, Gurugram", 28.495, 77.0895], ["Sohna Road, Gurugram", 28.4089, 77.041],
  ["Dwarka, Delhi", 28.5921, 77.046], ["Janakpuri, Delhi", 28.6219, 77.0878],
  ["Vasant Kunj, Delhi", 28.5293, 77.1539], ["Saket, Delhi", 28.5245, 77.2066],
  ["Connaught Place, Delhi", 28.6315, 77.2167], ["Noida Sector 18", 28.5708, 77.326],
];

export const options = {
  scenarios: {
    rush: {
      executor: "ramping-vus",
      stages: [
        { duration: "2m", target: TRAVELLERS }, // the wave lands
        { duration: "3m", target: TRAVELLERS }, // everyone refreshing
        { duration: "20s", target: 0 },
      ],
      gracefulRampDown: "30s",
    },
  },
  // The pass line from the lesson: p95 under 500 ms per kind of request, under 1% errors
  thresholds: {
    http_req_failed: ["rate<0.01"],
    "http_req_duration{name:plan trip}": ["p(95)<500"],
    "http_req_duration{name:current trip}": ["p(95)<500"],
    "http_req_duration{name:requests}": ["p(95)<500"],
    "http_req_duration{name:matches}": ["p(95)<500"],
    "http_req_duration{name:ask}": ["p(95)<500"],
    "http_req_duration{name:accept}": ["p(95)<500"],
    "http_req_duration{name:group}": ["p(95)<500"],
  },
  summaryTrendStats: ["avg", "p(50)", "p(95)", "p(99)", "max"],
};

// Per virtual user (each VU is one traveller and keeps these between iterations)
let tripId = null;
let asked = false;

export default function () {
  const auth = { Authorization: `Bearer ${tokens[(__VU - 1) % tokens.length]}`, "Content-Type": "application/json" };
  const req = (name, extra = {}) => ({ headers: auth, tags: { name }, ...extra });

  if (!tripId) {
    const [area, lat, lng] = AREAS[__VU % AREAS.length];
    const res = http.post(`${API}/trips`, JSON.stringify({
      terminalId: "DEL-T3",
      exitAt: new Date(WAVE + Math.random() * 30 * 60e3).toISOString(),
      destLat: lat + (Math.random() - 0.5) * 0.02, // spread over about 2 km around the area
      destLng: lng + (Math.random() - 0.5) * 0.02,
      destArea: area,
      seats: 1,
      bags: 1,
      womenOnly: false,
    }), req("plan trip"));
    check(res, { "trip planned": (r) => r.status === 201 });
    if (res.status !== 201) {
      sleep(5);
      return;
    }
    tripId = res.json("id");
  }

  const trip = http.get(`${API}/trips/current`, req("current trip"));
  const requests = http.get(`${API}/requests`, req("requests"));
  const status = trip.status === 200 ? trip.json("status") : null;

  if (status === "searching") {
    const matches = http.get(`${API}/trips/${tripId}/matches`, req("matches"));
    check(matches, { "matches answered": (r) => r.status === 200 });
    if (!asked && matches.status === 200 && matches.json().length > 0 && Math.random() < 0.33) {
      asked = true;
      http.post(`${API}/requests`, JSON.stringify({ toTripId: matches.json()[0].tripId }),
        req("ask", { responseCallback: conflictsAreFine }));
    }
  } else if (status === "grouped") {
    http.get(`${API}/groups/current`, req("group"));
  }

  const received = requests.status === 200 ? requests.json("received") : [];
  if (received.length > 0 && Math.random() < 0.5) {
    http.post(`${API}/requests/${received[0].id}/accept`, null, req("accept", { responseCallback: conflictsAreFine }));
  }

  sleep(50 + Math.random() * 20); // think time: the app refreshes about once a minute
}
