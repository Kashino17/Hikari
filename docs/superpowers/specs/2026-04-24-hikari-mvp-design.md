# Hikari MVP — Design Specification

- **Datum:** 2026-04-24
- **Status:** Draft for Review
- **Autoren:** Kadir-kun (Vision/Decisions), Rem (Architektur-Ausarbeitung)

---

## 1. Vision

**Hikari** (japanisch: Licht) ist eine Single-User Reels-App, die kuratierte, positive, wissensfördernde Kurzvideos von einer vom User selbst ausgewählten YouTube-Channel-Whitelist zeigt — ohne Algorithmus-Manipulation, ohne Doom-Scroll, ohne Werbung.

**Kern-Prinzipien:**
1. **User kontrolliert die Quellen** — Kein Discovery-Algorithmus. Kadir-kun gibt die Channels vor.
2. **AI filtert die Qualität, nicht die Entdeckung** — Nur Clickbait/emotionale Manipulation aus vertrautem Channel-Pool rausfiltern.
3. **Kein Dark Pattern** — Keine View-Counts, keine Likes, keine "Mehr für dich"-Rabbit-Holes. Optional: Tages-Budget.
4. **Local-First** — Kein Cloud-Dependency für Logik oder Daten. Dein Laptop ist Server + Datenbank.

## 2. User-Set Constraints

Von Kadir-kun explizit vorgegeben, nicht verhandelbar:

- Backend läuft **lokal auf dem Laptop** (macOS). Kein Vercel, keine Cloud-DB, keine Managed Services.
- Client ist **Android-APK, nativ**. Kein Web, kein PWA, keine Cross-Platform-Abstraktion.
- **Single-User** — nur Kadir-kun. Kein Auth, keine User-Accounts, keine Multi-Tenancy.
- **Content-Source:** YouTube IFrame Embed (legal), Werbungsfreiheit durch Kuration + SponsorBlock, nicht durch Ad-Blocking.

## 3. Architecture Overview

```
┌──────────────────────────────────────────────────────────────────┐
│  LAPTOP (Hikari Backend)                                          │
│                                                                   │
│  ┌────────────┐   ┌────────────┐   ┌────────────┐   ┌──────────┐ │
│  │ 1. Monitor │→  │ 2. Ingest  │→  │ 3. Score   │→  │ 4. Store │ │
│  │ RSS Poller │   │ YouTube    │   │ LLM Rating │   │ SQLite   │ │
│  │ (node-cron)│   │ Data API + │   │ (pluggable:│   │ feed_    │ │
│  │            │   │ Captions   │   │  Claude/   │   │ items    │ │
│  │            │   │            │   │  Ollama)   │   │          │ │
│  └────────────┘   └────────────┘   └────────────┘   └─────┬────┘ │
│                                                           │      │
│                              ┌────────────────────────────▼────┐ │
│                              │ 5. HTTP API (Fastify)           │ │
│                              │ GET /feed, POST /channels, ...  │ │
│                              └──────────────┬──────────────────┘ │
└─────────────────────────────────────────────┼────────────────────┘
                                              │
                                    Tailscale (WireGuard)
                                              │
┌─────────────────────────────────────────────▼────────────────────┐
│  ANDROID APP (Kotlin + Jetpack Compose)                           │
│                                                                   │
│  ┌──────────┐   ┌──────────┐   ┌──────────────────────────────┐ │
│  │ Room DB  │ ← │ Feed-    │ ← │ Reel-Player (vertical swipe) │ │
│  │ (cache)  │   │ Repo     │   │  └ android-youtube-player    │ │
│  └──────────┘   │ (Retro-  │   │    (IFrame wrapper + Sponsor │ │
│                 │  fit)    │   │     Block integration)       │ │
│                 └──────────┘   └──────────────────────────────┘ │
└───────────────────────────────────────────────────────────────────┘
```

**Data Flow im Normalbetrieb:**
1. Cron-Job auf Laptop pollt alle 15 Min die RSS-Feeds der whitelisted Channels
2. Neues Video entdeckt → Ingest holt Metadata + Transkript
3. LLM scort das Video (Qualität/Kategorie/Clickbait-Risiko)
4. Wenn Score ≥ Threshold → Eintrag in `feed_items`
5. Android-App fetcht Feed via Retrofit → rendert Reel-Stack mit YouTube-IFrame-Player

## 4. Component Details

### 4.1 Stage 1 — Monitor (RSS Poller)

- **Lib:** `node-cron` für Scheduling, `fast-xml-parser` für RSS
- **Schedule:** Alle 15 Min — Poll aller whitelisted Channels
- **Source:** `https://www.youtube.com/feeds/videos.xml?channel_id={CHANNEL_ID}` (werbungsfreies, API-quota-freies RSS)
- **Output:** Für jeden Channel eine Liste der letzten ~15 Video-IDs. Deduplication gegen `videos`-Tabelle.
- **Warum nicht WebSub Push:** Laptop ist nicht öffentlich erreichbar, und 15 Min Latenz sind für Single-User-Feed irrelevant.

### 4.2 Stage 2 — Ingest

- **Metadata:** 1× `youtube.videos.list` API-Call pro neuem Video (Title, Description, Duration, Thumbnails, Category, DefaultLanguage)
- **Transkript:**
  - Primär: `youtube-transcript-api` (npm) — zieht Captions direkt vom Timedtext-Endpoint (gratis, kein API-Quota)
  - Fallback: Wenn keine Captions → Video wird mit `hasTranscript: false` markiert und durchläuft einen schwächeren Score-Pfad (nur auf Title/Description)
- **Hard Filters (vor AI):**
  - Dauer 30s – 10min
  - `liveBroadcastContent === "none"` (keine Livestreams/Premieres)
  - Nicht bereits in DB
- **Lib:** `googleapis` SDK für YouTube API v3

### 4.3 Stage 3 — Score (AI-Filter)

- **Pluggable Interface:**
  ```ts
  interface Scorer {
    score(video: VideoWithTranscript): Promise<Score>;
  }
  ```
- **Default Implementation:** `ClaudeScorer` via Anthropic SDK
  - Modell: `claude-haiku-4-5` (günstig, schnell, gut genug für Nuancen)
  - Prompt mit Prompt-Caching auf System-Instruction (Kadirs Wert-System)
  - Structured Output via `response_format: { type: "json_schema" }`
- **Alternative Implementation:** `OllamaScorer` — HTTP-Call an `localhost:11434/api/chat`, Modell z.B. `qwen2.5:14b`
- **Score-Schema:**
  ```ts
  {
    overall_score: number,          // 0-100
    category: Category,             // enum: science, tech, philosophy, history, ...
    clickbait_risk: number,         // 0-10
    educational_value: number,      // 0-10
    emotional_manipulation: number, // 0-10
    reasoning: string               // 1-2 Sätze
  }
  ```
- **Decision-Rule (in einer eigenen Funktion, separat vom Scorer):**
  `overall_score >= 60 && clickbait_risk <= 4 && emotional_manipulation <= 3`
  → `approved`, sonst `rejected` mit reasoning.
- **SponsorBlock:** Parallel pro Video ein Lookup an `https://sponsor.ajay.pw/api/skipSegments?videoID={id}` — Ergebnis in `sponsor_segments` cachen.

> **NOTE:** Die Scoring-Prompt-Definition ist die wichtigste Design-Entscheidung im ganzen Projekt — sie enkodiert, was "gut" für Hikari bedeutet. Beim Implementieren wird dies eine Kadir-zu-schreiben Komponente sein (5-10 Zeilen Prompt, die dein Value-System encoden).

### 4.4 Stage 4 — Store (SQLite)

**Datei:** `~/.hikari/hikari.db` (via `better-sqlite3`)

**Schema:**

```sql
CREATE TABLE channels (
  id TEXT PRIMARY KEY,          -- YouTube Channel ID
  title TEXT NOT NULL,
  added_at INTEGER NOT NULL,    -- Unix epoch ms
  is_active INTEGER DEFAULT 1,
  last_polled_at INTEGER
);

CREATE TABLE videos (
  id TEXT PRIMARY KEY,          -- YouTube Video ID
  channel_id TEXT NOT NULL REFERENCES channels(id),
  title TEXT NOT NULL,
  description TEXT,
  published_at INTEGER NOT NULL,
  duration_seconds INTEGER NOT NULL,
  aspect_ratio TEXT,            -- "16:9" | "9:16"
  default_language TEXT,
  thumbnail_url TEXT,
  transcript TEXT,
  discovered_at INTEGER NOT NULL
);

CREATE TABLE scores (
  video_id TEXT PRIMARY KEY REFERENCES videos(id),
  overall_score INTEGER NOT NULL,
  category TEXT NOT NULL,
  clickbait_risk INTEGER NOT NULL,
  educational_value INTEGER NOT NULL,
  emotional_manipulation INTEGER NOT NULL,
  reasoning TEXT NOT NULL,
  model_used TEXT NOT NULL,     -- "claude-haiku-4-5" oder "ollama:qwen2.5:14b"
  scored_at INTEGER NOT NULL,
  decision TEXT NOT NULL        -- "approved" | "rejected"
);

CREATE TABLE feed_items (
  video_id TEXT PRIMARY KEY REFERENCES videos(id),
  added_to_feed_at INTEGER NOT NULL,
  seen_at INTEGER,              -- NULL = ungesehen
  saved INTEGER DEFAULT 0,      -- Merk-Liste
  playback_failed INTEGER DEFAULT 0   -- IFrame-Error beim Abspielen
);

CREATE TABLE sponsor_segments (
  video_id TEXT NOT NULL REFERENCES videos(id),
  start_seconds REAL NOT NULL,
  end_seconds REAL NOT NULL,
  category TEXT NOT NULL        -- "sponsor" | "selfpromo" | "intro" | ...
);

CREATE INDEX idx_feed_items_added ON feed_items(added_to_feed_at DESC);
CREATE INDEX idx_videos_channel ON videos(channel_id);
```

### 4.5 Stage 5 — HTTP API

**Framework:** Fastify (lightweight, TypeScript-first)

**Endpoints (JSON):**

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/feed?cursor=...&limit=20` | Kuratierte Reels, paginiert, neueste zuerst |
| `POST` | `/feed/:videoId/seen` | Markiere als gesehen |
| `POST` | `/feed/:videoId/save` | Zur Merk-Liste |
| `DELETE` | `/feed/:videoId/save` | Aus Merk-Liste |
| `POST` | `/feed/:videoId/unplayable` | IFrame-Fehler — Video aus Feed entfernen |
| `GET` | `/channels` | Whitelisted Channels |
| `POST` | `/channels` | Channel hinzufügen (`{ channelUrl }` → parsen → ID) |
| `DELETE` | `/channels/:id` | Channel entfernen |
| `GET` | `/rejected?limit=50` | AI-abgelehnte Videos (zum Tuning des Prompts) |
| `GET` | `/health` | Liveness |

**Kein Auth** — weil Single-User und nur über Tailscale erreichbar (implizite Auth via Netzwerk-Layer).

### 4.6 Android App

**Stack:**
- **Kotlin** + **Jetpack Compose**
- **Min SDK:** 26 (Android 8.0) — deckt 95%+ aller aktiven Geräte
- **HTTP:** Retrofit 2 + OkHttp + kotlinx-serialization
- **Player:** [`android-youtube-player`](https://github.com/PierfrancescoSoffritti/android-youtube-player) (IFrame-Wrapper, aktiv gepflegt, nimmt WebView-Schmerzen weg)
- **Local Cache:** Room (Feed-Items für Offline-Browsing der Liste, Videos streamen immer live)
- **DI:** Hilt
- **Image Loading:** Coil

**Screens:**
1. **FeedScreen** — Vertikal swipe-basierter Reel-Stack. Ein Video pro "Seite", wie Instagram Reels.
2. **SavedScreen** — Gemerkte Videos.
3. **ChannelsScreen** — Whitelist verwalten.
4. **SettingsScreen** — Backend-URL, Tages-Budget, Kategorie-Filter.

**Reel-Player-Behavior:**
- 9:16 Container, full-screen
- Bei 16:9 Video → letterboxed mit dezenten horizontalen Balken (schwarz), Titel + Channel als Overlay unten
- Bei Shorts (9:16) → cover, Overlay transparent
- **SponsorBlock-Integration:** Im `YouTubePlayerListener.onCurrentSecond()` Handler → wenn aktueller Zeitpunkt in einem Sponsor-Segment → `player.seekTo(segment.end_seconds)`
- Swipe Up = nächstes Video, Swipe Down = vorheriges
- Tap = Play/Pause
- Long-press = Save, Double-tap = "Weniger wie das" (flaggt Video zur Re-Evaluation)

**Explizit NICHT:**
- Like-Count, View-Count, Kommentar-Sektion
- "Suggestions for you" Leiste
- Infinite Scroll — Feed hat ein natürliches Ende (nur curated items)

### 4.7 Networking

**Empfehlung: Tailscale**
- Laptop und Android-Phone joinen in das gleiche Tailnet (kostenlos für persönliche Nutzung, bis zu 100 Geräte)
- Laptop bekommt eine stabile Tailnet-Adresse (z.B. `100.x.y.z` oder MagicDNS: `kadir-laptop.tailxxxx.ts.net`)
- Android-App hat die URL hardcoded in den Settings (einmalig setzen)
- **Funktioniert überall** — zu Hause, im Café, im Urlaub — solange Phone Internet hat und Tailscale-App läuft
- **Safer** als öffentlicher Endpoint: Niemand außer Kadir im Tailnet kann den Server erreichen

**Alternative: LAN-only**
- Einfacher Setup: Phone und Laptop im gleichen WLAN, App nutzt `http://192.168.x.x:3000`
- Nachteil: Funktioniert nur zu Hause. Wenn das OK ist, noch simpler.

## 5. Monorepo-Struktur

```
Hikari/
├── .gitignore
├── README.md
├── docs/
│   └── superpowers/
│       └── specs/
│           └── 2026-04-24-hikari-mvp-design.md   ← dieses Dokument
├── backend/                         ← Node.js Server auf Laptop
│   ├── package.json
│   ├── tsconfig.json
│   ├── src/
│   │   ├── index.ts                 ← Fastify server entry
│   │   ├── db/
│   │   │   ├── schema.sql
│   │   │   └── migrations.ts
│   │   ├── monitor/
│   │   │   └── rss-poller.ts
│   │   ├── ingest/
│   │   │   ├── youtube-api.ts
│   │   │   └── transcript.ts
│   │   ├── scorer/
│   │   │   ├── types.ts             ← Scorer interface
│   │   │   ├── claude-scorer.ts
│   │   │   ├── ollama-scorer.ts
│   │   │   └── decision.ts          ← threshold rules
│   │   ├── sponsorblock/
│   │   │   └── client.ts
│   │   ├── api/
│   │   │   ├── feed.ts
│   │   │   ├── channels.ts
│   │   │   └── health.ts
│   │   └── pipeline/
│   │       └── orchestrator.ts      ← verknüpft Stages 1–4
│   └── tests/
└── android/                         ← Kotlin Compose App
    ├── settings.gradle.kts
    ├── app/
    │   ├── build.gradle.kts
    │   └── src/main/
    │       ├── java/com/hikari/
    │       │   ├── MainActivity.kt
    │       │   ├── data/             ← Retrofit, Room, Repo
    │       │   ├── ui/
    │       │   │   ├── feed/
    │       │   │   ├── saved/
    │       │   │   ├── channels/
    │       │   │   └── settings/
    │       │   └── player/           ← YouTube player wrapper
    │       └── res/
    └── tests/
```

## 6. Error Handling & Resilience

- **RSS-Poll Failure:** Log-Level WARN, nächster Cron-Run retryt. Kein Retry-Storm.
- **YouTube API Rate-Limit (10k units/day default):** Ingest-Calls sind ~1 Unit pro Video. Bei Whitelist ≤ 500 Channels und realistischen Upload-Frequenzen: niemals limitiert. Monitoring via `rate_limit_used` Gauge in Logs.
- **LLM API Down:** Retry mit Exponential Backoff (3 Versuche), dann Video-Status `pending_score`. Nächster Cron-Run probiert erneut.
- **Transkript nicht verfügbar:** Score-Call läuft trotzdem, aber Prompt macht das explizit → LLM scort vorsichtig (schärfere Default-Thresholds).
- **SponsorBlock Outage:** Kein Blocker. Video läuft eben mit Sponsor-Segmenten durch.
- **Android-App Offline:** Room-Cache zeigt letzten bekannten Feed. Neue Items fehlen bis Reconnect.
- **Sponsor-Block Skip beim Ende:** Wenn Sponsor-Segment am Video-Ende ist und `seekTo(end)` über die Dauer hinaus geht → nächstes Video laden.
- **IFrame-Player-Fehler** (Video gelöscht, altersbeschränkt, region-gesperrt, Embedding vom Creator deaktiviert): App fängt `onError` ab, flaggt `feed_items.playback_failed = 1` serverseitig via `POST /feed/:videoId/unplayable`, wischt automatisch zum nächsten Video. Backend entfernt solche Videos aus künftigen Feed-Queries.

## 7. Testing Strategy

- **Backend Unit Tests:** `vitest` — Decision-Rules, SponsorBlock-Client, RSS-Parser
- **Backend Integration Tests:** Full-Pipeline mit Mock-YouTube-API und Mock-LLM — läuft gegen SQLite-in-Memory
- **Android Unit Tests:** JUnit + MockK für ViewModels und Repos
- **Android UI Tests:** Compose Test Rule für FeedScreen-Interaktion
- **Keine E2E-Tests im MVP** — manuelles Smoke-Testing durch Kadir auf echtem Gerät reicht

## 8. Out of Scope (YAGNI — bewusst weggelassen)

- **User-Accounts / Auth** — Single-User.
- **Multi-Device-Sync** — ein Android-Phone.
- **Downloads / Offline-Playback** — YouTube-ToS + YAGNI. Phone ist eh meist online.
- **Empfehlungs-Algorithmus** — nicht Teil der Vision.
- **Social Features** (Teilen, Kommentieren) — bewusst weg.
- **Analytics/Tracking** — Single-User, nicht nötig.
- **Publishing/Play Store** — direkte APK-Installation.
- **Web-Dashboard** — alle Steuerung via Android-App.
- **Channel-Discovery (außer manuell)** — explizit User-kuratiert.

## 9. Open Decisions (User-Approval benötigt beim Review)

### 9.1 LLM-Provider — Default

- **Option A (empfohlen):** Claude API (`claude-haiku-4-5`) via Anthropic SDK, mit Prompt-Caching
  - **Pro:** Massiv bessere Qualität bei Nuancen (Clickbait, Manipulation), Kosten ~0.01–0.10 €/Tag bei Single-User-Volumen
  - **Contra:** Nicht "rein lokal", hängt am Internet
- **Option B:** Ollama lokal (z.B. `qwen2.5:14b` oder `llama3.2:3b`)
  - **Pro:** Komplett offline, keine API-Kosten, philosophisch konsequent
  - **Contra:** LLM-Qualität bei Nuancen deutlich schwächer, Latenz sekunden bis zig-Sekunden pro Call
- **Option C:** Pluggable, beide implementieren — Default Claude, in Settings umstellbar

**Rem's Empfehlung:** **Option C**. Code kostet eine halbe Stunde extra, gibt dir Flexibilität und du kannst ausprobieren.

### 9.2 Netzwerk-Strategie

- **Option A (empfohlen):** Tailscale — überall erreichbar
- **Option B:** LAN-only — nur zu Hause, aber Zero-Setup

**Rem's Empfehlung:** **Option A (Tailscale)**.

### 9.3 Tages-Budget (Anti-Doom-Scroll-Feature)

- **Option A:** Hard Limit — "Du hast 15 neue Reels heute. Komm morgen wieder."
- **Option B:** Soft Indicator — Zähler zeigt, wie viele du heute schon gesehen hast, aber kein Stop
- **Option C:** Weg damit — ist dir selbst überlassen, Kadir

**Rem's Empfehlung:** **Option A mit konfigurierbarem Tageslimit (Default 15)**. Das ist der philosophische Kern von Hikari — sonst verfällst du auch hier ins endlose Scrollen.

## 10. Success Criteria (MVP fertig, wenn...)

1. Kadir kann über Android-App Channels zu seiner Whitelist hinzufügen
2. Backend pollt diese Channels und ingested neue Videos innerhalb 15 Min
3. LLM filtert Videos; nur approved Items landen im Feed
4. Android-App zeigt Feed als vertikal-swipebare Reels mit eingebettetem YouTube-Player
5. SponsorBlock überspringt Sponsor-Segmente automatisch
6. Kadir kann Videos speichern, als "weniger wie das" flaggen, als gesehen markieren
7. System läuft stabil 1 Woche lang ohne manuellen Eingriff

## 11. Next Step

Nach Approval dieses Dokuments → `superpowers:writing-plans` Skill invoken, um einen detaillierten Implementation-Plan zu schreiben.
