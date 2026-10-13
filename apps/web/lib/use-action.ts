"use client";

import { useState } from "react";
import { ApiError } from "./api";

// Shared form behaviour: show "busy" while an action runs, and its error message if it fails
export function useAction() {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");

  async function run(action: () => Promise<void>) {
    setBusy(true);
    setError("");
    try {
      await action();
    } catch (e) {
      setError(e instanceof ApiError ? e.message : "Can't reach Namastute. Check your connection and try again.");
    } finally {
      setBusy(false);
    }
  }

  return { busy, error, setError, run };
}
