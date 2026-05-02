# Auto-Clipper Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Lange Videos werden im Backend automatisch in Highlight-Clips zerlegt, smart auf 9:16 reframed und in den Feed eingewoben. Originale erreichbar via "Original ansehen"-Action im Fullscreen-Player. Lokales Qwen 3.6-35B-A3B (LM Studio / Ollama) analysiert; Remotion rendert.

**Architecture:** Strict-serial async Worker-Prozess (22:00–08:00) konsumiert SQLite-backed Queue. Qwen analysiert Videos multimodal → liefert `ClipSpec[]`. Remotion (`@remotion/renderer` programmatisch) rendert 9:16 Smart-Crop-Clips. Feed-API mergt Clips mit legacy items via UNION + Cooldown/Topic-Mix-Algorithmus.

**Tech Stack:** TypeScript (Node 24, ESM, NodeNext), Fastify 5, better-sqlite3 12, Vitest 2, Remotion 4 (`@remotion/bundler`, `@remotion/renderer`), node-cron, ExoPlayer/Compose (Android).

**Spec:** `docs/superpowers/specs/2026-05-02-auto-clipper-design.md`

---

## File Structure

```
backend/
├── package.json                              MODIFY  Remotion-Deps + clipper-Script
├── src/
│   ├── clipper/                              NEW
│   │   ├── prompt-builder.ts                 NEW
│   │   ├── prompt-builder.test.ts            NEW
│   │   ├── crop-math.ts                      NEW   (focus_region → 9:16-Crop-Rect)
│   │   ├── crop-math.test.ts                 NEW
│   │   ├── queue.ts                          NEW   (SQL queue + isWindowActive)
│   │   ├── queue.test.ts                     NEW
│   │   ├── qwen-analyzer.ts                  NEW   (LM-Studio HTTP wrapper)
│   │   ├── qwen-analyzer.test.ts             NEW
│   │   ├── remotion-renderer.ts              NEW
│   │   ├── remotion-renderer.test.ts         NEW
│   │   ├── worker.ts                         NEW
│   │   └── worker.integration.test.ts        NEW
│   ├── db/
│   │   ├── schema.sql                        MODIFY  +clips, +clipper_queue
│   │   ├── migrations.ts                     MODIFY  +clip_status, +is_pre_clipper
│   │   └── migrations.test.ts                NEW
│   ├── pipeline/
│   │   └── orchestrator.ts                   MODIFY  approved → enqueue (statt feed_items)
│   ├── api/
│   │   ├── feed.ts                           MODIFY  UNION + cooldown
│   │   ├── feed.test.ts                      MODIFY  +cooldown tests
│   │   ├── clipper-status.ts                 NEW
│   │   ├── clipper-status.test.ts            NEW
│   │   ├── video-full.ts                     NEW   (GET /videos/:id/full)
│   │   └── video-full.test.ts                NEW
│   ├── config.ts                             MODIFY  +clipper config (model, baseUrl)
│   └── index.ts                              MODIFY  register new routes
└── scripts/
    └── clipper-worker.ts                     NEW   (entry: `pnpm clipper`)

remotion/                                     NEW   (Remotion-Projekt-Root)
├── index.ts                                  NEW
├── Root.tsx                                  NEW
├── ClipComposition.tsx                       NEW
└── tsconfig.json                             NEW

android/app/src/main/java/com/hikari/app/
├── data/api/
│   ├── HikariApi.kt                          MODIFY  +full-video, +clipper-status
│   └── dto/
│       ├── FeedItemDto.kt                    MODIFY  +kind, +parentVideoId
│       └── ClipperStatusDto.kt               NEW
├── ui/
│   ├── feed/FeedScreen.kt                    MODIFY  Tap-Handler "Original ansehen"
│   ├── player/FullscreenOriginalPlayer.kt    NEW
│   ├── tuning/TuningSystemTab.kt             MODIFY  Counter + Retry-Button
│   └── HikariNavGraph.kt                     MODIFY  +original/{videoId} route
```

---

## Phase 0 — Dependencies & Setup

### Task 0.1: Add Remotion to backend dependencies

**Files:**
- Modify: `backend/package.json`

- [ ] **Step 1: Add deps via pnpm**

```bash
cd /Users/ayysir/Desktop/Hikari/backend
pnpm add remotion@4 @remotion/bundler@4 @remotion/renderer@4 @remotion/cli@4
pnpm add -D @types/react@19 react@19 react-dom@19
```

Expected: package.json gets new entries, pnpm-lock.yaml updates, ~80 new packages installed.

- [ ] **Step 2: Add clipper-worker script**

Edit `backend/package.json`, add to `"scripts"`:

```json
"clipper": "tsx scripts/clipper-worker.ts",
"clipper:dev": "tsx watch scripts/clipper-worker.ts"
```

- [ ] **Step 3: Verify types resolve**

```bash
cd /Users/ayysir/Desktop/Hikari/backend
pnpm tsc --noEmit
```

Expected: PASS (no errors before any clipper code is written).

- [ ] **Step 4: Commit**

```bash
git add backend/package.json backend/pnpm-lock.yaml
git commit -m "chore(backend): add Remotion deps + clipper script"
```

---

### Task 0.2: Add clipper config

**Files:**
- Modify: `backend/src/config.ts`

- [ ] **Step 1: Read existing config**

```bash
cat /Users/ayysir/Desktop/Hikari/backend/src/config.ts
```

Expected: contains `Config` type with `lmstudio: { baseUrl, model }` block.

- [ ] **Step 2: Add clipper config to Config type**

Edit `backend/src/config.ts`, extend the Config type:

```ts
export interface ClipperConfig {
  enabled: boolean;
  provider: "lmstudio" | "ollama";
  baseUrl: string;
  model: string;          // e.g. "qwen3.6-35b-a3b"
  scheduleStartHour: number;  // 22
  scheduleEndHour: number;    // 8
}
```

Add `clipper: ClipperConfig` to `Config`.

- [ ] **Step 3: Add env-driven defaults**

In the config-loading function, add:

```ts
clipper: {
  enabled: process.env.CLIPPER_ENABLED !== "false",
  provider: (process.env.CLIPPER_PROVIDER as "lmstudio" | "ollama") ?? "lmstudio",
  baseUrl: process.env.CLIPPER_BASE_URL ?? "http://localhost:1234",
  model: process.env.CLIPPER_MODEL ?? "qwen3.6-35b-a3b",
  scheduleStartHour: Number(process.env.CLIPPER_START_HOUR ?? 22),
  scheduleEndHour: Number(process.env.CLIPPER_END_HOUR ?? 8),
}
```

- [ ] **Step 4: Run existing config tests**

```bash
cd /Users/ayysir/Desktop/Hikari/backend
pnpm vitest run src/config.test.ts
```

Expected: PASS (no breakage to existing tests).

- [ ] **Step 5: Commit**

```bash
git add backend/src/config.ts
git commit -m "feat(config): add clipper config block"
```

---

## Phase 1 — Schema Migration

### Task 1.1: Tests for clipper schema additions

**Files:**
- Modify: `backend/src/db/migrations.test.ts` (file exists — APPEND new describe block; ALSO update existing test's expected table list to include `clips` and `clipper_queue`)

- [ ] **Step 1: Update existing "creates all expected tables" test**

The file currently asserts a specific list of tables. After our migration, `clips` and `clipper_queue` must be added to that list. Edit the existing `expect(names).toEqual([...])` to insert (in alphabetical order):
- `"clipper_queue",` after `"channels",`
- `"clips",` after `"clipper_queue",`

- [ ] **Step 2: Append new clipper describe block at end of file**

```ts
describe("clipper migrations", () => {
  it("creates clips table with all required columns", () => {
    const db = new Database(":memory:");
    applyMigrations(db);

    const cols = db.prepare("PRAGMA table_info(clips)").all() as { name: string }[];
    const names = cols.map((c) => c.name);

    expect(names).toEqual(
      expect.arrayContaining([
        "id", "parent_video_id", "order_in_parent",
        "start_seconds", "end_seconds", "file_path", "file_size_bytes",
        "focus_x", "focus_y", "focus_w", "focus_h",
        "reason", "created_at",
        "added_to_feed_at", "seen_at", "saved", "playback_failed", "progress_seconds",
      ]),
    );
  });

  it("creates clipper_queue table with lock fields", () => {
    const db = new Database(":memory:");
    applyMigrations(db);
    const cols = db.prepare("PRAGMA table_info(clipper_queue)").all() as { name: string }[];
    expect(cols.map((c) => c.name)).toEqual(
      expect.arrayContaining([
        "video_id", "queued_at", "attempts", "last_error", "locked_at", "locked_step",
      ]),
    );
  });

  it("adds clip_status column to videos", () => {
    const db = new Database(":memory:");
    applyMigrations(db);
    const cols = db.prepare("PRAGMA table_info(videos)").all() as { name: string }[];
    expect(cols.find((c) => c.name === "clip_status")).toBeTruthy();
  });

  it("adds is_pre_clipper column to feed_items, defaulting to 0", () => {
    const db = new Database(":memory:");
    applyMigrations(db);
    const cols = db.prepare("PRAGMA table_info(feed_items)").all() as
      { name: string; dflt_value: string | null }[];
    const col = cols.find((c) => c.name === "is_pre_clipper");
    expect(col).toBeTruthy();
    expect(col?.dflt_value).toBe("0");
  });

  it("is idempotent — running twice does not throw", () => {
    const db = new Database(":memory:");
    applyMigrations(db);
    expect(() => applyMigrations(db)).not.toThrow();
  });
});
```

- [ ] **Step 3: Run — expect FAIL**

```bash
cd /Users/ayysir/Desktop/Hikari/.worktrees/auto-clipper/backend
PATH="/opt/homebrew/bin:$PATH" npm test -- src/db/migrations.test.ts
```

Expected: FAIL — both the existing "creates all expected tables" test (now expecting clips+clipper_queue) and the new clipper tests fail because schema.sql doesn't have them yet.

---

### Task 1.2: Add new tables to schema.sql

**Files:**
- Modify: `backend/src/db/schema.sql`

- [ ] **Step 1: Append new tables**

Append at end of `backend/src/db/schema.sql`:

```sql
-- Auto-Clipper: clip rows function as both clip definitions AND feed items.
CREATE TABLE IF NOT EXISTS clips (
  id              TEXT PRIMARY KEY,
  parent_video_id TEXT NOT NULL REFERENCES videos(id),
  order_in_parent INTEGER NOT NULL,
  start_seconds   REAL NOT NULL,
  end_seconds     REAL NOT NULL,
  file_path       TEXT NOT NULL,
  file_size_bytes INTEGER NOT NULL,
  focus_x         REAL NOT NULL,
  focus_y         REAL NOT NULL,
  focus_w         REAL NOT NULL,
  focus_h         REAL NOT NULL,
  reason          TEXT,
  created_at      INTEGER NOT NULL,
  added_to_feed_at INTEGER NOT NULL,
  seen_at         INTEGER,
  saved           INTEGER DEFAULT 0,
  playback_failed INTEGER DEFAULT 0,
  progress_seconds REAL DEFAULT 0
);
CREATE INDEX IF NOT EXISTS idx_clips_added  ON clips(added_to_feed_at DESC);
CREATE INDEX IF NOT EXISTS idx_clips_parent ON clips(parent_video_id);

-- Single-slot job queue. locked_at NULL = available for dequeue.
CREATE TABLE IF NOT EXISTS clipper_queue (
  video_id    TEXT PRIMARY KEY REFERENCES videos(id),
  queued_at   INTEGER NOT NULL,
  attempts    INTEGER DEFAULT 0,
  last_error  TEXT,
  locked_at   INTEGER,
  locked_step TEXT
);
CREATE INDEX IF NOT EXISTS idx_clipper_queue_pending
  ON clipper_queue(queued_at) WHERE locked_at IS NULL;
```

- [ ] **Step 2: Run schema test (still FAIL — no ALTERs yet)**

```bash
pnpm vitest run src/db/migrations.test.ts -t "creates clips table"
```

Expected: PASS for clips and clipper_queue tests, FAIL for clip_status / is_pre_clipper / idempotency tests.

---

### Task 1.3: Add ALTER columns + idempotent backfill in migrations.ts

**Files:**
- Modify: `backend/src/db/migrations.ts`

- [ ] **Step 1: Add ALTERs + backfill**

In `applyMigrations`, after the existing `addColumnIfMissing` block, add:

```ts
// Auto-Clipper: per-video clip lifecycle status.
// NULL = legacy/pre-clipper; values: pending | analyzing | rendering | done
//                                    | no_highlights | failed
addColumnIfMissing(db, "videos", "clip_status", "TEXT");

// Auto-Clipper: legacy items predating the clipper get is_pre_clipper=1
// and continue to behave as full-length feed items.
addColumnIfMissing(db, "feed_items", "is_pre_clipper", "INTEGER DEFAULT 0");

// Backfill: mark all currently-existing feed_items as legacy. Idempotent because
// new rows from clipper-paths are never inserted into feed_items.
db.exec(`
  UPDATE feed_items SET is_pre_clipper = 1
  WHERE is_pre_clipper IS NULL OR is_pre_clipper = 0;
`);
```

⚠ Wait — the backfill above is NOT idempotent in a useful way. Better: only backfill rows where the column was just added (no `clip_status` analog exists for feed_items). Replace with:

```ts
addColumnIfMissing(db, "videos", "clip_status", "TEXT");

// Track whether is_pre_clipper was just added; if so, backfill once.
const feedCols = db.prepare("PRAGMA table_info(feed_items)").all() as { name: string }[];
const hadPreClipper = feedCols.some((c) => c.name === "is_pre_clipper");
addColumnIfMissing(db, "feed_items", "is_pre_clipper", "INTEGER DEFAULT 0");
if (!hadPreClipper) {
  // First migration: every existing row is legacy. From here on, new rows
  // are inserted with explicit is_pre_clipper=0 (set by clipper paths) or
  // is_pre_clipper=1 (legacy fallback paths).
  db.exec(`UPDATE feed_items SET is_pre_clipper = 1`);
}
```

- [ ] **Step 2: Run all migration tests**

```bash
pnpm vitest run src/db/migrations.test.ts
```

Expected: ALL PASS (5 tests).

- [ ] **Step 3: Run full backend test suite to catch regressions**

```bash
pnpm vitest run
```

Expected: ALL PASS. If `orchestrator.test.ts` or `feed.test.ts` break due to new schema, note them but DO NOT fix yet — they'll be intentionally rewritten in Phase 5/6.

- [ ] **Step 4: Commit**

```bash
git add backend/src/db/schema.sql backend/src/db/migrations.ts backend/src/db/migrations.test.ts
git commit -m "feat(db): clipper schema (clips + clipper_queue + status flags)"
```

---

## Phase 2 — Pure SQL & Math Helpers

### Task 2.1: Crop-math helper (focus_region → 9:16 crop rect)

**Files:**
- Create: `backend/src/clipper/crop-math.ts`
- Create: `backend/src/clipper/crop-math.test.ts`

- [ ] **Step 1: Write failing test**

```ts
// backend/src/clipper/crop-math.test.ts
import { describe, expect, it } from "vitest";
import { computeCropRect } from "./crop-math.js";

describe("computeCropRect", () => {
  it("portrait video (9:16) returns full frame regardless of focus", () => {
    const rect = computeCropRect({
      videoWidth: 1080, videoHeight: 1920,
      focus: { x: 0.3, y: 0.3, w: 0.4, h: 0.4 },
      targetAspect: 9 / 16,
    });
    expect(rect).toEqual({ x: 0, y: 0, w: 1080, h: 1920 });
  });

  it("landscape 16:9 with center-focus crops a 9:16 column around focus center", () => {
    const rect = computeCropRect({
      videoWidth: 1920, videoHeight: 1080,
      focus: { x: 0.4, y: 0.2, w: 0.2, h: 0.6 },
      targetAspect: 9 / 16,
    });
    // For 9:16 at 1080 height: cropW = 1080 * 9/16 = 607.5
    expect(rect.h).toBe(1080);
    expect(rect.w).toBeCloseTo(607.5, 0);
    // Focus center x = (0.4 + 0.2/2) * 1920 = 960; crop center should match,
    // clamped within frame.
    const cropCenterX = rect.x + rect.w / 2;
    expect(cropCenterX).toBeCloseTo(960, 0);
  });

  it("clamps crop to frame edges when focus is near the edge", () => {
    const rect = computeCropRect({
      videoWidth: 1920, videoHeight: 1080,
      focus: { x: 0.0, y: 0.0, w: 0.2, h: 0.4 },  // top-left corner
      targetAspect: 9 / 16,
    });
    expect(rect.x).toBe(0);   // can't go negative
    expect(rect.y).toBe(0);
    expect(rect.x + rect.w).toBeLessThanOrEqual(1920);
  });

  it("expands crop horizontally when focus_region is too narrow for 9:16", () => {
    // Tall narrow focus in landscape video — crop should be tall too
    const rect = computeCropRect({
      videoWidth: 1920, videoHeight: 1080,
      focus: { x: 0.45, y: 0.0, w: 0.1, h: 1.0 },  // 192x1080 region
      targetAspect: 9 / 16,
    });
    // We need 9:16 ratio — full height (1080) means width = 607.5
    expect(rect.h).toBe(1080);
    expect(rect.w).toBeCloseTo(607.5, 0);
  });

  it("returns rect with target aspect ratio (within rounding)", () => {
    const rect = computeCropRect({
      videoWidth: 1920, videoHeight: 1080,
      focus: { x: 0.3, y: 0.2, w: 0.4, h: 0.6 },
      targetAspect: 9 / 16,
    });
    expect(rect.w / rect.h).toBeCloseTo(9 / 16, 3);
  });
});
```

- [ ] **Step 2: Run — expect FAIL**

```bash
pnpm vitest run src/clipper/crop-math.test.ts
```

Expected: FAIL — "Cannot find module './crop-math.js'".

- [ ] **Step 3: Implement crop-math.ts**

```ts
// backend/src/clipper/crop-math.ts
export interface FocusRegion {
  x: number; y: number; w: number; h: number;  // all 0..1 normalized
}
export interface CropInput {
  videoWidth: number;
  videoHeight: number;
  focus: FocusRegion;
  targetAspect: number;  // e.g. 9/16 for portrait phone
}
export interface CropRect {
  x: number; y: number; w: number; h: number;  // pixels in source frame
}

/**
 * Compute a crop rectangle in source-pixel coords that:
 *  - has the target aspect ratio
 *  - is centered on focus-region center if possible
 *  - is clamped to source frame bounds
 *  - has the maximum possible size given the constraints
 *
 * If source aspect already matches target (within 1%), returns full frame.
 */
export function computeCropRect(input: CropInput): CropRect {
  const { videoWidth: W, videoHeight: H, focus, targetAspect } = input;
  const sourceAspect = W / H;

  // Already the right aspect: no crop needed.
  if (Math.abs(sourceAspect - targetAspect) / targetAspect < 0.01) {
    return { x: 0, y: 0, w: W, h: H };
  }

  // Decide crop dimensions: keep one full axis, compute the other from target aspect.
  let cropW: number;
  let cropH: number;
  if (sourceAspect > targetAspect) {
    // Source is wider than target → keep full height, narrow width
    cropH = H;
    cropW = H * targetAspect;
  } else {
    // Source is taller than target → keep full width, shorter height
    cropW = W;
    cropH = W / targetAspect;
  }

  // Center on focus-region center (in pixel coords)
  const focusCenterX = (focus.x + focus.w / 2) * W;
  const focusCenterY = (focus.y + focus.h / 2) * H;
  let x = focusCenterX - cropW / 2;
  let y = focusCenterY - cropH / 2;

  // Clamp to source frame
  x = Math.max(0, Math.min(W - cropW, x));
  y = Math.max(0, Math.min(H - cropH, y));

  return { x, y, w: cropW, h: cropH };
}
```

- [ ] **Step 4: Run — expect PASS**

```bash
pnpm vitest run src/clipper/crop-math.test.ts
```

Expected: 5/5 PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/clipper/crop-math.ts backend/src/clipper/crop-math.test.ts
git commit -m "feat(clipper): crop-math helper for focus_region → 9:16 rect"
```

---

### Task 2.2: Prompt builder (FilterConfig → Clipper prompt)

**Files:**
- Create: `backend/src/clipper/prompt-builder.ts`
- Create: `backend/src/clipper/prompt-builder.test.ts`

- [ ] **Step 1: Read existing FilterConfig type**

```bash
cat /Users/ayysir/Desktop/Hikari/backend/src/scorer/filter.ts | head -60
```

Note the `FilterConfig` shape and the existing `buildPrompt(filter)` for reference.

- [ ] **Step 2: Write failing test**

```ts
// backend/src/clipper/prompt-builder.test.ts
import { describe, expect, it } from "vitest";
import { buildClipperPrompt } from "./prompt-builder.js";
import type { FilterConfig } from "../scorer/filter.js";

const SAMPLE_FILTER: FilterConfig = {
  likeTags: ["lehrreich", "math"],
  dislikeTags: ["clickbait"],
  moodTags: ["ruhig"],
  depthTags: ["tiefgründig"],
  languages: ["de", "en"],
  minDurationSec: 60,
  maxDurationSec: 3600,
  examples: "kurze prägnante Erklärungen",
  scoreThreshold: 60,
};

describe("buildClipperPrompt", () => {
  it("includes the operational rules verbatim", () => {
    const out = buildClipperPrompt(SAMPLE_FILTER, { aspectRatio: "16:9" });
    expect(out).toContain("zwischen 20s und 60s");
    expect(out).toContain("Toleranz bis 90s");
    expect(out).toContain("1 pro 5 Min Original-Dauer");
    expect(out).toContain("ausschließlich gültiges JSON-Array");
  });

  it("renders all FilterConfig fields", () => {
    const out = buildClipperPrompt(SAMPLE_FILTER, { aspectRatio: "16:9" });
    expect(out).toContain("lehrreich, math");
    expect(out).toContain("clickbait");
    expect(out).toContain("ruhig");
    expect(out).toContain("tiefgründig");
    expect(out).toContain("de, en");
    expect(out).toContain("kurze prägnante Erklärungen");
  });

  it("hints fullframe focus when source is already 9:16", () => {
    const out = buildClipperPrompt(SAMPLE_FILTER, { aspectRatio: "9:16" });
    expect(out).toContain("schon hochkant");
    expect(out).toContain("x=0, y=0, w=1, h=1");
  });

  it("handles empty FilterConfig fields gracefully", () => {
    const empty: FilterConfig = {
      likeTags: [], dislikeTags: [], moodTags: [], depthTags: [], languages: [],
      minDurationSec: 0, maxDurationSec: 0, examples: "", scoreThreshold: 0,
    };
    expect(() => buildClipperPrompt(empty, { aspectRatio: "16:9" })).not.toThrow();
    const out = buildClipperPrompt(empty, { aspectRatio: "16:9" });
    expect(out).toContain("Highlight-Analyst");
  });

  it("matches snapshot for stable filter input (regression-guard)", () => {
    const out = buildClipperPrompt(SAMPLE_FILTER, { aspectRatio: "16:9" });
    expect(out).toMatchSnapshot();
  });
});
```

- [ ] **Step 3: Run — expect FAIL**

```bash
pnpm vitest run src/clipper/prompt-builder.test.ts
```

Expected: FAIL — module not found.

- [ ] **Step 4: Implement prompt-builder.ts**

```ts
// backend/src/clipper/prompt-builder.ts
import type { FilterConfig } from "../scorer/filter.js";

export interface VideoMeta {
  aspectRatio: string | null;  // e.g. "16:9", "9:16", or null if unknown
}

const OPERATIONAL_RULES = `OPERATIONELLE REGELN (fest):
- Pro Clip: zwischen 20s und 60s, Toleranz bis 90s wenn der Highlight-Moment unteilbar ist
- Anzahl Clips: ungefähr 1 pro 5 Min Original-Dauer (5 Min→1, 15 Min→3, 30 Min→6, 60 Min→12), aber NUR wenn Qualität es trägt
- Lieber WENIGER Clips von hoher Qualität als das ganze Video zerstückeln
- Wenn das Video keine highlight-würdigen Momente hat: leere Liste []`;

const OUTPUT_INSTRUCTIONS = `PRO CLIP gibst du an:
- start_sec, end_sec (Float)
- focus.{x,y,w,h} normalisiert 0.0–1.0 (wo das Wichtige im Frame liegt — für 9:16-Smart-Crop)
- reason (kurze Begründung warum dieser Part)

OUTPUT: ausschließlich gültiges JSON-Array, sortiert nach start_sec ASC. Keine Markdown-Code-Blocks, keine Erklärungen außerhalb des JSON.

Beispiel:
[
  {"start_sec": 142.5, "end_sec": 198.0,
   "focus": {"x": 0.25, "y": 0.15, "w": 0.5, "h": 0.7},
   "reason": "Klare Erklärung der Kernidee mit Diagramm"},
  {"start_sec": 612.0, "end_sec": 668.5,
   "focus": {"x": 0.3, "y": 0.2, "w": 0.4, "h": 0.6},
   "reason": "Punchy Quote über praktische Anwendung"}
]`;

export function buildClipperPrompt(filter: FilterConfig, meta: VideoMeta): string {
  const portraitHint =
    meta.aspectRatio === "9:16"
      ? "\n  HINWEIS: Das Original ist schon hochkant (9:16). Setze focus immer auf x=0, y=0, w=1, h=1 — kein Crop nötig, nur Cut."
      : "";

  return `Du bist ein Video-Highlight-Analyst für die App "Hikari" (kuratierte Kurzvideos, positiv und lehrreich, Anti-Doom-Scroll). Du siehst das Video direkt — Audio + Bild. Identifiziere die wertvollsten Highlight-Momente.

USER-KRITERIEN (was IST gut für diesen User):
- Bevorzugt: ${filter.likeTags.join(", ") || "(keine spezifischen Vorlieben)"}
- Vermeidet: ${filter.dislikeTags.join(", ") || "(keine expliziten Abneigungen)"}
- Stimmung: ${filter.moodTags.join(", ") || "(beliebig)"} | Tiefe: ${filter.depthTags.join(", ") || "(beliebig)"}
- Sprachen: ${filter.languages.join(", ") || "(beliebig)"}
- Beispiele bevorzugter Inhalte: ${filter.examples || "(keine)"}

${OPERATIONAL_RULES}${portraitHint}

${OUTPUT_INSTRUCTIONS}`;
}
```

- [ ] **Step 5: Run — expect PASS**

```bash
pnpm vitest run src/clipper/prompt-builder.test.ts
```

Expected: 5/5 PASS, snapshot file written.

- [ ] **Step 6: Commit**

```bash
git add backend/src/clipper/prompt-builder.ts backend/src/clipper/prompt-builder.test.ts \
        backend/src/clipper/__snapshots__/
git commit -m "feat(clipper): prompt-builder from FilterConfig"
```

---

### Task 2.3: Queue module (enqueue/dequeue/lock/unlock)

**Files:**
- Create: `backend/src/clipper/queue.ts`
- Create: `backend/src/clipper/queue.test.ts`

- [ ] **Step 1: Write failing test for enqueue + dequeue**

```ts
// backend/src/clipper/queue.test.ts
import Database from "better-sqlite3";
import { beforeEach, describe, expect, it } from "vitest";
import { applyMigrations } from "../db/migrations.js";
import {
  complete,
  dequeue,
  enqueue,
  fail,
  isWindowActive,
  unlockStale,
} from "./queue.js";

function makeDb(): Database.Database {
  const db = new Database(":memory:");
  applyMigrations(db);
  // Need a channel + video to satisfy FK constraints
  db.prepare("INSERT INTO channels (id, url, title, added_at) VALUES (?,?,?,?)")
    .run("ch1", "x", "ch1", 0);
  db.prepare(`
    INSERT INTO videos (id, channel_id, title, published_at, duration_seconds, discovered_at)
    VALUES (?, ?, ?, ?, ?, ?)
  `).run("v1", "ch1", "v1", 0, 600, 0);
  db.prepare(`
    INSERT INTO videos (id, channel_id, title, published_at, duration_seconds, discovered_at)
    VALUES (?, ?, ?, ?, ?, ?)
  `).run("v2", "ch1", "v2", 0, 300, 0);
  return db;
}

describe("clipper queue", () => {
  let db: Database.Database;
  beforeEach(() => { db = makeDb(); });

  it("enqueue adds a row with locked_at NULL", () => {
    enqueue(db, "v1");
    const row = db.prepare("SELECT * FROM clipper_queue WHERE video_id=?").get("v1") as any;
    expect(row).toBeTruthy();
    expect(row.locked_at).toBeNull();
    expect(row.attempts).toBe(0);
  });

  it("enqueue is idempotent — second call does not fail", () => {
    enqueue(db, "v1");
    expect(() => enqueue(db, "v1")).not.toThrow();
    const count = db.prepare("SELECT COUNT(*) as c FROM clipper_queue").get() as any;
    expect(count.c).toBe(1);
  });

  it("dequeue picks shortest video first (priority by duration)", () => {
    enqueue(db, "v1");  // 600s
    enqueue(db, "v2");  // 300s
    const job = dequeue(db);
    expect(job?.videoId).toBe("v2");
  });

  it("dequeue locks the picked job (locked_at + locked_step set)", () => {
    enqueue(db, "v1");
    const job = dequeue(db);
    expect(job).toBeTruthy();
    const row = db.prepare("SELECT * FROM clipper_queue WHERE video_id=?")
      .get("v1") as any;
    expect(row.locked_at).not.toBeNull();
    expect(row.locked_step).toBe("analyzing");
  });

  it("dequeue returns null when no unlocked jobs available", () => {
    enqueue(db, "v1");
    dequeue(db);  // locks v1
    expect(dequeue(db)).toBeNull();
  });

  it("complete removes the job from the queue", () => {
    enqueue(db, "v1");
    dequeue(db);
    complete(db, "v1");
    const row = db.prepare("SELECT * FROM clipper_queue WHERE video_id=?").get("v1");
    expect(row).toBeUndefined();
  });

  it("fail unlocks + increments attempts + records error", () => {
    enqueue(db, "v1");
    dequeue(db);
    fail(db, "v1", "qwen returned garbage");
    const row = db.prepare("SELECT * FROM clipper_queue WHERE video_id=?")
      .get("v1") as any;
    expect(row.locked_at).toBeNull();
    expect(row.attempts).toBe(1);
    expect(row.last_error).toBe("qwen returned garbage");
  });

  it("unlockStale unlocks jobs with locked_at older than threshold", () => {
    enqueue(db, "v1");
    const job = dequeue(db)!;
    // Manually backdate the lock by 31 minutes
    db.prepare("UPDATE clipper_queue SET locked_at=? WHERE video_id=?")
      .run(Date.now() - 31 * 60 * 1000, "v1");
    const unlocked = unlockStale(db, 30 * 60 * 1000);
    expect(unlocked).toBe(1);
    const row = db.prepare("SELECT * FROM clipper_queue WHERE video_id=?")
      .get("v1") as any;
    expect(row.locked_at).toBeNull();
    expect(row.attempts).toBe(1);
  });
});

describe("isWindowActive", () => {
  it("returns true at 22:00 sharp", () => {
    expect(isWindowActive(new Date("2026-05-02T22:00:00"), 22, 8)).toBe(true);
  });
  it("returns true at 02:00 (within window)", () => {
    expect(isWindowActive(new Date("2026-05-02T02:00:00"), 22, 8)).toBe(true);
  });
  it("returns true at 07:59 (window ends at 08:00 exclusive)", () => {
    expect(isWindowActive(new Date("2026-05-02T07:59:00"), 22, 8)).toBe(true);
  });
  it("returns false at 08:00 sharp", () => {
    expect(isWindowActive(new Date("2026-05-02T08:00:00"), 22, 8)).toBe(false);
  });
  it("returns false at 14:00 (outside window)", () => {
    expect(isWindowActive(new Date("2026-05-02T14:00:00"), 22, 8)).toBe(false);
  });
  it("returns false at 21:59", () => {
    expect(isWindowActive(new Date("2026-05-02T21:59:00"), 22, 8)).toBe(false);
  });
});
```

- [ ] **Step 2: Run — expect FAIL**

```bash
pnpm vitest run src/clipper/queue.test.ts
```

Expected: FAIL — module not found.

- [ ] **Step 3: Implement queue.ts**

```ts
// backend/src/clipper/queue.ts
import type Database from "better-sqlite3";

export interface QueueJob {
  videoId: string;
  queuedAt: number;
  attempts: number;
}

/** Idempotent — re-queueing a video that's already in the queue is a no-op. */
export function enqueue(db: Database.Database, videoId: string): void {
  db.prepare(`
    INSERT OR IGNORE INTO clipper_queue (video_id, queued_at, attempts)
    VALUES (?, ?, 0)
  `).run(videoId, Date.now());
}

/**
 * Atomic dequeue + lock. Picks the unlocked job with the shortest video
 * duration first, ties broken by oldest queued_at. Returns null if no job
 * available. Single-slot enforcement happens implicitly: if any row is locked,
 * dequeue will still return another unlocked one. The worker is responsible for
 * only running ONE dequeue at a time (single-process, sequential loop).
 */
export function dequeue(db: Database.Database): QueueJob | null {
  return db.transaction((): QueueJob | null => {
    const row = db.prepare(`
      SELECT q.video_id AS videoId, q.queued_at AS queuedAt, q.attempts AS attempts
        FROM clipper_queue q
        JOIN videos v ON v.id = q.video_id
       WHERE q.locked_at IS NULL
       ORDER BY v.duration_seconds ASC, q.queued_at ASC
       LIMIT 1
    `).get() as QueueJob | undefined;
    if (!row) return null;

    db.prepare(`
      UPDATE clipper_queue
         SET locked_at = ?, locked_step = 'analyzing'
       WHERE video_id = ?
    `).run(Date.now(), row.videoId);

    return row;
  })();
}

export function setStep(db: Database.Database, videoId: string, step: "analyzing" | "rendering"): void {
  db.prepare("UPDATE clipper_queue SET locked_step = ? WHERE video_id = ?")
    .run(step, videoId);
}

export function complete(db: Database.Database, videoId: string): void {
  db.prepare("DELETE FROM clipper_queue WHERE video_id = ?").run(videoId);
}

export function fail(db: Database.Database, videoId: string, error: string): void {
  db.prepare(`
    UPDATE clipper_queue
       SET locked_at  = NULL,
           locked_step = NULL,
           attempts   = attempts + 1,
           last_error = ?
     WHERE video_id = ?
  `).run(error, videoId);
}

/**
 * Unlock jobs whose lock has gone stale (worker crashed mid-job). Returns
 * number of rows unlocked. Increments attempts so we can detect repeat
 * failures.
 */
export function unlockStale(db: Database.Database, olderThanMs: number): number {
  const cutoff = Date.now() - olderThanMs;
  const res = db.prepare(`
    UPDATE clipper_queue
       SET locked_at  = NULL,
           locked_step = NULL,
           attempts   = attempts + 1,
           last_error = COALESCE(last_error, 'auto-unlocked stale lock')
     WHERE locked_at IS NOT NULL
       AND locked_at < ?
  `).run(cutoff);
  return res.changes;
}

/**
 * True if the current time falls within the configured nightly clipping
 * window. Wraps midnight: 22:00–08:00 means 22:00..23:59 OR 00:00..07:59.
 */
export function isWindowActive(now: Date, startHour: number, endHour: number): boolean {
  const h = now.getHours();
  if (startHour < endHour) {
    return h >= startHour && h < endHour;
  }
  // wraps midnight
  return h >= startHour || h < endHour;
}
```

- [ ] **Step 4: Run — expect PASS**

```bash
pnpm vitest run src/clipper/queue.test.ts
```

Expected: 14/14 PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/clipper/queue.ts backend/src/clipper/queue.test.ts
git commit -m "feat(clipper): SQL-backed single-slot job queue + window check"
```

---

## Phase 3 — Qwen Analyzer

### Task 3.1: Test for analyzer happy path + retry + validation

**Files:**
- Create: `backend/src/clipper/qwen-analyzer.test.ts`

- [ ] **Step 1: Write failing test**

```ts
// backend/src/clipper/qwen-analyzer.test.ts
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { analyzeVideo } from "./qwen-analyzer.js";

const VALID_RESPONSE = JSON.stringify([
  { start_sec: 30, end_sec: 60,
    focus: { x: 0.2, y: 0.2, w: 0.6, h: 0.6 },
    reason: "intro hook" },
]);

function mockFetch(responses: Array<{ ok: boolean; body: unknown }>) {
  let i = 0;
  return vi.fn(async () => {
    const r = responses[i++];
    return {
      ok: r.ok,
      status: r.ok ? 200 : 500,
      text: async () => typeof r.body === "string" ? r.body : JSON.stringify(r.body),
      json: async () => r.body,
    } as Response;
  });
}

describe("analyzeVideo", () => {
  beforeEach(() => { vi.useFakeTimers(); });
  afterEach(() => { vi.restoreAllMocks(); vi.useRealTimers(); });

  it("parses a valid LM-Studio response into ClipSpec[]", async () => {
    const fetchFn = mockFetch([{
      ok: true,
      body: { choices: [{ message: { content: VALID_RESPONSE } }] },
    }]);
    const out = await analyzeVideo(
      { filePath: "/fake.mp4", videoId: "v1", durationSec: 600 },
      "prompt",
      { provider: "lmstudio", baseUrl: "http://x", model: "qwen", fetchFn },
    );
    expect(out).toHaveLength(1);
    expect(out[0]).toMatchObject({
      startSec: 30, endSec: 60,
      focus: { x: 0.2, y: 0.2, w: 0.6, h: 0.6 },
      reason: "intro hook",
    });
  });

  it("retries once on invalid JSON, succeeds on second attempt", async () => {
    const fetchFn = mockFetch([
      { ok: true, body: { choices: [{ message: { content: "not json at all" } }] } },
      { ok: true, body: { choices: [{ message: { content: VALID_RESPONSE } }] } },
    ]);
    const out = await analyzeVideo(
      { filePath: "/fake.mp4", videoId: "v1", durationSec: 600 },
      "prompt",
      { provider: "lmstudio", baseUrl: "http://x", model: "qwen", fetchFn },
    );
    expect(out).toHaveLength(1);
    expect(fetchFn).toHaveBeenCalledTimes(2);
  });

  it("throws after second invalid JSON", async () => {
    const fetchFn = mockFetch([
      { ok: true, body: { choices: [{ message: { content: "garbage" } }] } },
      { ok: true, body: { choices: [{ message: { content: "still garbage" } }] } },
    ]);
    await expect(analyzeVideo(
      { filePath: "/fake.mp4", videoId: "v1", durationSec: 600 },
      "prompt",
      { provider: "lmstudio", baseUrl: "http://x", model: "qwen", fetchFn },
    )).rejects.toThrow(/invalid JSON/i);
  });

  it("filters out specs whose start/end exceed video.duration", async () => {
    const body = JSON.stringify([
      { start_sec: 30, end_sec: 60, focus: { x: 0, y: 0, w: 1, h: 1 }, reason: "ok" },
      { start_sec: 700, end_sec: 750, focus: { x: 0, y: 0, w: 1, h: 1 }, reason: "out of bounds" },
    ]);
    const fetchFn = mockFetch([{
      ok: true, body: { choices: [{ message: { content: body } }] },
    }]);
    const out = await analyzeVideo(
      { filePath: "/fake.mp4", videoId: "v1", durationSec: 600 },
      "prompt",
      { provider: "lmstudio", baseUrl: "http://x", model: "qwen", fetchFn },
    );
    expect(out).toHaveLength(1);
    expect(out[0].endSec).toBe(60);
  });

  it("clamps short clips to 20s by extending end_sec", async () => {
    const body = JSON.stringify([
      { start_sec: 30, end_sec: 35, focus: { x: 0, y: 0, w: 1, h: 1 }, reason: "too short" },
    ]);
    const fetchFn = mockFetch([{
      ok: true, body: { choices: [{ message: { content: body } }] },
    }]);
    const out = await analyzeVideo(
      { filePath: "/fake.mp4", videoId: "v1", durationSec: 600 },
      "prompt",
      { provider: "lmstudio", baseUrl: "http://x", model: "qwen", fetchFn },
    );
    expect(out[0].endSec).toBe(50); // 30 + 20
  });

  it("clamps long clips to 90s by trimming end_sec", async () => {
    const body = JSON.stringify([
      { start_sec: 30, end_sec: 200, focus: { x: 0, y: 0, w: 1, h: 1 }, reason: "too long" },
    ]);
    const fetchFn = mockFetch([{
      ok: true, body: { choices: [{ message: { content: body } }] },
    }]);
    const out = await analyzeVideo(
      { filePath: "/fake.mp4", videoId: "v1", durationSec: 600 },
      "prompt",
      { provider: "lmstudio", baseUrl: "http://x", model: "qwen", fetchFn },
    );
    expect(out[0].endSec).toBe(120); // 30 + 90
  });

  it("drops clips that cannot satisfy 20s minimum (start too close to video end)", async () => {
    const body = JSON.stringify([
      { start_sec: 590, end_sec: 595, focus: { x: 0, y: 0, w: 1, h: 1 }, reason: "no room to extend" },
    ]);
    const fetchFn = mockFetch([{
      ok: true, body: { choices: [{ message: { content: body } }] },
    }]);
    const out = await analyzeVideo(
      { filePath: "/fake.mp4", videoId: "v1", durationSec: 600 },
      "prompt",
      { provider: "lmstudio", baseUrl: "http://x", model: "qwen", fetchFn },
    );
    expect(out).toHaveLength(0);
  });

  it("returns empty array when Qwen returns []", async () => {
    const fetchFn = mockFetch([{
      ok: true, body: { choices: [{ message: { content: "[]" } }] },
    }]);
    const out = await analyzeVideo(
      { filePath: "/fake.mp4", videoId: "v1", durationSec: 600 },
      "prompt",
      { provider: "lmstudio", baseUrl: "http://x", model: "qwen", fetchFn },
    );
    expect(out).toEqual([]);
  });
});
```

- [ ] **Step 2: Run — expect FAIL**

```bash
pnpm vitest run src/clipper/qwen-analyzer.test.ts
```

Expected: FAIL — module not found.

- [ ] **Step 3: Implement qwen-analyzer.ts**

```ts
// backend/src/clipper/qwen-analyzer.ts
import { z } from "zod";

export interface ClipSpec {
  startSec: number;
  endSec: number;
  focus: { x: number; y: number; w: number; h: number };
  reason: string;
}

export interface AnalyzeInput {
  filePath: string;
  videoId: string;
  durationSec: number;
}

export interface AnalyzerConfig {
  provider: "lmstudio" | "ollama";
  baseUrl: string;
  model: string;
  fetchFn?: typeof fetch;  // injected for tests
}

const MIN_CLIP_SEC = 20;
const MAX_CLIP_SEC = 90;

const rawSpecSchema = z.object({
  start_sec: z.number(),
  end_sec: z.number(),
  focus: z.object({
    x: z.number().min(0).max(1),
    y: z.number().min(0).max(1),
    w: z.number().min(0).max(1),
    h: z.number().min(0).max(1),
  }),
  reason: z.string(),
});
const rawSpecArraySchema = z.array(rawSpecSchema);

/**
 * Send the video to Qwen via the OpenAI-compatible chat-completions endpoint
 * (LM-Studio and Ollama both support this for multimodal models).
 *
 * Strategy: pass the video file path as a `video_url` content part. The actual
 * encoding (file:// vs base64 vs HTTP URL) depends on the runtime — LM-Studio
 * typically accepts file:// for local files. If the environment requires a
 * different format, this is the place to adapt.
 */
async function callQwen(
  input: AnalyzeInput,
  prompt: string,
  config: AnalyzerConfig,
  retryHint = "",
): Promise<string> {
  const fetchFn = config.fetchFn ?? fetch;
  const url = `${config.baseUrl}/v1/chat/completions`;
  const systemPrompt = retryHint
    ? `${prompt}\n\nWICHTIG: ${retryHint}`
    : prompt;
  const body = {
    model: config.model,
    messages: [
      { role: "system", content: systemPrompt },
      {
        role: "user",
        content: [
          { type: "text", text: `Analysiere dieses Video (Dauer: ${input.durationSec}s).` },
          { type: "video_url", video_url: { url: `file://${input.filePath}` } },
        ],
      },
    ],
    temperature: 0.2,
    stream: false,
  };

  const res = await fetchFn(url, {
    method: "POST",
    headers: { "content-type": "application/json" },
    body: JSON.stringify(body),
  });
  if (!res.ok) {
    throw new Error(`Qwen request failed: ${res.status} ${await res.text()}`);
  }
  const json = (await res.json()) as {
    choices: Array<{ message: { content: string } }>;
  };
  const content = json.choices[0]?.message?.content;
  if (!content) throw new Error("Qwen returned no content");
  return content.trim();
}

function parseAndValidate(content: string): z.infer<typeof rawSpecArraySchema> {
  // Strip optional markdown code-fences if Qwen disobeyed the prompt.
  const stripped = content
    .replace(/^```(?:json)?\s*/i, "")
    .replace(/\s*```\s*$/, "")
    .trim();
  let parsed: unknown;
  try {
    parsed = JSON.parse(stripped);
  } catch (e) {
    throw new Error(`invalid JSON from Qwen: ${(e as Error).message}`);
  }
  return rawSpecArraySchema.parse(parsed);
}

/**
 * Apply duration + bounds constraints to raw specs.
 * - Clips with start/end outside [0, duration] are filtered out.
 * - Clips < 20s have end_sec extended (capped at duration); if no room, dropped.
 * - Clips > 90s have end_sec trimmed.
 */
function clampSpecs(raw: z.infer<typeof rawSpecArraySchema>, durationSec: number): ClipSpec[] {
  const out: ClipSpec[] = [];
  for (const r of raw) {
    if (r.start_sec < 0 || r.start_sec >= durationSec) continue;
    if (r.end_sec <= r.start_sec) continue;

    let endSec = Math.min(r.end_sec, durationSec);
    const len = endSec - r.start_sec;
    if (len < MIN_CLIP_SEC) {
      endSec = Math.min(r.start_sec + MIN_CLIP_SEC, durationSec);
      if (endSec - r.start_sec < MIN_CLIP_SEC) continue;  // no room to extend
    }
    if (endSec - r.start_sec > MAX_CLIP_SEC) {
      endSec = r.start_sec + MAX_CLIP_SEC;
    }
    out.push({
      startSec: r.start_sec,
      endSec,
      focus: r.focus,
      reason: r.reason,
    });
  }
  return out.sort((a, b) => a.startSec - b.startSec);
}

export async function analyzeVideo(
  input: AnalyzeInput,
  prompt: string,
  config: AnalyzerConfig,
): Promise<ClipSpec[]> {
  let raw: z.infer<typeof rawSpecArraySchema>;
  try {
    raw = parseAndValidate(await callQwen(input, prompt, config));
  } catch (firstErr) {
    if (!/invalid JSON/i.test((firstErr as Error).message)) throw firstErr;
    // 1× retry with strict-JSON system hint
    raw = parseAndValidate(
      await callQwen(input, prompt, config, "Respond with valid JSON only. No prose, no markdown."),
    );
  }
  return clampSpecs(raw, input.durationSec);
}
```

- [ ] **Step 4: Run — expect PASS**

```bash
pnpm vitest run src/clipper/qwen-analyzer.test.ts
```

Expected: 8/8 PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/clipper/qwen-analyzer.ts backend/src/clipper/qwen-analyzer.test.ts
git commit -m "feat(clipper): qwen-analyzer with retry + spec clamping"
```

---

## Phase 4 — Remotion Project + Renderer

### Task 4.1: Set up Remotion project at repo root

**Files:**
- Create: `remotion/index.ts`
- Create: `remotion/Root.tsx`
- Create: `remotion/ClipComposition.tsx`
- Create: `remotion/tsconfig.json`

- [ ] **Step 1: Create remotion/ directory and tsconfig**

```bash
mkdir -p /Users/ayysir/Desktop/Hikari/remotion
```

Write `remotion/tsconfig.json`:

```json
{
  "compilerOptions": {
    "target": "ES2020",
    "module": "ESNext",
    "moduleResolution": "Bundler",
    "jsx": "react-jsx",
    "strict": true,
    "esModuleInterop": true,
    "skipLibCheck": true,
    "allowImportingTsExtensions": true,
    "noEmit": true
  },
  "include": ["./**/*.ts", "./**/*.tsx"]
}
```

- [ ] **Step 2: Write ClipComposition.tsx**

```tsx
// remotion/ClipComposition.tsx
import React from "react";
import { AbsoluteFill, OffthreadVideo, useVideoConfig } from "remotion";
import { z } from "zod";

export const clipPropsSchema = z.object({
  src: z.string(),
  startSec: z.number(),
  endSec: z.number(),
  videoWidth: z.number(),
  videoHeight: z.number(),
  cropX: z.number(),
  cropY: z.number(),
  cropW: z.number(),
  cropH: z.number(),
});
export type ClipProps = z.infer<typeof clipPropsSchema>;

export const ClipComposition: React.FC<ClipProps> = ({
  src, startSec,
  videoWidth, videoHeight,
  cropX, cropY, cropW, cropH,
}) => {
  const { fps, width: outW, height: outH } = useVideoConfig();
  const scale = Math.max(outW / cropW, outH / cropH);
  const translateX = -(cropX + cropW / 2) * scale + outW / 2;
  const translateY = -(cropY + cropH / 2) * scale + outH / 2;
  return (
    <AbsoluteFill style={{ backgroundColor: "black", overflow: "hidden" }}>
      <div
        style={{
          position: "absolute",
          width: videoWidth,
          height: videoHeight,
          transform: `translate(${translateX}px, ${translateY}px) scale(${scale})`,
          transformOrigin: "0 0",
        }}
      >
        <OffthreadVideo src={src} startFrom={Math.floor(startSec * fps)} />
      </div>
    </AbsoluteFill>
  );
};
```

- [ ] **Step 3: Write Root.tsx**

```tsx
// remotion/Root.tsx
import React from "react";
import { Composition } from "remotion";
import { ClipComposition, clipPropsSchema } from "./ClipComposition.js";

export const RemotionRoot: React.FC = () => {
  return (
    <Composition
      id="Clip"
      component={ClipComposition}
      schema={clipPropsSchema}
      width={1080}
      height={1920}
      fps={30}
      durationInFrames={300}  // overridden at render-time per clip
      defaultProps={{
        src: "",
        startSec: 0,
        endSec: 60,
        videoWidth: 1920,
        videoHeight: 1080,
        cropX: 0,
        cropY: 0,
        cropW: 1920,
        cropH: 1080,
      }}
      calculateMetadata={({ props }) => ({
        durationInFrames: Math.ceil((props.endSec - props.startSec) * 30),
      })}
    />
  );
};
```

- [ ] **Step 4: Write index.ts**

```ts
// remotion/index.ts
import { registerRoot } from "remotion";
import { RemotionRoot } from "./Root.js";

registerRoot(RemotionRoot);
```

- [ ] **Step 5: Verify Remotion bundle works (smoke test)**

```bash
cd /Users/ayysir/Desktop/Hikari
pnpm exec remotion compositions remotion/index.ts
```

Expected: Output lists `Clip` composition, no errors. (If it complains about React, ensure react/react-dom are installed.)

- [ ] **Step 6: Commit**

```bash
git add remotion/ backend/package.json backend/pnpm-lock.yaml
git commit -m "feat(remotion): Clip composition with smart-crop transform"
```

---

### Task 4.2: Remotion-renderer wrapper module

**Files:**
- Create: `backend/src/clipper/remotion-renderer.ts`
- Create: `backend/src/clipper/remotion-renderer.test.ts`

- [ ] **Step 1: Write failing test (mocking @remotion/renderer)**

```ts
// backend/src/clipper/remotion-renderer.test.ts
import { afterEach, describe, expect, it, vi } from "vitest";
import { renderClip } from "./remotion-renderer.js";

vi.mock("@remotion/bundler", () => ({
  bundle: vi.fn(async () => "/fake/bundle"),
}));
vi.mock("@remotion/renderer", () => ({
  selectComposition: vi.fn(async () => ({
    id: "Clip", width: 1080, height: 1920, fps: 30, durationInFrames: 1800,
  })),
  renderMedia: vi.fn(async () => undefined),
}));
vi.mock("node:fs/promises", async () => ({
  ...(await vi.importActual<object>("node:fs/promises")),
  stat: vi.fn(async () => ({ size: 4_500_000 })),
}));

afterEach(() => vi.clearAllMocks());

describe("renderClip", () => {
  it("calls renderMedia with smart-crop input props and returns file info", async () => {
    const { renderMedia } = await import("@remotion/renderer");
    const out = await renderClip({
      inputPath: "/orig/v1.mp4",
      videoWidth: 1920, videoHeight: 1080,
      spec: {
        startSec: 30, endSec: 80,
        focus: { x: 0.4, y: 0.2, w: 0.2, h: 0.6 },
        reason: "test",
      },
      outputPath: "/clips/c1.mp4",
    });
    expect(out.filePath).toBe("/clips/c1.mp4");
    expect(out.sizeBytes).toBe(4_500_000);
    const callArgs = (renderMedia as any).mock.calls[0][0];
    expect(callArgs.inputProps.src).toBe("file:///orig/v1.mp4");
    expect(callArgs.inputProps.startSec).toBe(30);
    expect(callArgs.inputProps.endSec).toBe(80);
    expect(callArgs.inputProps.cropW).toBeCloseTo(1080 * (9 / 16), 0);
    expect(callArgs.codec).toBe("h264");
  });

  it("propagates renderMedia errors", async () => {
    const { renderMedia } = await import("@remotion/renderer");
    (renderMedia as any).mockImplementationOnce(async () => {
      throw new Error("ffmpeg crashed");
    });
    await expect(renderClip({
      inputPath: "/orig/v1.mp4",
      videoWidth: 1920, videoHeight: 1080,
      spec: {
        startSec: 30, endSec: 80,
        focus: { x: 0, y: 0, w: 1, h: 1 },
        reason: "test",
      },
      outputPath: "/clips/c1.mp4",
    })).rejects.toThrow(/ffmpeg crashed/);
  });
});
```

- [ ] **Step 2: Run — expect FAIL**

```bash
pnpm vitest run src/clipper/remotion-renderer.test.ts
```

Expected: FAIL — module not found.

- [ ] **Step 3: Implement remotion-renderer.ts**

```ts
// backend/src/clipper/remotion-renderer.ts
import { stat } from "node:fs/promises";
import { resolve } from "node:path";
import { bundle } from "@remotion/bundler";
import { renderMedia, selectComposition } from "@remotion/renderer";
import { computeCropRect } from "./crop-math.js";
import type { ClipSpec } from "./qwen-analyzer.js";

export interface RenderInput {
  inputPath: string;
  videoWidth: number;
  videoHeight: number;
  spec: ClipSpec;
  outputPath: string;
}
export interface RenderResult {
  filePath: string;
  sizeBytes: number;
}

const REMOTION_ENTRY = resolve(process.cwd(), "remotion/index.ts");

let bundlePromise: Promise<string> | null = null;
function getBundle(): Promise<string> {
  // Bundle once per process. Reused across all renders.
  bundlePromise ??= bundle({ entryPoint: REMOTION_ENTRY });
  return bundlePromise;
}

export async function renderClip(input: RenderInput): Promise<RenderResult> {
  const { inputPath, videoWidth, videoHeight, spec, outputPath } = input;
  const crop = computeCropRect({
    videoWidth, videoHeight,
    focus: spec.focus,
    targetAspect: 9 / 16,
  });

  const inputProps = {
    src: `file://${inputPath}`,
    startSec: spec.startSec,
    endSec: spec.endSec,
    videoWidth, videoHeight,
    cropX: crop.x, cropY: crop.y, cropW: crop.w, cropH: crop.h,
  };

  const serveUrl = await getBundle();
  const composition = await selectComposition({
    serveUrl,
    id: "Clip",
    inputProps,
  });

  await renderMedia({
    composition,
    serveUrl,
    codec: "h264",
    outputLocation: outputPath,
    inputProps,
  });

  const stats = await stat(outputPath);
  return { filePath: outputPath, sizeBytes: stats.size };
}
```

- [ ] **Step 4: Run — expect PASS**

```bash
pnpm vitest run src/clipper/remotion-renderer.test.ts
```

Expected: 2/2 PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/clipper/remotion-renderer.ts backend/src/clipper/remotion-renderer.test.ts
git commit -m "feat(clipper): remotion-renderer wrapping @remotion/renderer"
```

---

## Phase 5 — Worker Orchestrator

### Task 5.1: Worker integration test (happy path + edge cases)

**Files:**
- Create: `backend/src/clipper/worker.integration.test.ts`

- [ ] **Step 1: Write failing test**

```ts
// backend/src/clipper/worker.integration.test.ts
import Database from "better-sqlite3";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { applyMigrations } from "../db/migrations.js";
import { enqueue } from "./queue.js";
import { processNextJob } from "./worker.js";
import type { ClipSpec } from "./qwen-analyzer.js";

function makeDb(): Database.Database {
  const db = new Database(":memory:");
  applyMigrations(db);
  db.prepare("INSERT INTO channels (id,url,title,added_at) VALUES (?,?,?,?)")
    .run("c1", "x", "ch", 0);
  db.prepare(`
    INSERT INTO videos (id, channel_id, title, published_at, duration_seconds,
                        discovered_at, aspect_ratio, clip_status)
    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
  `).run("v1", "c1", "Vid 1", 0, 600, 0, "16:9", "pending");
  db.prepare(`
    INSERT INTO downloaded_videos (video_id, file_path, file_size_bytes, downloaded_at)
    VALUES (?, ?, ?, ?)
  `).run("v1", "/orig/v1.mp4", 100_000_000, 0);
  // Filter must exist
  db.prepare(`
    INSERT INTO filter_config (id, filter_json, prompt_override, updated_at)
    VALUES (1, ?, NULL, 0)
  `).run(JSON.stringify({
    likeTags: [], dislikeTags: [], moodTags: [], depthTags: [], languages: [],
    minDurationSec: 0, maxDurationSec: 0, examples: "", scoreThreshold: 0,
  }));
  return db;
}

const SPEC_TWO_CLIPS: ClipSpec[] = [
  { startSec: 30, endSec: 90, focus: { x: 0.2, y: 0.2, w: 0.6, h: 0.6 }, reason: "first" },
  { startSec: 200, endSec: 260, focus: { x: 0.3, y: 0.2, w: 0.4, h: 0.6 }, reason: "second" },
];

describe("processNextJob", () => {
  let db: Database.Database;
  beforeEach(() => { db = makeDb(); });

  it("happy path: dequeues, analyzes, renders, inserts clips, marks done", async () => {
    enqueue(db, "v1");
    const analyze = vi.fn(async () => SPEC_TWO_CLIPS);
    const render = vi.fn(async (i) => ({
      filePath: i.outputPath, sizeBytes: 5_000_000,
    }));

    const ran = await processNextJob(db, {
      analyze, render,
      mediaDir: "/clips",
      analyzerConfig: { provider: "lmstudio", baseUrl: "http://x", model: "qwen" },
    });
    expect(ran).toBe(true);
    expect(analyze).toHaveBeenCalledOnce();
    expect(render).toHaveBeenCalledTimes(2);

    const status = db.prepare("SELECT clip_status FROM videos WHERE id=?").get("v1") as any;
    expect(status.clip_status).toBe("done");

    const clips = db.prepare("SELECT * FROM clips ORDER BY order_in_parent").all() as any[];
    expect(clips).toHaveLength(2);
    expect(clips[0].order_in_parent).toBe(0);
    expect(clips[1].order_in_parent).toBe(1);
    expect(clips[0].parent_video_id).toBe("v1");
    expect(clips[0].file_path).toMatch(/\/clips\//);

    const queueRows = db.prepare("SELECT * FROM clipper_queue").all();
    expect(queueRows).toHaveLength(0);
  });

  it("no_highlights path: empty spec → status='no_highlights', no clips, queue cleaned", async () => {
    enqueue(db, "v1");
    const analyze = vi.fn(async () => []);
    const render = vi.fn();

    await processNextJob(db, {
      analyze, render,
      mediaDir: "/clips",
      analyzerConfig: { provider: "lmstudio", baseUrl: "http://x", model: "qwen" },
    });
    expect(render).not.toHaveBeenCalled();
    const v = db.prepare("SELECT clip_status FROM videos WHERE id=?").get("v1") as any;
    expect(v.clip_status).toBe("no_highlights");
    expect(db.prepare("SELECT COUNT(*) c FROM clipper_queue").get()).toEqual({ c: 0 });
    expect(db.prepare("SELECT COUNT(*) c FROM clips").get()).toEqual({ c: 0 });
  });

  it("render-fail path: clip-status='failed', already-rendered clips deleted (atomicity)", async () => {
    enqueue(db, "v1");
    const analyze = vi.fn(async () => SPEC_TWO_CLIPS);
    let calls = 0;
    const render = vi.fn(async (i: any) => {
      calls++;
      if (calls === 1) return { filePath: i.outputPath, sizeBytes: 5_000_000 };
      throw new Error("ffmpeg crashed");
    });

    await processNextJob(db, {
      analyze, render,
      mediaDir: "/clips",
      analyzerConfig: { provider: "lmstudio", baseUrl: "http://x", model: "qwen" },
    });
    const v = db.prepare("SELECT clip_status FROM videos WHERE id=?").get("v1") as any;
    expect(v.clip_status).toBe("failed");
    expect(db.prepare("SELECT COUNT(*) c FROM clips WHERE parent_video_id=?")
      .get("v1")).toEqual({ c: 0 });
  });

  it("short-form passthrough: video ≤ 90s skips analyze + render, inserts passthrough clip", async () => {
    db.prepare("UPDATE videos SET duration_seconds=60 WHERE id=?").run("v1");
    enqueue(db, "v1");
    const analyze = vi.fn();
    const render = vi.fn();

    await processNextJob(db, {
      analyze, render,
      mediaDir: "/clips",
      analyzerConfig: { provider: "lmstudio", baseUrl: "http://x", model: "qwen" },
    });
    expect(analyze).not.toHaveBeenCalled();
    expect(render).not.toHaveBeenCalled();
    const clips = db.prepare("SELECT * FROM clips").all() as any[];
    expect(clips).toHaveLength(1);
    expect(clips[0].reason).toBe("short-form-passthrough");
    expect(clips[0].file_path).toBe("/orig/v1.mp4");
    expect(clips[0].focus_x).toBe(0);
    expect(clips[0].focus_w).toBe(1);
    const v = db.prepare("SELECT clip_status FROM videos WHERE id=?").get("v1") as any;
    expect(v.clip_status).toBe("done");
  });

  it("returns false when queue is empty", async () => {
    const ran = await processNextJob(db, {
      analyze: vi.fn(), render: vi.fn(),
      mediaDir: "/clips",
      analyzerConfig: { provider: "lmstudio", baseUrl: "http://x", model: "qwen" },
    });
    expect(ran).toBe(false);
  });
});
```

- [ ] **Step 2: Run — expect FAIL**

```bash
pnpm vitest run src/clipper/worker.integration.test.ts
```

Expected: FAIL — module not found.

- [ ] **Step 3: Implement worker.ts**

```ts
// backend/src/clipper/worker.ts
import { unlink } from "node:fs/promises";
import { join } from "node:path";
import { randomUUID } from "node:crypto";
import type Database from "better-sqlite3";
import { getActiveFilter } from "../scorer/filter-repo.js";
import { complete, dequeue, fail, setStep } from "./queue.js";
import { buildClipperPrompt } from "./prompt-builder.js";
import type { ClipSpec, AnalyzerConfig } from "./qwen-analyzer.js";
import type { RenderResult } from "./remotion-renderer.js";

export interface WorkerDeps {
  analyze: (
    input: { filePath: string; videoId: string; durationSec: number },
    prompt: string,
    config: AnalyzerConfig,
  ) => Promise<ClipSpec[]>;
  render: (input: {
    inputPath: string;
    videoWidth: number; videoHeight: number;
    spec: ClipSpec;
    outputPath: string;
  }) => Promise<RenderResult>;
  mediaDir: string;
  analyzerConfig: AnalyzerConfig;
}

interface VideoRow {
  id: string;
  duration_seconds: number;
  aspect_ratio: string | null;
}
interface DownloadRow {
  file_path: string;
}

function parseAspect(aspect: string | null): { width: number; height: number } {
  // Default to 1920x1080 if unknown. Matches typical YouTube content.
  if (!aspect) return { width: 1920, height: 1080 };
  const m = aspect.match(/^(\d+):(\d+)$/);
  if (!m) return { width: 1920, height: 1080 };
  const w = Number(m[1]);
  const h = Number(m[2]);
  if (w === 9 && h === 16) return { width: 1080, height: 1920 };
  return { width: 1920, height: Math.round(1920 * h / w) };
}

const SHORT_FORM_THRESHOLD_SEC = 90;

/**
 * Process exactly ONE job from the queue. Returns true if a job was processed
 * (success or failure), false if the queue was empty. Caller is responsible
 * for the schedule-window check and the loop.
 */
export async function processNextJob(db: Database.Database, deps: WorkerDeps): Promise<boolean> {
  const job = dequeue(db);
  if (!job) return false;

  const video = db.prepare(`
    SELECT id, duration_seconds, aspect_ratio FROM videos WHERE id = ?
  `).get(job.videoId) as VideoRow | undefined;
  const dl = db.prepare(`
    SELECT file_path FROM downloaded_videos WHERE video_id = ?
  `).get(job.videoId) as DownloadRow | undefined;

  if (!video || !dl) {
    fail(db, job.videoId, "video or download row missing");
    db.prepare("UPDATE videos SET clip_status='failed' WHERE id=?").run(job.videoId);
    return true;
  }

  // Short-form passthrough — no AI, no render.
  if (video.duration_seconds <= SHORT_FORM_THRESHOLD_SEC) {
    insertPassthroughClip(db, video.id, video.duration_seconds, dl.file_path);
    db.prepare("UPDATE videos SET clip_status='done' WHERE id=?").run(video.id);
    complete(db, video.id);
    return true;
  }

  // Analyze
  db.prepare("UPDATE videos SET clip_status='analyzing' WHERE id=?").run(video.id);
  setStep(db, video.id, "analyzing");
  const filter = getActiveFilter(db);
  const prompt = buildClipperPrompt(filter, { aspectRatio: video.aspect_ratio });

  let specs: ClipSpec[];
  try {
    specs = await deps.analyze(
      { filePath: dl.file_path, videoId: video.id, durationSec: video.duration_seconds },
      prompt,
      deps.analyzerConfig,
    );
  } catch (e) {
    fail(db, video.id, `analyze: ${(e as Error).message}`);
    db.prepare("UPDATE videos SET clip_status='failed' WHERE id=?").run(video.id);
    return true;
  }

  if (specs.length === 0) {
    db.prepare("UPDATE videos SET clip_status='no_highlights' WHERE id=?").run(video.id);
    complete(db, video.id);
    return true;
  }

  // Render — if any clip fails, delete previously-rendered files for this video and mark failed.
  db.prepare("UPDATE videos SET clip_status='rendering' WHERE id=?").run(video.id);
  setStep(db, video.id, "rendering");
  const { width: vw, height: vh } = parseAspect(video.aspect_ratio);
  const rendered: { id: string; result: RenderResult; spec: ClipSpec; order: number }[] = [];

  for (let i = 0; i < specs.length; i++) {
    const spec = specs[i];
    const clipId = randomUUID();
    const outputPath = join(deps.mediaDir, `${clipId}.mp4`);
    try {
      const result = await deps.render({
        inputPath: dl.file_path,
        videoWidth: vw, videoHeight: vh,
        spec, outputPath,
      });
      rendered.push({ id: clipId, result, spec, order: i });
    } catch (e) {
      // Cleanup partially-rendered files
      for (const r of rendered) {
        await unlink(r.result.filePath).catch(() => undefined);
      }
      fail(db, video.id, `render clip ${i}: ${(e as Error).message}`);
      db.prepare("UPDATE videos SET clip_status='failed' WHERE id=?").run(video.id);
      return true;
    }
  }

  // Insert clips atomically
  const insert = db.prepare(`
    INSERT INTO clips (
      id, parent_video_id, order_in_parent,
      start_seconds, end_seconds, file_path, file_size_bytes,
      focus_x, focus_y, focus_w, focus_h,
      reason, created_at, added_to_feed_at
    ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
  `);
  const now = Date.now();
  db.transaction(() => {
    for (const r of rendered) {
      insert.run(
        r.id, video.id, r.order,
        r.spec.startSec, r.spec.endSec,
        r.result.filePath, r.result.sizeBytes,
        r.spec.focus.x, r.spec.focus.y, r.spec.focus.w, r.spec.focus.h,
        r.spec.reason, now, now,
      );
    }
  })();

  db.prepare("UPDATE videos SET clip_status='done' WHERE id=?").run(video.id);
  complete(db, video.id);
  return true;
}

function insertPassthroughClip(
  db: Database.Database,
  videoId: string,
  durationSec: number,
  filePath: string,
): void {
  const sizeBytes = (db.prepare(
    "SELECT file_size_bytes FROM downloaded_videos WHERE video_id=?",
  ).get(videoId) as { file_size_bytes: number } | undefined)?.file_size_bytes ?? 0;
  const now = Date.now();
  db.prepare(`
    INSERT INTO clips (
      id, parent_video_id, order_in_parent,
      start_seconds, end_seconds, file_path, file_size_bytes,
      focus_x, focus_y, focus_w, focus_h,
      reason, created_at, added_to_feed_at
    ) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
  `).run(
    randomUUID(), videoId, 0,
    0, durationSec, filePath, sizeBytes,
    0, 0, 1, 1,
    "short-form-passthrough", now, now,
  );
}
```

- [ ] **Step 4: Run — expect PASS**

```bash
pnpm vitest run src/clipper/worker.integration.test.ts
```

Expected: 5/5 PASS. (`getActiveFilter` may need `filter_config` row; fixture seeds it.)

- [ ] **Step 5: Commit**

```bash
git add backend/src/clipper/worker.ts backend/src/clipper/worker.integration.test.ts
git commit -m "feat(clipper): worker orchestrator (analyze → render → insert)"
```

---

### Task 5.2: Worker entry script with schedule loop

**Files:**
- Create: `backend/scripts/clipper-worker.ts`

- [ ] **Step 1: Implement entry script**

```ts
// backend/scripts/clipper-worker.ts
import { mkdir } from "node:fs/promises";
import Database from "better-sqlite3";
import pino from "pino";
import { loadConfig } from "../src/config.js";
import { applyMigrations } from "../src/db/migrations.js";
import { processNextJob } from "../src/clipper/worker.js";
import { isWindowActive, unlockStale } from "../src/clipper/queue.js";
import { analyzeVideo } from "../src/clipper/qwen-analyzer.js";
import { renderClip } from "../src/clipper/remotion-renderer.js";

const log = pino({ name: "clipper-worker", transport: { target: "pino-pretty" } });

async function main(): Promise<void> {
  const cfg = loadConfig();
  const db = new Database(cfg.database.path);
  db.pragma("journal_mode = WAL");
  applyMigrations(db);

  const mediaDir = `${cfg.media.directory}/clips`;
  await mkdir(mediaDir, { recursive: true });

  log.info({ schedule: `${cfg.clipper.scheduleStartHour}:00–${cfg.clipper.scheduleEndHour}:00`,
              model: cfg.clipper.model }, "clipper-worker started");

  // Recover stale locks from a prior crashed run
  const recovered = unlockStale(db, 30 * 60 * 1000);
  if (recovered > 0) log.warn({ recovered }, "unlocked stale locks");

  const POLL_INTERVAL_MS = 60_000;

  // Use a flag so SIGTERM can drain gracefully
  let stopping = false;
  process.on("SIGTERM", () => { stopping = true; log.info("SIGTERM received, draining"); });
  process.on("SIGINT",  () => { stopping = true; log.info("SIGINT received, draining"); });

  while (!stopping) {
    if (!isWindowActive(new Date(), cfg.clipper.scheduleStartHour, cfg.clipper.scheduleEndHour)) {
      await sleep(POLL_INTERVAL_MS);
      continue;
    }
    try {
      const ran = await processNextJob(db, {
        analyze: analyzeVideo,
        render: renderClip,
        mediaDir,
        analyzerConfig: {
          provider: cfg.clipper.provider,
          baseUrl: cfg.clipper.baseUrl,
          model: cfg.clipper.model,
        },
      });
      if (!ran) await sleep(POLL_INTERVAL_MS);
    } catch (e) {
      log.error({ err: e }, "unhandled error in processNextJob");
      await sleep(POLL_INTERVAL_MS);
    }
  }

  log.info("clipper-worker stopped cleanly");
  db.close();
}

function sleep(ms: number): Promise<void> {
  return new Promise((res) => setTimeout(res, ms));
}

main().catch((e) => {
  log.fatal({ err: e }, "fatal");
  process.exit(1);
});
```

- [ ] **Step 2: Smoke-run the worker (does it start without crash?)**

```bash
cd /Users/ayysir/Desktop/Hikari/backend
timeout 5 pnpm clipper || true
```

Expected: log line "clipper-worker started" + (depending on time) either "outside window" sleep or attempts to dequeue. Exits cleanly via SIGTERM after 5s. No stack traces.

- [ ] **Step 3: Commit**

```bash
git add backend/scripts/clipper-worker.ts
git commit -m "feat(clipper): worker entry script with schedule loop + drain"
```

---

## Phase 6 — Pipeline Integration

### Task 6.1: Adjust orchestrator: approved → enqueue (instead of feed_items)

**Files:**
- Modify: `backend/src/pipeline/orchestrator.ts`
- Modify: `backend/src/pipeline/orchestrator.test.ts`

- [ ] **Step 1: Update orchestrator.test.ts to assert new flow**

Replace the `it("writes approved video to feed_items and triggers download"` test with:

```ts
it("approved video: enqueues for clipper, sets clip_status='pending', NOT in feed_items", async () => {
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

  const v = db.prepare("SELECT clip_status FROM videos WHERE id='vid1'").get() as any;
  expect(v.clip_status).toBe("pending");

  const queued = db.prepare("SELECT * FROM clipper_queue WHERE video_id='vid1'").get();
  expect(queued).toBeTruthy();

  const feed = db.prepare("SELECT * FROM feed_items WHERE video_id='vid1'").all();
  expect(feed).toHaveLength(0);

  expect(download).toHaveBeenCalledOnce();
});
```

The "rejected" test remains unchanged.

- [ ] **Step 2: Run — expect FAIL**

```bash
pnpm vitest run src/pipeline/orchestrator.test.ts
```

Expected: FAIL — current code inserts into feed_items.

- [ ] **Step 3: Update orchestrator.ts — both approve paths**

The current file has TWO places that call `insertFeedItem(db, videoId, now)`:
1. The Green-Card / Vertrauenskanal auto-approve block (~line 96)
2. The regular `decision === "approved"` block (~line 122)

Both must change to enqueue the clipper instead.

Add import at top of file:

```ts
import { enqueue } from "../clipper/queue.js";
```

In the Green-Card block, replace:

```ts
db.transaction(() => {
  insertVideo(db, meta, transcript, channelId);
  insertScore(db, videoId, autoApproveScore(), "approved", now);
  insertSponsors(db, videoId, sponsors);
  insertDownload(db, videoId, dl, now);
  insertFeedItem(db, videoId, now);   // ← REMOVE
})();
```

with:

```ts
db.transaction(() => {
  insertVideo(db, meta, transcript, channelId);
  insertScore(db, videoId, autoApproveScore(), "approved", now);
  insertSponsors(db, videoId, sponsors);
  insertDownload(db, videoId, dl, now);
  db.prepare("UPDATE videos SET clip_status='pending' WHERE id=?").run(videoId);
  enqueue(db, videoId);
})();
```

In the regular-approve block, replace:

```ts
if (decision === "approved") {
  const dl = await deps.download(videoId);
  db.transaction(() => {
    insertVideo(db, meta, transcript, channelId);
    insertScore(db, videoId, scored, decision, now);
    insertSponsors(db, videoId, sponsors);
    insertDownload(db, videoId, dl, now);
    insertFeedItem(db, videoId, now);   // ← REMOVE
  })();
}
```

with:

```ts
if (decision === "approved") {
  const dl = await deps.download(videoId);
  db.transaction(() => {
    insertVideo(db, meta, transcript, channelId);
    insertScore(db, videoId, scored, decision, now);
    insertSponsors(db, videoId, sponsors);
    insertDownload(db, videoId, dl, now);
    db.prepare("UPDATE videos SET clip_status='pending' WHERE id=?").run(videoId);
    enqueue(db, videoId);
  })();
}
```

The `insertFeedItem` helper function in this file becomes unused — leave it for now (it's still imported by tests that simulate legacy paths) and remove only when no callers remain.

- [ ] **Step 4: Run — expect PASS**

```bash
pnpm vitest run src/pipeline/orchestrator.test.ts
```

Expected: 2/2 PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/pipeline/orchestrator.ts backend/src/pipeline/orchestrator.test.ts
git commit -m "feat(pipeline): approved videos enqueue for clipper instead of feed_items"
```

---

## Phase 7 — Feed API (UNION + Cooldown + Topic-Mix)

### Task 7.1: UNION feed query with kind discriminator

**Files:**
- Modify: `backend/src/api/feed.ts`
- Modify: `backend/src/api/feed.test.ts`

- [ ] **Step 1: Read existing feed.ts**

```bash
cat /Users/ayysir/Desktop/Hikari/backend/src/api/feed.ts | head -80
```

Note current shape of `GET /feed` and the query it runs.

- [ ] **Step 2: Add test for UNION result**

Add in `feed.test.ts`:

```ts
it("returns clip rows with kind='clip' and parentVideoId set", async () => {
  // seed legacy item
  db.prepare(`INSERT INTO feed_items (video_id, added_to_feed_at, is_pre_clipper)
              VALUES ('legacy1', 1000, 1)`).run();
  // seed clip
  db.prepare(`
    INSERT INTO clips (id, parent_video_id, order_in_parent,
      start_seconds, end_seconds, file_path, file_size_bytes,
      focus_x, focus_y, focus_w, focus_h,
      reason, created_at, added_to_feed_at)
    VALUES ('clip1', 'parent1', 0, 30, 90, '/c.mp4', 5_000_000,
            0,0,1,1, 'r', 2000, 2000)
  `).run();
  const rows = listFeedRaw(db, 50);
  const clip = rows.find((r) => r.id === "clip1");
  expect(clip).toBeTruthy();
  expect(clip!.kind).toBe("clip");
  expect(clip!.parentVideoId).toBe("parent1");
  const legacy = rows.find((r) => r.id === "legacy1");
  expect(legacy!.kind).toBe("legacy");
  expect(legacy!.parentVideoId).toBe("legacy1");
});
```

- [ ] **Step 3: Run — expect FAIL**

```bash
pnpm vitest run src/api/feed.test.ts
```

Expected: FAIL — `listFeedRaw` not exported.

- [ ] **Step 4: Add `listFeedRaw` UNION query in feed.ts**

```ts
export interface RawFeedRow {
  kind: "clip" | "legacy";
  id: string;
  parentVideoId: string;
  addedToFeedAt: number;
  channelId: string;
  category: string | null;
  durationSec: number;
}

export function listFeedRaw(db: Database.Database, limit: number): RawFeedRow[] {
  return db.prepare(`
    SELECT 'clip' AS kind,
           c.id AS id,
           c.parent_video_id AS parentVideoId,
           c.added_to_feed_at AS addedToFeedAt,
           v.channel_id AS channelId,
           s.category AS category,
           (c.end_seconds - c.start_seconds) AS durationSec
      FROM clips c
      JOIN videos v ON v.id = c.parent_video_id
      LEFT JOIN scores s ON s.video_id = c.parent_video_id
     WHERE c.seen_at IS NULL
    UNION ALL
    SELECT 'legacy' AS kind,
           f.video_id AS id,
           f.video_id AS parentVideoId,
           f.added_to_feed_at AS addedToFeedAt,
           v.channel_id AS channelId,
           s.category AS category,
           v.duration_seconds AS durationSec
      FROM feed_items f
      JOIN videos v ON v.id = f.video_id
      LEFT JOIN scores s ON s.video_id = f.video_id
     WHERE f.seen_at IS NULL AND f.is_pre_clipper = 1
    ORDER BY addedToFeedAt DESC
    LIMIT ?
  `).all(limit) as RawFeedRow[];
}
```

- [ ] **Step 5: Run — expect PASS**

```bash
pnpm vitest run src/api/feed.test.ts -t "kind"
```

Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/src/api/feed.ts backend/src/api/feed.test.ts
git commit -m "feat(api/feed): UNION query over clips + legacy feed_items"
```

---

### Task 7.2: Cooldown + Topic-Mix algorithm

**Files:**
- Modify: `backend/src/api/feed.ts`
- Modify: `backend/src/api/feed.test.ts`

- [ ] **Step 1: Add cooldown tests**

```ts
it("cooldown: same parent_video_id never appears twice in 3-window", () => {
  const candidates: RawFeedRow[] = [
    row("c1", "p1", "ch1", "math", 1000),
    row("c2", "p1", "ch1", "math", 999),  // same parent, must be skipped
    row("c3", "p2", "ch2", "tech", 998),
    row("c4", "p1", "ch1", "math", 997),  // p1 must NOT be third in a row
  ];
  const out = applyCooldown(candidates, 4);
  const parents = out.map((r) => r.parentVideoId);
  for (let i = 0; i < parents.length - 2; i++) {
    expect(new Set([parents[i], parents[i+1], parents[i+2]]).size).toBeGreaterThan(1);
  }
});

it("cooldown: same channel max 2× in 3-window", () => {
  const candidates: RawFeedRow[] = [
    row("c1", "p1", "ch1", "math", 1000),
    row("c2", "p2", "ch1", "math", 999),   // same channel — OK (1× of 2 allowed)
    row("c3", "p3", "ch1", "math", 998),   // 3rd from ch1 in row → must skip
    row("c4", "p4", "ch2", "tech", 997),
    row("c5", "p3", "ch1", "math", 996),
  ];
  const out = applyCooldown(candidates, 5);
  // After ch1 appeared 2×, the next must be from a different channel
  expect(out[0].channelId).toBe("ch1");
  expect(out[1].channelId).toBe("ch1");
  expect(out[2].channelId).not.toBe("ch1");
});

it("topic-mix tie-break: prefer different category from last output when possible", () => {
  const candidates: RawFeedRow[] = [
    row("c1", "p1", "ch1", "math", 1000),
    row("c2", "p2", "ch2", "math", 999),    // same category as c1
    row("c3", "p3", "ch3", "tech", 998),    // different category
  ];
  const out = applyCooldown(candidates, 3);
  expect(out[0].id).toBe("c1");
  // Topic-mix should swap c2 with c3 (different category) since both pass cooldown
  expect(out[1].id).toBe("c3");
  expect(out[2].id).toBe("c2");
});

function row(id: string, parent: string, channel: string, cat: string, t: number): RawFeedRow {
  return {
    kind: "clip", id, parentVideoId: parent, channelId: channel,
    category: cat, addedToFeedAt: t, durationSec: 60,
  };
}
```

- [ ] **Step 2: Run — expect FAIL**

```bash
pnpm vitest run src/api/feed.test.ts -t "cooldown\|topic-mix"
```

Expected: FAIL — `applyCooldown` not exported.

- [ ] **Step 3: Implement applyCooldown**

```ts
const COOLDOWN_WINDOW = 3;
const CHANNEL_MAX_IN_WINDOW = 2;
const LOOKAHEAD = 5;

export function applyCooldown(candidates: RawFeedRow[], pageSize: number): RawFeedRow[] {
  const out: RawFeedRow[] = [];
  const remaining = [...candidates];

  while (out.length < pageSize && remaining.length > 0) {
    const window = remaining.slice(0, LOOKAHEAD);
    const last3 = out.slice(-COOLDOWN_WINDOW);
    const last3Parents = new Set(last3.map((r) => r.parentVideoId));
    const channelCount = (chan: string) =>
      last3.filter((r) => r.channelId === chan).length;

    // Phase 1: strict cooldown
    let pickIdx = window.findIndex((r) =>
      !last3Parents.has(r.parentVideoId) &&
      channelCount(r.channelId) < CHANNEL_MAX_IN_WINDOW
    );

    // Phase 2: relax channel cap to 3
    if (pickIdx === -1) {
      pickIdx = window.findIndex((r) =>
        !last3Parents.has(r.parentVideoId) &&
        channelCount(r.channelId) < 3
      );
    }
    // Phase 3: still nothing — accept anything that is NOT a parent-cooldown violation
    if (pickIdx === -1) {
      pickIdx = window.findIndex((r) => !last3Parents.has(r.parentVideoId));
    }
    // Phase 4: nothing left in window. Try expanding window to entire remaining.
    if (pickIdx === -1) {
      pickIdx = remaining.findIndex((r) => !last3Parents.has(r.parentVideoId));
      if (pickIdx === -1) break;  // truly nothing — cut the page short
      out.push(remaining.splice(pickIdx, 1)[0]);
      continue;
    }

    const primary = window[pickIdx];

    // Topic-mix look-ahead: if primary.category == last(output).category and
    // there is another cooldown-OK item in window with a different category,
    // swap.
    const lastOut = out[out.length - 1];
    if (lastOut && primary.category && lastOut.category === primary.category) {
      const swapIdx = window.findIndex((r, i) =>
        i !== pickIdx &&
        !last3Parents.has(r.parentVideoId) &&
        channelCount(r.channelId) < CHANNEL_MAX_IN_WINDOW &&
        r.category && r.category !== lastOut.category
      );
      if (swapIdx !== -1) {
        const better = window[swapIdx];
        const realIdx = remaining.indexOf(better);
        out.push(remaining.splice(realIdx, 1)[0]);
        continue;
      }
    }

    const realIdx = remaining.indexOf(primary);
    out.push(remaining.splice(realIdx, 1)[0]);
  }

  return out;
}
```

- [ ] **Step 4: Run — expect PASS**

```bash
pnpm vitest run src/api/feed.test.ts -t "cooldown\|topic-mix"
```

Expected: 3/3 PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/src/api/feed.ts backend/src/api/feed.test.ts
git commit -m "feat(api/feed): cooldown-3 + topic-mix lookahead algorithm"
```

---

### Task 7.3: Wire up GET /feed handler

**Files:**
- Modify: `backend/src/api/feed.ts`

- [ ] **Step 1: Update GET /feed to use new pipeline**

In the existing route handler:

```ts
fastify.get("/feed", async (req) => {
  const limit = Number((req.query as any)?.limit ?? 50);
  const candidates = listFeedRaw(db, 100);
  const ordered = applyCooldown(candidates, limit);

  // Hydrate to FeedItem DTO
  return ordered.map((r) => hydrateFeedItem(db, r));
});
```

Add `hydrateFeedItem` helper:

```ts
interface FeedItemDto {
  id: string;
  kind: "clip" | "legacy";
  parentVideoId: string;
  fileUrl: string;
  durationSec: number;
  title: string;
  channelTitle: string;
  thumbnailUrl: string | null;
  startSec: number | null;  // only for clips
  endSec: number | null;
}

function hydrateFeedItem(db: Database.Database, row: RawFeedRow): FeedItemDto {
  if (row.kind === "clip") {
    const c = db.prepare(`
      SELECT c.start_seconds, c.end_seconds, c.file_path,
             v.title AS videoTitle, v.thumbnail_url AS thumb,
             ch.title AS channelTitle
        FROM clips c
        JOIN videos v ON v.id = c.parent_video_id
        JOIN channels ch ON ch.id = v.channel_id
       WHERE c.id = ?
    `).get(row.id) as any;
    return {
      id: row.id,
      kind: "clip",
      parentVideoId: row.parentVideoId,
      fileUrl: `/media/clips/${encodeURIComponent(c.file_path.split("/").pop()!)}`,
      durationSec: row.durationSec,
      title: c.videoTitle,
      channelTitle: c.channelTitle,
      thumbnailUrl: c.thumb,
      startSec: c.start_seconds,
      endSec: c.end_seconds,
    };
  }
  // legacy
  const l = db.prepare(`
    SELECT v.title, v.thumbnail_url AS thumb, ch.title AS channelTitle,
           dl.file_path
      FROM videos v
      JOIN channels ch ON ch.id = v.channel_id
      JOIN downloaded_videos dl ON dl.video_id = v.id
     WHERE v.id = ?
  `).get(row.id) as any;
  return {
    id: row.id,
    kind: "legacy",
    parentVideoId: row.id,
    fileUrl: `/media/originals/${encodeURIComponent(l.file_path.split("/").pop()!)}`,
    durationSec: row.durationSec,
    title: l.title,
    channelTitle: l.channelTitle,
    thumbnailUrl: l.thumb,
    startSec: null,
    endSec: null,
  };
}
```

- [ ] **Step 2: Run all api tests**

```bash
pnpm vitest run src/api/
```

Expected: PASS (or update existing tests if they expect old field shape).

- [ ] **Step 3: Commit**

```bash
git add backend/src/api/feed.ts
git commit -m "feat(api/feed): hydrate FeedItem DTO from clips + legacy union"
```

---

## Phase 8 — Status API + Retry + Original-Player API

### Task 8.1: GET /clipper/status + POST /clipper/retry-failed

**Files:**
- Create: `backend/src/api/clipper-status.ts`
- Create: `backend/src/api/clipper-status.test.ts`
- Modify: `backend/src/index.ts` (register routes)

- [ ] **Step 1: Write failing test**

```ts
// backend/src/api/clipper-status.test.ts
import Database from "better-sqlite3";
import { beforeEach, describe, expect, it } from "vitest";
import Fastify from "fastify";
import { applyMigrations } from "../db/migrations.js";
import { registerClipperStatusRoutes } from "./clipper-status.js";

function makeApp(db: Database.Database) {
  const app = Fastify();
  registerClipperStatusRoutes(app, db, { startHour: 22, endHour: 8 });
  return app;
}

function seedVideo(db: Database.Database, id: string, status: string | null): void {
  db.prepare("INSERT OR IGNORE INTO channels (id,url,title,added_at) VALUES ('c1','x','c',0)").run();
  db.prepare(`
    INSERT INTO videos (id, channel_id, title, published_at, duration_seconds,
                        discovered_at, clip_status)
    VALUES (?, 'c1', ?, 0, 600, 0, ?)
  `).run(id, id, status);
}

describe("GET /clipper/status", () => {
  let db: Database.Database;
  beforeEach(() => {
    db = new Database(":memory:");
    applyMigrations(db);
  });

  it("returns counts grouped by clip_status", async () => {
    seedVideo(db, "v1", "pending");
    seedVideo(db, "v2", "pending");
    seedVideo(db, "v3", "rendering");
    seedVideo(db, "v4", "failed");
    seedVideo(db, "v5", "no_highlights");
    seedVideo(db, "v6", "done");
    db.prepare("INSERT INTO clipper_queue (video_id, queued_at) VALUES ('v1', 0)").run();
    db.prepare("INSERT INTO clipper_queue (video_id, queued_at) VALUES ('v2', 0)").run();

    const app = makeApp(db);
    const res = await app.inject({ method: "GET", url: "/clipper/status" });
    expect(res.statusCode).toBe(200);
    const body = res.json();
    expect(body).toMatchObject({
      pending: 2,
      processing: 1,        // 'analyzing' or 'rendering'
      failed: 1,
      no_highlights: 1,
    });
    expect(typeof body.isWindowActive).toBe("boolean");
    await app.close();
  });
});

describe("POST /clipper/retry-failed", () => {
  it("resets failed videos to pending and re-enqueues them", async () => {
    const db = new Database(":memory:");
    applyMigrations(db);
    seedVideo(db, "v1", "failed");
    seedVideo(db, "v2", "failed");
    seedVideo(db, "v3", "done");

    const app = makeApp(db);
    const res = await app.inject({ method: "POST", url: "/clipper/retry-failed" });
    expect(res.statusCode).toBe(200);
    expect(res.json()).toEqual({ retriedCount: 2 });

    const v1 = db.prepare("SELECT clip_status FROM videos WHERE id='v1'").get() as any;
    expect(v1.clip_status).toBe("pending");
    expect(db.prepare("SELECT COUNT(*) c FROM clipper_queue").get())
      .toEqual({ c: 2 });
    await app.close();
  });
});
```

- [ ] **Step 2: Run — expect FAIL**

```bash
pnpm vitest run src/api/clipper-status.test.ts
```

Expected: FAIL — module not found.

- [ ] **Step 3: Implement clipper-status.ts**

```ts
// backend/src/api/clipper-status.ts
import type { FastifyInstance } from "fastify";
import type Database from "better-sqlite3";
import { enqueue, isWindowActive } from "../clipper/queue.js";

export function registerClipperStatusRoutes(
  app: FastifyInstance,
  db: Database.Database,
  schedule: { startHour: number; endHour: number },
): void {
  app.get("/clipper/status", async () => {
    const counts = db.prepare(`
      SELECT clip_status AS status, COUNT(*) AS c
        FROM videos
       WHERE clip_status IS NOT NULL
       GROUP BY clip_status
    `).all() as { status: string; c: number }[];
    const map = Object.fromEntries(counts.map((r) => [r.status, r.c]));

    return {
      pending:        map["pending"]        ?? 0,
      processing:    (map["analyzing"]      ?? 0) + (map["rendering"] ?? 0),
      failed:         map["failed"]         ?? 0,
      no_highlights: map["no_highlights"]   ?? 0,
      done:           map["done"]           ?? 0,
      isWindowActive: isWindowActive(new Date(), schedule.startHour, schedule.endHour),
      lastRanAt: lastRunTimestamp(db),
    };
  });

  app.post("/clipper/retry-failed", async () => {
    const failed = db.prepare("SELECT id FROM videos WHERE clip_status='failed'").all() as
      { id: string }[];
    db.transaction(() => {
      for (const v of failed) {
        db.prepare("UPDATE videos SET clip_status='pending' WHERE id=?").run(v.id);
        enqueue(db, v.id);
      }
    })();
    return { retriedCount: failed.length };
  });
}

function lastRunTimestamp(db: Database.Database): number | null {
  const row = db.prepare("SELECT MAX(created_at) AS t FROM clips").get() as { t: number | null };
  return row.t;
}
```

- [ ] **Step 4: Run — expect PASS**

```bash
pnpm vitest run src/api/clipper-status.test.ts
```

Expected: 2/2 PASS.

- [ ] **Step 5: Register route in index.ts**

In `backend/src/index.ts`, after the existing route registrations:

```ts
import { registerClipperStatusRoutes } from "./api/clipper-status.js";
// ...
registerClipperStatusRoutes(app, db, {
  startHour: cfg.clipper.scheduleStartHour,
  endHour:   cfg.clipper.scheduleEndHour,
});
```

- [ ] **Step 6: Commit**

```bash
git add backend/src/api/clipper-status.ts backend/src/api/clipper-status.test.ts \
        backend/src/index.ts
git commit -m "feat(api): clipper-status + retry-failed endpoints"
```

---

### Task 8.2: GET /videos/:id/full for FullscreenOriginalPlayer

**Files:**
- Create: `backend/src/api/video-full.ts`
- Create: `backend/src/api/video-full.test.ts`
- Modify: `backend/src/index.ts`

- [ ] **Step 1: Test**

```ts
// backend/src/api/video-full.test.ts
import Database from "better-sqlite3";
import Fastify from "fastify";
import { describe, expect, it } from "vitest";
import { applyMigrations } from "../db/migrations.js";
import { registerVideoFullRoute } from "./video-full.js";

function setup() {
  const db = new Database(":memory:");
  applyMigrations(db);
  db.prepare("INSERT INTO channels (id,url,title,added_at) VALUES ('c1','x','Ch',0)").run();
  db.prepare(`
    INSERT INTO videos (id, channel_id, title, published_at, duration_seconds, discovered_at)
    VALUES ('v1', 'c1', 'My Vid', 0, 1234, 0)
  `).run();
  db.prepare(`
    INSERT INTO downloaded_videos (video_id, file_path, file_size_bytes, downloaded_at)
    VALUES ('v1', '/orig/v1.mp4', 100_000, 0)
  `).run();
  const app = Fastify();
  registerVideoFullRoute(app, db);
  return { app, db };
}

describe("GET /videos/:id/full", () => {
  it("returns full-video info for known id", async () => {
    const { app } = setup();
    const res = await app.inject({ method: "GET", url: "/videos/v1/full" });
    expect(res.statusCode).toBe(200);
    expect(res.json()).toMatchObject({
      durationSec: 1234,
      title: "My Vid",
      channelTitle: "Ch",
      fileUrl: expect.stringContaining("/media/originals/"),
    });
    await app.close();
  });

  it("returns 404 for unknown id", async () => {
    const { app } = setup();
    const res = await app.inject({ method: "GET", url: "/videos/UNKNOWN/full" });
    expect(res.statusCode).toBe(404);
    await app.close();
  });
});
```

- [ ] **Step 2: Run — expect FAIL**

```bash
pnpm vitest run src/api/video-full.test.ts
```

Expected: FAIL — module not found.

- [ ] **Step 3: Implement**

```ts
// backend/src/api/video-full.ts
import type { FastifyInstance } from "fastify";
import type Database from "better-sqlite3";
import { basename } from "node:path";

export function registerVideoFullRoute(app: FastifyInstance, db: Database.Database): void {
  app.get("/videos/:id/full", async (req, reply) => {
    const id = (req.params as any).id;
    const row = db.prepare(`
      SELECT v.title, v.duration_seconds AS durationSec,
             v.thumbnail_url AS thumbnailUrl,
             ch.title AS channelTitle,
             dl.file_path AS filePath
        FROM videos v
        JOIN channels ch ON ch.id = v.channel_id
        JOIN downloaded_videos dl ON dl.video_id = v.id
       WHERE v.id = ?
    `).get(id) as any;
    if (!row) return reply.status(404).send({ error: "video not found" });
    return {
      title: row.title,
      durationSec: row.durationSec,
      thumbnailUrl: row.thumbnailUrl,
      channelTitle: row.channelTitle,
      fileUrl: `/media/originals/${encodeURIComponent(basename(row.filePath))}`,
    };
  });
}
```

- [ ] **Step 4: Run — expect PASS**

```bash
pnpm vitest run src/api/video-full.test.ts
```

Expected: 2/2 PASS.

- [ ] **Step 5: Register in index.ts and commit**

```ts
import { registerVideoFullRoute } from "./api/video-full.js";
// ...
registerVideoFullRoute(app, db);
```

```bash
git add backend/src/api/video-full.ts backend/src/api/video-full.test.ts backend/src/index.ts
git commit -m "feat(api): GET /videos/:id/full for original-fullscreen player"
```

---

## Phase 9 — Android Changes

### Task 9.1: FeedItemDto + ClipperStatusDto extensions

**Files:**
- Modify: `android/app/src/main/java/com/hikari/app/data/api/dto/FeedItemDto.kt`
- Create: `android/app/src/main/java/com/hikari/app/data/api/dto/ClipperStatusDto.kt`

- [ ] **Step 1: Read existing FeedItemDto**

```bash
cat /Users/ayysir/Desktop/Hikari/android/app/src/main/java/com/hikari/app/data/api/dto/FeedItemDto.kt
```

- [ ] **Step 2: Add new fields**

Edit FeedItemDto.kt — add:

```kotlin
@JsonClass(generateAdapter = true)
data class FeedItemDto(
    val id: String,
    val kind: String = "legacy",          // "clip" | "legacy"
    val parentVideoId: String,
    val fileUrl: String,
    val durationSec: Double,
    val title: String,
    val channelTitle: String,
    val thumbnailUrl: String?,
    val startSec: Double? = null,         // null for legacy
    val endSec: Double? = null,
    // ... existing fields
)
```

- [ ] **Step 3: Create ClipperStatusDto.kt**

```kotlin
package com.hikari.app.data.api.dto

import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class ClipperStatusDto(
    val pending: Int,
    val processing: Int,
    val failed: Int,
    val no_highlights: Int,
    val done: Int,
    val isWindowActive: Boolean,
    val lastRanAt: Long?,
)

@JsonClass(generateAdapter = true)
data class RetryFailedResponse(val retriedCount: Int)
```

- [ ] **Step 4: Add API methods to HikariApi.kt**

```kotlin
@GET("clipper/status")
suspend fun getClipperStatus(): ClipperStatusDto

@POST("clipper/retry-failed")
suspend fun retryFailed(): RetryFailedResponse

@GET("videos/{id}/full")
suspend fun getVideoFull(@Path("id") id: String): VideoFullDto
```

Add `VideoFullDto`:

```kotlin
@JsonClass(generateAdapter = true)
data class VideoFullDto(
    val title: String,
    val durationSec: Double,
    val thumbnailUrl: String?,
    val channelTitle: String,
    val fileUrl: String,
)
```

- [ ] **Step 5: Build Android project**

```bash
cd /Users/ayysir/Desktop/Hikari/android
./gradlew :app:compileDebugKotlin
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 6: Commit**

```bash
git add android/app/src/main/java/com/hikari/app/data/api/
git commit -m "feat(android,api): clipper status + video-full DTOs + endpoints"
```

---

### Task 9.2: FullscreenOriginalPlayer screen

**Files:**
- Create: `android/app/src/main/java/com/hikari/app/ui/player/FullscreenOriginalPlayer.kt`
- Modify: `android/app/src/main/java/com/hikari/app/ui/HikariNavGraph.kt`

- [ ] **Step 1: Implement FullscreenOriginalPlayer**

```kotlin
package com.hikari.app.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.hikari.app.data.api.HikariApi
import com.hikari.app.data.api.dto.VideoFullDto
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class FullscreenOriginalViewModel @Inject constructor(
    private val api: HikariApi,
    private val baseUrl: String,
) : ViewModel() {
    private val _state = MutableStateFlow<VideoFullDto?>(null)
    val state = _state.asStateFlow()

    fun load(videoId: String) {
        viewModelScope.launch {
            _state.value = api.getVideoFull(videoId)
        }
    }
}

@OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun FullscreenOriginalPlayer(
    videoId: String,
    onBack: () -> Unit,
    vm: FullscreenOriginalViewModel = hiltViewModel(),
) {
    LaunchedEffect(videoId) { vm.load(videoId) }
    val state by vm.state.collectAsState()
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val player = remember { ExoPlayer.Builder(ctx).build() }
    DisposableEffect(player) { onDispose { player.release() } }

    LaunchedEffect(state) {
        state?.fileUrl?.let { url ->
            player.setMediaItem(MediaItem.fromUri(url))
            player.prepare()
            player.playWhenReady = true
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        AndroidView(
            factory = { c ->
                PlayerView(c).apply {
                    this.player = player
                    useController = true
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart).padding(8.dp),
        ) {
            Icon(
                androidx.compose.material.icons.Icons.Default.Close,
                contentDescription = "Schließen",
                tint = Color.White,
            )
        }
    }
}
```

- [ ] **Step 2: Add nav route**

In `HikariNavGraph.kt`, add:

```kotlin
composable(
    route = "original/{videoId}",
    arguments = listOf(navArgument("videoId") { type = NavType.StringType }),
) { entry ->
    val videoId = entry.arguments?.getString("videoId") ?: return@composable
    FullscreenOriginalPlayer(
        videoId = videoId,
        onBack = { navController.popBackStack() },
    )
}
```

- [ ] **Step 3: Wire up tap-handler in FeedScreen**

In `FeedScreen.kt`, when a clip-item is rendered, add a long-press or button action "Original ansehen":

```kotlin
TextButton(
    onClick = {
        if (item.kind == "clip") {
            navController.navigate("original/${item.parentVideoId}")
        }
    },
    enabled = item.kind == "clip",
) { Text("Original ansehen") }
```

- [ ] **Step 4: Build**

```bash
cd /Users/ayysir/Desktop/Hikari/android
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Commit**

```bash
git add android/app/src/main/java/com/hikari/app/ui/player/ \
        android/app/src/main/java/com/hikari/app/ui/HikariNavGraph.kt \
        android/app/src/main/java/com/hikari/app/ui/feed/
git commit -m "feat(android): FullscreenOriginalPlayer + Feed-action 'Original ansehen'"
```

---

### Task 9.3: Tuning System-Tab — Clipper-Counter + Retry-Button

**Files:**
- Modify: `android/app/src/main/java/com/hikari/app/ui/tuning/TuningSystemTab.kt`

- [ ] **Step 1: Read existing tab**

```bash
cat /Users/ayysir/Desktop/Hikari/android/app/src/main/java/com/hikari/app/ui/tuning/TuningSystemTab.kt
```

- [ ] **Step 2: Add Clipper-section composable**

Insert into the existing tab's column content:

```kotlin
@Composable
fun ClipperStatusBlock(
    api: HikariApi,
) {
    var status by remember { mutableStateOf<ClipperStatusDto?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { status = api.getClipperStatus() }

    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        Text(
            "CLIPPER",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
        )
        Spacer(Modifier.height(8.dp))
        status?.let { s ->
            Text("Pending: ${s.pending}",       style = MaterialTheme.typography.bodyMedium)
            Text("Processing: ${s.processing}", style = MaterialTheme.typography.bodyMedium)
            Text("Done: ${s.done}",             style = MaterialTheme.typography.bodyMedium)
            if (s.failed > 0) {
                Text(
                    "Failed: ${s.failed}",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            if (s.no_highlights > 0) {
                Text("No highlights: ${s.no_highlights}",
                     style = MaterialTheme.typography.bodyMedium)
            }
            Text(
                if (s.isWindowActive) "● Window: aktiv (22–08)"
                else                  "○ Window: pausiert",
                style = MaterialTheme.typography.bodySmall,
                color = if (s.isWindowActive) MaterialTheme.colorScheme.primary else
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
            )
            if (s.failed > 0) {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = {
                    scope.launch {
                        api.retryFailed()
                        status = api.getClipperStatus()
                    }
                }) { Text("Failed retry (${s.failed})") }
            }
        } ?: Text("Lädt...")
    }
}
```

- [ ] **Step 3: Build**

```bash
cd /Users/ayysir/Desktop/Hikari/android
./gradlew :app:assembleDebug
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit**

```bash
git add android/app/src/main/java/com/hikari/app/ui/tuning/TuningSystemTab.kt
git commit -m "feat(android,tuning): Clipper-status block in System-Tab"
```

---

## Phase 10 — End-to-End Manual Tests + Release

### Task 10.1: Setup local Qwen + start the worker

**Files:** none (manual)

- [ ] **Step 1: Start LM Studio with Qwen 3.6**

In LM Studio:
- Load model `qwen3.6-35b-a3b` (multimodal)
- Start server on `http://localhost:1234`
- Verify with: `curl http://localhost:1234/v1/models`

Expected: returns model list including qwen3.6.

- [ ] **Step 2: Run schema migrations on real DB**

```bash
cd /Users/ayysir/Desktop/Hikari/backend
pnpm tsx scripts/run-migrations.ts  # if exists; otherwise:
pnpm dev   # starts main backend, migrations apply on boot
```

Expected: log "applying migrations" + new tables visible:

```bash
sqlite3 ~/.hikari/hikari.db ".tables"  # adjust path to actual DB
```

Expected: `clips`, `clipper_queue` listed.

- [ ] **Step 3: Start the clipper worker**

In a second terminal:

```bash
cd /Users/ayysir/Desktop/Hikari/backend
pnpm clipper
```

Expected: log "clipper-worker started" + (if outside 22:00–08:00) sleeping. To force-test outside the window, override `CLIPPER_START_HOUR=0 CLIPPER_END_HOUR=24`.

---

### Task 10.2: E2E happy-path: Long-form talking-head video

**Files:** none (manual)

- [ ] **Step 1: Pick a 30-min talking-head channel + ingest**

Add a known channel with one ~30-minute talking-head video:

```bash
# In Hikari Android app or via API:
curl -X POST http://localhost:3939/channels \
  -H "content-type: application/json" \
  -d '{"url":"https://www.youtube.com/@LexFridman"}'
```

- [ ] **Step 2: Trigger discovery + wait for queue**

```bash
pnpm trigger-once
```

Expected: discovery + scoring + download for at least one video. Check:

```bash
sqlite3 ~/.hikari/hikari.db "SELECT id, clip_status FROM videos WHERE clip_status='pending'"
```

Expected: at least one row.

- [ ] **Step 3: With CLIPPER_START_HOUR=0 CLIPPER_END_HOUR=24, watch worker logs**

```bash
CLIPPER_START_HOUR=0 CLIPPER_END_HOUR=24 pnpm clipper
```

Expected logs:
- "dequeued v=<id>"
- "analyze took N seconds, got K specs"
- "render clip 0/K"
- ... (per clip)
- "video done"

- [ ] **Step 4: Verify clips on disk and in DB**

```bash
ls ~/.hikari/media/clips/*.mp4
sqlite3 ~/.hikari/hikari.db "SELECT parent_video_id, order_in_parent, start_seconds, end_seconds, reason FROM clips"
```

Expected: K mp4 files (~5-10MB each), K rows. Each clip should be 20-90s, sorted by order_in_parent.

- [ ] **Step 5: Open Hikari app on phone, verify feed**

Expected: clips appear in feed, dynamic order (no clumping), tap "Original ansehen" opens FullscreenPlayer with the full video. Smart-crop visually checks out.

- [ ] **Step 6: Document any qualitative issues in TROUBLESHOOTING.md**

If clips look bad (focus_region mis-targeted, clips too short, etc.), note them — these are prompt-tuning concerns, not code bugs.

---

### Task 10.3: E2E: Schedule-boundary stress test

**Files:** none (manual)

- [ ] **Step 1: Set CLIPPER_END_HOUR to current_hour + 5min from now**

E.g. if it's 14:30, set `CLIPPER_START_HOUR=14 CLIPPER_END_HOUR=15` and the boundary will hit at 15:00.

- [ ] **Step 2: Enqueue a long video that takes >15 min to process**

Find a 60-min+ video in queue.

- [ ] **Step 3: Wait — observe behavior at boundary**

Expected:
- Worker continues current job past the boundary (does NOT cut off mid-render)
- After current job completes, worker idles
- DB has consistent state: no half-inserted clips, no orphan files in `media/clips/` without DB rows

- [ ] **Step 4: Verify clean state**

```bash
# Find media/clips files not referenced in clips table
ls ~/.hikari/media/clips/ | while read f; do
  hit=$(sqlite3 ~/.hikari/hikari.db "SELECT COUNT(*) FROM clips WHERE file_path LIKE '%$f'")
  if [ "$hit" = "0" ]; then echo "ORPHAN: $f"; fi
done
```

Expected: no ORPHAN output.

---

### Task 10.4: E2E: SIGTERM mid-render + Failed-retry

**Files:** none (manual)

- [ ] **Step 1: Start worker, queue a long video, wait until "rendering" log**

- [ ] **Step 2: Send SIGTERM**

```bash
# Find PID
ps aux | grep clipper-worker
kill -TERM <pid>
```

Expected: log "SIGTERM received, draining" → finishes current step → "clipper-worker stopped cleanly". Process exits within ~2 minutes (depending on render).

- [ ] **Step 3: Verify state**

```bash
sqlite3 ~/.hikari/hikari.db "SELECT id, clip_status FROM videos WHERE id='<id>'"
sqlite3 ~/.hikari/hikari.db "SELECT * FROM clipper_queue WHERE video_id='<id>'"
```

Expected: clip_status is `done` (if all clips rendered before SIGTERM) OR `pending` with locked_at != NULL (mid-job; will be unlocked on next worker start).

- [ ] **Step 4: Restart worker, verify recovery**

```bash
pnpm clipper
```

Expected log: "unlocked stale locks: N" (if applicable) → resumes processing.

---

### Task 10.5: Release tag

- [ ] **Step 1: Bump version + tag**

```bash
cd /Users/ayysir/Desktop/Hikari/backend
# Edit package.json: "version": "0.30.0"
git add backend/package.json
git commit -m "release: backend v0.30.0 — Auto-Clipper"
git tag -a backend-v0.30.0 -m "Auto-Clipper feature complete"
```

```bash
cd /Users/ayysir/Desktop/Hikari/android
# Edit app/build.gradle.kts: versionCode + versionName 0.30.0
git add android/app/build.gradle.kts
git commit -m "release: android v0.30.0 — Clip-Feed + FullscreenOriginalPlayer"
git tag -a android-v0.30.0 -m "Auto-Clipper feature complete"
```

- [ ] **Step 2: Push (only if user explicitly asks)**

```bash
# git push origin main --tags    # ← do NOT auto-run; ask user first
```

---

## Self-Review Checklist

Before claiming this plan complete:

1. **Spec coverage:**
   - ✅ Decision 1 (YouTube-Shorts-Pattern): Phase 8.2 (video-full API) + Phase 9.2 (FullscreenOriginalPlayer) + Phase 9.2 Step 3 (tap handler)
   - ✅ Decision 2 (alle neuen, alte legacy): Phase 1 (is_pre_clipper) + Phase 6 (orchestrator change) + Phase 5 (passthrough ≤90s)
   - ✅ Decision 3 (clip 20-60s, soft 90s, ~1/5min): Phase 2.2 (prompt) + Phase 3 (clamp) + Phase 5.1 (test)
   - ✅ Decision 4 (cooldown-3 + topic-mix): Phase 7.2
   - ✅ Decision 5 (async strict serial, originals not in feed): Phase 5 (single-slot worker) + Phase 6 (no feed_items insert)
   - ✅ Decision 6 (22:00–08:00 window): Phase 2.3 (isWindowActive) + Phase 5.2 (worker loop)
   - ✅ Decision 7 (smart-crop always): Phase 2.1 (crop-math) + Phase 4 (Composition)
   - ✅ Decision 8 (FilterConfig reuse): Phase 2.2 (prompt-builder)
   - ✅ Decision 9 (single-slot, M4 Max): Phase 2.3 (queue locking) + Phase 5.2 (loop)
   - ✅ Decision 10 (no_highlights status): Phase 5.1 (test) + Phase 5.1 (impl)
   - ✅ Decision 11 (1× retry then failed + retry button): Phase 3 (analyzer retry) + Phase 5.1 (render fail) + Phase 8.1 (retry endpoint)
   - ✅ Decision 12 (migration cutoff): Phase 1.3 (backfill is_pre_clipper)

2. **Placeholder scan:** No "TBD", "TODO", "implement later", or "similar to Task N" found. All steps have concrete code.

3. **Type consistency:**
   - `ClipSpec` defined in `qwen-analyzer.ts`, imported by `worker.ts` and `remotion-renderer.ts` ✅
   - `RawFeedRow` defined in `feed.ts`, used by `applyCooldown` ✅
   - `analyze` and `render` injected into `WorkerDeps` match the exported function signatures ✅

4. **Cross-file imports use `.js` extension** as per project convention ✅

5. **No spec gaps:** All 7 numbered sections of the spec map to phases.
