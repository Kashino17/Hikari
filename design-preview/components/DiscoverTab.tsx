'use client'

import { useMemo, useState, useEffect } from 'react'
import { Plus, Sparkles, CheckCircle2 } from 'lucide-react'
import { allChannels, mockVideos, type MockChannel, type MockVideo } from '@/lib/mock-data'
import { useHikariStore } from '@/lib/store'
import { cn } from '@/lib/utils'

// ─── Types ───────────────────────────────────────────────────────────────────

interface DiscoverChannel {
  channel: MockChannel
  matchScore: number       // 0–100
  topVideos: MockVideo[]   // up to 3
}

// ─── Helpers ─────────────────────────────────────────────────────────────────

function topVideosFor(channel: MockChannel): MockVideo[] {
  return mockVideos
    .filter((v) => v.channel === channel.name)
    .sort((a, b) => b.aiScore - a.aiScore)
    .slice(0, 3)
}

// Category-only match: 90 if user already follows that category, 35 otherwise.
// Neutral 50 when the user has no subs yet so the badges still mean something.
function computeMatchScore(
  channel: MockChannel,
  subscribedCategories: Set<string>,
): number {
  if (subscribedCategories.size === 0) return 50
  return subscribedCategories.has(channel.category) ? 90 : 35
}

// Avatar = gradient circle with channel initial (no photo assets in mock data)
function ChannelAvatar({ channel }: { channel: MockChannel }) {
  const initial = channel.name.charAt(0).toUpperCase()
  return (
    <div
      className="w-11 h-11 rounded-full flex items-center justify-center text-[15px] font-medium text-black shrink-0"
      style={{ background: channel.accentColor }}
      aria-hidden
    >
      {initial}
    </div>
  )
}

function MatchBadge({ score }: { score: number }) {
  const tier =
    score >= 80 ? 'high' : score >= 55 ? 'mid' : 'low'
  return (
    <span
      className={cn(
        'inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[10px] font-mono tracking-wider',
        tier === 'high' && 'bg-accent-soft text-accent border border-[var(--color-accent-border)]',
        tier === 'mid' && 'bg-surface border-hairline text-mute',
        tier === 'low' && 'bg-transparent border-hairline text-faint',
      )}
    >
      <Sparkles size={10} strokeWidth={2} />
      {score}% Match
    </span>
  )
}

function ThumbnailStrip({ videos, accent }: { videos: MockVideo[]; accent: string }) {
  // Always render 3 slots — fallback gradient uses channel accent if videos < 3
  const slots = [0, 1, 2].map((i) => videos[i])
  return (
    <div className="grid grid-cols-3 gap-1.5 mt-3">
      {slots.map((v, i) =>
        v ? (
          <div
            key={v.videoId}
            className={cn(
              'aspect-video rounded-sm bg-gradient-to-br relative overflow-hidden',
              v.thumbnailGradient,
            )}
            title={v.title}
          >
            <div className="absolute inset-0 bg-black/10" />
          </div>
        ) : (
          <div
            key={`empty-${i}`}
            className="aspect-video rounded-sm border-hairline opacity-30"
            style={{ background: `${accent}10` }}
            aria-hidden
          />
        ),
      )}
    </div>
  )
}

// ─── Main Component ──────────────────────────────────────────────────────────

export default function DiscoverTab({ embedded = false }: { embedded?: boolean } = {}) {
  const subscribed = useHikariStore((s) => s.subscribedChannelIds)
  const subscribe = useHikariStore((s) => s.subscribe)
  const addPrefetched = useHikariStore((s) => s.addPrefetched)
  const [toast, setToast] = useState<string | null>(null)

  useEffect(() => {
    if (!toast) return
    const t = setTimeout(() => setToast(null), 1800)
    return () => clearTimeout(t)
  }, [toast])

  const subscribedCategories = useMemo(() => {
    const cats = new Set<string>()
    for (const id of subscribed) {
      const c = allChannels.find((ch) => ch.id === id)
      if (c) cats.add(c.category)
    }
    return cats
  }, [subscribed])

  const discoverChannels = useMemo<DiscoverChannel[]>(() => {
    return allChannels
      .filter((c) => !subscribed.includes(c.id))
      .map((channel) => ({
        channel,
        matchScore: computeMatchScore(channel, subscribedCategories),
        topVideos: topVideosFor(channel),
      }))
      .sort((a, b) => b.matchScore - a.matchScore)
  }, [subscribed, subscribedCategories])

  // Option B: prefetched videos live in their own store slice (prefetchedByChannel),
  // get cleared automatically when the channel is unfollowed via store.unsubscribe.
  function handleFollowChannel(channelId: string) {
    const ch = allChannels.find((c) => c.id === channelId)
    if (!ch) return
    const topIds = topVideosFor(ch).map((v) => v.videoId)
    subscribe(channelId)
    addPrefetched(channelId, topIds)
    setToast(
      topIds.length > 0
        ? `${ch.name} · ${topIds.length} Videos in Bibliothek`
        : `${ch.name} hinzugefügt`,
    )
  }

  return (
    <div className="min-h-svh">
      {/* Header — only when standalone; in /discover the parent tab bar handles framing */}
      {!embedded && (
        <header
          className="sticky top-0 z-20 bg-[var(--color-bg)]/85 backdrop-blur border-b-hairline"
          style={{ paddingTop: 'env(safe-area-inset-top, 0)' }}
        >
          <div className="px-5 py-4">
            <h1 className="text-base font-medium tracking-tight">Entdecken</h1>
            <p className="text-faint text-[11px] mt-0.5">
              Kuratierte Kanäle, die zu deinem Profil passen
            </p>
          </div>
        </header>
      )}

      {/* List */}
      <section className="px-5 py-4">
        {discoverChannels.length === 0 ? (
          <div className="flex flex-col items-center justify-center py-16 px-6 text-center">
            <div className="w-12 h-12 rounded-full bg-accent-soft border border-[var(--color-accent-border)] flex items-center justify-center mb-4">
              <CheckCircle2 size={20} className="text-accent" strokeWidth={1.5} />
            </div>
            <div className="text-[14px] font-medium mb-1">Alle Kanäle gefolgt</div>
            <p className="text-faint text-[12px] leading-relaxed max-w-[260px]">
              Du folgst bereits allen kuratierten Kanälen. Schau im Video-Tab für
              einzelne Empfehlungen.
            </p>
          </div>
        ) : (
          <ul className="flex flex-col gap-3">
            {discoverChannels.map(({ channel, matchScore, topVideos }) => (
              <li
                key={channel.id}
                className="rounded-lg border-hairline bg-white/[0.02] p-4"
              >
                <div className="flex items-start gap-3">
                  <ChannelAvatar channel={channel} />
                  <div className="min-w-0 flex-1">
                    <div className="flex items-center justify-between gap-2">
                      <div className="text-[14px] font-medium truncate">
                        {channel.name}
                      </div>
                      <MatchBadge score={matchScore} />
                    </div>
                    <div className="text-faint text-[11px] truncate mt-0.5">
                      {channel.handle} · {channel.subscribers}
                    </div>
                    <div className="text-mute text-[12px] mt-1.5">
                      {channel.description}
                    </div>
                  </div>
                </div>

                <ThumbnailStrip videos={topVideos} accent={channel.accentColor} />

                <button
                  onClick={() => handleFollowChannel(channel.id)}
                  className="mt-3 w-full flex items-center justify-center gap-1.5 py-2.5 rounded-md bg-accent text-black text-[12px] font-medium hover:bg-accent/90 transition-colors"
                >
                  <Plus size={14} strokeWidth={2.5} />
                  Diesem Kanal folgen
                </button>
              </li>
            ))}
          </ul>
        )}
      </section>

      {/* Toast */}
      {toast && (
        <div className="fixed left-1/2 -translate-x-1/2 bottom-24 z-50 px-4 py-2 bg-surface-2 border-hairline rounded-md text-[12px]">
          {toast}
        </div>
      )}
    </div>
  )
}
