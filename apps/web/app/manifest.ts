import type { MetadataRoute } from "next";

// The web app manifest makes Namastute installable (Learning Notes 58.6): on iPhone, push only works once it's on the Home Screen
export default function manifest(): MetadataRoute.Manifest {
  return {
    name: "Namastute",
    short_name: "Namastute",
    description: "Share a cab with people from your flight.",
    start_url: "/",
    display: "standalone",
    background_color: "#ffffff",
    theme_color: "#000000",
    icons: [
      { src: "/icon-192.png", sizes: "192x192", type: "image/png", purpose: "any" },
      { src: "/icon-512.png", sizes: "512x512", type: "image/png", purpose: "any" },
      { src: "/icon-512.png", sizes: "512x512", type: "image/png", purpose: "maskable" },
    ],
  };
}
