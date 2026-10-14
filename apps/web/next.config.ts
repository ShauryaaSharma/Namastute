import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  output: "standalone", // a small self-contained server.js for the Docker image (Learning Notes 54.3)
  cacheComponents: true,
  partialPrefetching: true,
  turbopack: {
    rules: {
      "*.css": {
        loaders: ["@tailwindcss/turbopack"],
        as: "*.css",
      },
    },
  },
};

export default nextConfig;
