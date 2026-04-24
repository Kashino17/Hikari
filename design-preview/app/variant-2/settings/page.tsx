'use client'

import { useState } from 'react'
import { sponsorBlockCategories, sponsorBlockStats } from '@/lib/mock-data'

type SBBehavior = 'auto' | 'manual' | 'ignore'

const llmProviders = [
  { id: 'claude-sonnet', label: 'Claude Sonnet', note: 'Empfohlen' },
  { id: 'claude-haiku', label: 'Claude Haiku', note: 'Schneller' },
  { id: 'ollama', label: 'Ollama (lokal)', note: 'Privat' },
  { id: 'lmstudio', label: 'LM Studio', note: 'Lokal' },
]

export default function EditorialSettings() {
  const [dark] = useState(false)
  const [backendUrl, setBackendUrl] = useState('100.64.0.1:8080')
  const [dailyBudget, setDailyBudget] = useState(15)
  const [llmProvider, setLlmProvider] = useState('claude-sonnet')
  const [sbBehaviors, setSbBehaviors] = useState<Record<string, SBBehavior>>(
    Object.fromEntries(sponsorBlockCategories.map(c => [c.key, c.defaultBehavior]))
  )

  const textPrimary = dark ? '#F0EAE0' : '#1A1817'
  const textSecondary = dark ? 'rgba(240,234,224,0.5)' : 'rgba(26,24,23,0.5)'
  const accent = '#14532D'
  const divider = dark ? 'rgba(255,255,255,0.08)' : '#E8E4DC'

  const Section = ({ title, note, children }: { title: string; note?: string; children: React.ReactNode }) => (
    <div className="mb-0">
      <div
        className="px-5 py-4 flex items-baseline gap-2"
        style={{ borderBottom: `1px solid ${divider}` }}
      >
        <h2
          className="text-xl"
          style={{ fontFamily: 'var(--font-fraunces)', fontWeight: 400, color: textPrimary }}
        >
          {title}
        </h2>
        {note && <span className="text-xs" style={{ color: textSecondary }}>{note}</span>}
      </div>
      {children}
    </div>
  )

  return (
    <div style={{ fontFamily: 'var(--font-geist-sans)' }}>
      {/* Page header */}
      <div className="px-5 pt-6 pb-4" style={{ borderBottom: `1px solid ${divider}` }}>
        <h1 className="text-3xl mb-1" style={{ fontFamily: 'var(--font-fraunces)', fontWeight: 400, color: textPrimary }}>
          Einstellungen
        </h1>
      </div>

      {/* Backend */}
      <Section title="Verbindung">
        <div className="px-5 py-4" style={{ borderBottom: `1px solid ${divider}` }}>
          <label className="block text-[10px] uppercase tracking-widest mb-2" style={{ color: textSecondary }}>
            Backend-URL (Tailscale)
          </label>
          <input
            className="w-full py-2 text-sm font-mono outline-none border-b transition-all"
            style={{
              background: 'transparent',
              color: textPrimary,
              borderBottomColor: accent,
            }}
            value={backendUrl}
            onChange={e => setBackendUrl(e.target.value)}
          />
        </div>

        <div className="px-5 py-4" style={{ borderBottom: `1px solid ${divider}` }}>
          <div className="flex items-baseline justify-between mb-3">
            <label className="text-[10px] uppercase tracking-widest" style={{ color: textSecondary }}>
              Tages-Budget
            </label>
            <span className="text-sm font-mono" style={{ color: textPrimary }}>
              {dailyBudget} Videos / Tag
            </span>
          </div>
          <input
            type="range"
            min={1}
            max={30}
            value={dailyBudget}
            onChange={e => setDailyBudget(Number(e.target.value))}
            className="w-full appearance-none h-px cursor-pointer"
            style={{
              background: `linear-gradient(to right, ${accent} 0%, ${accent} ${(dailyBudget / 30) * 100}%, ${divider} ${(dailyBudget / 30) * 100}%, ${divider} 100%)`,
            }}
          />
          <div className="flex justify-between mt-2">
            <span className="text-[10px]" style={{ color: textSecondary }}>1</span>
            <span className="text-[10px]" style={{ color: textSecondary }}>30</span>
          </div>
        </div>
      </Section>

      {/* LLM */}
      <Section title="KI-Anbieter">
        <div className="py-2" style={{ borderBottom: `1px solid ${divider}` }}>
          {llmProviders.map((p) => (
            <div
              key={p.id}
              className="flex items-center justify-between px-5 py-3 cursor-pointer transition-all"
              style={{ borderBottom: `1px solid ${divider}` }}
              onClick={() => setLlmProvider(p.id)}
            >
              <div>
                <div className="text-sm" style={{ color: llmProvider === p.id ? accent : textPrimary }}>
                  {p.label}
                </div>
                <div className="text-[10px]" style={{ color: textSecondary }}>{p.note}</div>
              </div>
              <div
                className="w-4 h-4 rounded-full border-2 flex items-center justify-center transition-all"
                style={{ borderColor: llmProvider === p.id ? accent : divider }}
              >
                {llmProvider === p.id && (
                  <div className="w-2 h-2 rounded-full" style={{ background: accent }} />
                )}
              </div>
            </div>
          ))}
        </div>
      </Section>

      {/* SponsorBlock */}
      <Section title="SponsorBlock" note={`${sponsorBlockStats.timeSaved} gespart`}>
        {/* Stats row */}
        <div
          className="px-5 py-3 flex gap-6"
          style={{ borderBottom: `1px solid ${divider}`, background: dark ? 'rgba(20,83,45,0.08)' : '#F0F7F2' }}
        >
          <div>
            <div className="text-[10px] uppercase tracking-widest mb-0.5" style={{ color: textSecondary }}>Zeit gespart</div>
            <div className="text-base font-mono" style={{ color: accent }}>{sponsorBlockStats.timeSaved}</div>
          </div>
          <div>
            <div className="text-[10px] uppercase tracking-widest mb-0.5" style={{ color: textSecondary }}>Übersprungen</div>
            <div className="text-base font-mono" style={{ color: textPrimary }}>{sponsorBlockStats.totalSkipped}</div>
          </div>
        </div>

        {/* Categories */}
        {sponsorBlockCategories.map((cat, i) => (
          <div
            key={cat.key}
            className="px-5 py-3 flex items-center justify-between"
            style={{ borderBottom: i < sponsorBlockCategories.length - 1 ? `1px solid ${divider}` : 'none' }}
          >
            <div className="flex items-center gap-2.5">
              <div className="w-1.5 h-4 rounded-full flex-shrink-0" style={{ background: cat.color }} />
              <span className="text-sm" style={{ color: textPrimary }}>{cat.labelDe}</span>
            </div>
            <div className="flex rounded overflow-hidden" style={{ border: `1px solid ${divider}` }}>
              {(['auto', 'manual', 'ignore'] as SBBehavior[]).map((b, bi) => (
                <button
                  key={b}
                  onClick={() => setSbBehaviors(prev => ({ ...prev, [cat.key]: b }))}
                  className="px-2.5 py-1 text-[9px] uppercase tracking-wide transition-all"
                  style={{
                    background: sbBehaviors[cat.key] === b ? (dark ? 'rgba(20,83,45,0.2)' : '#E8F5EE') : 'transparent',
                    color: sbBehaviors[cat.key] === b ? accent : textSecondary,
                    borderRight: bi < 2 ? `1px solid ${divider}` : 'none',
                    fontWeight: sbBehaviors[cat.key] === b ? 600 : 400,
                  }}
                >
                  {b === 'auto' ? 'Auto' : b === 'manual' ? 'Manuell' : 'Ignore'}
                </button>
              ))}
            </div>
          </div>
        ))}
      </Section>

      <div className="h-8" />
    </div>
  )
}
