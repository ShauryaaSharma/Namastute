"use client";

import Link from "next/link";
import { useEffect, useState } from "react";
import { logout, restoreSession, type Profile } from "@/lib/api";

// Shows who is logged in. undefined = still checking, null = logged out.
export default function SessionBox() {
  const [user, setUser] = useState<Profile | null | undefined>(undefined);

  useEffect(() => {
    restoreSession()
      .then(setUser)
      .catch(() => setUser(null));
  }, []);

  if (user === undefined) return <p className="mt-6">Checking your session…</p>;

  if (user === null) {
    return (
      <Link href="/login" className="mt-6 inline-block rounded bg-black px-4 py-2 text-white">
        Log in
      </Link>
    );
  }

  return (
    <div className="mt-6 flex flex-col gap-2">
      <p>
        Logged in as <strong>{user.name ?? user.phone}</strong>
      </p>
      <div className="flex gap-4">
        <Link href="/profile" className="underline">
          {user.name ? "Edit profile" : "Complete your profile"}
        </Link>
        <button onClick={() => logout().finally(() => setUser(null))} className="underline">
          Log out
        </button>
      </div>
    </div>
  );
}
