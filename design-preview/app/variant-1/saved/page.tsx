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

export default function CinemaSaved() {
  const [dark] = useState(true)
  const [selected, setSelected] = useState<string | null>(null)

  return (
    <div
      className="min-h-full"
      style={{ fontFamily: 'var(--font-geist-sans)' }}
    >
      {/* Header */}
      <div className="px-5 pt-6 pb-4">
        <h1
          className="text-3xl font-light mb-1"
          style={{ color: dark ? '#fff' : '#1a0e00' }}
        >
          Gespeichert
        </h1>
        <p className="text-sm" style={{ color: dark ? 'rgba(255,255,255,0.35)' : 'rgba(0,0,0,0.4)' }}>
          {savedVideos.length} Videos
        </p>
      </div>

      {/* Grid */}
      <div className="px-5 pb-6">
        <div className="grid grid-cols-2 gap-3">
          {savedVideos.map((video) => (
            <div
              key={video.videoId}
              className="relative rounded-lg overflow-hidden cursor-pointer transition-all"
              style={{
                aspectRatio: '9/14',
                outline: selected === video.videoId ? '2px solid #F5A524' : '2px solid transparent',
                transform: selected === video.videoId ? 'scale(0.98)' : 'scale(1)',
              }}
              onClick={() => setSelected(selected === video.videoId ? null : video.videoId)}
            >
              {/* Thumbnail gradient */}
              <div className={`absolute inset-0 bg-gradient-to-br ${video.thumbnailGradient}`} />
              <div className="absolute inset-0 bg-gradient-to-t from-black/90 via-black/10 to-transparent" />

              {/* Play button */}
              <div className="absolute inset-0 flex items-center justify-center">
                <div
                  className="w-10 h-10 rounded-full flex items-center justify-center transition-all"
                  style={{ background: selected === video.videoId ? '#F5A524' : 'rgba(255,255,255,0.12)', backdropFilter: 'blur(8px)' }}
                >
                  <Play size={14} fill={selected === video.videoId ? '#000' : '#fff'} color={selected === video.videoId ? '#000' : '#fff'} className="ml-0.5" />
                </div>
              </div>

              {/* AI score badge */}
              <div
                className="absolute top-2 right-2 flex items-center gap-1 rounded-full px-1.5 py-0.5"
                style={{ background: 'rgba(0,0,0,0.6)', backdropFilter: 'blur(8px)' }}
              >
                <span className="text-[9px] font-mono text-amber-400">{video.aiScore}</span>
              </div>

              {/* Bookmark badge */}
              <div className="absolute top-2 left-2">
                <Bookmark size={12} fill="#F5A524" color="#F5A524" />
              </div>

              {/* Bottom info */}
              <div className="absolute bottom-0 left-0 right-0 p-2.5">
                <div className="text-[9px] text-amber-400/70 uppercase tracking-wider mb-0.5 truncate">{video.channel}</div>
                <div className="text-white text-[10px] font-light leading-tight line-clamp-2 mb-1">
                  {video.title}
                </div>
                <div className="text-white/30 text-[9px] font-mono">{formatDuration(video.durationSec)}</div>
              </div>
            </div>
          ))}
        </div>

        {savedVideos.length === 0 && (
          <div className="flex flex-col items-center justify-center h-48 gap-3">
            <Bookmark size={32} style={{ color: 'rgba(255,255,255,0.1)' }} />
            <p className="text-sm" style={{ color: 'rgba(255,255,255,0.3)' }}>Noch nichts gespeichert</p>
          </div>
        )}
      </div>
    </div>
  )
}
