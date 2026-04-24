'use client'

import { useEffect } from 'react'
import { usePathname, useRouter } from 'next/navigation'
import { useHikariStore } from '@/lib/store'
import { LayoutGrid, BookMarked, Radio, SlidersHorizontal, BarChart2, Sun, Moon } from 'lucide-react'

const navItems = [
  { label: 'FEED', href: '/variant-3/feed', icon: LayoutGrid },
  { label: 'SAVED', href: '/variant-3/saved', icon: BookMarked },
  { label: 'KANÄLE', href: '/variant-3/channels', icon: Radio },
  { label: 'STATS', href: '/variant-3/stats', icon: BarChart2 },
  { label: 'CONFIG', href: '/variant-3/settings', icon: SlidersHorizontal },
]

export default function DashboardLayout({ children }: { children: React.ReactNode }) {
  const { theme, setTheme } = useHikariStore()
  const dark = theme === 'dark'
  const pathname = usePathname()
  const router = useRouter()

  // Sync theme to DOM
  useEffect(() => {
    if (dark) {
      document.documentElement.classList.add('dark')
    } else {
      document.documentElement.classList.remove('dark')
    }
  }, [dark])

  const toggleTheme = () => {
    setTheme(dark ? 'light' : 'dark')
  }

  const bg = dark ? '#0A0A0A' : '#FFFFFF'
  const border = dark ? '1px solid rgba(255,255,255,0.07)' : '1px solid #E5E5E5'
  const violet = '#8B5CF6'

  // Feed uses full-height layout — no header/nav chrome overlap
  const isFeed = pathname === '/variant-3/feed'

  if (isFeed) {
    return (
      <div style={{ background: bg, minHeight: '100dvh', position: 'relative' }}>
        {/* Theme toggle — floats over feed, bottom-right corner */}
        <button
          onClick={toggleTheme}
          className="fixed bottom-4 right-3 z-50 w-8 h-8 flex items-center justify-center rounded-md transition-opacity"
          style={{ background: 'rgba(0,0,0,0.5)', border: '1px solid rgba(255,255,255,0.12)', backdropFilter: 'blur(8px)', opacity: 0.7 }}
        >
          {dark ? <Sun size={12} color="#8B5CF6" /> : <Moon size={12} color="#8B5CF6" />}
        </button>

        {/* Bottom nav — floats over feed */}
        <nav
          className="fixed bottom-0 left-0 right-0 z-40 flex items-center gap-1 px-3 py-2 pb-6"
          style={{ background: 'rgba(0,0,0,0.75)', borderTop: '1px solid rgba(255,255,255,0.06)', backdropFilter: 'blur(16px)' }}
        >
          {navItems.map((item) => {
            const active = pathname === item.href
            const Icon = item.icon
            return (
              <button
                key={item.href}
                onClick={() => router.push(item.href)}
                className="flex-1 flex flex-col items-center gap-0.5 py-1 rounded-md transition-all relative"
                style={{
                  background: 'transparent',
                }}
              >
                {active && (
                  <div
                    className="absolute top-0 left-1/2 -translate-x-1/2 rounded-sm"
                    style={{ width: 18, height: 1.5, background: violet }}
                  />
                )}
                <Icon
                  size={15}
                  color={active ? violet : 'rgba(255,255,255,0.35)'}
                  strokeWidth={active ? 2 : 1.5}
                />
                <span
                  className="text-[7px] font-mono tracking-widest"
                  style={{ color: active ? violet : 'rgba(255,255,255,0.25)' }}
                >
                  {item.label}
                </span>
              </button>
            )
          })}
        </nav>

        <div>{children}</div>
      </div>
    )
  }

  return (
    <div
      className="flex flex-col"
      style={{ background: bg, minHeight: '100dvh', fontFamily: 'var(--font-geist-sans)' }}
    >
      {/* Top header */}
      <header
        className="flex items-center justify-between px-4 pt-12 pb-3 sticky top-0 z-30"
        style={{ borderBottom: border, background: bg }}
      >
        <div className="flex items-center gap-3">
          <div className="font-mono text-xs tracking-widest font-semibold" style={{ color: violet }}>
            HIKARI
          </div>
          <div
            className="text-[8px] font-mono px-1.5 py-0.5 rounded-sm"
            style={{ background: 'rgba(139,92,246,0.12)', border: '1px solid rgba(139,92,246,0.2)', color: violet }}
          >
            DASHBOARD
          </div>
        </div>

        <div className="flex items-center gap-2">
          <div
            className="text-[9px] font-mono px-2 py-1 rounded-sm"
            style={{ background: dark ? 'rgba(255,255,255,0.05)' : '#F5F5F5', color: dark ? 'rgba(255,255,255,0.5)' : '#666' }}
          >
            <span style={{ color: violet }}>8</span>
            <span>/15</span>
          </div>
          <button onClick={toggleTheme} className="p-1.5" style={{ opacity: 0.6 }}>
            {dark ? <Sun size={13} color={violet} /> : <Moon size={13} color={violet} />}
          </button>
        </div>
      </header>

      <main className="flex-1 overflow-y-auto">
        {children}
      </main>

      {/* Bottom nav */}
      <nav
        className="flex items-center gap-1 px-3 py-2 pb-8 sticky bottom-0 z-30"
        style={{ background: bg, borderTop: border }}
      >
        {navItems.map((item) => {
          const active = pathname === item.href || (item.href !== '/variant-3/feed' && pathname?.startsWith(item.href))
          const Icon = item.icon
          return (
            <button
              key={item.href}
              onClick={() => router.push(item.href)}
              className="flex-1 flex flex-col items-center gap-0.5 py-1.5 rounded-md transition-all relative"
              style={{
                background: active ? 'rgba(139,92,246,0.1)' : 'transparent',
                border: active ? '1px solid rgba(139,92,246,0.2)' : '1px solid transparent',
              }}
            >
              {active && (
                <div
                  className="absolute top-0 left-1/2 -translate-x-1/2 rounded-sm"
                  style={{ width: 16, height: 1.5, background: violet }}
                />
              )}
              <Icon
                size={14}
                color={active ? violet : dark ? 'rgba(255,255,255,0.3)' : 'rgba(0,0,0,0.3)'}
                strokeWidth={active ? 2 : 1.5}
              />
              <span
                className="text-[7px] font-mono tracking-widest"
                style={{ color: active ? violet : dark ? 'rgba(255,255,255,0.25)' : 'rgba(0,0,0,0.3)' }}
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
