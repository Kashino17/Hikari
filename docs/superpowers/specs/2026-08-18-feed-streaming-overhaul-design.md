# Hikari 2.0 — Feed-Streaming-Umbau (Design)

**Datum:** 2026-08-18
**Status:** GELIEFERT — alle 5 Etappen umgesetzt (2026-08-18, v0.59.0–v0.65.0).
Abweichungen: Themen-Suche nutzt filter.likeTags statt eigener interest_topics-Tabelle;
Budget-Speicher ist feed_settings statt discovery_settings; Verlauf aus feed_items.seen_at;
kein expliziter Später-Button (Automatik: Wegswipe → rein, Öffnen → raus).

## 1. Ziel & Motivation

Hikari soll sich anfühlen wie „App auf, kurz unterhalten lassen" — lange und
kurze Videos, kuratiert nach den eigenen Bedürfnissen, mit bewusstem Ende
(Anti-Doomscroll bleibt Kernprinzip). Heutige Lücken:

- Man muss Kanäle kennen, suchen und abonnieren; laden die Abos nichts hoch,
  ist der Feed leer.
- Anschauen ist an Server-Downloads gekettet: freigegebene Videos sind erst
  nach Download (+ ggf. Clip-Rendering) verfügbar.
- Der Clipper (Download + Vision-LLM + Remotion + Whisper) baut mit viel
  Rechenaufwand Kurzform nach, die Creator als native YouTube-Shorts oft
  selbst produzieren.

Wichtiger Ist-Befund: Die Filterung läuft **bereits ohne Download** —
`ingest/metadata.ts` holt Metadaten + Auto-Caption-URL (`--skip-download`),
`ingest/transcript.ts` lädt das YouTube-VTT-Transkript, der Scorer entscheidet
damit. Der Download passiert erst nach Freigabe und existiert nur für Playback
und Clipper. Der Umbau ist daher primär ein **Playback- und Feed-Umbau**,
kein Filter-Umbau.

## 2. Getroffene Entscheidungen (User, 2026-08-18)

1. **Discovery-Quellen:** Ähnliche Kanäle + Interessen-Themen + Backfill aus
   Abos. Kein Related-Video-Radio.
2. **Clipper raus aus dem Feed.** Stattdessen native YouTube-Shorts der
   abonnierten Kanäle. Clipper-Code bleibt, wird per Flag deaktiviert.
3. **Feed-Form:** Ein gemischter vertikaler Swipe-Feed. Shorts spielen direkt,
   lange Videos erscheinen als Vorschau-Karte (Thumbnail, Titel, Dauer,
   KI-Kurzbeschreibung), Tap öffnet den Vollbild-Player.
4. **Bibliothek = Deine Sammlung:** Gespeichert, Später ansehen, Verlauf,
   Kanäle/Abos, Serien, Offline-Downloads. „Speichern" heißt merken;
   Herunterladen ist ein bewusster Offline-Knopf.
5. **Tagesbudget = Zeitbudget** (einstellbar, Start ~45 min). Shorts zählen
   mit ihrer Dauer, Langvideos mit voller Dauer. Hartes, serverseitig
   erzwungenes Ende mit Abschluss-Screen.

## 3. Architektur-Übersicht

**Bleibt unverändert:** RSS-Polling + adaptive Cadence, Ingest-Queue,
Metadaten/Transkript-Gewinnung, Scorer (Claude/Ollama/LM Studio) mit
Schwellen und Green-Card (`auto_approve`), SponsorBlock, Musik-Feature,
Manga, Ranking-Prinzip ohne Engagement-Signale.

**Neu/geändert:**

| Bereich | Vorher | Nachher |
|---|---|---|
| Playback | Server lädt MP4, App spielt `/videos/:id.mp4` | Stream-Resolver + Byte-Proxy `/stream/video/:id`, Download nur noch für Offline |
| Kurzform | KI-Clips (Clipper-Prozess) | Native Shorts der Abo-Kanäle (Innertube Shorts-Tab) |
| Feed-Inhalt | Clips + Legacy-Items, nur Abos | Tagesmix: Shorts (autoplay) + Langvideo-Karten, aus Abos + Discovery |
| Feed-Ende | Clientseitig (finiter Pager) | Serverseitiges Zeitbudget + Abschluss-Screen |
| Discovery | Nur Kanal-Vorschläge (separate Ansicht) | Drei Feed-Quellen: Probe-Kanäle, Themen-Suche, Backfill — alle durch den Scorer |
| Bibliothek | Server-Download-getrieben (Serien/Neues/Kanäle) | Sammlung: Gespeichert/Später/Verlauf/Kanäle/Serien/Offline |

## 4. Komponenten

### 4.1 Stream-Resolver + Proxy (Backend)

Neues Modul `backend/src/stream/` nach dem produktionserprobten Muster aus
`api/music.ts`:

- `resolver.ts`: löst `videoId` → googlevideo-URL per `yt-dlp -g`
  (`best[height<=720][ext=mp4]` gemuxt für Feed-Playback; Qualitätswahl
  konfigurierbar). Persistenter Cache (TTL ~5 h, da URLs ~6 h gelten),
  In-Flight-Dedup wie `music-stream-cache`.
- `proxy.ts`: `GET /stream/video/:videoId` — Byte-Proxy mit
  Range-Forwarding (206/Content-Range), da googlevideo-URLs IP-gebunden
  sind. Gleiches Muster wie `proxyMediaStream` der Musik.
- Der Resolver ist austauschbar gekapselt: späterer Umstieg auf
  Innertube-`/player` (schneller, liefert Caption-Tracks) ändert nur dieses
  Modul. Nicht Teil dieses Umbaus.
- **Fehlerfall:** Resolver-Fehler (Throttling, yt-dlp veraltet) → 502 mit
  Fehlercode; App zeigt Retry. Existiert eine Server- oder Gerätekopie,
  spielt die App diese als Fallback.

### 4.2 Shorts-Ingest (Backend)

- `music-innertube.ts`-Erweiterung: `itChannelShorts(channelId)` über den
  bestehenden WEB-`browse`-Weg (Tab-Params wie `itChannelVideos`/
  `itChannelPlaylists`).
- Der 15-min-Poller holt pro Abo-Kanal zusätzlich neue Shorts und enqueued
  sie in die bestehende `ingest_queue`.
- Shorts durchlaufen die unveränderte Pipeline (Metadaten → Transkript →
  Scorer). Ohne Sprache bewertet der Scorer Titel/Beschreibung;
  Green-Card-Kanäle passieren ohnehin.
- `videos` bekommt `format` (`'short' | 'long'`), bestimmt aus
  Innertube-Herkunft (Shorts-Tab) bzw. Heuristik (Hochformat + ≤ 3 min)
  bei RSS-Einträgen.
- **Kein Download** nach Freigabe mehr (weder Shorts noch Langvideos);
  `download/worker.ts` wird nur noch vom Offline-Flow (4.8) aufgerufen.

### 4.3 Clipper-Deaktivierung

- Config-Flag `clipper.enabled=false` (Default neu: aus). Orchestrator setzt
  kein `clip_status='pending'` mehr, keine Clipper-Enqueues.
- Clipper-Code, Tabellen und der separate Worker-Prozess bleiben erhalten
  (kein Rückbau in diesem Umbau).
- Bestehende Clips fallen aus dem Feed (Feed-Query liefert nur noch
  Tagesmix-Items); ihre Dateien räumt der bestehende Disk-Cleanup ab.

### 4.4 Discovery-Quellen (Backend)

Alle drei Quellen erzeugen nur **Kandidaten**; Türsteher bleibt der Scorer
mit unveränderten Schwellen. Kandidatenprüfung kostet nur Metadaten +
Transkript (Text), daher großzügig dimensionierbar.

- **Probe-Kanäle:** `monitor/recommendations.ts` (Kanalsuche nach
  `likeTags`) wird Feed-Quelle: Top-Kandidaten werden als Probe-Kanäle
  geführt (`channels.status='probe'`; bisher implizit „gefolgt/nicht
  gefolgt", neu: `'subscribed' | 'probe' | 'blocked'`). Von Probe-Kanälen
  werden die jüngsten Videos/Shorts ingestiert (gedrosselt, z. B. max. 3/Tag
  pro Kanal). Feed-Items tragen Badge „Neu für dich" mit Ein-Tap-Abonnieren
  und Ein-Tap-Blocken (blockt den Kanal dauerhaft, entfernt seine Items).
- **Interessen-Themen:** Neue Tabelle `interest_topics` (Label, Suchquery,
  aktiv-Flag), pflegbar im Tuning-Tab der App. Täglich pro Thema eine
  Innertube-Suche (`search` auf dem WEB-Client), Kandidaten → Pipeline.
- **Backfill:** Füllt der Tag sich nicht, holt `itChannelVideos()` ältere,
  nie ingestierte Videos der Abo-Kanäle nach (älteste Lücken zuerst,
  gedrosselt) → Pipeline.

### 4.5 Tagesmix-Builder (Backend)

Neues Modul `backend/src/feed/daily-mix.ts` + Tabelle `daily_mix_items`
(Datum, video_id, Position, Quelle, Dauer):

- Läuft täglich (Cron) und nach jedem Pipeline-Approve, bis das Budget
  voll ist.
- **Budget:** Setting `daily_time_budget_minutes` (Default 45, Tuning-Tab;
  ersetzt `DAILY_BUDGET`-Stückzahl). Summe der Item-Dauern ≤ Budget;
  ein Langvideo zählt voll.
- **Priorität:** 1. neue Abo-Inhalte, 2. Probe-Kanäle, 3. Themen,
  4. Backfill.
- **Rhythmus:** Bestehende Bausteine (`rankCandidates`,
  `interleaveByChannel`, `applyCooldown`) bleiben; zusätzlich
  Einstreuregel ≈ eine Langvideo-Karte je 4–6 Shorts, Kanal-Cooldown
  wie bisher.
- `GET /feed` liefert den Tagesmix des Tages (gesehene Items
  ausgefiltert); `GET /feed/today-count` meldet Restdauer statt Stückzahl.
- Die KI-Kurzbeschreibung der Langvideo-Karten liefert
  `clipper/context-summarizer.ts`, umgezogen/parametrisiert auf
  YouTube-Transkript-Input (läuft beim Approve, gespeichert am Video).

### 4.6 Feed-UI (Android)

- `FeedScreen.kt`: `VerticalPager` bleibt finit, rendert zwei Seitentypen:
  - **Short-Seite:** Autoplay über `/stream/video/:id` (ExoPlayer wie
    bisher, OkHttp-Datasource; `mediaItemFor()` baut künftig Stream-URLs).
  - **Langvideo-Karte:** Thumbnail, Titel, Kanal, Dauer, Kurzbeschreibung,
    Badge der Quelle („Neu für dich" bei Discovery). Tap →
    `VideoPlayerScreen` (streamt ebenfalls). Weiterswipen legt das Video
    automatisch in „Später ansehen".
- **Abschluss-Screen** nach dem letzten Item: bewusster Tagesabschluss
  (Restdauer 0), kein Nachladen.
- SponsorBlock-Skips und Caption-Overlay bleiben; Overlay-Quelle für
  Shorts/Videos ist das YouTube-VTT (bereits in `videos.transcript`),
  nicht mehr Whisper.

### 4.7 Bibliothek als Sammlung (Android + Backend)

`GET /library` wird umgebaut auf Sammlungs-Sektionen:

- **Gespeichert** (bestehendes `mode=saved`-Konzept),
- **Später ansehen** (neue Tabelle `watch_later`; befüllt durch
  Karten-Swipe und expliziten Button),
- **Verlauf** (aus Playback-Positionen, chronologisch),
- **Kanäle** (Abos inkl. Verwaltung von Probe-/geblockten Kanälen),
- **Serien** (unverändert),
- **Offline** (Geräte-Downloads, wie bisher aus Room `local_downloads`).

Offline-Fallback-Verhalten der Bibliothek (Room-basiert) bleibt.

### 4.8 Offline-Flow

Expliziter „Offline verfügbar machen"-Knopf (Video-Detail/Karte):
Server lädt per `download/worker.ts` (on demand statt automatisch),
Gerät zieht die Datei wie bisher über `LocalDownloadManager`; lokale
Wiedergabe über den bestehenden `file://`-Zweig. `SmartDownloadScheduler`
wird auf „Später ansehen + Gespeichert" als Quelle umgestellt (statt
Feed-Vorauslad).

## 5. Datenmodell-Änderungen

- `videos`: + `format TEXT ('short'|'long')`, + `source TEXT
  ('subscription'|'probe'|'topic'|'backfill')`, + `summary TEXT`
  (KI-Kurzbeschreibung).
- `channels`: + `status TEXT ('subscribed'|'probe'|'blocked')`
  (Migration: bestehende gefolgte Kanäle → `'subscribed'`).
- Neu: `interest_topics`, `daily_mix_items`, `watch_later`.
- Stream-URL-Cache: Datei-basiert wie Musik (`stream-cache.json`),
  generalisiert für Video.
- Android Room: Migration v14 → v15, `FeedItemEntity` + `itemType`,
  `source`, `summary`, `durationSeconds`.

## 6. API-Änderungen

- Neu: `GET /stream/video/:videoId` (Range-fähiger Proxy).
- Neu: `GET/POST/DELETE /topics` (Interessen-Themen).
- Neu: `POST /channels/:id/block`, `POST /channels/:id/subscribe`
  (Probe → Abo).
- Neu: `GET/POST/DELETE /watch-later`.
- Geändert: `GET /feed` (Tagesmix), `GET /feed/today-count`
  (Restminuten), `GET /library` (Sektionen), Offline-Download-Trigger
  `POST /videos/:id/offline`.
- Entfällt aus dem Hot Path: automatischer Download + Clip-Endpunkte
  (bleiben vorhanden, ungenutzt).

## 7. Fehlerbehandlung & Risiken

- **Stream-Auflösung schlägt fehl** (YouTube-Änderung, Throttling):
  Retry per App, Fallback auf vorhandene Datei; yt-dlp regelmäßig
  aktualisieren (Server-Ops). Downloads als Notfall-Modus bleiben im Code.
- **Shorts ohne Transkript:** Scorer erhält Titel/Beschreibung; bei
  Discovery-Quellen gelten die normalen Schwellen (kein Auto-Approve).
- **Discovery-Qualität schwankt:** Themen-Queries und Probe-Drosselung
  sind Tuning-Parameter; Blocken wirkt sofort und dauerhaft.
- **Leerer Tag trotz Backfill** (Katalog durchgesehen): Abschluss-Screen
  erscheint früher — akzeptiert, kein Künstlich-Auffüllen.
- **Speicher:** Server-Disk nur noch Offline-Anforderungen + Musik-Cache;
  bestehender Cleanup bleibt als Sicherung.

## 8. Testing

- Backend (Vitest, System-Node!): Resolver-Cache/TTL/Dedup,
  Range-Proxy-Header, Tagesmix-Budget & Prioritäten & Rhythmusregel,
  Shorts-Erkennung, Probe-Drosselung, Backfill-Auswahl,
  Watch-Later/Block-Endpoints. Innertube-Parser mit fixierten
  Response-Fixtures.
- Android: ViewModel-Tests für Feed-Seitentypen, Room-Migration 14→15.
- Manuell je Etappe: Streaming-Playback inkl. Seek, Feed-Durchlauf bis
  Abschluss-Screen, Offline-Flow Ende-zu-Ende.

## 9. Etappen (jede einzeln releasbar)

1. **Streaming-Fundament:** `stream/`-Modul, Proxy-Route, App spielt
   Streams (Feed sonst unverändert). Aufräumen: restliche
   macOS-„ 2"-Duplikate in `backend/src` löschen.
2. **Shorts + Feed-Umbau:** Shorts-Ingest, `format`-Spalte,
   Clipper-Flag aus, gemischter Pager (Short-Seiten +
   Langvideo-Karten), Karten-Zusammenfassung.
3. **Discovery:** Kanal-Status, Probe-Ingest, Themen (Tabelle + API +
   Tuning-UI), Backfill.
4. **Tagesmix:** `daily-mix.ts`, Zeitbudget-Setting, neue
   Feed-/Count-API, Abschluss-Screen.
5. **Bibliothek:** Sammlungs-Sektionen, Watch-Later, Verlauf,
   Offline-on-demand, `SmartDownloadScheduler`-Umstellung.

## 10. Nicht-Ziele

- Kein Related-Video-Radio für den Feed (bewusst abgelehnt).
- Kein Innertube-`/player` (nur vorbereitet durch Modulgrenze).
- Kein Rückbau des Clipper-Codes.
- Keine Änderungen an Musik, Spielen, Manga, News.
- Keine Engagement-Signale im Ranking (bewusstes Prinzip).
