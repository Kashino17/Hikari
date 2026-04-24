'use client'

import { useState } from 'react'
import { mockVideos } from '@/lib/mock-data'
import { Bookmark, Play } from 'lucide-react'

const savedVideos = mockVideos.filter(v => v.saved)

function formatDuration(sec: number) {
  const m = Math.floor(sec / 60)
  const s = sec % 60
  return `${m}:${s.toString().padStart(2, '0')}`
}

const categoryLabels: Record<string, string> = {
  math: 'Mathematik', science: 'Wissenschaft', philosophy: 'Philosophie',
  tech: 'Technik', society: 'Gesellschaft', art: 'Kunst', history: 'Geschichte',
}

export default function EditorialSaved() {
  const [dark] = useState(false)

  const textPrimary = dark ? '#F0EAE0' : '#1A1817'
  const textSecondary = dark ? 'rgba(240,234,224,0.5)' : 'rgba(26,24,23,0.5)'
  const accent = '#14532D'
  const divider = dark ? 'rgba(255,255,255,0.08)' : '#E8E4DC'

  return (
    <div style={{ fontFamily: 'var(--font-geist-sans)' }}>
      {/* Header */}
      <div className="px-5 pt-6 pb-4" style={{ borderBottom: `1px solid ${divider}` }}>
        <h1
          className="text-3xl mb-1"
          style={{ fontFamily: 'var(--font-fraunces)', fontWeight: 400, color: textPrimary }}
        >
          Gespeichert
        </h1>
        <p className="text-sm" style={{ color: textSecondary }}>
          {savedVideos.length} Videos in deiner Bibliothek
        </p>
      </div>

      {/* Saved videos — editorial: article list, not grid */}
      <div>
        {savedVideos.map((video, i) => (
          <article
            key={video.videoId}
            className="px-5 py-5 cursor-pointer group transition-all"
            style={{
              borderBottom: `1px solid ${divider}`,
              background: 'transparent',
            }}
          >
            <div className="flex gap-4">
              {/* Thumbnail */}
              <div
                className="relative flex-shrink-0 rounded-sm overflow-hidden"
                style={{ width: 88, height: 120 }}
              >
                <div className={`w-full h-full bg-gradient-to-br ${video.thumbnailGradient}`} />
                <div className="absolute inset-0 flex items-center justify-center opacity-0 group-hover:opacity-100 transition-opacity">
                  <div className="w-8 h-8 rounded-full flex items-center justify-center" style={{ background: 'rgba(255,255,255,0.9)' }}>
                    <Play size={14} fill={accent} color={accent} className="ml-0.5" />
                  </div>
                </div>
                <div className="absolute top-1.5 left-1.5">
                  <Bookmark size={11} fill={accent} color={accent} />
                </div>
              </div>

              {/* Info */}
              <div className="flex-1 min-w-0">
                <div className="text-[10px] uppercase tracking-widest font-semibold mb-1.5" style={{ color: accent }}>
                  {categoryLabels[video.category]} · {video.channel}
                </div>
                <h2
                  className="text-base leading-snug mb-2 line-clamp-3"
                  style={{ fontFamily: 'var(--font-fraunces)', fontWeight: 500, color: textPrimary }}
                >
                  {video.title}
                </h2>
                <p
                  className="text-xs leading-relaxed mb-2 line-clamp-2 italic"
                  style={{ color: textSecondary }}
                >
                  {video.aiReasoning}
                </p>
                <div className="flex items-center gap-3">
                  <span className="text-[10px] font-mono" style={{ color: textSecondary }}>
                    {formatDuration(video.durationSec)}
                  </span>
                  <span className="text-[10px] font-mono" style={{ color: accent }}>
                    Score {video.aiScore}
                  </span>
                </div>
              </div>
            </div>
          </article>
        ))}
      </div>
    </div>
  )
}
