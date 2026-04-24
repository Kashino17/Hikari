'use client'

import { useState } from 'react'
import { sponsorBlockCategories, sponsorBlockStats } from '@/lib/mock-data'
import { Server, Cpu, Clock, SkipForward } from 'lucide-react'

type SBBehavior = 'auto' | 'manual' | 'ignore'

const llmProviders = ['Claude Sonnet', 'Claude Haiku', 'Ollama (lokal)', 'LM Studio']

export default function CinemaSettings() {
  const [dark] = useState(true)
  const [backendUrl, setBackendUrl] = useState('100.64.0.1:8080')
  const [dailyBudget, setDailyBudget] = useState(15)
  const [llmProvider, setLlmProvider] = useState('Claude Sonnet')
  const [sbBehaviors, setSbBehaviors] = useState<Record<string, SBBehavior>>(
    Object.fromEntries(sponsorBlockCategories.map(c => [c.key, c.defaultBehavior]))
  )

  const textPrimary = dark ? '#fff' : '#1a0e00'
  const textSecondary = dark ? 'rgba(255,255,255,0.4)' : 'rgba(0,0,0,0.5)'
  const cardBg = dark ? 'rgba(255,255,255,0.04)' : 'rgba(255,255,255,0.7)'
  const cardBorder = dark ? '1px solid rgba(255,255,255,0.07)' : '1px solid rgba(0,0,0,0.08)'
  const inputBg = dark ? 'rgba(255,255,255,0.06)' : 'rgba(0,0,0,0.04)'

  const Section = ({ icon: Icon, title, children }: { icon: React.ElementType; title: string; children: React.ReactNode }) => (
    <div className="mb-5">
      <div className="flex items-center gap-2 px-5 mb-2">
        <Icon size={13} color="#F5A524" />
        <span className="text-[11px] uppercase tracking-widest" style={{ color: '#F5A524' }}>{title}</span>
      </div>
      <div className="mx-5 rounded-lg overflow-hidden" style={{ background: cardBg, border: cardBorder }}>
        {children}
      </div>
    </div>
  )

  const Row = ({ label, children }: { label: string; children: React.ReactNode }) => (
    <div className="flex items-center justify-between px-4 py-3" style={{ borderBottom: dark ? '1px solid rgba(255,255,255,0.05)' : '1px solid rgba(0,0,0,0.05)' }}>
      <span className="text-sm" style={{ color: textPrimary }}>{label}</span>
      {children}
    </div>
  )

  return (
    <div className="min-h-full pb-6" style={{ fontFamily: 'var(--font-geist-sans)' }}>
      <div className="px-5 pt-6 pb-4">
        <h1 className="text-3xl font-light mb-1" style={{ color: textPrimary }}>Einstellungen</h1>
        <p className="text-sm" style={{ color: textSecondary }}>Hikari — Cinema</p>
      </div>

      <Section icon={Server} title="Backend">
        <Row label="Backend URL">
          <input
            className="text-right text-sm font-mono rounded-md px-2 py-1 outline-none w-36"
            style={{ background: inputBg, color: '#F5A524', border: '1px solid rgba(245,165,36,0.2)' }}
            value={backendUrl}
            onChange={e => setBackendUrl(e.target.value)}
          />
        </Row>
        <div className="px-4 py-3">
          <div className="flex justify-between mb-3">
            <span className="text-sm" style={{ color: textPrimary }}>Tages-Budget</span>
            <span className="text-sm font-mono" style={{ color: '#F5A524' }}>{dailyBudget} Videos</span>
          </div>
          <div className="relative">
            <input
              type="range"
              min={1}
              max={30}
              value={dailyBudget}
              onChange={e => setDailyBudget(Number(e.target.value))}
              className="w-full h-1 rounded-full appearance-none cursor-pointer"
              style={{
                background: `linear-gradient(to right, #F5A524 0%, #F5A524 ${(dailyBudget / 30) * 100}%, rgba(255,255,255,0.12) ${(dailyBudget / 30) * 100}%, rgba(255,255,255,0.12) 100%)`,
              }}
            />
          </div>
          <div className="flex justify-between mt-1">
            <span className="text-[10px]" style={{ color: textSecondary }}>1</span>
            <span className="text-[10px]" style={{ color: textSecondary }}>30</span>
          </div>
        </div>
      </Section>

      <Section icon={Cpu} title="KI-Anbieter">
        <div className="p-3 grid grid-cols-2 gap-2">
          {llmProviders.map(p => (
            <button
              key={p}
              onClick={() => setLlmProvider(p)}
              className="py-2 px-3 rounded text-xs text-left transition-all"
              style={{
                background: llmProvider === p ? 'rgba(245,165,36,0.15)' : 'rgba(255,255,255,0.03)',
                border: llmProvider === p ? '1px solid rgba(245,165,36,0.4)' : '1px solid rgba(255,255,255,0.06)',
                color: llmProvider === p ? '#F5A524' : textSecondary,
              }}
            >
              {p}
            </button>
          ))}
        </div>
      </Section>

      <Section icon={SkipForward} title="SponsorBlock">
        {/* Stats */}
        <div className="px-4 py-3 flex gap-4" style={{ borderBottom: dark ? '1px solid rgba(255,255,255,0.05)' : '1px solid rgba(0,0,0,0.05)' }}>
          <div className="flex items-center gap-1.5">
            <Clock size={12} style={{ color: '#F5A524' }} />
            <span className="text-xs font-mono" style={{ color: '#F5A524' }}>{sponsorBlockStats.timeSaved}</span>
            <span className="text-[10px]" style={{ color: textSecondary }}>gespart</span>
          </div>
          <div className="flex items-center gap-1.5">
            <SkipForward size={12} style={{ color: textSecondary }} />
            <span className="text-xs font-mono" style={{ color: textPrimary }}>{sponsorBlockStats.totalSkipped}</span>
            <span className="text-[10px]" style={{ color: textSecondary }}>übersprungen</span>
          </div>
        </div>

        {/* Categories */}
        <div>
          {sponsorBlockCategories.map((cat, i) => (
            <div
              key={cat.key}
              className="flex items-center justify-between px-4 py-2.5"
              style={{ borderBottom: i < sponsorBlockCategories.length - 1 ? (dark ? '1px solid rgba(255,255,255,0.04)' : '1px solid rgba(0,0,0,0.04)') : 'none' }}
            >
              <div className="flex items-center gap-2">
                <div className="w-2 h-2 rounded-full flex-shrink-0" style={{ background: cat.color }} />
                <span className="text-xs" style={{ color: textPrimary }}>{cat.labelDe}</span>
              </div>
              <div className="flex gap-1">
                {(['auto', 'manual', 'ignore'] as SBBehavior[]).map(b => (
                  <button
                    key={b}
                    onClick={() => setSbBehaviors(prev => ({ ...prev, [cat.key]: b }))}
                    className="px-2 py-0.5 rounded text-[9px] font-mono uppercase transition-all"
                    style={{
                      background: sbBehaviors[cat.key] === b ? 'rgba(245,165,36,0.15)' : 'rgba(255,255,255,0.04)',
                      border: sbBehaviors[cat.key] === b ? '1px solid rgba(245,165,36,0.3)' : '1px solid transparent',
                      color: sbBehaviors[cat.key] === b ? '#F5A524' : textSecondary,
                    }}
                  >
                    {b === 'auto' ? 'Auto' : b === 'manual' ? 'Manuell' : 'Ignore'}
                  </button>
                ))}
              </div>
            </div>
          ))}
        </div>
      </Section>
    </div>
  )
}
