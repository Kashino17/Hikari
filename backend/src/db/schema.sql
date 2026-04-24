CREATE TABLE IF NOT EXISTS channels (
  id TEXT PRIMARY KEY,
  url TEXT NOT NULL,
  title TEXT NOT NULL,
  added_at INTEGER NOT NULL,
  is_active INTEGER DEFAULT 1,
  last_polled_at INTEGER
);

CREATE TABLE IF NOT EXISTS videos (
  id TEXT PRIMARY KEY,
  channel_id TEXT NOT NULL REFERENCES channels(id),
  title TEXT NOT NULL,
  description TEXT,
  published_at INTEGER NOT NULL,
  duration_seconds INTEGER NOT NULL,
  aspect_ratio TEXT,
  default_language TEXT,
  thumbnail_url TEXT,
  transcript TEXT,
  discovered_at INTEGER NOT NULL
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
  playback_failed INTEGER DEFAULT 0
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
