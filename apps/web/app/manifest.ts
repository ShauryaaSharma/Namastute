import type { MetadataRoute } from "next";

// The web app manifest makes Namastute installable (Learning Notes 58.6): on iPhone, push only works once it's on the Home Screen
export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "Namastute",
    short_name: "Namastute",
    description: "Share a cab with people from your flight.",
    start_url: "/",
    display: "standalone",
    background_color: "#110F0D", // the design's dark bg: the splash screen matches the app
    theme_color: "#110F0D",
    icons: [
      { src: "/icon-192.png", sizes: "192x192", type: "image/png", purpose: "any" },
      { src: "/icon-512.png", sizes: "512x512", type: "image/png", purpose: "any" },
      { src: "/icon-maskable-512.png", sizes: "512x512", type: "image/png", purpose: "maskable" }, // extra margin: phones crop it to a circle or squircle
    ],
  };
}
