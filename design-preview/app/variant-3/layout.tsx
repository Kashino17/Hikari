'use client'

import { useState, useEffect } from 'react'
import { usePathname, useRouter } from 'next/navigation'
import { LayoutGrid, BookMarked, Radio, SlidersHorizontal, Sun, Moon, ArrowLeft } from 'lucide-react'

const navItems = [
  { label: 'FEED', href: '/variant-3/feed', icon: LayoutGrid },
  { label: 'CH', href: '/variant-3/channels', icon: Radio },
  { label: 'SAVED', href: '/variant-3/saved', icon: BookMarked },
  { label: 'CONFIG', href: '/variant-3/settings', icon: SlidersHorizontal },
]

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  const [dark, setDark] = useState(true)
  const pathname = usePathname()
  const router = useRouter()

  useEffect(() => {
    const stored = localStorage.getItem('hikari-theme')
    if (stored === 'light') {
      setDark(false)
      document.documentElement.classList.remove('dark')
    } else {
      setDark(true)
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
    <div
      className="min-h-screen flex flex-col"
      style={{
        background: dark ? '#0A0A0A' : '#FFFFFF',
        fontFamily: 'var(--font-geist-sans)',
      }}
    >
      {/* Top status bar — dashboard style */}
      <header
        className="flex items-center justify-between px-4 pt-12 pb-3"
        style={{ borderBottom: dark ? '1px solid rgba(255,255,255,0.08)' : '1px solid #E5E5E5' }}
      >
        <div className="flex items-center gap-3">
          <button onClick={() => router.push('/')} className="transition-opacity" style={{ opacity: 0.4 }}>
            <ArrowLeft size={14} style={{ color: dark ? '#fff' : '#0A0A0A' }} />
          </button>
          <div
            className="font-mono text-xs tracking-widest font-semibold"
            style={{ color: '#8B5CF6' }}
          >
            HIKARI
          </div>
          <div
            className="text-[9px] font-mono px-1.5 py-0.5 rounded-sm"
            style={{ background: 'rgba(139,92,246,0.12)', border: '1px solid rgba(139,92,246,0.2)', color: '#8B5CF6' }}
          >
            DASHBOARD
          </div>
        </div>

        <div className="flex items-center gap-2">
          <div
            className="text-[9px] font-mono px-2 py-1 rounded-sm"
            style={{ background: dark ? 'rgba(255,255,255,0.05)' : '#F5F5F5', color: dark ? 'rgba(255,255,255,0.5)' : '#666' }}
          >
            8/15 HEUTE
          </div>
          <button onClick={toggleTheme} className="p-1 transition-opacity" style={{ opacity: 0.5 }}>
            {dark ? <Sun size={13} style={{ color: '#8B5CF6' }} /> : <Moon size={13} style={{ color: '#8B5CF6' }} />}
          </button>
        </div>
      </header>

      <main className="flex-1">
        {children}
      </main>

      {/* Bottom nav — bento-style tabs */}
      <nav
        className="flex items-center gap-1 px-3 py-3 pb-8 sticky bottom-0"
        style={{
          background: dark ? '#0A0A0A' : '#FFFFFF',
          borderTop: dark ? '1px solid rgba(255,255,255,0.08)' : '1px solid #E5E5E5',
        }}
      >
        {navItems.map((item) => {
          const active = pathname === item.href
          const Icon = item.icon
          return (
            <button
              key={item.href}
              onClick={() => router.push(item.href)}
              className="flex-1 flex flex-col items-center gap-1 py-2 rounded-sm transition-all"
              style={{
                background: active ? 'rgba(139,92,246,0.12)' : 'transparent',
                border: active ? '1px solid rgba(139,92,246,0.25)' : '1px solid transparent',
              }}
            >
              <Icon
                size={15}
                style={{ color: active ? '#8B5CF6' : dark ? 'rgba(255,255,255,0.3)' : 'rgba(0,0,0,0.3)' }}
                strokeWidth={active ? 2 : 1.5}
              />
              <span
                className="text-[8px] font-mono tracking-widest"
                style={{ color: active ? '#8B5CF6' : dark ? 'rgba(255,255,255,0.25)' : 'rgba(0,0,0,0.3)' }}
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
