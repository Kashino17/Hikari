# Manga-Tab — Design Spec

**Date:** 2026-04-25
**Author:** Kadir + Claude (Brainstorming-Session)
**Status:** Draft, awaiting user approval before plan
**Pilot Source:** https://onepiece.tube/manga/kapitel-mangaliste
**Pilot Series:** One Piece

## Goal

Add a Manga browsing + reading experience to Hikari. Start with One Piece via `onepiece.tube` as the pilot source, but build the system so additional sources/series can be added later via a plugin-style adapter.

The reading experience must be **ad-free, distraction-free, and aligned with the Hikari "anti-doom-scroll" vision** — kuratiert, ruhig, dunkel/amber, keine externe Werbe-Pipeline.

## Decisions (Brainstorming Outcome)

| # | Decision | Choice |
|---|---|---|
| 1 | Platform target for v1 | Web demo (`design-preview/`) only; Android follows later |
| 2 | IA slot | Top-level tab `/manga` in BottomNav |
| 3 | Image hosting strategy | Full mirror — backend pre-syncs all chapters and pages to local disk |
| 4 | Default reader mode | Horizontal RTL paged (canonical Japanese flow) |
| 5 | Persistence scope | Reading progress + Library/Bookmarks |
| 6 | Sync trigger | Manual button in Tuning → System (no cron in v1) |
| 7 | Manga home layout | Netflix-style: Hero + horizontal Rows |
| 8 | Detail page layout | Arcs as collapsible accordions |

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│  design-preview (Next.js)                                       │
│  ├── /manga              Hero + Rows                            │
│  ├── /manga/[seriesId]   Arc-Akkordeon + Chapter list           │
│  └── /manga/[seriesId]/[chapterId]   RTL Reader                 │
└─────────────────────────────────┬───────────────────────────────┘
                                  │ HTTP (HIKARI_API_BASE_URL)
┌─────────────────────────────────▼───────────────────────────────┐
│  backend (Node + Fastify + better-sqlite3)                      │
│  ├── api/manga.ts          REST endpoints                       │
│  ├── manga/sync.ts         Sync engine (idempotent)             │
│  └── manga/sources/                                             │
│       ├── types.ts         MangaSourceAdapter interface         │
│       └── onepiece-tube.ts First adapter (cheerio + undici)     │
└──────────────┬──────────────────────────────────┬───────────────┘
               │                                  │
        ┌──────▼─────────┐               ┌────────▼──────────────┐
        │  hikari.db     │               │  MANGA_DATA_DIR/      │
        │  manga_*       │               │   <source>/<series>/  │
        │  tables        │               │    <chapter>/<page>   │
        └────────────────┘               └───────────────────────┘
```

**Key points:**
- DB stores metadata only (rows). Page bytes live on disk under `MANGA_DATA_DIR`.
- Bytes are streamed through `/api/manga/page/:pageId` — never serve raw filesystem paths to clients (Auth + path-traversal control).
- Adapter pattern means the rest of the stack is source-agnostic; adding a second source means adding a new file under `sources/`.

**Data flow on sync:** UI → `POST /api/manga/sync` → Sync Engine → Adapter (HTTP fetch + parse) → SQLite upsert + image download → UI polls `/api/manga/sync/jobs/:id`.

**Data flow on read:** UI → `GET /api/manga/chapters/:id/pages` → JSON list of page IDs → UI loads each page from `/api/manga/page/:pageId` → Backend streams file → `<img>` renders.

## Data Model

All tables prefixed `manga_` to avoid collision with existing `series` (video series) table.

```sql
CREATE TABLE IF NOT EXISTS manga_series (
  id TEXT PRIMARY KEY,                  -- "onepiecetube:one-piece"
  source TEXT NOT NULL,                 -- "onepiecetube"
  source_url TEXT NOT NULL,
  title TEXT NOT NULL,
  author TEXT,
  description TEXT,
  cover_path TEXT,                      -- relative to MANGA_DATA_DIR
  status TEXT,                          -- "ongoing" | "completed"
  total_chapters INTEGER DEFAULT 0,
  added_at INTEGER NOT NULL,
  last_synced_at INTEGER
);

CREATE TABLE IF NOT EXISTS manga_arcs (
  id TEXT PRIMARY KEY,                  -- "onepiecetube:one-piece:east-blue"
  series_id TEXT NOT NULL REFERENCES manga_series(id) ON DELETE CASCADE,
  title TEXT NOT NULL,
  arc_order INTEGER NOT NULL,
  chapter_start INTEGER,
  chapter_end INTEGER
);

CREATE TABLE IF NOT EXISTS manga_chapters (
  id TEXT PRIMARY KEY,                  -- "onepiecetube:one-piece:1095"
  series_id TEXT NOT NULL REFERENCES manga_series(id) ON DELETE CASCADE,
  arc_id TEXT REFERENCES manga_arcs(id),
  number REAL NOT NULL,                 -- 1095.5 allowed for specials
  title TEXT,
  source_url TEXT NOT NULL,
  page_count INTEGER DEFAULT 0,
  published_at INTEGER,
  added_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_manga_chapters_series_num
  ON manga_chapters(series_id, number);

CREATE TABLE IF NOT EXISTS manga_pages (
  id TEXT PRIMARY KEY,                  -- "onepiecetube:one-piece:1095:01"
  chapter_id TEXT NOT NULL REFERENCES manga_chapters(id) ON DELETE CASCADE,
  page_number INTEGER NOT NULL,
  source_url TEXT NOT NULL,
  local_path TEXT,                      -- NULL = not downloaded yet
  width INTEGER,
  height INTEGER,
  bytes INTEGER
);
CREATE INDEX IF NOT EXISTS idx_manga_pages_chapter
  ON manga_pages(chapter_id, page_number);

CREATE TABLE IF NOT EXISTS manga_library (
  series_id TEXT PRIMARY KEY REFERENCES manga_series(id) ON DELETE CASCADE,
  added_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS manga_progress (
  series_id TEXT PRIMARY KEY REFERENCES manga_series(id) ON DELETE CASCADE,
  chapter_id TEXT NOT NULL REFERENCES manga_chapters(id),
  page_number INTEGER NOT NULL DEFAULT 1,
  updated_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS manga_chapter_read (
  chapter_id TEXT PRIMARY KEY REFERENCES manga_chapters(id) ON DELETE CASCADE,
  read_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS manga_sync_jobs (
  id TEXT PRIMARY KEY,
  source TEXT NOT NULL,
  series_id TEXT,                       -- NULL = full source sync
  status TEXT NOT NULL,                 -- queued | running | done | failed
  total_chapters INTEGER DEFAULT 0,
  done_chapters INTEGER DEFAULT 0,
  total_pages INTEGER DEFAULT 0,
  done_pages INTEGER DEFAULT 0,
  error_message TEXT,
  started_at INTEGER NOT NULL,
  finished_at INTEGER
);
```

**Migrations:** add to `backend/src/db/schema.sql`. The existing `applyMigrations` in `backend/src/db/migrations.ts` already runs `db.exec(schema)` and is idempotent (`CREATE TABLE IF NOT EXISTS`), no extra wiring needed for fresh installs.

**Storage layout on disk** — under `${HIKARI_DATA_DIR}/manga/` (using the existing `HIKARI_DATA_DIR` env var, default `~/.hikari/manga/`). The `Config` interface in `backend/src/config.ts` gets a new `mangaDir` field. Referenced as `MANGA_DATA_DIR` in the rest of this doc:
```
manga/
  onepiecetube/
    one-piece/
      cover.jpg
      0001/
        01.jpg
        02.jpg
        ...
      1095/
        01.jpg
        ...
```

`local_path` values stored relative to `MANGA_DATA_DIR` (e.g. `onepiecetube/one-piece/1095/01.jpg`). Never stored as absolute paths.

## Backend

### Source Adapter Interface

`backend/src/manga/sources/types.ts`:
```ts
export interface RawSeries {
  sourceSlug: string;          // "one-piece"
  title: string;
  sourceUrl: string;
  coverUrl?: string;
  author?: string;
  status?: "ongoing" | "completed";
}
export interface RawArc {
  title: string;
  arcOrder: number;
  chapterNumbers: number[];    // chapter numbers belonging to this arc
}
export interface RawChapter {
  number: number;
  title?: string;
  sourceUrl: string;
  publishedAt?: number;
}
export interface RawPage {
  pageNumber: number;
  sourceUrl: string;
}
export interface RawSeriesDetail {
  arcs: RawArc[];
  chapters: RawChapter[];
  description?: string;
}
export interface MangaSourceAdapter {
  readonly id: string;          // "onepiecetube"
  readonly name: string;        // "One Piece Tube"
  readonly baseUrl: string;
  listSeries(): Promise<RawSeries[]>;
  fetchSeriesDetail(seriesUrl: string): Promise<RawSeriesDetail>;
  fetchChapterPages(chapterUrl: string): Promise<RawPage[]>;
}
```

### `OnePieceTubeAdapter`

`backend/src/manga/sources/onepiece-tube.ts`. Uses `undici` (already supplied via Node 18+ `fetch` polyfill or as dep) and `cheerio` for HTML parsing. Throws `SourceLayoutError` (with offending URL + selector name) when expected DOM nodes are missing — this is the trigger for surfacing layout-change errors to the UI.

### Source Registry

A simple module-level array in `backend/src/manga/sources/index.ts` registers adapters at startup:

```ts
import { onePieceTubeAdapter } from "./onepiece-tube";
export const adapters: MangaSourceAdapter[] = [onePieceTubeAdapter];
export function getAdapter(id: string): MangaSourceAdapter | undefined { ... }
```

For v1 this is hardcoded. No "add source" UI. Adding a new source means committing a new adapter file and adding it to this array.

### Sync Engine

`backend/src/manga/sync.ts`:

- `runFullSync(sourceId)` — entry point for `POST /api/manga/sync`. Creates a `manga_sync_jobs` row, lists series via adapter, calls `runSeriesSync` per series.
- `runSeriesSync(seriesId)` — fetches detail (arcs + chapter list), upserts SQLite rows, then iterates chapters calling `runChapterSync`.
- `runChapterSync(chapterId)` — fetches page URLs, inserts page rows with `local_path = NULL`, downloads images with concurrency limit (4 parallel via simple semaphore), updates `local_path` after each successful write, updates `manga_sync_jobs.done_pages` counter.

All steps idempotent: `INSERT OR IGNORE` on Series/Arcs/Chapters/Pages by `id`; `UPDATE` only when source data changed (compare title/published_at).

Job runs async via `setImmediate` — no worker_threads in v1. The Express request returns immediately with `jobId`, the sync continues in the same Node process.

**Concurrency guard:** `POST /api/manga/sync` rejects with 409 if any job in `(queued, running)` state exists. Only one sync at a time.

### REST API

`backend/src/api/manga.ts`:

```
GET  /api/manga/series                        Liste aller Series (Cards)
GET  /api/manga/series/:id                    Detail mit Arcs + Chapters
GET  /api/manga/chapters/:id/pages            Page-Liste mit IDs (+ ready-Flag)
GET  /api/manga/page/:pageId                  Bild-Stream (Cache-Control 1y)
POST /api/manga/library/:seriesId             in Library aufnehmen
DEL  /api/manga/library/:seriesId             aus Library entfernen
PUT  /api/manga/progress/:seriesId            { chapterId, pageNumber }
PUT  /api/manga/chapters/:id/read             markiere als gelesen
GET  /api/manga/continue                      Library ∩ progress, sortiert
POST /api/manga/sync                          { sourceId? } — ohne Body: syncs ALLE registrierten Adapter; mit { sourceId }: nur dieser
GET  /api/manga/sync/jobs                     laufende + letzte 10
GET  /api/manga/sync/jobs/:id                 Detail (Polling)
```

`/api/manga/page/:pageId` reads `local_path`, **resolves it against `MANGA_DATA_DIR` and rejects any resolved path that escapes the data dir** (path-traversal defense), then `fs.createReadStream`s with proper `Content-Type` and `Cache-Control: public, max-age=31536000, immutable`. If `local_path IS NULL` → 404 with `{ status: "pending", chapterSyncStarted: true|false }`.

**Mount:** in `backend/src/index.ts` register the router at `/api/manga`, alongside the existing video API routers.

## Frontend

**Routes** (in `design-preview/app/manga/`):
- `app/manga/page.tsx` — Server Component, fetches `/api/manga/series` + `/api/manga/continue`. Renders `MangaHero` + multiple `MangaRow`s.
- `app/manga/[seriesId]/page.tsx` — Server Component for data, wraps a Client `<ArcAccordion>` for collapse state.
- `app/manga/[seriesId]/[chapterId]/page.tsx` — Client Component (`MangaReader`).

**Components** (in `design-preview/components/manga/`):
- `MangaHero.tsx` — backdrop with cover-derived gradient, big title, "Weiterlesen"-CTA wired to `/manga/[id]/[chapterId]?page=N`. With multiple series later, becomes a featured rotator. Empty state: "Erste Manga lesen".
- `MangaRow.tsx` — generic horizontal-scroll row, props `{ title, items[] }`.
- `MangaCard.tsx` — cover (aspect 2/3), title, optional progress bar at bottom (uses `manga_progress`).
- `ArcAccordion.tsx` — Client. One section per arc, default-expanded for the arc containing the user's `manga_progress.chapter_id`, all others collapsed.
- `ChapterRow.tsx` — chapter number, title, "read"-amber-dot when `manga_chapter_read` row exists.
- `MangaReader.tsx` — RTL paged reader. Tap-zone: right third = back, left third = forward (RTL!), middle = toggle chrome. Top chrome shows chapter + page indicator; bottom shows hairline scrubber. Preloads next 2 pages with `<link rel="prefetch">`. Pointer-event listener for swipe (no library).

**BottomNav update:** add `{ href: '/manga', label: 'Manga', Icon: BookOpen }`. Bar grows from 4 to 5 items; existing `flex-1` handles distribution. If the labels become too cramped on narrow screens, hide labels for inactive items in a follow-up.

**Reader progress:**
- `PUT /api/manga/progress/:seriesId` debounced at 1.5s while reading.
- On unmount + on `pagehide`: synchronous `navigator.sendBeacon` flush.
- On reaching last page: `PUT /api/manga/chapters/:id/read` and show a "Nächstes Kapitel"-card on the left edge (the RTL "forward" direction).

**Sync UI:**
- A `MangaSyncBanner.tsx` mounted in the `/manga` layout polls `/api/manga/sync/jobs` every 2s while a job is running and shows "Sync läuft (12/95 Kapitel)" with a progress bar. Disappears when no active job.
- The Tuning → System tab gets a "Manga sync now" button that POSTs to `/api/manga/sync`.

## Implementation Constraints

1. **Modified Next.js**: `design-preview/AGENTS.md` warns this is a non-standard Next.js with breaking changes vs. public docs. **Before writing any frontend file, the implementer must consult `design-preview/node_modules/next/dist/docs/`** for the relevant API (Server Components, dynamic routes, image handling). No assumed Next.js patterns.
2. **Series/Library work in flight**: `design-preview/app/series/[id]/` and `app/library/page.tsx` are being built in parallel by another agent (Gemini). The Manga work must not modify those files. If shared abstractions emerge later (e.g. a generic `<MediaCard>`), they get extracted in a separate refactor PR.
3. **Hikari design system**: dark `#0a0a0a` background, surface `#111111`, hairline borders, single accent `amber-400`, Geist Sans, mono for counters, `text-[10px] uppercase tracking-widest text-faint` for labels (per `project_hikari_design_v1`).
4. **Source-Layout fragility**: scraping is the highest external risk. The adapter must surface `SourceLayoutError` clearly. Tests run against committed HTML fixtures, not against the live site.

## Error Handling

### Sync errors

| Error | Detection | Strategy |
|---|---|---|
| `OnePieceTube` HTTP 5xx / Timeout | Adapter | Retry 3× exponential backoff (1s/3s/10s). After: chapter recorded in `manga_sync_jobs.error_message`, sync continues to next chapter. |
| Selector mismatch (layout change) | Parser | `SourceLayoutError` with URL + missing selector. Job → `failed`, UI shows red notice + link. |
| Image download failed | Sync | Page row stays with `local_path = NULL`. Next sync run retries. |
| Disk full | Sync | Job → `failed`, `error_message = "ENOSPC"`, abort. |
| Concurrent sync | API | `POST /api/manga/sync` checks for `queued|running` job → 409 Conflict. |

### Reader errors

| Error | Strategy |
|---|---|
| Page not yet downloaded (`local_path NULL`) | Reader shows skeleton + triggers `POST /api/manga/chapters/:id/sync` (chapter-only sync). UI label: "Diese Seite wird gerade geladen…" |
| 404 on `/api/manga/page/:id` (orphan row) | Reader skips with toast, page counter shows "X/Y (1 missing)". |
| Last page reached | Auto-mark `chapter_read`, render "Nächstes Kapitel" card on the left edge (RTL forward). |
| Network down on progress save | Optimistic local update; failed writes queue in `localStorage`, retried next reader open. |

### Data integrity

- `ON DELETE CASCADE` on series ensures arcs/chapters/pages cascade.
- `local_path` always relative to `MANGA_DATA_DIR` — moving the data dir between machines never breaks anything.
- An optional `manga sync gc` reconciler (out of scope for v1) can match disk files against `manga_pages.local_path`.

### Empty states

- Library empty + no progress: `/manga` shows only "Alle Mangas" row, hero shows top series with "Erste Manga lesen"-CTA.
- Sync never run: `/manga` empty-state with "Jetzt synchronisieren" button.
- Sync running: sticky banner with progress, polled every 2s.

## Testing

### Backend (`backend/tests/manga/`)

| Test | Verifies |
|---|---|
| `onepiece-tube-adapter.test.ts` | Parser against committed HTML fixtures (`backend/tests/fixtures/onepiece-tube/`). Stable even if site goes offline. |
| `sync.test.ts` | In-memory SQLite + fake adapter. Idempotency, incremental updates, image download with mocked `fetch`. |
| `api-manga.test.ts` | All endpoints: library add/remove, progress set, continue-reading sort, page-stream path-traversal protection. |
| `path-safety.test.ts` | `/api/manga/page/:id` rejects relative paths that resolve outside `MANGA_DATA_DIR`. |

**Fixture refresh:** an offline `npm run scrape:fixtures` script (added in plan) pulls live HTML once and commits it. Layout changes are noticed via diff during fixture updates.

### Frontend

Manual E2E in v1 (no component tests yet — `design-preview/` has none):

1. `npm run dev` (backend) + `npm run dev` (design-preview)
2. Trigger sync from Tuning → progress banner appears → banner disappears
3. `/manga` shows hero + rows
4. Click series → accordion opens current arc
5. Click chapter → reader opens; tap-right goes back, tap-left forward; chrome toggles on tap-middle
6. Reload app → "Weiterlesen" continues at the last position

### Out of v1

- Visual regression / Storybook
- Cross-browser tests (Chrome only as primary)
- Multi-source adapter tests (only One Piece Tube exists)

## Open Questions Deferred to the Plan

1. Exact CSS selector list for `OnePieceTubeAdapter` (will be derived during plan from a manual HTML inspection step).
2. Whether `cheerio` is already in `backend/package.json` or needs adding.
3. Image format from onepiece.tube — likely JPEG, confirmed during plan.
4. Whether the `MangaCard` should share an abstraction with the existing video card in `app/library/page.tsx` (no, in v1 — keep separate, refactor later).

## Glossary

- **Source / Adapter**: an external website that hosts manga; we have one adapter (`OnePieceTubeAdapter`), more can be added.
- **Series**: a manga title (e.g. "One Piece"). Different from existing `series` (video series, e.g. an anime).
- **Arc**: a story arc inside a series (e.g. "East Blue", "Alabasta").
- **Chapter**: a single chapter, usually 15–30 pages.
- **Page**: a single image inside a chapter.
- **Library**: user's "I'm reading this" collection.
- **Progress**: per-series last chapter + page; only the latest is kept.
