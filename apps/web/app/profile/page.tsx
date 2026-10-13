"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState, type FormEvent } from "react";
import { restoreSession, updateMe, type Profile } from "@/lib/api";
import { useAction } from "@/lib/use-action";

// Same rules as the API (MeController), checked here first for instant, friendly messages
const NAME = /^\p{L}([\p{L}\p{M} .'-]*[\p{L}\p{M}.])?$/u;
const UPI_ID = /^[a-zA-Z0-9._-]{2,256}@[a-zA-Z]{2,64}$/;

export default function ProfilePage() {
  const router = useRouter();
  const [me, setMe] = useState<Profile | null>(null);
  const [name, setName] = useState("");
  const [upiId, setUpiId] = useState("");
  const { busy, error, setError, run } = useAction();

  // Load the current profile; not logged in → go to the login page
  useEffect(() => {
    restoreSession()
      .then((profile) => {
        if (!profile) return router.replace("/login");
        setMe(profile);
        setName(profile.name ?? "");
        setUpiId(profile.upiId ?? "");
      })
      .catch(() => router.replace("/login"));
  }, [router]);

  function save(e: FormEvent) {
    e.preventDefault();
    const cleanName = name.trim();
    const cleanUpi = upiId.trim();
    if (cleanName.length < 2 || cleanName.length > 60 || !NAME.test(cleanName)) {
      setError("Enter your name (2 to 60 letters; spaces, dots, apostrophes and hyphens are fine).");
      return;
    }
    if (cleanUpi && !UPI_ID.test(cleanUpi)) {
      setError("A UPI ID looks like yourname@bank, for example keshav@okaxis. Leave it empty to skip.");
      return;
    }
    run(async () => {
      await updateMe({ name: cleanName, upiId: cleanUpi }); // empty upiId removes it
      router.replace("/");
    });
  }

  if (!me) return <p className="p-6">Loading…</p>;

  return (
    <main className="mx-auto w-full max-w-sm p-6">
      <h1 className="text-2xl font-bold">{me.name ? "Your profile" : "Complete your profile"}</h1>
      <p className="mt-1 text-sm">Logged in as {me.phone}</p>

      <form onSubmit={save} className="mt-6 flex flex-col gap-3">
        <label htmlFor="name" className="font-medium">
          Name
        </label>
        <p id="name-help" className="text-sm">
          Shown to people in your cab group.
        </p>
        <input
          id="name"
          autoComplete="name"
          maxLength={60}
          value={name}
          onChange={(e) => setName(e.target.value)}
          aria-describedby="name-help"
          className="rounded border px-3 py-2"
          required
        />

        <label htmlFor="upi" className="mt-2 font-medium">
          UPI ID <span className="font-normal">(optional)</span>
        </label>
        <p id="upi-help" className="text-sm">
          Only shared with your group, so they can pay you their share if you book the cab.
        </p>
        <input
          id="upi"
          autoComplete="off"
          autoCapitalize="none"
          spellCheck={false}
          placeholder="yourname@bank"
          value={upiId}
          onChange={(e) => setUpiId(e.target.value)}
          aria-describedby="upi-help"
          className="rounded border px-3 py-2"
        />

        <button type="submit" disabled={busy} className="mt-2 rounded bg-black px-4 py-2 text-white disabled:opacity-50">
          {busy ? "Saving…" : "Save"}
        </button>
      </form>

      <p role="alert" aria-live="polite" className="mt-4 text-red-600">
        {error}
      </p>
    </main>
  );
}
