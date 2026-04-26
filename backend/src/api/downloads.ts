import type { FastifyInstance } from "fastify";
import type Database from "better-sqlite3";

export interface DownloadsDeps {
  db: Database.Database;
  diskLimitBytes: number;
}

interface VideoRow {
  id: string;
  title: string;
  description: string | null;
  thumbnail_url: string | null;
  duration_seconds: number;
  season: number | null;
  episode: number | null;
  is_movie: number;
  series_id: string | null;
  channel_id: string;
  series_title: string | null;
  series_thumbnail: string | null;
  channel_title: string;
  channel_thumbnail: string | null;
  channel_banner: string | null;
  size_bytes: number;
  downloaded_at: number;
}

export interface MovieEntry {
  id: string;
  title: string;
  thumbnail_url: string | null;
  duration_seconds: number;
  size_bytes: number;
  downloaded_at: number;
}

export interface SeriesEpisode {
  id: string;
  title: string;
  season: number | null;
  episode: number | null;
  duration_seconds: number;
  thumbnail_url: string | null;
  size_bytes: number;
  downloaded_at: number;
}

export interface SeriesGroup {
  id: string;
  title: string;
  thumbnail_url: string | null;
  episode_count: number;
  total_bytes: number;
  episodes: SeriesEpisode[];
}

export interface ChannelVideoEntry {
  id: string;
  title: string;
  duration_seconds: number;
  thumbnail_url: string | null;
  size_bytes: number;
  downloaded_at: number;
}

export interface ChannelGroup {
  id: string;
  title: string;
  thumbnail_url: string | null;
  banner_url: string | null;
  video_count: number;
  total_bytes: number;
  videos: ChannelVideoEntry[];
}

export interface DownloadsResponse {
  total_bytes: number;
  limit_bytes: number;
  series: SeriesGroup[];
  channels: ChannelGroup[];
  movies: MovieEntry[];
}

export async function registerDownloadsRoutes(
  app: FastifyInstance,
  deps: DownloadsDeps,
): Promise<void> {
  app.get("/downloads", async () => {
    const rows = deps.db
      .prepare(
        `SELECT
            v.id,
            v.title,
            v.description,
            v.thumbnail_url,
            v.duration_seconds,
            v.season,
            v.episode,
            v.is_movie,
            v.series_id,
            v.channel_id,
            s.title AS series_title,
            s.thumbnail_url AS series_thumbnail,
            c.title AS channel_title,
            c.thumbnail_url AS channel_thumbnail,
            c.banner_url AS channel_banner,
            dv.file_size_bytes AS size_bytes,
            dv.downloaded_at
         FROM downloaded_videos dv
         JOIN videos v ON v.id = dv.video_id
         JOIN channels c ON c.id = v.channel_id
         LEFT JOIN series s ON s.id = v.series_id
         ORDER BY dv.downloaded_at DESC`,
      )
      .all() as VideoRow[];

    const movies: MovieEntry[] = [];
    const seriesMap = new Map<string, SeriesGroup>();
    const channelMap = new Map<string, ChannelGroup>();
    let total = 0;

    for (const r of rows) {
      total += r.size_bytes;
      if (r.is_movie === 1) {
        movies.push({
          id: r.id,
          title: r.title,
          thumbnail_url: r.thumbnail_url,
          duration_seconds: r.duration_seconds,
          size_bytes: r.size_bytes,
          downloaded_at: r.downloaded_at,
        });
      } else if (r.series_id) {
        let g = seriesMap.get(r.series_id);
        if (!g) {
          g = {
            id: r.series_id,
            title: r.series_title ?? "Unbekannte Serie",
            thumbnail_url: r.series_thumbnail ?? r.thumbnail_url,
            episode_count: 0,
            total_bytes: 0,
            episodes: [],
          };
          seriesMap.set(r.series_id, g);
        }
        g.episodes.push({
          id: r.id,
          title: r.title,
          season: r.season,
          episode: r.episode,
          duration_seconds: r.duration_seconds,
          thumbnail_url: r.thumbnail_url,
          size_bytes: r.size_bytes,
          downloaded_at: r.downloaded_at,
        });
        g.episode_count += 1;
        g.total_bytes += r.size_bytes;
        // Series fallback: if series has no manual cover, use first episode's thumbnail
        if (!g.thumbnail_url && r.thumbnail_url) g.thumbnail_url = r.thumbnail_url;
      } else {
        let g = channelMap.get(r.channel_id);
        if (!g) {
          g = {
            id: r.channel_id,
            title: r.channel_title,
            thumbnail_url: r.channel_thumbnail,
            banner_url: r.channel_banner,
            video_count: 0,
            total_bytes: 0,
            videos: [],
          };
          channelMap.set(r.channel_id, g);
        }
        g.videos.push({
          id: r.id,
          title: r.title,
          duration_seconds: r.duration_seconds,
          thumbnail_url: r.thumbnail_url,
          size_bytes: r.size_bytes,
          downloaded_at: r.downloaded_at,
        });
        g.video_count += 1;
        g.total_bytes += r.size_bytes;
      }
    }

    // Sort episodes within each series by season then episode (asc).
    for (const g of seriesMap.values()) {
      g.episodes.sort((a, b) => {
        const sa = a.season ?? 0;
        const sb = b.season ?? 0;
        if (sa !== sb) return sa - sb;
        return (a.episode ?? 0) - (b.episode ?? 0);
      });
    }

    const response: DownloadsResponse = {
      total_bytes: total,
      limit_bytes: deps.diskLimitBytes,
      series: Array.from(seriesMap.values()).sort((a, b) => b.total_bytes - a.total_bytes),
      channels: Array.from(channelMap.values()).sort((a, b) => b.total_bytes - a.total_bytes),
      movies: movies.sort((a, b) => b.downloaded_at - a.downloaded_at),
    };
    return response;
  });
}
