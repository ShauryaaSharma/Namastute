"use client";

import { useRouter } from "next/navigation";
import { type FormEvent, useEffect, useState } from "react";
import { Lockup } from "@/components/ui/brand";
import { Button } from "@/components/ui/button";
import { Alert } from "@/components/ui/display";
import { OtpInput, TextField } from "@/components/ui/field";
import { BottomBar } from "@/components/ui/layout";
import { getMe, requestOtp, verifyOtp } from "@/lib/api";
import { useAction } from "@/lib/use-action";
import { AuthShell, Headline, LoginAside, PITCH } from "../auth-shell";

const RESEND_SECONDS = 30; // matches the API's resend cooldown
const NOT_AN_EMAIL = "Enter your email address, like you@example.com.";
const EMAIL = /^[^@\s]+@[^@\s]+\.[^@\s]+$/; // same check as the API; the code proves the rest

export default function LoginPage() {
  const router = useRouter();
  const [step, setStep] = useState<"email" | "code">("email");
  const [typed, setTyped] = useState("");
  const [code, setCode] = useState("");
  const [emailError, setEmailError] = useState("");
  const [resendIn, setResendIn] = useState(0);
  const { busy, error, setError, run } = useAction();

  const email = typed.trim();

  // Count the resend timer down by one each second until it reaches 0
  useEffect(() => {
    if (resendIn <= 0) return;
    const timer = setTimeout(() => setResendIn(resendIn - 1), 1000);
    return () => clearTimeout(timer);
  }, [resendIn]);

  function sendCode(e?: FormEvent) {
    e?.preventDefault();
    if (!EMAIL.test(email)) {
      setEmailError(NOT_AN_EMAIL);
      return;
    }
    setEmailError("");
    run(async () => {
      await requestOtp({ email });
      setStep("code");
      setCode("");
      setResendIn(RESEND_SECONDS);
    });
  }

  function verify(value: string) {
    if (value.length !== 6 || busy) return;
    run(async () => {
      await verifyOtp({ email }, value);
      const me = await getMe();
      router.replace(me.name ? "/home" : "/profile"); // first login: ask for a name before anything else
    });
  }

  // Typing (or SMS autofill) the 6th digit submits by itself
  function onCode(value: string) {
    setCode(value);
    if (value.length === 6) verify(value);
  }

  // Wide screens: paste the code anywhere on the page and it fills in by itself
  useEffect(() => {
    if (step !== "code") return;
    function onPaste(e: ClipboardEvent) {
      if (e.target instanceof HTMLInputElement) return; // the code box handles its own paste
      const found = e.clipboardData?.getData("text").match(/\d{6}/)?.[0];
      if (found) onCode(found);
    }
    document.addEventListener("paste", onPaste);
    return () => document.removeEventListener("paste", onPaste);
  });

  function change() {
    setStep("email"); // the address stays filled in
    setError("");
  }

  return (
    <AuthShell aside={<LoginAside withTrust={step === "email"} />}>
      {step === "email" ? (
        <form onSubmit={sendCode} noValidate className="flex flex-1 flex-col lg:flex-none">
          <div className="flex flex-col gap-6 px-5 pt-6 lg:p-0">
            <div className="flex flex-col gap-14 lg:hidden">
              <Lockup />
              <div className="flex flex-col gap-3">
                <Headline />
                <p className="text-[1.0625rem] text-fg-muted">{PITCH}</p>
              </div>
            </div>
            <div className="hidden flex-col gap-2 lg:flex">
              <h2 className="font-display text-h1 stretch-110">Log in</h2>
              <p className="text-fg-muted">New here? Same thing. We&apos;ll set you up.</p>
            </div>
            <TextField
              id="email"
              label="Email address"
              type="email"
              inputMode="email"
              autoComplete="email"
              autoCapitalize="none"
              spellCheck={false}
              maxLength={254}
              placeholder="you@example.com"
              value={typed}
              onChange={(e) => { setTyped(e.target.value); setEmailError(""); }}
              error={emailError}
            />
            <Alert variant="info">Your email is never shown to other travellers.</Alert>
            {error && <Alert variant="error">{error}</Alert>}
          </div>
          <BottomBar>
            <Button type="submit" loading={busy} loadingLabel="Sending code…" className="w-full">Send code</Button>
            <p className="text-center text-[0.8125rem] text-fg-muted lg:text-left lg:text-small">
              We only connect people. We never book rides or handle money.
            </p>
          </BottomBar>
        </form>
      ) : (
        <form onSubmit={(e) => { e.preventDefault(); verify(code); }} className="flex flex-1 flex-col lg:flex-none">
          <div className="flex flex-col gap-5 px-5 pt-3 lg:gap-[1.375rem] lg:p-0">
            <button type="button" onClick={change} className="min-h-tap self-start pr-3 font-semibold">‹ Back</button>
            <div className="flex flex-col gap-2">
              <h1 className="font-display text-h1 stretch-110">Enter the 6-digit code</h1>
              <p className="flex flex-wrap items-center gap-x-2 text-[1.0625rem] text-fg-muted">
                Sent to <b className="break-all text-fg">{email}</b>
                <Button variant="link" size="tap" onClick={change}>Change</Button>
              </p>
              <p className="text-fg-muted">Not there? Check your spam folder. It can take a minute.</p>
            </div>
            <OtpInput id="code" value={code} onChange={onCode} error={!!error} autoFocus />
            {error && <Alert variant="error">{error}</Alert>}
            <ResendRow seconds={resendIn} onResend={() => sendCode()} disabled={busy} />
          </div>
          <BottomBar>
            <Button type="submit" disabled={code.length !== 6} loading={busy} loadingLabel="Checking…" className="w-full">Verify</Button>
            <p className="hidden text-small text-fg-muted lg:block">Tip: paste the code anywhere. It fills in by itself.</p>
          </BottomBar>
        </form>
      )}
    </AuthShell>
  );
}

// "Resend code in 29s" with a ring that empties as time passes; at 0 it becomes a button
function ResendRow({ seconds, onResend, disabled }: { seconds: number; onResend: () => void; disabled: boolean }) {
  if (seconds <= 0) {
    return (
      <Button variant="link" size="tap" onClick={onResend} disabled={disabled} className="self-start px-0!">
        Resend code
      </Button>
    );
  }
  const circumference = 2 * Math.PI * 11.5;
  return (
    <p className="flex min-h-tap items-center gap-2.5 font-medium text-fg-muted" aria-live="off">
      <svg aria-hidden viewBox="0 0 28 28" className="size-7 shrink-0 -rotate-90">
        <circle cx="14" cy="14" r="11.5" fill="none" strokeWidth="3" className="stroke-line" />
        <circle
          cx="14" cy="14" r="11.5" fill="none" strokeWidth="3" strokeLinecap="round"
          className="stroke-accent transition-[stroke-dashoffset] duration-1000 ease-linear"
          strokeDasharray={circumference}
          strokeDashoffset={circumference * (1 - seconds / RESEND_SECONDS)}
        />
      </svg>
      Resend code in <b className="tnum text-fg">{seconds}s</b>
    </p>
  );
}
