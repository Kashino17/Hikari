'use client'

import { useState, useEffect } from 'react'
import { useRouter } from 'next/navigation'
import { Sun, Moon, ArrowRight } from 'lucide-react'

const variants = [
  {
    id: 'variant-1',
    name: 'Cinema',
    tagline: 'Content-first. The video is everything.',
    description: 'Pitch-black canvas, warm amber accents, cinematic overlays. Chrome disappears so the video fills your consciousness. Inspired by how Netflix presents content — with reverence.',
    accentColor: '#F5A524',
    feeling: 'Immersive · Dark · Cinematic',
  },
  {
    id: 'variant-2',
    name: 'Editorial',
    tagline: 'Context surfaces. Reasoning shows.',
    description: "Off-white pages, Fraunces serif headlines, generous breathing room. The AI's reasoning appears as a sidebar note. Feels like reading Aeon or N+1, not watching YouTube.",
    accentColor: '#14532D',
    feeling: 'Thoughtful · Light · Editorial',
  },
  {
    id: 'variant-3',
    name: 'Dashboard',
    tagline: 'Data-forward. Every number visible.',
    description: 'Bento grid of cards, electric violet accents, monospace numbers. Scoring breakdowns, channel stats, segment counts — all on screen. Feels like Linear or Raycast.',
    accentColor: '#8B5CF6',
    feeling: 'Dense · Precise · Analytical',
  },
]

function CinemaPreview() {
  return (
    <div className="w-full h-full bg-black flex flex-col overflow-hidden relative">
      <div className="absolute inset-0 bg-gradient-to-br from-blue-900/60 via-indigo-800/40 to-blue-600/20" />
      <div className="absolute inset-0 bg-gradient-to-t from-black via-black/20 to-transparent" />
      <div className="relative z-10 flex items-center justify-between px-4 pt-10 pb-2">
        <div className="text-[9px] font-light tracking-[0.2em] text-amber-300/80 uppercase">Hikari</div>
        <div className="flex gap-1">
          <div className="w-8 h-0.5 rounded-full bg-amber-400" />
          <div className="w-8 h-0.5 rounded-full bg-white/20" />
        </div>
      </div>
      <div className="absolute bottom-10 left-0 right-0 z-10 px-4">
        <div className="text-[8px] text-amber-400/70 uppercase tracking-widest mb-1">3Blue1Brown</div>
        <div className="text-white text-[11px] font-light leading-tight mb-2">
          But what is a Neural Network?
        </div>
        <div className="flex items-center gap-2">
          <div className="w-5 h-5 rounded-full bg-white/10 flex items-center justify-center">
            <div className="w-0 h-0 border-t-[3px] border-t-transparent border-b-[3px] border-b-transparent border-l-[5px] border-l-white/80 ml-0.5" />
          </div>
          <div className="w-5 h-5 rounded-full bg-white/10 flex items-center justify-center">
            <div className="w-2.5 h-0.5 bg-white/60" />
          </div>
          <div className="ml-auto text-[8px] text-white/40">20:04</div>
        </div>
      </div>
      <div className="absolute bottom-4 left-4 right-4 h-px bg-white/10 rounded-full">
        <div className="h-full w-1/3 bg-amber-400 rounded-full" />
      </div>
    </div>
  )
}

function EditorialPreview() {
  return (
    <div className="w-full h-full bg-[#FAFAF9] flex flex-col overflow-hidden relative">
      <div className="flex items-center justify-between px-4 pt-8 pb-2.5 border-b border-stone-200">
        <div className="text-[11px] tracking-wide font-medium text-stone-800" style={{ fontFamily: 'var(--font-fraunces)' }}>Hikari</div>
        <div className="flex gap-2.5">
          <div className="text-[8px] text-stone-800 font-medium border-b border-stone-800 pb-px">Neu</div>
          <div className="text-[8px] text-stone-400">Alt</div>
        </div>
      </div>
      <div className="px-4 pt-3 flex-1 overflow-hidden">
        <div className="text-[7px] text-green-800 uppercase tracking-widest mb-1.5 font-medium">Mathematik</div>
        <div className="text-[13px] leading-tight text-stone-900 mb-2.5" style={{ fontFamily: 'var(--font-fraunces)', fontWeight: 500 }}>
          But what is a Neural Network?
        </div>
        <div className="flex items-center gap-1.5 mb-2.5">
          <div className="w-8 h-px bg-stone-900" />
          <div className="text-[7px] text-stone-400">3Blue1Brown · 20 Min</div>
        </div>
        <div className="w-full h-[72px] rounded-sm bg-gradient-to-br from-blue-900 via-indigo-800 to-blue-600 mb-3 relative overflow-hidden">
          <div className="absolute inset-0 flex items-center justify-center">
            <div className="w-7 h-7 rounded-full border border-white/40 flex items-center justify-center">
              <div className="w-0 h-0 border-t-[4px] border-t-transparent border-b-[4px] border-b-transparent border-l-[7px] border-l-white/80 ml-0.5" />
            </div>
          </div>
        </div>
        <div className="border-l-2 border-green-700 pl-2">
          <div className="text-[6px] text-green-800 uppercase tracking-widest mb-0.5 font-medium">KI-Einschätzung</div>
          <div className="text-[7.5px] text-stone-600 leading-relaxed">Visuelle Erklärung neuronaler Netze — mathematisch präzise und dabei zugänglich.</div>
        </div>
      </div>
    </div>
  )
}

function DashboardPreview() {
  return (
    <div className="w-full h-full bg-[#0A0A0A] flex flex-col overflow-hidden relative p-2.5">
      <div className="flex items-center justify-between mb-2">
        <div className="text-[8px] font-mono text-violet-400 tracking-widest">HIKARI</div>
        <div className="flex gap-1">
          <div className="text-[6px] font-mono text-violet-400 bg-violet-400/10 border border-violet-400/20 px-1.5 py-0.5 rounded-sm">FEED</div>
        </div>
      </div>
      <div className="grid grid-cols-3 gap-1 flex-1" style={{ gridTemplateRows: 'repeat(4, 1fr)' }}>
        <div className="col-span-2 row-span-3 bg-gradient-to-br from-blue-900/60 via-indigo-800/40 to-blue-600/20 rounded-sm border border-white/5 p-2 relative overflow-hidden">
          <div className="absolute bottom-2 left-2 right-2">
            <div className="text-[6px] text-violet-400 font-mono mb-0.5">SCORE 97</div>
            <div className="text-[8px] text-white leading-tight">Neural Network Explained</div>
          </div>
        </div>
        <div className="bg-white/5 rounded-sm border border-white/5 p-1.5 flex flex-col">
          <div className="text-[5px] font-mono text-white/30 uppercase mb-0.5">Score</div>
          <div className="text-[14px] font-mono text-violet-400 leading-none mt-auto">97</div>
        </div>
        <div className="bg-violet-400/10 border border-violet-400/20 rounded-sm p-1.5 flex flex-col">
          <div className="text-[5px] font-mono text-violet-400/60 uppercase mb-0.5">Budget</div>
          <div className="text-[9px] font-mono text-violet-300 mt-auto">8/15</div>
        </div>
        <div className="col-span-3 grid grid-cols-3 gap-1">
          <div className="bg-white/5 rounded-sm border border-white/5 p-1.5">
            <div className="text-[5px] font-mono text-white/30">DUR</div>
            <div className="text-[8px] font-mono text-white/60">20:04</div>
          </div>
          <div className="bg-white/5 rounded-sm border border-white/5 p-1.5">
            <div className="text-[5px] font-mono text-white/30">CAT</div>
            <div className="text-[7px] font-mono text-cyan-400">MATH</div>
          </div>
          <div className="bg-white/5 rounded-sm border border-white/5 p-1.5">
            <div className="text-[5px] font-mono text-white/30">CH</div>
            <div className="text-[7px] font-mono text-white/50">3B1B</div>
          </div>
        </div>
      </div>
      <div className="flex gap-1 mt-2">
        {['FEED', 'CH', 'SVD', 'SET'].map((item, i) => (
          <div key={item} className={`flex-1 text-center py-1 rounded-sm text-[5px] font-mono ${i === 0 ? 'bg-violet-400/20 text-violet-400 border border-violet-400/30' : 'text-white/25'}`}>
            {item}
          </div>
        ))}
      </div>
    </div>
  )
}

export default function LandingPage() {
  const [dark, setDark] = useState(true)
  const router = useRouter()

  useEffect(() => {
    const stored = localStorage.getItem('hikari-theme')
    if (stored === 'light') {
      setDark(false)
      document.documentElement.classList.remove('dark')
    } else {
      document.documentElement.classList.add('dark')
    }
  }, [])

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
    <main
      className="min-h-screen transition-colors duration-300"
      style={{
        background: dark
          ? 'radial-gradient(ellipse at 20% 20%, #1a1208 0%, #0a0a0a 50%, #000 100%)'
          : 'radial-gradient(ellipse at 20% 20%, #fefcf8 0%, #f5f0e8 50%, #ede8de 100%)',
      }}
    >
      <header className="flex items-center justify-between px-8 py-6 max-w-7xl mx-auto">
        <div className="flex items-center gap-3">
          <div className="w-7 h-7 rounded-sm flex items-center justify-center" style={{ background: 'linear-gradient(135deg, #F5A524, #e8880a)' }}>
            <span className="text-black text-xs font-bold">光</span>
          </div>
          <span className={`text-base font-medium tracking-tight ${dark ? 'text-white' : 'text-stone-900'}`} style={{ fontFamily: 'var(--font-geist-sans)' }}>
            Hikari
          </span>
        </div>
        <button
          onClick={toggleTheme}
          className={`flex items-center gap-2 px-4 py-2 rounded-md text-sm transition-all border ${
            dark
              ? 'border-white/10 text-white/60 hover:text-white hover:border-white/20 bg-white/5'
              : 'border-stone-300 text-stone-600 hover:text-stone-900 hover:border-stone-400 bg-white/50'
          }`}
        >
          {dark ? <Sun size={14} /> : <Moon size={14} />}
          <span>{dark ? 'Light mode' : 'Dark mode'}</span>
        </button>
      </header>

      <section className="max-w-7xl mx-auto px-8 pt-10 pb-16">
        <div className="mb-3">
          <span className={`text-xs uppercase tracking-[0.2em] font-medium ${dark ? 'text-amber-400/70' : 'text-amber-700/70'}`}>
            Design Preview — Pick your direction
          </span>
        </div>
        <h1
          className={`text-5xl md:text-7xl leading-none tracking-tight mb-6 ${dark ? 'text-white' : 'text-stone-900'}`}
          style={{ fontFamily: 'var(--font-fraunces)', fontWeight: 300 }}
        >
          Three ways to see
          <br />
          <em className="not-italic" style={{ color: '#F5A524' }}>Hikari</em>
        </h1>
        <p className={`text-lg max-w-xl leading-relaxed ${dark ? 'text-white/50' : 'text-stone-500'}`}>
          Each card below is a complete design philosophy for the Android app. Pick the one that feels right — it becomes the basis for v1.0.
        </p>
      </section>

      <section className="max-w-7xl mx-auto px-8 pb-24">
        <div className="grid grid-cols-1 md:grid-cols-3 gap-6">
          {variants.map((v, i) => (
            <VariantCard key={v.id} variant={v} dark={dark} index={i} onEnter={() => router.push(`/${v.id}`)} />
          ))}
        </div>
      </section>
    </main>
  )
}

function VariantCard({ variant, dark, index, onEnter }: {
  variant: typeof variants[0]
  dark: boolean
  index: number
  onEnter: () => void
}) {
  const [hovered, setHovered] = useState(false)

  return (
    <div
      className="group relative rounded-2xl overflow-hidden cursor-pointer transition-all duration-500"
      style={{
        border: `1px solid ${hovered ? variant.accentColor + '40' : dark ? 'rgba(255,255,255,0.07)' : 'rgba(0,0,0,0.1)'}`,
        boxShadow: hovered ? `0 20px 60px ${variant.accentColor}20, 0 0 0 1px ${variant.accentColor}20` : 'none',
        transform: hovered ? 'translateY(-4px)' : 'translateY(0)',
        background: dark ? 'rgba(255,255,255,0.03)' : 'rgba(255,255,255,0.7)',
      }}
      onMouseEnter={() => setHovered(true)}
      onMouseLeave={() => setHovered(false)}
      onClick={onEnter}
    >
      {/* Phone preview */}
      <div className="relative mx-auto mt-8" style={{ width: 160, height: 300 }}>
        <div
          className="absolute inset-0 rounded-[22px]"
          style={{
            background: '#1a1a1a',
            boxShadow: `0 0 0 1px rgba(255,255,255,0.06), 0 20px 40px rgba(0,0,0,0.4)${hovered ? `, 0 0 30px ${variant.accentColor}20` : ''}`,
          }}
        >
          <div className="absolute inset-[2px] rounded-[20px] overflow-hidden bg-black">
            <div className="absolute top-2 left-1/2 -translate-x-1/2 w-10 h-3 rounded-full bg-black z-10" />
            <div className="absolute inset-0">
              {index === 0 && <CinemaPreview />}
              {index === 1 && <EditorialPreview />}
              {index === 2 && <DashboardPreview />}
            </div>
          </div>
        </div>
      </div>

      <div className="p-6 pt-5">
        <div className="flex items-center justify-between mb-1">
          <span className="text-xs uppercase tracking-[0.12em] font-medium" style={{ color: variant.accentColor }}>
            {variant.feeling}
          </span>
          <span className={`text-xs font-mono ${dark ? 'text-white/20' : 'text-stone-400'}`}>{String(index + 1).padStart(2, '0')}</span>
        </div>
        <h2
          className={`text-2xl font-light mb-2 ${dark ? 'text-white' : 'text-stone-900'}`}
          style={{ fontFamily: 'var(--font-fraunces)' }}
        >
          {variant.name}
        </h2>
        <p className={`text-sm leading-relaxed mb-5 ${dark ? 'text-white/50' : 'text-stone-500'}`}>
          {variant.description}
        </p>
        <button
          className="flex items-center gap-2 text-sm font-medium transition-all"
          style={{ color: variant.accentColor }}
          onClick={(e) => { e.stopPropagation(); onEnter() }}
        >
          Enter {variant.name}
          <ArrowRight size={14} style={{ transform: hovered ? 'translateX(4px)' : 'translateX(0)', transition: 'transform 0.2s' }} />
        </button>
      </div>
    </div>
  )
}
