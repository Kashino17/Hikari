// Discovery API client.
//
// Two implementations live here:
//   1. `fetchDiscoveryCandidates`     — local mock, returns *video-shaped* data
//      that the current `DiscoveryCandidates` UI consumes directly.
//   2. `fetchDiscoveryFromBackend`    — real backend call to GET /discovery.
//      Returns *channel-shaped* data (the backend scores channels, not videos).
//
// They have intentionally different return types because the data models are
// genuinely different: switching to the real backend is a UI refactor too,
// not just an import swap. See README at bottom of file for migration notes.

import { mockVideos, type VideoCategory } from './mock-data'
import type { ScoreWeights } from './discovery-tuning'

export interface ScoreBreakdown {
  category: number   // 0–100 — how well the video's category matches the user's tuning
  similarity: number // 0–100 — similarity to videos the user has already saved
  quality: number    // 0–100 — Hikari curation score (aiScore in mock)
}

export interface ScoredCandidate {
  videoId: string
  scores: ScoreBreakdown
}

export interface DiscoveryContext {
  savedVideoIds: string[]
  subscribedCategories: Set<VideoCategory>
}

// Deterministic "hash" so mock similarity scores don't shuffle between renders.
function hashString(s: string): number {
  let h = 0
  for (let i = 0; i < s.length; i++) h = (h * 31 + s.charCodeAt(i)) | 0
  return Math.abs(h)
}

export async function fetchDiscoveryCandidates(
  ctx: DiscoveryContext,
): Promise<ScoredCandidate[]> {
  // Simulate network latency so the loading state is visible
  await new Promise((r) => setTimeout(r, 400))

  // "Similarity" mock: count how many of the user's saved videos sit in each
  // category; videos in heavily-saved categories score higher.
  const savedCategoryCounts = new Map<string, number>()
  for (const id of ctx.savedVideoIds) {
    const v = mockVideos.find((mv) => mv.videoId === id)
    if (v) savedCategoryCounts.set(v.category, (savedCategoryCounts.get(v.category) ?? 0) + 1)
  }
  const totalSaved = Math.max(
    1,
    Array.from(savedCategoryCounts.values()).reduce((a, b) => a + b, 0),
  )

  return mockVideos
    .filter((v) => !ctx.savedVideoIds.includes(v.videoId))
    .map<ScoredCandidate>((v) => {
      const category = ctx.subscribedCategories.has(v.category) ? 88 : 32
      const share = (savedCategoryCounts.get(v.category) ?? 0) / totalSaved
      const jitter = hashString(v.videoId) % 14
      const similarity = Math.min(95, Math.max(25, Math.round(share * 70) + jitter + 25))
      return {
        videoId: v.videoId,
        scores: { category, similarity, quality: v.aiScore },
      }
    })
}

// ─── Real Backend Client ─────────────────────────────────────────────────────
//
// Mirrors backend `/discovery` GET response shape (see backend/src/routes/discovery.ts
// and backend/src/discovery/discoveryEngine.ts). The backend returns scored
// CHANNELS, with a 4-axis breakdown that includes `longForm` (anti-doom-scroll
// signal) — one axis more than the mock.

export interface BackendScoreBreakdown {
  category: number   // 0..1
  similarity: number // 0..1
  quality: number    // 0..1
  longForm: number   // 0..1
}

export interface BackendChannelCandidate {
  id: string
  title: string
  thumbnailUrl: string | null
  bannerUrl: string | null
  subscribers: number | null
  videoCount: number
  longFormRatio: number
  avgOverallScore: number
  avgEducationalValue: number
  avgClickbaitRisk: number
  categoryDistribution: Record<string, number>
  score: number       // 0..100
  breakdown: BackendScoreBreakdown
}

export interface BackendDiscoveryResponse {
  results: BackendChannelCandidate[]
  meta: {
    limit: number
    followedCount: number
    candidatePoolSize: number
    qualityThreshold: number
    categoryWeights: Record<string, number>
    longFormMinSeconds: number
  }
}

export interface BackendFetchOptions {
  backendUrl: string
  limit?: number
  longFormMinSeconds?: number
  signal?: AbortSignal
}

export async function fetchDiscoveryFromBackend(
  opts: BackendFetchOptions,
): Promise<BackendDiscoveryResponse> {
  const url = new URL('/discovery', opts.backendUrl)
  if (opts.limit !== undefined) url.searchParams.set('limit', String(opts.limit))
  if (opts.longFormMinSeconds !== undefined) {
    url.searchParams.set('longFormMinSeconds', String(opts.longFormMinSeconds))
  }
  const res = await fetch(url, { signal: opts.signal })
  if (!res.ok) {
    throw new Error(`Discovery API ${res.status}: ${await res.text()}`)
  }
  return res.json() as Promise<BackendDiscoveryResponse>
}

/**
 * Push tuning settings to backend's `/discovery/settings` PUT endpoint.
 *
 * Field names use the wire format (camelCase), translated from our local
 * snake_case `DiscoverySettings`. Backend persists these in `discovery_settings`
 * table and uses them on subsequent /discovery calls.
 *
 * Note: backend `discovery_settings` does NOT currently store `scoreWeights`
 * (axis weights are hardcoded to SCORE_WEIGHTS in discoveryEngine.ts).
 * The `weights` arg is a no-op until the backend is extended — kept here so
 * the call site doesn't need to change later.
 */
export async function pushDiscoverySettings(
  backendUrl: string,
  settings: {
    discoveryRatio?: number      // 0..1 — backend uses 0..1, frontend uses 0..100
    qualityThreshold?: number    // 0..100
    categoryWeights?: Record<string, number>
    weights?: ScoreWeights       // not yet honored by backend — see comment above
  },
): Promise<void> {
  const url = new URL('/discovery/settings', backendUrl)
  const res = await fetch(url, {
    method: 'PUT',
    headers: { 'content-type': 'application/json' },
    body: JSON.stringify(settings),
  })
  if (!res.ok) {
    throw new Error(`Discovery settings PUT ${res.status}: ${await res.text()}`)
  }
}

// ─── Migration Notes ─────────────────────────────────────────────────────────
//
// Switching DiscoveryCandidates.tsx from mock → backend requires:
//
//   1. New "channel-as-candidate" rendering: thumbnail = channel.thumbnailUrl,
//      title = channel.title, no per-video duration. Or: a second backend call
//      to fetch each channel's top video, then render those (would need a new
//      `/channels/:id/top-videos` endpoint — doesn't exist yet).
//
//   2. Score breakdown: 4 bars instead of 3 (add `longForm`). Backend values
//      are 0..1, multiply by 100 for display.
//
//   3. Follow handler: subscribe(channel.id), but `addPrefetched` won't work
//      until the backend exposes top-video IDs per channel.
//
//   4. Score-weighting persistence: extend backend `discovery_settings` table
//      with `score_weights` JSON column, expose in PUT route, then
//      `pushDiscoverySettings({ weights })` becomes load-bearing.
