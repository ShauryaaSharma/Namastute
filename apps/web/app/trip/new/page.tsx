"use client";

import dynamic from "next/dynamic";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { type FormEvent, useEffect, useMemo, useState } from "react";
import { AppBar } from "@/components/app-bar";
import { Button } from "@/components/ui/button";
import { Segmented, Stepper, Toggle } from "@/components/ui/controls";
import { cx } from "@/components/ui/cx";
import { Alert } from "@/components/ui/display";
import { boxClass, TextField } from "@/components/ui/field";
import { BottomBar } from "@/components/ui/layout";
import { createTrip, getTerminals, restoreSession, type Terminal } from "@/lib/api";
import { areaAt, searchPlaces, type Place } from "@/lib/geocode";
import { istDayName, istInputToIso, istInputValue, shortDate } from "@/lib/time";
import { useAction } from "@/lib/use-action";
import { useWide } from "@/lib/use-wide";
import type { LatLng } from "./destination-map";

// Load the map only in the browser (Leaflet uses `window`), with a placeholder while it downloads
const DestinationMap = dynamic(() => import("./destination-map"), {
  ssr: false,
  loading: () => <div className="size-full animate-pulse bg-surface-2" />,
});

const FOCUS = "outline-none focus-visible:shadow-none focus:border-2 focus:border-fg dark:focus:border-accent";
const short = (t: Terminal) => t.id.replace("DEL-", ""); // "DEL-T3" → "T3"

export default function NewTripPage() {
  const router = useRouter();
  const wide = useWide();
  const [name, setName] = useState("");
  const [terminals, setTerminals] = useState<Terminal[]>([]);
  const [terminalId, setTerminalId] = useState("DEL-T3");
  const [days, setDays] = useState<string[]>([]); // [today, tomorrow] in India; set after load: "now" must come from the browser
  const [day, setDay] = useState("");
  const [time, setTime] = useState("");
  const [flightNo, setFlightNo] = useState("");
  const [seats, setSeats] = useState(1);
  const [bags, setBags] = useState(1);
  const [womenOnly, setWomenOnly] = useState(false);
  const [womenOnlyAllowed, setWomenOnlyAllowed] = useState(false);
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
        const at = istInputValue(30); // default: 30 minutes from now, IST
        setDays([istInputValue(0).slice(0, 10), istInputValue(24 * 60).slice(0, 10)]);
        setDay(at.slice(0, 10));
        setTime(at.slice(11));
        setTerminals(await getTerminals());
        setName(me.name);
        setWomenOnlyAllowed(me.womenOnlyAllowed);
      })
      .catch(() => router.replace("/login"));
  }, [router]);

  const terminal = terminals.find((t) => t.id === terminalId);
  // Stable objects, so the map only redraws (and re-fits) when something really changed
  const pickupPin = useMemo(() => (terminal ? { lat: terminal.lat, lng: terminal.lng, label: short(terminal) } : null), [terminal]);
  const destinationPin = useMemo(() => (destination ? { ...destination, label: destArea } : null), [destination, destArea]);

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
    setQuery("");
  }

  function runSearch() {
    if (query.trim().length < 3) return search.setError("Type at least 3 letters to search.");
    search.run(async () => {
      const found = await searchPlaces(query.trim());
      setResults(found);
      if (found.length === 0) search.setError("No places found in Delhi NCR. Try another name, or tap the map.");
    });
  }

  function save(e: FormEvent) {
    e.preventDefault();
    if (!destination) return submit.setError("Choose where you're going: search for it or tap the map.");
    if (!destArea.trim()) return submit.setError("Give the destination area a name, like \"DLF Phase 3, Gurugram\".");
    if (!time) return submit.setError("Enter the time you'll be outside.");
    submit.run(async () => {
      await createTrip({
        terminalId,
        flightNo: flightNo.trim(),
        exitAt: istInputToIso(`${day}T${time}`),
        destLat: destination.lat,
        destLng: destination.lng,
        destArea: destArea.trim(),
        seats,
        bags,
        womenOnly,
      });
      router.replace("/home");
    });
  }

  if (terminals.length === 0) return <p role="status" className="p-6 text-fg-muted">Loading…</p>;

  const map = (
    <DestinationMap pickup={pickupPin} destination={destinationPin} onPick={pickOnMap} padBottom={wide ? 180 : 40} className="h-full" />
  );
  const area = <AreaCard area={destArea} onRename={setDestArea} chosen={destination !== null} floating={wide} />;

  return (
    <>
      <AppBar name={name} />
      <main className="mx-auto flex w-full max-w-[30rem] flex-col lg:grid lg:h-[calc(100dvh-4rem)] lg:max-w-none lg:grid-cols-[520px_minmax(0,1fr)]">
        <form onSubmit={save} className="flex flex-col lg:min-h-0 lg:border-r lg:border-line">
          <div className="flex flex-col gap-7 px-5 pt-3 pb-6 lg:min-h-0 lg:flex-1 lg:gap-[1.375rem] lg:overflow-y-auto lg:px-8 lg:pt-7">
            <h1 className="font-display text-h1 stretch-110 lg:text-[2.125rem] lg:leading-[1.05]">Plan your trip</h1>

            <div className="flex flex-col gap-7 lg:grid lg:grid-cols-2 lg:gap-4">
              <div className="flex flex-col gap-2">
                <span className="font-semibold">Terminal</span>
                <Segmented
                  label="Terminal"
                  value={terminalId}
                  onChange={setTerminalId}
                  options={terminals.map((t) => ({ value: t.id, label: short(t) }))}
                />
              </div>
              <TextField
                id="flight"
                label="Flight number"
                optional
                value={flightNo}
                onChange={(e) => setFlightNo(e.target.value.toUpperCase())}
                placeholder="6E 2134"
                maxLength={8}
                autoComplete="off"
                className="font-mono tracking-[0.04em]"
              />
            </div>

            <fieldset className="flex flex-col gap-2">
              <legend className="mb-2 font-semibold">
                When will you be outside? <span className="font-normal text-fg-muted">(India time)</span>
              </legend>
              <div className="grid grid-cols-[1fr_1.1fr] gap-2 lg:grid-cols-2">
                <select aria-label="Day" value={day} onChange={(e) => setDay(e.target.value)}
                        className={cx(boxClass(), FOCUS, "h-cta w-full px-3 text-[1.0625rem] font-medium")}>
                  {days.map((d) => <option key={d} value={d}>{istDayName(d)}, {shortDate(d)}</option>)}
                </select>
                <input type="time" aria-label="Time" required value={time} onChange={(e) => setTime(e.target.value)}
                       className={cx(boxClass(), FOCUS, "tnum h-cta w-full px-3 font-display text-[1.375rem] font-bold")} />
              </div>
            </fieldset>

            <div className="flex flex-col gap-3 lg:grid lg:grid-cols-2 lg:gap-4">
              <Stepper label="Seats" hint="1–3 people with you" value={seats} min={1} max={3} onChange={setSeats}
                       fewerLabel="Fewer seats" moreLabel="More seats" />
              <Stepper label="Bags" hint="0–6, big ones count" value={bags} min={0} max={6} onChange={setBags}
                       fewerLabel="Fewer bags" moreLabel="More bags" />
            </div>

            <div className="flex flex-col gap-2.5">
              <label htmlFor="where" className="font-semibold">Where to?</label>
              <div className="flex gap-2">
                <input
                  id="where"
                  value={query}
                  onChange={(e) => setQuery(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter") {
                      e.preventDefault(); // Enter here searches; it must not submit the whole trip form
                      runSearch();
                    }
                  }}
                  placeholder="Search, e.g. DLF Phase 3"
                  className={cx(boxClass(), FOCUS, "h-cta min-w-0 flex-1 px-3.5 text-[1.0625rem] font-medium placeholder:text-fg-disabled")}
                />
                <Button variant="tonal" size="cta" loading={search.busy} onClick={runSearch}>Search</Button>
              </div>
              {results.length > 0 && (
                <ul className="overflow-hidden rounded-md border border-line bg-surface">
                  {results.map((place) => (
                    <li key={`${place.lat},${place.lng}`} className="border-b border-line last:border-b-0">
                      <button type="button" onClick={() => choose(place)} className="min-h-tap w-full px-3.5 py-2 text-left hover:bg-surface-2">
                        {place.name}
                      </button>
                    </li>
                  ))}
                </ul>
              )}
              {search.error && <Alert variant="error">{search.error}</Alert>}
              {!wide && (
                <>
                  <div className="relative h-60 overflow-hidden rounded-lg border-[1.5px] border-line">
                    {map}
                    <span className="pointer-events-none absolute top-2.5 left-3 z-[450] rounded-sm bg-bg/80 px-1.5 text-small font-medium text-fg-muted">
                      Tap the map to move the pin
                    </span>
                  </div>
                  {area}
                </>
              )}
            </div>

            {/* The server checks again: this switch only saves a wasted tap (Learning Notes 84) */}
            <Toggle
              label="Women-only group"
              description={womenOnlyAllowed ? "Only women with a verified ID will see your trip" : (
                <>For women with a verified ID · <Link href="/verify" className="font-semibold underline underline-offset-4">Verify your ID</Link></>
              )}
              checked={womenOnly}
              onChange={setWomenOnly}
              unavailable={!womenOnlyAllowed}
            />
          </div>

          <BottomBar bordered pane>
            {submit.error && <Alert variant="error">{submit.error}</Alert>}
            <Button type="submit" loading={submit.busy} loadingLabel="Saving…">Find co-travellers →</Button>
          </BottomBar>
        </form>

        {wide && (
          <div className="relative min-h-0">
            {map}
            <span className="pointer-events-none absolute top-4 left-5 z-[450] rounded-sm bg-bg/80 px-1.5 text-small font-medium text-fg-muted">
              Click the map to drop a pin
            </span>
            <div className="absolute inset-x-6 bottom-6 z-[450]">{area}</div>
          </div>
        )}
      </main>
    </>
  );
}

// The area other travellers will see. Map lookups sometimes give odd names ("Unnamed Road"), so it can be renamed.
function AreaCard({ area, onRename, chosen, floating }: {
  area: string;
  onRename: (area: string) => void;
  chosen: boolean;
  floating: boolean; // laptops: a card floating over the full-height map
}) {
  const [editing, setEditing] = useState(false);
  return (
    <div className={cx(
      "flex flex-col gap-1",
      floating ? "rounded-lg border border-line bg-bg px-5 py-[1.125rem] xl:flex-row xl:items-center xl:justify-between xl:gap-5"
        : "rounded-[14px] bg-surface p-3.5",
    )}>
      <div className="flex min-w-0 flex-col gap-1">
        <span className="text-small font-semibold text-fg-muted">Destination area</span>
        {editing ? (
          <div className="flex gap-2">
            <input
              aria-label="Destination area name"
              autoFocus
              value={area}
              maxLength={120}
              onChange={(e) => onRename(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter") {
                  e.preventDefault();
                  setEditing(false);
                }
              }}
              className={cx(boxClass(), FOCUS, "h-tap min-w-0 flex-1 px-3 font-display text-[1.1875rem] font-bold")}
            />
            <Button variant="link" onClick={() => setEditing(false)}>Done</Button>
          </div>
        ) : (
          <span className="flex flex-wrap items-baseline gap-x-3">
            <b className={cx("font-display text-[1.1875rem]", floating && "lg:text-[1.375rem]", !chosen && "text-fg-muted")}>
              {chosen ? area || "Name this area" : "Search above, or tap the map"}
            </b>
            {chosen && (
              <button type="button" onClick={() => setEditing(true)} className="min-h-tap font-semibold text-accent-text underline underline-offset-4">
                Edit name
              </button>
            )}
          </span>
        )}
      </div>
      <p className={cx("text-[0.9375rem] leading-[1.45] text-fg-muted", floating && "xl:max-w-[22.5rem]")}>
        <span aria-hidden>🔒︎ </span>Other travellers only ever see this area, never your exact address.
      </p>
    </div>
  );
}
