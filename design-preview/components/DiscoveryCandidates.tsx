'use client'

import { useEffect, useMemo, useState } from 'react'
import { Plus, Check, RefreshCw, Sparkles, Inbox, AlertCircle } from 'lucide-react'
import {
  mockVideos,
  allChannels,
  type MockVideo,
  type MockChannel,
  type VideoCategory,
} from '@/lib/mock-data'
import { useHikariStore } from '@/lib/store'
import {
  fetchDiscoveryCandidates,
  type ScoredCandidate,
  type ScoreBreakdown,
} from '@/lib/discovery-api'
import { cn } from '@/lib/utils'

// ─── Helpers ─────────────────────────────────────────────────────────────────

function formatDuration(s: number): string {
  const m = Math.floor(s / 60)
  const sec = s % 60
  return `${m}:${String(sec).padStart(2, '0')}`
}

function findChannelByName(name: string): MockChannel | undefined {
  return allChannels.find((c) => c.name === name)
}

// Category-as-gate: off-category videos must clear a high quality bar to surface.
// Off-category (cat < 50): only quality counts, capped at 50 so a perfect off-cat
// video lands around the median of on-category content. On-category: weighted
// blend favoring category, then balanced sim/quality.
function combinedScore(s: ScoreBreakdown): number {
  if (s.category < 50) return Math.round(s.quality * 0.5)
  return Math.round(s.category * 0.4 + s.similarity * 0.3 + s.quality * 0.3)
}

// ─── Score Bar ───────────────────────────────────────────────────────────────

function ScoreBar({ label, value }: { label: string; value: number }) {
  return (
    <div className="flex items-center gap-2 text-[10px] leading-none">
      <span className="text-faint w-9 shrink-0 uppercase tracking-wider">{label}</span>
      <div className="flex-1 h-[3px] bg-white/[0.06] rounded-full overflow-hidden">
        <div
          className={cn(
            'h-full rounded-full transition-all',
            value >= 70 ? 'bg-accent' : value >= 40 ? 'bg-white/40' : 'bg-white/15',
          )}
          style={{ width: `${value}%` }}
        />
      </div>
      <span className="text-mute font-mono w-7 text-right">{value}</span>
    </div>
  )
}

// ─── Candidate Row ───────────────────────────────────────────────────────────

interface RowProps {
  video: MockVideo
  channel: MockChannel | undefined
  scores: ScoreBreakdown
  isFollowing: boolean
  onFollow: (channelId: string) => void
}

function CandidateRow({ video, channel, scores, isFollowing, onFollow }: RowProps) {
  return (
    <li className="flex gap-3 p-3 rounded-lg border-hairline bg-white/[0.02]">
      {/* Thumbnail */}
      <div
        className={cn(
          'w-[120px] aspect-video rounded-md bg-gradient-to-br relative overflow-hidden shrink-0',
          video.thumbnailGradient,
        )}
      >
        <div className="absolute inset-0 bg-black/15" />
        <div className="absolute bottom-1 right-1 px-1.5 py-0.5 rounded-sm bg-black/70 text-[9px] font-mono">
          {formatDuration(video.durationSec)}
        </div>
      </div>

      {/* Body */}
      <div className="flex-1 min-w-0 flex flex-col">
        <div className="text-[12px] font-medium leading-tight line-clamp-2">
          {video.title}
        </div>
        <div className="text-faint text-[10px] mt-0.5 truncate">{video.channel}</div>

        <div className="flex flex-col gap-1 mt-2">
          <ScoreBar label="Cat" value={scores.category} />
          <ScoreBar label="Sim" value={scores.similarity} />
          <ScoreBar label="Qual" value={scores.quality} />
        </div>

        <button
          onClick={() => channel && onFollow(channel.id)}
          disabled={!channel || isFollowing}
          className={cn(
            'mt-2.5 inline-flex items-center justify-center gap-1 px-2.5 py-1.5 rounded-md text-[11px] font-medium transition-colors w-fit',
            isFollowing
              ? 'bg-accent-soft text-accent border border-[var(--color-accent-border)]'
              : 'bg-accent text-black hover:bg-accent/90',
            !channel && 'opacity-40 cursor-not-allowed',
          )}
        >
          {isFollowing ? (
            <>
              <Check size={11} strokeWidth={2.5} /> Folgst du
            </>
          ) : (
            <>
              <Plus size={11} strokeWidth={2.5} /> Folgen
            </>
          )}
        </button>
      </div>
    </li>
  )
}

// ─── Main Component ──────────────────────────────────────────────────────────

export default function DiscoveryCandidates({
  embedded = false,
}: { embedded?: boolean } = {}) {
  const subscribedChannelIds = useHikariStore((s) => s.subscribedChannelIds)
  const savedVideoIds = useHikariStore((s) => s.savedVideoIds)
  const subscribe = useHikariStore((s) => s.subscribe)
  const addPrefetched = useHikariStore((s) => s.addPrefetched)

  const [candidates, setCandidates] = useState<ScoredCandidate[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [reloadKey, setReloadKey] = useState(0)

  // Build the API context from the latest store state
  const context = useMemo(() => {
    const cats = new Set<VideoCategory>()
    for (const id of subscribedChannelIds) {
      const c = allChannels.find((ch) => ch.id === id)
      if (c) cats.add(c.category)
    }
    return { savedVideoIds, subscribedCategories: cats }
    // Intentional: we want a snapshot; refetches happen via reloadKey, not on every save.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [reloadKey])

  useEffect(() => {
    let cancelled = false
    setLoading(true)
    setError(null)
    fetchDiscoveryCandidates(context)
      .then((data) => {
        if (!cancelled) setCandidates(data)
      })
      .catch((e: unknown) => {
        if (!cancelled) setError(e instanceof Error ? e.message : 'Unbekannter Fehler')
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    return () => {
      cancelled = true
    }
  }, [context])

  const sorted = useMemo(
    () =>
      [...candidates].sort((a, b) => combinedScore(b.scores) - combinedScore(a.scores)),
    [candidates],
  )

  function handleFollow(channelId: string) {
    const ch = allChannels.find((c) => c.id === channelId)
    if (!ch) return
    const topIds = mockVideos
      .filter((v) => v.channel === ch.name)
      .sort((a, b) => b.aiScore - a.aiScore)
      .slice(0, 3)
      .map((v) => v.videoId)
    subscribe(channelId)
    addPrefetched(channelId, topIds)
  }

  return (
    <div className="min-h-svh">
      {/* Standalone header — the /discover tab parent renders its own framing,
          so when embedded we keep just an inline refresh button (below). */}
      {!embedded && (
        <header
          className="sticky top-0 z-20 bg-[var(--color-bg)]/85 backdrop-blur border-b-hairline"
          style={{ paddingTop: 'env(safe-area-inset-top, 0)' }}
        >
          <div className="px-5 py-4 flex items-start justify-between gap-3">
            <div className="min-w-0">
              <h1 className="text-base font-medium tracking-tight flex items-center gap-1.5">
                <Sparkles size={14} className="text-accent" strokeWidth={2} />
                Empfehlungen
              </h1>
              <p className="text-faint text-[11px] mt-0.5">
                Gescorte Kandidaten aus der Discovery API
              </p>
            </div>
            <button
              onClick={() => setReloadKey((k) => k + 1)}
              disabled={loading}
              className="shrink-0 w-8 h-8 flex items-center justify-center rounded-md border-hairline bg-surface text-mute hover:text-white disabled:opacity-40"
              aria-label="Neu laden"
            >
              <RefreshCw size={14} strokeWidth={1.5} className={cn(loading && 'animate-spin')} />
            </button>
          </div>
        </header>
      )}

      {/* Body */}
      <section className="px-5 py-4">
        {embedded && (
          <div className="flex justify-end mb-2">
            <button
              onClick={() => setReloadKey((k) => k + 1)}
              disabled={loading}
              className="inline-flex items-center gap-1.5 px-2 py-1 rounded-md border-hairline bg-surface text-[10px] text-mute hover:text-white disabled:opacity-40"
              aria-label="Neu laden"
            >
              <RefreshCw size={11} strokeWidth={1.5} className={cn(loading && 'animate-spin')} />
              Neu laden
            </button>
          </div>
        )}
        {loading && candidates.length === 0 && (
          <div className="flex flex-col items-center justify-center py-16 text-center">
            <div className="w-8 h-8 border-2 border-white/10 border-t-accent rounded-full animate-spin mb-3" />
            <div className="text-faint text-[12px]">Lade Empfehlungen…</div>
          </div>
        )}

        {error && !loading && (
          <div className="flex flex-col items-center justify-center py-12 px-6 text-center">
            <div className="w-12 h-12 rounded-full bg-[rgba(248,113,113,0.08)] border border-[rgba(248,113,113,0.3)] flex items-center justify-center mb-4">
              <AlertCircle size={20} className="text-[var(--color-danger)]" strokeWidth={1.5} />
            </div>
            <div className="text-[14px] font-medium mb-1">Konnte nicht laden</div>
            <p className="text-faint text-[12px] leading-relaxed max-w-[260px] mb-4">{error}</p>
            <button
              onClick={() => setReloadKey((k) => k + 1)}
              className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-md bg-surface border-hairline text-[11px] hover:text-white text-mute transition-colors"
            >
              <RefreshCw size={11} strokeWidth={1.5} />
              Erneut versuchen
            </button>
          </div>
        )}

        {!loading && !error && sorted.length === 0 && (
          <div className="flex flex-col items-center justify-center py-16 px-6 text-center">
            <div className="w-12 h-12 rounded-full bg-surface border-hairline flex items-center justify-center mb-4">
              <Inbox size={20} className="text-faint" strokeWidth={1.5} />
            </div>
            <div className="text-[14px] font-medium mb-1">Alles in der Bibliothek</div>
            <p className="text-faint text-[12px] leading-relaxed max-w-[260px]">
              Keine neuen Empfehlungen gerade. Probier später nochmal oder schau im
              Kanäle-Tab nach neuen Quellen.
            </p>
          </div>
        )}

        {sorted.length > 0 && (
          <ul className="flex flex-col gap-2.5">
            {sorted.map((c) => {
              const video = mockVideos.find((v) => v.videoId === c.videoId)
              if (!video) return null
              const channel = findChannelByName(video.channel)
              const isFollowing = channel
                ? subscribedChannelIds.includes(channel.id)
                : false
              return (
                <CandidateRow
                  key={c.videoId}
                  video={video}
                  channel={channel}
                  scores={c.scores}
                  isFollowing={isFollowing}
                  onFollow={handleFollow}
                />
              )
            })}
          </ul>
        )}
      </section>
    </div>
  )
}
