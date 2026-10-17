"use client";

import "leaflet/dist/leaflet.css";
import L from "leaflet";
import { useEffect, useRef } from "react";
import { addTiles, DOT_PIN, LABEL, pin } from "./map-parts";

export type MapDot = { name: string; lat: number; lng: number; accuracy: number; you: boolean };
export type MeetPoint = { lat: number; lng: number; label: string };

// The pickup: a "Meet here" pin at the terminal, and everyone sharing their location (Learning Notes 60): a dot each,
// with a circle for "probably within N metres". Leaflet needs the browser's `window`, so it's loaded with ssr: false.
export default function PickupMap({ meet, dots }: { meet: MeetPoint; dots: MapDot[] }) {
  const box = useRef<HTMLDivElement>(null);
  const map = useRef<L.Map | null>(null);
  const layer = useRef<L.LayerGroup | null>(null);
  const fitted = useRef(false);

  useEffect(() => {
    if (!box.current) return;
    const m = L.map(box.current, { zoomControl: false }).setView(meet, 16);
    L.control.zoom({ position: "topright" }).addTo(m);
    addTiles(m);
    layer.current = L.layerGroup().addTo(m);
    map.current = m;
    return () => {
      m.remove();
      map.current = null;
      fitted.current = false;
    };
  }, []); // eslint-disable-line react-hooks/exhaustive-deps -- the first view only; pins are redrawn below

  useEffect(() => {
    const m = map.current;
    const l = layer.current;
    if (!m || !l) return;
    l.clearLayers();
    L.marker(meet, { keyboard: false, icon: pin(DOT_PIN("block size-5 rounded-full border-4 border-bg bg-accent"), meet.label, `${LABEL} bg-bg text-fg`) })
      .addTo(l);
    for (const d of dots) {
      L.circle([d.lat, d.lng], { radius: d.accuracy, weight: 1, fillOpacity: 0.12, className: d.you ? "fill-fg stroke-fg" : "fill-info stroke-info" })
        .addTo(l);
      L.marker([d.lat, d.lng], {
        keyboard: false,
        icon: pin(DOT_PIN(`block size-3.5 rounded-full border-2 border-bg ${d.you ? "bg-fg" : "bg-info"}`), d.name, `${LABEL} bg-bg text-fg`),
      }).addTo(l);
    }
    // Fit the meeting point and everyone once, when the first dots arrive; after that the map stays where you put it
    if (dots.length > 0 && !fitted.current) {
      m.fitBounds(L.latLngBounds([meet, ...dots]), { padding: [40, 40], maxZoom: 18 });
      fitted.current = true;
    }
  }, [meet, dots]);

  return <div ref={box} className="z-0 size-full bg-surface!" role="img" aria-label={`Map of the pickup: ${meet.label}, and where your group is`} />;
}
