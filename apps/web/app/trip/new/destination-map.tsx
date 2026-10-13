"use client";

import "leaflet/dist/leaflet.css";
import L from "leaflet";
import { useEffect, useRef } from "react";

export type LatLng = { lat: number; lng: number };

type Props = {
  pickup: LatLng | null;
  destination: LatLng | null;
  onPick: (point: LatLng) => void;
};

// A Leaflet map: shows the terminal pickup (blue) and the destination (red); clicking the map moves the destination.
// Leaflet needs the browser's `window`, so the page loads this file with ssr: false.
export default function DestinationMap({ pickup, destination, onPick }: Props) {
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
    const m = L.map(box.current).setView([28.55, 77.1], 11);
    L.tileLayer("https://tile.openstreetmap.org/{z}/{x}/{y}.png", {
      maxZoom: 19,
      attribution: "© OpenStreetMap contributors", // required by OpenStreetMap's licence
    }).addTo(m);
    markers.current = L.layerGroup().addTo(m);
    m.on("click", (e: L.LeafletMouseEvent) => latestOnPick.current({ lat: e.latlng.lat, lng: e.latlng.lng }));
    map.current = m;
    return () => {
      m.remove();
      map.current = null;
    };
  }, []);

  // Redraw the two markers whenever they change
  useEffect(() => {
    const m = map.current;
    const layer = markers.current;
    if (!m || !layer) return;
    layer.clearLayers();
    if (pickup) {
      L.circleMarker([pickup.lat, pickup.lng], { radius: 8, color: "#1d4ed8" }).bindTooltip("Pickup").addTo(layer);
    }
    if (destination) {
      L.circleMarker([destination.lat, destination.lng], { radius: 8, color: "#dc2626" })
        .bindTooltip("Destination")
        .addTo(layer);
      m.panTo([destination.lat, destination.lng]);
    }
  }, [pickup, destination]);

  return (
    <div
      ref={box}
      className="h-72 w-full rounded border"
      role="application"
      aria-label="Map. Click to set your destination, or use the search box above."
    />
  );
}
