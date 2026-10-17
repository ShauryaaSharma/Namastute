// Shared by the two Leaflet maps (destination, pickup). Leaflet needs `window`: import only from ssr:false modules.
import L from "leaflet";

// OpenStreetMap tiles; dark mode darkens them with a CSS filter on .map-tiles (globals.css)
export function addTiles(map: L.Map) {
  L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
    maxZoom: 19,
    className: "map-tiles",
    attribution: "© OpenStreetMap contributors", // required by OpenStreetMap's licence
  }).addTo(map);
}

export const LABEL = "rounded-sm px-2 py-0.5 font-sans text-[0.8125rem] font-bold whitespace-nowrap";

// A pin made of our own HTML (a Leaflet divIcon). The label is set as text, never as HTML: names and areas are typed by users.
export function pin(html: string, label: string, labelClass: string) {
  const el = document.createElement("div");
  el.innerHTML = html;
  const slot = el.querySelector("[data-label]")!;
  slot.textContent = label;
  slot.className = labelClass;
  return L.divIcon({ html: el.firstElementChild as HTMLElement, className: "", iconSize: [0, 0] });
}

// A round dot with a label to its right, centred on the point (pickup, "Meet here", people sharing their location)
export const DOT_PIN = (dot: string) =>
  `<div class="flex w-max -translate-x-2.5 -translate-y-1/2 items-center gap-2"><span class="${dot}"></span><span data-label></span></div>`;
