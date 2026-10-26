import type { Metadata } from "next";
import Link from "next/link";
import type { ReactNode } from "react";
import { AppIcon, Lockup, Wordmark } from "@/components/ui/brand";
import { buttonClass } from "@/components/ui/button";
import { cx } from "@/components/ui/cx";
import StickyCta from "./sticky-cta";

// The landing page (Learning Notes 86): static HTML built once, so strangers and search engines see the product at
// once. The app itself lives at /home, which sends logged-out people to /login.
export const metadata: Metadata = {
  title: "namastute · Share the cab from Delhi IGI",
  description: "Find people walking out of Delhi IGI arrivals around your time, heading your way. Split one Uber or Ola.",
};

const APP = "/home";
const WRAP = "mx-auto max-w-[1280px] px-5 lg:px-16";
const SECTION = cx(WRAP, "pt-18 lg:pt-32");
const EYEBROW = "mb-3 text-eyebrow uppercase text-accent-text";
const H2 = "font-display text-[2.25rem] font-extrabold leading-none tracking-[-0.03em] stretch-115 lg:text-[3.75rem]";
const LEAD = "text-[1.125rem] leading-[1.45] text-fg-muted text-pretty lg:text-[1.3125rem]";
// The app's Card and Chip have fixed padding and shape; the design wants other values, so these are written out.
// CHIP has no colour or weight: each use adds its own, so no two classes ever set the same property.
const CARD = "rounded-lg border border-line bg-surface dark:border-transparent";
const CHIP = "rounded-sm px-2.5 py-0.5 text-small";
const PLAIN_CHIP = cx(CHIP, "bg-surface-2 font-semibold");
const NAV_LINK = "flex min-h-tap items-center rounded-md px-3.5 font-semibold hover:bg-surface-2";

export default function Landing() {
  return (
    <>
      <header className={cx(WRAP, "flex items-center justify-between gap-4 py-3")}>
        <Link href="/" aria-label="namastute home" className="flex min-h-tap items-center rounded-sm"><Lockup /></Link>
        <nav aria-label="Main" className="flex items-center gap-2">
          <span className="hidden gap-1 md:flex">
            <a href="#how" className={NAV_LINK}>How it works</a>
            <a href="#safety" className={NAV_LINK}>Safety</a>
            <a href="#faq" className={NAV_LINK}>FAQ</a>
          </span>
          <Link href={APP} className={buttonClass({ size: "tap", className: "whitespace-nowrap" })}>Open the app</Link>
        </nav>
      </header>

      <main>
        <section aria-labelledby="hero-title" className={cx(WRAP, "pt-7 lg:pt-18")}>
          <div className="grid items-center gap-10 lg:grid-cols-[minmax(0,1.2fr)_minmax(0,0.8fr)] lg:gap-16">
            <div className="min-w-0">
              <p className="mb-5 text-eyebrow uppercase text-accent-text">Delhi IGI · Arrivals</p>
              <h1 id="hero-title" className="font-display text-[2.875rem] font-extrabold leading-[0.95] tracking-[-0.03em] stretch-115 lg:text-[5.75rem]">
                Landed at IGI?<br /><span className="text-brand-mark">Share the cab.</span>
              </h1>
              <p className={cx(LEAD, "mt-6 max-w-[30ch]")}>Find people walking out around your time, heading your way. Split one Uber or Ola.</p>
              <div className="mt-8 flex flex-col gap-2 md:flex-row md:items-center md:gap-5">
                <Link id="hero-cta" href={APP} className={buttonClass({ className: "w-full md:w-auto md:px-7" })}>
                  Find co-travellers <span aria-hidden>→</span>
                </Link>
                <a href="#how" className={buttonClass({ variant: "link", className: "text-[1.0625rem]" })}>How it works</a>
              </div>
            </div>
            <MatchesPreview />
          </div>

          <ul className="mt-12 grid gap-6 md:grid-cols-3 lg:mt-22 lg:gap-10">
            <Trust title="First name + area" line="That's all anyone sees." />
            <Trust title="You book, you pay" line="We never touch rides or money." />
            <Trust title="T1 · T2 · T3" line="Delhi IGI arrivals only." />
          </ul>
        </section>

        <section id="how" aria-labelledby="how-title" className={SECTION}>
          <p className={EYEBROW}>How it works</p>
          <h2 id="how-title" className={cx(H2, "max-w-[16ch]")}>From arrivals to one shared cab.</h2>
          <ol className="mt-7 grid gap-4 md:grid-cols-2 lg:mt-12 lg:grid-cols-4">
            <Step n="01" title="Plan your exit" line="Terminal, time, area.">
              <span className={cx(PLAIN_CHIP, "text-fg-muted")}>T1</span>
              <span className={cx(PLAIN_CHIP, "text-fg-muted")}>T2</span>
              <span className={cx(CHIP, "bg-accent-soft font-bold text-accent-text")}>✓ T3</span>
            </Step>
            <Step n="02" title="See who's heading your way" line="People walking out around your time.">
              <span className={PLAIN_CHIP}>10 min after you</span>
            </Step>
            <Step n="03" title="Ask to share" line="Form a group of up to 4 and chat.">
              <span className={PLAIN_CHIP}>Group · <span className="font-mono">3/4</span></span>
            </Step>
            <Step n="04" title="Meet at the pickup" line="One person books. Everyone pays their share by UPI.">
              <span className={PLAIN_CHIP}><span className="font-mono">₹246</span> → Asha</span>
            </Step>
          </ol>
        </section>

        <section aria-labelledby="fares-title" className={SECTION}>
          <div className="grid items-center gap-10 md:grid-cols-2 lg:gap-16">
            <div className="min-w-0">
              <p className={EYEBROW}>Fair fares</p>
              <h2 id="fares-title" className={H2}>Go further, pay more.</h2>
              <p className={cx(LEAD, "mt-5 max-w-[34ch]")}>
                The fare is split by how far each person rides, not down the middle. Everyone pays the booker in their own UPI app.
              </p>
              <p className="mt-5 flex items-baseline gap-2.5 font-bold"><span aria-hidden className="text-success">✓</span>Namastute never sees or confirms payments.</p>
            </div>
            <FareExample />
          </div>
        </section>

        <section id="safety" aria-labelledby="safety-title" className={SECTION}>
          <p className={EYEBROW}>Safety and privacy</p>
          <h2 id="safety-title" className={cx(H2, "max-w-[18ch]")}>Made for landing alone, late.</h2>
          <ul className="mt-7 grid gap-4 md:grid-cols-2 lg:mt-12 lg:grid-cols-3">
            <li className="flex flex-col gap-2.5 rounded-lg border-2 border-accent bg-accent-soft p-6">
              <span className="self-start rounded-sm bg-accent px-2.5 py-0.5 text-small font-bold text-on-accent">✓ Women only</span>
              <h3 className="font-display text-[1.5rem] font-extrabold leading-[1.1] tracking-[-0.02em] stretch-115">Women-only groups</h3>
              <p>Once your ID check confirms you&apos;re a woman, you can form groups that nobody else can see or join.</p>
            </li>
            <Safety badge="🔒 Hidden" title="Phone number never shown" line="Nor your surname or exact address. Just first name and area." />
            <Safety badge="◆ ID verified" badgeClass="text-info" title="An optional ID check" line="Pass it and your card shows the badge to others." />
            <Safety badge={["Report", "Block"]} badgeClass="text-danger" title="Report and block in one tap" line="From any match or group member." />
            <Safety badge="● Group only" badgeClass="text-success" title="Live location, briefly" line="Shared only with your group, and only around pickup time." />
            <Safety badge="✓ Deleted" title="ID photos deleted" line="Once the check is decided. Within 7 days at most." />
          </ul>
        </section>

        <section id="faq" aria-labelledby="faq-title" className={SECTION}>
          <div className="grid gap-10 md:grid-cols-[minmax(0,0.8fr)_minmax(0,1.2fr)] lg:gap-16">
            <div>
              <p className={EYEBROW}>FAQ</p>
              <h2 id="faq-title" className={H2}>Plain answers.</h2>
            </div>
            <div className="border-t border-line">
              {FAQ.map(([q, a], i) => (
                // name="faq": the browser keeps only one answer open at a time, no JavaScript needed
                <details key={q} name="faq" open={i === 0} className="group border-b border-line">
                  <summary className="flex min-h-16 cursor-pointer list-none items-center justify-between gap-4 rounded-sm text-[1.1875rem] font-bold [&::-webkit-details-marker]:hidden">
                    {q}
                    <span aria-hidden className="font-mono text-[1.375rem] text-accent-text">
                      <span className="group-open:hidden">+</span><span className="hidden group-open:inline">−</span>
                    </span>
                  </summary>
                  <p className="pr-10 pb-5 text-fg-muted text-pretty">{a}</p>
                </details>
              ))}
            </div>
          </div>
        </section>

        <section aria-labelledby="cta-title" className={cx(WRAP, "py-18 lg:py-32")}>
          <div className="flex flex-col items-start gap-8 rounded-sheet border border-line bg-surface px-5 py-7 lg:p-18 dark:border-transparent">
            <h2 id="cta-title" className="max-w-[14ch] font-display text-[2.75rem] font-extrabold leading-[0.95] tracking-[-0.03em] stretch-115 lg:text-[5.5rem]">
              Your cab is cheaper with <span className="text-brand-mark">company.</span>
            </h2>
            <Link href={APP} className={buttonClass({ className: "w-full md:w-auto md:px-7" })}>
              Find co-travellers <span aria-hidden>→</span>
            </Link>
          </div>
        </section>
      </main>

      {/* shortcut: no Privacy / Terms / Contact links yet, add them once those pages are written (Learning Notes 88) */}
      <footer className="border-t border-line">
        <div className={cx(WRAP, "flex flex-col gap-3 pt-8 pb-10")}>
          <span className="inline-flex items-center gap-2.5">
            <AppIcon className="size-8 text-[1.3125rem]" />
            <Wordmark className="text-[1.375rem]" />
          </span>
          <p className="max-w-[40ch] text-fg-muted">We only connect people. We never book rides or handle money.</p>
        </div>
      </footer>

      <StickyCta href={APP} watch="hero-cta" />
    </>
  );
}

const FAQ: [string, string][] = [
  ["Do you book the cab?", "No. One person in your group books a single Uber or Ola in their own app. Namastute only connects people."],
  ["Who sees my phone number?", "Nobody. Others see your first name and your area, like \"Sector 29, Gurgaon\". Never your phone number, surname or exact address."],
  ["How is the fare split?", "By how far each person goes, so whoever rides further pays more. Everyone pays the booker by UPI, in their own UPI app. We never see or confirm payments."],
  ["What are women-only groups?", "Women whose ID check is approved can form groups that nobody else can see or join."],
  ["What happens to my ID photos?", "They're used only for the optional ID check, then deleted once it's decided. Within 7 days at most."],
  ["Do I need to download an app?", "No. Namastute runs in your browser. You can add it to your home screen like an app, with no app store needed."],
];

function Trust({ title, line }: { title: string; line: string }) {
  return (
    <li className="border-t-2 border-accent pt-4">
      <p className="font-display text-[1.375rem] font-extrabold leading-[1.1] tracking-[-0.02em] stretch-115">{title}</p>
      <p className="mt-1 text-fg-muted">{line}</p>
    </li>
  );
}

function Step({ n, title, line, children }: { n: string; title: string; line: string; children: ReactNode }) {
  return (
    <li className={cx(CARD, "flex flex-col gap-3 p-6")}>
      <span className="font-mono text-small text-accent-text">{n}</span>
      <h3 className="text-[1.25rem] font-bold leading-[1.25]">{title}</h3>
      <p className="text-fg-muted">{line}</p>
      <div aria-hidden className="mt-auto flex gap-1.5 pt-2">{children}</div>
    </li>
  );
}

function Safety({ badge, badgeClass, title, line }: { badge: string | string[]; badgeClass?: string; title: string; line: string }) {
  return (
    <li className={cx(CARD, "flex flex-col gap-2.5 p-6")}>
      <span className="flex gap-1.5">
        {[badge].flat().map((b) => <span key={b} className={cx(CHIP, "bg-surface-2 font-bold", badgeClass)}>{b}</span>)}
      </span>
      <h3 className="text-[1.25rem] font-bold leading-[1.25]">{title}</h3>
      <p className="text-fg-muted">{line}</p>
    </li>
  );
}

// A picture of the matches screen, not a working one: role="img" so screen readers hear one sentence
function MatchesPreview() {
  return (
    <div className="flex min-w-0 justify-center">
      <div role="img" aria-label="Preview of the matches screen: Asha, going to Sector 29, Gurgaon, walks out 10 minutes after you, with a 0.5 km detour."
           className="w-[300px] rounded-[40px] bg-surface-2 p-2.5 shadow-[0_30px_80px_-30px_rgba(0,0,0,0.45)]">
        <div className="flex flex-col gap-3.5 overflow-hidden rounded-[30px] bg-bg px-4 pt-5 pb-5.5">
          <div className="flex justify-between font-mono text-[0.8125rem] text-fg-muted"><span>23:31</span><span>T3</span></div>
          <div>
            <p className="text-eyebrow uppercase text-fg-muted">T3 arrivals · out at <span className="font-mono tracking-normal">23:40</span></p>
            <p className="mt-1.5 font-display text-[1.625rem] font-extrabold leading-[1.05] tracking-[-0.03em] stretch-115">Heading your way</p>
          </div>
          <div className={cx(CARD, "flex flex-col gap-3 p-4")}>
            <div className="flex items-center gap-3">
              <span className="flex size-10 items-center justify-center rounded-md bg-accent-soft text-[1.125rem] font-bold text-accent-text">A</span>
              <span>
                <span className="block text-[1.0625rem] font-bold leading-tight">Asha</span>
                <span className="block text-small text-fg-muted">Sector 29, Gurgaon</span>
              </span>
            </div>
            <div className="flex flex-wrap gap-1.5"><span className={PLAIN_CHIP}>10 min after you</span><span className={PLAIN_CHIP}>0.5 km detour</span></div>
            <span className="flex min-h-tap items-center justify-center rounded-btn bg-accent font-bold text-on-accent">Ask to share</span>
          </div>
          <div className={cx(CARD, "flex items-center gap-3 p-4 opacity-55")}>
            <span className="flex size-10 items-center justify-center rounded-md bg-surface-2 text-[1.125rem] font-bold text-fg-muted">M</span>
            <span>
              <span className="block text-[1.0625rem] font-bold leading-tight">Meera</span>
              <span className="block text-small text-fg-muted">DLF Phase 3, Gurgaon</span>
            </span>
          </div>
        </div>
      </div>
    </div>
  );
}

function FareExample() {
  return (
    <div className={cx(CARD, "flex min-w-0 flex-col gap-5 p-5 lg:p-8")}>
      <div className="flex items-baseline justify-between gap-3">
        <div>
          <p className="text-eyebrow uppercase text-fg-muted">Total fare</p>
          <p className="text-small text-fg-muted">One Uber, booked by Asha</p>
        </div>
        <p className="font-mono text-[2.25rem] leading-none">₹600</p>
      </div>
      <div aria-hidden className="flex h-3 gap-1">
        <span className="flex-[246] rounded-sm bg-info" />
        <span className="flex-[354] rounded-sm bg-accent" />
      </div>
      <dl>
        <FareRow colour="bg-info" name="Keshav" note="Gets off first" amount="₹246" />
        <FareRow colour="bg-accent" name="Asha" booker note="Goes further" amount="₹354" />
      </dl>
      <div className="flex flex-wrap items-center justify-between gap-3 rounded-btn bg-surface-2 py-3 pr-3 pl-4">
        <p className="text-small text-fg-muted">Keshav&apos;s screen</p>
        <span className="flex min-h-tap items-center gap-1.5 rounded-btn bg-accent px-4.5 font-bold text-on-accent">
          Pay Asha <span className="font-mono">₹246</span> by UPI
        </span>
      </div>
    </div>
  );
}

function FareRow({ colour, name, booker, note, amount }: { colour: string; name: string; booker?: boolean; note: string; amount: string }) {
  return (
    <div className="flex items-center justify-between gap-3 border-line py-3 not-last:border-b">
      <dt className="flex items-center gap-2.5">
        <span aria-hidden className={cx("size-2.5 rounded-[3px]", colour)} />
        <span>
          <span className="font-bold">{name}</span>
          {booker && <span className={cx(PLAIN_CHIP, "ml-1 text-fg-muted")}>Booker</span>}
          <span className="block text-small text-fg-muted">{note}</span>
        </span>
      </dt>
      <dd className="font-mono text-[1.25rem]">{amount}</dd>
    </div>
  );
}
