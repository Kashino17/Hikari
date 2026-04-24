'use client'

import { useState } from 'react'
import { mockVideos } from '@/lib/mock-data'
import { Bookmark, Play, ChevronDown, ChevronUp } from 'lucide-react'

const video = mockVideos[0]
const queueVideos = mockVideos.slice(1, 5)

function formatDuration(sec: number) {
  const m = Math.floor(sec / 60)
  const s = sec % 60
  return `${m}:${s.toString().padStart(2, '0')}`
}

const categoryLabels: Record<string, string> = {
  math: 'Mathematik', science: 'Wissenschaft', philosophy: 'Philosophie',
  tech: 'Technik', society: 'Gesellschaft', art: 'Kunst', history: 'Geschichte',
}

export default function EditorialFeed() {
  const [tab, setTab] = useState<'new' | 'old'>('new')
  const [saved, setSaved] = useState(false)
  const [playing, setPlaying] = useState(false)
  const [progress, setProgress] = useState(0.33)
  const [dark] = useState(false)

  const textPrimary = dark ? '#F0EAE0' : '#1A1817'
  const textSecondary = dark ? 'rgba(240,234,224,0.5)' : 'rgba(26,24,23,0.5)'
  const accent = '#14532D'
  const bg = dark ? '#1A1817' : '#FAFAF9'
  const divider = dark ? 'rgba(255,255,255,0.08)' : '#E8E4DC'

  return (
    <div style={{ background: bg, fontFamily: 'var(--font-geist-sans)' }}>
      {/* Tab selector — editorial: underline style */}
      <div className="flex items-center px-5 pt-5 pb-0 gap-6" style={{ borderBottom: `1px solid ${divider}` }}>
        {(['new', 'old'] as const).map((t) => (
          <button
            key={t}
            onClick={() => setTab(t)}
            className="pb-3 text-sm font-medium transition-all relative"
            style={{
              color: tab === t ? accent : textSecondary,
              fontFamily: 'var(--font-geist-sans)',
            }}
          >
            {t === 'new' ? 'Neu' : 'Archiv'}
            {tab === t && (
              <span
                className="absolute bottom-0 left-0 right-0 h-0.5 rounded-full"
                style={{ background: accent }}
              />
            )}
          </button>
        ))}
        <span className="ml-auto mb-3 text-[10px] font-mono" style={{ color: textSecondary }}>8 / 15 heute</span>
      </div>

      {/* Hero video article */}
      <article className="px-5 pt-6 pb-0">
        {/* Category + score */}
        <div className="flex items-center gap-3 mb-3">
          <span className="text-[11px] uppercase tracking-widest font-semibold" style={{ color: accent }}>
            {categoryLabels[video.category] || video.category}
          </span>
          <span style={{ color: divider }}>·</span>
          <span className="text-[11px] uppercase tracking-wide" style={{ color: textSecondary }}>
            {video.channel}
          </span>
          <div className="ml-auto flex items-center gap-1">
            <span className="text-xs font-mono" style={{ color: accent }}>
              {video.aiScore}
            </span>
            <span className="text-[10px]" style={{ color: textSecondary }}>/100</span>
          </div>
        </div>

        {/* Title — serif, big */}
        <h1
          className="text-[26px] leading-tight mb-3"
          style={{ fontFamily: 'var(--font-fraunces)', fontWeight: 500, color: textPrimary }}
        >
          {video.title}
        </h1>

        {/* Byline */}
        <div className="flex items-center gap-2 mb-4" style={{ borderBottom: `1px solid ${divider}`, paddingBottom: 16 }}>
          <div className="w-10 h-px flex-none" style={{ background: textPrimary }} />
          <span className="text-[11px]" style={{ color: textSecondary }}>
            {formatDuration(video.durationSec)} · {video.channel}
          </span>
        </div>

        {/* Video thumbnail */}
        <div
          className="relative rounded-sm overflow-hidden mb-4 cursor-pointer"
          style={{ aspectRatio: '16/9' }}
          onClick={() => setPlaying(!playing)}
        >
          <div className={`absolute inset-0 bg-gradient-to-br ${video.thumbnailGradient}`} />
          <div className="absolute inset-0 flex items-center justify-center">
            <div
              className="w-14 h-14 rounded-full flex items-center justify-center transition-all"
              style={{ background: 'rgba(255,255,255,0.15)', backdropFilter: 'blur(8px)', border: '1px solid rgba(255,255,255,0.3)' }}
            >
              <Play size={22} fill="white" color="white" className="ml-1" />
            </div>
          </div>
          {/* Duration badge */}
          <div
            className="absolute bottom-2 right-2 px-1.5 py-0.5 rounded text-[10px] font-mono"
            style={{ background: 'rgba(0,0,0,0.7)', color: '#fff' }}
          >
            {formatDuration(video.durationSec)}
          </div>
        </div>

        {/* Scrubber — subtle, editorial */}
        <div className="mb-5">
          <div
            className="relative h-px cursor-pointer group"
            style={{ background: divider }}
            onClick={(e) => {
              const rect = e.currentTarget.getBoundingClientRect()
              setProgress((e.clientX - rect.left) / rect.width)
            }}
          >
            <div
              className="absolute top-0 left-0 h-full transition-all"
              style={{ width: `${progress * 100}%`, background: accent }}
            />
            <div
              className="absolute top-1/2 -translate-y-1/2 w-2.5 h-2.5 rounded-full opacity-0 group-hover:opacity-100 transition-opacity"
              style={{ left: `calc(${progress * 100}% - 5px)`, background: accent }}
            />
          </div>
          <div className="flex justify-between mt-1.5">
            <span className="text-[10px] font-mono" style={{ color: textSecondary }}>
              {formatDuration(Math.floor(video.durationSec * progress))}
            </span>
            <span className="text-[10px] font-mono" style={{ color: textSecondary }}>
              {formatDuration(video.durationSec)}
            </span>
          </div>
        </div>

        {/* AI reasoning — editorial sidebar note style */}
        <div
          className="rounded-sm p-4 mb-5 relative"
          style={{ background: dark ? 'rgba(20,83,45,0.12)' : '#F0F7F2', borderLeft: `3px solid ${accent}` }}
        >
          <div className="text-[10px] uppercase tracking-widest font-semibold mb-1.5" style={{ color: accent }}>
            KI-Einschätzung
          </div>
          <p className="text-sm leading-relaxed" style={{ color: textSecondary, fontStyle: 'italic' }}>
            &ldquo;{video.aiReasoning}&rdquo;
          </p>
          <div className="mt-2 flex items-center gap-2">
            <div className="flex items-center gap-1">
              <div className="w-1 h-1 rounded-full" style={{ background: accent }} />
              <span className="text-[10px] font-mono" style={{ color: accent }}>Score {video.aiScore}/100</span>
            </div>
          </div>
        </div>

        {/* Actions */}
        <div className="flex items-center gap-4 pb-5" style={{ borderBottom: `1px solid ${divider}` }}>
          <button
            onClick={() => setSaved(!saved)}
            className="flex items-center gap-1.5 text-xs transition-all"
            style={{ color: saved ? accent : textSecondary }}
          >
            <Bookmark size={14} fill={saved ? accent : 'none'} />
            {saved ? 'Gespeichert' : 'Speichern'}
          </button>
        </div>
      </article>

      {/* Queue — next videos as article list */}
      <section className="px-5 pt-4 pb-8">
        <div className="flex items-center gap-2 mb-4">
          <ChevronDown size={12} style={{ color: textSecondary }} />
          <span className="text-[11px] uppercase tracking-widest" style={{ color: textSecondary }}>Weiter in der Warteschlange</span>
        </div>

        <div className="space-y-0">
          {queueVideos.map((qv, i) => (
            <div
              key={qv.videoId}
              className="flex gap-3 py-4 transition-all cursor-pointer"
              style={{ borderTop: i === 0 ? 'none' : `1px solid ${divider}` }}
            >
              <div
                className="flex-shrink-0 rounded-sm overflow-hidden"
                style={{ width: 72, height: 48 }}
              >
                <div className={`w-full h-full bg-gradient-to-br ${qv.thumbnailGradient}`} />
              </div>
              <div className="flex-1 min-w-0">
                <div className="text-[9px] uppercase tracking-widest mb-0.5 font-medium" style={{ color: accent }}>
                  {qv.channel}
                </div>
                <div className="text-sm leading-tight line-clamp-2 mb-1" style={{ color: textPrimary, fontFamily: 'var(--font-fraunces)', fontWeight: 400 }}>
                  {qv.title}
                </div>
                <div className="text-[10px] font-mono" style={{ color: textSecondary }}>
                  {formatDuration(qv.durationSec)} · Score {qv.aiScore}
                </div>
              </div>
            </div>
          ))}
        </div>
      </section>
    </div>
  )
}
