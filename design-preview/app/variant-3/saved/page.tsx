'use client'

import { useState } from 'react'
import { mockVideos } from '@/lib/mock-data'
import { useHikariStore } from '@/lib/store'
import { Bookmark, X } from 'lucide-react'

function formatDuration(sec: number) {
  const m = Math.floor(sec / 60)
  const s = sec % 60
  return `${m}:${s.toString().padStart(2, '0')}`
}

const categoryColors: Record<string, string> = {
  math: '#06B6D4',
  science: '#10B981',
  philosophy: '#F59E0B',
  tech: '#8B5CF6',
  society: '#EF4444',
  art: '#EC4899',
  history: '#F97316',
}

interface Toast { id: number; text: string }

export default function DashboardSaved() {
  const { savedVideoIds, toggleSaved, theme } = useHikariStore()
  const dark = theme === 'dark'
  const [filter, setFilter] = useState<string>('all')
  const [toast, setToast] = useState<Toast | null>(null)
  const [playingId, setPlayingId] = useState<string | null>(null)

  const textPrimary = dark ? '#fff' : '#0A0A0A'
  const textMuted = dark ? 'rgba(255,255,255,0.4)' : 'rgba(0,0,0,0.45)'
  const cardBg = dark ? 'rgba(255,255,255,0.04)' : 'rgba(0,0,0,0.025)'
  const cardBorder = dark ? 'rgba(255,255,255,0.08)' : '#e5e5e5'
  const violet = '#8B5CF6'

  const savedVideos = mockVideos.filter((v) => savedVideoIds.includes(v.videoId))
  const categories = ['all', ...Array.from(new Set(savedVideos.map((v) => v.category)))]
  const filtered = filter === 'all' ? savedVideos : savedVideos.filter((v) => v.category === filter)

  const totalDuration = savedVideos.reduce((acc, v) => acc + v.durationSec, 0)
  const avgScore = savedVideos.length > 0
    ? Math.round(savedVideos.reduce((acc, v) => acc + v.aiScore, 0) / savedVideos.length)
    : 0

  const showToast = (text: string) => {
    const id = Date.now()
    setToast({ id, text })
    setTimeout(() => setToast(null), 2000)
  }

  const handleRemove = (e: React.MouseEvent, videoId: string, title: string) => {
    e.stopPropagation()
    toggleSaved(videoId)
    showToast(`Entfernt: ${title.slice(0, 28)}…`)
  }

  return (
    <div className="p-3 pb-6" style={{ fontFamily: 'var(--font-geist-sans)' }}>
      {/* Toast */}
      {toast && (
        <div
          className="fixed bottom-24 left-1/2 -translate-x-1/2 z-50 px-4 py-2 rounded-md font-mono text-[11px] tracking-wide"
          style={{ background: 'rgba(239,68,68,0.2)', border: '1px solid rgba(239,68,68,0.4)', color: '#EF4444', backdropFilter: 'blur(12px)', whiteSpace: 'nowrap' }}
        >
          {toast.text}
        </div>
      )}

      {/* Playing modal */}
      {playingId && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center"
          style={{ background: 'rgba(0,0,0,0.85)', backdropFilter: 'blur(12px)' }}
          onClick={() => setPlayingId(null)}
        >
          {(() => {
            const v = mockVideos.find((x) => x.videoId === playingId)
            if (!v) return null
            return (
              <div
                className="relative rounded-md overflow-hidden"
                style={{ width: '80vw', maxWidth: 320, border: `1px solid rgba(139,92,246,0.3)` }}
                onClick={(e) => e.stopPropagation()}
              >
                <div className={`w-full bg-gradient-to-br ${v.thumbnailGradient}`} style={{ aspectRatio: '9/16', maxHeight: '60vh' }} />
                <div className="p-3" style={{ background: dark ? '#111' : '#fff' }}>
                  <div className="text-[9px] font-mono uppercase tracking-widest mb-1" style={{ color: violet }}>{v.channel}</div>
                  <div className="text-sm font-medium" style={{ color: textPrimary }}>{v.title}</div>
                </div>
                <button
                  className="absolute top-2 right-2 w-7 h-7 flex items-center justify-center rounded-md"
                  style={{ background: 'rgba(0,0,0,0.6)', border: '1px solid rgba(255,255,255,0.15)' }}
                  onClick={() => setPlayingId(null)}
                >
                  <X size={12} color="white" strokeWidth={1.5} />
                </button>
              </div>
            )
          })()}
        </div>
      )}

      {/* Stats row */}
      <div className="grid grid-cols-3 gap-2 mb-3">
        <div className="rounded-md p-2.5" style={{ background: 'rgba(139,92,246,0.08)', border: '1px solid rgba(139,92,246,0.2)' }}>
          <div className="text-[7px] font-mono uppercase tracking-widest mb-1" style={{ color: 'rgba(139,92,246,0.6)' }}>GESPEICHERT</div>
          <div className="text-2xl font-mono leading-none" style={{ color: violet }}>{savedVideos.length}</div>
        </div>
        <div className="rounded-md p-2.5" style={{ background: cardBg, border: `1px solid ${cardBorder}` }}>
          <div className="text-[7px] font-mono uppercase tracking-widest mb-1" style={{ color: textMuted }}>AVG SCORE</div>
          <div className="text-xl font-mono leading-none" style={{ color: textPrimary }}>{avgScore}</div>
        </div>
        <div className="rounded-md p-2.5" style={{ background: cardBg, border: `1px solid ${cardBorder}` }}>
          <div className="text-[7px] font-mono uppercase tracking-widest mb-1" style={{ color: textMuted }}>GESAMT</div>
          <div className="text-sm font-mono leading-none" style={{ color: textPrimary }}>{formatDuration(totalDuration)}</div>
        </div>
      </div>

      {/* Category filters */}
      <div className="flex gap-1 mb-3 flex-wrap">
        {categories.map((cat) => (
          <button
            key={cat}
            onClick={() => setFilter(cat)}
            className="px-2 py-1 rounded-md text-[8px] font-mono uppercase tracking-widest transition-all"
            style={{
              background: filter === cat ? 'rgba(139,92,246,0.15)' : cardBg,
              border: `1px solid ${filter === cat ? 'rgba(139,92,246,0.35)' : cardBorder}`,
              color: filter === cat ? violet : textMuted,
            }}
          >
            {cat === 'all' ? 'ALLE' : cat}
          </button>
        ))}
      </div>

      {/* Video bento grid */}
      {filtered.length === 0 ? (
        <div className="rounded-md p-8 text-center" style={{ background: cardBg, border: `1px dashed ${cardBorder}` }}>
          <div className="text-[11px] font-mono" style={{ color: textMuted }}>Keine gespeicherten Videos</div>
        </div>
      ) : (
        <div className="grid gap-2" style={{ gridTemplateColumns: 'repeat(2, 1fr)' }}>
          {filtered.map((video, i) => (
            <div
              key={video.videoId}
              className={`rounded-md overflow-hidden cursor-pointer group relative transition-all ${i === 0 && filter === 'all' ? 'col-span-2' : ''}`}
              style={{ border: `1px solid ${cardBorder}` }}
              onClick={() => setPlayingId(video.videoId)}
            >
              {/* Thumbnail */}
              <div
                className={`relative overflow-hidden`}
                style={{ aspectRatio: '9/16', maxHeight: i === 0 && filter === 'all' ? 200 : 140 }}
              >
                <div className={`absolute inset-0 bg-gradient-to-br ${video.thumbnailGradient}`} />
                <div className="absolute inset-0 bg-gradient-to-t from-black/60 to-transparent" />

                {/* SAVED badge */}
                <div className="absolute top-1.5 left-1.5">
                  <span
                    className="text-[7px] font-mono px-1.5 py-0.5 rounded-sm tracking-wider"
                    style={{ background: 'rgba(139,92,246,0.3)', border: '1px solid rgba(139,92,246,0.4)', color: '#8B5CF6' }}
                  >
                    SAVED
                  </span>
                </div>

                <div className="absolute top-1.5 right-1.5">
                  <span className="text-[7px] font-mono px-1 py-0.5 rounded-sm" style={{ background: 'rgba(0,0,0,0.7)', color: violet }}>
                    {video.aiScore}
                  </span>
                </div>

                <div className="absolute bottom-1.5 right-1.5">
                  <span className="text-[7px] font-mono px-1 py-0.5 rounded-sm" style={{ background: 'rgba(0,0,0,0.7)', color: 'rgba(255,255,255,0.6)' }}>
                    {formatDuration(video.durationSec)}
                  </span>
                </div>

                {/* Remove button — on hover/right-click */}
                <button
                  className="absolute top-1.5 left-1/2 -translate-x-1/2 opacity-0 group-hover:opacity-100 transition-opacity w-6 h-6 flex items-center justify-center rounded-sm"
                  style={{ background: 'rgba(239,68,68,0.25)', border: '1px solid rgba(239,68,68,0.4)' }}
                  onClick={(e) => handleRemove(e, video.videoId, video.title)}
                >
                  <X size={10} color="#EF4444" strokeWidth={2} />
                </button>
              </div>

              {/* Info */}
              <div className="p-2" style={{ background: dark ? 'rgba(255,255,255,0.03)' : '#fff' }}>
                <div className="text-[7px] font-mono uppercase tracking-widest mb-0.5" style={{ color: violet }}>
                  {video.channel}
                </div>
                <div className="text-[10px] font-medium leading-tight line-clamp-2" style={{ color: textPrimary }}>
                  {video.title}
                </div>
                <div className="text-[8px] font-mono mt-1" style={{ color: textMuted }}>
                  {video.channel} · {formatDuration(video.durationSec)}
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}
