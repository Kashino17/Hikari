CREATE TABLE IF NOT EXISTS series (
  id TEXT PRIMARY KEY,
  title TEXT NOT NULL,
  description TEXT,
  thumbnail_url TEXT,
  added_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS channels (
  id TEXT PRIMARY KEY,
  url TEXT NOT NULL,
  title TEXT NOT NULL,
  added_at INTEGER NOT NULL,
  is_active INTEGER DEFAULT 1,
  last_polled_at INTEGER,
  handle TEXT,
  description TEXT,
  subscribers INTEGER,
  thumbnail_url TEXT,
  banner_url TEXT,
  -- Feed = Videos der Kanäle mit status 'subscribed' AND is_active = 1.
  -- 'probe' | 'blocked' stammen aus der entfernten Discovery (Altbestand).
  status TEXT
);

CREATE TABLE IF NOT EXISTS videos (
  id TEXT PRIMARY KEY,
  channel_id TEXT NOT NULL REFERENCES channels(id),
  series_id TEXT REFERENCES series(id),
  title TEXT NOT NULL,
  description TEXT,
  published_at INTEGER NOT NULL,
  duration_seconds INTEGER NOT NULL,
  aspect_ratio TEXT,
  default_language TEXT,
  thumbnail_url TEXT,
  transcript TEXT,
  discovered_at INTEGER NOT NULL,
  season INTEGER,
  episode INTEGER,
  dub_language TEXT,
  sub_language TEXT,
  is_movie INTEGER DEFAULT 0,
  format TEXT,   -- 'short' | 'long'
  source TEXT,   -- 'subscription' (Altbestand auch 'probe' | 'topic' | 'backfill')
  summary TEXT   -- Kurzbeschreibung fuer Feed-Karten (Altbestand, wird nicht mehr erzeugt)
);

-- Eine Zeile pro aufgenommenem Video. Es wird nicht mehr bewertet: neue
-- Abo-Videos bekommen eine neutrale Zeile (decision 'approved', model_used
-- 'subscription'), weil Feed, Bibliothek und Statistik gegen scores joinen.
CREATE TABLE IF NOT EXISTS scores (
  video_id TEXT PRIMARY KEY REFERENCES videos(id),
  overall_score INTEGER NOT NULL,
  category TEXT NOT NULL,
  clickbait_risk INTEGER NOT NULL,
  educational_value INTEGER NOT NULL,
  emotional_manipulation INTEGER NOT NULL,
  reasoning TEXT NOT NULL,
  model_used TEXT NOT NULL,
  scored_at INTEGER NOT NULL,
  decision TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS feed_items (
  video_id TEXT PRIMARY KEY REFERENCES videos(id),
  added_to_feed_at INTEGER NOT NULL,
  seen_at INTEGER,
  saved INTEGER DEFAULT 0,
  playback_failed INTEGER DEFAULT 0,
  progress_seconds REAL DEFAULT 0,
  queued_at INTEGER,
  queue_order INTEGER
);

-- Wiedergabefortschritt pro Video, unabhängig davon, ob das Video je im Feed
-- lag. feed_items deckt nur Feed-Videos ab; heruntergeladene Serienfolgen und
-- Filme haben dort oft keine Zeile. Ohne diese Tabelle verpuffte ihr Fortschritt
-- beim PUT /feed/:id/progress still — die Bibliothek zeigte weiter 0 %.
CREATE TABLE IF NOT EXISTS video_progress (
  video_id TEXT PRIMARY KEY REFERENCES videos(id) ON DELETE CASCADE,
  progress_seconds REAL NOT NULL DEFAULT 0,
  updated_at INTEGER NOT NULL
);

-- Aus »Weiterschauen« entfernte Titel: Schlüssel "s:<Serie>" oder "v:<Video>". Der Titel bleibt
-- verborgen, bis er nach dem Entfernen wieder geschaut wird (library/history.ts).
CREATE TABLE IF NOT EXISTS continue_dismissed (
  key TEXT PRIMARY KEY,
  dismissed_at INTEGER NOT NULL
);

-- Etappe 4: kuratierter Tagesmix mit Zeitbudget. Ein Eintrag = ein Slot des
-- Tages; gesehene Items bleiben drin (konsumierte Zeit zaehlt gegen das Budget).
CREATE TABLE IF NOT EXISTS daily_mix_items (
  mix_date TEXT NOT NULL,           -- lokaler Kalendertag YYYY-MM-DD
  video_id TEXT NOT NULL REFERENCES videos(id),
  position INTEGER NOT NULL,        -- Reihenfolge im Tagesmix
  source TEXT,                      -- Quelle zum Bau-Zeitpunkt (Anzeige/Debug)
  duration_seconds INTEGER NOT NULL,
  PRIMARY KEY (mix_date, video_id)
);
CREATE INDEX IF NOT EXISTS idx_daily_mix_date ON daily_mix_items(mix_date, position);

-- Etappe 5: "Später ansehen" — vom Karten-Wegswipe oder explizit befuellt.
CREATE TABLE IF NOT EXISTS watch_later (
  video_id TEXT PRIMARY KEY REFERENCES videos(id),
  added_at INTEGER NOT NULL
);

-- Feed-Einstellungen (Singleton) — Zeitbudget des Tagesmixes in Minuten.
CREATE TABLE IF NOT EXISTS feed_settings (
  id INTEGER PRIMARY KEY CHECK (id = 1),
  daily_time_budget_minutes INTEGER NOT NULL DEFAULT 45,
  updated_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS sponsor_segments (
  video_id TEXT NOT NULL REFERENCES videos(id),
  start_seconds REAL NOT NULL,
  end_seconds REAL NOT NULL,
  category TEXT NOT NULL
);

CREATE TABLE IF NOT EXISTS downloaded_videos (
  video_id TEXT PRIMARY KEY REFERENCES videos(id),
  file_path TEXT NOT NULL,
  file_size_bytes INTEGER NOT NULL,
  video_codec TEXT,
  audio_codec TEXT,
  resolution_height INTEGER,
  downloaded_at INTEGER NOT NULL,
  last_served_at INTEGER
);

CREATE INDEX IF NOT EXISTS idx_feed_items_added ON feed_items(added_to_feed_at DESC);
CREATE INDEX IF NOT EXISTS idx_videos_channel ON videos(channel_id);
CREATE INDEX IF NOT EXISTS idx_downloaded_last_served ON downloaded_videos(last_served_at);
CREATE INDEX IF NOT EXISTS idx_sponsor_segments_video ON sponsor_segments(video_id);

CREATE TABLE IF NOT EXISTS manga_series (
  id TEXT PRIMARY KEY,
  source TEXT NOT NULL,
  source_url TEXT NOT NULL,
  title TEXT NOT NULL,
  author TEXT,
  description TEXT,
  cover_path TEXT,
  status TEXT,
  total_chapters INTEGER DEFAULT 0,
  added_at INTEGER NOT NULL,
  last_synced_at INTEGER
);

CREATE TABLE IF NOT EXISTS manga_arcs (
  id TEXT PRIMARY KEY,
  series_id TEXT NOT NULL REFERENCES manga_series(id) ON DELETE CASCADE,
  title TEXT NOT NULL,
  arc_order INTEGER NOT NULL,
  chapter_start INTEGER,
  chapter_end INTEGER
);

CREATE TABLE IF NOT EXISTS manga_chapters (
  id TEXT PRIMARY KEY,
  series_id TEXT NOT NULL REFERENCES manga_series(id) ON DELETE CASCADE,
  arc_id TEXT REFERENCES manga_arcs(id),
  number REAL NOT NULL,
  title TEXT,
  source_url TEXT NOT NULL,
  page_count INTEGER DEFAULT 0,
  is_available INTEGER DEFAULT 1,
  published_at INTEGER,
  added_at INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_manga_chapters_series_num
  ON manga_chapters(series_id, number);

CREATE TABLE IF NOT EXISTS manga_pages (
  id TEXT PRIMARY KEY,
  chapter_id TEXT NOT NULL REFERENCES manga_chapters(id) ON DELETE CASCADE,
  page_number INTEGER NOT NULL,
  source_url TEXT NOT NULL,
  local_path TEXT,
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
  series_id TEXT,
  status TEXT NOT NULL,
  total_chapters INTEGER DEFAULT 0,
  done_chapters INTEGER DEFAULT 0,
  total_pages INTEGER DEFAULT 0,
  done_pages INTEGER DEFAULT 0,
  error_message TEXT,
  started_at INTEGER NOT NULL,
  finished_at INTEGER
);

-- Durable ingest queue. The channel poll only ENQUEUEs newly-seen video ids
-- of subscribed channels here (fast: RSS + insert); a worker drains them,
-- running the metadata/SponsorBlock pipeline off the request loop.
-- locked_at NULL = claimable; a stale lock (crashed worker) is reclaimed;
-- attempts bounds retries so a permanently-failing video is dropped.
-- channel_id is carried because only subscribed channels are ingested.
CREATE TABLE IF NOT EXISTS ingest_queue (
  video_id   TEXT PRIMARY KEY,
  channel_id TEXT NOT NULL,
  queued_at  INTEGER NOT NULL,
  attempts   INTEGER DEFAULT 0,
  last_error TEXT,
  locked_at  INTEGER,
  source     TEXT   -- 'subscription'
);
CREATE INDEX IF NOT EXISTS idx_ingest_queue_pending
  ON ingest_queue(queued_at) WHERE locked_at IS NULL;

-- Daily news briefing cache. Key = "<date>|<lang>|<sorted topics>|<city>",
-- payload = JSON BriefingItem[]. One row per day/topic-set/city combo.
CREATE TABLE IF NOT EXISTS news_briefings (
  key TEXT PRIMARY KEY,
  created_at INTEGER NOT NULL,
  payload TEXT NOT NULL
);
