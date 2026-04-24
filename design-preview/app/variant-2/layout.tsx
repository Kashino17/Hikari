'use client'

import { useState, useEffect } from 'react'
import { usePathname, useRouter } from 'next/navigation'
import { Newspaper, Bookmark, Users, Settings2, Sun, Moon, ArrowLeft } from 'lucide-react'

const navItems = [
  { label: 'Lesen', href: '/variant-2/feed', icon: Newspaper },
  { label: 'Kanäle', href: '/variant-2/channels', icon: Users },
  { label: 'Gespeichert', href: '/variant-2/saved', icon: Bookmark },
  { label: 'Einstellungen', href: '/variant-2/settings', icon: Settings2 },
]

export default function EditorialLayout({ children }: { children: React.ReactNode }) {
  const [dark, setDark] = useState(false)
  const pathname = usePathname()
  const router = useRouter()

  useEffect(() => {
    const stored = localStorage.getItem('hikari-theme')
    if (stored === 'dark') {
      setDark(true)
      document.documentElement.classList.add('dark')
    } else {
      setDark(false)
      document.documentElement.classList.remove('dark')
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
        background: dark ? '#1A1817' : '#FAFAF9',
        fontFamily: 'var(--font-geist-sans)',
      }}
    >
      {/* Top bar */}
      <header
        className="flex items-center justify-between px-5 pt-12 pb-4"
        style={{ borderBottom: dark ? '1px solid rgba(255,255,255,0.08)' : '1px solid #E8E4DC' }}
      >
        <button onClick={() => router.push('/')} className="flex items-center gap-1.5 transition-opacity" style={{ opacity: 0.5 }}>
          <ArrowLeft size={15} style={{ color: dark ? '#fff' : '#1A1817' }} />
          <span className="text-xs" style={{ color: dark ? '#fff' : '#1A1817' }}>Zurück</span>
        </button>

        <div className="flex items-center gap-2">
          <span
            className="text-base tracking-tight"
            style={{ fontFamily: 'var(--font-fraunces)', color: dark ? '#F0EAE0' : '#1A1817', fontWeight: 500 }}
          >
            Hikari
          </span>
          <span className="text-[10px] px-1.5 py-0.5 rounded" style={{ background: dark ? '#14532D' : '#14532D', color: '#fff', fontFamily: 'var(--font-geist-sans)' }}>
            Editorial
          </span>
        </div>

        <button onClick={toggleTheme} className="p-1.5 transition-opacity" style={{ opacity: 0.6 }}>
          {dark ? <Sun size={15} style={{ color: '#F0EAE0' }} /> : <Moon size={15} style={{ color: '#1A1817' }} />}
        </button>
      </header>

      <main className="flex-1">
        {children}
      </main>

      {/* Bottom nav — editorial style: text-only, serif */}
      <nav
        className="flex items-center justify-around px-4 py-4 pb-8 sticky bottom-0"
        style={{
          background: dark ? '#1A1817' : '#FAFAF9',
          borderTop: dark ? '1px solid rgba(255,255,255,0.08)' : '1px solid #E8E4DC',
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
            >
              <Icon
                size={18}
                style={{ color: active ? '#14532D' : dark ? 'rgba(240,234,224,0.35)' : 'rgba(26,24,23,0.35)' }}
                strokeWidth={active ? 2 : 1.5}
              />
              <span
                className="text-[9px] tracking-wide"
                style={{
                  color: active ? '#14532D' : dark ? 'rgba(240,234,224,0.35)' : 'rgba(26,24,23,0.35)',
                  fontWeight: active ? 600 : 400,
                  borderBottom: active ? '1px solid #14532D' : 'none',
                }}
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
