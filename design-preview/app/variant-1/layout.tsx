'use client'

import { useState, useEffect } from 'react'
import { usePathname, useRouter } from 'next/navigation'
import { Clapperboard, BookMarked, Users, Settings2, Sun, Moon, ArrowLeft } from 'lucide-react'

const navItems = [
  { label: 'Feed', href: '/variant-1/feed', icon: Clapperboard },
  { label: 'Channels', href: '/variant-1/channels', icon: Users },
  { label: 'Saved', href: '/variant-1/saved', icon: BookMarked },
  { label: 'Settings', href: '/variant-1/settings', icon: Settings2 },
]

export default function CinemaLayout({ children }: { children: React.ReactNode }) {
  const [dark, setDark] = useState(true)
  const pathname = usePathname()
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

  // Feed is full-bleed; other screens have a contained layout
  const isFeed = pathname === '/variant-1/feed'

  return (
    <div
      className="min-h-screen flex flex-col"
      style={{
        background: dark
          ? '#000'
          : 'linear-gradient(160deg, #FAF5EF 0%, #F0E8DC 100%)',
        fontFamily: 'var(--font-geist-sans)',
      }}
    >
      {/* Top bar — only on non-feed screens */}
      {!isFeed && (
        <header
          className="flex items-center justify-between px-5 pt-12 pb-3"
          style={{ borderBottom: dark ? '1px solid rgba(255,255,255,0.06)' : '1px solid rgba(0,0,0,0.08)' }}
        >
          <button onClick={() => router.push('/')} className="flex items-center gap-2 opacity-50 hover:opacity-100 transition-opacity">
            <ArrowLeft size={16} style={{ color: dark ? '#fff' : '#1a1208' }} />
          </button>
          <div className="flex items-center gap-2">
            <div className="w-5 h-5 rounded-sm flex items-center justify-center" style={{ background: 'linear-gradient(135deg, #F5A524, #e8880a)' }}>
              <span className="text-black text-[9px] font-bold">光</span>
            </div>
            <span className="text-sm font-medium tracking-tight" style={{ color: dark ? '#F5A524' : '#92400e' }}>
              Cinema
            </span>
          </div>
          <button onClick={toggleTheme} className="p-1.5 opacity-50 hover:opacity-100 transition-opacity">
            {dark ? <Sun size={16} color="#F5A524" /> : <Moon size={16} color="#92400e" />}
          </button>
        </header>
      )}

      {/* Main content */}
      <main className="flex-1 relative">
        {children}
      </main>

      {/* Bottom nav */}
      <nav
        className="flex items-center justify-around px-4 pb-8 pt-3 sticky bottom-0"
        style={{
          background: dark
            ? 'linear-gradient(to top, rgba(0,0,0,0.98) 0%, rgba(0,0,0,0.85) 100%)'
            : 'linear-gradient(to top, rgba(250,245,239,0.98) 0%, rgba(250,245,239,0.85) 100%)',
          backdropFilter: 'blur(20px)',
        }}
      >
        {navItems.map((item) => {
          const active = pathname === item.href
          const Icon = item.icon
          return (
            <button
              key={item.href}
              onClick={() => router.push(item.href)}
              className="flex flex-col items-center gap-1 transition-all"
              style={{ opacity: active ? 1 : 0.4 }}
            >
              <Icon
                size={22}
                style={{ color: active ? '#F5A524' : dark ? '#fff' : '#1a1208' }}
                strokeWidth={active ? 2 : 1.5}
              />
              <span
                className="text-[10px] tracking-wide"
                style={{ color: active ? '#F5A524' : dark ? 'rgba(255,255,255,0.5)' : 'rgba(0,0,0,0.4)' }}
              >
                {item.label}
              </span>
            </button>
          )
        })}
      </nav>
    </div>
  )
}
