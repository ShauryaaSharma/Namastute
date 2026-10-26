"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState } from "react";
import { AppBar } from "@/components/app-bar";
import { restoreSession, type Profile } from "@/lib/api";
import CurrentTrip from "../current-trip";

// Home: your trip, people heading your way and requests. Logged out → log in; no name yet → profile first.
export default function Home() {
  const router = useRouter();
  const [me, setMe] = useState<Profile | null>(null);
  const [live, setLive] = useState<boolean | null>(null); // the group's live connection, lifted up for the top bar

  useEffect(() => {
    restoreSession()
      .then((profile) => {
        if (!profile) return router.replace("/login");
        if (!profile.name) return router.replace("/profile");
        setMe(profile);
      })
      .catch(() => router.replace("/login"));
  }, [router]);

  if (!me?.name) return <p role="status" className="p-6 text-fg-muted">Checking your session…</p>;
  return (
    <>
      <AppBar name={me.name} live={live} />
      <CurrentTrip onLive={setLive} />
    </>
  );
}
