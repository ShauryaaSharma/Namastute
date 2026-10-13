"use client";

import { useRouter } from "next/navigation";
import { useEffect, useState, type FormEvent } from "react";
import { ApiError, requestOtp, verifyOtp } from "@/lib/api";

const RESEND_SECONDS = 30; // matches the API's resend cooldown

export default function LoginPage() {
  const router = useRouter();
  const [step, setStep] = useState<"phone" | "code">("phone");
  const [digits, setDigits] = useState(""); // the 10 digits after +91
  const [code, setCode] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);
  const [resendIn, setResendIn] = useState(0);

  const phone = `+91${digits}`;

  // Count the resend timer down by one each second until it reaches 0
  useEffect(() => {
    if (resendIn <= 0) return;
    const timer = setTimeout(() => setResendIn(resendIn - 1), 1000);
    return () => clearTimeout(timer);
  }, [resendIn]);

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

  function sendCode(e?: FormEvent) {
    e?.preventDefault();
    if (!/^[6-9]\d{9}$/.test(digits)) {
      setError("Enter a 10-digit Indian mobile number.");
      return;
    }
    run(async () => {
      await requestOtp(phone);
      setStep("code");
      setCode("");
      setResendIn(RESEND_SECONDS);
    });
  }

  function checkCode(e: FormEvent) {
    e.preventDefault();
    run(async () => {
      await verifyOtp(phone, code);
      router.replace("/");
    });
  }

  return (
    <main className="mx-auto w-full max-w-sm p-6">
      <h1 className="text-2xl font-bold">Log in</h1>

      {step === "phone" ? (
        <form onSubmit={sendCode} className="mt-6 flex flex-col gap-3">
          <label htmlFor="phone" className="font-medium">
            Mobile number
          </label>
          <div className="flex items-center gap-2">
            <span>+91</span>
            <input
              id="phone"
              type="tel"
              inputMode="numeric"
              autoComplete="tel-national"
              maxLength={10}
              value={digits}
              onChange={(e) => setDigits(e.target.value.replace(/\D/g, ""))}
              className="flex-1 rounded border px-3 py-2"
              required
            />
          </div>
          <button type="submit" disabled={busy} className="rounded bg-black px-4 py-2 text-white disabled:opacity-50">
            {busy ? "Sending…" : "Send code"}
          </button>
        </form>
      ) : (
        <form onSubmit={checkCode} className="mt-6 flex flex-col gap-3">
          <p>
            We sent a 6-digit code to <strong>{phone}</strong>.{" "}
            <button type="button" onClick={() => setStep("phone")} className="underline">
              Change
            </button>
          </p>
          <label htmlFor="code" className="font-medium">
            Code
          </label>
          <input
            id="code"
            inputMode="numeric"
            autoComplete="one-time-code"
            maxLength={6}
            value={code}
            onChange={(e) => setCode(e.target.value.replace(/\D/g, ""))}
            className="rounded border px-3 py-2 tracking-widest"
            required
          />
          <button
            type="submit"
            disabled={busy || code.length !== 6}
            className="rounded bg-black px-4 py-2 text-white disabled:opacity-50"
          >
            {busy ? "Checking…" : "Log in"}
          </button>
          <button type="button" onClick={() => sendCode()} disabled={busy || resendIn > 0} className="underline disabled:no-underline disabled:opacity-50">
            {resendIn > 0 ? `Resend code in ${resendIn}s` : "Resend code"}
          </button>
        </form>
      )}

      <p role="alert" aria-live="polite" className="mt-4 text-red-600">
        {error}
      </p>
    </main>
  );
}
