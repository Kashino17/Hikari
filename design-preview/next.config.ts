import type { NextConfig } from "next";
import { dirname } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));

const nextConfig: NextConfig = {
  // Allow phone (via Tailscale) + LAN access. Without this Next.js 16 blocks
  // cross-origin dev resources (/_next/webpack-hmr, /_next/static/...) and
  // the page loads HTML but JS never hydrates — all buttons dead.
  allowedDevOrigins: [
    "100.125.192.44", // laptop Tailscale IPv4
    "192.168.2.208",  // laptop LAN IPv4
  ],
  // Pin Turbopack workspace root to wherever this config file lives, so the
  // same config works from the main repo and from git worktrees. Without this
  // pin Turbopack walks up and finds an orphan /Users/ayysir/package-lock.json,
  // treats the home dir as the workspace root, and fails to resolve tailwindcss.
  turbopack: {
    root: here,
  },
  // Proxy /api/manga/* through Next so the browser sees same-origin requests
  // (no CORS) regardless of whether it's loaded over Tailscale, LAN, or local.
  // Server Components hit the backend directly via mangaApi's absolute base URL.
  async rewrites() {
    const target = process.env.HIKARI_API_BASE_URL ?? "http://localhost:3000";
    return [{ source: "/api/manga/:path*", destination: `${target}/api/manga/:path*` }];
  },
};

export default nextConfig;
