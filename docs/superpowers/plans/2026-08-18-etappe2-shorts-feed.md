# Etappe 2: Shorts + Feed-Umbau — Implementierungsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Der Feed liefert native YouTube-Shorts (autoplay) und Langvideos (Vorschau-Karten) der Abo-Kanäle statt gerenderter Clips; Clipper und Auto-Download sind aus dem Approve-Pfad entfernt.

**Architecture:** Neuer Innertube-Abruf des Kanal-Shorts-Tabs speist die bestehende Ingest-Queue; `videos` bekommt `format/source/summary`; der Approve-Pfad legt nur noch eine `feed_items`-Row an (kein Download, kein Clip-Enqueue) und erzeugt best-effort eine KI-Kurzbeschreibung; die Feed-API liefert Videos direkt aus `videos`+`feed_items` (LEFT JOIN auf Downloads); die App rendert pro Feed-Seite entweder den ReelPlayer (Shorts) oder eine Vorschau-Karte (Langvideos) mit Index-Mapping zwischen Pager und Player-Playlist.

**Tech Stack:** Fastify + TypeScript (ESM, Imports mit `.js`), better-sqlite3, Vitest; Android: Kotlin, Compose, Room 15, Media3.

**Spec:** `docs/superpowers/specs/2026-08-18-feed-streaming-overhaul-design.md` (§4.2, §4.3, §4.5-Teil „Karten-Zusammenfassung", §4.6, §5, §6, Etappe 2)

## Global Constraints

- Backend-Tests/Build NUR mit System-Node: `cd backend && npm test` bzw. `npm run build`. Biome-Stil, deutsche Kommentare im Stil der Nachbardateien.
- ESM: relative Imports enden auf `.js`.
- `kind == "clip"` bleibt überall funktionsfähig (gespeicherte/alte Clips in mode=saved/old, `/clips/`-Mount, „Original ansehen"-Button).
- Playback: `/stream/video/:id` existiert (Etappe 1) und ist der Pfad für alles außer Clips — `HikariPlayerFactory.mediaItemFor` routet `else`-kind bereits dorthin.
- Keine Engagement-Signale im Ranking; UI: keine Emojis, Amber nur als Akzent.
- Der Produktiv-Server wird erst NACH Abschluss aller Backend-Tasks per `cli/hikari restart` neu gestartet.
- ACHTUNG: In diesem Repo können parallele Sessions committen — vor Versionsbump/Release `git fetch --tags` + `gh release list` prüfen.

---

### Task 1: DB-Migration `videos.format/source/summary`

**Files:**
- Modify: `backend/src/db/migrations.ts` (ans Ende von `applyMigrations`)
- Modify: `backend/src/db/schema.sql` (CREATE TABLE videos, ~L24–42)
- Test: `backend/src/db/migrations.test.ts`

**Interfaces:**
- Produces: Spalten `videos.format TEXT` (`'short'|'long'`, für Bestandsvideos backfilled), `videos.source TEXT` (Default-Bedeutung `'subscription'`), `videos.summary TEXT` (nullable).

- [ ] **Step 1: Failing Test** — in `migrations.test.ts` beim bestehenden Spalten-Test-Muster (`expect.arrayContaining` auf `PRAGMA table_info`) ergänzen:

```ts
test("videos hat format/source/summary und Bestand ist als long/short backfilled", () => {
  const db = new Database(":memory:");
  applyMigrations(db);
  const cols = (db.prepare("PRAGMA table_info(videos)").all() as { name: string }[]).map((c) => c.name);
  expect(cols).toEqual(expect.arrayContaining(["format", "source", "summary"]));
  db.prepare(
    "INSERT INTO channels (id, url, title, is_active, added_at) VALUES ('c1','u','t',1,0)",
  ).run();
  db.prepare(
    "INSERT INTO videos (id, channel_id, title, published_at, duration_seconds, aspect_ratio, discovered_at, format) VALUES ('v1','c1','t',0,60,'9:16',0,NULL)",
  ).run();
  applyMigrations(db); // idempotent + Backfill
  const row = db.prepare("SELECT format FROM videos WHERE id='v1'").get() as { format: string };
  expect(row.format).toBe("short");
});
```

(Vorher prüfen, welche NOT-NULL-Spalten der `channels`/`videos`-INSERT im echten Schema braucht — an bestehende Test-Seeds in `migrations.test.ts`/`feed.test.ts` anlehnen.)

- [ ] **Step 2: Run** `npx vitest run src/db/migrations.test.ts` — Expected: FAIL (Spalten fehlen).

- [ ] **Step 3: Implementierung** — `schema.sql` CREATE TABLE videos um drei Zeilen ergänzen (`format TEXT`, `source TEXT`, `summary TEXT`); in `migrations.ts` ans Ende:

```ts
// Etappe 2 (Feed-Streaming-Umbau): Format-/Quellen-/Zusammenfassungs-Spalten.
addColumnIfMissing(db, "videos", "format", "TEXT"); // 'short' | 'long'
addColumnIfMissing(db, "videos", "source", "TEXT"); // 'subscription' | 'probe' | 'topic' | 'backfill'
addColumnIfMissing(db, "videos", "summary", "TEXT"); // KI-Kurzbeschreibung für Feed-Karten
// Backfill: Bestandsvideos nach Heuristik klassifizieren (Hochkant + ≤3 min = Short).
db.exec(`UPDATE videos SET format = CASE
  WHEN aspect_ratio = '9:16' AND duration_seconds <= 180 THEN 'short' ELSE 'long' END
  WHERE format IS NULL`);
db.exec("UPDATE videos SET source = 'subscription' WHERE source IS NULL");
```

- [ ] **Step 4: Run** `npx vitest run src/db/migrations.test.ts` — Expected: PASS (alle Tests, auch der Tabellenlisten-Test — es kommt keine neue Tabelle hinzu).

- [ ] **Step 5: Commit** `git add backend/src/db backend/src/db/migrations.test.ts && git commit -m "feat(db): videos.format/source/summary + Backfill"`

---

### Task 2: `itChannelShorts` (Innertube Shorts-Tab)

**Files:**
- Modify: `backend/src/api/music-innertube.ts` (bei `itChannelVideos`, ~L922)
- Test: `backend/tests/api/innertube-shorts.test.ts` (neu)

**Interfaces:**
- Consumes: vorhandene Helfer in `music-innertube.ts`: `webBrowse(fetchImpl, payload)`, `findAllByKey`, `VIDEO_ID_RE`.
- Produces: `export async function itChannelShorts(fetchImpl: typeof fetch, channelId: string): Promise<string[] | undefined>` — bis zu 30 Video-IDs des Shorts-Tabs, `undefined` bei Fehler/leer (wirft nie).

**Empirisch verifiziert (2026-08-18):** `params: "EgZzaG9ydHPyBgUKA5oBAA=="` auf dem WEB-`browse`-Endpoint wählt den Shorts-Tab; Items kommen als `shortsLockupViewModel` (48 Stück beim Testkanal); die Video-ID steht mehrfach als `videoId`-Feld in jedem Lockup (u. a. im `reelWatchEndpoint`). Der bestehende `collectChannelVideos`-Parser verwirft Shorts (Content-Type-Filter + Pflicht-Dauer) — deshalb eigener, ID-only-Parser.

- [ ] **Step 1: Failing Test** (`backend/tests/api/innertube-shorts.test.ts`):

```ts
import { expect, test } from "vitest";
import { itChannelShorts } from "../../src/api/music-innertube.js";

function lockup(videoId: string) {
  return {
    shortsLockupViewModel: {
      onTap: { innertubeCommand: { reelWatchEndpoint: { videoId } } },
    },
  };
}
const body = { contents: [lockup("aaaaaaaaaaa"), lockup("bbbbbbbbbbb"), lockup("aaaaaaaaaaa"), lockup("kurz")] };
const okFetch = (async () => new Response(JSON.stringify(body), { status: 200 })) as typeof fetch;

test("liefert deduplizierte, valide Shorts-IDs", async () => {
  const ids = await itChannelShorts(okFetch, "UCtest");
  expect(ids).toEqual(["aaaaaaaaaaa", "bbbbbbbbbbb"]); // "kurz" fällt an VIDEO_ID_RE
});

test("Fehler/leer ⇒ undefined", async () => {
  const failFetch = (async () => new Response("x", { status: 500 })) as typeof fetch;
  expect(await itChannelShorts(failFetch, "UCtest")).toBeUndefined();
  const emptyFetch = (async () => new Response("{}", { status: 200 })) as typeof fetch;
  expect(await itChannelShorts(emptyFetch, "UCtest")).toBeUndefined();
});
```

- [ ] **Step 2: Run** `npx vitest run tests/api/innertube-shorts.test.ts` — Expected: FAIL (Export fehlt).

- [ ] **Step 3: Implementierung** in `music-innertube.ts`, direkt unter `itChannelVideos`:

```ts
// Shorts-Tab (base64-Protobuf, sprachunabhängig — empirisch verifiziert 2026-08).
const WEB_SHORTS_TAB_PARAMS = "EgZzaG9ydHPyBgUKA5oBAA==";
const CHANNEL_SHORTS_MAX = 30;

/**
 * IDs der neuesten Shorts eines Kanals. Bewusst ID-only: Shorts-Lockups tragen
 * oft keine Dauer-Badge, Metadaten holt ohnehin die Ingest-Pipeline.
 */
export async function itChannelShorts(
  fetchImpl: typeof fetch,
  channelId: string,
): Promise<string[] | undefined> {
  try {
    const body = await webBrowse(fetchImpl, { browseId: channelId, params: WEB_SHORTS_TAB_PARAMS });
    const ids: string[] = [];
    const seen = new Set<string>();
    for (const lockup of findAllByKey(body, "shortsLockupViewModel")) {
      const candidates = findAllByKey(lockup, "videoId").filter(
        (v): v is string => typeof v === "string" && VIDEO_ID_RE.test(v),
      );
      const id = candidates[0];
      if (!id || seen.has(id)) continue;
      seen.add(id);
      ids.push(id);
      if (ids.length >= CHANNEL_SHORTS_MAX) break;
    }
    return ids.length > 0 ? ids : undefined;
  } catch {
    return undefined;
  }
}
```

(Falls `findAllByKey` nicht exportiert/nutzbar signiert ist: Signatur vor Ort prüfen und ggf. lokal anpassen — sie existiert in derselben Datei.)

- [ ] **Step 4: Run** `npx vitest run tests/api/innertube-shorts.test.ts` — Expected: PASS. Danach `npm test` komplett.

- [ ] **Step 5: Live-Smoke** (optional, Netz): kleines Node-Skript oder `npx tsx -e` mit echtem `fetch` gegen `UCX6OQ3DkcsbYNE6H8uQQuVA` — erwartet ≥1 ID.

- [ ] **Step 6: Commit** `git add backend/src/api/music-innertube.ts backend/tests/api/innertube-shorts.test.ts && git commit -m "feat(ingest): itChannelShorts — Shorts-Tab-IDs via Innertube"`

---

### Task 3: Poller holt Shorts der Abo-Kanäle

**Files:**
- Modify: `backend/src/index.ts` (`pollAllChannels`, ~L148–212)

**Interfaces:**
- Consumes: `itChannelShorts(fetch, channelId)` aus Task 2; `enqueueIngest(db, videoId, channelId)` (skippt bereits bekannte Videos).

- [ ] **Step 1: Implementierung** — im due-Kanal-Zweig von `pollAllChannels`, nach der RSS-Verarbeitung (gleicher try/catch-Stil wie RSS, best-effort):

```ts
// Shorts-Tab zusätzlich zum RSS abfragen: Kanal-RSS enthält Shorts unzuverlässig.
try {
  const shortIds = await itChannelShorts(fetch, c.id);
  for (const id of shortIds ?? []) enqueueIngest(db, id, c.id);
} catch {
  // best-effort — ein Innertube-Schluckauf darf den Poll nicht brechen
}
```

Import oben ergänzen: `import { itChannelShorts } from "./api/music-innertube.js";`

- [ ] **Step 2: Build + Tests** `npm run build && npm test` — Expected: grün (keine bestehenden Tests betroffen; `pollAllChannels` ist untestbar verdrahtet — Verhalten wird im Gesamt-Smoke verifiziert).

- [ ] **Step 3: Commit** `git add backend/src/index.ts && git commit -m "feat(ingest): Poller enqueued zusätzlich Kanal-Shorts"`

---

### Task 4: Video-Zusammenfassung (`summarizeVideoTranscript`)

**Files:**
- Modify: `backend/src/clipper/context-summarizer.ts`
- Test: `backend/src/clipper/context-summarizer.test.ts` (erweitern)

**Interfaces:**
- Consumes: bestehende interne Request-Logik von `summarizeContext` (fetch auf `${baseUrl}/v1/chat/completions`, `SummarizerConfig { baseUrl, model, fetchFn? }`).
- Produces: `export async function summarizeVideoTranscript(title: string, transcript: string, config: SummarizerConfig): Promise<string | null>` — 1–2 Sätze Karten-Teaser, `null` bei zu kurzem Transkript (<100 Zeichen) oder Fehler (wirft nie).

- [ ] **Step 1: Failing Test** — im bestehenden Test-Stil (fetchFn-Fake) ergänzen:

```ts
test("summarizeVideoTranscript liefert Teaser aus Titel+Transkript", async () => {
  const fetchFn = (async () =>
    new Response(
      JSON.stringify({ choices: [{ message: { content: "Ein Teaser." } }] }),
      { status: 200 },
    )) as typeof fetch;
  const out = await summarizeVideoTranscript("Titel", "x".repeat(200), {
    baseUrl: "http://llm",
    model: "m",
    fetchFn,
  });
  expect(out).toBe("Ein Teaser.");
});

test("summarizeVideoTranscript: kurzes Transkript oder LLM-Fehler ⇒ null", async () => {
  expect(await summarizeVideoTranscript("t", "kurz", { baseUrl: "http://llm", model: "m" })).toBeNull();
  const failFn = (async () => new Response("x", { status: 500 })) as typeof fetch;
  expect(
    await summarizeVideoTranscript("t", "x".repeat(200), { baseUrl: "http://llm", model: "m", fetchFn: failFn }),
  ).toBeNull();
});
```

- [ ] **Step 2: Run** — Expected: FAIL (Export fehlt).

- [ ] **Step 3: Implementierung** — die HTTP-/Antwort-Parsing-Logik aus `summarizeContext` in eine private Helferfunktion `chatCompletion(systemPrompt: string, userContent: string, config: SummarizerConfig): Promise<string | null>` extrahieren (Fehlerverhalten der neuen Funktion: try/catch um alles, `null` statt Throw — `summarizeContext` behält sein bisheriges Throw-Verhalten, ruft aber intern denselben Helfer mit eigenem try/rethrow). Dann:

```ts
const SYSTEM_PROMPT_VIDEO = `Du schreibst Kurzbeschreibungen für Video-Vorschaukarten einer deutschen App.
Du bekommst Titel und Transkript eines YouTube-Videos.
Antworte mit 1-2 Sätzen (max. 220 Zeichen), die neugierig machen, ohne zu spoilern.
Nüchtern und konkret, keine Werbesprache, keine Emojis, keine Anführungszeichen, kein Präfix.`;

/** Karten-Teaser für Langvideos — best-effort, wirft nie. */
export async function summarizeVideoTranscript(
  title: string,
  transcript: string,
  config: SummarizerConfig,
): Promise<string | null> {
  const text = transcript.trim();
  if (text.length < 100) return null;
  try {
    return await chatCompletion(
      SYSTEM_PROMPT_VIDEO,
      `Titel: ${title}\n\nTranskript:\n${text.slice(0, 24_000)}`,
      config,
    );
  } catch {
    return null;
  }
}
```

- [ ] **Step 4: Run** Summarizer-Tests + `npm test` komplett — Expected: PASS (bestehende `summarizeContext`-Tests unverändert grün).

- [ ] **Step 5: Commit** `git add backend/src/clipper/context-summarizer.ts backend/src/clipper/context-summarizer.test.ts && git commit -m "feat(feed): summarizeVideoTranscript — Karten-Teaser aus YouTube-Transkript"`

---

### Task 5: Orchestrator — kein Download, kein Clipper, feed_items + summary

**Files:**
- Modify: `backend/src/pipeline/orchestrator.ts`
- Modify: `backend/src/config.ts` (~L92–99: `clipper.enabled`-Default)
- Modify: `backend/src/index.ts` (drainIngestQueue-Callsite, ~L228–236)
- Test: `backend/src/pipeline/orchestrator.test.ts`, `backend/src/config.test.ts`

**Interfaces:**
- Consumes: `summarizeVideoTranscript` (Task 4), Spalten aus Task 1.
- Produces: geänderte `ProcessNewVideoDeps`:

```ts
export interface ProcessNewVideoDeps {
  db; videoId: string; channelId: string;
  fetchMetadata: (videoId: string) => Promise<VideoMetadata>;
  fetchTranscript: (url: string) => Promise<string | null>;
  fetchSponsorSegments: (videoId: string) => Promise<SponsorSegment[] | null>;
  scorer: Scorer;
  clipperEnabled?: boolean;                                              // default false
  summarize?: (title: string, transcript: string) => Promise<string | null>; // best-effort
}
// `download` entfällt komplett aus den Deps.
```

Approve legt eine `feed_items`-Row an (`is_pre_clipper=1`); `insertVideo` schreibt zusätzlich `format` (Heuristik `aspectRatio === "9:16" && durationSeconds <= 180 → 'short'`, sonst `'long'`), `source='subscription'`, `summary`.

- [ ] **Step 1: Failing Tests** — in `orchestrator.test.ts` (bestehende Fake-Deps-Muster nutzen; `download`-Fakes entfernen):

```ts
test("approve: kein Download, kein Clipper-Enqueue, feed_items-Row + format/summary gesetzt", async () => {
  // Fake-Metadaten: 9:16, 45s → format 'short'; scorer approved; summarize wird für shorts NICHT gerufen
  await processNewVideo({ ...deps, clipperEnabled: false });
  expect(db.prepare("SELECT format, source FROM videos WHERE id=?").get(VID)).toEqual({
    format: "short", source: "subscription",
  });
  expect(db.prepare("SELECT is_pre_clipper FROM feed_items WHERE video_id=?").get(VID)).toEqual({ is_pre_clipper: 1 });
  expect(db.prepare("SELECT COUNT(*) c FROM downloaded_videos").get()).toEqual({ c: 0 });
  expect(db.prepare("SELECT COUNT(*) c FROM clipper_queue").get()).toEqual({ c: 0 });
  expect(db.prepare("SELECT clip_status FROM videos WHERE id=?").get(VID)).toEqual({ clip_status: null });
});

test("approve long: summary via summarize-Dep, Fehler ⇒ null-summary aber Approve läuft durch", async () => { /* meta 16:9/1200s; summarize → "Teaser" bzw. throw */ });

test("clipperEnabled=true: clip_status pending + clipper_queue-Enqueue wie früher (aber weiterhin ohne Download)", async () => { /* ... */ });

test("Green-Card: Short unterhalb minDurationSec wird trotzdem approved", async () => {
  // Kanal auto_approve=1, filter.minDurationSec=300; meta 9:16/45s → approved, feed_items-Row existiert
});
```

- [ ] **Step 2: Run** `npx vitest run src/pipeline/orchestrator.test.ts` — Expected: FAIL.

- [ ] **Step 3: Implementierung** in `orchestrator.ts`:
  - Deps-Interface wie oben; `download`-Aufrufe (L121, L151) und `insertDownload`-Aufrufe streichen (`insertDownload`-Funktion löschen, wenn dann ungenutzt).
  - Format bestimmen: `const format = meta.aspectRatio === "9:16" && meta.durationSeconds <= 180 ? "short" : "long";`
  - `insertVideo`-Signatur um `format`, `source` (fest `"subscription"`), `summary` erweitern; SQL-Spalten ergänzen.
  - Neuer Helfer:

```ts
function insertFeedItem(db: Database.Database, videoId: string, now: number): void {
  db.prepare(
    "INSERT OR IGNORE INTO feed_items (video_id, added_to_feed_at, is_pre_clipper) VALUES (?, ?, 1)",
  ).run(videoId, now);
}
```

  - Beide Approve-Transaktionen: `insertVideo(…); insertScore(…"approved"…); insertSponsors(…); insertFeedItem(db, videoId, now); if (deps.clipperEnabled) { db.prepare("UPDATE videos SET clip_status='pending' WHERE id=?").run(videoId); enqueue(db, videoId); }`
  - Summary VOR der Transaktion (LLM-Call gehört nicht in eine SQLite-Transaktion): `const summary = format === "long" && transcript ? ((await deps.summarize?.(meta.title, transcript).catch(() => null)) ?? null) : null;`
  - Green-Card-Dauerprüfung (L101–103): nur noch für `format !== "short"` anwenden (der Min-Dauer-Filter ist ein Langform-Kriterium; Kommentar dazu schreiben).
  - `config.ts`: `enabled: env.CLIPPER_ENABLED === "true"` (Default **false**, Spec §4.3); `config.test.ts` entsprechend anpassen.
  - `index.ts` Callsite: `download`-Property entfernen, ergänzen: `clipperEnabled: cfg.clipper.enabled, summarize: (title, transcript) => summarizeVideoTranscript(title, transcript, { baseUrl: cfg.clipper.baseUrl, model: cfg.clipper.model })` (Import aus `./clipper/context-summarizer.js`). Ungenutzten `downloadVideo`-Import prüfen — `runCleanup`/andere Nutzer beachten, nur entfernen wenn wirklich ungenutzt.

- [ ] **Step 4: Run** `npm run build && npm test` — Expected: PASS (inkl. angepasster config-Tests).

- [ ] **Step 5: Commit** `git add backend/src/pipeline backend/src/config.ts backend/src/config.test.ts backend/src/index.ts && git commit -m "feat(pipeline): Approve ohne Download/Clipper — feed_items + format/summary"`

---

### Task 6: Feed-API liefert Videos (kind short/video)

**Files:**
- Modify: `backend/src/api/feed.ts`
- Modify: `backend/src/api/video-full.ts` (~L14: INNER→LEFT JOIN)
- Test: `backend/src/api/feed.test.ts`

**Interfaces:**
- Consumes: Spalten aus Task 1; feed_items-Rows aus Task 5.
- Produces: `GET /feed?mode=new` liefert Items mit `kind: "short" | "video"` (Clips-Arm entfällt in new), zusätzlich Feld `summary: string | null`; `filePath` ist nullable (LEFT JOIN). mode=saved/old behalten Clips; deren Video-Queries werden LEFT-JOINed. `today-count.unseenCount` zählt nur noch feed_items.

- [ ] **Step 1: Failing Tests** — `feed.test.ts` anpassen/ergänzen (bestehende Seed-Helfer nutzen):

```ts
test("mode=new liefert approved Video OHNE Download als kind 'video' mit summary", () => {
  seedVideo({ id: "v1", format: "long", summary: "Teaser" }); seedFeedItem("v1"); seedScore("v1", "approved");
  const rows = get("/feed?mode=new");
  expect(rows[0]).toMatchObject({ videoId: "v1", kind: "video", summary: "Teaser" });
});

test("mode=new: format short ⇒ kind 'short'", () => { /* analog */ });

test("mode=new enthält keine Clips mehr", () => {
  seedClip(...); // ungesehener Clip
  expect(get("/feed?mode=new").every((r) => r.kind !== "clip")).toBe(true);
});

test("mode=saved liefert gespeicherte Clips weiterhin (kind 'clip')", () => { /* Bestand */ });

test("today-count zählt nur feed_items", () => { /* Clip ungesehen + 1 feed_item → unseenCount 1 */ });
```

Bestehende Tests, die den Clips-Arm in mode=new prüfen, auf die neue Semantik umschreiben (nicht löschen ohne Ersatz — die Ranking-/Interleave-/Cooldown-Tests bleiben, nur mit Video-Seeds).

- [ ] **Step 2: Run** — Expected: FAIL.

- [ ] **Step 3: Implementierung** in `feed.ts`:
  - `listFeedRaw` (L107–143): Clips-Arm (Arm A) entfernen; Arm B wird:

```sql
SELECT CASE WHEN v.format = 'short' THEN 'short' ELSE 'video' END AS kind,
       f.video_id AS id, f.video_id AS parentVideoId, v.channel_id AS channelId,
       s.category AS category, f.added_to_feed_at AS addedToFeedAt,
       v.duration_seconds AS durationSec, s.overall_score AS overallScore,
       s.educational_value AS educationalValue, cms.calculated_score AS channelMatch
FROM feed_items f
JOIN videos v ON v.id = f.video_id
LEFT JOIN scores s ON s.video_id = f.video_id
LEFT JOIN channel_match_scores cms ON cms.channel_id = v.channel_id
WHERE f.seen_at IS NULL AND f.playback_failed = 0 AND f.is_pre_clipper = 1
ORDER BY f.added_to_feed_at DESC LIMIT ?
```

  - Hydration: der Legacy-Zweig (L322–340) wird der Video-Zweig — `downloaded_videos` per **LEFT JOIN** (`filePath` nullable), zusätzlich `v.format`, `v.summary AS summary`; `kind` aus dem RawRow durchreichen (`"short" | "video"`).
  - `BASE_SELECT` (L375–387, mode=saved/old): INNER JOIN `downloaded_videos` → LEFT JOIN; `CASE WHEN v.format='short' THEN 'short' ELSE 'video' END AS kind` + `v.summary AS summary` in die Spaltenliste.
  - `GET /feed/today-count` (L577–592): Clip-COUNT entfernen, nur feed_items zählen.
  - `video-full.ts` L14: INNER→LEFT JOIN `downloaded_videos` (Original-Player streamt jetzt; `filePath` nullable tolerieren).

- [ ] **Step 4: Run** `npm run build && npm test` — Expected: PASS komplett.

- [ ] **Step 5: Commit** `git add backend/src/api/feed.ts backend/src/api/video-full.ts backend/src/api/feed.test.ts && git commit -m "feat(feed): Videos statt Clips im Feed — kind short/video, LEFT JOIN downloads, summary"`

---

### Task 7: Android Datenmodell — `summary` + Room 15

**Files:**
- Modify: `android/app/src/main/java/com/hikari/app/data/api/dto/FeedItemDto.kt`
- Modify: `android/app/src/main/java/com/hikari/app/data/db/FeedItemEntity.kt`
- Modify: `android/app/src/main/java/com/hikari/app/domain/model/FeedItem.kt`
- Modify: `android/app/src/main/java/com/hikari/app/domain/repo/FeedRepository.kt` (3 Mapper am Dateiende)
- Modify: `android/app/src/main/java/com/hikari/app/data/db/HikariDatabase.kt` (version 15 + `MIGRATION_14_15`)
- Modify: `android/app/src/main/java/com/hikari/app/di/DatabaseModule.kt` (`.addMigrations(...)`)

**Interfaces:**
- Consumes: Feed-JSON aus Task 6 (`summary: String?`, kind-Werte `"short"/"video"`).
- Produces: `FeedItem.summary: String?` (als LETZTES Feld — positionsstabile Konstruktor-Aufrufe, siehe Kommentar in `FeedItem.kt`).

- [ ] **Step 1: Implementierung (mechanisch, alle 6 Dateien in einem Zug):**
  - DTO: `val summary: String? = null`
  - Entity: `@ColumnInfo(defaultValue = "NULL") val summary: String? = null`
  - Domain: `val summary: String? = null` ans Ende.
  - Mapper (alle drei!): Feld durchreichen.
  - `HikariDatabase.kt`: `version = 15` und:

```kotlin
val MIGRATION_14_15 = object : Migration(14, 15) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE feed_items ADD COLUMN summary TEXT DEFAULT NULL")
    }
}
```

  - `DatabaseModule.kt:29`: `.addMigrations(MIGRATION_12_13, MIGRATION_13_14, MIGRATION_14_15)`

- [ ] **Step 2: Kompilieren** `cd android && ./gradlew :app:compileDebugKotlin` — Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Commit** `git add android/app/src/main/java/com/hikari/app && git commit -m "feat(app): summary-Feld + Room 15"`

---

### Task 8: Gemischter Pager — Karten-Seiten für Langvideos

**Files:**
- Create: `android/app/src/main/java/com/hikari/app/ui/feed/LongVideoCard.kt`
- Modify: `android/app/src/main/java/com/hikari/app/ui/feed/FeedScreen.kt` (~L216–292: Playlist-Bau + Pager-Inhalt; L336: Original-Gate bleibt)

**Interfaces:**
- Consumes: `FeedItem` mit `kind ∈ {"short","video","clip","legacy"}`, `summary`, `reasoning`, `thumbnailUrl`, `durationSeconds`; `playVideoRoute(videoId, title, channel)` aus `HikariNavHost.kt:79`; Dauer-Format-Helfer nach Vorbild `SavedTab.kt:126`.
- Produces: Feed-Seiten: `kind == "video"` → `LongVideoCard` (kein Playback), alles andere → `ReelPlayer` wie bisher. Player-Playlist enthält NUR abspielbare Items; Mapping `videoId → playlistIndex`.

**Kritische Invariante (aus Code-Analyse):** Heute gilt `pagerState.currentPage == player.currentMediaItemIndex` (FeedScreen L230–262). Diese Kopplung wird durch das Mapping ersetzt — Karten-Seiten pausieren den Player nur.

- [ ] **Step 1: `LongVideoCard.kt`** — dunkle Vollbild-Seite im App-Stil (kein Emoji, Amber nur Akzent):

```kotlin
@Composable
fun LongVideoCard(
    item: FeedItem,
    onOpen: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .clickable(onClick = onOpen)
            .padding(horizontal = 20.dp)
            .padding(top = 96.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Box {
            AsyncImage(
                model = item.thumbnailUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(16.dp)),
            )
            Text(
                text = formatDuration(item.durationSeconds),
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .background(Color.Black.copy(alpha = 0.7f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.height(16.dp))
        Text(item.title, style = MaterialTheme.typography.titleLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(6.dp))
        Text(item.channelTitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        val teaser = item.summary ?: item.reasoning
        if (teaser.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(teaser, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 4, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(20.dp))
        Text("Tippen zum Ansehen", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
    }
}

private fun formatDuration(seconds: Int): String {
    val h = seconds / 3600; val m = (seconds % 3600) / 60; val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
```

(Imports gemäß Nachbardateien; Farb-/Typo-Töne an `ReelPlayer`/`LibraryScreen` angleichen — vorher kurz dort nachsehen.)

- [ ] **Step 2: `FeedScreen.kt` umbauen:**

```kotlin
// Nur abspielbare Items in die Player-Playlist (Karten-Seiten spielen nicht).
val playableItems = remember(items) { items.filter { it.kind != "video" } }
val playlistIndexByVideoId = remember(playableItems) {
    playableItems.withIndex().associate { (i, it) -> it.videoId to i }
}
val playlistKey = playableItems.joinToString("|") { it.videoId }
// setMediaItems: mediaItems aus playableItems bauen; Start-Index über das Mapping
// der aktuellen Seite (Karten-Seite → 0.coerceAtMost(...)-Fallback).
```

Seitenwechsel-Effekt (ersetzt das nackte `seekTo(idx, 0)`):

```kotlin
LaunchedEffect(pagerState.settledPage, playlistKey) {
    val item = items.getOrNull(pagerState.settledPage) ?: return@LaunchedEffect
    if (item.kind == "video") {
        player.pause()
    } else {
        val target = playlistIndexByVideoId[item.videoId] ?: return@LaunchedEffect
        if (player.currentMediaItemIndex != target) player.seekTo(target, 0L)
        player.play()
    }
}
```

Pager-Inhalt:

```kotlin
VerticalPager(state = pagerState, key = { items[it].videoId }) { page ->
    val item = items[page]
    if (item.kind == "video") {
        LongVideoCard(
            item = item,
            onOpen = { onNavigate(playVideoRoute(item.videoId, item.title, item.channelTitle)) },
        )
        // Karten-„gesehen": 1,5 s Verweildauer statt 3 s Playback.
        LaunchedEffect(item.videoId, pagerState.settledPage) {
            if (pagerState.settledPage == page) { delay(1500); vm.onSeen(item.videoId) }
        }
    } else {
        ReelPlayer(/* bestehende Parameter unverändert */)
    }
}
```

Wichtig: `vm.onSeen` ist idempotent genug (setzt seen_at); FeedDao behält Top-10-Regel, die Seite verschwindet also nicht unterm Finger. Das `ReelPlayer`-eigene Seen-Verhalten bleibt unberührt.

- [ ] **Step 3: Kompilieren** `./gradlew :app:compileDebugKotlin` — Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Commit** `git add android/app/src/main/java/com/hikari/app/ui/feed && git commit -m "feat(feed): gemischter Pager — Shorts autoplay, Langvideo-Karten"`

---

### Task 9: Gesamtverifikation, Version, Release

**Files:**
- Modify: `android/app/build.gradle.kts` (Version — AKTUELLEN Stand + `git fetch --tags`/`gh release list` prüfen, Parallel-Sessions!)

- [ ] **Step 1: Backend komplett** `cd backend && npm run build && npm test` — PASS.
- [ ] **Step 2: Server-Deploy** `cli/hikari restart`; danach Smoke:
  - `curl -s localhost:3939/feed?mode=new | python3 -m json.tool | head -40` — Items mit `kind` short/video, `summary`-Feld vorhanden.
  - Poll-Durchlauf abwarten oder Kanal manuell enqueuen; prüfen, dass neue Videos OHNE `downloaded_videos`-Row im Feed auftauchen und `/stream/video/<id>` 206 liefert.
- [ ] **Step 3: App bauen + manueller Test** (Emulator/Gerät): Feed öffnen → Shorts spielen beim Swipen, Langvideo-Karte erscheint, Tap öffnet Player (streamt), Karte-wegswipen markiert als gesehen, gespeicherte alte Clips unter „Gespeichert" weiter abspielbar.
- [ ] **Step 4: Version bumpen** (nächste freie Minor, versionCode +1), committen, `git push`, `gh release create v<X> --notes ...` im Stil der Vorgänger-Releases, CI-APK abwarten (`gh run watch`). Dem User erst danach berichten (Release-Prozess).
