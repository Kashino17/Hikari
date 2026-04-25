'use client'

import Link from 'next/link'
import { usePathname } from 'next/navigation'
import { Play, Hash, Sliders, LayoutGrid, BookOpen } from 'lucide-react'

const items = [
  { href: '/library', label: 'Bibliothek', Icon: LayoutGrid },
  { href: '/feed', label: 'Feed', Icon: Play },
  { href: '/manga', label: 'Manga', Icon: BookOpen },
  { href: '/channels', label: 'Kanäle', Icon: Hash },
  { href: '/tuning', label: 'Tuning', Icon: Sliders },
]

export function BottomNav() {
  const pathname = usePathname()

  return (
    <nav
      className="fixed bottom-0 inset-x-0 h-16 bg-[var(--color-bg)]/95 backdrop-blur border-t-hairline z-50"
      style={{ paddingBottom: 'env(safe-area-inset-bottom, 0)' }}
    >
      <div className="flex h-16 max-w-md mx-auto">
        {items.map(({ href, label, Icon }) => {
          const active = pathname === href || pathname.startsWith(href + '/')
          return (
            <Link
              key={href}
              href={href}
              className="flex-1 flex flex-col items-center justify-center gap-1 transition-colors"
            >
              <Icon
                size={20}
                strokeWidth={1.5}
                className={active ? 'text-accent' : 'text-faint'}
              />
              <span
                className={`text-[10px] tracking-wide ${
                  active ? 'text-accent' : 'text-faint'
                }`}
              >
                {label}
              </span>
            </Link>
          )
        })}
      </div>
    </nav>
  )
}
