'use client'

import { useState, useRef, useEffect, useCallback, useMemo } from 'react'
import { mockVideos, rejectedVideos } from '@/lib/mock-data'
import { useHikariStore } from '@/lib/store'
import { Bookmark, Heart, Play } from 'lucide-react'

const GRADIENTS = [
  'from-zinc-900 via-zinc-800 to-zinc-900',
  'from-stone-900 via-neutral-800 to-stone-900',
  'from-slate-900 via-slate-800 to-zinc-900',
  'from-zinc-950 via-stone-900 to-neutral-900',
  'from-neutral-900 via-zinc-800 to-slate-900',
]

function fmt(sec: number) {
  const m = Math.floor(sec / 60)
  const s = sec % 60
  return `${m}:${s.toString().padStart(2, '0')}`
}

interface HeartAnim { id: number; x: number; y: number }

interface SlideProps {
  videoId: string
  title: string
  channel: string
  durationSec: number
  aiScore: number
  index: number
  total: number
  isActive: boolean
}

function Slide({ videoId, title, channel, durationSec, aiScore, index, total, isActive }: SlideProps) {
  const savedVideoIds = useHikariStore((s) => s.savedVideoIds)
  const toggleSaved = useHikariStore((s) => s.toggleSaved)
  const isSaved = savedVideoIds.includes(videoId)

  const [chrome, setChrome] = useState(true)
  const [playing, setPlaying] = useState(false)
  const [progress, setProgress] = useState(0)
  const [hearts, setHearts] = useState<HeartAnim[]>([])

  const hideTimer = useRef<ReturnType<typeof setTimeout> | null>(null)
  const lastTap = useRef(0)
  const heartId = useRef(0)

  const showChrome = useCallback(() => {
    setChrome(true)
    if (hideTimer.current) clearTimeout(hideTimer.current)
    hideTimer.current = setTimeout(() => setChrome(false), 2500)
  }, [])

  useEffect(() => {
    if (isActive) {
      showChrome()
      setProgress(0)
      setPlaying(false)
    }
    return () => { if (hideTimer.current) clearTimeout(hideTimer.current) }
  }, [isActive, showChrome])

  useEffect(() => {
    if (!playing || !isActive) return
    const t = setInterval(() => {
      setProgress((p) => {
        if (p >= 1) { clearInterval(t); setPlaying(false); return 1 }
        return p + 0.003
      })
    }, 100)
    return () => clearInterval(t)
  }, [playing, isActive])

  function handleTap(e: React.MouseEvent) {
    const now = Date.now()
    if (now - lastTap.current < 300) {
      const rect = (e.currentTarget as HTMLElement).getBoundingClientRect()
      const id = ++heartId.current
      setHearts((h) => [...h, { id, x: e.clientX - rect.left, y: e.clientY - rect.top }])
      setTimeout(() => setHearts((h) => h.filter((x) => x.id !== id)), 800)
      lastTap.current = 0
      if (!isSaved) toggleSaved(videoId)
      return
    }
    lastTap.current = now
    setPlaying((p) => !p)
    showChrome()
  }

  const gradient = GRADIENTS[index % GRADIENTS.length]

  return (
    <div
      className="relative w-full snap-start overflow-hidden select-none"
      style={{ height: '100dvh' }}
      onClick={handleTap}
    >
      <div className={`absolute inset-0 bg-gradient-to-br ${gradient}`} />
      <div className="absolute inset-0 bg-gradient-to-b from-black/40 via-transparent to-black/70" />

      {/* Center play indicator */}
      {!playing && (
        <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
          <div className="w-12 h-12 rounded-full flex items-center justify-center bg-black/30 backdrop-blur-sm">
            <Play size={18} fill="white" color="white" className="ml-0.5" strokeWidth={1} />
          </div>
        </div>
      )}

      {/* Heart bursts */}
      {hearts.map((h) => (
        <div
          key={h.id}
          className="absolute pointer-events-none z-50"
          style={{ left: h.x - 24, top: h.y - 24, animation: 'heart-pop 0.8s ease-out forwards' }}
        >
          <Heart size={48} fill="#fbbf24" color="#fbbf24" strokeWidth={0} />
        </div>
      ))}
      <style jsx>{`
        @keyframes heart-pop {
          0% { transform: scale(0.4); opacity: 0; }
          30% { transform: scale(1.2); opacity: 1; }
          100% { transform: scale(0.9); opacity: 0; transform: translateY(-30px) scale(0.9); }
        }
      `}</style>

      {/* Top — counter + save */}
      <div
        className="absolute top-0 inset-x-0 z-20 flex items-center justify-between px-5 pt-12 transition-opacity duration-300"
        style={{ opacity: chrome ? 1 : 0, paddingTop: 'calc(env(safe-area-inset-top, 0) + 12px)' }}
        onClick={(e) => e.stopPropagation()}
      >
        <span className="text-faint text-[11px] font-mono tabular-nums">
          {String(index + 1).padStart(2, '0')} / {String(total).padStart(2, '0')}
        </span>
        <button
          onClick={() => toggleSaved(videoId)}
          className="w-9 h-9 flex items-center justify-center"
        >
          <Bookmark
            size={18}
            fill={isSaved ? '#fbbf24' : 'none'}
            color={isSaved ? '#fbbf24' : 'white'}
            strokeWidth={1.5}
          />
        </button>
      </div>

      {/* Bottom info */}
      <div
        className="absolute bottom-0 inset-x-0 z-20 px-5 pb-8 transition-opacity duration-300"
        style={{ opacity: chrome ? 1 : 0 }}
        onClick={(e) => e.stopPropagation()}
      >
        <div className="flex items-baseline gap-2 mb-2">
          <span className="text-accent text-[10px] font-mono uppercase tracking-[0.18em]">
            {channel}
          </span>
          <span className="text-faint text-[10px] font-mono">·</span>
          <span className="text-faint text-[10px] font-mono tabular-nums">{fmt(durationSec)}</span>
          <span className="text-faint text-[10px] font-mono">·</span>
          <span className="text-faint text-[10px] font-mono tabular-nums">{aiScore}</span>
        </div>
        <h2 className="text-[15px] font-medium leading-snug text-white/95 line-clamp-2 max-w-[85%]">
          {title}
        </h2>
      </div>

      {/* Hairline scrubber, always visible */}
      <div className="absolute bottom-0 inset-x-0 h-px z-20 bg-white/10">
        <div
          className="absolute inset-y-0 left-0 bg-accent transition-all"
          style={{ width: `${progress * 100}%` }}
        />
      </div>
    </div>
  )
}

type FeedFilter = 'all' | 'saved' | 'rejected'

export default function FeedPage() {
  const feedFilter = useHikariStore((s) => s.feedFilter)
  const setFeedFilter = useHikariStore((s) => s.setFeedFilter)
  const savedVideoIds = useHikariStore((s) => s.savedVideoIds)
  const [activeIdx, setActiveIdx] = useState(0)
  const containerRef = useRef<HTMLDivElement>(null)

  const list = useMemo(() => {
    if (feedFilter === 'saved') {
      return mockVideos.filter((v) => savedVideoIds.includes(v.videoId))
    }
    if (feedFilter === 'rejected') {
      return rejectedVideos.map((r) => ({
        videoId: r.videoId,
        title: r.title,
        channel: r.channel,
        durationSec: r.durationSec,
        aiScore: r.aiScore,
      }))
    }
    return mockVideos.map((v) => ({
      videoId: v.videoId,
      title: v.title,
      channel: v.channel,
      durationSec: v.durationSec,
      aiScore: v.aiScore,
    }))
  }, [feedFilter, savedVideoIds])

  const onScroll = useCallback(() => {
    if (!containerRef.current) return
    const idx = Math.round(containerRef.current.scrollTop / containerRef.current.clientHeight)
    setActiveIdx(idx)
  }, [])

  useEffect(() => {
    const el = containerRef.current
    if (!el) return
    el.addEventListener('scroll', onScroll, { passive: true })
    return () => el.removeEventListener('scroll', onScroll)
  }, [onScroll])

  // Reset scroll when filter changes
  useEffect(() => {
    if (containerRef.current) containerRef.current.scrollTop = 0
    setActiveIdx(0)
  }, [feedFilter])

  return (
    <div className="relative" style={{ height: '100dvh' }}>
      {/* Filter pill row — top center, floats */}
      <div
        className="absolute top-0 inset-x-0 z-30 flex justify-center pt-3"
        style={{ paddingTop: 'calc(env(safe-area-inset-top, 0) + 8px)' }}
      >
        <div className="flex gap-1 bg-black/40 backdrop-blur rounded-full p-1 border-hairline">
          {(['all', 'saved', 'rejected'] as const).map((f) => {
            const active = feedFilter === f
            const label = f === 'all' ? 'Alle' : f === 'saved' ? 'Gespeichert' : 'Abgelehnt'
            return (
              <button
                key={f}
                onClick={() => setFeedFilter(f)}
                className={`px-3 py-1 text-[11px] rounded-full transition-colors ${
                  active ? 'bg-accent text-black font-medium' : 'text-mute'
                }`}
              >
                {label}
              </button>
            )
          })}
        </div>
      </div>

      {list.length === 0 ? (
        <div className="h-full flex items-center justify-center text-faint text-sm">
          {feedFilter === 'saved' ? 'Noch nichts gespeichert' : 'Nichts hier'}
        </div>
      ) : (
        <div
          ref={containerRef}
          className="w-full overflow-y-scroll"
          style={{
            height: '100dvh',
            scrollSnapType: 'y mandatory',
            WebkitOverflowScrolling: 'touch',
          }}
        >
          {list.map((v, i) => (
            <Slide
              key={v.videoId}
              videoId={v.videoId}
              title={v.title}
              channel={v.channel}
              durationSec={v.durationSec}
              aiScore={v.aiScore}
              index={i}
              total={list.length}
              isActive={i === activeIdx}
            />
          ))}
        </div>
      )}
    </div>
  )
}
