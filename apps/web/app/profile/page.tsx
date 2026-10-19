"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { type FormEvent, type ReactNode, useEffect, useState } from "react";
import { Button, buttonClass } from "@/components/ui/button";
import { Segmented } from "@/components/ui/controls";
import { Alert, Avatar, Card, Chip, Eyebrow } from "@/components/ui/display";
import { TextField } from "@/components/ui/field";
import { BottomBar } from "@/components/ui/layout";
import { type BlockView, getBlocks, getVerification, type Verification, getCurrentTrip, logout, restoreSession, type Profile, type Trip, unblock, updateMe } from "@/lib/api";
import { plural } from "@/lib/format";
import { setThemeChoice, type ThemeChoice, themeChoice } from "@/lib/theme";
import { useAction } from "@/lib/use-action";
import { AuthShell } from "../auth-shell";
import PushPrompt from "../push-prompt";

// Same rules as the API (MeController), checked here first for instant, friendly messages
const NAME = /^\p{L}([\p{L}\p{M} .'-]*[\p{L}\p{M}.])?$/u;
const UPI_ID = /^[a-zA-Z0-9._-]{2,256}@[a-zA-Z]{2,64}$/;

export default function ProfilePage() {
  const router = useRouter();
  const [me, setMe] = useState<Profile | null>(null);
  const [trip, setTrip] = useState<Trip | null>(null);
  const [name, setName] = useState("");
  const [upiId, setUpiId] = useState("");
  const [errors, setErrors] = useState<{ name?: string; upi?: string }>({});
  const { busy, error, run } = useAction();

  // Load the current profile; not logged in → go to the login page
  useEffect(() => {
    restoreSession()
      .then((profile) => {
        if (!profile) return router.replace("/login");
        setMe(profile);
        setName(profile.name ?? "");
        setUpiId(profile.upiId ?? "");
        if (profile.name) getCurrentTrip().then(setTrip).catch(() => {});
      })
      .catch(() => router.replace("/login"));
  }, [router]);

  function save(e: FormEvent) {
    e.preventDefault();
    const cleanName = name.trim();
    const cleanUpi = upiId.trim();
    const found = {
      name: cleanName.length < 2 || cleanName.length > 60 || !NAME.test(cleanName)
        ? "Enter your name: 2 to 60 letters, in Hindi or English." : undefined,
      upi: cleanUpi && !UPI_ID.test(cleanUpi) ? "Use the format yourname@bank" : undefined,
    };
    setErrors(found);
    if (found.name || found.upi) return;
    run(async () => {
      await updateMe({ name: cleanName, upiId: cleanUpi }); // empty upiId removes it
      router.replace("/");
    });
  }

  if (!me) {
    return <p role="status" className="p-6 text-fg-muted">Loading…</p>;
  }
  const firstTime = !me.name;
  const firstName = name.trim().split(/\s+/)[0];

  return (
    <AuthShell aside={<GroupPreview firstName={firstName} trip={trip} />}>
      <div className="flex flex-1 flex-col lg:flex-none">
        <div className="flex flex-col gap-6 px-5 pt-6 lg:p-0">
          {!firstTime && (
            <button type="button" onClick={() => router.push("/")} className="min-h-tap self-start pr-3 font-semibold">‹ Back</button>
          )}
          <div className="flex flex-col gap-2">
            {firstTime && <Eyebrow>Namaste · one last thing</Eyebrow>}
            <h1 className="font-display text-h1 stretch-110">{firstTime ? "Complete your profile" : "Your profile"}</h1>
          </div>
          <form id="profile" onSubmit={save} noValidate className="flex flex-col gap-6">
            <TextField
              id="name"
              label="Your first name"
              autoComplete="given-name"
              maxLength={60}
              value={name}
              onChange={(e) => { setName(e.target.value); setErrors({ ...errors, name: undefined }); }}
              helper="Hindi or English. Co-travellers see only this and your area."
              error={errors.name}
            />
            <TextField
              id="upi"
              label="UPI ID"
              optional
              autoComplete="off"
              autoCapitalize="none"
              spellCheck={false}
              placeholder="yourname@bank"
              value={upiId}
              onChange={(e) => { setUpiId(e.target.value); setErrors({ ...errors, upi: undefined }); }}
              helper="Only shared with your group, so they can pay you their share if you book the cab."
              error={errors.upi}
            />
            {error && <Alert variant="error">{error}</Alert>}
          </form>

          {!firstTime && (
            <>
              <Section title="ID verification"><IdStatus /></Section>
              <Section title="Notifications"><PushPrompt /></Section>
              <Section title="Appearance"><ThemePicker /></Section>
              <Section title="Blocked people"><BlockedPeople /></Section>
              <Button
                variant="secondary"
                onClick={() => logout().finally(() => router.replace("/login"))}
                className="self-start"
              >
                Log out
              </Button>
            </>
          )}
        </div>
        <BottomBar>
          <Button type="submit" form="profile" loading={busy} loadingLabel="Saving…" className="w-full">
            {firstTime ? "Save and continue" : "Save"}
          </Button>
        </BottomBar>
      </div>
    </AuthShell>
  );
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section aria-label={title} className="flex flex-col gap-3 border-t border-line pt-6">
      <h2 className="font-display text-h3 stretch-110">{title}</h2>
      {children}
    </section>
  );
}

// Wide screens (W3): what other travellers will see of you, updating as you type
function GroupPreview({ firstName, trip }: { firstName: string; trip: Trip | null }) {
  const details = trip
    ? `${trip.destArea} · ${plural(trip.seats, "seat")} · ${plural(trip.bags, "bag")}`
    : "Your area · 1 seat · 1 bag";
  return (
    <div className="flex flex-col gap-5">
      <Eyebrow className="text-[0.9375rem]">What your group will see</Eyebrow>
      <Card className="flex w-[26.25rem] max-w-full flex-col gap-3">
        <div className="flex items-center gap-3">
          <Avatar name={firstName || "?"} you size={52} />
          <div className="flex min-w-0 flex-col">
            <b className="truncate text-[1.375rem]">{firstName || "Your first name"}</b>
            <span className="text-[0.9375rem] text-fg-muted">{details}</span>
          </div>
        </div>
        <div className="flex flex-wrap gap-1.5">
          <Chip variant="muted">No phone number</Chip>
          <Chip variant="muted">No surname</Chip>
          <Chip variant="muted">No exact address</Chip>
        </div>
      </Card>
    </div>
  );
}

// Where your ID check stands, and the way to /verify (Learning Notes 78)
function IdStatus() {
  const [v, setV] = useState<Verification | null>(null);
  useEffect(() => {
    getVerification().then(setV).catch(() => {});
  }, []);
  if (!v) return null;
  if (v.status === "verified") return <Chip variant="info" className="self-start">◆ ID verified</Chip>;
  if (v.status === "pending") return <p className="text-fg-muted">Under review. We&apos;ll let you know, usually within a day.</p>;
  return (
    <>
      <p className="text-fg-muted">
        {v.status === "rejected" ? `Not approved: ${v.rejectReason}` : "Get the ID verified badge, and women-only groups for women."}
      </p>
      <Link href="/verify" className={buttonClass({ variant: "secondary", className: "self-start" })}>
        {v.status === "rejected" ? "Try again" : "Verify your ID"}
      </Link>
    </>
  );
}

// Dark, light, or follow the phone. Remembered on this device (lib/theme.ts).
function ThemePicker() {
  const [choice, setChoice] = useState<ThemeChoice | null>(null);
  useEffect(() => setChoice(themeChoice()), []); // eslint-disable-line react-hooks/set-state-in-effect -- browser-only
  if (!choice) return null;
  return (
    <Segmented
      label="Theme"
      value={choice}
      onChange={(c) => { setChoice(c); setThemeChoice(c); }}
      options={[
        { value: "system", label: "Phone" },
        { value: "dark", label: "Dark" },
        { value: "light", label: "Light" },
      ]}
    />
  );
}

// People you blocked (Learning Notes 56.2): only you see this list; unblocking makes you visible to each other again
function BlockedPeople() {
  const [blocks, setBlocks] = useState<BlockView[] | null>(null);
  const { busy, error, run } = useAction();

  useEffect(() => {
    getBlocks()
      .then(setBlocks)
      .catch(() => setBlocks([]));
  }, []);

  if (!blocks) return null;
  if (blocks.length === 0) return <p className="text-fg-muted">You haven&apos;t blocked anyone.</p>;
  return (
    <>
      <ul className="flex flex-col">
        {blocks.map((b) => (
          <li key={b.id} className="flex min-h-14 items-center gap-3 border-b border-line py-2 last:border-0">
            <Avatar name={b.firstName} size={40} />
            <span className="flex-1 font-bold">{b.firstName}</span>
            <Button
              variant="tonal"
              disabled={busy}
              aria-label={`Unblock ${b.firstName}`}
              onClick={() => run(async () => { await unblock(b.id); setBlocks(blocks.filter((x) => x.id !== b.id)); })}
            >
              Unblock
            </Button>
          </li>
        ))}
      </ul>
      {error && <Alert variant="error">{error}</Alert>}
    </>
  );
}
