# Hikari Backend Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build the local Node.js backend that monitors a YouTube channel whitelist, scrapes new videos via yt-dlp, LLM-scores them, downloads approved ones as 720p MP4, and serves a curated feed + MP4 files over HTTP to the Android client.

**Architecture:** Fastify HTTP server + SQLite (better-sqlite3) + node-cron + execa-wrapped yt-dlp CLI + pluggable Scorer (Claude / Ollama). Single process, single machine, single user. TypeScript strict mode.

**Tech Stack:** Node.js 24 LTS · TypeScript 5.x · pnpm · Fastify 5 · better-sqlite3 · execa · node-cron · @anthropic-ai/sdk · fast-xml-parser · @fastify/static · Vitest · Biome

**Spec Reference:** `docs/superpowers/specs/2026-04-24-hikari-mvp-design.md`

---

## File Structure (Target Layout)

```
backend/
├── package.json
├── tsconfig.json
├── biome.json
├── vitest.config.ts
├── .env.example
├── src/
│   ├── index.ts                     # Fastify entry, wires everything
│   ├── config.ts                    # env + defaults
│   ├── db/
│   │   ├── schema.sql
│   │   ├── connection.ts            # shared better-sqlite3 handle
│   │   └── migrations.ts            # apply schema idempotently
│   ├── yt-dlp/
│   │   └── client.ts                # execa wrapper around yt-dlp CLI
│   ├── monitor/
│   │   ├── channel-resolver.ts      # channel URL → channel_id
│   │   └── rss-poller.ts            # RSS feed → new video IDs
│   ├── ingest/
│   │   ├── metadata.ts              # yt-dlp --dump-json
│   │   └── transcript.ts            # VTT fetch + parse
│   ├── scorer/
│   │   ├── types.ts                 # Score, Scorer interface
│   │   ├── claude-scorer.ts
│   │   ├── ollama-scorer.ts
│   │   ├── prompt.ts                # ← Kadir-kun writes this (value system)
│   │   └── decision.ts              # threshold rule
│   ├── sponsorblock/
│   │   └── client.ts
│   ├── download/
│   │   ├── worker.ts                # yt-dlp video download
│   │   └── cleanup.ts               # LRU 10GB policy
│   ├── pipeline/
│   │   └── orchestrator.ts          # one-shot pipeline run per video
│   ├── api/
│   │   ├── channels.ts              # POST/GET/DELETE /channels
│   │   ├── feed.ts                  # feed + item actions + budget
│   │   ├── videos.ts                # @fastify/static mp4 serving
│   │   └── health.ts
│   └── util/
│       └── logger.ts                # pino
└── tests/
    ├── fixtures/                    # sample RSS XML, VTT, yt-dlp JSON
    ├── integration/
    │   └── full-pipeline.test.ts
    └── unit/                        # co-located with src/ instead; tests/unit holds cross-cutting helpers
```

**Tests live next to source**, e.g. `src/monitor/rss-poller.test.ts`. Vitest picks them up via `include: ["src/**/*.test.ts"]`.

---

## Task 0: Project Scaffolding

**Files:**
- Create: `backend/package.json`
- Create: `backend/tsconfig.json`
- Create: `backend/biome.json`
- Create: `backend/vitest.config.ts`
- Create: `backend/.gitignore`
- Create: `backend/.env.example`
- Create: `backend/src/index.ts` (minimal entry)

- [ ] **Step 1: Create `backend/package.json`**

```json
{
  "name": "hikari-backend",
  "version": "0.1.0",
  "private": true,
  "type": "module",
  "engines": {
    "node": ">=24.0.0"
  },
  "scripts": {
    "dev": "tsx watch src/index.ts",
    "build": "tsc -p tsconfig.json",
    "start": "node dist/index.js",
    "test": "vitest run",
    "test:watch": "vitest",
    "lint": "biome check src tests",
    "format": "biome format --write src tests"
  },
  "dependencies": {
    "@anthropic-ai/sdk": "^0.39.0",
    "@fastify/static": "^8.0.0",
    "better-sqlite3": "^11.7.0",
    "execa": "^9.5.0",
    "fast-xml-parser": "^4.5.0",
    "fastify": "^5.1.0",
    "node-cron": "^3.0.3",
    "pino": "^9.5.0",
    "pino-pretty": "^13.0.0",
    "zod": "^3.23.0"
  },
  "devDependencies": {
    "@biomejs/biome": "^1.9.0",
    "@types/better-sqlite3": "^7.6.0",
    "@types/node": "^24.0.0",
    "@types/node-cron": "^3.0.11",
    "tsx": "^4.19.0",
    "typescript": "^5.7.0",
    "vitest": "^2.1.0"
  }
}
```

- [ ] **Step 2: Create `backend/tsconfig.json`**

```json
{
  "compilerOptions": {
    "target": "ES2023",
    "module": "ESNext",
    "moduleResolution": "Bundler",
    "lib": ["ES2023"],
    "outDir": "./dist",
    "rootDir": "./src",
    "strict": true,
    "noUncheckedIndexedAccess": true,
    "exactOptionalPropertyTypes": true,
    "esModuleInterop": true,
    "forceConsistentCasingInFileNames": true,
    "skipLibCheck": true,
    "resolveJsonModule": true,
    "declaration": false,
    "sourceMap": true
  },
  "include": ["src/**/*"],
  "exclude": ["node_modules", "dist", "**/*.test.ts"]
}
```

- [ ] **Step 3: Create `backend/biome.json`**

```json
{
  "$schema": "https://biomejs.dev/schemas/1.9.0/schema.json",
  "organizeImports": { "enabled": true },
  "linter": {
    "enabled": true,
    "rules": {
      "recommended": true,
      "style": { "useImportType": "error" }
    }
  },
  "formatter": {
    "enabled": true,
    "indentStyle": "space",
    "indentWidth": 2,
    "lineWidth": 100
  }
}
```

- [ ] **Step 4: Create `backend/vitest.config.ts`**

```ts
import { defineConfig } from "vitest/config";

export default defineConfig({
  test: {
    include: ["src/**/*.test.ts", "tests/**/*.test.ts"],
    globals: false,
    environment: "node",
    passWithNoTests: true,
  },
});
```

- [ ] **Step 5: Create `backend/.gitignore`**

```
node_modules/
dist/
.env
*.log
coverage/
.hikari/
```

- [ ] **Step 6: Create `backend/.env.example`**

```
# Anthropic API key — required if LLM_PROVIDER=claude
ANTHROPIC_API_KEY=sk-ant-...

# Which scorer to use: "claude" | "ollama"
LLM_PROVIDER=claude

# Ollama endpoint (only used if LLM_PROVIDER=ollama)
OLLAMA_URL=http://localhost:11434
OLLAMA_MODEL=qwen2.5:14b

# Claude model
CLAUDE_MODEL=claude-haiku-4-5

# Data directory (created if missing)
HIKARI_DATA_DIR=~/.hikari

# HTTP port
PORT=3000

# Daily budget (hard limit, per spec decision #8)
DAILY_BUDGET=15

# Disk limit for video cache in GB (per spec decision #9)
DISK_LIMIT_GB=10
```

- [ ] **Step 7: Create `backend/src/index.ts` — minimal stub**

```ts
import Fastify from "fastify";

const app = Fastify({ logger: true });

app.get("/health", async () => ({ status: "ok" }));

const port = Number(process.env.PORT ?? 3000);
app.listen({ port, host: "0.0.0.0" }).catch((err) => {
  app.log.error(err);
  process.exit(1);
});
```

- [ ] **Step 8: Install dependencies**

Run: `cd backend && pnpm install`
Expected: Lockfile written, 0 errors.

- [ ] **Step 9: Verify dev server starts**

Run: `cd backend && pnpm dev`
Expected: Fastify logs "Server listening at http://0.0.0.0:3000". Ctrl-C to stop.

- [ ] **Step 10: Verify tests run (no tests yet, but runner works)**

Run: `cd backend && pnpm test`
Expected: "No test files found" — but exit code 0 or vitest-specific "no tests ran" message. (If Vitest exits with error on zero tests, add `passWithNoTests: true` to config.)

- [ ] **Step 11: Commit**

```bash
git -C /Users/ayysir/Desktop/Hikari add backend/
git -C /Users/ayysir/Desktop/Hikari commit -m "feat(backend): project scaffolding"
```

---

## Task 1: Database Schema & Migrations

**Files:**
- Create: `backend/src/db/schema.sql`
- Create: `backend/src/db/connection.ts`
- Create: `backend/src/db/migrations.ts`
- Create: `backend/src/db/migrations.test.ts`

- [ ] **Step 1: Write `backend/src/db/schema.sql`** — copy from spec Section 5.4:

```sql
CREATE TABLE IF NOT EXISTS channels (
  id TEXT PRIMARY KEY,
  url TEXT NOT NULL,
  title TEXT NOT NULL,
  added_at INTEGER NOT NULL,
  is_active INTEGER DEFAULT 1,
  last_polled_at INTEGER
);

CREATE TABLE IF NOT EXISTS videos (
  id TEXT PRIMARY KEY,
  channel_id TEXT NOT NULL REFERENCES channels(id),
  title TEXT NOT NULL,
  description TEXT,
  published_at INTEGER NOT NULL,
  duration_seconds INTEGER NOT NULL,
  aspect_ratio TEXT,
  default_language TEXT,
  thumbnail_url TEXT,
  transcript TEXT,
  discovered_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS scores (
  video_id TEXT PRIMARY KEY REFERENCES videos(id),
  overall_score INTEGER NOT NULL,
  category TEXT NOT NULL,
  clickbait_risk INTEGER NOT NULL,
  educational_value INTEGER NOT NULL,
  emotional_manipulation INTEGER NOT NULL,
  reasoning TEXT NOT NULL,
  model_used TEXT NOT NULL,
  scored_at INTEGER NOT NULL,
  decision TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS feed_items (
  video_id TEXT PRIMARY KEY REFERENCES videos(id),
  added_to_feed_at INTEGER NOT NULL,
  seen_at INTEGER,
  saved INTEGER DEFAULT 0,
  playback_failed INTEGER DEFAULT 0
);

CREATE TABLE IF NOT EXISTS sponsor_segments (
  video_id TEXT NOT NULL REFERENCES videos(id),
  start_seconds REAL NOT NULL,
  end_seconds REAL NOT NULL,
  category TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS downloaded_videos (
  video_id TEXT PRIMARY KEY REFERENCES videos(id),
  file_path TEXT NOT NULL,
  file_size_bytes INTEGER NOT NULL,
  video_codec TEXT,
  audio_codec TEXT,
  resolution_height INTEGER,
  downloaded_at INTEGER NOT NULL,
  last_served_at INTEGER
);

CREATE INDEX IF NOT EXISTS idx_feed_items_added ON feed_items(added_to_feed_at DESC);
CREATE INDEX IF NOT EXISTS idx_videos_channel ON videos(channel_id);
CREATE INDEX IF NOT EXISTS idx_downloaded_last_served ON downloaded_videos(last_served_at);
```

- [ ] **Step 2: Write the failing test — `backend/src/db/migrations.test.ts`**

```ts
import Database from "better-sqlite3";
import { describe, expect, it } from "vitest";
import { applyMigrations } from "./migrations.js";

describe("applyMigrations", () => {
  it("creates all 7 tables on a fresh database", () => {
    const db = new Database(":memory:");
    applyMigrations(db);
    const tables = db
      .prepare("SELECT name FROM sqlite_master WHERE type='table' ORDER BY name")
      .all() as { name: string }[];
    const names = tables.map((t) => t.name);
    expect(names).toEqual([
      "channels",
      "downloaded_videos",
      "feed_items",
      "scores",
      "sponsor_segments",
      "videos",
    ]);
  });

  it("is idempotent — can run twice without error", () => {
    const db = new Database(":memory:");
    applyMigrations(db);
    expect(() => applyMigrations(db)).not.toThrow();
  });
});
```

- [ ] **Step 3: Run test — verify it fails**

Run: `cd backend && pnpm test src/db/migrations.test.ts`
Expected: FAIL — `Cannot find module './migrations.js'`

- [ ] **Step 4: Implement `backend/src/db/migrations.ts`**

```ts
import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import type Database from "better-sqlite3";

const here = dirname(fileURLToPath(import.meta.url));

export function applyMigrations(db: Database.Database): void {
  const schema = readFileSync(join(here, "schema.sql"), "utf8");
  db.exec(schema);
}
```

- [ ] **Step 5: Implement `backend/src/db/connection.ts`**

```ts
import { mkdirSync } from "node:fs";
import { dirname } from "node:path";
import Database from "better-sqlite3";
import { applyMigrations } from "./migrations.js";

export function openDatabase(filePath: string): Database.Database {
  mkdirSync(dirname(filePath), { recursive: true });
  const db = new Database(filePath);
  db.pragma("journal_mode = WAL");
  db.pragma("foreign_keys = ON");
  applyMigrations(db);
  return db;
}
```

Note: Vitest's default resolution needs `.js` extensions in imports for NodeNext-style modules. We use `moduleResolution: "Bundler"` in tsconfig, so `.js` suffix is required for runtime.

- [ ] **Step 6: Run tests — verify passing**

Run: `cd backend && pnpm test src/db/migrations.test.ts`
Expected: PASS both tests.

Note: Count in first test is 6 (the expected array length). Update if needed — sqlite_master returns tables in alphabetical order when ORDER BY name is used. The expected array has 6 names but there are 7 tables in schema (channels, videos, scores, feed_items, sponsor_segments, downloaded_videos — that's 6). Confirm: six tables + sqlite_sequence is NOT auto-created since we don't use INTEGER PRIMARY KEY AUTOINCREMENT. So the expected is correct at 6.

- [ ] **Step 7: Commit**

```bash
git -C /Users/ayysir/Desktop/Hikari add backend/src/db
git -C /Users/ayysir/Desktop/Hikari commit -m "feat(backend): db schema and migrations"
```

---

## Task 2: yt-dlp Client Wrapper

**Files:**
- Create: `backend/src/yt-dlp/client.ts`
- Create: `backend/src/yt-dlp/client.test.ts`

**Purpose:** Single choke-point for spawning yt-dlp. All other modules call `runYtDlp()` rather than invoking execa directly. Enables a single mock point for tests.

- [ ] **Step 1: Write the failing test — `backend/src/yt-dlp/client.test.ts`**

```ts
import { describe, expect, it, vi } from "vitest";
import type { ExecaReturnValue } from "execa";
import { runYtDlp } from "./client.js";

vi.mock("execa", () => ({
  execa: vi.fn(),
}));

describe("runYtDlp", () => {
  it("calls yt-dlp with given args and returns stdout", async () => {
    const { execa } = await import("execa");
    vi.mocked(execa).mockResolvedValue({
      stdout: '{"id":"abc","title":"test"}',
      stderr: "",
      exitCode: 0,
    } as unknown as ExecaReturnValue);

    const result = await runYtDlp(["--dump-json", "https://youtube.com/watch?v=abc"]);
    expect(result.stdout).toBe('{"id":"abc","title":"test"}');
    expect(execa).toHaveBeenCalledWith(
      "yt-dlp",
      ["--dump-json", "https://youtube.com/watch?v=abc"],
      expect.objectContaining({ timeout: expect.any(Number) })
    );
  });

  it("throws YtDlpError with stderr when exit code is non-zero", async () => {
    const { execa } = await import("execa");
    vi.mocked(execa).mockRejectedValue({
      stderr: "ERROR: Video unavailable",
      exitCode: 1,
      shortMessage: "Command failed",
    });

    await expect(runYtDlp(["--dump-json", "bad-url"])).rejects.toThrow(/Video unavailable/);
  });
});
```

- [ ] **Step 2: Run test — verify fail**

Run: `cd backend && pnpm test src/yt-dlp/client.test.ts`
Expected: FAIL — `Cannot find module './client.js'`

- [ ] **Step 3: Implement `backend/src/yt-dlp/client.ts`**

```ts
import { execa } from "execa";

export class YtDlpError extends Error {
  constructor(
    message: string,
    public readonly stderr: string,
    public readonly exitCode: number | undefined,
  ) {
    super(message);
    this.name = "YtDlpError";
  }
}

export interface YtDlpResult {
  stdout: string;
  stderr: string;
}

const DEFAULT_TIMEOUT_MS = 120_000;

export async function runYtDlp(
  args: string[],
  opts: { timeoutMs?: number } = {},
): Promise<YtDlpResult> {
  try {
    const result = await execa("yt-dlp", args, {
      timeout: opts.timeoutMs ?? DEFAULT_TIMEOUT_MS,
    });
    return { stdout: result.stdout, stderr: result.stderr };
  } catch (err) {
    const e = err as { stderr?: string; exitCode?: number; shortMessage?: string };
    throw new YtDlpError(
      e.shortMessage ?? `yt-dlp failed: ${e.stderr ?? "unknown error"}`,
      e.stderr ?? "",
      e.exitCode,
    );
  }
}
```

- [ ] **Step 4: Run tests — verify pass**

Run: `cd backend && pnpm test src/yt-dlp/client.test.ts`
Expected: PASS both tests.

- [ ] **Step 5: Commit**

```bash
git -C /Users/ayysir/Desktop/Hikari add backend/src/yt-dlp
git -C /Users/ayysir/Desktop/Hikari commit -m "feat(backend): yt-dlp client wrapper"
```

---

## Task 3: Channel Resolver

**Files:**
- Create: `backend/src/monitor/channel-resolver.ts`
- Create: `backend/src/monitor/channel-resolver.test.ts`

**Purpose:** Given any form of YouTube channel URL (`@handle`, `/channel/UCxxx`, `/c/Name`, `/user/Name`), returns `{ channelId, title }`.

- [ ] **Step 1: Write the failing test**

```ts
import { beforeEach, describe, expect, it, vi } from "vitest";
import { resolveChannel } from "./channel-resolver.js";

vi.mock("../yt-dlp/client.js", () => ({
  runYtDlp: vi.fn(),
  YtDlpError: class YtDlpError extends Error {},
}));

describe("resolveChannel", () => {
  beforeEach(() => vi.clearAllMocks());

  it("extracts channel_id and uploader from yt-dlp JSON output", async () => {
    const { runYtDlp } = await import("../yt-dlp/client.js");
    vi.mocked(runYtDlp).mockResolvedValue({
      stdout: '{"channel_id":"UCabc123","channel":"3Blue1Brown"}',
      stderr: "",
    });

    const result = await resolveChannel("https://www.youtube.com/@3blue1brown");
    expect(result).toEqual({ channelId: "UCabc123", title: "3Blue1Brown" });
    expect(runYtDlp).toHaveBeenCalledWith([
      "--flat-playlist",
      "--playlist-items",
      "1",
      "--dump-single-json",
      "--no-warnings",
      "https://www.youtube.com/@3blue1brown",
    ]);
  });

  it("throws when channel_id is missing from yt-dlp output", async () => {
    const { runYtDlp } = await import("../yt-dlp/client.js");
    vi.mocked(runYtDlp).mockResolvedValue({ stdout: "{}", stderr: "" });

    await expect(resolveChannel("https://invalid")).rejects.toThrow(/channel_id/);
  });
});
```

- [ ] **Step 2: Run test — verify fail**

Run: `cd backend && pnpm test src/monitor/channel-resolver.test.ts`
Expected: FAIL — module not found.

- [ ] **Step 3: Implement `backend/src/monitor/channel-resolver.ts`**

```ts
import { runYtDlp } from "../yt-dlp/client.js";

export interface ResolvedChannel {
  channelId: string;
  title: string;
}

export async function resolveChannel(url: string): Promise<ResolvedChannel> {
  const { stdout } = await runYtDlp([
    "--flat-playlist",
    "--playlist-items",
    "1",
    "--dump-single-json",
    "--no-warnings",
    url,
  ]);

  const parsed = JSON.parse(stdout) as { channel_id?: string; channel?: string; uploader?: string };
  if (!parsed.channel_id) {
    throw new Error(`Could not extract channel_id from yt-dlp output for URL: ${url}`);
  }
  return {
    channelId: parsed.channel_id,
    title: parsed.channel ?? parsed.uploader ?? "Unknown",
  };
}
```

- [ ] **Step 4: Run tests — verify pass**

Run: `cd backend && pnpm test src/monitor/channel-resolver.test.ts`
Expected: PASS both tests.

- [ ] **Step 5: Commit**

```bash
git -C /Users/ayysir/Desktop/Hikari add backend/src/monitor/channel-resolver.ts backend/src/monitor/channel-resolver.test.ts
git -C /Users/ayysir/Desktop/Hikari commit -m "feat(backend): channel URL resolver"
```

---

## Task 4: RSS Poller

**Files:**
- Create: `backend/tests/fixtures/sample-channel-rss.xml`
- Create: `backend/src/monitor/rss-poller.ts`
- Create: `backend/src/monitor/rss-poller.test.ts`

**Purpose:** Fetch `https://www.youtube.com/feeds/videos.xml?channel_id=X` and return the most recent video IDs as a flat list.

- [ ] **Step 1: Create fixture — `backend/tests/fixtures/sample-channel-rss.xml`**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<feed xmlns="http://www.w3.org/2005/Atom" xmlns:yt="http://www.youtube.com/xml/schemas/2015">
  <title>3Blue1Brown</title>
  <entry>
    <yt:videoId>dQw4w9WgXcQ</yt:videoId>
    <title>Sample video one</title>
    <published>2026-04-23T10:00:00+00:00</published>
  </entry>
  <entry>
    <yt:videoId>aBcDeFgHiJk</yt:videoId>
    <title>Sample video two</title>
    <published>2026-04-22T10:00:00+00:00</published>
  </entry>
</feed>
```

- [ ] **Step 2: Write the failing test — `backend/src/monitor/rss-poller.test.ts`**

```ts
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { afterEach, describe, expect, it, vi } from "vitest";
import { fetchChannelFeed, parseChannelFeed } from "./rss-poller.js";

const fixture = readFileSync(
  resolve(import.meta.dirname, "../../tests/fixtures/sample-channel-rss.xml"),
  "utf8",
);

describe("parseChannelFeed", () => {
  it("extracts video IDs and titles in feed order", () => {
    const entries = parseChannelFeed(fixture);
    expect(entries).toEqual([
      { videoId: "dQw4w9WgXcQ", title: "Sample video one", publishedAt: 1776247200000 },
      { videoId: "aBcDeFgHiJk", title: "Sample video two", publishedAt: 1776160800000 },
    ]);
  });

  it("returns empty array for feed with no entries", () => {
    const empty = '<?xml version="1.0"?><feed xmlns="http://www.w3.org/2005/Atom"></feed>';
    expect(parseChannelFeed(empty)).toEqual([]);
  });
});

describe("fetchChannelFeed", () => {
  afterEach(() => vi.restoreAllMocks());

  it("calls YouTube RSS URL with the given channel_id", async () => {
    const fetchSpy = vi.spyOn(global, "fetch").mockResolvedValue(
      new Response(fixture, { status: 200 })
    );
    const entries = await fetchChannelFeed("UCxxx");
    expect(fetchSpy).toHaveBeenCalledWith(
      "https://www.youtube.com/feeds/videos.xml?channel_id=UCxxx"
    );
    expect(entries).toHaveLength(2);
  });

  it("throws on non-200 response", async () => {
    vi.spyOn(global, "fetch").mockResolvedValue(new Response("not found", { status: 404 }));
    await expect(fetchChannelFeed("UCmissing")).rejects.toThrow(/404/);
  });
});
```

Note: `publishedAt` in fixture is `2026-04-23T10:00:00+00:00` → verify that `1776247200000` matches; adjust if off by a day/hour when you run the test. If the epoch differs, update expected value to match actual parsed date.

- [ ] **Step 3: Run test — verify fail**

Run: `cd backend && pnpm test src/monitor/rss-poller.test.ts`
Expected: FAIL — module not found.

- [ ] **Step 4: Implement `backend/src/monitor/rss-poller.ts`**

```ts
import { XMLParser } from "fast-xml-parser";

export interface FeedEntry {
  videoId: string;
  title: string;
  publishedAt: number;
}

interface AtomEntry {
  "yt:videoId": string;
  title: string;
  published: string;
}

interface AtomFeed {
  feed: { entry?: AtomEntry | AtomEntry[] };
}

const parser = new XMLParser({
  ignoreAttributes: true,
  removeNSPrefix: false,
  textNodeName: "#text",
});

export function parseChannelFeed(xml: string): FeedEntry[] {
  const parsed = parser.parse(xml) as AtomFeed;
  const raw = parsed.feed?.entry;
  if (!raw) return [];
  const entries = Array.isArray(raw) ? raw : [raw];
  return entries.map((e) => ({
    videoId: e["yt:videoId"],
    title: e.title,
    publishedAt: new Date(e.published).getTime(),
  }));
}

export async function fetchChannelFeed(channelId: string): Promise<FeedEntry[]> {
  const url = `https://www.youtube.com/feeds/videos.xml?channel_id=${channelId}`;
  const res = await fetch(url);
  if (!res.ok) {
    throw new Error(`RSS fetch failed: ${res.status} for channel ${channelId}`);
  }
  const xml = await res.text();
  return parseChannelFeed(xml);
}
```

- [ ] **Step 5: Run tests — verify pass**

Run: `cd backend && pnpm test src/monitor/rss-poller.test.ts`
Expected: PASS (if publishedAt expected value is off, update the fixture expectation to match what Date.parse returns — compute it once, hardcode the exact number).

- [ ] **Step 6: Commit**

```bash
git -C /Users/ayysir/Desktop/Hikari add backend/src/monitor/rss-poller.ts backend/src/monitor/rss-poller.test.ts backend/tests/fixtures/sample-channel-rss.xml
git -C /Users/ayysir/Desktop/Hikari commit -m "feat(backend): RSS feed poller"
```

---

## Task 5: Video Metadata & Transcript Ingest

**Files:**
- Create: `backend/tests/fixtures/sample-video-metadata.json`
- Create: `backend/tests/fixtures/sample-captions.vtt`
- Create: `backend/src/ingest/metadata.ts`
- Create: `backend/src/ingest/metadata.test.ts`
- Create: `backend/src/ingest/transcript.ts`
- Create: `backend/src/ingest/transcript.test.ts`

- [ ] **Step 1: Create fixture `backend/tests/fixtures/sample-video-metadata.json`**

Minimal realistic yt-dlp --dump-json output:

```json
{
  "id": "dQw4w9WgXcQ",
  "title": "Why is this number everywhere?",
  "description": "A short description.",
  "duration": 612,
  "upload_date": "20260401",
  "thumbnail": "https://i.ytimg.com/vi/dQw4w9WgXcQ/maxres.jpg",
  "width": 1920,
  "height": 1080,
  "language": "en",
  "live_status": "not_live",
  "automatic_captions": {
    "en": [
      { "url": "https://youtube.com/caption-url-en", "ext": "vtt" }
    ]
  }
}
```

- [ ] **Step 2: Create fixture `backend/tests/fixtures/sample-captions.vtt`**

```
WEBVTT

00:00:00.000 --> 00:00:03.500
Welcome to the video.

00:00:03.500 --> 00:00:07.000
Today we look at the number e.
```

- [ ] **Step 3: Write failing test `backend/src/ingest/metadata.test.ts`**

```ts
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { fetchVideoMetadata } from "./metadata.js";

vi.mock("../yt-dlp/client.js", () => ({
  runYtDlp: vi.fn(),
  YtDlpError: class extends Error {},
}));

const fixture = readFileSync(
  resolve(import.meta.dirname, "../../tests/fixtures/sample-video-metadata.json"),
  "utf8",
);

describe("fetchVideoMetadata", () => {
  beforeEach(() => vi.clearAllMocks());

  it("parses yt-dlp JSON output into VideoMetadata shape", async () => {
    const { runYtDlp } = await import("../yt-dlp/client.js");
    vi.mocked(runYtDlp).mockResolvedValue({ stdout: fixture, stderr: "" });

    const meta = await fetchVideoMetadata("dQw4w9WgXcQ");
    expect(meta).toMatchObject({
      id: "dQw4w9WgXcQ",
      title: "Why is this number everywhere?",
      durationSeconds: 612,
      aspectRatio: "16:9",
      defaultLanguage: "en",
      captionsUrl: "https://youtube.com/caption-url-en",
      isLive: false,
    });
  });

  it("correctly classifies a 9:16 video as vertical aspect ratio", async () => {
    const { runYtDlp } = await import("../yt-dlp/client.js");
    const vertical = JSON.stringify({
      ...JSON.parse(fixture),
      width: 720,
      height: 1280,
    });
    vi.mocked(runYtDlp).mockResolvedValue({ stdout: vertical, stderr: "" });

    const meta = await fetchVideoMetadata("short-id");
    expect(meta.aspectRatio).toBe("9:16");
  });
});
```

- [ ] **Step 4: Run test — verify fail**

Run: `cd backend && pnpm test src/ingest/metadata.test.ts`
Expected: FAIL.

- [ ] **Step 5: Implement `backend/src/ingest/metadata.ts`**

```ts
import { runYtDlp } from "../yt-dlp/client.js";

export interface VideoMetadata {
  id: string;
  title: string;
  description: string;
  durationSeconds: number;
  publishedAt: number;
  thumbnailUrl: string;
  aspectRatio: string;
  defaultLanguage: string | null;
  isLive: boolean;
  captionsUrl: string | null;
}

interface YtDlpJson {
  id: string;
  title: string;
  description?: string;
  duration: number;
  upload_date: string;
  thumbnail?: string;
  width?: number;
  height?: number;
  language?: string;
  live_status?: string;
  automatic_captions?: Record<string, { url: string; ext: string }[]>;
}

export async function fetchVideoMetadata(videoId: string): Promise<VideoMetadata> {
  const { stdout } = await runYtDlp([
    "--dump-json",
    "--skip-download",
    "--write-auto-subs",
    "--sub-lang",
    "en,de",
    "--no-warnings",
    `https://www.youtube.com/watch?v=${videoId}`,
  ]);
  const d = JSON.parse(stdout) as YtDlpJson;

  return {
    id: d.id,
    title: d.title,
    description: d.description ?? "",
    durationSeconds: d.duration,
    publishedAt: parseYouTubeDate(d.upload_date),
    thumbnailUrl: d.thumbnail ?? "",
    aspectRatio: classifyAspect(d.width, d.height),
    defaultLanguage: d.language ?? null,
    isLive: d.live_status !== "not_live" && d.live_status !== undefined,
    captionsUrl: pickCaptionsUrl(d.automatic_captions),
  };
}

function parseYouTubeDate(yyyymmdd: string): number {
  // "20260401" → timestamp
  const y = Number(yyyymmdd.slice(0, 4));
  const m = Number(yyyymmdd.slice(4, 6)) - 1;
  const day = Number(yyyymmdd.slice(6, 8));
  return Date.UTC(y, m, day);
}

function classifyAspect(width?: number, height?: number): string {
  if (!width || !height) return "unknown";
  const ratio = width / height;
  if (ratio >= 1.5) return "16:9";
  if (ratio <= 0.7) return "9:16";
  return "1:1";
}

function pickCaptionsUrl(caps?: Record<string, { url: string; ext: string }[]>): string | null {
  if (!caps) return null;
  for (const lang of ["en", "de"]) {
    const entry = caps[lang]?.find((c) => c.ext === "vtt");
    if (entry) return entry.url;
  }
  return null;
}
```

- [ ] **Step 6: Run tests — verify pass**

Run: `cd backend && pnpm test src/ingest/metadata.test.ts`
Expected: PASS both tests.

- [ ] **Step 7: Write failing test `backend/src/ingest/transcript.test.ts`**

```ts
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { afterEach, describe, expect, it, vi } from "vitest";
import { fetchTranscript, parseVtt } from "./transcript.js";

const vtt = readFileSync(
  resolve(import.meta.dirname, "../../tests/fixtures/sample-captions.vtt"),
  "utf8",
);

describe("parseVtt", () => {
  it("extracts plain text lines, joined with spaces, no cues or timestamps", () => {
    expect(parseVtt(vtt)).toBe("Welcome to the video. Today we look at the number e.");
  });

  it("returns empty string for empty VTT", () => {
    expect(parseVtt("WEBVTT\n\n")).toBe("");
  });
});

describe("fetchTranscript", () => {
  afterEach(() => vi.restoreAllMocks());

  it("fetches URL and returns parsed transcript", async () => {
    vi.spyOn(global, "fetch").mockResolvedValue(new Response(vtt, { status: 200 }));
    const text = await fetchTranscript("https://example.com/captions.vtt");
    expect(text).toContain("Welcome to the video");
  });

  it("returns null on non-200", async () => {
    vi.spyOn(global, "fetch").mockResolvedValue(new Response("", { status: 404 }));
    expect(await fetchTranscript("https://example.com/missing.vtt")).toBeNull();
  });
});
```

- [ ] **Step 8: Run test — verify fail**

Run: `cd backend && pnpm test src/ingest/transcript.test.ts`

- [ ] **Step 9: Implement `backend/src/ingest/transcript.ts`**

```ts
export function parseVtt(vtt: string): string {
  const lines = vtt.split(/\r?\n/);
  const text: string[] = [];
  for (const raw of lines) {
    const line = raw.trim();
    if (!line) continue;
    if (line.startsWith("WEBVTT")) continue;
    if (/^\d+$/.test(line)) continue;
    if (/-->/.test(line)) continue;
    if (line.startsWith("NOTE ")) continue;
    text.push(line);
  }
  return text.join(" ");
}

export async function fetchTranscript(url: string): Promise<string | null> {
  const res = await fetch(url);
  if (!res.ok) return null;
  return parseVtt(await res.text());
}
```

- [ ] **Step 10: Run tests — verify pass**

Run: `cd backend && pnpm test src/ingest`
Expected: all ingest tests pass.

- [ ] **Step 11: Commit**

```bash
git -C /Users/ayysir/Desktop/Hikari add backend/src/ingest backend/tests/fixtures/sample-video-metadata.json backend/tests/fixtures/sample-captions.vtt
git -C /Users/ayysir/Desktop/Hikari commit -m "feat(backend): video metadata + VTT transcript ingest"
```

---

## Task 6: Scorer Types, Decision Rule, Prompt (Kadir-kun writes the prompt)

**Files:**
- Create: `backend/src/scorer/types.ts`
- Create: `backend/src/scorer/decision.ts`
- Create: `backend/src/scorer/decision.test.ts`
- Create: `backend/src/scorer/prompt.ts` ← **Kadir writes this**
- Create: `backend/src/scorer/prompt.test.ts`

- [ ] **Step 1: Write `backend/src/scorer/types.ts`**

```ts
export type Category =
  | "science"
  | "tech"
  | "philosophy"
  | "history"
  | "math"
  | "art"
  | "language"
  | "society"
  | "other";

export interface Score {
  overallScore: number;            // 0-100
  category: Category;
  clickbaitRisk: number;           // 0-10
  educationalValue: number;        // 0-10
  emotionalManipulation: number;   // 0-10
  reasoning: string;
}

export interface ScoredVideo {
  score: Score;
  modelUsed: string;
}

export interface Scorer {
  readonly name: string;
  score(input: {
    title: string;
    description: string;
    transcript: string | null;
    durationSeconds: number;
  }): Promise<ScoredVideo>;
}
```

- [ ] **Step 2: Write failing test `backend/src/scorer/decision.test.ts`**

```ts
import { describe, expect, it } from "vitest";
import { decide } from "./decision.js";
import type { Score } from "./types.js";

const base: Score = {
  overallScore: 70,
  category: "science",
  clickbaitRisk: 2,
  educationalValue: 8,
  emotionalManipulation: 1,
  reasoning: "",
};

describe("decide", () => {
  it("approves when all thresholds pass", () => {
    expect(decide(base)).toBe("approved");
  });

  it("rejects when overall_score < 60", () => {
    expect(decide({ ...base, overallScore: 59 })).toBe("rejected");
  });

  it("rejects when clickbait_risk > 4", () => {
    expect(decide({ ...base, clickbaitRisk: 5 })).toBe("rejected");
  });

  it("rejects when emotional_manipulation > 3", () => {
    expect(decide({ ...base, emotionalManipulation: 4 })).toBe("rejected");
  });
});
```

- [ ] **Step 3: Run test — verify fail**

Run: `cd backend && pnpm test src/scorer/decision.test.ts`

- [ ] **Step 4: Implement `backend/src/scorer/decision.ts`**

```ts
import type { Score } from "./types.js";

export type Decision = "approved" | "rejected";

export const DEFAULT_THRESHOLDS = {
  minOverall: 60,
  maxClickbait: 4,
  maxManipulation: 3,
} as const;

export function decide(score: Score, thresholds = DEFAULT_THRESHOLDS): Decision {
  if (score.overallScore < thresholds.minOverall) return "rejected";
  if (score.clickbaitRisk > thresholds.maxClickbait) return "rejected";
  if (score.emotionalManipulation > thresholds.maxManipulation) return "rejected";
  return "approved";
}
```

- [ ] **Step 5: Run tests — verify pass**

Run: `cd backend && pnpm test src/scorer/decision.test.ts`

- [ ] **Step 6: Write failing test `backend/src/scorer/prompt.test.ts`** — light test, real content comes from Kadir

```ts
import { describe, expect, it } from "vitest";
import { SCORING_SYSTEM_PROMPT } from "./prompt.js";

describe("SCORING_SYSTEM_PROMPT", () => {
  it("is a non-empty string", () => {
    expect(SCORING_SYSTEM_PROMPT).toBeTypeOf("string");
    expect(SCORING_SYSTEM_PROMPT.length).toBeGreaterThan(100);
  });

  it("mentions key concepts Kadir cares about", () => {
    // These are ANCHOR CHECKS — Kadir's prompt must encode these values.
    // Update expectations if Kadir intentionally drops them.
    expect(SCORING_SYSTEM_PROMPT.toLowerCase()).toMatch(/clickbait|sensationalis/);
    expect(SCORING_SYSTEM_PROMPT.toLowerCase()).toMatch(/educational|lehrreich|learn/);
  });
});
```

- [ ] **Step 7: ⚠️ Kadir-kun: Write `backend/src/scorer/prompt.ts`**

This is the single most important file in Hikari. It defines what "good content" means. Write the system prompt in plain language, in German or English — your call. Keep it 5–15 lines. Address the LLM directly ("You are a content curator..."). Describe what to boost (deep, wissensfördernd, non-sensationalist) and what to reject (Rage-Bait, Drama, shallow listicles, manipulative thumbnails in title wording, etc.).

Template to edit (**minimally valid placeholder**, replace with your values):

```ts
export const SCORING_SYSTEM_PROMPT = `
You score YouTube videos for Hikari, a curated reels app. Videos come from
channels the user already trusts — your job is ONLY to catch outlier
low-quality posts.

Score on four axes:
- overallScore (0-100): how valuable is this for a user who wants to learn
  and think better?
- clickbaitRisk (0-10): how much does the title/description rely on
  outrage, exaggeration, or fake promises?
- educationalValue (0-10): does watching this leave the viewer with real
  knowledge or insight?
- emotionalManipulation (0-10): does this try to weaponize fear, envy, or
  rage to keep the viewer watching?

Prefer: depth, nuance, real explanations, genuine curiosity.
Reject: sensationalism, drama, listicles without substance, fake urgency.

Respond with valid JSON matching the Score schema. Reason in 1–2 sentences.
`.trim();
```

After writing, run the test and confirm it still passes.

- [ ] **Step 8: Run tests — verify pass**

Run: `cd backend && pnpm test src/scorer/prompt.test.ts`

- [ ] **Step 9: Commit**

```bash
git -C /Users/ayysir/Desktop/Hikari add backend/src/scorer
git -C /Users/ayysir/Desktop/Hikari commit -m "feat(backend): scorer types, decision rule, and scoring prompt"
```

---

## Task 7: Claude Scorer

**Files:**
- Create: `backend/src/scorer/claude-scorer.ts`
- Create: `backend/src/scorer/claude-scorer.test.ts`

**Purpose:** Implements `Scorer` interface using `@anthropic-ai/sdk`. Uses prompt caching on the system prompt (encoded in `scorer/prompt.ts`) and tool-use structured output to return a typed `Score`.

- [ ] **Step 1: Write failing test `backend/src/scorer/claude-scorer.test.ts`**

```ts
import { beforeEach, describe, expect, it, vi } from "vitest";
import { ClaudeScorer } from "./claude-scorer.js";

const mockCreate = vi.fn();

vi.mock("@anthropic-ai/sdk", () => ({
  default: class {
    messages = { create: mockCreate };
  },
}));

describe("ClaudeScorer", () => {
  beforeEach(() => mockCreate.mockReset());

  it("returns typed Score from tool_use response", async () => {
    mockCreate.mockResolvedValue({
      content: [
        {
          type: "tool_use",
          name: "record_score",
          input: {
            overallScore: 82,
            category: "science",
            clickbaitRisk: 1,
            educationalValue: 9,
            emotionalManipulation: 0,
            reasoning: "Deep dive on prime numbers.",
          },
        },
      ],
    });

    const scorer = new ClaudeScorer({ apiKey: "test", model: "claude-haiku-4-5" });
    const result = await scorer.score({
      title: "Why primes are weird",
      description: "A look at prime distribution.",
      transcript: "Some transcript text...",
      durationSeconds: 420,
    });

    expect(result.modelUsed).toBe("claude-haiku-4-5");
    expect(result.score.overallScore).toBe(82);
    expect(result.score.category).toBe("science");
  });

  it("throws when no tool_use block in response", async () => {
    mockCreate.mockResolvedValue({
      content: [{ type: "text", text: "I refuse." }],
    });
    const scorer = new ClaudeScorer({ apiKey: "test", model: "claude-haiku-4-5" });
    await expect(
      scorer.score({ title: "x", description: "", transcript: null, durationSeconds: 60 }),
    ).rejects.toThrow(/tool_use/);
  });
});
```

- [ ] **Step 2: Run test — verify fail**

Run: `cd backend && pnpm test src/scorer/claude-scorer.test.ts`

- [ ] **Step 3: Implement `backend/src/scorer/claude-scorer.ts`**

```ts
import Anthropic from "@anthropic-ai/sdk";
import { SCORING_SYSTEM_PROMPT } from "./prompt.js";
import type { Score, ScoredVideo, Scorer } from "./types.js";

const SCORE_TOOL = {
  name: "record_score",
  description: "Record the score for this video.",
  input_schema: {
    type: "object" as const,
    required: [
      "overallScore",
      "category",
      "clickbaitRisk",
      "educationalValue",
      "emotionalManipulation",
      "reasoning",
    ],
    properties: {
      overallScore: { type: "integer", minimum: 0, maximum: 100 },
      category: {
        type: "string",
        enum: [
          "science",
          "tech",
          "philosophy",
          "history",
          "math",
          "art",
          "language",
          "society",
          "other",
        ],
      },
      clickbaitRisk: { type: "integer", minimum: 0, maximum: 10 },
      educationalValue: { type: "integer", minimum: 0, maximum: 10 },
      emotionalManipulation: { type: "integer", minimum: 0, maximum: 10 },
      reasoning: { type: "string", minLength: 1, maxLength: 500 },
    },
  },
};

export interface ClaudeScorerOptions {
  apiKey: string;
  model: string;
}

export class ClaudeScorer implements Scorer {
  readonly name = "claude";
  private readonly client: Anthropic;
  private readonly model: string;

  constructor(opts: ClaudeScorerOptions) {
    this.client = new Anthropic({ apiKey: opts.apiKey });
    this.model = opts.model;
  }

  async score(input: {
    title: string;
    description: string;
    transcript: string | null;
    durationSeconds: number;
  }): Promise<ScoredVideo> {
    const userText = buildUserMessage(input);

    const response = await this.client.messages.create({
      model: this.model,
      max_tokens: 1024,
      system: [
        {
          type: "text",
          text: SCORING_SYSTEM_PROMPT,
          cache_control: { type: "ephemeral" },
        },
      ],
      tools: [SCORE_TOOL],
      tool_choice: { type: "tool", name: "record_score" },
      messages: [{ role: "user", content: userText }],
    });

    const block = response.content.find((b) => b.type === "tool_use");
    if (!block || block.type !== "tool_use") {
      throw new Error("Claude did not return a tool_use block");
    }
    return { score: block.input as Score, modelUsed: this.model };
  }
}

function buildUserMessage(input: {
  title: string;
  description: string;
  transcript: string | null;
  durationSeconds: number;
}): string {
  const transcriptPart = input.transcript
    ? `TRANSCRIPT (first 2000 chars):\n${input.transcript.slice(0, 2000)}`
    : "TRANSCRIPT: (not available — score on title+description alone; apply stricter thresholds)";
  return [
    `TITLE: ${input.title}`,
    `DURATION: ${input.durationSeconds}s`,
    `DESCRIPTION:\n${input.description.slice(0, 1000)}`,
    transcriptPart,
  ].join("\n\n");
}
```

- [ ] **Step 4: Run tests — verify pass**

Run: `cd backend && pnpm test src/scorer/claude-scorer.test.ts`

- [ ] **Step 5: Commit**

```bash
git -C /Users/ayysir/Desktop/Hikari add backend/src/scorer/claude-scorer.ts backend/src/scorer/claude-scorer.test.ts
git -C /Users/ayysir/Desktop/Hikari commit -m "feat(backend): Claude scorer implementation"
```

---

## Task 8: Ollama Scorer

**Files:**
- Create: `backend/src/scorer/ollama-scorer.ts`
- Create: `backend/src/scorer/ollama-scorer.test.ts`

**Purpose:** Alternative `Scorer` using a local Ollama server. Uses JSON Schema via Ollama's `format` parameter (supported in recent versions for structured output).

- [ ] **Step 1: Write failing test `backend/src/scorer/ollama-scorer.test.ts`**

```ts
import { afterEach, describe, expect, it, vi } from "vitest";
import { OllamaScorer } from "./ollama-scorer.js";

describe("OllamaScorer", () => {
  afterEach(() => vi.restoreAllMocks());

  it("POSTs to /api/chat and parses JSON-mode response", async () => {
    const fetchSpy = vi.spyOn(global, "fetch").mockResolvedValue(
      new Response(
        JSON.stringify({
          message: {
            content: JSON.stringify({
              overallScore: 75,
              category: "tech",
              clickbaitRisk: 2,
              educationalValue: 8,
              emotionalManipulation: 1,
              reasoning: "Clear tutorial on rust.",
            }),
          },
        }),
        { status: 200 },
      ),
    );

    const scorer = new OllamaScorer({ baseUrl: "http://localhost:11434", model: "qwen2.5:14b" });
    const result = await scorer.score({
      title: "Rust lifetimes explained",
      description: "",
      transcript: null,
      durationSeconds: 300,
    });

    expect(result.modelUsed).toBe("qwen2.5:14b");
    expect(result.score.overallScore).toBe(75);
    expect(fetchSpy).toHaveBeenCalledWith(
      "http://localhost:11434/api/chat",
      expect.objectContaining({ method: "POST" }),
    );
  });

  it("throws on non-200", async () => {
    vi.spyOn(global, "fetch").mockResolvedValue(new Response("boom", { status: 500 }));
    const scorer = new OllamaScorer({ baseUrl: "http://localhost:11434", model: "qwen2.5:14b" });
    await expect(
      scorer.score({ title: "x", description: "", transcript: null, durationSeconds: 60 }),
    ).rejects.toThrow(/Ollama/);
  });
});
```

- [ ] **Step 2: Run test — verify fail**

Run: `cd backend && pnpm test src/scorer/ollama-scorer.test.ts`

- [ ] **Step 3: Implement `backend/src/scorer/ollama-scorer.ts`**

```ts
import { SCORING_SYSTEM_PROMPT } from "./prompt.js";
import type { Score, ScoredVideo, Scorer } from "./types.js";

export interface OllamaScorerOptions {
  baseUrl: string;
  model: string;
}

const JSON_SCHEMA = {
  type: "object",
  required: [
    "overallScore",
    "category",
    "clickbaitRisk",
    "educationalValue",
    "emotionalManipulation",
    "reasoning",
  ],
  properties: {
    overallScore: { type: "integer", minimum: 0, maximum: 100 },
    category: {
      type: "string",
      enum: [
        "science",
        "tech",
        "philosophy",
        "history",
        "math",
        "art",
        "language",
        "society",
        "other",
      ],
    },
    clickbaitRisk: { type: "integer", minimum: 0, maximum: 10 },
    educationalValue: { type: "integer", minimum: 0, maximum: 10 },
    emotionalManipulation: { type: "integer", minimum: 0, maximum: 10 },
    reasoning: { type: "string" },
  },
} as const;

export class OllamaScorer implements Scorer {
  readonly name = "ollama";
  constructor(private readonly opts: OllamaScorerOptions) {}

  async score(input: {
    title: string;
    description: string;
    transcript: string | null;
    durationSeconds: number;
  }): Promise<ScoredVideo> {
    const userMessage =
      `TITLE: ${input.title}\n\nDURATION: ${input.durationSeconds}s\n\n` +
      `DESCRIPTION:\n${input.description.slice(0, 1000)}\n\n` +
      (input.transcript
        ? `TRANSCRIPT (first 2000 chars):\n${input.transcript.slice(0, 2000)}`
        : "TRANSCRIPT: (not available)");

    const res = await fetch(`${this.opts.baseUrl}/api/chat`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      body: JSON.stringify({
        model: this.opts.model,
        messages: [
          { role: "system", content: SCORING_SYSTEM_PROMPT },
          { role: "user", content: userMessage },
        ],
        format: JSON_SCHEMA,
        stream: false,
      }),
    });
    if (!res.ok) {
      throw new Error(`Ollama request failed: ${res.status} ${await res.text()}`);
    }
    const body = (await res.json()) as { message: { content: string } };
    const score = JSON.parse(body.message.content) as Score;
    return { score, modelUsed: this.opts.model };
  }
}
```

- [ ] **Step 4: Run tests — verify pass**

Run: `cd backend && pnpm test src/scorer`

- [ ] **Step 5: Commit**

```bash
git -C /Users/ayysir/Desktop/Hikari add backend/src/scorer/ollama-scorer.ts backend/src/scorer/ollama-scorer.test.ts
git -C /Users/ayysir/Desktop/Hikari commit -m "feat(backend): Ollama scorer implementation"
```

---

## Task 9: SponsorBlock Client

**Files:**
- Create: `backend/src/sponsorblock/client.ts`
- Create: `backend/src/sponsorblock/client.test.ts`

**Purpose:** Fetch Sponsor-segment timestamps for a video from sponsor.ajay.pw.

- [ ] **Step 1: Write failing test `backend/src/sponsorblock/client.test.ts`**

```ts
import { afterEach, describe, expect, it, vi } from "vitest";
import { fetchSponsorSegments } from "./client.js";

describe("fetchSponsorSegments", () => {
  afterEach(() => vi.restoreAllMocks());

  it("maps API response to flat segment list", async () => {
    vi.spyOn(global, "fetch").mockResolvedValue(
      new Response(
        JSON.stringify([
          { category: "sponsor", segment: [12.5, 38.2] },
          { category: "selfpromo", segment: [120, 145] },
        ]),
        { status: 200 },
      ),
    );
    const segs = await fetchSponsorSegments("abc123");
    expect(segs).toEqual([
      { category: "sponsor", startSeconds: 12.5, endSeconds: 38.2 },
      { category: "selfpromo", startSeconds: 120, endSeconds: 145 },
    ]);
  });

  it("returns empty array on 404", async () => {
    vi.spyOn(global, "fetch").mockResolvedValue(new Response("[]", { status: 404 }));
    expect(await fetchSponsorSegments("noseg")).toEqual([]);
  });
});
```

- [ ] **Step 2: Run — fail**

Run: `cd backend && pnpm test src/sponsorblock/client.test.ts`

- [ ] **Step 3: Implement `backend/src/sponsorblock/client.ts`**

```ts
export interface SponsorSegment {
  category: string;
  startSeconds: number;
  endSeconds: number;
}

interface ApiSegment {
  category: string;
  segment: [number, number];
}

export async function fetchSponsorSegments(videoId: string): Promise<SponsorSegment[]> {
  const url = `https://sponsor.ajay.pw/api/skipSegments?videoID=${encodeURIComponent(videoId)}`;
  const res = await fetch(url);
  if (res.status === 404 || !res.ok) return [];
  const data = (await res.json()) as ApiSegment[];
  return data.map((s) => ({
    category: s.category,
    startSeconds: s.segment[0],
    endSeconds: s.segment[1],
  }));
}
```

- [ ] **Step 4: Run — pass**

Run: `cd backend && pnpm test src/sponsorblock`

- [ ] **Step 5: Commit**

```bash
git -C /Users/ayysir/Desktop/Hikari add backend/src/sponsorblock
git -C /Users/ayysir/Desktop/Hikari commit -m "feat(backend): SponsorBlock client"
```

---

## Task 10: Download Worker

**Files:**
- Create: `backend/src/download/worker.ts`
- Create: `backend/src/download/worker.test.ts`

**Purpose:** Given a videoId + target directory, run yt-dlp to download a 720p MP4. Return `{ filePath, fileSizeBytes, codecInfo }`.

- [ ] **Step 1: Write failing test `backend/src/download/worker.test.ts`**

```ts
import { mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { downloadVideo } from "./worker.js";

vi.mock("../yt-dlp/client.js", () => ({
  runYtDlp: vi.fn(),
  YtDlpError: class extends Error {},
}));

describe("downloadVideo", () => {
  beforeEach(() => vi.clearAllMocks());

  it("invokes yt-dlp with expected format args and returns file metadata", async () => {
    const { runYtDlp } = await import("../yt-dlp/client.js");
    const dir = mkdtempSync(join(tmpdir(), "hikari-test-"));
    const videoId = "abc123";
    const path = join(dir, `${videoId}.mp4`);

    vi.mocked(runYtDlp).mockImplementation(async () => {
      writeFileSync(path, Buffer.alloc(1024, 0xff));
      return { stdout: "", stderr: "" };
    });

    const result = await downloadVideo({ videoId, outDir: dir });

    expect(result.filePath).toBe(path);
    expect(result.fileSizeBytes).toBe(1024);
    expect(runYtDlp).toHaveBeenCalledWith(
      expect.arrayContaining([
        "-f",
        "bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/best[height<=720]",
        "--merge-output-format",
        "mp4",
        "-o",
        join(dir, "%(id)s.%(ext)s"),
      ]),
      expect.any(Object),
    );
  });

  it("throws when download fails to create the expected file", async () => {
    const { runYtDlp } = await import("../yt-dlp/client.js");
    const dir = mkdtempSync(join(tmpdir(), "hikari-test-"));
    vi.mocked(runYtDlp).mockResolvedValue({ stdout: "", stderr: "" });

    await expect(downloadVideo({ videoId: "missing", outDir: dir })).rejects.toThrow(/file/);
  });
});
```

- [ ] **Step 2: Run — fail**

Run: `cd backend && pnpm test src/download/worker.test.ts`

- [ ] **Step 3: Implement `backend/src/download/worker.ts`**

```ts
import { existsSync, statSync } from "node:fs";
import { join } from "node:path";
import { runYtDlp } from "../yt-dlp/client.js";

export interface DownloadResult {
  filePath: string;
  fileSizeBytes: number;
}

export async function downloadVideo(opts: {
  videoId: string;
  outDir: string;
  timeoutMs?: number;
}): Promise<DownloadResult> {
  const outTemplate = join(opts.outDir, "%(id)s.%(ext)s");
  await runYtDlp(
    [
      "-f",
      "bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/best[height<=720]",
      "--merge-output-format",
      "mp4",
      "-o",
      outTemplate,
      "--no-warnings",
      `https://www.youtube.com/watch?v=${opts.videoId}`,
    ],
    { timeoutMs: opts.timeoutMs ?? 10 * 60_000 },
  );

  const filePath = join(opts.outDir, `${opts.videoId}.mp4`);
  if (!existsSync(filePath)) {
    throw new Error(`Download completed but file not found at ${filePath}`);
  }
  return { filePath, fileSizeBytes: statSync(filePath).size };
}
```

- [ ] **Step 4: Run — pass**

Run: `cd backend && pnpm test src/download/worker.test.ts`

- [ ] **Step 5: Commit**

```bash
git -C /Users/ayysir/Desktop/Hikari add backend/src/download
git -C /Users/ayysir/Desktop/Hikari commit -m "feat(backend): video download worker"
```

---

## Task 11: LRU Cleanup

**Files:**
- Create: `backend/src/download/cleanup.ts`
- Create: `backend/src/download/cleanup.test.ts`

**Purpose:** Query `downloaded_videos` LEFT JOIN `feed_items`, compute total bytes. If > limit, delete oldest `last_served_at` rows where `saved = 0`, unlinking files until under limit.

- [ ] **Step 1: Write failing test `backend/src/download/cleanup.test.ts`**

```ts
import { writeFileSync, existsSync, mkdtempSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import Database from "better-sqlite3";
import { beforeEach, describe, expect, it } from "vitest";
import { applyMigrations } from "../db/migrations.js";
import { runCleanup } from "./cleanup.js";

function seedVideo(db: Database.Database, id: string, size: number, lastServed: number, saved: 0 | 1, filePath: string) {
  db.prepare(
    "INSERT INTO channels (id, url, title, added_at) VALUES ('UC1', 'x', 'x', 0)",
  ).run();
  db.exec(`UPDATE channels SET id = 'UC1' WHERE id = 'UC1'`); // ensure exists
  db.prepare(
    `INSERT OR IGNORE INTO channels (id, url, title, added_at) VALUES ('UC1','x','x',0)`,
  ).run();
  db.prepare(
    `INSERT INTO videos (id, channel_id, title, published_at, duration_seconds, discovered_at)
     VALUES (?, 'UC1', ?, 0, 0, 0)`,
  ).run(id, `title-${id}`);
  db.prepare(
    `INSERT INTO feed_items (video_id, added_to_feed_at, saved) VALUES (?, 0, ?)`,
  ).run(id, saved);
  db.prepare(
    `INSERT INTO downloaded_videos (video_id, file_path, file_size_bytes, downloaded_at, last_served_at)
     VALUES (?, ?, ?, 0, ?)`,
  ).run(id, filePath, size, lastServed);
}

describe("runCleanup", () => {
  let db: Database.Database;
  let videoDir: string;

  beforeEach(() => {
    db = new Database(":memory:");
    applyMigrations(db);
    videoDir = mkdtempSync(join(tmpdir(), "hikari-cleanup-"));
  });

  it("deletes oldest non-saved files when over limit", () => {
    // 3 videos, 4 MB each, limit 10 MB total → must delete 1 oldest non-saved
    for (const { id, lastServed, saved } of [
      { id: "old-unsaved", lastServed: 1000, saved: 0 as const },
      { id: "mid-saved", lastServed: 2000, saved: 1 as const },
      { id: "new-unsaved", lastServed: 3000, saved: 0 as const },
    ]) {
      const p = join(videoDir, `${id}.mp4`);
      writeFileSync(p, Buffer.alloc(4 * 1024 * 1024, 0));
      seedVideo(db, id, 4 * 1024 * 1024, lastServed, saved, p);
    }

    const result = runCleanup({ db, limitBytes: 10 * 1024 * 1024 });

    expect(result.deletedCount).toBe(1);
    expect(result.deletedVideoIds).toEqual(["old-unsaved"]);
    expect(existsSync(join(videoDir, "old-unsaved.mp4"))).toBe(false);
    expect(existsSync(join(videoDir, "mid-saved.mp4"))).toBe(true);
    expect(db.prepare("SELECT COUNT(*) as c FROM downloaded_videos").get()).toEqual({ c: 2 });
  });

  it("never deletes saved videos, even if over limit", () => {
    for (const { id, lastServed } of [
      { id: "saved-1", lastServed: 1000 },
      { id: "saved-2", lastServed: 2000 },
    ]) {
      const p = join(videoDir, `${id}.mp4`);
      writeFileSync(p, Buffer.alloc(8 * 1024 * 1024, 0));
      seedVideo(db, id, 8 * 1024 * 1024, lastServed, 1, p);
    }

    const result = runCleanup({ db, limitBytes: 10 * 1024 * 1024 });
    expect(result.deletedCount).toBe(0);
    expect(existsSync(join(videoDir, "saved-1.mp4"))).toBe(true);
  });
});
```

- [ ] **Step 2: Run — fail**

Run: `cd backend && pnpm test src/download/cleanup.test.ts`

- [ ] **Step 3: Implement `backend/src/download/cleanup.ts`**

```ts
import { existsSync, unlinkSync } from "node:fs";
import type Database from "better-sqlite3";

interface CleanupCandidate {
  video_id: string;
  file_path: string;
  file_size_bytes: number;
}

export interface CleanupResult {
  deletedCount: number;
  deletedVideoIds: string[];
  freedBytes: number;
  finalBytes: number;
}

export function runCleanup(opts: {
  db: Database.Database;
  limitBytes: number;
}): CleanupResult {
  const totalRow = opts.db
    .prepare("SELECT COALESCE(SUM(file_size_bytes), 0) as total FROM downloaded_videos")
    .get() as { total: number };

  let currentBytes = totalRow.total;
  const deleted: string[] = [];
  let freed = 0;

  if (currentBytes <= opts.limitBytes) {
    return { deletedCount: 0, deletedVideoIds: [], freedBytes: 0, finalBytes: currentBytes };
  }

  const candidates = opts.db
    .prepare(
      `SELECT dv.video_id, dv.file_path, dv.file_size_bytes
       FROM downloaded_videos dv
       JOIN feed_items fi ON fi.video_id = dv.video_id
       WHERE fi.saved = 0
       ORDER BY COALESCE(dv.last_served_at, dv.downloaded_at) ASC`,
    )
    .all() as CleanupCandidate[];

  const removeRow = opts.db.prepare("DELETE FROM downloaded_videos WHERE video_id = ?");

  for (const c of candidates) {
    if (currentBytes <= opts.limitBytes) break;
    if (existsSync(c.file_path)) {
      unlinkSync(c.file_path);
    }
    removeRow.run(c.video_id);
    deleted.push(c.video_id);
    currentBytes -= c.file_size_bytes;
    freed += c.file_size_bytes;
  }

  return {
    deletedCount: deleted.length,
    deletedVideoIds: deleted,
    freedBytes: freed,
    finalBytes: currentBytes,
  };
}
```

- [ ] **Step 4: Run — pass**

Run: `cd backend && pnpm test src/download/cleanup.test.ts`

- [ ] **Step 5: Commit**

```bash
git -C /Users/ayysir/Desktop/Hikari add backend/src/download/cleanup.ts backend/src/download/cleanup.test.ts
git -C /Users/ayysir/Desktop/Hikari commit -m "feat(backend): LRU cleanup policy"
```

---

## Task 12: Pipeline Orchestrator

**Files:**
- Create: `backend/src/pipeline/orchestrator.ts`
- Create: `backend/src/pipeline/orchestrator.test.ts`

**Purpose:** Single function `processNewVideo(videoId, channelId)` that runs Ingest → Score → Decision → (if approved) Download → write all DB rows in one transaction at the end. Isolated from cron — can be called from anywhere.

- [ ] **Step 1: Write failing test `backend/src/pipeline/orchestrator.test.ts`**

```ts
import Database from "better-sqlite3";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { applyMigrations } from "../db/migrations.js";
import { processNewVideo } from "./orchestrator.js";
import type { Scorer } from "../scorer/types.js";

const fakeMetadata = {
  id: "vid1",
  title: "Deep prime talk",
  description: "Primes are cool.",
  durationSeconds: 600,
  publishedAt: 1_700_000_000_000,
  thumbnailUrl: "https://t",
  aspectRatio: "16:9",
  defaultLanguage: "en",
  isLive: false,
  captionsUrl: null,
};

function makeScorer(decision: "approve" | "reject"): Scorer {
  return {
    name: "mock",
    async score() {
      return {
        modelUsed: "mock-v1",
        score: {
          overallScore: decision === "approve" ? 80 : 40,
          category: "math",
          clickbaitRisk: 1,
          educationalValue: 9,
          emotionalManipulation: 0,
          reasoning: "test",
        },
      };
    },
  };
}

describe("processNewVideo", () => {
  let db: Database.Database;
  beforeEach(() => {
    db = new Database(":memory:");
    applyMigrations(db);
    db.prepare(
      "INSERT INTO channels (id, url, title, added_at) VALUES ('UC1','x','chan',0)",
    ).run();
  });

  it("writes approved video to feed_items and triggers download", async () => {
    const download = vi.fn(async () => ({
      filePath: "/fake/vid1.mp4",
      fileSizeBytes: 1024,
    }));
    await processNewVideo({
      db,
      videoId: "vid1",
      channelId: "UC1",
      fetchMetadata: async () => fakeMetadata,
      fetchTranscript: async () => null,
      fetchSponsorSegments: async () => [],
      scorer: makeScorer("approve"),
      download,
    });

    const feed = db.prepare("SELECT * FROM feed_items WHERE video_id='vid1'").all();
    expect(feed).toHaveLength(1);
    expect(download).toHaveBeenCalledOnce();
    const downloaded = db.prepare("SELECT * FROM downloaded_videos WHERE video_id='vid1'").get();
    expect(downloaded).toBeTruthy();
  });

  it("writes rejected video to scores only, no feed_items, no download", async () => {
    const download = vi.fn();
    await processNewVideo({
      db,
      videoId: "vid1",
      channelId: "UC1",
      fetchMetadata: async () => fakeMetadata,
      fetchTranscript: async () => null,
      fetchSponsorSegments: async () => [],
      scorer: makeScorer("reject"),
      download,
    });

    const scores = db.prepare("SELECT decision FROM scores WHERE video_id='vid1'").get();
    expect(scores).toEqual({ decision: "rejected" });
    expect(db.prepare("SELECT COUNT(*) as c FROM feed_items").get()).toEqual({ c: 0 });
    expect(download).not.toHaveBeenCalled();
  });
});
```

- [ ] **Step 2: Run — fail**

Run: `cd backend && pnpm test src/pipeline/orchestrator.test.ts`

- [ ] **Step 3: Implement `backend/src/pipeline/orchestrator.ts`**

```ts
import type Database from "better-sqlite3";
import type { VideoMetadata } from "../ingest/metadata.js";
import { decide } from "../scorer/decision.js";
import type { Scorer } from "../scorer/types.js";
import type { SponsorSegment } from "../sponsorblock/client.js";
import type { DownloadResult } from "../download/worker.js";

export interface ProcessNewVideoDeps {
  db: Database.Database;
  videoId: string;
  channelId: string;
  fetchMetadata: (videoId: string) => Promise<VideoMetadata>;
  fetchTranscript: (url: string) => Promise<string | null>;
  fetchSponsorSegments: (videoId: string) => Promise<SponsorSegment[]>;
  scorer: Scorer;
  download: (videoId: string) => Promise<DownloadResult>;
}

export async function processNewVideo(deps: ProcessNewVideoDeps): Promise<void> {
  const { db, videoId, channelId } = deps;

  const existing = db
    .prepare("SELECT 1 FROM videos WHERE id = ?")
    .get(videoId);
  if (existing) return; // already processed

  const meta = await deps.fetchMetadata(videoId);

  // Hard filters (section 5.2 spec)
  if (meta.isLive) return;
  if (meta.durationSeconds < 30 || meta.durationSeconds > 600) return;

  const transcript = meta.captionsUrl ? await deps.fetchTranscript(meta.captionsUrl) : null;
  const [scored, sponsors] = await Promise.all([
    deps.scorer.score({
      title: meta.title,
      description: meta.description,
      transcript,
      durationSeconds: meta.durationSeconds,
    }),
    deps.fetchSponsorSegments(videoId),
  ]);

  const decision = decide(scored.score);
  const now = Date.now();

  if (decision === "approved") {
    const dl = await deps.download(videoId);
    db.transaction(() => {
      insertVideo(db, meta, transcript, channelId);
      insertScore(db, videoId, scored, decision, now);
      insertSponsors(db, videoId, sponsors);
      insertDownload(db, videoId, dl, now);
      insertFeedItem(db, videoId, now);
    })();
  } else {
    db.transaction(() => {
      insertVideo(db, meta, transcript, channelId);
      insertScore(db, videoId, scored, decision, now);
    })();
  }
}

function insertVideo(
  db: Database.Database,
  m: VideoMetadata,
  transcript: string | null,
  channelId: string,
): void {
  db.prepare(
    `INSERT OR IGNORE INTO videos
     (id, channel_id, title, description, published_at, duration_seconds,
      aspect_ratio, default_language, thumbnail_url, transcript, discovered_at)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
  ).run(
    m.id,
    channelId,
    m.title,
    m.description,
    m.publishedAt,
    m.durationSeconds,
    m.aspectRatio,
    m.defaultLanguage,
    m.thumbnailUrl,
    transcript,
    Date.now(),
  );
}

function insertScore(
  db: Database.Database,
  videoId: string,
  scored: { score: import("../scorer/types.js").Score; modelUsed: string },
  decision: "approved" | "rejected",
  now: number,
): void {
  db.prepare(
    `INSERT INTO scores
     (video_id, overall_score, category, clickbait_risk, educational_value,
      emotional_manipulation, reasoning, model_used, scored_at, decision)
     VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
  ).run(
    videoId,
    scored.score.overallScore,
    scored.score.category,
    scored.score.clickbaitRisk,
    scored.score.educationalValue,
    scored.score.emotionalManipulation,
    scored.score.reasoning,
    scored.modelUsed,
    now,
    decision,
  );
}

function insertSponsors(
  db: Database.Database,
  videoId: string,
  segments: SponsorSegment[],
): void {
  const stmt = db.prepare(
    `INSERT INTO sponsor_segments (video_id, start_seconds, end_seconds, category)
     VALUES (?, ?, ?, ?)`,
  );
  for (const s of segments) {
    stmt.run(videoId, s.startSeconds, s.endSeconds, s.category);
  }
}

function insertDownload(
  db: Database.Database,
  videoId: string,
  dl: DownloadResult,
  now: number,
): void {
  db.prepare(
    `INSERT INTO downloaded_videos (video_id, file_path, file_size_bytes, downloaded_at)
     VALUES (?, ?, ?, ?)`,
  ).run(videoId, dl.filePath, dl.fileSizeBytes, now);
}

function insertFeedItem(db: Database.Database, videoId: string, now: number): void {
  db.prepare(`INSERT INTO feed_items (video_id, added_to_feed_at) VALUES (?, ?)`).run(videoId, now);
}
```

- [ ] **Step 4: Run — pass**

Run: `cd backend && pnpm test src/pipeline/orchestrator.test.ts`

- [ ] **Step 5: Commit**

```bash
git -C /Users/ayysir/Desktop/Hikari add backend/src/pipeline
git -C /Users/ayysir/Desktop/Hikari commit -m "feat(backend): pipeline orchestrator"
```

---

## Task 13: HTTP API — Channels

**Files:**
- Create: `backend/src/api/channels.ts`
- Create: `backend/src/api/channels.test.ts`

- [ ] **Step 1: Write failing test `backend/src/api/channels.test.ts`**

```ts
import Database from "better-sqlite3";
import Fastify from "fastify";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { applyMigrations } from "../db/migrations.js";
import { registerChannelsRoutes } from "./channels.js";

vi.mock("../monitor/channel-resolver.js", () => ({
  resolveChannel: vi.fn(),
}));

describe("channels API", () => {
  let db: Database.Database;

  beforeEach(() => {
    db = new Database(":memory:");
    applyMigrations(db);
    vi.clearAllMocks();
  });

  it("POST /channels resolves URL and inserts row", async () => {
    const { resolveChannel } = await import("../monitor/channel-resolver.js");
    vi.mocked(resolveChannel).mockResolvedValue({ channelId: "UC1", title: "Test Channel" });

    const app = Fastify();
    await registerChannelsRoutes(app, { db });

    const res = await app.inject({
      method: "POST",
      url: "/channels",
      payload: { channelUrl: "https://www.youtube.com/@test" },
    });

    expect(res.statusCode).toBe(200);
    expect(res.json()).toEqual({ id: "UC1", title: "Test Channel", url: "https://www.youtube.com/@test" });
    expect(db.prepare("SELECT COUNT(*) as c FROM channels").get()).toEqual({ c: 1 });
  });

  it("GET /channels lists active channels", async () => {
    db.prepare(
      "INSERT INTO channels (id, url, title, added_at, is_active) VALUES ('UC1','x','Alpha',0,1)",
    ).run();
    db.prepare(
      "INSERT INTO channels (id, url, title, added_at, is_active) VALUES ('UC2','y','Beta',0,1)",
    ).run();

    const app = Fastify();
    await registerChannelsRoutes(app, { db });
    const res = await app.inject({ method: "GET", url: "/channels" });

    expect(res.statusCode).toBe(200);
    const body = res.json() as { id: string; title: string }[];
    expect(body).toHaveLength(2);
    expect(body[0]).toMatchObject({ id: "UC1", title: "Alpha" });
  });

  it("DELETE /channels/:id sets is_active=0", async () => {
    db.prepare(
      "INSERT INTO channels (id, url, title, added_at, is_active) VALUES ('UC1','x','Alpha',0,1)",
    ).run();

    const app = Fastify();
    await registerChannelsRoutes(app, { db });
    const res = await app.inject({ method: "DELETE", url: "/channels/UC1" });

    expect(res.statusCode).toBe(204);
    expect(
      db.prepare("SELECT is_active FROM channels WHERE id='UC1'").get(),
    ).toEqual({ is_active: 0 });
  });
});
```

- [ ] **Step 2: Run — fail**

Run: `cd backend && pnpm test src/api/channels.test.ts`

- [ ] **Step 3: Implement `backend/src/api/channels.ts`**

```ts
import type { FastifyInstance } from "fastify";
import type Database from "better-sqlite3";
import { resolveChannel } from "../monitor/channel-resolver.js";

export interface ChannelsDeps {
  db: Database.Database;
}

export async function registerChannelsRoutes(
  app: FastifyInstance,
  deps: ChannelsDeps,
): Promise<void> {
  app.post<{ Body: { channelUrl: string } }>("/channels", async (req, reply) => {
    const { channelUrl } = req.body;
    const resolved = await resolveChannel(channelUrl);
    deps.db
      .prepare(
        `INSERT OR REPLACE INTO channels (id, url, title, added_at, is_active)
         VALUES (?, ?, ?, ?, 1)`,
      )
      .run(resolved.channelId, channelUrl, resolved.title, Date.now());
    return reply.code(200).send({ id: resolved.channelId, title: resolved.title, url: channelUrl });
  });

  app.get("/channels", async () => {
    return deps.db
      .prepare("SELECT id, url, title, added_at, is_active FROM channels WHERE is_active=1 ORDER BY added_at DESC")
      .all();
  });

  app.delete<{ Params: { id: string } }>("/channels/:id", async (req, reply) => {
    deps.db.prepare("UPDATE channels SET is_active = 0 WHERE id = ?").run(req.params.id);
    return reply.code(204).send();
  });
}
```

- [ ] **Step 4: Run — pass**

Run: `cd backend && pnpm test src/api/channels.test.ts`

- [ ] **Step 5: Commit**

```bash
git -C /Users/ayysir/Desktop/Hikari add backend/src/api/channels.ts backend/src/api/channels.test.ts
git -C /Users/ayysir/Desktop/Hikari commit -m "feat(backend): channels API endpoints"
```

---

## Task 14: HTTP API — Feed (with daily budget)

**Files:**
- Create: `backend/src/api/feed.ts`
- Create: `backend/src/api/feed.test.ts`

**Purpose:** GET /feed honors the daily hard budget (decision #8 — default 15/day). Also implements seen, save, unplayable, less-like-this.

- [ ] **Step 1: Write failing test `backend/src/api/feed.test.ts`**

```ts
import Database from "better-sqlite3";
import Fastify from "fastify";
import { beforeEach, describe, expect, it } from "vitest";
import { applyMigrations } from "../db/migrations.js";
import { registerFeedRoutes } from "./feed.js";

function seedFeedItem(db: Database.Database, id: string, addedAt: number, seen = false) {
  db.prepare(
    "INSERT OR IGNORE INTO channels (id, url, title, added_at) VALUES ('UC1','x','c',0)",
  ).run();
  db.prepare(
    `INSERT INTO videos (id, channel_id, title, published_at, duration_seconds, discovered_at)
     VALUES (?, 'UC1', ?, 0, 60, 0)`,
  ).run(id, `t-${id}`);
  db.prepare(
    `INSERT INTO downloaded_videos (video_id, file_path, file_size_bytes, downloaded_at)
     VALUES (?, '/x', 0, 0)`,
  ).run(id);
  db.prepare(
    `INSERT INTO feed_items (video_id, added_to_feed_at, seen_at) VALUES (?, ?, ?)`,
  ).run(id, addedAt, seen ? addedAt : null);
}

describe("feed API", () => {
  let db: Database.Database;
  beforeEach(() => {
    db = new Database(":memory:");
    applyMigrations(db);
  });

  it("GET /feed returns unseen items, newest first, capped by daily budget", async () => {
    const today = Date.now();
    for (let i = 0; i < 20; i++) {
      seedFeedItem(db, `v${i}`, today - i * 1000);
    }
    const app = Fastify();
    await registerFeedRoutes(app, { db, dailyBudget: 5 });

    const res = await app.inject({ method: "GET", url: "/feed" });
    expect(res.statusCode).toBe(200);
    const body = res.json() as { videoId: string }[];
    expect(body).toHaveLength(5);
    expect(body[0].videoId).toBe("v0");
  });

  it("GET /feed excludes already-seen items", async () => {
    const now = Date.now();
    seedFeedItem(db, "seen1", now - 1000, true);
    seedFeedItem(db, "unseen1", now - 500);
    const app = Fastify();
    await registerFeedRoutes(app, { db, dailyBudget: 15 });

    const res = await app.inject({ method: "GET", url: "/feed" });
    const body = res.json() as { videoId: string }[];
    expect(body.map((x) => x.videoId)).toEqual(["unseen1"]);
  });

  it("POST /feed/:id/seen marks the item seen", async () => {
    seedFeedItem(db, "v1", Date.now());
    const app = Fastify();
    await registerFeedRoutes(app, { db, dailyBudget: 15 });
    const res = await app.inject({ method: "POST", url: "/feed/v1/seen" });
    expect(res.statusCode).toBe(204);
    const row = db
      .prepare("SELECT seen_at FROM feed_items WHERE video_id='v1'")
      .get() as { seen_at: number | null };
    expect(row.seen_at).toBeGreaterThan(0);
  });

  it("POST /feed/:id/save toggles saved, DELETE unsets", async () => {
    seedFeedItem(db, "v1", Date.now());
    const app = Fastify();
    await registerFeedRoutes(app, { db, dailyBudget: 15 });
    await app.inject({ method: "POST", url: "/feed/v1/save" });
    expect(
      db.prepare("SELECT saved FROM feed_items WHERE video_id='v1'").get(),
    ).toEqual({ saved: 1 });
    await app.inject({ method: "DELETE", url: "/feed/v1/save" });
    expect(
      db.prepare("SELECT saved FROM feed_items WHERE video_id='v1'").get(),
    ).toEqual({ saved: 0 });
  });

  it("POST /feed/:id/unplayable sets playback_failed", async () => {
    seedFeedItem(db, "v1", Date.now());
    const app = Fastify();
    await registerFeedRoutes(app, { db, dailyBudget: 15 });
    await app.inject({ method: "POST", url: "/feed/v1/unplayable" });
    expect(
      db.prepare("SELECT playback_failed FROM feed_items WHERE video_id='v1'").get(),
    ).toEqual({ playback_failed: 1 });
  });
});
```

- [ ] **Step 2: Run — fail**

Run: `cd backend && pnpm test src/api/feed.test.ts`

- [ ] **Step 3: Implement `backend/src/api/feed.ts`**

```ts
import type { FastifyInstance } from "fastify";
import type Database from "better-sqlite3";

export interface FeedDeps {
  db: Database.Database;
  dailyBudget: number;
}

export async function registerFeedRoutes(app: FastifyInstance, deps: FeedDeps): Promise<void> {
  app.get("/feed", async () => {
    return deps.db
      .prepare(
        `SELECT fi.video_id as videoId, v.title, v.duration_seconds as durationSeconds,
                v.aspect_ratio as aspectRatio, v.thumbnail_url as thumbnailUrl,
                v.channel_id as channelId, c.title as channelTitle,
                s.category, s.reasoning,
                fi.added_to_feed_at as addedAt, fi.saved
         FROM feed_items fi
         JOIN videos v ON v.id = fi.video_id
         JOIN channels c ON c.id = v.channel_id
         JOIN scores s ON s.video_id = fi.video_id
         JOIN downloaded_videos dv ON dv.video_id = fi.video_id
         WHERE fi.seen_at IS NULL
           AND fi.playback_failed = 0
         ORDER BY fi.added_to_feed_at DESC
         LIMIT ?`,
      )
      .all(deps.dailyBudget);
  });

  app.post<{ Params: { id: string } }>("/feed/:id/seen", async (req, reply) => {
    deps.db
      .prepare("UPDATE feed_items SET seen_at = ? WHERE video_id = ?")
      .run(Date.now(), req.params.id);
    deps.db
      .prepare("UPDATE downloaded_videos SET last_served_at = ? WHERE video_id = ?")
      .run(Date.now(), req.params.id);
    return reply.code(204).send();
  });

  app.post<{ Params: { id: string } }>("/feed/:id/save", async (req, reply) => {
    deps.db.prepare("UPDATE feed_items SET saved = 1 WHERE video_id = ?").run(req.params.id);
    return reply.code(204).send();
  });

  app.delete<{ Params: { id: string } }>("/feed/:id/save", async (req, reply) => {
    deps.db.prepare("UPDATE feed_items SET saved = 0 WHERE video_id = ?").run(req.params.id);
    return reply.code(204).send();
  });

  app.post<{ Params: { id: string } }>("/feed/:id/unplayable", async (req, reply) => {
    deps.db
      .prepare("UPDATE feed_items SET playback_failed = 1 WHERE video_id = ?")
      .run(req.params.id);
    return reply.code(204).send();
  });

  app.post<{ Params: { id: string } }>("/feed/:id/less-like-this", async (req, reply) => {
    // Simplest encoding: mark seen+playback_failed (removes from feed).
    // Kept separate endpoint so Kadir can tune prompt off these records.
    deps.db
      .prepare(
        "UPDATE feed_items SET seen_at = COALESCE(seen_at, ?), playback_failed = 1 WHERE video_id = ?",
      )
      .run(Date.now(), req.params.id);
    return reply.code(204).send();
  });
}
```

- [ ] **Step 4: Run — pass**

Run: `cd backend && pnpm test src/api/feed.test.ts`

- [ ] **Step 5: Commit**

```bash
git -C /Users/ayysir/Desktop/Hikari add backend/src/api/feed.ts backend/src/api/feed.test.ts
git -C /Users/ayysir/Desktop/Hikari commit -m "feat(backend): feed API with daily budget"
```

---

## Task 15: HTTP API — Video Serving + Health

**Files:**
- Create: `backend/src/api/videos.ts`
- Create: `backend/src/api/videos.test.ts`
- Create: `backend/src/api/health.ts`
- Create: `backend/src/api/health.test.ts`

- [ ] **Step 1: Write failing test `backend/src/api/videos.test.ts`**

```ts
import { mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import Fastify from "fastify";
import fastifyStatic from "@fastify/static";
import { describe, expect, it } from "vitest";
import { registerVideosRoutes } from "./videos.js";

describe("videos API", () => {
  it("serves MP4 with Content-Type and supports Range requests", async () => {
    const dir = mkdtempSync(join(tmpdir(), "hikari-videos-"));
    writeFileSync(join(dir, "vid1.mp4"), Buffer.alloc(10000, 0xaa));

    const app = Fastify();
    await app.register(fastifyStatic, { root: dir, prefix: "/videos/" });
    await registerVideosRoutes(app);

    const full = await app.inject({ method: "GET", url: "/videos/vid1.mp4" });
    expect(full.statusCode).toBe(200);
    expect(full.body.length).toBe(10000);

    const ranged = await app.inject({
      method: "GET",
      url: "/videos/vid1.mp4",
      headers: { range: "bytes=0-99" },
    });
    expect(ranged.statusCode).toBe(206);
    expect(ranged.body.length).toBe(100);
  });

  it("returns 404 for missing file", async () => {
    const dir = mkdtempSync(join(tmpdir(), "hikari-videos-empty-"));
    const app = Fastify();
    await app.register(fastifyStatic, { root: dir, prefix: "/videos/" });
    await registerVideosRoutes(app);

    const res = await app.inject({ method: "GET", url: "/videos/nope.mp4" });
    expect(res.statusCode).toBe(404);
  });
});
```

- [ ] **Step 2: Run — fail**

Run: `cd backend && pnpm test src/api/videos.test.ts`

- [ ] **Step 3: Implement `backend/src/api/videos.ts`**

```ts
import type { FastifyInstance } from "fastify";

// @fastify/static with { prefix: "/videos/", root: VIDEO_DIR } handles Range,
// Content-Type, and ETag automatically. This function is a no-op placeholder
// for future logic (access logs, last_served_at updates).
export async function registerVideosRoutes(_app: FastifyInstance): Promise<void> {
  // intentionally empty — static serving is registered in index.ts
}
```

- [ ] **Step 4: Run — pass**

Run: `cd backend && pnpm test src/api/videos.test.ts`

- [ ] **Step 5: Write failing test `backend/src/api/health.test.ts`**

```ts
import Database from "better-sqlite3";
import Fastify from "fastify";
import { describe, expect, it, vi } from "vitest";
import { applyMigrations } from "../db/migrations.js";
import { registerHealthRoute } from "./health.js";

vi.mock("../yt-dlp/client.js", () => ({
  runYtDlp: vi.fn().mockResolvedValue({ stdout: "2026.04.01\n", stderr: "" }),
  YtDlpError: class extends Error {},
}));

describe("health API", () => {
  it("returns ok with yt-dlp version and db status", async () => {
    const db = new Database(":memory:");
    applyMigrations(db);

    const app = Fastify();
    await registerHealthRoute(app, { db, videoDir: "/tmp/hikari-test" });
    const res = await app.inject({ method: "GET", url: "/health" });
    expect(res.statusCode).toBe(200);
    const body = res.json() as { status: string; ytDlpVersion: string };
    expect(body.status).toBe("ok");
    expect(body.ytDlpVersion).toBe("2026.04.01");
  });
});
```

- [ ] **Step 6: Run — fail**

Run: `cd backend && pnpm test src/api/health.test.ts`

- [ ] **Step 7: Implement `backend/src/api/health.ts`**

```ts
import { existsSync, statSync, readdirSync } from "node:fs";
import { join } from "node:path";
import type { FastifyInstance } from "fastify";
import type Database from "better-sqlite3";
import { runYtDlp, YtDlpError } from "../yt-dlp/client.js";

export interface HealthDeps {
  db: Database.Database;
  videoDir: string;
}

export async function registerHealthRoute(app: FastifyInstance, deps: HealthDeps): Promise<void> {
  app.get("/health", async () => {
    let ytDlpVersion = "unknown";
    try {
      const { stdout } = await runYtDlp(["--version"], { timeoutMs: 5000 });
      ytDlpVersion = stdout.trim();
    } catch (e) {
      if (e instanceof YtDlpError) ytDlpVersion = "unavailable";
    }

    const dbOk = (() => {
      try {
        deps.db.prepare("SELECT 1").get();
        return true;
      } catch {
        return false;
      }
    })();

    const diskBytes = existsSync(deps.videoDir)
      ? readdirSync(deps.videoDir).reduce(
          (sum, f) => sum + statSync(join(deps.videoDir, f)).size,
          0,
        )
      : 0;

    return {
      status: dbOk && ytDlpVersion !== "unavailable" ? "ok" : "degraded",
      ytDlpVersion,
      dbOk,
      videoDirBytes: diskBytes,
    };
  });
}
```

- [ ] **Step 8: Run — pass**

Run: `cd backend && pnpm test src/api`

- [ ] **Step 9: Commit**

```bash
git -C /Users/ayysir/Desktop/Hikari add backend/src/api/videos.ts backend/src/api/videos.test.ts backend/src/api/health.ts backend/src/api/health.test.ts
git -C /Users/ayysir/Desktop/Hikari commit -m "feat(backend): video serving + health endpoint"
```

---

## Task 16: Config Loader

**Files:**
- Create: `backend/src/config.ts`
- Create: `backend/src/config.test.ts`

- [ ] **Step 1: Write failing test `backend/src/config.test.ts`**

```ts
import { describe, expect, it } from "vitest";
import { loadConfig } from "./config.js";

describe("loadConfig", () => {
  it("reads env with sensible defaults", () => {
    const cfg = loadConfig({
      HOME: "/home/k",
    });
    expect(cfg.port).toBe(3000);
    expect(cfg.dailyBudget).toBe(15);
    expect(cfg.diskLimitBytes).toBe(10 * 1024 ** 3);
    expect(cfg.dataDir).toBe("/home/k/.hikari");
    expect(cfg.llmProvider).toBe("claude");
  });

  it("honors PORT and DAILY_BUDGET override", () => {
    const cfg = loadConfig({ HOME: "/h", PORT: "4000", DAILY_BUDGET: "7" });
    expect(cfg.port).toBe(4000);
    expect(cfg.dailyBudget).toBe(7);
  });

  it("throws when LLM_PROVIDER=claude but no ANTHROPIC_API_KEY", () => {
    expect(() => loadConfig({ HOME: "/h", LLM_PROVIDER: "claude" })).toThrow(/ANTHROPIC_API_KEY/);
  });
});
```

- [ ] **Step 2: Run — fail**

Run: `cd backend && pnpm test src/config.test.ts`

- [ ] **Step 3: Implement `backend/src/config.ts`**

```ts
import { join } from "node:path";

export interface Config {
  port: number;
  dataDir: string;
  videoDir: string;
  dbPath: string;
  dailyBudget: number;
  diskLimitBytes: number;
  llmProvider: "claude" | "ollama";
  claude: { apiKey: string; model: string };
  ollama: { baseUrl: string; model: string };
}

export function loadConfig(env: NodeJS.ProcessEnv = process.env): Config {
  const home = env.HOME ?? env.USERPROFILE ?? "/tmp";
  const dataDir = (env.HIKARI_DATA_DIR ?? join(home, ".hikari")).replace(/^~/, home);
  const llmProvider = (env.LLM_PROVIDER ?? "claude") as "claude" | "ollama";

  if (llmProvider === "claude" && !env.ANTHROPIC_API_KEY) {
    throw new Error("ANTHROPIC_API_KEY is required when LLM_PROVIDER=claude");
  }

  return {
    port: Number(env.PORT ?? 3000),
    dataDir,
    videoDir: join(dataDir, "videos"),
    dbPath: join(dataDir, "hikari.db"),
    dailyBudget: Number(env.DAILY_BUDGET ?? 15),
    diskLimitBytes: Number(env.DISK_LIMIT_GB ?? 10) * 1024 ** 3,
    llmProvider,
    claude: {
      apiKey: env.ANTHROPIC_API_KEY ?? "",
      model: env.CLAUDE_MODEL ?? "claude-haiku-4-5",
    },
    ollama: {
      baseUrl: env.OLLAMA_URL ?? "http://localhost:11434",
      model: env.OLLAMA_MODEL ?? "qwen2.5:14b",
    },
  };
}
```

- [ ] **Step 4: Run — pass**

Run: `cd backend && pnpm test src/config.test.ts`

- [ ] **Step 5: Commit**

```bash
git -C /Users/ayysir/Desktop/Hikari add backend/src/config.ts backend/src/config.test.ts
git -C /Users/ayysir/Desktop/Hikari commit -m "feat(backend): config loader"
```

---

## Task 17: Scorer Factory & Main Entry

**Files:**
- Create: `backend/src/scorer/factory.ts`
- Modify: `backend/src/index.ts` — full wiring

- [ ] **Step 1: Implement `backend/src/scorer/factory.ts`**

```ts
import type { Config } from "../config.js";
import { ClaudeScorer } from "./claude-scorer.js";
import { OllamaScorer } from "./ollama-scorer.js";
import type { Scorer } from "./types.js";

export function createScorer(cfg: Config): Scorer {
  if (cfg.llmProvider === "claude") {
    return new ClaudeScorer({ apiKey: cfg.claude.apiKey, model: cfg.claude.model });
  }
  return new OllamaScorer({ baseUrl: cfg.ollama.baseUrl, model: cfg.ollama.model });
}
```

- [ ] **Step 2: Rewrite `backend/src/index.ts` to wire everything**

```ts
import { mkdirSync, existsSync, readdirSync, unlinkSync } from "node:fs";
import { join } from "node:path";
import fastifyStatic from "@fastify/static";
import Fastify from "fastify";
import cron from "node-cron";
import { registerChannelsRoutes } from "./api/channels.js";
import { registerFeedRoutes } from "./api/feed.js";
import { registerHealthRoute } from "./api/health.js";
import { loadConfig } from "./config.js";
import { openDatabase } from "./db/connection.js";
import { runCleanup } from "./download/cleanup.js";
import { downloadVideo } from "./download/worker.js";
import { fetchVideoMetadata } from "./ingest/metadata.js";
import { fetchTranscript } from "./ingest/transcript.js";
import { fetchChannelFeed } from "./monitor/rss-poller.js";
import { processNewVideo } from "./pipeline/orchestrator.js";
import { createScorer } from "./scorer/factory.js";
import { fetchSponsorSegments } from "./sponsorblock/client.js";

const cfg = loadConfig();
mkdirSync(cfg.videoDir, { recursive: true });

const db = openDatabase(cfg.dbPath);
const scorer = createScorer(cfg);

// Startup consistency check: orphan files
for (const f of readdirSync(cfg.videoDir)) {
  if (!f.endsWith(".mp4")) continue;
  const videoId = f.replace(/\.mp4$/, "");
  const row = db.prepare("SELECT 1 FROM downloaded_videos WHERE video_id = ?").get(videoId);
  if (!row) {
    unlinkSync(join(cfg.videoDir, f));
  }
}
// DB rows without files
const orphanRows = db
  .prepare("SELECT video_id, file_path FROM downloaded_videos")
  .all() as { video_id: string; file_path: string }[];
for (const r of orphanRows) {
  if (!existsSync(r.file_path)) {
    db.prepare("DELETE FROM downloaded_videos WHERE video_id = ?").run(r.video_id);
    db.prepare("UPDATE feed_items SET playback_failed = 1 WHERE video_id = ?").run(r.video_id);
  }
}

const app = Fastify({ logger: { level: "info" } });
await app.register(fastifyStatic, { root: cfg.videoDir, prefix: "/videos/" });
await registerChannelsRoutes(app, { db });
await registerFeedRoutes(app, { db, dailyBudget: cfg.dailyBudget });
await registerHealthRoute(app, { db, videoDir: cfg.videoDir });

// 15-min channel polling
cron.schedule("*/15 * * * *", async () => {
  const channels = db
    .prepare("SELECT id FROM channels WHERE is_active = 1")
    .all() as { id: string }[];
  for (const c of channels) {
    try {
      const entries = await fetchChannelFeed(c.id);
      for (const e of entries) {
        await processNewVideo({
          db,
          videoId: e.videoId,
          channelId: c.id,
          fetchMetadata: fetchVideoMetadata,
          fetchTranscript,
          fetchSponsorSegments,
          scorer,
          download: (id) => downloadVideo({ videoId: id, outDir: cfg.videoDir }),
        });
      }
      db.prepare("UPDATE channels SET last_polled_at = ? WHERE id = ?").run(Date.now(), c.id);
    } catch (err) {
      app.log.warn({ err, channelId: c.id }, "channel poll failed");
    }
  }
});

// Daily cleanup
cron.schedule("0 4 * * *", () => {
  const result = runCleanup({ db, limitBytes: cfg.diskLimitBytes });
  if (result.deletedCount > 0) {
    app.log.info({ result }, "cleanup completed");
  }
});

app.listen({ port: cfg.port, host: "0.0.0.0" }).catch((err) => {
  app.log.error(err);
  process.exit(1);
});
```

- [ ] **Step 3: Compile check**

Run: `cd backend && pnpm build`
Expected: `tsc` compiles with no errors.

- [ ] **Step 4: Run all tests**

Run: `cd backend && pnpm test`
Expected: all pass.

- [ ] **Step 5: Commit**

```bash
git -C /Users/ayysir/Desktop/Hikari add backend/src/scorer/factory.ts backend/src/index.ts
git -C /Users/ayysir/Desktop/Hikari commit -m "feat(backend): wire pipeline + cron jobs in main entry"
```

---

## Task 18: End-to-End Smoke Test (Manual)

**Files:** none (script-style instructions)

- [ ] **Step 1: Ensure yt-dlp is installed on laptop**

Run: `pipx install yt-dlp` (or `brew install yt-dlp`)
Verify: `yt-dlp --version` prints a date-stamped version.

- [ ] **Step 2: Create `.env` from `.env.example`**

```bash
cd backend
cp .env.example .env
```

Edit `.env` and fill in `ANTHROPIC_API_KEY`.

- [ ] **Step 3: Start server**

Run: `pnpm dev`
Expected: Logs "Server listening at http://0.0.0.0:3000".

- [ ] **Step 4: Add a channel**

From another terminal:
```bash
curl -X POST http://localhost:3000/channels \
  -H "content-type: application/json" \
  -d '{"channelUrl":"https://www.youtube.com/@3blue1brown"}'
```
Expected: `{"id":"UCYO_jab_esuFRV4b17AJtAw","title":"3Blue1Brown","url":"..."}`

- [ ] **Step 5: Verify RSS poll works**

Wait 15 min or manually trigger cron by restarting with modified schedule to `* * * * *` temporarily.
Watch logs for "channel poll failed" — should not appear.
Query DB: `sqlite3 ~/.hikari/hikari.db "SELECT id, title FROM videos"` — should have rows within 15 min if new content exists.

- [ ] **Step 6: Verify video serving**

Run: `ls ~/.hikari/videos/` — expect at least one `.mp4` file.
Run: `curl -I http://localhost:3000/videos/<videoId>.mp4` — expect `Content-Type: video/mp4`, `Accept-Ranges: bytes`.

- [ ] **Step 7: Verify feed endpoint**

Run: `curl http://localhost:3000/feed | jq`
Expected: JSON array with at most 15 items, each with `videoId`, `title`, `durationSeconds`.

- [ ] **Step 8: Tag completion**

```bash
git -C /Users/ayysir/Desktop/Hikari tag backend-mvp-v0.1
```

---

## Done Criteria

- [ ] All unit tests pass: `cd backend && pnpm test` → 0 failures
- [ ] TypeScript compiles: `cd backend && pnpm build` → exit 0
- [ ] Biome clean: `cd backend && pnpm lint` → exit 0
- [ ] Manual smoke test Task 18 passes
- [ ] All 18 task commits in git log
- [ ] Spec's Success Criteria 1–11 (Section 11 of spec) are all met except #5 (Android app — next plan)

---

## Next Plan

After backend-mvp-v0.1 tag: write `docs/superpowers/plans/2026-04-<date>-hikari-android.md` covering Kotlin+Compose+Media3 implementation.
