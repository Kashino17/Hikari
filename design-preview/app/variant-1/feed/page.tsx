'use client'

import { useState } from 'react'
import { mockVideos } from '@/lib/mock-data'
import { Heart, MoreVertical, Bookmark, ChevronUp, ChevronDown, Sun, Moon, ArrowLeft } from 'lucide-react'
import { useRouter } from 'next/navigation'

const video = mockVideos[0]
const nextVideo = mockVideos[1]
const prevVideo = mockVideos[5]

function formatDuration(sec: number) {
  const m = Math.floor(sec / 60)
  const s = sec % 60
  return `${m}:${s.toString().padStart(2, '0')}`
}

export default function CinemaFeed() {
  const [tab, setTab] = useState<'new' | 'old'>('new')
  const [scrubberExpanded, setScrubberExpanded] = useState(false)
  const [progress, setProgress] = useState(0.33)
  const [liked, setLiked] = useState(false)
  const [saved, setSaved] = useState(false)
  const [dark, setDark] = useState(true)
  const router = useRouter()

  const toggleTheme = () => {
    const next = !dark
    setDark(next)
    if (next) {
      document.documentElement.classList.add('dark')
      localStorage.setItem('hikari-theme', 'dark')
    } else {
      document.documentElement.classList.remove('dark')
      localStorage.setItem('hikari-theme', 'light')
    }
  }

  return (
    <div
      className="relative h-screen overflow-hidden select-none"
      style={{ background: dark ? '#000' : '#FAF5EF' }}
    >
      {/* Full-bleed video background */}
      <div className={`absolute inset-0 bg-gradient-to-br ${video.thumbnailGradient}`} />

      {/* Cinematic dark overlays */}
      <div className="absolute inset-0 bg-gradient-to-t from-black via-black/20 to-black/40" />
      <div className="absolute inset-0 bg-gradient-to-b from-black/60 via-transparent to-transparent" />

      {/* PREV video peek — top */}
      <div className="absolute -top-2 left-0 right-0 h-24 pointer-events-none">
        <div className={`absolute inset-0 bg-gradient-to-br ${prevVideo.thumbnailGradient} opacity-30`} />
        <div className="absolute inset-0 bg-gradient-to-b from-transparent to-black" />
        <div className="absolute bottom-3 left-5 text-white/30 text-xs font-light truncate max-w-[60%]">
          {prevVideo.title}
        </div>
      </div>

      {/* NEXT video peek — bottom shadow context */}
      <div className="absolute -bottom-0 left-0 right-0 h-20 pointer-events-none" style={{ zIndex: 1 }}>
        <div className={`absolute inset-0 bg-gradient-to-br ${nextVideo.thumbnailGradient} opacity-20`} />
        <div className="absolute inset-0 bg-gradient-to-t from-transparent to-black" />
      </div>

      {/* Top chrome */}
      <div className="absolute top-0 left-0 right-0 z-20 flex items-center justify-between px-5 pt-12 pb-4">
        <button onClick={() => router.push('/')} className="p-1.5 opacity-60 hover:opacity-100 transition-opacity">
          <ArrowLeft size={18} color="white" />
        </button>

        {/* New / Old tabs */}
        <div className="flex items-center gap-1 bg-white/10 backdrop-blur-md rounded-full p-1">
          <button
            onClick={() => setTab('new')}
            className="px-4 py-1 rounded-full text-xs font-medium transition-all"
            style={{
              background: tab === 'new' ? '#F5A524' : 'transparent',
              color: tab === 'new' ? '#000' : 'rgba(255,255,255,0.7)',
            }}
          >
            Neu
          </button>
          <button
            onClick={() => setTab('old')}
            className="px-4 py-1 rounded-full text-xs font-medium transition-all"
            style={{
              background: tab === 'old' ? '#F5A524' : 'transparent',
              color: tab === 'old' ? '#000' : 'rgba(255,255,255,0.7)',
            }}
          >
            Alt
          </button>
        </div>

        <button onClick={toggleTheme} className="p-1.5 opacity-60 hover:opacity-100 transition-opacity">
          {dark ? <Sun size={16} color="white" /> : <Moon size={16} color="white" />}
        </button>
      </div>

      {/* Double-tap seek badges */}
      <div className="absolute left-4 top-1/2 -translate-y-1/2 z-10 flex flex-col items-center gap-1 opacity-30">
        <div className="w-8 h-8 rounded-full border border-white/30 flex items-center justify-center">
          <span className="text-white text-[9px] font-mono">−5s</span>
        </div>
      </div>
      <div className="absolute right-4 top-1/2 -translate-y-1/2 z-10 flex flex-col items-center gap-1 opacity-30">
        <div className="w-8 h-8 rounded-full border border-white/30 flex items-center justify-center">
          <span className="text-white text-[9px] font-mono">+5s</span>
        </div>
      </div>

      {/* Right-side action bar */}
      <div className="absolute right-4 bottom-32 z-20 flex flex-col items-center gap-5">
        <button
          onClick={() => setLiked(!liked)}
          className="flex flex-col items-center gap-1 transition-all"
        >
          <div
            className="w-11 h-11 rounded-full flex items-center justify-center transition-all"
            style={{
              background: liked ? '#F5A524' : 'rgba(255,255,255,0.12)',
              backdropFilter: 'blur(12px)',
            }}
          >
            <Heart size={20} fill={liked ? '#000' : 'none'} color={liked ? '#000' : '#fff'} />
          </div>
          <span className="text-white/50 text-[10px]">94</span>
        </button>

        <button
          onClick={() => setSaved(!saved)}
          className="flex flex-col items-center gap-1 transition-all"
        >
          <div
            className="w-11 h-11 rounded-full flex items-center justify-center transition-all"
            style={{
              background: saved ? '#F5A524' : 'rgba(255,255,255,0.12)',
              backdropFilter: 'blur(12px)',
            }}
          >
            <Bookmark size={20} fill={saved ? '#000' : 'none'} color={saved ? '#000' : '#fff'} />
          </div>
          <span className="text-white/50 text-[10px]">Speichern</span>
        </button>

        <button className="flex flex-col items-center gap-1">
          <div
            className="w-11 h-11 rounded-full flex items-center justify-center"
            style={{ background: 'rgba(255,255,255,0.12)', backdropFilter: 'blur(12px)' }}
          >
            <MoreVertical size={18} color="#fff" />
          </div>
        </button>
      </div>

      {/* Bottom info overlay */}
      <div className="absolute bottom-0 left-0 right-0 z-20 px-5 pb-24">
        {/* Budget indicator */}
        <div className="flex items-center gap-2 mb-3">
          {Array.from({ length: 15 }).map((_, i) => (
            <div
              key={i}
              className="flex-1 h-0.5 rounded-full"
              style={{ background: i < 8 ? '#F5A524' : 'rgba(255,255,255,0.15)' }}
            />
          ))}
          <span className="text-[10px] text-white/40 ml-1 flex-shrink-0">8/15</span>
        </div>

        {/* Channel + score */}
        <div className="flex items-center gap-2 mb-1.5">
          <span className="text-[11px] text-amber-400/80 uppercase tracking-widest font-medium">{video.channel}</span>
          <span className="text-white/20 text-[10px]">·</span>
          <span className="text-[10px] text-white/40 uppercase tracking-widest">{video.category}</span>
          <div className="ml-auto flex items-center gap-1 bg-amber-400/10 border border-amber-400/20 rounded-full px-2 py-0.5">
            <span className="text-amber-400 text-[10px] font-mono">{video.aiScore}</span>
          </div>
        </div>

        {/* Title */}
        <h1
          className="text-white text-xl font-light leading-snug mb-2"
          style={{ fontFamily: 'var(--font-geist-sans)', textShadow: '0 2px 20px rgba(0,0,0,0.8)' }}
        >
          {video.title}
        </h1>

        {/* AI reasoning */}
        <p className="text-white/40 text-xs leading-relaxed mb-4 max-w-xs">
          {video.aiReasoning}
        </p>

        {/* Duration */}
        <div className="flex items-center justify-between text-white/30 text-xs mb-3">
          <span className="font-mono">{formatDuration(Math.floor(video.durationSec * progress))}</span>
          <span className="font-mono">{formatDuration(video.durationSec)}</span>
        </div>

        {/* Scrubber */}
        <div
          className="relative cursor-pointer transition-all duration-200"
          style={{ height: scrubberExpanded ? 20 : 2 }}
          onMouseEnter={() => setScrubberExpanded(true)}
          onMouseLeave={() => setScrubberExpanded(false)}
          onTouchStart={() => setScrubberExpanded(true)}
          onTouchEnd={() => setScrubberExpanded(false)}
          onClick={(e) => {
            const rect = e.currentTarget.getBoundingClientRect()
            setProgress((e.clientX - rect.left) / rect.width)
          }}
        >
          <div className="absolute inset-0 rounded-full bg-white/15" />
          <div
            className="absolute top-0 bottom-0 left-0 rounded-full bg-amber-400 transition-all"
            style={{ width: `${progress * 100}%` }}
          />
          {scrubberExpanded && (
            <div
              className="absolute top-1/2 -translate-y-1/2 w-5 h-5 rounded-full bg-amber-400 border-2 border-white shadow-lg transition-all"
              style={{ left: `calc(${progress * 100}% - 10px)` }}
            />
          )}
        </div>
      </div>

      {/* Swipe hint arrows */}
      <div className="absolute left-1/2 -translate-x-1/2 top-20 z-10 opacity-20">
        <ChevronUp size={18} color="white" />
      </div>
      <div className="absolute left-1/2 -translate-x-1/2 z-10 opacity-20" style={{ bottom: 96 }}>
        <ChevronDown size={18} color="white" />
      </div>
    </div>
  )
}
