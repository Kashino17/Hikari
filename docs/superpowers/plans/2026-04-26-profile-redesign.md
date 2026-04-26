# Profile Tab Redesign — Variante 1

**Status:** planning
**Target version:** v0.20.0 (Phase A) → v0.21.0 (Phase B) → v0.22.0 (Polish) → v0.23.0+ (Offline)
**Current state:** main steht auf 0.19.1, letzter Tag android-mvp-v0.17.0
**Mockup reference:** `backend/mockups/profile/variant-1.html` (+ sub-pages)

## Goal

Den aktuellen `Bibliothek`-Tab durch einen Instagram-artigen `Profil`-Tab ersetzen, der den standalone `Kanäle`-Tab als Sub-Tab integriert. Die drei Profil-Sub-Tabs sind:

1. **Gespeichert** (`feed?mode=saved`) — Insta-Style 3-Spalten-Grid
2. **Kanäle** — Banner-Cards (bisherige Kanal-Logik beibehalten)
3. **Downloads** — Netflix-Style mit Kategorie-Cards (Serien / Kanäle / Filme) und Sub-Pages

## Bottom-Nav-Änderung

| Vorher | Nachher |
|---|---|
| Bibliothek · Feed · Manga · Kanäle · Tuning (5) | **Profil** · Feed · Manga · Tuning (4) |

Die Library-Sektionen `Hero`, `Continue Watching`, `Empfohlen` werden **nicht ersatzlos gestrichen**:
- "Continue Watching" wandert oben in den `Gespeichert`-Tab als horizontale Reihe
- "Empfohlen" wandert in den `Kanäle`-Tab als zweite Sektion (existiert dort schon im Mockup)
- Die alte "Recently Added"-Sektion wird gestrichen (Feed deckt Neues ab)

## Phase A — v0.20.0: Profil-Shell + Saved + Channels

Einfache Visual-Migration ohne neue Backend-Endpoints. Downloads-Tab ist **Stub** (zeigt „Bald verfügbar" oder leere Kategorien).

### A1. Backend: Banner-URL für Channels
- `channels.banner_url TEXT` Migration
- yt-dlp Channel-Fetch erweitern: `banner` aus dem Metadata extrahieren (`channel.banner` oder `channel.thumbnails[…aspect~16:9]`)
- `ChannelDto`/`Channel`-Domainmodel um `bannerUrl` erweitern
- Tests für Migration

### A2. Android: ProfileScreen-Skelett
- Neuer Package `ui/profile/`
- `ProfileScreen.kt` mit:
  - **TopBar**: `IconPill` Composable (Back-Chevron + "Zurück"-Label) | "PROFIL" mono-Title | Settings-Gear
  - **Header**: Avatar (Gradient + Initial), 3-Stat-Row (Videos / Kanäle / Downloads), Name + Bio mit Amber-Tags
  - **TabBar**: 3 Icon-Tabs (Bookmark / Globe / Download) mit Amber-Underline
  - **Panel-Switching** in einer Composable
- `ProfileViewModel.kt` lädt Counts (Saved, Channels, Downloads) parallel

### A3. Saved-Tab
- `SavedTab.kt` — `LazyVerticalGrid(GridCells.Fixed(3))`
- Item: `aspectRatio(1f)` Box mit Coil-Thumbnail + Duration-Badge top-right + 2-Zeilen-Title bottom mit Gradient-Fade
- Source: bestehender `feed?mode=saved` über `FeedRepository.fetchSaved()`
- Tap → Player

### A4. Channels-Tab (Banner-Cards)
- `ChannelsTab.kt` migriert die Logik aus `ChannelsScreen` (Search, Add, Recommendations, Follow, Delete, Long-press → ChannelDetail)
- **Search-Bar** hairline mit Lupe-Icon
- **BannerCard** Composable (2-col `LazyVerticalGrid`):
  - 21:9 Banner mit Coil-Image (Fallback-Gradient bei null banner_url)
  - Avatar-Circle bottom-left mit `zIndex(2f)` über dem Banner-Gradient (der Z-Index-Bug aus dem HTML-Mockup ist hier nativ vermeidbar)
  - Name, Subs · Handle in Mono-Meta
- **Empfohlen-Sektion** mit gleichem BannerCard-Layout

### A5. Navigation
- Bottom-Nav-Liste ändern: `Profil` ersetzt `Bibliothek`, `Kanäle` raus
- Route-Renaming: `library` → `profile`
- Bestehende Routen (`series/{id}`, `channel/{id}`, `video-edit/{id}`) bleiben erreichbar

### A6. Settings-Screen-Shell (Gear-Icon)
- Minimal: nur `backendUrl` + Theme. Restliche Tuning-Optionen bleiben im `Tuning`-Tab.

**Release-Kriterium A:** Profil-Tab existiert mit Header, 3 Tabs, Saved + Channels funktional. Downloads zeigt leere Kategorie-Cards mit "Bald verfügbar"-Hinweis.

## Phase B — v0.21.0: Downloads

Nutzt das **bestehende Backend** (Videos liegen schon in `~/.hikari/videos/`). Kein neuer Offline-Storage auf dem Handy in dieser Phase. Downloads = Videos die in der Backend-DB liegen, gruppiert nach Source.

### B1. Backend: GET /downloads
```ts
GET /downloads → {
  totalBytes: number,
  series: [{ id, title, thumbnail_url, episodeCount, totalBytes, episodes: [{ id, title, episode, season, durationSeconds, thumbnail_url, sizeBytes }] }],
  channels: [{ id, title, banner_url, thumbnail_url, videoCount, totalBytes, videos: [{ id, title, durationSeconds, thumbnail_url, sizeBytes }] }],
  movies: [{ id, title, thumbnail_url, durationSeconds, sizeBytes }]  // is_movie = 1
}
```
- Joins über `videos` + `series` + `channels` + `downloaded_videos` (für `byteSize`)
- `is_movie = 1` → `movies`-Bucket
- Sonst nach `series_id` gruppieren wenn vorhanden, sonst nach `channel_id`
- Tests: 5+ Cases (leer, nur Serien, nur Filme, Mischung, Smart-Sort)

### B2. Android: DTOs + Repo
- `DownloadsResponse` mit drei Listen
- `DownloadsRepository.getDownloads()` (oder als Methode in `FeedRepository`)

### B3. DownloadsTab — Hauptansicht
- **Storage-Strip**: `totalBytes / DISK_LIMIT_BYTES`, Bar in Amber-Gradient, "OFFLINE"-Badge
- **Mini-Sort/Edit-Row**: dezent, rechts ausgerichtet — "Sortiert nach Neueste ▾" + "Bearbeiten" als Text-Links
- **SmartDownloadsCard**: `Card` mit Amber-Tönung, Icon + Text + Toggle (DataStore-Pref, in Phase B nur Persistierung; tatsächliches Auto-DL = Phase D)
- **3 KategorieCards** (`Serien` / `Kanäle` / `Filme`):
  - Stack-Visual links (3 fanned Mini-Posters bzw. 3 overlapping Avatars)
  - Title + Meta + Amber "ÖFFNEN"-Pill + Chevron
  - Background-Glow per Kategorie (radial-gradient hinter Inhalt)
- Tap → `download-category/{type}` Route

### B4. DownloadCategoryScreen — Sub-Page
- Generisch, `category: DownloadCategory` Argument (SERIES/CHANNELS/MOVIES)
- **TopBar**: "Downloads"-Pill als Zurück + Kategorie-Title + Edit-Icon
- **Hero**: Großer Title + Meta-Line
- **FilterPills** kategoriespezifisch:
  - Serien: Alle / Laufend / Komplett / Mit Updates
  - Kanäle: Alle / Auto-DL / Manuell / YouTube
  - Filme: Alle / Anime / 2024 / Klassiker (oder dynamisch aus Daten)
- **Sort-Toolrow** dezent
- **Liste**:
  - Series/Channels: `DownloadGroupCard` expandable (Cover/Avatar + Name + Meta + Count-Badge + Chevron). Expanded zeigt EpisodeRow-Liste mit Thumb + Title + Größe + Play-Button
  - Movies: 2-col Poster-Grid mit Größen-Pill

### B5. Long-Press Edit-Mode
- Long-Press auf Download-Item → Multi-Select-Mode
- TopBar wechselt zu "X ausgewählt | Auswahl aufheben | Löschen"
- Delete → DELETE per Backend (existing) + Liste neuladen

**Release-Kriterium B:** Downloads-Tab voll funktional. Alle Backend-Videos werden korrekt nach Serie/Kanal/Film gruppiert. Sub-Pages mit Filtern arbeiten.

## Phase C — v0.22.0: Polish

- Settings-Screen ausbauen: Smart-Downloads-Toggle, Disk-Limit, Daily-Budget aus Tuning hier hin migrieren
- Edit-Mode-Animation, Selection-Checkboxes
- Continue-Watching-Strip in Saved-Tab oben
- Stats: Live-Counts (nicht statisch berechnet)

## Phase D — v0.23.0+: Echte Offline-Downloads (Future)

- Room-Entity `LocalDownloadEntity` (videoId, localFilePath, byteSize, downloadedAt)
- WorkManager + OkHttp/DownloadManager für tatsächlichen Pull auf Handy-Speicher
- ExoPlayer bevorzugt lokale Datei wenn vorhanden
- Smart-Downloads-Engine: PeriodicWorkRequest, WLAN-Constraint, neue-Folge-Hook

## Datei-Struktur (Phase A + B)

### Backend
- `backend/src/db/schema.sql` — Migration `banner_url` auf channels
- `backend/src/api/channels.ts` — banner_url im Response
- `backend/src/monitor/yt-dlp-fetch.ts` — banner extraction
- `backend/src/api/downloads.ts` (neu) — GET /downloads
- `backend/src/index.ts` — registerDownloadsRoutes mounten
- `backend/tests/api/downloads.test.ts` (neu) — 5+ Tests

### Android
- `data/api/dto/DownloadsDto.kt` (neu)
- `data/api/dto/ChannelDto.kt` — `banner_url` field
- `data/api/HikariApi.kt` — `getDownloads()`, banner in `ChannelDto`
- `domain/repo/FeedRepository.kt` — `getDownloads()` method
- `ui/profile/` (neu):
  - `ProfileScreen.kt`
  - `ProfileViewModel.kt`
  - `tabs/SavedTab.kt`
  - `tabs/ChannelsTab.kt`
  - `tabs/DownloadsTab.kt`
  - `download/DownloadCategoryScreen.kt`
  - `download/DownloadCategoryViewModel.kt`
  - `components/IconPill.kt`
  - `components/BannerCard.kt`
  - `components/CategoryCard.kt`
  - `components/DownloadGroupCard.kt`
  - `components/SmartDownloadsCard.kt`
- `ui/settings/SettingsScreen.kt` (Phase A minimal)
- `ui/navigation/HikariNavHost.kt` — Routen-Update
- `ui/navigation/NavDestinations.kt` — Bottom-Nav neu
- `ui/library/` — bleibt für SeriesDetailScreen, alte LibraryScreen wird entfernt

## Annahmen / Entscheidungen

1. **Continue-Watching** wird in Phase C in den Saved-Tab integriert. In Phase A und B vorerst weg.
2. **Banner-URL** wird per yt-dlp extrahiert. Manuelle Kanäle nutzen Fallback-Gradient (deterministisch aus Title-Hash, wie bisher die Avatar-Gradients).
3. **Smart-Downloads** ist in Phase B nur ein Toggle ohne Backend-Effekt. Echte Implementation in Phase D.
4. **Movies-Erkennung** kommt aus dem bestehenden `is_movie`-Flag (gesetzt im Import-Form).
5. **Downloads-Definition** = Videos in der Backend-DB. In Phase D wird das auf "lokal auf dem Handy" verschärft.
6. **Bibliothek-Tab** wird ersatzlos gestrichen. Series-Detail bleibt via Kanal-Detail oder Saved → Series-Card erreichbar.

## Risiken

- **YT-DLP Banner-Extraction** könnte je nach Video unzuverlässig sein. Fallback-Gradient löst das.
- **Bottom-Nav-Umbenennung** kann Bookmarks/Deep-Links brechen. Aktuell keine externen Links → unkritisch.
- **Edit-Mode-Konsistenz** zwischen Long-Press-auf-Saved (öffnet Edit-Page) und Long-Press-auf-Downloads (Multi-Select-Mode) muss klar voneinander unterscheidbar sein. Lösung: Long-Press in Downloads = Multi-Select; einfacher Tap = Play; Edit-Pen-Icon in TopBar = Edit-Mode für Items selbst.

## Aufbau der Releases

| Version | Inhalt | Geschätzt |
|---|---|---|
| v0.20.0 | Phase A — Profile-Shell, Saved, Channels | 1 Session |
| v0.21.0 | Phase B — Downloads + Sub-Pages | 1-2 Sessions |
| v0.22.0 | Phase C — Polish | 1 Session |
| v0.23.0 | Phase D — Echte Offline-Downloads + Smart-DL Engine | 2-3 Sessions |
