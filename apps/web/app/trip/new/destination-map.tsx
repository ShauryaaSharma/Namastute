"use client";

import "leaflet/dist/leaflet.css";
import L from "leaflet";
import { useEffect, useRef } from "react";
import { cx } from "@/components/ui/cx";

export type LatLng = { lat: number; lng: number };

type Props = {
  pickup: (LatLng & { label: string }) | null;
  destination: (LatLng & { label: string }) | null;
  onPick: (point: LatLng) => void;
  padBottom?: number; // pixels hidden under a floating card, kept clear when fitting both pins
  className?: string;
};

// A pin made of our own HTML (a Leaflet divIcon). The label is set as text, never as HTML: it can be typed by the user.
function pin(html: string, label: string, labelClass: string) {
  const el = document.createElement("div");
  el.innerHTML = html;
  el.querySelector("[data-label]")!.textContent = label;
  el.querySelector("[data-label]")!.className = labelClass;
  return L.divIcon({ html: el.firstElementChild as HTMLElement, className: "", iconSize: [0, 0] });
}
const LABEL = "rounded-sm px-2 py-0.5 font-sans text-[0.8125rem] font-bold whitespace-nowrap";

// A Leaflet map: the terminal pickup (ink dot) and the destination (orange pin) joined by a dashed line;
// clicking the map moves the destination. Dark mode darkens the map tiles with a CSS filter (globals.css).
// Leaflet needs the browser's `window`, so the page loads this file with ssr: false.
export default function DestinationMap({ pickup, destination, onPick, padBottom = 40, className }: Props) {
  const box = useRef<HTMLDivElement>(null);
  const map = useRef<L.Map | null>(null);
  const markers = useRef<L.LayerGroup | null>(null);
  const latestOnPick = useRef(onPick);

  // Keep the click handler pointing at the newest onPick without recreating the map
  useEffect(() => {
    latestOnPick.current = onPick;
  });

  // Create the map once; remove it when the component goes away
  useEffect(() => {
    if (!box.current) return;
    const m = L.map(box.current, { zoomControl: false }).setView([28.55, 77.1], 11);
    L.control.zoom({ position: "topright" }).addTo(m);
    L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
      maxZoom: 19,
      className: "map-tiles",
      attribution: "© OpenStreetMap contributors", // required by OpenStreetMap's licence
    }).addTo(m);
    markers.current = L.layerGroup().addTo(m);
    m.on("click", (e: L.LeafletMouseEvent) => latestOnPick.current({ lat: e.latlng.lat, lng: e.latlng.lng }));
    const resized = new ResizeObserver(() => m.invalidateSize()); // the pane can change size after the map is drawn
    resized.observe(box.current);
    map.current = m;
    return () => {
      resized.disconnect();
      m.remove();
      map.current = null;
    };
  }, []);

  // Redraw the pins whenever they change, and fit both into view
  useEffect(() => {
    const m = map.current;
    const layer = markers.current;
    if (!m || !layer) return;
    layer.clearLayers();
    if (pickup) {
      L.marker(pickup, {
        keyboard: false,
        icon: pin(
          '<div class="flex w-max -translate-x-2.5 -translate-y-1/2 items-center gap-2"><span class="size-5 rounded-full border-4 border-bg bg-fg ring-2 ring-fg"></span><span data-label></span></div>',
          `Pickup · ${pickup.label}`,
          cx(LABEL, "bg-bg text-fg"),
        ),
      }).addTo(layer);
    }
    if (destination) {
      L.marker(destination, {
        keyboard: false,
        icon: pin(
          '<div class="flex w-max -translate-x-1/2 -translate-y-[calc(100%-0.25rem)] flex-col items-center gap-1"><span data-label></span><span class="size-6 -rotate-45 rounded-[50%_50%_50%_0] border-[3px] border-bg bg-accent"></span></div>',
          destination.label || "Destination",
          cx(LABEL, "bg-accent text-on-accent"),
        ),
      }).addTo(layer);
    }
    if (pickup && destination) {
      L.polyline([pickup, destination], { className: "stroke-accent", weight: 2, dashArray: "6 8", interactive: false }).addTo(layer);
      m.fitBounds(L.latLngBounds([pickup, destination]), { paddingTopLeft: [40, 56], paddingBottomRight: [64, padBottom] });
    } else if (pickup) {
      m.setView(pickup, 12);
    }
  }, [pickup, destination, padBottom]);

  return (
    <div
      ref={box}
      className={cx("z-0 w-full bg-surface!", className)}
      role="application"
      aria-label="Map. Click to set your destination, or use the search box."
    />
  );
}
