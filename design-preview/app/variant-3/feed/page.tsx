'use client'

import { useState, useRef, useEffect, useCallback } from 'react'
import { mockVideos } from '@/lib/mock-data'
import { useHikariStore } from '@/lib/store'
import {
  Bookmark,
  Heart,
  MoreVertical,
  Maximize2,
  Menu,
  Play,
  Pause,
} from 'lucide-react'

function formatDuration(sec: number) {
  const m = Math.floor(sec / 60)
  const s = sec % 60
  return `${m}:${s.toString().padStart(2, '0')}`
}

// Deterministic gradient from videoId
const GRADIENTS = [
  'from-blue-900 via-indigo-800 to-blue-600',
  'from-violet-900 via-purple-700 to-indigo-600',
  'from-amber-700 via-orange-600 to-yellow-500',
  'from-slate-800 via-slate-700 to-zinc-600',
  'from-cyan-600 via-teal-500 to-emerald-500',
  'from-rose-900 via-red-800 to-orange-700',
  'from-green-700 via-emerald-600 to-teal-500',
  'from-sky-900 via-blue-800 to-indigo-900',
  'from-fuchsia-800 via-pink-700 to-rose-600',
  'from-zinc-800 via-neutral-700 to-stone-600',
  'from-lime-700 via-green-700 to-teal-800',
  'from-orange-800 via-red-700 to-rose-700',
  'from-blue-800 via-cyan-700 to-teal-600',
  'from-yellow-600 via-amber-500 to-orange-400',
  'from-emerald-800 via-green-700 to-lime-600',
]

interface HeartAnim {
  id: number
  x: number
  y: number
}

interface VideoSlideProps {
  video: (typeof mockVideos)[0]
  index: number
  isActive: boolean
  tab: 'new' | 'old'
}

function VideoSlide({ video, index, isActive, tab }: VideoSlideProps) {
  const { savedVideoIds, toggleSaved } = useHikariStore()
  const isSaved = savedVideoIds.includes(video.videoId)

  const [chromeVisible, setChromeVisible] = useState(true)
  const [playing, setPlaying] = useState(false)
  const [progress, setProgress] = useState(0.0)
  const [hearts, setHearts] = useState<HeartAnim[]>([])
  const [scrubActive, setScrubActive] = useState(false)

  const hideTimer = useRef<ReturnType<typeof setTimeout> | null>(null)
  const lastTap = useRef(0)
  const heartId = useRef(0)

  const gradient = GRADIENTS[index % GRADIENTS.length]

  const showChrome = useCallback(() => {
    setChromeVisible(true)
    if (hideTimer.current) clearTimeout(hideTimer.current)
    hideTimer.current = setTimeout(() => setChromeVisible(false), 3000)
  }, [])

  useEffect(() => {
    if (isActive) {
      showChrome()
      setProgress(0)
      setPlaying(false)
    }
    return () => {
      if (hideTimer.current) clearTimeout(hideTimer.current)
    }
  }, [isActive, showChrome])

  // Simulate progress when playing
  useEffect(() => {
    if (!playing || !isActive) return
    const interval = setInterval(() => {
      setProgress((p) => {
        if (p >= 1) { clearInterval(interval); setPlaying(false); return 1 }
        return p + 0.002
      })
    }, 100)
    return () => clearInterval(interval)
  }, [playing, isActive])

  const handleTap = (e: React.MouseEvent) => {
    const now = Date.now()
    if (now - lastTap.current < 300) {
      // double tap → heart
      const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
      const id = ++heartId.current
      setHearts((h) => [...h, { id, x: e.clientX - rect.left, y: e.clientY - rect.top }])
      setTimeout(() => setHearts((h) => h.filter((x) => x.id !== id)), 900)
      lastTap.current = 0
      return
    }
    lastTap.current = now
    setPlaying((p) => !p)
    showChrome()
  }

  return (
    <div
      className="relative flex-shrink-0 w-full snap-start overflow-hidden select-none"
      style={{ height: '100dvh' }}
      onClick={handleTap}
    >
      {/* Gradient bg */}
      <div className={`absolute inset-0 bg-gradient-to-br ${gradient}`} />
      <div className="absolute inset-0 bg-gradient-to-t from-black/70 via-black/10 to-black/30" />

      {/* Play/pause center indicator */}
      {!playing && (
        <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
          <div
            className="w-14 h-14 rounded-md flex items-center justify-center transition-opacity"
            style={{ background: 'rgba(139,92,246,0.25)', border: '1px solid rgba(139,92,246,0.5)', backdropFilter: 'blur(8px)' }}
          >
            <Play size={20} fill="white" color="white" className="ml-0.5" />
          </div>
        </div>
      )}

      {/* Heart animations */}
      {hearts.map((h) => (
        <div
          key={h.id}
          className="absolute pointer-events-none z-50 text-3xl animate-ping"
          style={{ left: h.x - 20, top: h.y - 20, color: '#8B5CF6', opacity: 0.9, animationDuration: '0.8s' }}
        >
          <Heart size={40} fill="#8B5CF6" color="#8B5CF6" />
        </div>
      ))}

      {/* TOP CHROME */}
      <div
        className="absolute top-0 left-0 right-0 z-20 flex items-center justify-between px-3 pt-14 pb-3 transition-opacity duration-300"
        style={{ opacity: chromeVisible ? 1 : 0 }}
        onClick={(e) => e.stopPropagation()}
      >
        <button
          className="w-8 h-8 flex items-center justify-center rounded-md"
          style={{ background: 'rgba(0,0,0,0.4)', border: '1px solid rgba(255,255,255,0.12)', backdropFilter: 'blur(8px)' }}
        >
          <Menu size={14} color="white" strokeWidth={1.5} />
        </button>

        <div
          className="px-2.5 py-1 rounded-md font-mono text-[11px] tracking-widest"
          style={{ background: 'rgba(0,0,0,0.4)', border: '1px solid rgba(139,92,246,0.3)', backdropFilter: 'blur(8px)', color: '#8B5CF6' }}
        >
          {tab === 'new' ? '08/15' : 'ARCHIV'}
        </div>

        <button
          className="w-8 h-8 flex items-center justify-center rounded-md"
          style={{ background: 'rgba(0,0,0,0.4)', border: '1px solid rgba(255,255,255,0.12)', backdropFilter: 'blur(8px)' }}
        >
          <Maximize2 size={13} color="white" strokeWidth={1.5} />
        </button>
      </div>

      {/* BOTTOM LEFT INFO */}
      <div
        className="absolute bottom-12 left-0 right-16 z-20 px-3 pb-3 transition-opacity duration-300"
        style={{ opacity: chromeVisible ? 1 : 0 }}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="font-mono text-[10px] tracking-widest mb-1" style={{ color: '#8B5CF6' }}>
          {video.channel.toUpperCase()}
        </div>
        <div className="text-sm font-medium text-white leading-snug line-clamp-2 pr-2">
          {video.title}
        </div>
      </div>

      {/* RIGHT RAIL */}
      <div
        className="absolute right-3 bottom-14 z-20 flex flex-col gap-2 items-center transition-opacity duration-300"
        style={{ opacity: chromeVisible ? 1 : 0 }}
        onClick={(e) => e.stopPropagation()}
      >
        <button
          onClick={() => toggleSaved(video.videoId)}
          className="w-10 h-10 flex items-center justify-center rounded-md transition-all"
          style={{
            background: isSaved ? 'rgba(139,92,246,0.25)' : 'rgba(0,0,0,0.4)',
            border: `1px solid ${isSaved ? 'rgba(139,92,246,0.5)' : 'rgba(255,255,255,0.12)'}`,
            backdropFilter: 'blur(8px)',
          }}
        >
          <Bookmark size={16} fill={isSaved ? '#8B5CF6' : 'none'} color={isSaved ? '#8B5CF6' : 'white'} strokeWidth={1.5} />
        </button>

        <button
          className="w-10 h-10 flex items-center justify-center rounded-md"
          style={{ background: 'rgba(0,0,0,0.4)', border: '1px solid rgba(255,255,255,0.12)', backdropFilter: 'blur(8px)' }}
        >
          <MoreVertical size={15} color="white" strokeWidth={1.5} />
        </button>

        <div
          className="w-10 flex items-center justify-center rounded-md px-1 py-1.5"
          style={{ background: 'rgba(0,0,0,0.4)', border: '1px solid rgba(255,255,255,0.08)', backdropFilter: 'blur(8px)' }}
        >
          <span className="font-mono text-[9px] text-center leading-none" style={{ color: '#8B5CF6' }}>
            {video.aiScore}
          </span>
        </div>
      </div>

      {/* SCRUBBER — always visible, grows on active */}
      <div
        className="absolute bottom-0 left-0 right-0 z-20 transition-all duration-200"
        style={{ height: scrubActive ? 20 : 6, paddingBottom: scrubActive ? 4 : 0 }}
        onMouseEnter={() => setScrubActive(true)}
        onMouseLeave={() => setScrubActive(false)}
        onClick={(e) => {
          e.stopPropagation()
          const rect = e.currentTarget.getBoundingClientRect()
          setProgress((e.clientX - rect.left) / rect.width)
        }}
      >
        <div
          className="absolute bottom-0 left-0 right-0"
          style={{ height: scrubActive ? 4 : 2, background: 'rgba(255,255,255,0.15)' }}
        >
          <div
            className="absolute top-0 left-0 h-full transition-all"
            style={{ width: `${progress * 100}%`, background: '#8B5CF6' }}
          />
          {scrubActive && (
            <div
              className="absolute top-1/2 -translate-y-1/2 w-3.5 h-3.5 rounded-sm"
              style={{ left: `calc(${progress * 100}% - 7px)`, background: '#8B5CF6', border: '2px solid white' }}
            />
          )}
        </div>
        {scrubActive && (
          <div className="absolute bottom-5 right-2 font-mono text-[9px]" style={{ color: 'rgba(255,255,255,0.6)' }}>
            {formatDuration(Math.floor(video.durationSec * progress))} / {formatDuration(video.durationSec)}
          </div>
        )}
      </div>
    </div>
  )
}

export default function DashboardFeed() {
  const [tab, setTab] = useState<'new' | 'old'>('new')
  const [activeIndex, setActiveIndex] = useState(0)
  const containerRef = useRef<HTMLDivElement>(null)

  // Detect which slide is active via scroll
  const handleScroll = useCallback(() => {
    if (!containerRef.current) return
    const scrollTop = containerRef.current.scrollTop
    const height = containerRef.current.clientHeight
    const idx = Math.round(scrollTop / height)
    setActiveIndex(idx)
  }, [])

  useEffect(() => {
    const el = containerRef.current
    if (!el) return
    el.addEventListener('scroll', handleScroll, { passive: true })
    return () => el.removeEventListener('scroll', handleScroll)
  }, [handleScroll])

  return (
    <div className="relative" style={{ height: '100dvh' }}>
      {/* Tab row — floats over feed */}
      <div
        className="absolute top-0 left-0 right-0 z-30 flex gap-1 px-3 pt-2"
        style={{ pointerEvents: 'none' }}
      >
        <div className="flex gap-1" style={{ pointerEvents: 'auto' }}>
          {(['new', 'old'] as const).map((t) => (
            <button
              key={t}
              onClick={() => setTab(t)}
              className="px-2.5 py-1 rounded-md text-[9px] font-mono uppercase tracking-widest transition-all"
              style={{
                background: tab === t ? 'rgba(139,92,246,0.25)' : 'rgba(0,0,0,0.5)',
                border: `1px solid ${tab === t ? 'rgba(139,92,246,0.5)' : 'rgba(255,255,255,0.1)'}`,
                color: tab === t ? '#8B5CF6' : 'rgba(255,255,255,0.5)',
                backdropFilter: 'blur(8px)',
              }}
            >
              {t === 'new' ? 'NEU' : 'ARCHIV'}
            </button>
          ))}
        </div>
      </div>

      {/* Scrollable video container */}
      <div
        ref={containerRef}
        className="w-full overflow-y-scroll"
        style={{
          height: '100dvh',
          scrollSnapType: 'y mandatory',
          WebkitOverflowScrolling: 'touch',
        }}
      >
        {mockVideos.map((video, i) => (
          <VideoSlide
            key={video.videoId}
            video={video}
            index={i}
            isActive={i === activeIndex}
            tab={tab}
          />
        ))}
      </div>
    </div>
  )
}
