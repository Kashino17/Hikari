'use client'

import { useState } from 'react'
import { mockChannels } from '@/lib/mock-data'
import { RefreshCw, Plus, TrendingUp, HardDrive, CheckSquare, XSquare } from 'lucide-react'

function formatMB(mb: number) {
  if (mb >= 1000) return `${(mb / 1000).toFixed(1)} GB`
  return `${mb} MB`
}

export default function DashboardChannels() {
  const [dark] = useState(true)
  const [refreshing, setRefreshing] = useState<string | null>(null)

  const textPrimary = dark ? '#fff' : '#0A0A0A'
  const textMuted = dark ? 'rgba(255,255,255,0.4)' : 'rgba(0,0,0,0.4)'
  const cardBg = dark ? 'rgba(255,255,255,0.04)' : 'rgba(0,0,0,0.03)'
  const cardBorder = dark ? 'rgba(255,255,255,0.08)' : '#E5E5E5'
  const violet = '#8B5CF6'

  const handleRefresh = (id: string) => {
    setRefreshing(id)
    setTimeout(() => setRefreshing(null), 1500)
  }

  // Aggregate stats
  const totalApproved = mockChannels.reduce((acc, c) => acc + c.approved, 0)
  const totalRejected = mockChannels.reduce((acc, c) => acc + c.rejected, 0)
  const totalDisk = mockChannels.reduce((acc, c) => acc + c.diskMB, 0)

  return (
    <div className="p-3" style={{ fontFamily: 'var(--font-geist-sans)' }}>
      {/* Top stats bento */}
      <div className="grid grid-cols-3 gap-2 mb-3">
        <div className="rounded-sm p-2.5" style={{ background: 'rgba(139,92,246,0.08)', border: '1px solid rgba(139,92,246,0.2)' }}>
          <div className="text-[7px] font-mono uppercase tracking-widest mb-1" style={{ color: 'rgba(139,92,246,0.6)' }}>KANÄLE</div>
          <div className="text-2xl font-mono leading-none" style={{ color: violet }}>{mockChannels.length}</div>
        </div>
        <div className="rounded-sm p-2.5" style={{ background: cardBg, border: `1px solid ${cardBorder}` }}>
          <div className="text-[7px] font-mono uppercase tracking-widest mb-1" style={{ color: textMuted }}>FREIGEGEBEN</div>
          <div className="text-lg font-mono leading-none" style={{ color: '#10B981' }}>{totalApproved}</div>
        </div>
        <div className="rounded-sm p-2.5" style={{ background: cardBg, border: `1px solid ${cardBorder}` }}>
          <div className="text-[7px] font-mono uppercase tracking-widest mb-1" style={{ color: textMuted }}>DISK</div>
          <div className="text-sm font-mono leading-none" style={{ color: textPrimary }}>{formatMB(totalDisk)}</div>
        </div>
      </div>

      {/* Add channel */}
      <button
        className="w-full mb-3 flex items-center justify-center gap-1.5 py-2.5 rounded-sm text-[10px] font-mono uppercase tracking-widest transition-all"
        style={{ border: `1px dashed rgba(139,92,246,0.3)`, color: violet, background: 'rgba(139,92,246,0.05)' }}
      >
        <Plus size={11} />
        KANAL HINZUFÜGEN
      </button>

      {/* Channel grid — bento cards */}
      <div className="space-y-2">
        {mockChannels.map((channel) => {
          const approvalRate = Math.round((channel.approved / channel.totalVideos) * 100)
          return (
            <div
              key={channel.id}
              className="rounded-sm overflow-hidden"
              style={{ border: `1px solid ${cardBorder}` }}
            >
              {/* Header */}
              <div
                className="flex items-center justify-between px-3 py-2"
                style={{ borderBottom: `1px solid ${cardBorder}`, background: cardBg }}
              >
                <div className="flex items-center gap-2">
                  <div className="w-1.5 h-4 rounded-full flex-shrink-0" style={{ background: channel.accentColor }} />
                  <div>
                    <div className="text-xs font-medium" style={{ color: textPrimary }}>{channel.name}</div>
                    <div className="text-[8px] font-mono" style={{ color: textMuted }}>{channel.handle}</div>
                  </div>
                </div>
                <button
                  onClick={() => handleRefresh(channel.id)}
                  className="p-1 rounded-sm transition-all"
                  style={{ background: 'rgba(255,255,255,0.05)' }}
                >
                  <RefreshCw
                    size={10}
                    style={{
                      color: textMuted,
                      transform: refreshing === channel.id ? 'rotate(360deg)' : 'none',
                      transition: 'transform 0.8s',
                    }}
                  />
                </button>
              </div>

              {/* Stats grid */}
              <div className="grid grid-cols-4">
                {[
                  { label: 'TOTAL', value: channel.totalVideos.toString(), color: textPrimary },
                  { label: 'OK', value: channel.approved.toString(), color: '#10B981' },
                  { label: 'REJECT', value: channel.rejected.toString(), color: '#EF4444' },
                  { label: 'DISK', value: formatMB(channel.diskMB), color: textMuted },
                ].map((stat, i) => (
                  <div
                    key={stat.label}
                    className="p-2.5"
                    style={{ borderRight: i < 3 ? `1px solid ${cardBorder}` : 'none' }}
                  >
                    <div className="text-[6px] font-mono uppercase tracking-widest mb-1" style={{ color: textMuted }}>{stat.label}</div>
                    <div className="text-sm font-mono leading-none" style={{ color: stat.color }}>{stat.value}</div>
                  </div>
                ))}
              </div>

              {/* Approval bar */}
              <div className="px-3 py-2" style={{ borderTop: `1px solid ${cardBorder}` }}>
                <div className="flex items-center justify-between mb-1">
                  <span className="text-[7px] font-mono uppercase" style={{ color: textMuted }}>FREIGABERATE</span>
                  <span className="text-[8px] font-mono" style={{ color: approvalRate >= 90 ? '#10B981' : approvalRate >= 70 ? '#F59E0B' : '#EF4444' }}>
                    {approvalRate}%
                  </span>
                </div>
                <div className="h-1 rounded-sm overflow-hidden" style={{ background: dark ? 'rgba(255,255,255,0.06)' : '#E5E5E5' }}>
                  <div
                    className="h-full rounded-sm transition-all"
                    style={{
                      width: `${approvalRate}%`,
                      background: approvalRate >= 90 ? '#10B981' : approvalRate >= 70 ? '#F59E0B' : '#EF4444',
                    }}
                  />
                </div>
              </div>
            </div>
          )
        })}
      </div>
    </div>
  )
}
