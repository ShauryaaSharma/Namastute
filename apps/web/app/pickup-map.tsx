"use client";

import "leaflet/dist/leaflet.css";
import L from "leaflet";
import { useEffect, useRef } from "react";

export type MapDot = { name: string; lat: number; lng: number; accuracy: number; you: boolean };

// Where everyone sharing is: a dot each, and a circle for "probably within N metres" (Learning Notes 60).
// Leaflet needs the browser's `window`, so the card loads this file with ssr: false.
export default function PickupMap({ dots }: { dots: MapDot[] }) {
  const box = useRef<HTMLDivElement>(null);
  const map = useRef<L.Map | null>(null);
  const layer = useRef<L.LayerGroup | null>(null);
  const centred = useRef(false);

  useEffect(() => {
    if (!box.current) return;
    const m = L.map(box.current);
    L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
      maxZoom: 19,
      attribution: "© OpenStreetMap contributors", // required by OpenStreetMap's licence
    }).addTo(m);
    layer.current = L.layerGroup().addTo(m);
    map.current = m;
    return () => {
      m.remove();
      map.current = null;
      centred.current = false;
    };
  }, []);

  useEffect(() => {
    const m = map.current;
    if (!m || !layer.current) return;
    layer.current.clearLayers();
    for (const d of dots) {
      const colour = d.you ? "#1d4ed8" : "#dc2626";
      L.circle([d.lat, d.lng], { radius: d.accuracy, color: colour, weight: 1, fillOpacity: 0.1 }).addTo(layer.current);
      L.circleMarker([d.lat, d.lng], { radius: 6, color: colour, fillOpacity: 1 })
        .bindTooltip(d.name, { permanent: true, direction: "top" })
        .addTo(layer.current);
    }
    // Centre once, on the first dot (you if you're sharing); after that the map stays where you put it
    const first = dots.find((d) => d.you) ?? dots[0];
    if (first && !centred.current) {
      m.setView([first.lat, first.lng], 17);
      centred.current = true;
    }
  }, [dots]);

  return <div ref={box} className="mt-2 h-56 w-full rounded border" role="img" aria-label="Map of where your group is" />;
}
