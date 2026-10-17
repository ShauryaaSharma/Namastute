import type { Metadata, Viewport } from "next";
import { Anek_Devanagari, IBM_Plex_Mono, Mukta } from "next/font/google";
import Script from "next/script";
import "./globals.css";

// Self-hosted at build time by next/font: no request to Google from users' phones, and no layout shift
const anek = Anek_Devanagari({ subsets: ["latin", "devanagari"], axes: ["wdth"], variable: "--font-anek" });
const mukta = Mukta({ subsets: ["latin", "devanagari"], weight: ["400", "500", "600", "700"], variable: "--font-mukta" });
const plexMono = IBM_Plex_Mono({ subsets: ["latin"], weight: "500", variable: "--font-plex-mono" });

export const metadata: Metadata = {
  title: "Namastute",
  description: "Share a cab with people from your flight.",
};

export const viewport: Viewport = {
  themeColor: [
    { media: "(prefers-color-scheme: dark)", color: "#110F0D" },
    { media: "(prefers-color-scheme: light)", color: "#FBF7F1" },
  ],
};

// Runs before the page is drawn, so there's no flash of the wrong theme: the user's choice on this device,
// else the phone's setting, else dark (the design's default). lib/theme.ts writes the choice.
const THEME_SCRIPT = `try{var t=localStorage.getItem("theme");var d=t?t==="dark":!matchMedia("(prefers-color-scheme: light)").matches;document.documentElement.classList.toggle("dark",d)}catch(e){document.documentElement.classList.add("dark")}`;

export default function RootLayout({ children }: LayoutProps<"/">) {
  return (
    // suppressHydrationWarning: the theme script changes <html>'s class before React takes over
    <html lang="en" className={`${anek.variable} ${mukta.variable} ${plexMono.variable} dark`} suppressHydrationWarning>
      <body className="min-h-dvh">
        <Script id="theme" strategy="beforeInteractive">{THEME_SCRIPT}</Script>
        {children}
      </body>
    </html>
  );
}
