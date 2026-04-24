'use client'

import { useState } from 'react'
import { sponsorBlockCategories, sponsorBlockStats } from '@/lib/mock-data'
import { Activity, Clock, SkipForward } from 'lucide-react'

type SBBehavior = 'auto' | 'manual' | 'ignore'

const llmProviders = [
  { id: 'claude-sonnet', label: 'Claude Sonnet', cost: 'API', latency: '~2s' },
  { id: 'claude-haiku', label: 'Claude Haiku', cost: 'API', latency: '~0.8s' },
  { id: 'ollama', label: 'Ollama', cost: 'lokal', latency: 'variabel' },
  { id: 'lmstudio', label: 'LM Studio', cost: 'lokal', latency: 'variabel' },
]

export default function DashboardSettings() {
  const [dark] = useState(true)
  const [backendUrl, setBackendUrl] = useState('100.64.0.1:8080')
  const [dailyBudget, setDailyBudget] = useState(15)
  const [llmProvider, setLlmProvider] = useState('claude-sonnet')
  const [sbBehaviors, setSbBehaviors] = useState<Record<string, SBBehavior>>(
    Object.fromEntries(sponsorBlockCategories.map(c => [c.key, c.defaultBehavior]))
  )

  const textPrimary = dark ? '#fff' : '#0A0A0A'
  const textMuted = dark ? 'rgba(255,255,255,0.4)' : 'rgba(0,0,0,0.4)'
  const cardBg = dark ? 'rgba(255,255,255,0.04)' : 'rgba(0,0,0,0.03)'
  const cardBorder = dark ? 'rgba(255,255,255,0.08)' : '#E5E5E5'
  const violet = '#8B5CF6'

  const SectionHeader = ({ label }: { label: string }) => (
    <div
      className="flex items-center gap-2 px-3 py-2 mb-2"
      style={{ background: cardBg, border: `1px solid ${cardBorder}`, borderRadius: 2 }}
    >
      <span className="text-[9px] font-mono uppercase tracking-widest" style={{ color: violet }}>{label}</span>
    </div>
  )

  return (
    <div className="p-3 pb-6" style={{ fontFamily: 'var(--font-geist-sans)' }}>

      {/* Backend section */}
      <SectionHeader label="VERBINDUNG" />
      <div className="mb-3 rounded-sm overflow-hidden" style={{ border: `1px solid ${cardBorder}` }}>
        <div className="flex items-center justify-between px-3 py-2.5" style={{ borderBottom: `1px solid ${cardBorder}` }}>
          <span className="text-[9px] font-mono uppercase" style={{ color: textMuted }}>BACKEND URL</span>
          <input
            className="text-right text-[11px] font-mono outline-none bg-transparent"
            style={{ color: violet, width: 140 }}
            value={backendUrl}
            onChange={e => setBackendUrl(e.target.value)}
          />
        </div>
        <div className="px-3 py-2.5">
          <div className="flex items-center justify-between mb-2">
            <span className="text-[9px] font-mono uppercase" style={{ color: textMuted }}>TAGES-BUDGET</span>
            <span className="text-[11px] font-mono" style={{ color: violet }}>{dailyBudget}/30</span>
          </div>
          <div
            className="relative h-1.5 rounded-sm cursor-pointer"
            style={{ background: dark ? 'rgba(255,255,255,0.06)' : '#E5E5E5' }}
            onClick={(e) => {
              const rect = e.currentTarget.getBoundingClientRect()
              setDailyBudget(Math.round(((e.clientX - rect.left) / rect.width) * 30))
            }}
          >
            <div
              className="absolute top-0 left-0 h-full rounded-sm transition-all"
              style={{ width: `${(dailyBudget / 30) * 100}%`, background: violet }}
            />
          </div>
          {/* Tick marks */}
          <div className="flex justify-between mt-1">
            {[1, 5, 10, 15, 20, 25, 30].map(n => (
              <span key={n} className="text-[7px] font-mono" style={{ color: textMuted }}>{n}</span>
            ))}
          </div>
        </div>
      </div>

      {/* LLM section */}
      <SectionHeader label="KI-ANBIETER" />
      <div className="mb-3 rounded-sm overflow-hidden" style={{ border: `1px solid ${cardBorder}` }}>
        {llmProviders.map((p, i) => (
          <div
            key={p.id}
            className="flex items-center gap-3 px-3 py-2.5 cursor-pointer transition-all"
            style={{
              borderBottom: i < llmProviders.length - 1 ? `1px solid ${cardBorder}` : 'none',
              background: llmProvider === p.id ? 'rgba(139,92,246,0.08)' : 'transparent',
            }}
            onClick={() => setLlmProvider(p.id)}
          >
            {/* Selector dot */}
            <div
              className="w-3 h-3 rounded-sm flex items-center justify-center flex-shrink-0 transition-all"
              style={{ border: `1px solid ${llmProvider === p.id ? violet : cardBorder}`, background: llmProvider === p.id ? violet : 'transparent' }}
            >
              {llmProvider === p.id && <div className="w-1 h-1 bg-white rounded-sm" />}
            </div>
            <div className="flex-1">
              <div className="text-xs font-medium" style={{ color: llmProvider === p.id ? violet : textPrimary }}>{p.label}</div>
            </div>
            <div className="flex gap-2">
              <span className="text-[8px] font-mono px-1.5 py-0.5 rounded-sm" style={{ background: cardBg, color: textMuted }}>{p.cost}</span>
              <span className="text-[8px] font-mono px-1.5 py-0.5 rounded-sm" style={{ background: cardBg, color: textMuted }}>{p.latency}</span>
            </div>
          </div>
        ))}
      </div>

      {/* SponsorBlock section */}
      <SectionHeader label="SPONSORBLOCK" />

      {/* Stats */}
      <div className="grid grid-cols-2 gap-2 mb-2">
        <div className="rounded-sm p-2.5 flex items-center gap-2" style={{ background: 'rgba(139,92,246,0.08)', border: '1px solid rgba(139,92,246,0.2)' }}>
          <Clock size={12} style={{ color: violet }} />
          <div>
            <div className="text-[7px] font-mono uppercase" style={{ color: 'rgba(139,92,246,0.6)' }}>GESPART</div>
            <div className="text-sm font-mono" style={{ color: violet }}>{sponsorBlockStats.timeSaved}</div>
          </div>
        </div>
        <div className="rounded-sm p-2.5 flex items-center gap-2" style={{ background: cardBg, border: `1px solid ${cardBorder}` }}>
          <SkipForward size={12} style={{ color: textMuted }} />
          <div>
            <div className="text-[7px] font-mono uppercase" style={{ color: textMuted }}>ÜBERSPRUNGEN</div>
            <div className="text-sm font-mono" style={{ color: textPrimary }}>{sponsorBlockStats.totalSkipped}</div>
          </div>
        </div>
      </div>

      {/* Categories */}
      <div className="rounded-sm overflow-hidden" style={{ border: `1px solid ${cardBorder}` }}>
        {sponsorBlockCategories.map((cat, i) => (
          <div
            key={cat.key}
            className="flex items-center gap-2 px-3 py-2"
            style={{ borderBottom: i < sponsorBlockCategories.length - 1 ? `1px solid ${cardBorder}` : 'none' }}
          >
            <div className="w-2 h-2 rounded-sm flex-shrink-0" style={{ background: cat.color }} />
            <span className="text-[10px] flex-1 min-w-0 truncate" style={{ color: textPrimary }}>{cat.labelDe}</span>
            <div className="flex gap-px">
              {(['auto', 'manual', 'ignore'] as SBBehavior[]).map((b, bi) => (
                <button
                  key={b}
                  onClick={() => setSbBehaviors(prev => ({ ...prev, [cat.key]: b }))}
                  className="px-1.5 py-0.5 text-[7px] font-mono uppercase transition-all"
                  style={{
                    background: sbBehaviors[cat.key] === b ? 'rgba(139,92,246,0.2)' : dark ? 'rgba(255,255,255,0.04)' : '#f0f0f0',
                    color: sbBehaviors[cat.key] === b ? violet : textMuted,
                    borderRadius: bi === 0 ? '2px 0 0 2px' : bi === 2 ? '0 2px 2px 0' : '0',
                    border: `1px solid ${sbBehaviors[cat.key] === b ? 'rgba(139,92,246,0.3)' : cardBorder}`,
                    marginRight: bi < 2 ? -1 : 0,
                  }}
                >
                  {b === 'auto' ? 'A' : b === 'manual' ? 'M' : 'I'}
                </button>
              ))}
            </div>
          </div>
        ))}
      </div>
    </div>
  )
}
