// Pickup chatter (Learning Notes 66): GROUPS locked groups of four at the pickup, each person on a real WebSocket,
// sharing their location every 15 s and chatting about every 30-40 s. Chat bodies carry the send time, so every
// member measures how long a message took to reach them (chat_delivery).
import ws from "k6/ws";
import { check } from "k6";
import { Counter, Trend } from "k6/metrics";

const WS = __ENV.WS || "ws://api:8080/ws";
const ORIGIN = __ENV.ORIGIN || "https://localhost"; // the only origin the API's WebSocket accepts in this setup
const GROUPS = Number(__ENV.GROUPS || 25);
const members = JSON.parse(open("./tokens.json")).chatter; // four in a row share a group
const delivery = new Trend("chat_delivery", true);
const stompErrors = new Counter("stomp_errors");

export const options = {
  scenarios: {
    chatter: {
      executor: "ramping-vus",
      stages: [
        { duration: "1m", target: GROUPS * 4 }, // people reach the pickup and open the app
        { duration: "4m", target: GROUPS * 4 },
        { duration: "10s", target: 0 },
      ],
      gracefulRampDown: "20s",
    },
  },
  thresholds: {
    chat_delivery: ["p(95)<500"],
    ws_connecting: ["p(95)<1000"],
    stomp_errors: ["count==0"],
    checks: ["rate>0.99"],
  },
  summaryTrendStats: ["avg", "p(50)", "p(95)", "p(99)", "max"],
};

// A STOMP frame: command, headers, blank line, body, NUL (Learning Notes 45)
const frame = (command, headers, body = "") =>
  `${command}\n${Object.entries(headers).map(([k, v]) => `${k}:${v}`).join("\n")}\n\n${body}\0`;

export default function () {
  const me = members[(__VU - 1) % members.length];
  const res = ws.connect(WS, { headers: { Origin: ORIGIN } }, (socket) => {
    socket.on("open", () => {
      socket.send(frame("CONNECT", { "accept-version": "1.2", host: "localhost", Authorization: `Bearer ${me.token}` }));
    });
    socket.on("message", (data) => {
      if (data.startsWith("CONNECTED")) {
        socket.send(frame("SUBSCRIBE", { id: "chat", destination: `/topic/groups.${me.groupId}.chat` }));
        socket.send(frame("SUBSCRIBE", { id: "location", destination: `/topic/groups.${me.groupId}.location` }));
        const lat = 28.5562 + Math.random() * 0.002;
        const lng = 77.087 + Math.random() * 0.002;
        socket.setInterval(() => socket.send(frame("SEND",
          { destination: `/app/groups/${me.groupId}/location`, "content-type": "application/json" },
          JSON.stringify({ lat, lng, accuracy: 30 }))), 15000);
        socket.setInterval(() => socket.send(frame("SEND",
          { destination: `/app/groups/${me.groupId}/chat`, "content-type": "application/json" },
          JSON.stringify({ body: String(Date.now()), clientId: `k6-${__VU}-${Date.now()}` }))), 30000 + Math.random() * 10000);
      } else if (data.startsWith("MESSAGE") && data.includes(".chat\n")) {
        const body = JSON.parse(data.slice(data.indexOf("\n\n") + 2).replace(/\0$/, ""));
        const sentAt = Number(body.body); // system messages ("Keshav joined") aren't numbers: skipped
        if (sentAt) delivery.add(Date.now() - sentAt);
      } else if (data.startsWith("ERROR")) {
        stompErrors.add(1);
      }
    });
    socket.setTimeout(() => socket.close(), 4 * 60e3); // then reconnect, as phones do
  });
  check(res, { "WebSocket opened (101)": (r) => r && r.status === 101 });
}
