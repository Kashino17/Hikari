'use client'

import { useState } from 'react'
import { mockVideos } from '@/lib/mock-data'
import { Bookmark, Heart, MoreVertical, Play, SkipForward, ChevronRight } from 'lucide-react'

const video = mockVideos[0]
const queueVideos = mockVideos.slice(1, 5)

function formatDuration(sec: number) {
  const m = Math.floor(sec / 60)
  const s = sec % 60
  return `${m}:${s.toString().padStart(2, '0')}`
}

const categoryColors: Record<string, string> = {
  math: '#06B6D4', science: '#10B981', philosophy: '#F59E0B',
  tech: '#8B5CF6', society: '#EF4444', art: '#EC4899', history: '#F97316',
}

export default function DashboardFeed() {
  const [dark] = useState(true)
  const [tab, setTab] = useState<'new' | 'old'>('new')
  const [progress, setProgress] = useState(0.33)
  const [liked, setLiked] = useState(false)
  const [saved, setSaved] = useState(false)

  const textPrimary = dark ? '#fff' : '#0A0A0A'
  const textMuted = dark ? 'rgba(255,255,255,0.4)' : 'rgba(0,0,0,0.4)'
  const cardBg = dark ? 'rgba(255,255,255,0.04)' : 'rgba(0,0,0,0.03)'
  const cardBorder = dark ? 'rgba(255,255,255,0.08)' : '#E5E5E5'
  const violet = '#8B5CF6'

  return (
    <div className="p-3" style={{ fontFamily: 'var(--font-geist-sans)' }}>
      {/* Tab row */}
      <div className="flex gap-1 mb-3">
        {(['new', 'old'] as const).map(t => (
          <button
            key={t}
            onClick={() => setTab(t)}
            className="px-3 py-1.5 rounded-sm text-[10px] font-mono uppercase tracking-widest transition-all"
            style={{
              background: tab === t ? 'rgba(139,92,246,0.15)' : cardBg,
              border: `1px solid ${tab === t ? 'rgba(139,92,246,0.3)' : cardBorder}`,
              color: tab === t ? violet : textMuted,
            }}
          >
            {t === 'new' ? 'NEU' : 'ARCHIV'}
          </button>
        ))}
        <div className="ml-auto flex items-center gap-1 px-2 py-1 rounded-sm" style={{ background: cardBg, border: `1px solid ${cardBorder}` }}>
          <span className="text-[9px] font-mono" style={{ color: violet }}>8</span>
          <span className="text-[9px] font-mono" style={{ color: textMuted }}>/15</span>
        </div>
      </div>

      {/* Main bento grid */}
      <div className="grid gap-2" style={{ gridTemplateColumns: '1fr 1fr', gridTemplateRows: 'auto' }}>

        {/* Hero video — full width */}
        <div
          className="col-span-2 rounded-sm overflow-hidden relative"
          style={{ aspectRatio: '16/9', border: `1px solid ${cardBorder}` }}
        >
          <div className={`absolute inset-0 bg-gradient-to-br ${video.thumbnailGradient}`} />
          <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-transparent to-transparent" />

          {/* Play button */}
          <div className="absolute inset-0 flex items-center justify-center">
            <div className="w-12 h-12 rounded-sm flex items-center justify-center" style={{ background: 'rgba(139,92,246,0.25)', border: '1px solid rgba(139,92,246,0.4)', backdropFilter: 'blur(8px)' }}>
              <Play size={18} fill="white" color="white" className="ml-0.5" />
            </div>
          </div>

          {/* Top badges */}
          <div className="absolute top-2 left-2 flex gap-1">
            <span className="text-[8px] font-mono px-1.5 py-0.5 rounded-sm" style={{ background: 'rgba(0,0,0,0.7)', color: violet }}>
              SCORE {video.aiScore}
            </span>
            <span
              className="text-[8px] font-mono px-1.5 py-0.5 rounded-sm uppercase"
              style={{ background: 'rgba(0,0,0,0.7)', color: categoryColors[video.category] || '#fff' }}
            >
              {video.category}
            </span>
          </div>

          <div className="absolute top-2 right-2 text-[8px] font-mono px-1.5 py-0.5 rounded-sm" style={{ background: 'rgba(0,0,0,0.7)', color: 'rgba(255,255,255,0.6)' }}>
            {formatDuration(video.durationSec)}
          </div>

          {/* Bottom info */}
          <div className="absolute bottom-0 left-0 right-0 p-3">
            <div className="text-[9px] font-mono mb-1 uppercase tracking-widest" style={{ color: violet }}>{video.channel}</div>
            <div className="text-sm font-medium leading-tight text-white line-clamp-2">{video.title}</div>
          </div>
        </div>

        {/* Scrubber card — full width */}
        <div
          className="col-span-2 rounded-sm p-3"
          style={{ background: cardBg, border: `1px solid ${cardBorder}` }}
        >
          <div className="flex items-center justify-between mb-2">
            <span className="text-[9px] font-mono" style={{ color: textMuted }}>POSITION</span>
            <span className="text-[9px] font-mono" style={{ color: textMuted }}>{formatDuration(Math.floor(video.durationSec * progress))} / {formatDuration(video.durationSec)}</span>
          </div>
          <div
            className="relative h-2 rounded-sm cursor-pointer overflow-hidden"
            style={{ background: dark ? 'rgba(255,255,255,0.06)' : '#E5E5E5' }}
            onClick={(e) => {
              const rect = e.currentTarget.getBoundingClientRect()
              setProgress((e.clientX - rect.left) / rect.width)
            }}
          >
            <div
              className="absolute top-0 left-0 h-full rounded-sm transition-all"
              style={{ width: `${progress * 100}%`, background: violet }}
            />
          </div>
          {/* Seek controls */}
          <div className="flex items-center gap-2 mt-2">
            <button className="text-[9px] font-mono px-2 py-1 rounded-sm transition-all" style={{ background: 'rgba(255,255,255,0.05)', color: textMuted }}>−5s</button>
            <button className="text-[9px] font-mono px-2 py-1 rounded-sm transition-all" style={{ background: 'rgba(255,255,255,0.05)', color: textMuted }}>+5s</button>
            <div className="ml-auto flex gap-1">
              <button onClick={() => setLiked(!liked)} className="p-1.5 rounded-sm transition-all" style={{ background: liked ? 'rgba(139,92,246,0.15)' : 'rgba(255,255,255,0.05)', border: `1px solid ${liked ? 'rgba(139,92,246,0.3)' : 'transparent'}` }}>
                <Heart size={11} fill={liked ? violet : 'none'} color={liked ? violet : textMuted} />
              </button>
              <button onClick={() => setSaved(!saved)} className="p-1.5 rounded-sm transition-all" style={{ background: saved ? 'rgba(139,92,246,0.15)' : 'rgba(255,255,255,0.05)', border: `1px solid ${saved ? 'rgba(139,92,246,0.3)' : 'transparent'}` }}>
                <Bookmark size={11} fill={saved ? violet : 'none'} color={saved ? violet : textMuted} />
              </button>
              <button className="p-1.5 rounded-sm" style={{ background: 'rgba(255,255,255,0.05)' }}>
                <MoreVertical size={11} color={textMuted} />
              </button>
            </div>
          </div>
        </div>

        {/* AI Score card */}
        <div className="rounded-sm p-3" style={{ background: 'rgba(139,92,246,0.08)', border: '1px solid rgba(139,92,246,0.2)' }}>
          <div className="text-[8px] font-mono uppercase tracking-widest mb-1" style={{ color: 'rgba(139,92,246,0.6)' }}>AI SCORE</div>
          <div className="text-3xl font-mono leading-none" style={{ color: violet }}>{video.aiScore}</div>
          <div className="text-[8px] font-mono mt-1" style={{ color: 'rgba(139,92,246,0.5)' }}>/100</div>
        </div>

        {/* Category card */}
        <div className="rounded-sm p-3" style={{ background: cardBg, border: `1px solid ${cardBorder}` }}>
          <div className="text-[8px] font-mono uppercase tracking-widest mb-1" style={{ color: textMuted }}>KATEGORIE</div>
          <div
            className="text-xs font-mono uppercase"
            style={{ color: categoryColors[video.category] || textPrimary }}
          >
            {video.category}
          </div>
          <div className="text-[8px] font-mono mt-1" style={{ color: textMuted }}>{video.channel}</div>
        </div>

        {/* AI Reasoning — full width */}
        <div className="col-span-2 rounded-sm p-3" style={{ background: cardBg, border: `1px solid ${cardBorder}` }}>
          <div className="text-[8px] font-mono uppercase tracking-widest mb-1.5" style={{ color: violet }}>AI REASONING</div>
          <p className="text-[11px] leading-relaxed" style={{ color: textMuted }}>{video.aiReasoning}</p>
        </div>

        {/* Queue preview */}
        <div className="col-span-2 rounded-sm" style={{ border: `1px solid ${cardBorder}` }}>
          <div className="flex items-center justify-between px-3 py-2" style={{ borderBottom: `1px solid ${cardBorder}` }}>
            <span className="text-[8px] font-mono uppercase tracking-widest" style={{ color: textMuted }}>WARTESCHLANGE</span>
            <span className="text-[8px] font-mono" style={{ color: violet }}>7 verbleibend</span>
          </div>
          {queueVideos.slice(0, 3).map((qv, i) => (
            <div
              key={qv.videoId}
              className="flex items-center gap-2 px-3 py-2 cursor-pointer"
              style={{ borderBottom: i < 2 ? `1px solid ${cardBorder}` : 'none' }}
            >
              <div className="flex-shrink-0 w-1 h-1 rounded-full" style={{ background: categoryColors[qv.category] || '#fff' }} />
              <span className="text-[10px] flex-1 truncate" style={{ color: textPrimary }}>{qv.title}</span>
              <span className="text-[8px] font-mono flex-shrink-0" style={{ color: violet }}>{qv.aiScore}</span>
              <ChevronRight size={10} style={{ color: textMuted, flexShrink: 0 }} />
            </div>
          ))}
        </div>
      </div>
    </div>
  )
}
