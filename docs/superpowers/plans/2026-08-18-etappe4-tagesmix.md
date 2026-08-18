# Etappe 4: Tagesmix mit Zeitbudget — Implementierungsplan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Der Feed wird ein täglich kuratierter, STABILER Mix mit Zeitbudget (Default 45 min) statt einer endlosen Kandidatenliste — mit serverseitig erzwungenem Ende, Restdauer-Anzeige und einem bewussten Abschluss-Screen in der App.

**Architecture:** Neues Modul `feed/daily-mix.ts` baut den Tagesmix idempotent auf (Top-up bei jedem Aufruf): Kandidaten nach Quellen-Priorität (subscription → probe → topic → backfill) und bestehendem Ranking, verwoben im Rhythmus ~5 Shorts : 1 Langvideo, aufgenommen bis die Dauersumme das Budget erreicht. `GET /feed?mode=new` liefert nur noch die ungesehenen Items des heutigen Mixes in fixer Reihenfolge (lazy Build beim ersten Abruf); `today-count` meldet Restdauer. Das Budget lebt in der neuen Singleton-Tabelle `feed_settings` (Tuning-Regler in Minuten, GET/PUT `/feed/budget`).

**Tech Stack:** Fastify + TypeScript (ESM, `.js`-Imports), better-sqlite3, Vitest; Android: Kotlin/Compose (kein Room-Bump nötig — Abschluss-Screen und Budget sind serverseitig).

**Spec:** `docs/superpowers/specs/2026-08-18-feed-streaming-overhaul-design.md` §4.5/§4.6/§5/§6. Abweichungen: (a) Budget-Speicher ist die neue Tabelle `feed_settings` statt einer Erweiterung von `discovery_settings` (die hat fachfremde NOT-NULL-Spalten); (b) der bisherige lokale „Tagesbudget"-Regler (App-DataStore, Stückzahl fürs Scoring) wird durch den Minuten-Regler ERSETZT — zwei Budget-Konzepte parallel wären verwirrend; (c) `deps.dailyBudget` (Stückzahl) bleibt intern als Backfill-Schwelle der Discovery bestehen (Etappe-3-Mechanik, unabhängig vom Zeitbudget).

## Global Constraints

- Backend-Tests/Build NUR mit System-Node: `cd /Users/ayysir/Desktop/Hikari/backend && npm test` / `npm run build`. Biome, deutsche Kommentare, `.js`-Imports.
- Mix-Datum ist der LOKALE Kalendertag: `new Date(now).toLocaleDateString("sv-SE")` → `YYYY-MM-DD` (sv-SE liefert ISO-Format in lokaler Zeit).
- Budget-Regeln: Items werden aufgenommen, solange die Dauersumme des heutigen Mixes UNTER dem Budget liegt (das letzte Item darf überziehen — sonst bliebe der Mix bei langen Videos leer). Gesehene Mix-Items zählen weiter gegen das Budget (konsumiert ist konsumiert).
- Rhythmus: `SHORTS_PER_LONG = 5` — nach je 5 Shorts eine Langvideo-Karte; ist ein Pool leer, läuft der andere weiter.
- Budget-Clamp: 10–240 Minuten, Default 45.
- UI: keine Emojis, Amber nur Akzent. Abschluss-Screen ruhig und wertschätzend, kein Guilt-Tripping.
- Parallel-Sessions: vor Versionsbump/Release `git fetch --tags` + `gh release list`; Musik-Dateien und `build.gradle.kts` nur für den eigenen Bump anfassen.

---

### Task 1: Migration — `daily_mix_items` + `feed_settings`

**Files:**
- Modify: `backend/src/db/schema.sql` (zwei neue CREATE TABLE), `backend/src/db/migrations.ts` (kein addColumn nötig — neue Tabellen kommen über schema.sql)
- Test: `backend/src/db/migrations.test.ts` (ACHTUNG: der erste Test asserted die EXAKTE sortierte Tabellenliste — beide neuen Namen dort einsortieren!)

**Interfaces:**
- Produces:

```sql
CREATE TABLE IF NOT EXISTS daily_mix_items (
  mix_date TEXT NOT NULL,           -- lokaler Kalendertag YYYY-MM-DD
  video_id TEXT NOT NULL REFERENCES videos(id),
  position INTEGER NOT NULL,        -- Reihenfolge im Tagesmix
  source TEXT,                      -- Quelle zum Bau-Zeitpunkt (Anzeige/Debug)
  duration_seconds INTEGER NOT NULL,
  PRIMARY KEY (mix_date, video_id)
);
CREATE INDEX IF NOT EXISTS idx_daily_mix_date ON daily_mix_items(mix_date, position);

CREATE TABLE IF NOT EXISTS feed_settings (
  id INTEGER PRIMARY KEY CHECK (id = 1),
  daily_time_budget_minutes INTEGER NOT NULL DEFAULT 45,
  updated_at INTEGER NOT NULL
);
```

- [ ] **Step 1: Failing Test** — in `migrations.test.ts`: `"daily_mix_items"` und `"feed_settings"` in das erwartete Tabellen-Array einsortieren (alphabetisch: `daily_mix_items` nach `clips`, `feed_settings` nach `feed_items`) + Mini-Test:

```ts
it("daily_mix_items und feed_settings existieren mit erwarteten Spalten", () => {
  const db = new Database(":memory:");
  applyMigrations(db);
  const mixCols = (db.prepare("PRAGMA table_info(daily_mix_items)").all() as { name: string }[]).map((c) => c.name);
  expect(mixCols).toEqual(expect.arrayContaining(["mix_date", "video_id", "position", "source", "duration_seconds"]));
  const fsCols = (db.prepare("PRAGMA table_info(feed_settings)").all() as { name: string }[]).map((c) => c.name);
  expect(fsCols).toEqual(expect.arrayContaining(["daily_time_budget_minutes"]));
});
```

- [ ] **Step 2: Run** `npx vitest run src/db/migrations.test.ts` — FAIL (Tabellen fehlen; Tabellenlisten-Test schlägt auch fehl, solange schema.sql sie nicht hat).
- [ ] **Step 3:** Beide CREATE-Blöcke in `schema.sql` einfügen (bei den anderen Feed-Tabellen).
- [ ] **Step 4: Run** Migrations- + Vollsuite — PASS.
- [ ] **Step 5: Commit** `git add backend/src/db && git commit -m "feat(db): daily_mix_items + feed_settings"`

---

### Task 2: `feed/daily-mix.ts` — Builder, Budget, Stats

**Files:**
- Create: `backend/src/feed/daily-mix.ts`
- Test: `backend/src/feed/daily-mix.test.ts` (neu)

**Interfaces:**
- Consumes: `rankCandidates`, `interleaveByChannel` aus `../api/feed.js` (beide exportiert); Tabellen aus Task 1.
- Produces:

```ts
export function getTimeBudgetMinutes(db: Database.Database): number;            // Default 45, seeded on read
export function setTimeBudgetMinutes(db: Database.Database, minutes: number): number; // clamped 10–240, returns effektiven Wert
export function mixDateFor(now: number): string;                                 // lokales YYYY-MM-DD
export function buildDailyMix(db: Database.Database, now?: number): void;        // idempotenter Top-up
export interface TodayMixStats {
  budgetMinutes: number;
  totalSeconds: number;      // Dauersumme ALLER heutigen Mix-Items (auch gesehene)
  remainingSeconds: number;  // Dauersumme der ungesehenen, nicht-failed Mix-Items
  unseenCount: number;
  capped: boolean;           // totalSeconds >= budget
}
export function todayMixStats(db: Database.Database, now?: number): TodayMixStats;
```

`buildDailyMix`-Ablauf:
1. `used = SUM(duration_seconds)` der heutigen Mix-Items; `if (used >= budgetSeconds) return`.
2. Kandidaten (ungesehen, nicht failed, `is_pre_clipper=1`, NICHT im heutigen Mix):

```sql
SELECT f.video_id AS id, v.channel_id AS channelId, v.duration_seconds AS durationSec,
       CASE WHEN v.format = 'short' THEN 'short' ELSE 'video' END AS kind,
       COALESCE(v.source, 'subscription') AS source, f.added_to_feed_at AS addedToFeedAt,
       s.category AS category, s.overall_score AS overallScore,
       s.educational_value AS educationalValue, cms.calculated_score AS channelMatch,
       f.video_id AS parentVideoId
  FROM feed_items f
  JOIN videos v ON v.id = f.video_id
  LEFT JOIN scores s ON s.video_id = f.video_id
  LEFT JOIN channel_match_scores cms ON cms.channel_id = v.channel_id
 WHERE f.seen_at IS NULL AND f.playback_failed = 0 AND f.is_pre_clipper = 1
   AND f.video_id NOT IN (SELECT video_id FROM daily_mix_items WHERE mix_date = ?)
```

3. Sortierung: Quellen-Priorität (`subscription:0, probe:1, topic:2, backfill:3`) als Primärschlüssel, innerhalb jeder Quelle `rankCandidates(rows, now)`.
4. Kanal-Vielfalt: `interleaveByChannel(sortiert, rotation)` mit `rotation = Number(mixDate.replaceAll("-", ""))` (stabil pro Tag) — getrennt je Pool (shorts / longs) NACH dem Prioritäts-Sort? Nein: erst global sortieren + interleaven, DANN in shorts/longs-Pools splitten (Reihenfolge bleibt erhalten).
5. Weben: abwechselnd `SHORTS_PER_LONG` Shorts, dann 1 Long; leerer Pool → Rest des anderen.
6. Aufnahme-Schleife: `while (used < budgetSeconds && next)` → INSERT mit fortlaufender `position` (ab `MAX(position)+1` des Tages), `used += durationSec`.

- [ ] **Step 1: Failing Tests** (`src/feed/daily-mix.test.ts`; Seeds nach dem Muster von `feed.test.ts::seedFeedItem`, aber ohne downloaded_videos — plus format/source setzen):

```ts
import Database from "better-sqlite3";
import { beforeEach, describe, expect, it } from "vitest";
import { applyMigrations } from "../db/migrations.js";
import {
  buildDailyMix,
  getTimeBudgetMinutes,
  mixDateFor,
  setTimeBudgetMinutes,
  todayMixStats,
} from "./daily-mix.js";

const NOW = new Date("2026-08-18T12:00:00").getTime();

function seed(db: Database.Database, id: string, opts: { dur?: number; format?: string; source?: string; seen?: boolean } = {}) {
  db.prepare("INSERT OR IGNORE INTO channels (id,url,title,added_at) VALUES ('c1','x','C',0)").run();
  db.prepare(
    `INSERT INTO videos (id, channel_id, title, published_at, duration_seconds, discovered_at, format, source)
     VALUES (?, 'c1', ?, 0, ?, 0, ?, ?)`,
  ).run(id, `t-${id}`, opts.dur ?? 60, opts.format ?? "short", opts.source ?? "subscription");
  db.prepare(
    `INSERT INTO scores (video_id, overall_score, category, clickbait_risk, educational_value,
      emotional_manipulation, reasoning, model_used, scored_at, decision)
     VALUES (?, 80, 'x', 1, 9, 0, 'ok', 'mock', 0, 'approved')`,
  ).run(id);
  db.prepare(
    "INSERT INTO feed_items (video_id, added_to_feed_at, seen_at, is_pre_clipper) VALUES (?, ?, ?, 1)",
  ).run(id, NOW - 1000, opts.seen ? NOW : null);
}

describe("daily-mix", () => {
  let db: Database.Database;
  beforeEach(() => {
    db = new Database(":memory:");
    applyMigrations(db);
  });

  it("Budget: Default 45, set clamped und persistent", () => {
    expect(getTimeBudgetMinutes(db)).toBe(45);
    expect(setTimeBudgetMinutes(db, 60)).toBe(60);
    expect(getTimeBudgetMinutes(db)).toBe(60);
    expect(setTimeBudgetMinutes(db, 5)).toBe(10);   // clamp unten
    expect(setTimeBudgetMinutes(db, 999)).toBe(240); // clamp oben
  });

  it("füllt bis zum Budget (letztes Item darf überziehen) und ist idempotent", () => {
    setTimeBudgetMinutes(db, 10); // 600s
    for (let i = 0; i < 5; i++) seed(db, `v${i}`, { dur: 240, format: "long" }); // 4-min-Videos
    buildDailyMix(db, NOW);
    const rows = db.prepare("SELECT video_id, duration_seconds FROM daily_mix_items WHERE mix_date = ?").all(mixDateFor(NOW));
    // 240+240 = 480 < 600 → drittes kommt noch rein (überzieht auf 720), viertes nicht mehr
    expect(rows).toHaveLength(3);
    buildDailyMix(db, NOW); // idempotent — Budget voll, nichts dazu
    expect(db.prepare("SELECT COUNT(*) c FROM daily_mix_items").get()).toEqual({ c: 3 });
  });

  it("Quellen-Priorität: subscription vor probe vor topic", () => {
    setTimeBudgetMinutes(db, 10);
    seed(db, "t-topic", { dur: 60, source: "topic" });
    seed(db, "s-abo", { dur: 60, source: "subscription" });
    seed(db, "p-probe", { dur: 60, source: "probe" });
    buildDailyMix(db, NOW);
    const order = (db.prepare("SELECT video_id FROM daily_mix_items ORDER BY position").all() as { video_id: string }[]).map((r) => r.video_id);
    expect(order).toEqual(["s-abo", "p-probe", "t-topic"]);
  });

  it("Rhythmus: nach 5 Shorts kommt ein Langvideo", () => {
    setTimeBudgetMinutes(db, 240);
    for (let i = 0; i < 8; i++) seed(db, `sh${i}`, { dur: 30, format: "short" });
    seed(db, "long1", { dur: 600, format: "long" });
    buildDailyMix(db, NOW);
    const order = (db.prepare("SELECT video_id FROM daily_mix_items ORDER BY position").all() as { video_id: string }[]).map((r) => r.video_id);
    expect(order.indexOf("long1")).toBe(5); // Position 5 = nach 5 Shorts
  });

  it("todayMixStats: total zählt alles, remaining nur Ungesehenes", () => {
    setTimeBudgetMinutes(db, 10);
    seed(db, "a", { dur: 300, format: "long" });
    seed(db, "b", { dur: 300, format: "long" });
    buildDailyMix(db, NOW);
    db.prepare("UPDATE feed_items SET seen_at = ? WHERE video_id = 'a'").run(NOW);
    const stats = todayMixStats(db, NOW);
    expect(stats.budgetMinutes).toBe(10);
    expect(stats.totalSeconds).toBe(600);
    expect(stats.remainingSeconds).toBe(300);
    expect(stats.unseenCount).toBe(1);
    expect(stats.capped).toBe(true);
  });

  it("gesehene Items zählen weiter gegen das Budget — kein Nachfüllen", () => {
    setTimeBudgetMinutes(db, 10);
    seed(db, "a", { dur: 700, format: "long" });
    buildDailyMix(db, NOW);
    db.prepare("UPDATE feed_items SET seen_at = ? WHERE video_id = 'a'").run(NOW);
    seed(db, "b", { dur: 700, format: "long" });
    buildDailyMix(db, NOW); // Budget (600) schon durch 'a' (700) überzogen
    expect(db.prepare("SELECT COUNT(*) c FROM daily_mix_items").get()).toEqual({ c: 1 });
  });
});
```

- [ ] **Step 2: Run** — FAIL (Modul fehlt).
- [ ] **Step 3: Implementierung** gemäß Ablauf; `getTimeBudgetMinutes` seeded per `INSERT OR IGNORE INTO feed_settings (id, daily_time_budget_minutes, updated_at) VALUES (1, 45, ?)`.
- [ ] **Step 4: Run** neue Tests + Vollsuite — PASS.
- [ ] **Step 5: Commit** `git add backend/src/feed && git commit -m "feat(feed): Tagesmix-Builder mit Zeitbudget"`

---

### Task 3: Feed-API — Mix serviert, today-count mit Restdauer, /feed/budget

**Files:**
- Modify: `backend/src/api/feed.ts` (mode=new-Zweig, today-count, zwei neue Routen), `backend/src/index.ts` (Cron + Start-Build + Top-up nach Drain)
- Test: `backend/src/api/feed.test.ts`

**Interfaces:**
- Consumes: `buildDailyMix`, `todayMixStats`, `getTimeBudgetMinutes`, `setTimeBudgetMinutes`, `mixDateFor` aus `../feed/daily-mix.js`.
- Produces:
  - `GET /feed?mode=new` → lazy `buildDailyMix(db)` + heutige Mix-Items (ungesehen, nicht failed) in `position`-Reihenfolge, hydratisiert wie bisher (`hydrateFeedBatch` erhält `{ id }`-Rows in Mix-Reihenfolge — es nutzt nur `r.id` und die Reihenfolge).
  - `GET /feed/today-count` → `{ dailyBudget: <Minuten>, unseenCount, capped, budgetMinutes, remainingSeconds, totalSeconds }` (alte Feldnamen bleiben für alte App-Versionen gefüllt).
  - `GET /feed/budget` → `{ minutes }`; `PUT /feed/budget` Body `{ minutes: number }` → `{ minutes }` (clamped); ungültiger Body → 400.
  - `index.ts`: Cron `0 6 * * *` → `buildDailyMix(db)`; einmal beim Start; in `drainIngestQueue` nach der Schleife `if (processedAny) try { buildDailyMix(db); } catch {}`.

- [ ] **Step 1: Failing Tests** (bestehende mode=new-Tests umbauen — sie erwarten die Kandidaten-Pipeline; künftig erscheinen Items erst NACH `buildDailyMix`, das der Route-Handler lazy aufruft — die Assertions selbst bleiben meist gültig, weil der Handler den Mix ja selbst baut. Prüfen und nur brechende anpassen):

```ts
it("mode=new liefert den stabilen Tagesmix in Mix-Reihenfolge", async () => {
  const db = new Database(":memory:");
  applyMigrations(db);
  seedFeedItem(db, "m1", Date.now() - 3000);
  seedFeedItem(db, "m2", Date.now() - 2000);
  const app = Fastify();
  await registerFeedRoutes(app, { db, dailyBudget: 15 });
  const first = (await app.inject({ method: "GET", url: "/feed?mode=new" })).json() as { videoId: string }[];
  const second = (await app.inject({ method: "GET", url: "/feed?mode=new" })).json() as { videoId: string }[];
  expect(first.length).toBeGreaterThan(0);
  expect(second.map((x) => x.videoId)).toEqual(first.map((x) => x.videoId)); // stabil, keine Rotation mehr
});

it("today-count meldet Zeitbudget und Restdauer", async () => {
  const db = new Database(":memory:");
  applyMigrations(db);
  seedFeedItem(db, "tc1", Date.now());
  const app = Fastify();
  await registerFeedRoutes(app, { db, dailyBudget: 15 });
  await app.inject({ method: "GET", url: "/feed?mode=new" }); // baut den Mix
  const body = (await app.inject({ method: "GET", url: "/feed/today-count" })).json() as Record<string, number | boolean>;
  expect(body.budgetMinutes).toBe(45);
  expect(body.remainingSeconds).toBe(60); // seedFeedItem-Video hat 60s
  expect(body.unseenCount).toBe(1);
});

it("GET/PUT /feed/budget liest und clamped", async () => {
  const db = new Database(":memory:");
  applyMigrations(db);
  const app = Fastify();
  await registerFeedRoutes(app, { db, dailyBudget: 15 });
  expect((await app.inject({ method: "GET", url: "/feed/budget" })).json()).toEqual({ minutes: 45 });
  const put = await app.inject({ method: "PUT", url: "/feed/budget", payload: { minutes: 90 } });
  expect(put.json()).toEqual({ minutes: 90 });
  expect((await app.inject({ method: "GET", url: "/feed/budget" })).json()).toEqual({ minutes: 90 });
  expect((await app.inject({ method: "PUT", url: "/feed/budget", payload: { minutes: "x" } })).statusCode).toBe(400);
});
```

- [ ] **Step 2: Run** — FAIL.
- [ ] **Step 3: Implementierung.** mode=new-Zweig ersetzt Ranking/Interleave/Cooldown durch:

```ts
buildDailyMix(deps.db);
const mixRows = deps.db
  .prepare(
    `SELECT m.video_id AS id FROM daily_mix_items m
      JOIN feed_items f ON f.video_id = m.video_id
     WHERE m.mix_date = ? AND f.seen_at IS NULL AND f.playback_failed = 0
     ORDER BY m.position ASC`,
  )
  .all(mixDateFor(Date.now())) as { id: string }[];
return hydrateFeedBatch(deps.db, mixRows as RawFeedRow[]);
```

(`rankCandidates`/`interleaveByChannel`/`applyCooldown`/`listFeedRaw` bleiben exportiert — daily-mix.ts nutzt die ersten beiden; `listFeedRaw` wird nur noch von Tests genutzt → dort belassen, Kommentar anpassen.) today-count auf `todayMixStats` umstellen; Budget-Routen ergänzen; `index.ts` verdrahten (Cron + Start + Drain-Top-up mit `processedAny`-Flag in der Drain-Schleife).

- [ ] **Step 4: Run** `npm run build && npm test` — PASS (brechende Alt-Tests der mode=new-Pipeline auf die neue Semantik angepasst; Rotation-/Cooldown-Unit-Tests der puren Funktionen bleiben unverändert gültig).
- [ ] **Step 5: Commit** `git add backend/src/api/feed.ts backend/src/api/feed.test.ts backend/src/index.ts && git commit -m "feat(feed): Tagesmix serviert, Restdauer, /feed/budget"`

---

### Task 4: App — Abschluss-Screen + Restdauer + Minuten-Regler

**Files:**
- Create: `android/.../ui/feed/DailyDonePage.kt`
- Modify: `data/api/dto/TodayCountResponse.kt` (+`budgetMinutes: Int? = null`, `remainingSeconds: Int? = null`, `totalSeconds: Int? = null`), `data/api/HikariApi.kt` (+`GET feed/budget`, `PUT feed/budget`), `domain/repo/FeedRepository.kt` (Budget-Methoden), `ui/feed/FeedScreen.kt` (Extra-Seite im Pager), `ui/feed/FeedViewModel.kt` (today nach Refresh laden), `ui/tuning/TuningScreen.kt` + `ui/tuning/TuningViewModel.kt` (Minuten-Regler statt lokalem Stückzahl-Slider)

**Interfaces:**
- Produces:

```kotlin
// DailyDonePage.kt — bewusster Tagesabschluss, KEIN Nachladen.
@Composable
fun DailyDonePage(watchedMinutes: Int?, modifier: Modifier = Modifier)

// HikariApi
@Serializable data class BudgetBody(val minutes: Int)          // in FeedItemDto.kt-Datei oder eigener
@GET("feed/budget") suspend fun getBudget(): BudgetBody
@PUT("feed/budget") suspend fun setBudget(@Body body: BudgetBody): BudgetBody
```

- [ ] **Step 1: `DailyDonePage.kt`** — Vollbild, HikariBg, mittig:

```kotlin
@Composable
fun DailyDonePage(watchedMinutes: Int?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().background(HikariBg).padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Das war's für heute", style = MaterialTheme.typography.headlineSmall, color = HikariText)
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (watchedMinutes != null && watchedMinutes > 0)
                "Du hast dein Zeitbudget erreicht — rund $watchedMinutes Minuten kuratierte Inhalte."
            else
                "Dein Tagesmix ist durchgeschaut.",
            style = MaterialTheme.typography.bodyMedium,
            color = HikariTextFaint,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Text("Morgen gibt es frischen Nachschub.", style = MaterialTheme.typography.labelMedium, color = HikariAmber)
    }
}
```

- [ ] **Step 2: FeedScreen** — Extra-Seite nur im NEW-Modus:

```kotlin
val showDonePage = mode == FeedMode.NEW
val pageCount = items.size + if (showDonePage) 1 else 0
val pagerState = rememberPagerState(pageCount = { pageCount })
// key: { if (it < items.size) items[it].videoId else "daily-done" }
// Pager-Inhalt: if (page >= items.size) { DailyDonePage(watchedMinutes = today?.totalSeconds?.div(60)) ; player.pause() via settledPage-Effekt (Guard unten) } else { bisherige Logik }
```

Anpassungen mit Guard `items.getOrNull(...)`: der Seitenwechsel-Effekt behandelt `item == null` (Done-Seite) wie eine Karten-Seite → `player.pause()`. Der Seiten-Zähler oben nutzt `(pagerState.currentPage + 1).coerceAtMost(items.size)`. `vm.today` existiert bereits (StateFlow<TodayCountResponse?>) — in FeedScreen einsammeln (`val today by vm.today.collectAsState()`); prüfen, dass `vm.refresh()` auch `todayCount()` nachlädt (sonst dort ergänzen).

- [ ] **Step 3: Tuning-Regler** — in `TuningViewModel`: `budgetMinutes: StateFlow<Int?>` (beim Init via `api.getBudget()` laden, `runCatching`), `fun setBudgetMinutes(m: Int)` → `viewModelScope.launch { runCatching { api.setBudget(BudgetBody(m)) } }`. In `TuningScreen` SystemTab: die „Tagesbudget"-Section ersetzen durch:

```kotlin
Section("Zeitbudget", "Etwa $minutesDraft Minuten Feed pro Tag — danach ist bewusst Schluss.") {
    LabeledSlider(
        label = null,
        value = minutesDraft.toFloat(),
        range = 10f..240f,
        steps = 22,
        valueLabel = "$minutesDraft min",
        accentLabel = true,
        onValueChange = {
            minutesDraft = it.toInt()
            vm.setBudgetMinutes(minutesDraft)
        },
    )
}
```

(`minutesDraft` per `remember(budgetMinutes)` initialisiert, Fallback 45. Der alte lokale `settings.dailyBudget`-DataStore-Wert bleibt ungenutzt bestehen — kein Datenverlust, kein Migrationbedarf.)

- [ ] **Step 4: Kompilieren** `cd android && ./gradlew :app:compileDebugKotlin` — BUILD SUCCESSFUL.
- [ ] **Step 5: Commit** `git add android/app/src/main/java/com/hikari/app && git commit -m "feat(app): Abschluss-Screen, Restdauer, Zeitbudget-Regler"`

---

### Task 5: Verifikation, Deploy, Release

- [ ] **Step 1:** Backend Vollsuite + Build; `cli/hikari restart`; Smoke:
  - `curl -s localhost:3939/feed/budget` → `{"minutes":45}`; PUT 60 → persistiert.
  - `curl -s "localhost:3939/feed?mode=new" | python3 -m json.tool | head` — Items da, zweiter Call identische Reihenfolge.
  - `curl -s localhost:3939/feed/today-count` — `budgetMinutes`/`remainingSeconds` plausibel; `sqlite3`: heutige `daily_mix_items` vorhanden, Priorität/Rhythmus stichprobenartig prüfen.
- [ ] **Step 2:** `git fetch --tags` + `gh release list` (Parallel-Session!); nächste freie Version bumpen (versionCode +1), committen, push, `gh release create` im Hausstil, `gh run watch` bis CI-APK. Dem User erst danach berichten.
