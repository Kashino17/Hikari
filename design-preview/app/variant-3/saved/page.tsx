'use client'

import { useState } from 'react'
import { mockVideos } from '@/lib/mock-data'
import { Bookmark, Filter } from 'lucide-react'

const savedVideos = mockVideos.filter(v => v.saved)

function formatDuration(sec: number) {
  const m = Math.floor(sec / 60)
  const s = sec % 60
  return `${m}:${s.toString().padStart(2, '0')}`
}

const categoryColors: Record<string, string> = {
  math: '#06B6D4', science: '#10B981', philosophy: '#F59E0B',
  tech: '#8B5CF6', society: '#EF4444', art: '#EC4899', history: '#F97316',
}

export default function DashboardSaved() {
  const [dark] = useState(true)
  const [filter, setFilter] = useState<string>('all')

  const textPrimary = dark ? '#fff' : '#0A0A0A'
  const textMuted = dark ? 'rgba(255,255,255,0.4)' : 'rgba(0,0,0,0.4)'
  const cardBg = dark ? 'rgba(255,255,255,0.04)' : 'rgba(0,0,0,0.03)'
  const cardBorder = dark ? 'rgba(255,255,255,0.08)' : '#E5E5E5'
  const violet = '#8B5CF6'

  const categories = ['all', ...Array.from(new Set(savedVideos.map(v => v.category)))]

  const filtered = filter === 'all' ? savedVideos : savedVideos.filter(v => v.category === filter)

  // Summary stats
  const totalDuration = savedVideos.reduce((acc, v) => acc + v.durationSec, 0)
  const avgScore = Math.round(savedVideos.reduce((acc, v) => acc + v.aiScore, 0) / savedVideos.length)

  return (
    <div className="p-3" style={{ fontFamily: 'var(--font-geist-sans)' }}>
      {/* Stats row */}
      <div className="grid grid-cols-3 gap-2 mb-3">
        <div className="rounded-sm p-2.5" style={{ background: 'rgba(139,92,246,0.08)', border: '1px solid rgba(139,92,246,0.2)' }}>
          <div className="text-[7px] font-mono uppercase tracking-widest mb-1" style={{ color: 'rgba(139,92,246,0.6)' }}>GESPEICHERT</div>
          <div className="text-2xl font-mono leading-none" style={{ color: violet }}>{savedVideos.length}</div>
        </div>
        <div className="rounded-sm p-2.5" style={{ background: cardBg, border: `1px solid ${cardBorder}` }}>
          <div className="text-[7px] font-mono uppercase tracking-widest mb-1" style={{ color: textMuted }}>AVG SCORE</div>
          <div className="text-xl font-mono leading-none" style={{ color: textPrimary }}>{avgScore}</div>
        </div>
        <div className="rounded-sm p-2.5" style={{ background: cardBg, border: `1px solid ${cardBorder}` }}>
          <div className="text-[7px] font-mono uppercase tracking-widest mb-1" style={{ color: textMuted }}>GESAMT</div>
          <div className="text-sm font-mono leading-none" style={{ color: textPrimary }}>{formatDuration(totalDuration)}</div>
        </div>
      </div>

      {/* Category filters */}
      <div className="flex gap-1 mb-3 flex-wrap">
        {categories.map(cat => (
          <button
            key={cat}
            onClick={() => setFilter(cat)}
            className="px-2 py-1 rounded-sm text-[8px] font-mono uppercase tracking-widest transition-all"
            style={{
              background: filter === cat ? 'rgba(139,92,246,0.15)' : cardBg,
              border: `1px solid ${filter === cat ? 'rgba(139,92,246,0.3)' : cardBorder}`,
              color: filter === cat ? violet : textMuted,
            }}
          >
            {cat}
          </button>
        ))}
      </div>

      {/* Video grid — bento */}
      <div className="grid grid-cols-2 gap-2">
        {filtered.map((video, i) => (
          <div
            key={video.videoId}
            className={`rounded-sm overflow-hidden cursor-pointer transition-all ${i === 0 ? 'col-span-2' : ''}`}
            style={{ border: `1px solid ${cardBorder}` }}
          >
            {/* Thumbnail */}
            <div
              className={`relative ${i === 0 ? 'h-36' : 'h-24'} overflow-hidden`}
            >
              <div className={`absolute inset-0 bg-gradient-to-br ${video.thumbnailGradient}`} />
              <div className="absolute inset-0 bg-gradient-to-t from-black/60 to-transparent" />

              {/* Badges */}
              <div className="absolute top-1.5 left-1.5 flex gap-1">
                <span
                  className="text-[7px] font-mono px-1 py-0.5 rounded-sm uppercase"
                  style={{ background: 'rgba(0,0,0,0.75)', color: categoryColors[video.category] || '#fff' }}
                >
                  {video.category}
                </span>
              </div>
              <div className="absolute top-1.5 right-1.5">
                <span className="text-[7px] font-mono px-1 py-0.5 rounded-sm" style={{ background: 'rgba(0,0,0,0.75)', color: violet }}>
                  {video.aiScore}
                </span>
              </div>
              <div className="absolute bottom-1.5 right-1.5">
                <span className="text-[7px] font-mono px-1 py-0.5 rounded-sm" style={{ background: 'rgba(0,0,0,0.75)', color: 'rgba(255,255,255,0.6)' }}>
                  {formatDuration(video.durationSec)}
                </span>
              </div>
              <div className="absolute top-1.5 left-1/2 -translate-x-1/2">
                <Bookmark size={9} fill={violet} color={violet} />
              </div>
            </div>

            {/* Info */}
            <div className="p-2">
              <div className="text-[8px] font-mono uppercase tracking-widest mb-0.5" style={{ color: violet }}>{video.channel}</div>
              <div className={`font-medium leading-tight ${i === 0 ? 'text-xs' : 'text-[10px]'} line-clamp-2`} style={{ color: textPrimary }}>
                {video.title}
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
