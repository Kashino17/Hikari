# Hikari MVP — Design Specification (v2)

- **Datum:** 2026-04-24
- **Status:** Draft for Review (v2 — überarbeitet nach Content-Source-Entscheidung)
- **Autoren:** Kadir-kun (Vision/Decisions), Rem (Architektur-Ausarbeitung)

---

## 1. Vision

**Hikari** (japanisch: Licht) ist eine Single-User Reels-App, die kuratierte, positive, wissensfördernde Kurzvideos von einer vom User selbst ausgewählten YouTube-Channel-Whitelist zeigt — mit nativem TikTok/Shorts-artigem Swipe-Interface, ohne YouTube-Branding, ohne Werbung, ohne Algorithmus-Manipulation.

**Kern-Prinzipien:**
1. **User kontrolliert die Quellen** — Kadir-kun gibt Channel-Links vor, kein Discovery-Algorithmus.
2. **AI filtert die Qualität, nicht die Entdeckung** — Clickbait / emotionale Manipulation raus.
3. **Kein Dark Pattern** — Keine View-Counts, Likes, Infinite Rabbit-Holes. Optional: Tages-Budget.
4. **Local-First** — Laptop ist Server + Datenbank. Phone spricht nur mit dem Laptop (Tailscale).
5. **Native Playback** — Raw Video-Streams via ExoPlayer, kein IFrame, keine YouTube-Ads, volle UI-Kontrolle.

## 2. User-Set Constraints

Von Kadir-kun explizit vorgegeben, nicht verhandelbar:

- Backend läuft **lokal auf dem Laptop** (macOS). Kein Vercel, keine Cloud-DB.
- Client ist **Android-APK, nativ**. Kein Web, kein PWA.
- **Single-User** — nur Kadir-kun. Kein Auth, keine Multi-Tenancy.
- **Kein YouTube Data API.** Content wird via `yt-dlp`-Scraping ingested.
- **Direct Embedding, kein IFrame.** Videos spielen nativ via ExoPlayer.
- **UX:** Vertikaler Scroll-Feed wie TikTok/Shorts.

## 3. Rechtliche & Betriebs-Realität (Kadir-kun bewusst akzeptiert)

Der gewählte Scraping-Ansatz bringt Implikationen:

- **YouTube ToS-Verletzung** — in der Praxis für Single-User-Privatnutzung nicht verfolgt, formal aber gegeben.
- **Keine App-Store-Distribution** — APK wird direkt per USB/ADB auf Kadir's Phone installiert.
- **`yt-dlp`-Wartung** — YouTube bricht Scraping-Endpoints typischerweise alle paar Wochen. Upgrade via `pipx upgrade yt-dlp` oder equivalent gehört zu den regelmäßigen Wartungsaufgaben.
- **IP-Rate-Limiting** möglich bei aggressivem Scraping. Pipeline implementiert vernünftige Delays und exponentielles Backoff.
- **Creator-Monetarisierung umgangen** — bewusste Akzeptanz des Users. Optional: "Support Creator"-Button im Player, der zum Patreon / YouTube-Channel linkt.

## 4. Architecture Overview

```
┌──────────────────────────────────────────────────────────────────┐
│  LAPTOP (Hikari Backend, Node.js)                                 │
│                                                                   │
│ ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────┐ │
│ │1.Monitor │→ │2. Ingest │→ │3. Score  │→ │4.Download│→ │5.Store│ │
│ │RSS Poll  │  │yt-dlp    │  │LLM       │  │yt-dlp    │  │SQLite│ │
│ │(15 min)  │  │meta+subs │  │Haiku 4.5 │  │download  │  │+ FS  │ │
│ │          │  │          │  │(approve?)│  │720p mp4  │  │videos│ │
│ └──────────┘  └──────────┘  └──────────┘  └──────────┘  └──┬───┘ │
│                                                             │     │
│                              ┌──────────────────────────────▼──┐  │
│                              │ 6. HTTP API (Fastify)           │  │
│                              │   /feed, /channels              │  │
│                              │   /videos/:id.mp4 (file serve,  │  │
│                              │     Range requests)             │  │
│                              └──────────────┬──────────────────┘  │
└─────────────────────────────────────────────┼─────────────────────┘
                                              │
                                    Tailscale (WireGuard) — ALLES
                                              │
┌─────────────────────────────────────────────▼────────────────────┐
│  ANDROID APP (Kotlin + Jetpack Compose)                           │
│                                                                   │
│  ┌──────────┐   ┌──────────┐   ┌──────────────────────────────┐ │
│  │ Room DB  │ ← │ Feed-    │ ← │ Reel-Player (vertical swipe) │ │
│  │ (cache)  │   │ Repo     │   │  └ ExoPlayer (Media3)        │ │
│  └──────────┘   │ (Retro-  │   │    - plays laptop mp4 via    │ │
│                 │  fit)    │   │      Tailscale HTTP          │ │
│                 └──────────┘   │    - SponsorBlock skip       │ │
│                                │    - preload next 2 videos   │ │
│                                └──────────────────────────────┘ │
└───────────────────────────────────────────────────────────────────┘
```

**Data Flow:**
1. Cron-Job pollt alle 15 Min die RSS-Feeds der whitelisted Channels
2. Neues Video entdeckt → `yt-dlp --dump-json --write-auto-subs --skip-download` holt Meta + Transkript
3. LLM scort das Video
4. Wenn **approved** → `yt-dlp` lädt das Video als MP4 (720p, H.264+AAC) nach `~/.hikari/videos/{videoId}.mp4`
5. Eintrag in `feed_items` + `downloaded_videos`
6. Android-App fetcht Feed. Beim Swipe-to-Play öffnet ExoPlayer direkt `https://kadir-laptop.tailxxxx.ts.net/videos/{videoId}.mp4` — Fastify serviert die Datei mit HTTP-Range-Support. Rock-solid, kein Stream-URL-Expiry, offline-fähig wenn Tailscale steht.

## 5. Component Details

### 5.1 Stage 1 — Monitor (RSS-Poller)

- **Lib:** `node-cron` + `fast-xml-parser`
- **Schedule:** Alle 15 Min, alle `is_active = 1` Channels
- **Source:** `https://www.youtube.com/feeds/videos.xml?channel_id={CHANNEL_ID}` — ist ein **regulärer RSS-Feed**, kein YouTube Data API, kein Key nötig
- **Output:** Liste von Video-IDs pro Channel. Dedupe gegen `videos`-Tabelle.

### 5.2 Stage 2 — Ingest (yt-dlp)

**Tool:** [`yt-dlp`](https://github.com/yt-dlp/yt-dlp) — Python CLI, installiert via `pipx install yt-dlp` auf dem Laptop.

**Wrapper im Node.js-Backend:** direkter Child-Process-Aufruf via `execa` (kein NPM-Wrapper nötig, der eh nur subprozess-spawned).

**Channel-URL → Channel-ID (beim Hinzufügen eines Channels):**
```bash
yt-dlp --flat-playlist --playlist-items 1 --print channel_id --no-warnings 'https://www.youtube.com/@3blue1brown'
```

**Metadata + Transkript pro Video:**
```bash
yt-dlp --dump-json --skip-download --write-auto-subs --sub-lang 'de,en' \
       --cookies-from-browser chrome --no-warnings \
       'https://www.youtube.com/watch?v={VIDEO_ID}'
```
Output: JSON mit `title`, `description`, `duration`, `upload_date`, `thumbnail`, `width/height` (für Aspect-Ratio), `automatic_captions.en/de[0].url` (für Transkript).

**Transkript-Fetch:** Separater `curl` auf die `automatic_captions.*.url` → VTT-Parsing.

**Hard Filters:** Dauer 30s–10min, keine Livestreams (`live_status == "not_live"`), nicht in DB.

**Rate-Limiting:** Max 1 Request pro 2 Sekunden pro Channel, exponentielles Backoff bei HTTP 429.

### 5.3 Stage 3 — Score (AI-Filter)

**Pluggable Interface:**
```ts
interface Scorer {
  score(video: VideoWithTranscript): Promise<Score>;
}
```

**Default:** `ClaudeScorer` via Anthropic SDK
- Modell: `claude-haiku-4-5`
- Prompt-Caching auf System-Instruction (Kadirs Wert-System)
- Structured Output (JSON Schema)

**Alternative:** `OllamaScorer` — lokaler Ollama-Server (`localhost:11434`), Modell z.B. `qwen2.5:14b`.

**Score-Schema:**
```ts
{
  overall_score: number,          // 0-100
  category: Category,             // enum: science, tech, philosophy, history, ...
  clickbait_risk: number,         // 0-10
  educational_value: number,      // 0-10
  emotional_manipulation: number, // 0-10
  reasoning: string
}
```

**Decision-Rule:**
`overall_score >= 60 && clickbait_risk <= 4 && emotional_manipulation <= 3` → `approved`.

**SponsorBlock:** Parallel pro Video Lookup an `https://sponsor.ajay.pw/api/skipSegments?videoID={id}` → `sponsor_segments` cachen.

> **NOTE:** Die Scoring-Prompt-Definition ist die wichtigste Design-Entscheidung. Beim Implementieren wird dies eine Kadir-zu-schreiben Komponente sein (5–10 Zeilen Prompt, die sein Value-System encoden).

### 5.4 Stage 4 — Store (SQLite)

**Datei:** `~/.hikari/hikari.db` via `better-sqlite3`.

```sql
CREATE TABLE channels (
  id TEXT PRIMARY KEY,                 -- YouTube Channel ID (UCxxxxxx)
  url TEXT NOT NULL,                   -- original link that Kadir added
  title TEXT NOT NULL,
  added_at INTEGER NOT NULL,
  is_active INTEGER DEFAULT 1,
  last_polled_at INTEGER
);

CREATE TABLE videos (
  id TEXT PRIMARY KEY,                 -- YouTube Video ID
  channel_id TEXT NOT NULL REFERENCES channels(id),
  title TEXT NOT NULL,
  description TEXT,
  published_at INTEGER NOT NULL,
  duration_seconds INTEGER NOT NULL,
  aspect_ratio TEXT,                   -- "16:9" | "9:16" | "1:1"
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
  model_used TEXT NOT NULL,
  scored_at INTEGER NOT NULL,
  decision TEXT NOT NULL               -- "approved" | "rejected"
);

CREATE TABLE feed_items (
  video_id TEXT PRIMARY KEY REFERENCES videos(id),
  added_to_feed_at INTEGER NOT NULL,
  seen_at INTEGER,
  saved INTEGER DEFAULT 0,
  playback_failed INTEGER DEFAULT 0
);

CREATE TABLE sponsor_segments (
  video_id TEXT NOT NULL REFERENCES videos(id),
  start_seconds REAL NOT NULL,
  end_seconds REAL NOT NULL,
  category TEXT NOT NULL
);

CREATE TABLE downloaded_videos (
  video_id TEXT PRIMARY KEY REFERENCES videos(id),
  file_path TEXT NOT NULL,             -- z.B. "/Users/ayysir/.hikari/videos/xxx.mp4"
  file_size_bytes INTEGER NOT NULL,
  video_codec TEXT,                    -- "h264" | "vp9" | ...
  audio_codec TEXT,                    -- "aac" | "opus" | ...
  resolution_height INTEGER,           -- 720, 480, ...
  downloaded_at INTEGER NOT NULL,
  last_served_at INTEGER               -- für LRU-basierte Cleanup
);

CREATE INDEX idx_feed_items_added ON feed_items(added_to_feed_at DESC);
CREATE INDEX idx_videos_channel ON videos(channel_id);
CREATE INDEX idx_downloaded_last_served ON downloaded_videos(last_served_at);
```

### 5.5 Stage 5 — Download Worker (yt-dlp)

Wenn Score-Decision `approved` → Download-Worker triggert:

```bash
yt-dlp -f "bestvideo[height<=720][ext=mp4]+bestaudio[ext=m4a]/best[height<=720]" \
       --merge-output-format mp4 \
       -o "~/.hikari/videos/%(id)s.%(ext)s" \
       --no-warnings \
       'https://www.youtube.com/watch?v={VIDEO_ID}'
```

- **Format:** 720p H.264 + AAC in MP4 — sehr gut von ExoPlayer unterstützt, mobile-freundliche Dateigröße (~30-80MB pro 5-Min-Video)
- **Concurrency:** Max 2 parallele Downloads (zum Schutz vor YouTube-Rate-Limits)
- **Retry:** Bei Fehler 3× mit exponentiellem Backoff
- **Post-Download:** File-Size + Codec-Info in `downloaded_videos` schreiben

**Auto-Cleanup (LRU-basiert):**
- Alle 24 h läuft ein Cleanup-Job
- Policy: Wenn Gesamtgröße von `~/.hikari/videos/` > 10 GB (konfigurierbar) → lösche Videos mit ältestem `last_served_at`, außer `saved = 1`
- Bei Löschung: Eintrag aus `downloaded_videos` raus, `feed_items` bleibt aber als "bereits gesehen"-Historie

**Filesystem-Konsistenz:** `~/.hikari/videos/` ist ein Verzeichnis, das mit der DB konsistent gehalten wird. DB (`downloaded_videos`) ist Single-Source-of-Truth; Dateien ohne DB-Eintrag werden beim Start-up als "orphans" gelöscht. Umgekehrt: DB-Einträge ohne File werden bereinigt + das zugehörige `feed_items` bekommt `playback_failed = 1`.

### 5.6 Stage 6 — HTTP API (Fastify)

| Method | Path | Purpose |
|--------|------|---------|
| `GET` | `/feed?cursor=...&limit=20` | Kuratierte Reels, paginiert, neueste zuerst |
| `GET` | `/videos/:videoId.mp4` | MP4-Datei serven (HTTP Range-Support via `@fastify/static` oder `send`) |
| `POST` | `/feed/:videoId/seen` | Markiere als gesehen + update `last_served_at` |
| `POST` | `/feed/:videoId/save` | Zur Merk-Liste (schützt vor Cleanup) |
| `DELETE` | `/feed/:videoId/save` | Aus Merk-Liste |
| `POST` | `/feed/:videoId/unplayable` | Playback-Error — Video aus Feed entfernen |
| `POST` | `/feed/:videoId/less-like-this` | Negativ-Feedback fürs Prompt-Tuning |
| `GET` | `/channels` | Whitelisted Channels |
| `POST` | `/channels` | Channel hinzufügen: `{ channelUrl }` → resolve via yt-dlp → ID + Title |
| `DELETE` | `/channels/:id` | Channel entfernen |
| `GET` | `/rejected?limit=50` | AI-abgelehnte Videos (zum Prompt-Tuning) |
| `GET` | `/health` | Liveness + yt-dlp-Version + DB + Disk-Usage |

**Kein Auth** — Single-User, implizite Auth via Tailscale.

**Video-Serving Details:**
- `@fastify/static` mit `prefix: "/videos/"` + root auf `~/.hikari/videos/`
- HTTP Range-Requests sind automatisch unterstützt → ExoPlayer kann mitten im Video starten, effizient buffern
- `Cache-Control: public, max-age=604800` — Phone kann bei Wieder-Anschauen sogar lokal cachen
- File-IDs sind YouTube-Video-IDs (non-guessable); kein Auth nötig

### 5.7 Android App

**Stack:**
- **Kotlin** + **Jetpack Compose**
- **Min SDK:** 26 (Android 8.0)
- **HTTP:** Retrofit 2 + OkHttp + kotlinx-serialization
- **Player:** **ExoPlayer (Media3)** — `androidx.media3:media3-exoplayer:1.4.x` + `media3-ui:1.4.x` (HLS-Module nicht nötig, wir spielen nur MP4 ab)
- **Local Cache:** Room (Feed-Items, Metadata); ExoPlayer SimpleCache für Chunk-Caching der MP4s
- **DI:** Hilt
- **Image Loading:** Coil

**Screens:**
1. **FeedScreen** — VerticalPager (Compose Foundation) mit einem Video pro Page
2. **SavedScreen** — Gemerkte Videos als Grid
3. **ChannelsScreen** — Whitelist verwalten
4. **SettingsScreen** — Backend-Tailscale-URL, Tages-Budget (Default 15), LLM-Provider-Switch (Claude/Ollama), Kategorie-Filter

**Reel-Player (ExoPlayer) Behavior:**
- Container immer 9:16 full-screen
- Video-Content: `scaleType = FIT` bei 16:9 (letterboxed) oder `FILL` bei 9:16
- **Preloading:** Nächste 2 Videos bekommen `MediaItem` (URL = `https://kadir-laptop.tailxxxx.ts.net/videos/{id}.mp4`) in einem `ExoPlayer` mit `PreloadMediaSource` — Buffer wird sofort im Hintergrund aufgebaut, Swipe = instant Playback
- **SponsorBlock-Skip:** Player-Listener polling current position, bei Sponsor-Segment → `player.seekTo(segment.end_seconds * 1000L)`
- **Swipe Up:** nächstes Video, **Swipe Down:** vorheriges
- **Tap:** Play/Pause
- **Long-Press:** Save toggle
- **Double-Tap:** "Weniger wie das" (flaggt Video)
- **Overlay unten:** Channel-Name, Titel, Kategorie, Länge. Semi-transparent. "Support Creator"-Button (optional) linkt zum YouTube-Kanal.

**Explizit nicht:**
- Kein Like-Count, kein View-Count, kein Comment-Thread
- Kein "Suggestions for you" außerhalb der Whitelist
- Kein Infinite-Scroll-Loop — natürliches Ende des kuratierten Feeds

### 5.8 Networking

**Tailscale — ALLES läuft drüber:**
- Laptop + Android-Phone im gleichen Tailnet
- Laptop bekommt MagicDNS: `kadir-laptop.tailxxxx.ts.net`
- Android-App hat diese URL in Settings hardcoded (einmalig)
- Funktioniert überall, solange Phone Internet + Tailscale-App laufen
- **Video-Traffic geht jetzt durch Tailscale** (Pre-Download-Design) — Laptop streamt die MP4-Dateien zum Phone. Bei Laptop-Heim-Internet mit ~50 Mbps upload ist das für Single-User problemlos; typische 720p-MP4-Bitrate ist 3-5 Mbps.
- **Offline-Szenario:** Wenn Phone keine Internet hat (Flugmodus, U-Bahn), funktioniert die App NICHT — Laptop ist nicht erreichbar. Kein echtes Offline-Playback, aber ExoPlayer-SimpleCache hält kürzlich gesehene Videos ein paar Stunden.

## 6. Monorepo-Struktur

```
Hikari/
├── .gitignore
├── docs/
│   └── superpowers/
│       └── specs/
│           └── 2026-04-24-hikari-mvp-design.md
├── backend/                           ← Node.js auf Laptop
│   ├── package.json
│   ├── tsconfig.json
│   ├── src/
│   │   ├── index.ts                   ← Fastify entry
│   │   ├── db/
│   │   │   ├── schema.sql
│   │   │   └── migrations.ts
│   │   ├── monitor/
│   │   │   └── rss-poller.ts
│   │   ├── ingest/
│   │   │   ├── yt-dlp.ts              ← execa-wrapper für yt-dlp
│   │   │   ├── channel-resolver.ts    ← URL → channel_id
│   │   │   └── transcript.ts          ← VTT-Parser
│   │   ├── scorer/
│   │   │   ├── types.ts
│   │   │   ├── claude-scorer.ts
│   │   │   ├── ollama-scorer.ts
│   │   │   └── decision.ts
│   │   ├── sponsorblock/
│   │   │   └── client.ts
│   │   ├── download/
│   │   │   ├── worker.ts               ← yt-dlp-Download + Retry
│   │   │   └── cleanup.ts              ← LRU-Cleanup-Job
│   │   ├── api/
│   │   │   ├── feed.ts
│   │   │   ├── channels.ts
│   │   │   ├── videos.ts               ← @fastify/static MP4-Serving
│   │   │   └── health.ts
│   │   └── pipeline/
│   │       └── orchestrator.ts
│   └── tests/
└── android/
    ├── settings.gradle.kts
    ├── app/
    │   ├── build.gradle.kts
    │   └── src/main/
    │       ├── java/com/hikari/
    │       │   ├── MainActivity.kt
    │       │   ├── data/
    │       │   ├── ui/
    │       │   │   ├── feed/
    │       │   │   ├── saved/
    │       │   │   ├── channels/
    │       │   │   └── settings/
    │       │   └── player/            ← ExoPlayer wrapper
    │       └── res/
    └── tests/
```

## 7. Error Handling & Resilience

- **RSS-Poll Failure:** WARN-Log, nächster Cron-Run retryt.
- **yt-dlp Ingest-Failure (Video nicht verfügbar, geo-blocked, altersrestriktiert):** Logge stderr, markiere `playback_failed = 1`, kein Retry-Storm, skip für künftige Feed-Queries.
- **yt-dlp Download-Failure:** 3× Retry mit Exponential Backoff. Bei endgültigem Scheitern: Eintrag in `scores` bleibt als `approved`, aber `feed_items.playback_failed = 1` → erscheint nicht im Feed. Re-Download beim nächsten Cron-Run möglich.
- **yt-dlp-Format gebrochen** (YouTube hat Scraping gebrochen):
  - Globaler Fehler, alle Ingests/Downloads schlagen fehl
  - Health-Endpoint zeigt "yt-dlp-Version veraltet" an
  - Kadir wird via Log oder (optional später) Push-Notification benachrichtigt → manueller `pipx upgrade yt-dlp`
- **Disk Full:** Cleanup-Job läuft bei jedem Download-Ende. Bei < 500 MB frei: blockiere neue Downloads, log CRITICAL, Health-Endpoint flaggt `disk_warning`. Saved-Videos nie auto-gelöscht — wenn Kadir alle saved macht und Disk voll, muss er manuell aufräumen.
- **LLM API Down:** Retry mit Backoff (3×), dann Status `pending_score`, nächster Cron probiert erneut.
- **Transkript nicht verfügbar:** Score-Call läuft, Prompt weiß das, Thresholds schärfer.
- **SponsorBlock Outage:** Video läuft mit Sponsor-Segmenten, kein Blocker.
- **Tailscale Down / Phone Offline:** App zeigt Room-Cache-Feed, Playback schlägt fehl → "Keine Verbindung zum Hikari-Server"-Message. ExoPlayer SimpleCache kann kürzlich gesehene Videos noch ein paar Stunden wiedergeben.
- **Orphan-Files / DB-Mismatch:** Beim Backend-Start läuft ein Consistency-Check: Files in `~/.hikari/videos/` ohne DB-Eintrag → löschen. DB-Einträge ohne File → löschen + flag `playback_failed = 1`.

## 8. Testing Strategy

- **Backend Unit:** `vitest` — Decision-Rules, RSS-Parser, yt-dlp-Output-Parser, LRU-Cleanup-Policy, Orphan-File-Detection
- **Backend Integration:** Full-Pipeline mit Mock-yt-dlp-CLI (script-replacement im PATH), Mock-LLM, In-Memory-SQLite
- **Android Unit:** JUnit + MockK für ViewModels und Repos
- **Android UI:** Compose Test Rule für FeedScreen-Swipe-Interaktion
- **Keine E2E** — Smoke-Test durch Kadir auf echtem Gerät

## 9. Out of Scope (YAGNI)

- User-Accounts / Auth
- Multi-Device-Sync
- Live-Streaming ohne Download (Option A aus Brainstorming wurde verworfen — Pre-Download ist gewählt)
- Empfehlungs-Algorithmus
- Social Features
- Analytics
- Play-Store-Distribution
- Web-Dashboard
- Channel-Discovery (außer manuell)

## 10. Entscheidungen (Decided)

| # | Thema | Entscheidung |
|---|-------|--------------|
| 1 | Content-Quelle | yt-dlp-Scraping (kein YouTube Data API) |
| 2 | Playback | Pre-Download 720p MP4 → ExoPlayer, alles via Tailscale |
| 3 | Backend-Runtime | Node.js + Fastify auf Laptop |
| 4 | DB | SQLite (better-sqlite3) |
| 5 | Client | Kotlin + Jetpack Compose + Media3/ExoPlayer |
| 6 | Netzwerk | Tailscale |
| 7 | LLM-Provider | **Pluggable:** Claude Haiku 4.5 als Default, Ollama als Fallback (in Settings umschaltbar) |
| 8 | Tages-Budget | **Hard Limit 15/Tag**, in Settings konfigurierbar |
| 9 | Disk-Limit | **10 GB Default**, LRU-Cleanup wenn überschritten, `saved = 1` nie gelöscht |
| 10 | Support-Creator-Button | **Nein** — keiner |

## 11. Success Criteria (MVP fertig, wenn...)

1. Kadir kann über Android-App Channels (per URL) zu seiner Whitelist hinzufügen — Backend resolved via yt-dlp zu Channel-ID
2. Backend pollt RSS, ingested neue Videos via yt-dlp innerhalb 15 Min nach Upload
3. LLM filtert; nur approved Items werden heruntergeladen und landen im Feed
4. Approved Videos werden als 720p MP4 auf den Laptop heruntergeladen
5. Android-App zeigt Feed als vertikal-swipebare Reels mit ExoPlayer
6. Videos streamen über Tailscale vom Laptop zum Phone
7. SponsorBlock überspringt Sponsor-Segmente automatisch
8. Kadir kann Save, "Less-Like-This", Seen markieren
9. LRU-Cleanup hält Disk-Usage unter 10 GB
10. Kein YouTube-Branding oder -Werbung sichtbar
11. System läuft 1 Woche stabil ohne manuellen Eingriff (außer ggf. yt-dlp-Upgrade)

## 12. Next Step

Implementation-Plan via `superpowers:writing-plans`, dann Subagent-driven Development mit TDD pro Komponente.
