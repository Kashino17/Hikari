'use client'

import { useState } from 'react'
import { Compass, Hash, PlaySquare } from 'lucide-react'
import DiscoverTab from '@/components/DiscoverTab'
import DiscoveryCandidates from '@/components/DiscoveryCandidates'
import { cn } from '@/lib/utils'

type Tab = 'channels' | 'videos'

export default function DiscoverPage() {
  const [tab, setTab] = useState<Tab>('channels')

  return (
    <div className="min-h-svh">
      {/* Tab bar — sits above each component's own sticky header so we
          render them as plain content (no double sticky). */}
      <div
        className="sticky top-0 z-30 bg-[var(--color-bg)]/95 backdrop-blur border-b-hairline"
        style={{ paddingTop: 'env(safe-area-inset-top, 0)' }}
      >
        <div className="px-5 pt-4 pb-2 flex items-center gap-2">
          <Compass size={14} className="text-accent" strokeWidth={2} />
          <h1 className="text-base font-medium tracking-tight">Entdecken</h1>
        </div>
        <div className="flex px-5">
          <TabButton
            active={tab === 'channels'}
            onClick={() => setTab('channels')}
            icon={<Hash size={12} strokeWidth={2} />}
            label="Kanäle"
          />
          <TabButton
            active={tab === 'videos'}
            onClick={() => setTab('videos')}
            icon={<PlaySquare size={12} strokeWidth={2} />}
            label="Videos"
          />
        </div>
      </div>

      {/* Render only the active tab so component-internal sticky headers,
          fetch-on-mount effects, and scroll positions don't collide. */}
      {tab === 'channels' ? <DiscoverTab embedded /> : <DiscoveryCandidates embedded />}
    </div>
  )
}

function TabButton({
  active,
  onClick,
  icon,
  label,
}: {
  active: boolean
  onClick: () => void
  icon: React.ReactNode
  label: string
}) {
  return (
    <button
      onClick={onClick}
      className={cn(
        'flex items-center gap-1.5 px-3 py-2 text-[12px] border-b-2 transition-colors',
        active
          ? 'text-accent border-[var(--color-accent)]'
          : 'text-faint border-transparent hover:text-mute',
      )}
    >
      {icon}
      {label}
    </button>
  )
}
