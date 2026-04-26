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
  auto_approve INTEGER DEFAULT 0
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
  is_movie INTEGER DEFAULT 0
);

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

CREATE TABLE IF NOT EXISTS filter_config (
  id INTEGER PRIMARY KEY CHECK (id = 1),
  filter_json TEXT NOT NULL,
  prompt_override TEXT,
  updated_at INTEGER NOT NULL
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
