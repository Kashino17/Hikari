'use client'

import { useHikariStore } from '@/lib/store'
import { rejectedVideos } from '@/lib/mock-data'

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

export default function DashboardRejected() {
  const { theme } = useHikariStore()
  const dark = theme === 'dark'

  const textPrimary = dark ? '#fff' : '#0A0A0A'
  const textMuted = dark ? 'rgba(255,255,255,0.4)' : 'rgba(0,0,0,0.45)'
  const cardBg = dark ? 'rgba(255,255,255,0.03)' : 'rgba(0,0,0,0.02)'
  const cardBorder = dark ? 'rgba(255,255,255,0.07)' : '#e5e5e5'

  return (
    <div className="p-3 pb-8" style={{ fontFamily: 'var(--font-geist-sans)' }}>
      {/* Header */}
      <div
        className="flex items-center justify-between rounded-md px-3 py-2.5 mb-3"
        style={{ background: 'rgba(239,68,68,0.07)', border: '1px solid rgba(239,68,68,0.18)' }}
      >
        <span className="text-[9px] font-mono uppercase tracking-widest" style={{ color: '#EF4444' }}>
          Abgelehnt
        </span>
        <span className="text-lg font-mono leading-none" style={{ color: '#EF4444' }}>
          {rejectedVideos.length}
        </span>
      </div>

      {/* List */}
      <div className="space-y-2">
        {rejectedVideos.map((video) => (
          <div
            key={video.videoId}
            className="flex gap-3 rounded-md overflow-hidden p-2.5"
            style={{ background: cardBg, border: `1px solid ${cardBorder}` }}
          >
            {/* Thumbnail */}
            <div
              className="flex-shrink-0 rounded-sm overflow-hidden relative"
              style={{ width: 52, height: 72 }}
            >
              <div className={`absolute inset-0 bg-gradient-to-br ${video.thumbnailGradient}`} />
              <div className="absolute inset-0 bg-black/30" />
            </div>

            {/* Content */}
            <div className="flex-1 min-w-0">
              <div className="flex items-start justify-between gap-2 mb-1">
                <div className="flex-1 min-w-0">
                  <div className="text-[10px] font-medium leading-snug line-clamp-2" style={{ color: textPrimary }}>
                    {video.title}
                  </div>
                  <div className="text-[8px] font-mono mt-0.5" style={{ color: textMuted }}>
                    {video.channel} · {formatDuration(video.durationSec)}
                  </div>
                </div>

                {/* Score badge */}
                <div
                  className="flex-shrink-0 px-1.5 py-1 rounded-md text-center"
                  style={{ background: 'rgba(239,68,68,0.15)', border: '1px solid rgba(239,68,68,0.3)' }}
                >
                  <div className="font-mono text-[13px] leading-none" style={{ color: '#EF4444' }}>
                    {video.aiScore}
                  </div>
                  <div className="text-[6px] font-mono" style={{ color: 'rgba(239,68,68,0.6)' }}>/100</div>
                </div>
              </div>

              {/* Chips */}
              <div className="flex flex-wrap gap-1 mb-1.5">
                <span
                  className="text-[7px] font-mono px-1.5 py-0.5 rounded-sm uppercase"
                  style={{ background: 'rgba(0,0,0,0.2)', color: categoryColors[video.category] || '#fff', border: `1px solid ${categoryColors[video.category]}33` }}
                >
                  {video.category}
                </span>
                {video.clickbaitScore !== undefined && video.clickbaitScore >= 7 && (
                  <span
                    className="text-[7px] font-mono px-1.5 py-0.5 rounded-sm"
                    style={{ background: 'rgba(239,68,68,0.12)', border: '1px solid rgba(239,68,68,0.25)', color: '#EF4444' }}
                  >
                    clickbait {video.clickbaitScore}/10
                  </span>
                )}
                {video.manipulationScore !== undefined && video.manipulationScore >= 7 && (
                  <span
                    className="text-[7px] font-mono px-1.5 py-0.5 rounded-sm"
                    style={{ background: 'rgba(251,146,60,0.12)', border: '1px solid rgba(251,146,60,0.25)', color: '#FB923C' }}
                  >
                    manipulation {video.manipulationScore}/10
                  </span>
                )}
              </div>

              {/* Reasoning */}
              <div className="text-[9px] italic leading-relaxed" style={{ color: textMuted }}>
                {video.aiReasoning}
              </div>
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
