'use client'

import { useParams } from 'next/navigation'
import { mockVideos, mockSeries, MockVideo } from '@/lib/mock-data'
import { useMemo, useState } from 'react'
import { Play, ChevronDown, Check, Plus, Share2 } from 'lucide-react'
import Link from 'next/link'

function EpisodeCard({ video }: { video: MockVideo }) {
  return (
    <div className="flex gap-3 mb-4 group cursor-pointer">
      <div className={`relative flex-none w-32 aspect-video rounded overflow-hidden bg-gradient-to-br ${video.thumbnailGradient}`}>
        {video.progress && (
          <div className="absolute bottom-0 left-0 right-0 h-1 bg-white/30">
            <div className="h-full bg-accent" style={{ width: `${video.progress * 100}%` }} />
          </div>
        )}
      </div>
      <div className="flex-1 min-w-0 flex flex-col justify-center">
        <div className="text-[13px] font-medium text-white/95 line-clamp-2 leading-snug">
          {video.episode}. {video.title}
        </div>
        <div className="text-[11px] text-faint mt-1">
          {Math.floor(video.durationSec / 60)} min
        </div>
      </div>
    </div>
  )
}

export default function SeriesDetailPage() {
  const { id } = useParams()
  const series = useMemo(() => mockSeries.find(s => s.id === id), [id])
  const episodes = useMemo(() => mockVideos.filter(v => v.seriesId === id), [id])
  const [season, setSeason] = useState(1)

  if (!series) return <div className="p-10 text-center">Serie nicht gefunden</div>

  return (
    <div className="min-h-svh bg-[#0f0f0f] text-white pb-24">
      {/* Banner */}
      <div className="relative aspect-[16/10] w-full overflow-hidden">
        <div className={`absolute inset-0 bg-gradient-to-br ${series.thumbnailGradient}`} />
        <div className="absolute inset-0 bg-gradient-to-t from-[#0f0f0f] via-black/20 to-transparent" />
        
        <Link href="/library" className="absolute top-12 left-5 z-20 w-8 h-8 flex items-center justify-center bg-black/40 backdrop-blur rounded-full">
          <ChevronDown size={20} className="rotate-90" />
        </Link>
      </div>

      <div className="px-5 -mt-12 relative z-10">
        <h1 className="text-2xl font-black mb-1">{series.title}</h1>
        <div className="flex items-center gap-3 text-[12px] text-faint font-medium mb-4">
          <span className="text-green-500">98% Match</span>
          <span>2024</span>
          <span className="border border-white/30 px-1 rounded-sm text-[10px]">12+</span>
          <span>{episodes.length} Folgen</span>
        </div>

        <button className="w-full bg-white text-black font-bold py-2.5 rounded flex items-center justify-center gap-2 mb-3">
          <Play size={18} fill="black" /> Abspielen
        </button>

        <p className="text-[13px] text-white/80 leading-relaxed mb-6">
          {series.description}
        </p>

        <div className="flex gap-6 mb-8">
          <button className="flex flex-col items-center gap-1">
            <Plus size={22} className="text-white" />
            <span className="text-[10px] text-faint">Meine Liste</span>
          </button>
          <button className="flex flex-col items-center gap-1">
            <Share2 size={20} className="text-white" />
            <span className="text-[10px] text-faint">Empfehlen</span>
          </button>
        </div>

        {/* Episode List */}
        <div className="border-t border-white/10 pt-6">
          <div className="flex items-center gap-2 mb-6">
            <button className="flex items-center gap-1 bg-white/10 px-3 py-1.5 rounded text-[13px] font-bold">
              Staffel {season} <ChevronDown size={14} />
            </button>
          </div>

          <div>
            {episodes.map(v => <EpisodeCard key={v.videoId} video={v} />)}
          </div>
        </div>
      </div>
    </div>
  )
}
