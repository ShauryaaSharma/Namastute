"use client";

import { useRouter } from "next/navigation";
import { type FormEvent, useEffect, useState } from "react";
import { AppBar } from "@/components/app-bar";
import { Button } from "@/components/ui/button";
import { cx } from "@/components/ui/cx";
import { Alert, Card, Eyebrow } from "@/components/ui/display";
import { BottomBar } from "@/components/ui/layout";
import { getVerification, restoreSession, submitVerification, type Verification } from "@/lib/api";
import { shrinkPhoto } from "@/lib/photo";
import { useAction } from "@/lib/use-action";

const DOCS = [
  { value: "passport", label: "Passport" },
  { value: "driving_licence", label: "Driving licence" },
  { value: "voter_id", label: "Voter ID" },
  { value: "masked_aadhaar", label: "Masked Aadhaar" },
];

type Photo = { blob: Blob; url: string };

// ID verification, step 1 of the check (Learning Notes 78): consent, a photo of the ID, a selfie with a random gesture.
// A person reviews them, then both photos are deleted.
export default function VerifyPage() {
  const router = useRouter();
  const [name, setName] = useState("");
  const [v, setV] = useState<Verification | null>(null);
  const [consent, setConsent] = useState(false);
  const [docType, setDocType] = useState("");
  const [idPhoto, setIdPhoto] = useState<Photo | null>(null);
  const [selfie, setSelfie] = useState<Photo | null>(null);
  const { busy, error, setError, run } = useAction();

  useEffect(() => {
    restoreSession()
      .then(async (me) => {
        if (!me) return router.replace("/login");
        if (!me.name) return router.replace("/profile");
        setV(await getVerification());
        setName(me.name);
      })
      .catch(() => router.replace("/login"));
  }, [router]);

  async function pick(file: File | undefined, set: (p: Photo | null) => void, old: Photo | null) {
    if (!file) return;
    setError("");
    try {
      const blob = await shrinkPhoto(file);
      if (old) URL.revokeObjectURL(old.url);
      set({ blob, url: URL.createObjectURL(blob) });
    } catch {
      setError("Couldn't read that photo. Take it again with your camera, or choose a JPEG or PNG.");
    }
  }

  function send(e: FormEvent) {
    e.preventDefault();
    if (!consent || !docType || !idPhoto || !selfie) return;
    const form = new FormData();
    form.set("docType", docType);
    form.set("consent", "true");
    form.set("idPhoto", idPhoto.blob, "id.jpg");
    form.set("selfie", selfie.blob, "selfie.jpg");
    run(async () => {
      try {
        setV(await submitVerification(form));
      } catch (err) {
        getVerification().then(setV).catch(() => {}); // e.g. an expired gesture: show the new one
        throw err;
      }
    });
  }

  if (!v) return <p role="status" className="p-6 text-fg-muted">Loading…</p>;

  return (
    <>
      <AppBar name={name} />
      <main className="mx-auto flex w-full max-w-[30rem] flex-col lg:max-w-[40rem] lg:pt-8">
        <div className="flex flex-col gap-7 px-5 pt-3 pb-6">
          <div className="flex flex-col gap-2">
            <Eyebrow>ID verification</Eyebrow>
            <h1 className="font-display text-h1 stretch-110">Verify your ID</h1>
            <p className="text-fg-muted">It gives you the ID verified badge, and lets women join women-only groups.</p>
          </div>

          {v.status === "verified" && <Alert variant="success">You&apos;re ID verified.</Alert>}
          {v.status === "pending" && (
            <Card className="flex flex-col gap-2">
              <h2 className="font-display text-h3">Under review</h2>
              <p className="text-fg-muted">
                A person at Namastute is checking your photos, usually within a day. We&apos;ll let you know. Your photos are
                deleted right after the check.
              </p>
            </Card>
          )}

          {(v.status === "unverified" || v.status === "rejected") && v.gesture && (
            <form id="verify" onSubmit={send} className="flex flex-col gap-7">
              {v.status === "rejected" && <Alert variant="error">Not approved: {v.rejectReason} Please try again.</Alert>}

              <Card className="flex flex-col gap-3">
                <h2 className="font-display text-h3">What we ask for, and why</h2>
                <ul className="flex list-disc flex-col gap-1.5 pl-5 text-fg-muted">
                  <li>A photo of one ID, and a selfie, so a person can check the ID is really yours.</li>
                  <li>Only a person at Namastute sees them, once. Nobody in your groups ever does.</li>
                  <li>Both photos are deleted right after the check, and after 7 days at the latest.</li>
                  <li>We keep only: the type of ID, its last 4 digits, the gender it shows, and the date.</li>
                </ul>
                <label className="flex min-h-tap cursor-pointer items-start gap-3 pt-1 font-semibold">
                  <input type="checkbox" checked={consent} onChange={(e) => setConsent(e.target.checked)}
                         className="mt-0.5 size-6 shrink-0 accent-accent" />
                  I agree to Namastute checking my ID and selfie this way.
                </label>
              </Card>

              <fieldset className="flex flex-col gap-3">
                <legend className="mb-3 font-display text-h3">1. Your ID</legend>
                <div className="grid grid-cols-2 gap-2">
                  {DOCS.map((d) => (
                    <label key={d.value} className={cx(
                      "flex min-h-tap cursor-pointer items-center gap-2.5 rounded-md border-[1.5px] px-3 font-semibold",
                      docType === d.value ? "border-accent-text bg-accent-soft dark:border-accent" : "border-line bg-surface dark:bg-surface-2",
                    )}>
                      <input type="radio" name="docType" value={d.value} checked={docType === d.value}
                             onChange={() => setDocType(d.value)} className="size-5 accent-accent" />
                      {d.label}
                    </label>
                  ))}
                </div>
                {docType === "masked_aadhaar" && (
                  <Alert variant="warning">
                    Only a masked Aadhaar: the first 8 digits hidden, like XXXX XXXX 1234. Download one from UIDAI&apos;s myAadhaar
                    website (choose &quot;masked&quot;). A full Aadhaar number is refused.
                  </Alert>
                )}
                <PhotoPicker label="Photo of your ID" hint="The whole card, flat, in good light, every word readable."
                             capture="environment" photo={idPhoto} onPick={(f) => pick(f, setIdPhoto, idPhoto)} />
              </fieldset>

              <fieldset className="flex flex-col gap-3">
                <legend className="mb-3 font-display text-h3">2. A selfie</legend>
                <div className="rounded-[14px] bg-accent-soft px-4 py-3.5 text-accent-text">
                  <p className="text-small font-semibold">In your selfie, please:</p>
                  <p className="font-display text-h3">{v.gesture}</p>
                </div>
                <p className="text-[0.9375rem] text-fg-muted">Face the camera, no sunglasses or mask. The gesture shows it&apos;s a new photo.</p>
                <PhotoPicker label="Your selfie" capture="user" photo={selfie} onPick={(f) => pick(f, setSelfie, selfie)} />
              </fieldset>
            </form>
          )}
        </div>

        {(v.status === "unverified" || v.status === "rejected") && (
          <BottomBar bordered>
            {error && <Alert variant="error">{error}</Alert>}
            <Button type="submit" form="verify" loading={busy} loadingLabel="Sending…" disabled={!consent || !docType || !idPhoto || !selfie}>
              Send for checking
            </Button>
          </BottomBar>
        )}
      </main>
    </>
  );
}

// A photo button: phones open the camera (front or back), laptops a file picker; shows what was taken
function PhotoPicker({ label, hint, capture, photo, onPick }: {
  label: string;
  hint?: string;
  capture: "user" | "environment";
  photo: Photo | null;
  onPick: (file: File | undefined) => void;
}) {
  return (
    <div className="flex flex-col gap-2">
      {hint && <p className="text-[0.9375rem] text-fg-muted">{hint}</p>}
      {photo && (
        // eslint-disable-next-line @next/next/no-img-element -- a local preview (blob: URL), nothing to optimise
        <img src={photo.url} alt={`${label}, as taken`} className="max-h-64 w-full rounded-md border border-line object-contain" />
      )}
      <label className="flex min-h-tap cursor-pointer items-center justify-center rounded-btn border-[1.5px] border-line-strong px-4 font-semibold hover:bg-surface-2 has-focus-visible:ring-2 has-focus-visible:ring-ring">
        {photo ? `Retake: ${label.toLowerCase()}` : label}
        <input type="file" accept="image/*" capture={capture} className="sr-only" onChange={(e) => onPick(e.target.files?.[0])} />
      </label>
    </div>
  );
}
