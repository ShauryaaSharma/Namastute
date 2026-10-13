"use client";

import dynamic from "next/dynamic";
import { useRouter } from "next/navigation";
import { useEffect, useState, type FormEvent } from "react";
import { createTrip, getTerminals, restoreSession, type Terminal } from "@/lib/api";
import { areaAt, searchPlaces, type Place } from "@/lib/geocode";
import { istInputToIso, istInputValue } from "@/lib/time";
import { useAction } from "@/lib/use-action";
import type { LatLng } from "./destination-map";

// Load the map only in the browser (Leaflet uses `window`), with a placeholder while it downloads
const DestinationMap = dynamic(() => import("./destination-map"), {
  ssr: false,
  loading: () => <div className="h-72 w-full rounded border p-4">Loading map…</div>,
});

export default function NewTripPage() {
  const router = useRouter();
  const [terminals, setTerminals] = useState<Terminal[]>([]);
  const [terminalId, setTerminalId] = useState("DEL-T3");
  const [exitAt, setExitAt] = useState(""); // set after load: "now" must come from the browser, not the build
  const [flightNo, setFlightNo] = useState("");
  const [seats, setSeats] = useState(1);
  const [bags, setBags] = useState(1);
  const [destination, setDestination] = useState<LatLng | null>(null);
  const [destArea, setDestArea] = useState("");
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<Place[]>([]);
  const search = useAction();
  const submit = useAction();

  // Logged out → login; no name yet → profile first (others will see your name in a group)
  useEffect(() => {
    restoreSession()
      .then(async (me) => {
        if (!me) return router.replace("/login");
        if (!me.name) return router.replace("/profile");
        setExitAt(istInputValue(30)); // default: 30 minutes from now, IST
        setTerminals(await getTerminals());
      })
      .catch(() => router.replace("/login"));
  }, [router]);

  const pickup = terminals.find((t) => t.id === terminalId) ?? null;

  async function pickOnMap(point: LatLng) {
    setDestination(point);
    setResults([]);
    setDestArea("Finding the area…");
    setDestArea((await areaAt(point.lat, point.lng).catch(() => "")) || "");
  }

  function choose(place: Place) {
    setDestination({ lat: place.lat, lng: place.lng });
    setDestArea(place.area);
    setResults([]);
  }

  function runSearch() {
    if (query.trim().length < 3) return search.setError("Type at least 3 letters to search.");
    search.run(async () => {
      const found = await searchPlaces(query.trim());
      setResults(found);
      if (found.length === 0) search.setError("No places found in Delhi NCR. Try another name, or click the map.");
    });
  }

  function save(e: FormEvent) {
    e.preventDefault();
    if (!destination) return submit.setError("Choose your destination: search for it or click the map.");
    if (!destArea.trim()) return submit.setError("Give the destination area a name, like \"DLF Phase 3, Gurugram\".");
    submit.run(async () => {
      await createTrip({
        terminalId,
        flightNo: flightNo.trim(),
        exitAt: istInputToIso(exitAt),
        destLat: destination.lat,
        destLng: destination.lng,
        destArea: destArea.trim(),
        seats,
        bags,
        womenOnly: false,
      });
      router.replace("/");
    });
  }

  if (terminals.length === 0) return <p className="p-6">Loading…</p>;

  return (
    <main className="mx-auto w-full max-w-xl p-6">
      <h1 className="text-2xl font-bold">Plan your trip</h1>

      <form onSubmit={save} className="mt-6 flex flex-col gap-4">
        <div className="flex flex-col gap-1">
          <label htmlFor="terminal" className="font-medium">Arriving at</label>
          <select id="terminal" value={terminalId} onChange={(e) => setTerminalId(e.target.value)} className="rounded border px-3 py-2">
            {terminals.map((t) => (
              <option key={t.id} value={t.id}>{t.name}, Delhi (IGI)</option>
            ))}
          </select>
        </div>

        <div className="flex flex-col gap-1">
          <label htmlFor="exit" className="font-medium">When will you be outside? (India time)</label>
          <input
            id="exit"
            type="datetime-local"
            value={exitAt}
            min={istInputValue(0)}
            max={istInputValue(24 * 60)}
            onChange={(e) => setExitAt(e.target.value)}
            className="rounded border px-3 py-2"
            required
          />
        </div>

        <div className="flex flex-col gap-1">
          <label htmlFor="flight" className="font-medium">Flight number <span className="font-normal">(optional)</span></label>
          <input id="flight" value={flightNo} onChange={(e) => setFlightNo(e.target.value)} placeholder="AI 865" maxLength={8} className="rounded border px-3 py-2" />
        </div>

        <div className="flex gap-4">
          <div className="flex flex-1 flex-col gap-1">
            <label htmlFor="seats" className="font-medium">Seats</label>
            <select id="seats" value={seats} onChange={(e) => setSeats(Number(e.target.value))} className="rounded border px-3 py-2">
              {[1, 2, 3].map((n) => <option key={n} value={n}>{n}</option>)}
            </select>
          </div>
          <div className="flex flex-1 flex-col gap-1">
            <label htmlFor="bags" className="font-medium">Bags</label>
            <select id="bags" value={bags} onChange={(e) => setBags(Number(e.target.value))} className="rounded border px-3 py-2">
              {[0, 1, 2, 3, 4, 5, 6].map((n) => <option key={n} value={n}>{n}</option>)}
            </select>
          </div>
        </div>

        <fieldset className="flex flex-col gap-2">
          <legend className="font-medium">Where are you going?</legend>
          <div className="flex gap-2">
            <input
              aria-label="Search for a place in Delhi NCR"
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter") {
                  e.preventDefault(); // Enter here searches; it must not submit the whole trip form
                  runSearch();
                }
              }}
              placeholder="Search, e.g. DLF Phase 3"
              className="flex-1 rounded border px-3 py-2"
            />
            <button type="button" onClick={runSearch} disabled={search.busy} className="rounded border px-4 py-2 disabled:opacity-50">
              {search.busy ? "Searching…" : "Search"}
            </button>
          </div>
          {results.length > 0 && (
            <ul className="rounded border">
              {results.map((place) => (
                <li key={`${place.lat},${place.lng}`}>
                  <button type="button" onClick={() => choose(place)} className="w-full px-3 py-2 text-left hover:underline">
                    {place.name}
                  </button>
                </li>
              ))}
            </ul>
          )}
          <p role="alert" aria-live="polite" className="text-red-600">{search.error}</p>

          <DestinationMap pickup={pickup} destination={destination} onPick={pickOnMap} />

          <label htmlFor="area" className="font-medium">Destination area</label>
          <p id="area-help" className="text-sm">Other travellers only ever see this area, never your exact address.</p>
          <input
            id="area"
            value={destArea}
            onChange={(e) => setDestArea(e.target.value)}
            maxLength={120}
            aria-describedby="area-help"
            className="rounded border px-3 py-2"
          />
        </fieldset>

        <label className="flex items-center gap-2 opacity-60">
          <input type="checkbox" disabled /> Women-only group (needs ID verification, coming soon)
        </label>

        <button type="submit" disabled={submit.busy} className="rounded bg-black px-4 py-2 text-white disabled:opacity-50">
          {submit.busy ? "Saving…" : "Find co-travellers"}
        </button>
        <p role="alert" aria-live="polite" className="text-red-600">{submit.error}</p>
      </form>
    </main>
  );
}
