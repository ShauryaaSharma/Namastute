// Place search and "what area is this pin in?", using OpenStreetMap's Nominatim service.
// shortcut: Nominatim's usage policy allows about 1 request per second and no search-as-you-type. Fine for
// development and demos; switch to Ola Maps or Google (design open question 2) before real users. Only this file changes.

const NOMINATIM = "https://nominatim.openstreetmap.org";
const DELHI_NCR = "76.80,28.95,77.60,28.20"; // search box: left, top, right, bottom (longitude, latitude)

export type Place = { lat: number; lng: number; name: string; area: string };

type NominatimResult = { lat: string; lon: string; display_name: string; address?: Record<string, string> };

export async function searchPlaces(query: string): Promise<Place[]> {
  const params = new URLSearchParams({
    q: query,
    format: "jsonv2",
    addressdetails: "1",
    countrycodes: "in",
    viewbox: DELHI_NCR,
    bounded: "1", // only results inside Delhi NCR
    limit: "5",
    "accept-language": "en",
  });
  const res = await fetch(`${NOMINATIM}/search?${params}`);
  if (!res.ok) throw new Error(`Place search failed (${res.status})`);
  const results: NominatimResult[] = await res.json();
  return results.map((r) => ({ lat: Number(r.lat), lng: Number(r.lon), name: r.display_name, area: areaLabel(r) }));
}

// Reverse geocoding: coordinates → "Area, City"
export async function areaAt(lat: number, lng: number): Promise<string> {
  const params = new URLSearchParams({
    lat: String(lat),
    lon: String(lng),
    format: "jsonv2",
    zoom: "16", // neighbourhood level, not a street address
    addressdetails: "1",
    "accept-language": "en",
  });
  const res = await fetch(`${NOMINATIM}/reverse?${params}`);
  if (!res.ok) return "";
  return areaLabel(await res.json());
}

// "DLF Phase 3, Gurugram": an area plus the city, never a house number, because other travellers will see it
function areaLabel(r: NominatimResult): string {
  const a = r.address ?? {};
  const area = a.neighbourhood ?? a.suburb ?? a.quarter ?? a.residential ?? a.village;
  const city = a.city ?? a.town ?? a.state_district ?? a.county;
  const parts = [area, city].filter((part, i, all): part is string => !!part && all.indexOf(part) === i);
  return parts.length > 0 ? parts.join(", ") : r.display_name.split(",").slice(-4, -2).join(",").trim();
}
