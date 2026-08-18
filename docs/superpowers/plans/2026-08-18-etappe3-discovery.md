# Etappe 3: Discovery — Implementierungsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Der Feed füllt sich auch ohne neue Abo-Uploads: Probe-Kanäle (aus den Kanal-Empfehlungen), Themen-Videosuche (über die bestehenden likeTags) und Backfill älterer Abo-Videos liefern Kandidaten — alle durch den unveränderten Scorer; die App zeigt Discovery-Inhalte mit „Neu für dich"-Badge und Ein-Tap-Abonnieren/Blocken.

**Architecture:** Neues Modul `discovery/feed-sources.ts` orchestriert die drei Quellen in einem Cron-Zyklus und enqueued nur IDs (die bestehende Pipeline erledigt Metadaten/Transkript/Scoring). `channels.status` unterscheidet subscribed/probe/blocked (Probe-Kanäle: `is_active=0`, damit der RSS-Poller sie ignoriert); `ingest_queue.source` transportiert die Quelle bis in `videos.source`. Feed-API reicht `source` durch; die App rendert Badge + Aktionen.

**Tech Stack:** Fastify + TypeScript (ESM, `.js`-Imports), better-sqlite3, Vitest; Android: Kotlin/Compose, Room 17, Retrofit-artiges HikariApi.

**Spec:** `docs/superpowers/specs/2026-08-18-feed-streaming-overhaul-design.md` §4.4/§5/§6 — **bewusste Abweichung:** keine eigene `interest_topics`-Tabelle/API; die Themen-Suche nutzt `filter.likeTags` („Themen die du magst" im Tuning-Tab existiert bereits und treibt schon die Kanal-Empfehlungen — eine Themenliste statt zwei).

## Global Constraints

- Backend-Tests/Build NUR mit System-Node: `cd /Users/ayysir/Desktop/Hikari/backend && npm test` bzw. `npm run build`. Biome, deutsche Kommentare.
- ESM: relative Imports enden auf `.js`.
- Alle Discovery-Kandidaten laufen durch den unveränderten Scorer (kein Auto-Approve für probe/topic/backfill; Green-Card ist Kanal-Eigenschaft und bleibt bei Probe-Kanälen aus, da `auto_approve=0`-Default).
- Drosseln als Konstanten in `feed-sources.ts`: `PROBE_POOL_MAX = 8` (gleichzeitige Probe-Kanäle), `PROBE_PER_CYCLE = 3` (Videos je Probe-Kanal je Lauf), `TOPIC_PER_TAG = 5`, `BACKFILL_MAX = 10` (gesamt je Lauf).
- UI: keine Emojis, Amber nur Akzent; Badge-Text „Neu für dich".
- Parallel-Session (`hikari-ef`) arbeitet an Musik-UI und released v0.61.0 — vor Release `git fetch --tags` + `gh release list`; deren Dateien (Music*, DiscoverComponents, MusicScreen, PlaylistDetailScreen, build.gradle.kts) nicht anfassen.

**Empirisch verifiziert (2026-08-18):** Innertube-WEB-`search` mit `params: "EgIQAQ%3D%3D"` (nur Videos) liefert `videoRenderer`-Items mit `videoId`/`title`/`lengthText`.

---

### Task 1: Migration — `channels.status` + `ingest_queue.source`

**Files:**
- Modify: `backend/src/db/migrations.ts` (ans Ende), `backend/src/db/schema.sql` (CREATE TABLE channels + ingest_queue)
- Test: `backend/src/db/migrations.test.ts`

**Interfaces:**
- Produces: `channels.status TEXT` (`'subscribed'|'probe'|'blocked'`; Bestand: aktive → `'subscribed'`), `ingest_queue.source TEXT` (Default-Bedeutung `'subscription'`, nullable — NULL wird als subscription gelesen).

- [ ] **Step 1: Failing Test** (an `migrations.test.ts` anhängen):

```ts
it("channels.status backfilled auf subscribed, ingest_queue hat source", () => {
  const db = new Database(":memory:");
  applyMigrations(db);
  db.prepare("INSERT INTO channels (id,url,title,added_at,is_active) VALUES ('c1','x','c',0,1)").run();
  db.prepare("UPDATE channels SET status = NULL").run();
  applyMigrations(db);
  expect(db.prepare("SELECT status FROM channels WHERE id='c1'").get()).toEqual({ status: "subscribed" });
  const cols = (db.prepare("PRAGMA table_info(ingest_queue)").all() as { name: string }[]).map((c) => c.name);
  expect(cols).toContain("source");
});
```

- [ ] **Step 2: Run** `npx vitest run src/db/migrations.test.ts` — FAIL.
- [ ] **Step 3: Implementierung** — `schema.sql`: `status TEXT` in channels, `source TEXT` in ingest_queue; `migrations.ts` ans Ende:

```ts
// Etappe 3 (Discovery): Kanal-Status + Ingest-Quelle.
addColumnIfMissing(db, "channels", "status", "TEXT"); // 'subscribed' | 'probe' | 'blocked'
addColumnIfMissing(db, "ingest_queue", "source", "TEXT"); // 'subscription' | 'probe' | 'topic' | 'backfill'
// Bestand: alle aktiven Kanäle sind Abos; inaktive bleiben statuslos (weiches Löschen).
db.exec("UPDATE channels SET status = 'subscribed' WHERE status IS NULL AND is_active = 1");
```

- [ ] **Step 4: Run** Migrations-Tests + `npm test` — PASS.
- [ ] **Step 5: Commit** `git add backend/src/db && git commit -m "feat(db): channels.status + ingest_queue.source"`

---

### Task 2: `itVideoSearch` (Innertube-Videosuche)

**Files:**
- Modify: `backend/src/api/music-innertube.ts` (neben `itChannelShorts`)
- Test: `backend/tests/api/innertube-video-search.test.ts` (neu)

**Interfaces:**
- Consumes: vorhandene Helfer `findAllByKey`, `VIDEO_ID_RE`; WEB-Kontext-Muster von `webBrowse` (eigener POST an `/youtubei/v1/search`).
- Produces: `export async function itVideoSearch(fetchImpl: typeof fetch, query: string, max?: number): Promise<string[] | undefined>` — Video-IDs (Default max 20), `undefined` bei Fehler/leer, wirft nie.

- [ ] **Step 1: Failing Test:**

```ts
import { expect, test } from "vitest";
import { itVideoSearch } from "../../src/api/music-innertube.js";

const body = {
  contents: [
    { videoRenderer: { videoId: "aaaaaaaaaaa" } },
    { videoRenderer: { videoId: "bbbbbbbbbbb" } },
    { videoRenderer: { videoId: "aaaaaaaaaaa" } },
    { videoRenderer: { videoId: "nix" } },
  ],
};
const okFetch = (async () => new Response(JSON.stringify(body), { status: 200 })) as typeof fetch;

test("liefert deduplizierte Video-IDs, respektiert max", async () => {
  expect(await itVideoSearch(okFetch, "weltraum")).toEqual(["aaaaaaaaaaa", "bbbbbbbbbbb"]);
  expect(await itVideoSearch(okFetch, "weltraum", 1)).toEqual(["aaaaaaaaaaa"]);
});

test("Fehler/leer ⇒ undefined", async () => {
  const failFetch = (async () => new Response("x", { status: 500 })) as typeof fetch;
  expect(await itVideoSearch(failFetch, "q")).toBeUndefined();
  const emptyFetch = (async () => new Response("{}", { status: 200 })) as typeof fetch;
  expect(await itVideoSearch(emptyFetch, "q")).toBeUndefined();
});
```

- [ ] **Step 2: Run** — FAIL (Export fehlt).
- [ ] **Step 3: Implementierung** (unter `itChannelShorts`; `WEB_CONTEXT`/UA-Konstanten der Datei wiederverwenden — Suchanker `webBrowse`):

```ts
// Nur-Videos-Filter der YouTube-Suche (base64-Protobuf, empirisch verifiziert 2026-08).
const WEB_VIDEO_SEARCH_PARAMS = "EgIQAQ%3D%3D";

/** Video-IDs der YouTube-Suche — für die Themen-Discovery (likeTags → Kandidaten). */
export async function itVideoSearch(
  fetchImpl: typeof fetch,
  query: string,
  max = 20,
): Promise<string[] | undefined> {
  try {
    const res = await fetchImpl("https://www.youtube.com/youtubei/v1/search?prettyPrint=false", {
      method: "POST",
      headers: {
        "content-type": "application/json",
        origin: "https://www.youtube.com",
        "user-agent": WEB_UA,
      },
      body: JSON.stringify({ context: WEB_CONTEXT, query, params: WEB_VIDEO_SEARCH_PARAMS }),
      signal: AbortSignal.timeout(IT_TIMEOUT_MS),
    });
    if (!res.ok) return undefined;
    const body = (await res.json()) as unknown;
    const ids: string[] = [];
    const seen = new Set<string>();
    for (const vr of findAllByKey(body, "videoRenderer")) {
      const id = (vr as { videoId?: unknown }).videoId;
      if (typeof id !== "string" || !VIDEO_ID_RE.test(id) || seen.has(id)) continue;
      seen.add(id);
      ids.push(id);
      if (ids.length >= max) break;
    }
    return ids.length > 0 ? ids : undefined;
  } catch {
    return undefined;
  }
}
```

Hinweis: Existiert keine `WEB_UA`-Konstante, den UA-String aus `webBrowse` in eine Konstante ziehen und an beiden Stellen nutzen; `WEB_CONTEXT` heißt in der Datei ggf. anders — Anker: das `context`-Objekt mit `clientName: "WEB"`.

- [ ] **Step 4: Run** neue Tests + `npm test` — PASS.
- [ ] **Step 5: Commit** `git add backend/src/api/music-innertube.ts backend/tests/api/innertube-video-search.test.ts && git commit -m "feat(discovery): itVideoSearch — Themen-Videosuche via Innertube"`

---

### Task 3: `source` durch die Pipeline

**Files:**
- Modify: `backend/src/ingest/queue.ts` (`enqueueIngest`, `IngestQueueRow`), `backend/src/pipeline/orchestrator.ts` (Deps + `insertVideo`), `backend/src/index.ts` (drain reicht `job.source` durch)
- Test: `backend/src/ingest/queue.test.ts`, `backend/src/pipeline/orchestrator.test.ts`

**Interfaces:**
- Produces: `enqueueIngest(db, videoId, channelId, source?: string)` (Default `'subscription'`); `IngestQueueRow.source: string | null`; `ProcessNewVideoDeps.source?: string | undefined` — `insertVideo` schreibt `deps.source ?? "subscription"` statt des Festwerts.

- [ ] **Step 1: Failing Tests:**

```ts
// queue.test.ts
it("enqueueIngest speichert die Quelle (Default subscription)", () => {
  enqueueIngest(db, "v1", "c1");
  enqueueIngest(db, "v2", "c1", "probe");
  const rows = db.prepare("SELECT video_id, source FROM ingest_queue ORDER BY video_id").all();
  expect(rows).toEqual([
    { video_id: "v1", source: "subscription" },
    { video_id: "v2", source: "probe" },
  ]);
});
// orchestrator.test.ts
it("source-Dep landet in videos.source", async () => {
  await processNewVideo(baseDeps({ source: "topic" }));
  expect(db.prepare("SELECT source FROM videos WHERE id='vid1'").get()).toEqual({ source: "topic" });
});
```

- [ ] **Step 2: Run** — FAIL.
- [ ] **Step 3: Implementierung** — `enqueueIngest`: `source`-Param + Spalte im INSERT (`INSERT OR IGNORE` behält bestehende Rows ✓); `claimNextIngest`-SELECT um `source` erweitern; Orchestrator: `source?: string | undefined` in Deps, `insertVideo(db, meta, transcript, channelId, format, summary, deps.source ?? "subscription")` (Signatur erweitern, alle 4 Callsites); `index.ts` drain: `source: job.source ?? undefined`.
- [ ] **Step 4: Run** `npm run build && npm test` — PASS.
- [ ] **Step 5: Commit** `git add backend/src/ingest backend/src/pipeline backend/src/index.ts && git commit -m "feat(pipeline): Ingest-Quelle bis videos.source durchgereicht"`

---

### Task 4: `discovery/feed-sources.ts` — der Discovery-Zyklus

**Files:**
- Create: `backend/src/discovery/feed-sources.ts`
- Modify: `backend/src/index.ts` (Cron `30 5,17 * * *`)
- Test: `backend/src/discovery/feed-sources.test.ts` (neu)

**Interfaces:**
- Consumes: `recommendChannels(db, opts)` → `RecommendationResult[]` (mit `channelId`, `channelUrl`, `title`, `thumbnail`, `subscribers`); `itChannelVideos(fetch, id)` → `MusicTrack[] | undefined`; `itChannelShorts(fetch, id)` → `string[] | undefined`; `itVideoSearch(fetch, q, max)`; `getFilterState(db).filter.likeTags`; `enqueueIngest(db, videoId, channelId, source)` aus Task 3.
- Produces:

```ts
export interface FeedSourceDeps {
  recommend?: typeof recommendChannels;
  channelVideos?: typeof itChannelVideos;
  channelShorts?: typeof itChannelShorts;
  videoSearch?: typeof itVideoSearch;
  dailyBudget?: number; // Backfill-Schwelle, Default 15
}
export async function runDiscoveryCycle(db: Database.Database, deps?: FeedSourceDeps): Promise<void>
```

Ablauf (jede Phase best-effort in eigenem try/catch):
1. **Probe-Refresh:** Aktive Probe-Anzahl < `PROBE_POOL_MAX` → `recommend(db)`-Top-Kandidaten, die es in `channels` noch NICHT gibt, als `INSERT INTO channels (id, url, title, added_at, is_active, status, thumbnail_url, subscribers) VALUES (?,?,?,?,0,'probe',?,?)` auffüllen.
2. **Probe-Ingest:** je `status='probe'`-Kanal: `channelShorts`-IDs + `channelVideos`-IDs (in dieser Reihenfolge gemischt), Videos die es in `videos` noch nicht gibt, max `PROBE_PER_CYCLE` → `enqueueIngest(db, id, kanalId, "probe")`.
3. **Themen:** `likeTags` je Tag `videoSearch(fetch, tag, 20)` → unbekannte IDs, max `TOPIC_PER_TAG` → `enqueueIngest(..., "topic")` — als channelId den Sammelkanal `'manual'`? NEIN: die echte Kanal-Id ist unbekannt (Suche liefert nur Video-IDs) — `enqueueIngest` braucht eine channels-FK. Lösung: Pseudo-Kanal `discovery-topics` einmalig anlegen (`INSERT OR IGNORE INTO channels (id,url,title,added_at,is_active,status) VALUES ('discovery-topics','','Themen-Entdeckung',?,0,'probe')`); `fetchVideoMetadata` liefert später zwar den echten Uploader nicht als Kanal-Row — akzeptiert für Etappe 3 (Anzeige nutzt channelTitle aus JOIN → 'Themen-Entdeckung'). Kommentar im Code, Verbesserung (echten Kanal nachziehen) ist Etappe-4-Kandidat.
4. **Backfill:** `SELECT COUNT(*) FROM feed_items WHERE seen_at IS NULL AND playback_failed=0 AND is_pre_clipper=1` < `dailyBudget` → je `is_active=1`-Kanal `channelVideos`-IDs, unbekannte sammeln, insgesamt max `BACKFILL_MAX` → `enqueueIngest(..., "backfill")`.

- [ ] **Step 1: Failing Tests** (Fake-Deps; DB via applyMigrations + Seeds):

```ts
import Database from "better-sqlite3";
import { beforeEach, describe, expect, it } from "vitest";
import { applyMigrations } from "../db/migrations.js";
import { runDiscoveryCycle } from "./feed-sources.js";

describe("runDiscoveryCycle", () => {
  let db: Database.Database;
  beforeEach(() => {
    db = new Database(":memory:");
    applyMigrations(db);
    db.prepare("INSERT INTO channels (id,url,title,added_at,is_active,status) VALUES ('UC-abo','x','Abo',0,1,'subscribed')").run();
  });
  const noRecommend = (async () => []) as never;

  it("legt Probe-Kanäle aus Empfehlungen an (is_active=0, status probe)", async () => {
    const recommend = (async () => [
      { channelId: "UC-neu", channelUrl: "u", title: "Neu", handle: null, description: null, subscribers: 5, thumbnail: "t", banner: null },
    ]) as never;
    await runDiscoveryCycle(db, { recommend, channelVideos: async () => undefined, channelShorts: async () => undefined, videoSearch: async () => undefined });
    expect(db.prepare("SELECT is_active, status FROM channels WHERE id='UC-neu'").get())
      .toEqual({ is_active: 0, status: "probe" });
  });

  it("enqueued max PROBE_PER_CYCLE unbekannte Videos je Probe-Kanal mit source probe", async () => {
    db.prepare("INSERT INTO channels (id,url,title,added_at,is_active,status) VALUES ('UC-p','x','P',0,0,'probe')").run();
    const channelShorts = (async () => ["sssssssssss"]) as never;
    const channelVideos = (async () => ["v0000000001","v0000000002","v0000000003","v0000000004"].map(
      (videoId) => ({ videoId, title: "t", uploader: "P", thumbnailUrl: "", durationSeconds: 60 }),
    )) as never;
    await runDiscoveryCycle(db, { recommend: noRecommend, channelShorts, channelVideos, videoSearch: async () => undefined });
    const rows = db.prepare("SELECT video_id, source FROM ingest_queue WHERE channel_id='UC-p'").all() as { video_id: string }[];
    expect(rows).toHaveLength(3); // PROBE_PER_CYCLE
    expect(rows[0]).toMatchObject({ video_id: "sssssssssss", source: "probe" }); // Shorts zuerst
  });

  it("Themen-Suche enqueued unter dem Pseudo-Kanal mit source topic", async () => {
    db.prepare("UPDATE filter_config SET config = json_set(config, '$.likeTags', json('[\"weltraum\"]')) WHERE id = 1").run();
    const videoSearch = (async () => ["ttttttttttt"]) as never;
    await runDiscoveryCycle(db, { recommend: noRecommend, channelVideos: async () => undefined, channelShorts: async () => undefined, videoSearch });
    expect(db.prepare("SELECT channel_id, source FROM ingest_queue WHERE video_id='ttttttttttt'").get())
      .toEqual({ channel_id: "discovery-topics", source: "topic" });
  });

  it("Backfill nur wenn unseen unter dailyBudget; source backfill", async () => {
    const channelVideos = (async () => [{ videoId: "bfbfbfbfbfb", title: "t", uploader: "A", thumbnailUrl: "", durationSeconds: 600 }]) as never;
    await runDiscoveryCycle(db, { recommend: noRecommend, channelVideos, channelShorts: async () => undefined, videoSearch: async () => undefined, dailyBudget: 15 });
    expect(db.prepare("SELECT source FROM ingest_queue WHERE video_id='bfbfbfbfbfb'").get())
      .toEqual({ source: "backfill" });
  });
});
```

(Vorher prüfen: exakte Struktur von `filter_config` (Spalte `config`? `json`?) für den likeTags-Seed — an `scorer/filter-repo.ts` orientieren; Test ggf. anpassen. `getFilterState` heißt evtl. anders — Anker: die Funktion, die `filter.likeTags` liefert, siehe `monitor/recommendations.ts` Import.)

- [ ] **Step 2: Run** — FAIL.
- [ ] **Step 3: Implementierung** von `runDiscoveryCycle` gemäß Ablauf oben; Konstanten am Dateikopf; `index.ts`: Import + `cron.schedule("30 5,17 * * *", () => { runDiscoveryCycle(db, { dailyBudget: cfg.dailyBudget }).catch((err) => app.log.error({ err }, "discovery cycle crashed")); });`
- [ ] **Step 4: Run** `npm run build && npm test` — PASS.
- [ ] **Step 5: Commit** `git add backend/src/discovery backend/src/index.ts && git commit -m "feat(discovery): Probe-Kanäle, Themen-Suche, Backfill — Discovery-Zyklus"`

---

### Task 5: Kanal-APIs — Abonnieren/Blocken + Empfehlungs-Ausschluss

**Files:**
- Modify: `backend/src/api/channels.ts` (zwei neue Routen), `backend/src/monitor/recommendations.ts` (excludedIds)
- Test: `backend/tests/api/channels-status.test.ts` (neu)

**Interfaces:**
- Produces: `POST /channels/:id/subscribe` → 204 (`status='subscribed', is_active=1`; 404 wenn unbekannt); `POST /channels/:id/block` → 204 (`status='blocked', is_active=0` + alle ungesehenen feed_items des Kanals bekommen `seen_at=now`); `recommendChannels` schließt zusätzlich `status IN ('probe','blocked')` aus.

- [ ] **Step 1: Failing Tests:**

```ts
import Database from "better-sqlite3";
import Fastify from "fastify";
import { beforeEach, describe, expect, it } from "vitest";
import { applyMigrations } from "../../src/db/migrations.js";
import { registerChannelsRoutes } from "../../src/api/channels.js";

describe("channel status routes", () => {
  let db: Database.Database;
  beforeEach(() => {
    db = new Database(":memory:");
    applyMigrations(db);
    db.prepare("INSERT INTO channels (id,url,title,added_at,is_active,status) VALUES ('UC-p','x','P',0,0,'probe')").run();
  });
  async function app() {
    const a = Fastify();
    await registerChannelsRoutes(a, { db });
    return a;
  }

  it("subscribe macht aus Probe ein Abo", async () => {
    const res = await (await app()).inject({ method: "POST", url: "/channels/UC-p/subscribe" });
    expect(res.statusCode).toBe(204);
    expect(db.prepare("SELECT is_active, status FROM channels WHERE id='UC-p'").get())
      .toEqual({ is_active: 1, status: "subscribed" });
  });

  it("block deaktiviert den Kanal und räumt seine ungesehenen Feed-Items weg", async () => {
    db.prepare("INSERT INTO videos (id, channel_id, title, published_at, duration_seconds, discovered_at) VALUES ('v1','UC-p','t',0,60,0)").run();
    db.prepare("INSERT INTO feed_items (video_id, added_to_feed_at, is_pre_clipper) VALUES ('v1', 0, 1)").run();
    const res = await (await app()).inject({ method: "POST", url: "/channels/UC-p/block" });
    expect(res.statusCode).toBe(204);
    expect(db.prepare("SELECT status FROM channels WHERE id='UC-p'").get()).toEqual({ status: "blocked" });
    const fi = db.prepare("SELECT seen_at FROM feed_items WHERE video_id='v1'").get() as { seen_at: number | null };
    expect(fi.seen_at).toBeGreaterThan(0);
  });

  it("unbekannter Kanal ⇒ 404", async () => {
    const res = await (await app()).inject({ method: "POST", url: "/channels/UC-nix/subscribe" });
    expect(res.statusCode).toBe(404);
  });
});
```

- [ ] **Step 2: Run** — FAIL.
- [ ] **Step 3: Implementierung** in `channels.ts` (neben dem auto-approve-PATCH):

```ts
app.post<{ Params: { id: string } }>("/channels/:id/subscribe", async (req, reply) => {
  const row = deps.db.prepare("SELECT 1 FROM channels WHERE id = ?").get(req.params.id);
  if (!row) return reply.code(404).send({ error: "channel not found" });
  deps.db
    .prepare("UPDATE channels SET status = 'subscribed', is_active = 1 WHERE id = ?")
    .run(req.params.id);
  return reply.code(204).send();
});

app.post<{ Params: { id: string } }>("/channels/:id/block", async (req, reply) => {
  const row = deps.db.prepare("SELECT 1 FROM channels WHERE id = ?").get(req.params.id);
  if (!row) return reply.code(404).send({ error: "channel not found" });
  deps.db.transaction(() => {
    deps.db
      .prepare("UPDATE channels SET status = 'blocked', is_active = 0 WHERE id = ?")
      .run(req.params.id);
    // Geblockter Kanal verschwindet sofort aus dem Feed — weich (→ "Alt"), kein Löschen.
    deps.db
      .prepare(
        `UPDATE feed_items SET seen_at = ? WHERE seen_at IS NULL AND video_id IN
         (SELECT id FROM videos WHERE channel_id = ?)`,
      )
      .run(Date.now(), req.params.id);
  })();
  return reply.code(204).send();
});
```

`recommendations.ts`: die `subscribedIds`-Query auf `SELECT id FROM channels WHERE is_active = 1 OR status IN ('probe','blocked')` erweitern (Variable in `excludedIds` umbenennen).

- [ ] **Step 4: Run** `npm test` — PASS.
- [ ] **Step 5: Commit** `git add backend/src/api/channels.ts backend/src/monitor/recommendations.ts backend/tests/api/channels-status.test.ts && git commit -m "feat(channels): subscribe/block — Probe wird Abo, Block räumt Feed"`

---

### Task 6: Feed-API liefert `source`

**Files:**
- Modify: `backend/src/api/feed.ts` (Hydration-Query + BASE_SELECT: `v.source AS source`)
- Test: `backend/src/api/feed.test.ts`

**Interfaces:**
- Produces: Feed-Items tragen `source: string | null` (App-Badge-Grundlage).

- [ ] **Step 1: Failing Test:**

```ts
it("liefert source im Feed-Item durch", async () => {
  seedFeedItem(db, "src-v", Date.now());
  db.prepare("UPDATE videos SET source = 'probe' WHERE id = 'src-v'").run();
  const app = Fastify();
  await registerFeedRoutes(app, { db, dailyBudget: 15 });
  const body = (await app.inject({ method: "GET", url: "/feed?mode=new" })).json() as { videoId: string; source: string }[];
  expect(body.find((x) => x.videoId === "src-v")?.source).toBe("probe");
});
```

- [ ] **Step 2: Run** — FAIL. **Step 3:** `v.source AS source` in Hydration-SELECT und BASE_SELECT ergänzen. **Step 4: Run** `npm test` — PASS.
- [ ] **Step 5: Commit** `git add backend/src/api/feed.ts backend/src/api/feed.test.ts && git commit -m "feat(feed): source im Feed-Item"`

---

### Task 7: App-Datenmodell — `source` + Room 17 + API-Methoden

**Files:**
- Modify: `android/.../data/api/dto/FeedItemDto.kt` (+`source: String? = null`), `data/db/FeedItemEntity.kt` (+`@ColumnInfo(defaultValue = "NULL") val source: String? = null`), `domain/model/FeedItem.kt` (+`val source: String? = null` ans ENDE), `domain/repo/FeedRepository.kt` (3 Mapper + 2 Methoden), `data/api/HikariApi.kt`, `data/db/HikariDatabase.kt` (version 17 + `MIGRATION_16_17`), `di/DatabaseModule.kt` (Registrierung + Import)

**Interfaces:**
- Produces: `FeedItem.source: String?`; `HikariApi`: `@POST("channels/{id}/subscribe") suspend fun subscribeChannel(@Path("id") id: String)` und `@POST("channels/{id}/block") suspend fun blockChannel(@Path("id") id: String)`; `FeedRepository.subscribeChannel(channelId)` / `blockChannel(channelId)` (Letzteres ruft danach `refresh()`).

- [ ] **Step 1: Mechanische Änderungen** in allen 7 Dateien; Migration:

```kotlin
// Etappe 3 (Discovery): Quelle des Feed-Items fuer das "Neu fuer dich"-Badge.
val MIGRATION_16_17 = object : Migration(16, 17) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE feed_items ADD COLUMN source TEXT DEFAULT NULL")
    }
}
```

ACHTUNG Parallel-Session: aktuelle Room-`version` VOR dem Bump prüfen (`grep version data/db/HikariDatabase.kt`) — ist sie inzwischen 17, wird meine Migration 17→18 usw.; Registrierung in `DatabaseModule` + Import nicht vergessen. `HikariApi`-Annotationsstil an bestehende `@POST("feed/{id}/seen")` anlehnen.

- [ ] **Step 2: Kompilieren** `cd android && ./gradlew :app:compileDebugKotlin` — BUILD SUCCESSFUL.
- [ ] **Step 3: Commit** `git add android/app/src/main/java/com/hikari/app && git commit -m "feat(app): source-Feld, Room 17, subscribe/block-API"`

---

### Task 8: App-UI — Badge „Neu für dich" + Ein-Tap-Abo/Block

**Files:**
- Create: `android/.../ui/feed/DiscoveryActions.kt`
- Modify: `ui/feed/LongVideoCard.kt`, `ui/feed/ReelPlayer.kt` (kleine Einfügung im unteren Chrome), `ui/feed/FeedViewModel.kt`, `ui/feed/FeedScreen.kt` (Callbacks durchreichen)

**Interfaces:**
- Produces:

```kotlin
// DiscoveryActions.kt — Badge + Aktionszeile, nur gerendert wenn item.source in probe/topic.
@Composable
fun DiscoveryActions(
    item: FeedItem,
    onSubscribe: () -> Unit,
    onBlock: () -> Unit,
    modifier: Modifier = Modifier,
)
```

Inhalt: Amber-umrandete „Neu für dich"-Pill + zwei Text-Buttons „Kanal abonnieren" / „Nicht mehr zeigen" (HikariTextFaint, Abstände wie Nachbar-Chrome; bei `source == "topic"` nur das Badge und „Nicht mehr zeigen" — es gibt keinen echten Kanal zum Abonnieren, channelId ist der Pseudo-Kanal).

FeedViewModel:

```kotlin
fun onSubscribeChannel(channelId: String) = viewModelScope.launch {
    runCatching { repo.subscribeChannel(channelId) }
}
fun onBlockChannel(channelId: String) = viewModelScope.launch {
    runCatching { repo.blockChannel(channelId) } // Repository refresht danach — Items des Kanals verschwinden
}
```

- [ ] **Step 1: DiscoveryActions.kt schreiben**, in `LongVideoCard` zwischen Kanalzeile und Teaser einfügen (`if (item.source == "probe" || item.source == "topic")`), in `ReelPlayer` an der Stelle der Kanal-Anzeige (Suchanker: channelTitle-Text im unteren Overlay) mit denselben Bedingungen einhängen; `FeedScreen` reicht `onSubscribe = { vm.onSubscribeChannel(item.channelId) }` / `onBlock = { vm.onBlockChannel(item.channelId) }` in beide.
- [ ] **Step 2: Kompilieren** — BUILD SUCCESSFUL.
- [ ] **Step 3: Commit** `git add android/app/src/main/java/com/hikari/app/ui/feed && git commit -m "feat(app): Neu-für-dich-Badge + Ein-Tap-Abo/Block"`

---

### Task 9: Gesamtverifikation, Deploy, Release-Koordination

- [ ] **Step 1:** `cd backend && npm run build && npm test` (alles PASS) + `cd android && ./gradlew :app:compileDebugKotlin`.
- [ ] **Step 2:** `cli/hikari restart`; Smoke: `runDiscoveryCycle` einmal manuell anstoßen (`node --input-type=module -e "..."` gegen dist mit echter DB — oder auf den 17:30-Cron warten); danach `sqlite3`: Probe-Kanäle vorhanden (`SELECT id,status FROM channels WHERE status='probe'`), Queue-Rows mit source probe/topic; `GET /feed` zeigt (nach Scoring, LM Studio nötig!) Items mit `source`.
- [ ] **Step 3:** Peer-Session (`hikari-ef`) fragen, ob v0.61.0 schon getaggt ist: meine App-Commits fahren entweder dort mit (Notes ergänzen) oder ich release v0.62.0 selbst (`git fetch --tags`, nächste freie Nummer, versionCode +1, Notes im Hausstil, `gh run watch` bis CI-APK da). Dem User erst danach berichten.
