'use client'

import { useState } from 'react'
import { mockChannels } from '@/lib/mock-data'
import { RefreshCw, Plus, HardDrive, CheckCircle, XCircle } from 'lucide-react'

function formatMB(mb: number) {
  if (mb >= 1000) return `${(mb / 1000).toFixed(1)} GB`
  return `${mb} MB`
}

export default function CinemaChannels() {
  const [dark] = useState(true)
  const [refreshing, setRefreshing] = useState<string | null>(null)

  const handleRefresh = (id: string) => {
    setRefreshing(id)
    setTimeout(() => setRefreshing(null), 1500)
  }

  return (
    <div
      className="min-h-full"
      style={{
        background: dark ? 'transparent' : 'transparent',
        fontFamily: 'var(--font-geist-sans)',
      }}
    >
      {/* Page header */}
      <div className="px-5 pt-6 pb-4">
        <h1
          className="text-3xl font-light mb-1"
          style={{
            color: dark ? '#fff' : '#1a0e00',
            fontFamily: 'var(--font-geist-sans)',
          }}
        >
          Channels
        </h1>
        <p className="text-sm" style={{ color: dark ? 'rgba(255,255,255,0.35)' : 'rgba(0,0,0,0.4)' }}>
          {mockChannels.length} abonnierte Kanäle
        </p>
      </div>

      {/* Add channel button */}
      <div className="px-5 mb-5">
        <button
          className="w-full flex items-center justify-center gap-2 py-3 rounded text-sm font-medium transition-all"
          style={{
            border: '1px dashed rgba(245,165,36,0.3)',
            color: '#F5A524',
            background: 'rgba(245,165,36,0.05)',
          }}
        >
          <Plus size={15} />
          Kanal hinzufügen
        </button>
      </div>

      {/* Channel list */}
      <div className="px-5 space-y-3 pb-4">
        {mockChannels.map((channel) => {
          const approvalRate = Math.round((channel.approved / channel.totalVideos) * 100)
          return (
            <div
              key={channel.id}
              className="rounded-lg p-4 transition-all"
              style={{
                background: dark ? 'rgba(255,255,255,0.04)' : 'rgba(255,255,255,0.7)',
                border: dark ? '1px solid rgba(255,255,255,0.07)' : '1px solid rgba(0,0,0,0.08)',
              }}
            >
              {/* Channel header */}
              <div className="flex items-start justify-between mb-3">
                <div>
                  <div className="flex items-center gap-2 mb-0.5">
                    <div
                      className="w-2 h-2 rounded-full flex-shrink-0"
                      style={{ background: channel.accentColor }}
                    />
                    <span
                      className="font-medium text-sm"
                      style={{ color: dark ? '#fff' : '#1a0e00' }}
                    >
                      {channel.name}
                    </span>
                  </div>
                  <span
                    className="text-xs ml-4"
                    style={{ color: dark ? 'rgba(255,255,255,0.3)' : 'rgba(0,0,0,0.4)' }}
                  >
                    {channel.handle}
                  </span>
                </div>
                <button
                  onClick={() => handleRefresh(channel.id)}
                  className="p-1.5 rounded transition-all"
                  style={{ color: dark ? 'rgba(255,255,255,0.3)' : 'rgba(0,0,0,0.3)' }}
                >
                  <RefreshCw
                    size={14}
                    style={{
                      transform: refreshing === channel.id ? 'rotate(360deg)' : 'none',
                      transition: 'transform 0.8s',
                    }}
                  />
                </button>
              </div>

              {/* Stats row */}
              <div className="grid grid-cols-3 gap-2 mb-3">
                <div>
                  <div className="text-xs font-mono" style={{ color: '#F5A524' }}>
                    {channel.totalVideos}
                  </div>
                  <div className="text-[10px]" style={{ color: dark ? 'rgba(255,255,255,0.3)' : 'rgba(0,0,0,0.4)' }}>
                    Videos
                  </div>
                </div>
                <div className="flex items-start gap-1">
                  <CheckCircle size={11} className="mt-0.5 flex-shrink-0" style={{ color: '#22c55e' }} />
                  <div>
                    <div className="text-xs font-mono" style={{ color: '#22c55e' }}>{channel.approved}</div>
                    <div className="text-[10px]" style={{ color: dark ? 'rgba(255,255,255,0.3)' : 'rgba(0,0,0,0.4)' }}>
                      Freigegeben
                    </div>
                  </div>
                </div>
                <div className="flex items-start gap-1">
                  <XCircle size={11} className="mt-0.5 flex-shrink-0" style={{ color: '#ef4444' }} />
                  <div>
                    <div className="text-xs font-mono" style={{ color: '#ef4444' }}>{channel.rejected}</div>
                    <div className="text-[10px]" style={{ color: dark ? 'rgba(255,255,255,0.3)' : 'rgba(0,0,0,0.4)' }}>
                      Abgelehnt
                    </div>
                  </div>
                </div>
              </div>

              {/* Approval bar */}
              <div className="mb-2">
                <div className="h-1 rounded-full overflow-hidden" style={{ background: dark ? 'rgba(255,255,255,0.08)' : 'rgba(0,0,0,0.08)' }}>
                  <div
                    className="h-full rounded-full transition-all"
                    style={{ width: `${approvalRate}%`, background: '#22c55e' }}
                  />
                </div>
                <div className="flex justify-between mt-1">
                  <span className="text-[10px]" style={{ color: dark ? 'rgba(255,255,255,0.25)' : 'rgba(0,0,0,0.35)' }}>
                    {approvalRate}% freigegeben
                  </span>
                  <span className="text-[10px] flex items-center gap-1" style={{ color: dark ? 'rgba(255,255,255,0.25)' : 'rgba(0,0,0,0.35)' }}>
                    <HardDrive size={9} />
                    {formatMB(channel.diskMB)}
                  </span>
                </div>
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
