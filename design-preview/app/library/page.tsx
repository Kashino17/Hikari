'use client'

import { useState, useMemo } from 'react'
import { mockVideos, allChannels, mockSeries, MockVideo, MockSeries, MockChannel } from '@/lib/mock-data'
import { useHikariStore } from '@/lib/store'
import { Play, Info, ChevronRight, Plus, Sparkles } from 'lucide-react'
import Link from 'next/link'

function VideoCard({ video }: { video: MockVideo }) {
  return (
    <div className="flex-none w-40 group cursor-pointer">
      <div className={`relative aspect-video rounded-md overflow-hidden bg-gradient-to-br ${video.thumbnailGradient}`}>
        <div className="absolute inset-0 bg-black/20 group-hover:bg-black/0 transition-colors" />
        {video.progress && (
          <div className="absolute bottom-0 left-0 right-0 h-1 bg-white/30">
            <div className="h-full bg-accent" style={{ width: `${video.progress * 100}%` }} />
          </div>
        )}
      </div>
      <div className="mt-2">
        <div className="text-[11px] font-medium text-white/90 line-clamp-1">{video.title}</div>
        <div className="text-[9px] text-faint uppercase tracking-wider">{video.channel}</div>
      </div>
    </div>
  )
}

function SeriesCard({ series }: { series: MockSeries }) {
  return (
    <Link href={`/series/${series.id}`} className="flex-none w-40 group cursor-pointer">
      <div className={`relative aspect-[2/3] rounded-md overflow-hidden bg-gradient-to-br ${series.thumbnailGradient}`}>
        <div className="absolute inset-0 bg-gradient-to-t from-black/80 via-transparent to-transparent" />
        <div className="absolute bottom-3 left-3 right-3">
          <div className="text-[12px] font-bold text-white leading-tight">{series.title}</div>
        </div>
      </div>
    </Link>
  )
}

function ChannelCard({ channel }: { channel: MockChannel }) {
  return (
    <div className="flex-none w-28 text-center group cursor-pointer">
      <div className="relative aspect-square rounded-full overflow-hidden bg-surface border-hairline mb-2 group-hover:scale-105 transition-transform">
        <div className={`absolute inset-0 bg-gradient-to-br ${channel.thumbnailGradient || 'from-zinc-800 to-zinc-900'}`} />
      </div>
      <div className="text-[11px] font-medium text-white/90 truncate px-1">{channel.name}</div>
    </div>
  )
}

function Row({
  title,
  icon,
  children,
}: {
  title: string
  icon?: React.ReactNode
  children: React.ReactNode
}) {
  return (
    <div className="mb-8">
      <div className="flex items-center justify-between px-5 mb-3">
        <h2 className="text-[15px] font-bold text-white/95 flex items-center gap-1.5">
          {icon}
          {title} <ChevronRight size={16} className="text-faint" />
        </h2>
      </div>
      <div className="flex gap-3 overflow-x-auto px-5 no-scrollbar">
        {children}
      </div>
    </div>
  )
}

export default function LibraryPage() {
  const [showImport, setShowImport] = useState(false)
  const prefetchedByChannel = useHikariStore((s) => s.prefetchedByChannel)
  const continueWatching = useMemo(() => mockVideos.filter(v => v.progress && v.progress < 0.95), [])
  const newVideos = useMemo(() => mockVideos.slice(0, 8), [])
  const featured = mockVideos[0]

  // Group-by-channel ordering: videos from the same follow-action stay together,
  // so the row reads as discrete "discovery batches" instead of a shuffled list.
  const freshlyDiscovered = useMemo(() => {
    return Object.entries(prefetchedByChannel).flatMap(([, videoIds]) =>
      videoIds
        .map((id) => mockVideos.find((v) => v.videoId === id))
        .filter((v): v is MockVideo => v !== undefined),
    )
  }, [prefetchedByChannel])

  return (
    <div className="min-h-svh bg-[#0f0f0f] text-white pb-24">
      {/* Header */}
      <header className="fixed top-0 inset-x-0 z-30 px-5 pt-12 pb-4 flex justify-between items-center bg-gradient-to-b from-black/60 to-transparent pointer-events-none">
        <div className="text-xl font-black tracking-tighter text-white pointer-events-auto">HIKARI</div>
        <button 
          onClick={() => setShowImport(true)}
          className="w-10 h-10 flex items-center justify-center bg-white/20 backdrop-blur-md rounded-full pointer-events-auto border border-white/10"
        >
          <Plus size={20} />
        </button>
      </header>
      <div className="relative h-[55dvh] w-full overflow-hidden">
        <div className={`absolute inset-0 bg-gradient-to-br ${featured.thumbnailGradient}`} />
        <div className="absolute inset-0 bg-gradient-to-t from-[#0f0f0f] via-black/20 to-transparent" />
        
        <div className="absolute bottom-8 inset-x-0 px-5">
          <div className="flex items-center gap-2 mb-2">
            <span className="bg-accent/20 text-accent text-[10px] font-bold px-1.5 py-0.5 rounded border border-accent/30 uppercase tracking-wider">
              Neu
            </span>
            <span className="text-[11px] font-medium text-white/80 uppercase tracking-[0.15em]">
              {featured.channel}
            </span>
          </div>
          <h1 className="text-2xl font-black mb-4 leading-tight max-w-[80%]">
            {featured.title}
          </h1>
          <div className="flex gap-3">
            <button className="flex-1 bg-white text-black font-bold py-2 rounded flex items-center justify-center gap-2">
              <Play size={18} fill="black" /> Abspielen
            </button>
            <button className="flex-1 bg-white/20 backdrop-blur-md text-white font-bold py-2 rounded flex items-center justify-center gap-2">
              <Info size={18} /> Infos
            </button>
          </div>
        </div>
      </div>

      {/* Rows */}
      <div className="mt-4">
        {continueWatching.length > 0 && (
          <Row title="Weitersehen">
            {continueWatching.map(v => <VideoCard key={v.videoId} video={v} />)}
          </Row>
        )}

        {freshlyDiscovered.length > 0 && (
          <Row
            title="Frisch entdeckt"
            icon={<Sparkles size={14} className="text-accent" strokeWidth={2} />}
          >
            {freshlyDiscovered.map(v => <VideoCard key={v.videoId} video={v} />)}
          </Row>
        )}

        <Row title="Serien für dich">
          {mockSeries.map(s => <SeriesCard key={s.id} series={s} />)}
        </Row>

        <Row title="Deine Kanäle">
          {allChannels.map(c => <ChannelCard key={c.id} channel={c} />)}
        </Row>

        <Row title="Neu hinzugefügt">
          {newVideos.map(v => <VideoCard key={v.videoId} video={v} />)}
        </Row>
      </div>

      {/* Bottom Nav Spacer */}
      <div className="h-10" />

      {showImport && (
        <ImportModal onClose={() => setShowImport(false)} />
      )}
    </div>
  )
}

function ImportModal({ onClose }: { onClose: () => void }) {
  const [url, setUrl] = useState('')
  const [step, setStep] = useState<'url' | 'analyze' | 'edit'>('url')
  const [meta, setMeta] = useState({
    title: '',
    seriesTitle: '',
    season: 1,
    episode: 1,
    dub: 'Deutsch',
    isMovie: false
  })

  const analyze = () => {
    setStep('analyze')
    // Simulate AI extraction
    setTimeout(() => {
      setMeta({
        title: 'But what is a Neural Network? | Chapter 1',
        seriesTitle: 'Deep Learning',
        season: 1,
        episode: 1,
        dub: 'English',
        isMovie: false
      })
      setStep('edit')
    }, 1500)
  }

  return (
    <div className="fixed inset-0 z-[100] bg-black/80 backdrop-blur-sm flex items-end sm:items-center justify-center">
      <div className="w-full max-w-md bg-[#1a1a1a] rounded-t-2xl sm:rounded-2xl border border-white/10 overflow-hidden animate-in slide-in-from-bottom duration-300">
        <div className="px-5 py-4 border-b border-white/10 flex justify-between items-center">
          <h2 className="text-[15px] font-bold">Video hinzufügen</h2>
          <button onClick={onClose} className="p-1"><X size={20} className="text-faint" /></button>
        </div>
        
        <div className="p-5">
          {step === 'url' && (
            <div className="space-y-4">
              <p className="text-[12px] text-faint">Füge einen YouTube-Link oder eine andere URL ein.</p>
              <input 
                value={url}
                onChange={e => setUrl(e.target.value)}
                placeholder="https://..."
                className="w-full bg-white/5 border border-white/10 rounded-lg px-4 py-3 text-sm focus:border-accent outline-none"
              />
              <button 
                onClick={analyze}
                disabled={!url}
                className="w-full bg-accent text-black font-bold py-3 rounded-lg disabled:opacity-50"
              >
                Analysieren
              </button>
            </div>
          )}

          {step === 'analyze' && (
            <div className="py-12 flex flex-col items-center justify-center gap-4">
              <div className="w-8 h-8 border-2 border-accent border-t-transparent rounded-full animate-spin" />
              <p className="text-sm font-medium animate-pulse">KI extrahiert Metadaten...</p>
            </div>
          )}

          {step === 'edit' && (
            <div className="space-y-4">
              <div className="aspect-video rounded-lg bg-white/5 border border-white/10 mb-4 flex items-center justify-center relative overflow-hidden">
                <div className="absolute inset-0 bg-gradient-to-br from-blue-900 to-indigo-900" />
                <Play size={24} className="relative z-10 text-white/50" />
              </div>

              <div>
                <label className="text-[10px] uppercase tracking-wider text-faint mb-1 block">Titel</label>
                <input 
                  value={meta.title}
                  onChange={e => setMeta({...meta, title: e.target.value})}
                  className="w-full bg-white/5 border border-white/10 rounded-lg px-3 py-2 text-[13px] outline-none"
                />
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-[10px] uppercase tracking-wider text-faint mb-1 block">Serie</label>
                  <input 
                    value={meta.seriesTitle}
                    onChange={e => setMeta({...meta, seriesTitle: e.target.value})}
                    className="w-full bg-white/5 border border-white/10 rounded-lg px-3 py-2 text-[13px] outline-none"
                  />
                </div>
                <div>
                  <label className="text-[10px] uppercase tracking-wider text-faint mb-1 block">Dab / Sprache</label>
                  <input 
                    value={meta.dub}
                    onChange={e => setMeta({...meta, dub: e.target.value})}
                    className="w-full bg-white/5 border border-white/10 rounded-lg px-3 py-2 text-[13px] outline-none"
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="text-[10px] uppercase tracking-wider text-faint mb-1 block">Staffel</label>
                  <input 
                    type="number"
                    value={meta.season}
                    onChange={e => setMeta({...meta, season: parseInt(e.target.value)})}
                    className="w-full bg-white/5 border border-white/10 rounded-lg px-3 py-2 text-[13px] outline-none"
                  />
                </div>
                <div>
                  <label className="text-[10px] uppercase tracking-wider text-faint mb-1 block">Folge</label>
                  <input 
                    type="number"
                    value={meta.episode}
                    onChange={e => setMeta({...meta, episode: parseInt(e.target.value)})}
                    className="w-full bg-white/5 border border-white/10 rounded-lg px-3 py-2 text-[13px] outline-none"
                  />
                </div>
              </div>

              <button 
                onClick={onClose}
                className="w-full bg-white text-black font-bold py-3 rounded-lg mt-4"
              >
                Import bestätigen
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}

function X({ size, className }: { size: number, className?: string }) {
  return (
    <svg 
      width={size} 
      height={size} 
      viewBox="0 0 24 24" 
      fill="none" 
      stroke="currentColor" 
      strokeWidth="2" 
      strokeLinecap="round" 
      strokeLinejoin="round" 
      className={className}
    >
      <path d="M18 6L6 18M6 6l12 12" />
    </svg>
  )
}
