# Auto-Clipper für Hikari-Feed — Design Spec

**Date:** 2026-05-02
**Author:** Kadir + Rem (Brainstorming-Session)
**Status:** Draft, awaiting user approval before plan
**Builds on:** `2026-04-24-hikari-mvp-design.md` (Pipeline, FilterConfig, Tuning-IA)

## Goal

Lange Videos werden automatisch in mehrere kurze Highlight-Clips zerlegt, smart auf 9:16 reframed, und dynamisch in den Feed eingewoben. Anstelle von 15-minütigen Talking-Heads zeigt der Feed kuratierte 30–90-Sekunden-Highlights, mit Link auf das Original (YouTube-Shorts-Pattern). Lokale AI (Qwen 3.6-35B-A3B via LM Studio / Ollama) analysiert Videos multimodal, Remotion rendert die Clips. Worker läuft nachts (22:00–08:00) auf Kadirs M4 Max, single-slot, um den Tag-Workflow nicht zu stören.

## Decisions (Brainstorming Outcome)

| # | Decision | Choice |
|---|---|---|
| 1 | Co-Existenz oder Ersatz? | YouTube-Shorts-Pattern: Clips im Feed, Original nur via "Original ansehen"-Action im Fullscreen-Player erreichbar |
| 2 | Welche Videos werden geclippt? | **Alle neu approved Videos** durchlaufen den Clipper-Pfad. Originale ≤ 90s werden als Passthrough-Single-Clip ohne AI/Render in den Feed gestellt (siehe Edge Cases). Alte Videos (pre-clipper) bleiben unangetastet (kein Backfill zum Launch) |
| 3 | Clip-Länge / Anzahl | AI entscheidet qualitätsgetrieben. **Min 20s, Soft-Cap 60s, Hard-Cap 90s** (Toleranz für unteilbare Highlight-Momente). Soft-Anker-Skala: ~1 Clip pro 5 Min Original (5 Min→1, 15 Min→3, 30 Min→6) |
| 4 | Feed-Dynamik | **Soft-Cooldown 3** (Channel max 2× in Folge, dann Pause für 3 Items) **+ Topic-Mix-Tie-Breaker** (`category` aus Scorer-Output) |
| 5 | Pipeline-Timing | **Async, strict serial**: ein Video zur Zeit, nie parallel. Originale erscheinen **niemals** im Feed |
| 6 | Throttling / Schedule | **Fixes Zeitfenster 22:00–08:00**, Worker idled außerhalb |
| 7 | Reframing-Strategie | **Smart-Crop always** via Qwen `focus_region`. Kein Blur-BG-Fallback |
| 8 | Prompt-Integration | **Reine Wiederverwendung von `FilterConfig`**. `buildClipperPrompt(filter)` mirrors `buildPrompt(filter)` mit clipper-spezifischen Instruktionen, gleichen User-Daten |
| 9 | Hardware | M4 Max 128GB, **single-slot** (kein paralleles Qwen + Render — würde während Tagesarbeit das System abwürgen) |
| 10 | "Keine guten Clips" | `clip_status='no_highlights'`, Video erscheint nicht im Feed, gelogged |
| 11 | Technische Fehler | 1× Retry, dann `clip_status='failed'`, sichtbar im Tuning-Tab "System" mit manuellem Retry-Button |
| 12 | "Neu"-Cutoff | Migration-Zeitpunkt: alle existierenden `feed_items` werden mit `is_pre_clipper=1` markiert und fließen unverändert weiter |

## User-Set Constraints (nicht verhandelbar)

- **Lokal-only** — Qwen 3.6-35B-A3B läuft auf Kadirs M4 Max via LM Studio oder Ollama. Keine Cloud, keine remote APIs.
- **Single-slot** — niemals zwei Videos gleichzeitig analysieren oder rendern. Tagsarbeit hat Vorrang.
- **Schedule-fenster fix** — 22:00–08:00, kein Manual-Toggle, kein System-Idle-Detection (keep it simple).
- **3-Routen-IA bleibt heilig** — `/feed`, `/channels`, `/tuning` (mit 3 Tabs). Kein neuer Tab "Clipper". Settings landet im Tuning-Tab "System".
- **Single Source of Truth = `FilterConfig`** — keine separate ClipperConfig, keine Duplikation der User-Kriterien.

## Architecture Overview

```
┌──────────────────────────────────────────────────────────────────┐
│  Backend-Hauptprozess (Node + Fastify + better-sqlite3)          │
│                                                                  │
│  TAGSÜBER:                                                       │
│  yt-dlp discover → ingest → scorer → decide=approve              │
│       ↓                                                          │
│  download original.mp4 (downloaded_videos table)                 │
│       ↓                                                          │
│  IF Video ist legacy (pre-clipper) → feed_items insert (heute)   │
│  ELSE                                                            │
│       videos.clip_status = 'pending'                             │
│       clipper_queue.insert(video_id, queued_at=now)              │
│       — KEIN feed_items insert (Original landet nie im Feed) —   │
└──────────────────────────────────────────────────────────────────┘
                              │
                              │ teilen sich SQLite (WAL-mode)
                              ▼
┌──────────────────────────────────────────────────────────────────┐
│  clipper-worker  (separater Node-Prozess: pnpm clipper)          │
│                                                                  │
│  loop:                                                           │
│    if NOT isWindowActive(now): sleep 60s; continue               │
│    job = dequeue (priority: duration ASC, queued_at ASC)         │
│    if !job: sleep 60s; continue                                  │
│                                                                  │
│    1. UPDATE clip_status='analyzing', queue.locked_at=now        │
│    2. spec = qwen.analyzeVideo(file_path,                        │
│              buildClipperPrompt(activeFilter))                   │
│    3. if spec.length === 0:                                      │
│         clip_status='no_highlights'; DELETE queue row; continue  │
│    4. UPDATE clip_status='rendering'                             │
│    5. for s in spec:                                             │
│         clip_file = remotion.renderClip(file_path, s)            │
│         INSERT INTO clips (...)                                  │
│    6. UPDATE clip_status='done'; DELETE queue row                │
│                                                                  │
│  on crash: stale-locks (locked_at>30min) werden bei Restart      │
│            unlocked + attempts++                                 │
└──────────────────────────────────────────────────────────────────┘
                              │
                              ▼
                       clips table  ──────►  /feed API
                                             (UNION über clips +
                                              legacy feed_items,
                                              + Cooldown/Topic-Mix
                                              im App-Layer)
                                             │
                                             ▼
                                        Android Client
                                        (Clip-Item im Feed,
                                         "Original ansehen" →
                                         FullscreenPlayer)
```

## New Backend Modules

```
backend/src/clipper/
├── qwen-analyzer.ts        Wrapper für LM-Studio / Ollama HTTP-API,
│                           parsed Qwen-JSON-Response in ClipSpec[]
├── prompt-builder.ts       buildClipperPrompt(filter: FilterConfig): string
├── remotion-renderer.ts    Wrapper für Remotion CLI, baut 9:16-Composition
│                           mit Smart-Crop aus focus_region
├── queue.ts                SQL-Wrapper: enqueue/dequeue/lock/unlock,
│                           priority by duration ASC, isWindowActive
├── worker.ts               Orchestrator-Loop (separater Prozess)
├── prompt-builder.test.ts
├── qwen-analyzer.test.ts
├── remotion-renderer.test.ts
├── queue.test.ts
└── worker.integration.test.ts
```

## Component Contracts

```ts
// clipper/qwen-analyzer.ts
interface ClipSpec {
  startSec: number;
  endSec: number;
  focus: { x: number; y: number; w: number; h: number };  // 0–1 normalized
  reason: string;
}

async function analyzeVideo(
  filePath: string,
  prompt: string,
  config: { provider: 'lmstudio' | 'ollama'; model: string; baseUrl: string }
): Promise<ClipSpec[]>;

// clipper/prompt-builder.ts
function buildClipperPrompt(filter: FilterConfig, videoMeta: VideoMeta): string;

// clipper/remotion-renderer.ts
async function renderClip(
  inputPath: string,
  spec: ClipSpec,
  outputPath: string
): Promise<{ filePath: string; sizeBytes: number }>;

// clipper/queue.ts
function enqueue(db: Database, videoId: string): void;
function dequeue(db: Database): QueueJob | null;   // atomic lock
function complete(db: Database, videoId: string): void;
function fail(db: Database, videoId: string, error: string): void;
function unlockStale(db: Database, olderThanMs: number): number;
function isWindowActive(now: Date): boolean;       // 22:00–08:00 (Europe/Berlin)
```

## Data Model — Schema Changes

**Eine neue Tabelle, eine neue Job-Queue-Tabelle, zwei kleine Spalten-Adds. Keine Breaking Changes.**

```sql
-- NEW: clips = sowohl Clip-Definition als auch Feed-Item-State in einem
CREATE TABLE clips (
  id              TEXT PRIMARY KEY,           -- UUID, fließt als Feed-Item-ID
  parent_video_id TEXT NOT NULL REFERENCES videos(id),
  order_in_parent INTEGER NOT NULL,           -- 0,1,2,... innerhalb des Originals
  start_seconds   REAL NOT NULL,
  end_seconds     REAL NOT NULL,
  file_path       TEXT NOT NULL,              -- gerenderter mp4
  file_size_bytes INTEGER NOT NULL,
  focus_x         REAL NOT NULL,              -- 0.0–1.0 normiert (Qwen-Output)
  focus_y         REAL NOT NULL,
  focus_w         REAL NOT NULL,
  focus_h         REAL NOT NULL,
  reason          TEXT,                       -- Qwens Begründung
  created_at      INTEGER NOT NULL,
  -- Feed-Item-State (parallel zu feed_items, gleiche Semantik):
  added_to_feed_at INTEGER NOT NULL,
  seen_at          INTEGER,
  saved            INTEGER DEFAULT 0,
  playback_failed  INTEGER DEFAULT 0,
  progress_seconds REAL DEFAULT 0
);
CREATE INDEX idx_clips_added  ON clips(added_to_feed_at DESC);
CREATE INDEX idx_clips_parent ON clips(parent_video_id);

-- NEW: Single-slot-Job-Queue
CREATE TABLE clipper_queue (
  video_id     TEXT PRIMARY KEY REFERENCES videos(id),
  queued_at    INTEGER NOT NULL,
  attempts     INTEGER DEFAULT 0,
  last_error   TEXT,
  locked_at    INTEGER,                       -- NULL = frei
  locked_step  TEXT                           -- 'analyzing' | 'rendering'
);
CREATE INDEX idx_clipper_queue_pending ON clipper_queue(queued_at)
  WHERE locked_at IS NULL;

-- ADD: clip_status auf videos
-- Werte: NULL=legacy/pre-clipper · 'pending' · 'analyzing' · 'rendering'
--        · 'done' · 'no_highlights' · 'failed'
ALTER TABLE videos ADD COLUMN clip_status TEXT;

-- ADD: is_pre_clipper Flag auf existierenden feed_items (Migration-Cutoff)
ALTER TABLE feed_items ADD COLUMN is_pre_clipper INTEGER DEFAULT 0;
-- Migration: UPDATE feed_items SET is_pre_clipper=1 (alle Rows zum Migrationszeitpunkt)
```

**Warum neue `clips`-Tabelle und nicht `feed_items` erweitern:**

`feed_items.video_id` ist Primary Key → 1 Item pro Video. Mit Clipping: N Clips pro Original. Würde Breaking-Schema-Migration bedeuten. Stattdessen: legacy `feed_items` bleibt für pre-clipper Items unverändert, neue `clips`-Tabelle hat eigene PK + identische Feed-State-Felder. Feed-Query macht UNION über beide.

**Storage:**
- Originale: bleiben in `downloaded_videos.file_path` (existierender Mechanismus, `media/originals/<video_id>.mp4`)
- Clips: neuer Pfad `media/clips/<clip_id>.mp4` (9:16, smart-cropped, ~2–10 MB pro Clip)
- Cleanup: **kein automatisches Löschen zum Launch**. Bei späterem Disk-Druck → separater Cleanup-Job.

## Pipeline Integration Point

In `backend/src/pipeline/orchestrator.ts` ändert sich der `processNewVideo`-Flow nach `decide=approve`:

```ts
// HEUTE (vereinfacht):
if (decision === 'approve') {
  await download(...);
  insertFeedItem(db, videoId);    // ← bisheriger Pfad
}

// NEU:
if (decision === 'approve') {
  await download(...);
  // Pre-clipper legacy path entfällt für neue Videos:
  db.prepare('UPDATE videos SET clip_status=? WHERE id=?').run('pending', videoId);
  enqueue(db, videoId);            // ← neuer Pfad
  // KEIN insertFeedItem
}
```

Bestehende Tests in `pipeline/orchestrator.test.ts` werden entsprechend angepasst (siehe Testing-Section).

## Cooldown- + Topic-Mix-Algorithmus (App-Layer)

In `backend/src/api/feed.ts`:

```
1. Hole top 100 unseen feed-rows aus DB (UNION clips + legacy feed_items),
   sorted added_to_feed_at DESC. Diese Liste = candidates.
2. output = []
3. while output.length < requested_page_size and candidates not empty:
     // Suche bestes nächstes Item via Look-Ahead-Window (max 5 voraus):
     window = candidates.slice(0, 5)
     primary = first item in window that passes BOTH cooldowns:
       - parent_video_id NOT in last 3 of output (strict)
       - count of channel_id in last 3 of output < 2 (strict)
     if primary not found:
       apply Lockerungs-Stufe (siehe unten); restart inner search

     // Topic-Mix Look-Ahead:
     // Wenn primary.category == last(output).category UND es gibt im Window
     // ein anderes cooldown-erfüllendes Item mit anderer category, nimm dieses.
     better = first item in window after primary that:
       - passes both cooldowns
       - has category != last(output).category
       - AND primary.category == last(output).category
     pick = better if exists else primary

     output.push(pick); remove pick from candidates
4. return output

Lockerungs-Stufen (wenn keine cooldown-erfüllenden Items im Window):
  a) Topic-Mix-Look-Ahead deaktivieren (war nur Soft-Preference)
  b) Channel-Constraint von <2 auf <3 lockern
  c) Parent-Video-Constraint bleibt strict — sechs Clips eines 1h-Videos
     dürfen NIE direkt in Folge im Feed landen
  d) Wenn auch (b) nicht reicht: Window auf 10 vergrößern und Schritte a)+b)
     erneut versuchen
  e) Wenn auch das nicht reicht (kleiner Backlog): Page mit weniger als
     requested_page_size returnen, Client stoppt scrollen sanft
```

## API Changes

```
GET /feed
  → FeedItem[] mit:
    { id, kind: 'legacy'|'clip', videoId, parentVideoId, fileUrl,
      durationSec, title, channelTitle, thumbnail, ... }
  Cooldown + Topic-Mix bereits angewendet.

GET /videos/:id/full
  → { fileUrl, durationSec, title, channelTitle, ... }
  Für FullscreenPlayer "Original ansehen".

GET /clipper/status
  → { pending: number, processing: number, failed: number,
      no_highlights: number, lastRanAt: number, isWindowActive: boolean }
  Für Tuning-Tab "System"-Counter.

POST /clipper/retry-failed
  → { retriedCount: number }
  Setzt alle clip_status='failed' zurück auf 'pending', re-enqueued.
```

## Frontend Changes (Android)

- **`ui/feed/FeedScreen.kt`** — Feed-Item-DTO erweitert um `parentVideoId`. Tap-Handler auf Clip-Item öffnet "Original ansehen"-Action.
- **`ui/player/FullscreenOriginalPlayer.kt`** *(neu)* — Route: `original/{videoId}`. ExoPlayer mit `/videos/:id/full`-URL, fullscreen, immersive.
- **`ui/tuning/TuningSystemTab.kt`** — Neuer Counter-Block "Clipper Queue: X pending, Y failed" + Button "Failed retry" (POST /clipper/retry-failed).
- **`data/api/dto/FeedItemDto.kt`** — `kind`, `parentVideoId` ergänzen.
- **`data/api/dto/ClipperStatusDto.kt`** *(neu)*

**Keine** neue BottomNav-Route, **keine** neuen Tuning-Tabs (3-Routen-IA bleibt heilig).

## Qwen Clipper Prompt

Rendert aus aktivem `FilterConfig`:

```
SYSTEM:
Du bist ein Video-Highlight-Analyst für die App "Hikari" (kuratierte
Kurzvideos, positiv und lehrreich, Anti-Doom-Scroll). Du siehst das Video
direkt — Audio + Bild. Identifiziere die wertvollsten Highlight-Momente.

USER-KRITERIEN (was IST gut für diesen User):
- Bevorzugt: {likeTags, kommagetrennt}
- Vermeidet: {dislikeTags}
- Stimmung: {moodTags} | Tiefe: {depthTags}
- Sprachen: {languages}
- Beispiele bevorzugter Inhalte: {examples}

OPERATIONELLE REGELN (fest):
- Pro Clip: zwischen 20s und 60s, Toleranz bis 90s wenn der Highlight-
  Moment unteilbar ist
- Anzahl Clips: ungefähr 1 pro 5 Min Original-Dauer (5 Min→1, 15 Min→3,
  30 Min→6, 60 Min→12), aber NUR wenn Qualität es trägt
- Lieber WENIGER Clips von hoher Qualität als das ganze Video zerstückeln
- Wenn das Video keine highlight-würdigen Momente hat: leere Liste []

PRO CLIP gibst du an:
- start_sec, end_sec (Float)
- focus.{x,y,w,h} normalisiert 0.0–1.0 (wo das Wichtige im Frame liegt —
  für 9:16-Smart-Crop). Wenn Original schon hochkant: x=0, y=0, w=1, h=1.
- reason (kurze Begründung warum dieser Part)

OUTPUT: ausschließlich gültiges JSON-Array, sortiert nach start_sec ASC.
Keine Markdown-Code-Blocks, keine Erklärungen außerhalb des JSON.

Beispiel:
[
  {"start_sec": 142.5, "end_sec": 198.0,
   "focus": {"x": 0.25, "y": 0.15, "w": 0.5, "h": 0.7},
   "reason": "Klare Erklärung der Kernidee mit Diagramm"},
  {"start_sec": 612.0, "end_sec": 668.5,
   "focus": {"x": 0.3, "y": 0.2, "w": 0.4, "h": 0.6},
   "reason": "Punchy Quote über praktische Anwendung"}
]
```

## Error Handling

| Fehlerfall | Reaktion | Folge-Status |
|---|---|---|
| Qwen invalides JSON (1. Versuch) | 1× Retry mit System-Hint "respond with valid JSON only" | — |
| Qwen invalides JSON (2× fail) | Job gibt auf | `clip_status='failed'`, queue.last_error gesetzt |
| Qwen returnt `[]` | Akzeptiert | `clip_status='no_highlights'`, kein Feed-Item |
| Qwen liefert start_sec/end_sec außerhalb video.duration | Filter raus invalide Specs, render valide. Wenn 0 valide übrig: `'no_highlights'` | — |
| Qwen liefert Clip < 20s | Verlängere `end_sec` auf `start_sec + 20`, capped an `video.duration`. Wenn nicht möglich (Original kürzer als start+20): Clip verwerfen. Warning in log | Clip wird gerendert |
| Qwen liefert Clip > 90s | Trimme `end_sec` auf `start_sec + 90`. Warning in log | Clip wird gerendert |
| Remotion render-crash (1. Clip) | 1× Retry mit denselben Params | — |
| Remotion render-crash (2× Retry) | Job gibt auf, bereits gerenderte Clips dieses Videos werden gelöscht (Atomicity) | `clip_status='failed'` |
| Original-File fehlt (manuell gelöscht) | Sofort `failed`, kein Retry | `clip_status='failed'` |
| Backend-Hauptprozess crasht | Worker läuft weiter (eigener Prozess), DB ist crash-safe via WAL | — |
| Worker crasht mitten im Job | Beim Neustart: queue-rows mit `locked_at != NULL` aber älter 30 Min werden auto-unlocked, `attempts++`, neu eingereiht | — |

## Edge Cases

| Edge-Case | Verhalten |
|---|---|
| Original ≤ 90s | Kein Qwen-Run, kein Render. `clip_status='done'`, ein Passthrough-Clip-Eintrag wird erstellt mit: `start_seconds=0, end_seconds=video.duration_seconds, focus={x:0,y:0,w:1,h:1}, reason='short-form-passthrough', file_path=downloaded_videos.file_path` (referenziert das Original direkt — kein neuer Render-Output, kein Disk-Bedarf), `file_size_bytes=downloaded_videos.file_size_bytes`. Im Feed erscheint dieser Eintrag wie ein normaler Clip; Player streamt aus dem Originalfile. |
| Original schon 9:16 (vertikal) | Qwen-Prompt wird mit `aspect_ratio` aus videos-Tabelle erweitert, instructs focus={0,0,1,1}. Remotion macht kein Crop, nur Cut. |
| Schedule-Boundary 07:55 → startet 30-Min-Video | Worker checkt `isWindowActive()` NUR vor neuem Dequeue, nicht mitten im Job. Laufendes Video läuft zu Ende, dann Stop. Worst-Case ~30 Min Drift in den Morgen. |
| Filter-Config wird mitten in Job geändert | Job nutzt den Snapshot der bei Job-Start gelesen wurde (kein Live-Reload). |
| 1000 Videos auf einmal in Queue | Worker arbeitet sie über mehrere Nächte ab. Discovery-Layer ist davon nicht betroffen. Kein Backpressure nötig. |
| Channel mit `auto_approve=1` (Vertrauenskanal) | Funktioniert identisch — Auto-Approve umgeht nur den Scorer, nicht den Clipper. |
| User pausiert Discovery, Queue läuft leer | Worker idled in 60s-Polls bis 08:00 oder Queue füllt sich wieder. |

## Testing Strategy

Folgt dem existierenden Pattern: Vitest + in-memory better-sqlite3, externe Dependencies (Qwen, Remotion, ffmpeg) werden gemockt. Same Style wie `pipeline/orchestrator.test.ts`.

**Unit-Tests:**

| Datei | Was wird getestet |
|---|---|
| `clipper/queue.test.ts` | enqueue/dequeue atomicity · single-slot-lock · stale-lock recovery (>30 Min) · priority by duration ASC · isWindowActive(22:00–08:00) |
| `clipper/prompt-builder.test.ts` | `buildClipperPrompt(filter)` rendert FilterConfig sauber · alle Felder eingebettet · Snapshot-Test gegen reference prompt |
| `clipper/qwen-analyzer.test.ts` | Mock LM-Studio HTTP-Response · valides JSON wird korrekt geparsed · invalides JSON → 1× retry · 2× invalid → throws · Spec-Validierung (start<end, in-bounds, focus 0–1, clamp 20–90s) |
| `clipper/remotion-renderer.test.ts` | Mock Remotion CLI · Smart-Crop-Composition wird mit korrekten focus-Coords aufgerufen · Output-File existiert + non-zero size · Render-Crash → throws |
| `api/feed.test.ts` (erweitert) | Cooldown-3-Logik: Channel ≥2× in letzten 3 wird übersprungen · Parent-Video-ID-Cooldown · Topic-Mix-Tie-Breaker · Fallback-Lockerung wenn Page leer bleibt |
| `api/clipper-status.test.ts` | GET /clipper/status returnt korrekte Counts · POST /clipper/retry-failed setzt failed→pending zurück |

**Integration-Tests:**

| Datei | Was wird getestet |
|---|---|
| `clipper/worker.integration.test.ts` | End-to-end Worker-Loop mit gemocktem Qwen+Remotion: Video in Queue → Worker holt → analyze → render → clips inserted → queue clean. Inkl. happy path, no_highlights path, failed path mit Cleanup. |
| `pipeline/orchestrator.test.ts` (erweitert) | Approved Video läuft NICHT mehr direkt in feed_items, sondern in clipper_queue + videos.clip_status='pending'. Legacy `is_pre_clipper`-Path bleibt für rückwärtskompatible Tests. |
| `db/migrations.test.ts` | Migration ist idempotent · existierende feed_items werden mit is_pre_clipper=1 markiert · neue Tabellen werden erstellt · ALTERs kollidieren nicht mit bereits-aufgelaufenen DBs |

**Manuelle Tests (vor Release):**

1. Long-Form-Video (≥30 Min Talking-Head) durch komplette Pipeline → Clips landen im Feed mit Smart-Crop, Original via Fullscreen-Player erreichbar.
2. Wide-Shot-Video (Konzert / Landschaft) → Smart-Crop sieht plausibel aus.
3. 1h-Video während Schedule-Boundary 07:55 starten → läuft sauber zu Ende.
4. SIGTERM während Render → Worker beendet aktuellen Schritt, kein Halbzustand in DB.
5. Failed-Retry-Button im Tuning-System-Tab → Failed clips re-enqueued.
6. Cooldown-Verhalten: Scrollen über 30 Items, kein Channel ≥3× in 3-Item-Fenstern.

**Bewusst NICHT getestet:**
- Echte Qwen-Output-Qualität (Modell-Output, nicht Code) — nur Schema-Validität.
- Echte Remotion-Render-Performance (Hardware-abhängig, nicht reproducible in CI).
- macOS-spezifisches Cron-Verhalten — `isWindowActive` nutzt simple `Date.getHours()`, plattformunabhängig.

## Out of Scope (bewusst nicht in v1)

- Backfill von alten pre-clipper-Videos durch den Clipper. Falls später gewünscht: separater "Backfill alte Videos jetzt durchclippen"-Button im Tuning-System-Tab — nicht zum Launch.
- Automatischer Cleanup alter Originale (Disk-Druck-Strategie). Erst wenn der User es brauchst.
- System-Idle-Detection als Schedule-Alternative. Schedule-Fenster reicht.
- Manueller Pause-Toggle. Schedule deckt es ab.
- Re-Try-Cron für failed Clips. Manueller Retry-Button reicht.
- Clipper-spezifische Filter-Felder neben FilterConfig (`clipperHints`-Textarea). YAGNI bis erste Real-Use-Daten zeigen ob nötig.
- Vierter Tuning-Tab "Clipper". Bricht 3-Tab-IA.

## Implementation Order (für späteren Plan)

Skizze, nicht final:

1. Schema-Migration + DB-Tests
2. `clipper/prompt-builder.ts` + Tests (kein Modell-Call nötig)
3. `clipper/queue.ts` + Tests (pure SQL, kein Modell)
4. `clipper/qwen-analyzer.ts` + Tests (gemockt)
5. `clipper/remotion-renderer.ts` + Tests (gemockt)
6. `clipper/worker.ts` + Integration-Test
7. `pipeline/orchestrator.ts` Anpassung (approved → enqueue statt feed_items)
8. `api/feed.ts` Cooldown-/Topic-Mix-Logik + UNION-Query
9. `api/clipper-status.ts` + retry endpoint
10. Android: FullscreenOriginalPlayer + DTO-Updates + System-Tab-Counter
11. Manuelle End-to-End-Tests auf echtem M4 Max mit echtem Qwen
12. Release
