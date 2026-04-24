'use client'

import { useState } from 'react'
import { mockChannels } from '@/lib/mock-data'
import { RefreshCw, Plus } from 'lucide-react'

function formatMB(mb: number) {
  if (mb >= 1000) return `${(mb / 1000).toFixed(1)} GB`
  return `${mb} MB`
}

export default function EditorialChannels() {
  const [dark] = useState(false)
  const [refreshing, setRefreshing] = useState<string | null>(null)

  const textPrimary = dark ? '#F0EAE0' : '#1A1817'
  const textSecondary = dark ? 'rgba(240,234,224,0.5)' : 'rgba(26,24,23,0.5)'
  const accent = '#14532D'
  const divider = dark ? 'rgba(255,255,255,0.08)' : '#E8E4DC'

  const handleRefresh = (id: string) => {
    setRefreshing(id)
    setTimeout(() => setRefreshing(null), 1500)
  }

  return (
    <div style={{ fontFamily: 'var(--font-geist-sans)' }}>
      {/* Header */}
      <div className="px-5 pt-6 pb-4" style={{ borderBottom: `1px solid ${divider}` }}>
        <h1
          className="text-3xl mb-1"
          style={{ fontFamily: 'var(--font-fraunces)', fontWeight: 400, color: textPrimary }}
        >
          Kanäle
        </h1>
        <p className="text-sm" style={{ color: textSecondary }}>
          {mockChannels.length} abonnierte Quellen
        </p>
      </div>

      {/* Add channel */}
      <div className="px-5 py-4" style={{ borderBottom: `1px solid ${divider}` }}>
        <button
          className="flex items-center gap-2 text-sm transition-all"
          style={{ color: accent }}
        >
          <Plus size={14} />
          <span style={{ borderBottom: `1px solid ${accent}` }}>Kanal hinzufügen</span>
        </button>
      </div>

      {/* Channel list — editorial: clean table-like rows */}
      <div>
        {mockChannels.map((channel, i) => {
          const approvalRate = Math.round((channel.approved / channel.totalVideos) * 100)
          return (
            <div
              key={channel.id}
              className="px-5 py-5"
              style={{ borderBottom: `1px solid ${divider}` }}
            >
              {/* Header row */}
              <div className="flex items-start justify-between mb-3">
                <div>
                  <h2
                    className="text-lg mb-0.5 leading-tight"
                    style={{ fontFamily: 'var(--font-fraunces)', fontWeight: 500, color: textPrimary }}
                  >
                    {channel.name}
                  </h2>
                  <span className="text-xs" style={{ color: textSecondary }}>{channel.handle}</span>
                </div>
                <button
                  onClick={() => handleRefresh(channel.id)}
                  className="mt-1 transition-all"
                  style={{ color: textSecondary }}
                >
                  <RefreshCw
                    size={13}
                    style={{
                      transform: refreshing === channel.id ? 'rotate(360deg)' : 'none',
                      transition: 'transform 0.8s',
                    }}
                  />
                </button>
              </div>

              {/* Stats — editorial: clean columns with dividers */}
              <div className="grid grid-cols-3 gap-0 mb-3" style={{ borderTop: `1px solid ${divider}`, borderLeft: `1px solid ${divider}` }}>
                {[
                  { label: 'Videos', value: channel.totalVideos.toString(), mono: true },
                  { label: 'Freigegeben', value: `${channel.approved} (${approvalRate}%)`, mono: true, color: '#14532D' },
                  { label: 'Gespeichert', value: formatMB(channel.diskMB), mono: true },
                ].map((stat) => (
                  <div
                    key={stat.label}
                    className="p-2.5"
                    style={{ borderRight: `1px solid ${divider}`, borderBottom: `1px solid ${divider}` }}
                  >
                    <div className="text-[9px] uppercase tracking-widest mb-1" style={{ color: textSecondary }}>
                      {stat.label}
                    </div>
                    <div
                      className="text-sm font-mono leading-none"
                      style={{ color: stat.color || textPrimary }}
                    >
                      {stat.value}
                    </div>
                  </div>
                ))}
              </div>

              {/* Approval bar */}
              <div>
                <div className="h-px relative" style={{ background: divider }}>
                  <div
                    className="absolute top-0 left-0 h-full transition-all"
                    style={{ width: `${approvalRate}%`, background: accent }}
                  />
                </div>
                <div className="flex justify-between mt-1.5">
                  <span className="text-[10px]" style={{ color: accent }}>
                    {approvalRate}% Freigaberate
                  </span>
                  <span className="text-[10px] text-red-500/60">
                    {channel.rejected} abgelehnt
                  </span>
                </div>
              </div>
            </div>
          )
        })}
      </div>

      <div className="h-8" />
    </div>
  )
}
