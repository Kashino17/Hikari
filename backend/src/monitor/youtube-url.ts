/**
 * Validates that a user-supplied "channel URL" really points at YouTube before
 * it is handed to yt-dlp. Without this, POST /channels would spawn yt-dlp
 * against ANY url — an SSRF / arbitrary-fetch primitive (file://, internal
 * hosts, http://169.254.169.254/… metadata endpoints, etc.).
 *
 * We allow only https YouTube hosts and the handful of channel-ish path shapes
 * yt-dlp can resolve: /channel/UC…, /@handle, /c/Name, /user/Name, and a bare
 * /Name vanity path. Returns the normalized URL string, or null if rejected.
 */

const ALLOWED_HOSTS = new Set([
  "youtube.com",
  "www.youtube.com",
  "m.youtube.com",
  "music.youtube.com",
]);

// A channel id is "UC" + 22 url-safe base64 chars. Handles are @ + 3–30 chars.
const CHANNEL_ID_RE = /^UC[A-Za-z0-9_-]{22}$/;
const HANDLE_RE = /^@[A-Za-z0-9._-]{3,30}$/;
const VANITY_RE = /^[A-Za-z0-9._-]{1,100}$/;

export function validateYouTubeChannelUrl(input: string): string | null {
  if (typeof input !== "string") return null;
  const raw = input.trim();
  if (raw === "") return null;

  let url: URL;
  try {
    url = new URL(raw);
  } catch {
    return null;
  }

  if (url.protocol !== "https:") return null;
  if (!ALLOWED_HOSTS.has(url.hostname.toLowerCase())) return null;

  const segments = url.pathname.split("/").filter((s) => s !== "");
  if (segments.length === 0) return null;

  const [first, second] = segments;

  // /@handle
  if (first.startsWith("@")) {
    return HANDLE_RE.test(first) ? `https://www.youtube.com/${first}` : null;
  }

  // /channel/UC…
  if (first === "channel") {
    if (!second || !CHANNEL_ID_RE.test(second)) return null;
    return `https://www.youtube.com/channel/${second}`;
  }

  // /c/Name and /user/Name
  if (first === "c" || first === "user") {
    if (!second || !VANITY_RE.test(second)) return null;
    return `https://www.youtube.com/${first}/${second}`;
  }

  // bare /Name vanity path (single segment only — avoids /watch, /playlist…)
  if (segments.length === 1 && VANITY_RE.test(first) && !RESERVED_PATHS.has(first.toLowerCase())) {
    return `https://www.youtube.com/${first}`;
  }

  return null;
}

// Single-segment paths that are NOT channels — reject so a /watch or /shorts
// link can't be mistaken for a vanity channel name.
const RESERVED_PATHS = new Set([
  "watch",
  "playlist",
  "shorts",
  "results",
  "feed",
  "embed",
  "live",
  "hashtag",
  "account",
]);
