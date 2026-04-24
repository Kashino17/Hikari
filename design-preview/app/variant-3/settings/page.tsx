'use client'

import { useState } from 'react'
import { sponsorBlockCategories, sponsorBlockStats } from '@/lib/mock-data'
import { useHikariStore, SBBehavior } from '@/lib/store'
import { useRouter } from 'next/navigation'
import { Clock, SkipForward, CheckCircle, XCircle, RotateCcw, Save, ChevronRight, AlertTriangle } from 'lucide-react'

const DEFAULT_PROMPT = `Du bist Hikaris KI-Bewerter für Kurzvideos. Bewerte jeden Clip von 0–100.

Hohe Punktzahl (80–100): Lehrt etwas Substantielles, respektiert die Intelligenz des Zuschauers, kein Clickbait, klare Aussage, gute Informationsdichte, keine Manipulation.

Niedrige Punktzahl (0–40): Reine Unterhaltung ohne Lernwert, irreführende Titel, emotionale Manipulation, übertriebene Reaktionen, Padding/Filler.

Ausgabe als JSON: { "score": <0-100>, "category": "<math|science|tech|philosophy|society|history|art>", "reasoning": "<1 Satz auf Deutsch>" }`.trim()

const LLM_PROVIDERS = [
  { id: 'claude-haiku', label: 'Claude Haiku 4.5', modelId: 'claude-haiku-4-5', type: 'API', latency: '~0.8s' },
  { id: 'ollama', label: 'Ollama', modelId: 'llama3.2:3b', type: 'Lokal', latency: 'variabel' },
  { id: 'lmstudio', label: 'LM Studio', modelId: 'lmstudio-community/..', type: 'Lokal', latency: 'variabel' },
]

export default function DashboardSettings() {
  const router = useRouter()
  const {
    theme,
    backendUrl, setBackendUrl,
    dailyBudget, setDailyBudget,
    llmProvider, setLlmProvider,
    sbBehaviors, setSbBehavior,
    scoringPrompt, setScoringPrompt,
  } = useHikariStore()
  const dark = theme === 'dark'

  const [connectionStatus, setConnectionStatus] = useState<'idle' | 'success' | 'error'>('idle')
  const [llmTestStatus, setLlmTestStatus] = useState<Record<string, 'idle' | 'testing' | 'ok' | 'fail'>>({})
  const [promptDirty, setPromptDirty] = useState(false)
  const [promptSaved, setPromptSaved] = useState(false)
  const [localPrompt, setLocalPrompt] = useState(scoringPrompt)

  const textPrimary = dark ? '#fff' : '#0A0A0A'
  const textMuted = dark ? 'rgba(255,255,255,0.4)' : 'rgba(0,0,0,0.45)'
  const cardBg = dark ? 'rgba(255,255,255,0.04)' : 'rgba(0,0,0,0.025)'
  const cardBorder = dark ? 'rgba(255,255,255,0.08)' : '#e5e5e5'
  const violet = '#8B5CF6'

  const SectionLabel = ({ label }: { label: string }) => (
    <div className="text-[8px] font-mono uppercase tracking-widest mb-2 px-1" style={{ color: textMuted }}>
      {label}
    </div>
  )

  const CardWrap = ({ children, className = '' }: { children: React.ReactNode; className?: string }) => (
    <div
      className={`rounded-md overflow-hidden mb-3 ${className}`}
      style={{ background: cardBg, border: `1px solid ${cardBorder}` }}
    >
      {children}
    </div>
  )

  const Row = ({ children, noBorder = false }: { children: React.ReactNode; noBorder?: boolean }) => (
    <div
      className="flex items-center gap-3 px-3 py-2.5"
      style={{ borderBottom: noBorder ? 'none' : `1px solid ${cardBorder}` }}
    >
      {children}
    </div>
  )

  const testConnection = () => {
    setConnectionStatus('idle')
    setTimeout(() => setConnectionStatus('success'), 900)
    setTimeout(() => setConnectionStatus('idle'), 4000)
  }

  const testLlm = (id: string) => {
    setLlmTestStatus((s) => ({ ...s, [id]: 'testing' }))
    setTimeout(() => setLlmTestStatus((s) => ({ ...s, [id]: 'ok' })), 1200)
    setTimeout(() => setLlmTestStatus((s) => ({ ...s, [id]: 'idle' })), 5000)
  }

  const savePrompt = () => {
    setScoringPrompt(localPrompt)
    setPromptDirty(false)
    setPromptSaved(true)
    setTimeout(() => setPromptSaved(false), 2000)
  }

  const resetPrompt = () => {
    setLocalPrompt(DEFAULT_PROMPT)
    setPromptDirty(true)
  }

  return (
    <div className="p-3 pb-8" style={{ fontFamily: 'var(--font-geist-sans)' }}>

      {/* ── Card 1: Backend ─────────────────────────────── */}
      <SectionLabel label="Verbindung" />
      <CardWrap>
        <Row>
          <span className="text-[9px] font-mono uppercase flex-1" style={{ color: textMuted }}>Backend URL</span>
          <input
            className="text-right text-[11px] font-mono outline-none bg-transparent flex-1"
            style={{ color: violet }}
            value={backendUrl}
            onChange={(e) => setBackendUrl(e.target.value)}
            spellCheck={false}
          />
        </Row>
        <Row noBorder>
          <div className="flex items-center gap-1.5">
            <div
              className="w-1.5 h-1.5 rounded-full"
              style={{ background: connectionStatus === 'success' ? '#10B981' : connectionStatus === 'error' ? '#EF4444' : 'rgba(255,255,255,0.2)' }}
            />
            <span className="text-[9px] font-mono" style={{ color: connectionStatus === 'success' ? '#10B981' : connectionStatus === 'error' ? '#EF4444' : textMuted }}>
              {connectionStatus === 'success' ? 'verbunden' : connectionStatus === 'error' ? 'offline' : 'unbekannt'}
            </span>
          </div>
          <div className="ml-auto">
            <button
              onClick={testConnection}
              className="px-2.5 py-1.5 rounded-md text-[9px] font-mono uppercase tracking-wider transition-all"
              style={{ background: 'rgba(139,92,246,0.12)', border: '1px solid rgba(139,92,246,0.25)', color: violet }}
            >
              Verbinden testen
            </button>
          </div>
        </Row>
      </CardWrap>

      {/* ── Card 2: Daily Budget ────────────────────────── */}
      <SectionLabel label="Tages-Budget" />
      <CardWrap>
        <div className="p-3">
          <div className="flex items-end gap-2 mb-3">
            <div className="text-5xl font-mono leading-none" style={{ color: violet }}>{dailyBudget}</div>
            <div className="mb-1">
              <div className="text-[8px] font-mono" style={{ color: textMuted }}>/30</div>
              <div className="text-[8px] font-mono" style={{ color: textMuted }}>Reels/Tag</div>
            </div>
          </div>

          <div
            className="relative h-2 rounded-sm cursor-pointer"
            style={{ background: dark ? 'rgba(255,255,255,0.07)' : '#e5e5e5' }}
            onClick={(e) => {
              const rect = e.currentTarget.getBoundingClientRect()
              setDailyBudget(Math.max(1, Math.min(30, Math.round(((e.clientX - rect.left) / rect.width) * 30))))
            }}
          >
            <div
              className="absolute top-0 left-0 h-full rounded-sm"
              style={{ width: `${(dailyBudget / 30) * 100}%`, background: violet }}
            />
            <div
              className="absolute top-1/2 -translate-y-1/2 w-4 h-4 rounded-sm"
              style={{ left: `calc(${(dailyBudget / 30) * 100}% - 8px)`, background: violet, border: '2px solid white' }}
            />
          </div>

          <div className="flex justify-between mt-2">
            {[1, 5, 10, 15, 20, 25, 30].map((n) => (
              <span key={n} className="text-[7px] font-mono" style={{ color: textMuted }}>{n}</span>
            ))}
          </div>

          <div className="mt-2 text-[9px] font-mono" style={{ color: textMuted }}>
            Maximale neue Reels pro Tag
          </div>
        </div>
      </CardWrap>

      {/* ── Card 3: LLM Provider ───────────────────────── */}
      <SectionLabel label="KI-Anbieter" />
      <CardWrap>
        {LLM_PROVIDERS.map((p, i) => {
          const active = llmProvider === p.id
          const status = llmTestStatus[p.id] || 'idle'
          return (
            <div
              key={p.id}
              className="px-3 py-2.5 cursor-pointer transition-all"
              style={{
                borderBottom: i < LLM_PROVIDERS.length - 1 ? `1px solid ${cardBorder}` : 'none',
                background: active ? 'rgba(139,92,246,0.07)' : 'transparent',
                boxShadow: active ? 'inset 0 0 0 1px rgba(139,92,246,0.25)' : 'none',
              }}
              onClick={() => setLlmProvider(p.id)}
            >
              <div className="flex items-center gap-3">
                <div
                  className="w-3 h-3 rounded-sm flex-shrink-0 flex items-center justify-center"
                  style={{ border: `1.5px solid ${active ? violet : cardBorder}`, background: active ? violet : 'transparent' }}
                >
                  {active && <div className="w-1 h-1 bg-white rounded-sm" />}
                </div>
                <div className="flex-1">
                  <div className="text-[12px] font-medium" style={{ color: active ? violet : textPrimary }}>
                    {p.label}
                  </div>
                  <div className="text-[8px] font-mono mt-0.5" style={{ color: textMuted }}>{p.modelId}</div>
                </div>
                <div className="flex items-center gap-2">
                  <span className="text-[8px] font-mono px-1.5 py-0.5 rounded-sm" style={{ background: dark ? 'rgba(255,255,255,0.06)' : '#f5f5f5', color: textMuted }}>
                    {p.type}
                  </span>
                  <span className="text-[8px] font-mono px-1.5 py-0.5 rounded-sm" style={{ background: dark ? 'rgba(255,255,255,0.06)' : '#f5f5f5', color: status === 'ok' ? '#10B981' : textMuted }}>
                    {status === 'testing' ? '…' : status === 'ok' ? p.latency : p.latency}
                  </span>
                  <button
                    onClick={(e) => { e.stopPropagation(); testLlm(p.id) }}
                    className="text-[8px] font-mono px-2 py-1 rounded-sm transition-all"
                    style={{ background: 'rgba(139,92,246,0.1)', border: '1px solid rgba(139,92,246,0.2)', color: violet }}
                  >
                    {status === 'testing' ? '…' : status === 'ok' ? 'ok' : 'test'}
                  </button>
                </div>
              </div>
            </div>
          )
        })}
      </CardWrap>

      {/* ── Card 4: Scoring Prompt ─────────────────────── */}
      <SectionLabel label="Scoring-Prompt" />
      <div
        className="rounded-md overflow-hidden mb-3"
        style={{ border: `1px solid ${promptDirty ? 'rgba(139,92,246,0.35)' : cardBorder}` }}
      >
        <div className="px-3 pt-3 pb-1" style={{ borderBottom: `1px solid ${cardBorder}` }}>
          <div className="text-[9px] font-mono" style={{ color: textMuted }}>
            Der Prompt definiert, was Hikari für dich wertvoll hält.
          </div>
        </div>
        <textarea
          className="w-full p-3 text-[11px] font-mono leading-relaxed outline-none resize-none bg-transparent"
          style={{ color: textPrimary, minHeight: 160 }}
          value={localPrompt}
          onChange={(e) => { setLocalPrompt(e.target.value); setPromptDirty(true); setPromptSaved(false) }}
          spellCheck={false}
        />
        <div
          className="flex items-center gap-2 px-3 py-2"
          style={{ borderTop: `1px solid ${cardBorder}`, background: dark ? 'rgba(255,255,255,0.02)' : 'rgba(0,0,0,0.02)' }}
        >
          <button
            onClick={resetPrompt}
            className="flex items-center gap-1.5 px-2.5 py-1.5 rounded-md text-[9px] font-mono uppercase"
            style={{ background: dark ? 'rgba(255,255,255,0.05)' : '#f5f5f5', border: `1px solid ${cardBorder}`, color: textMuted }}
          >
            <RotateCcw size={10} strokeWidth={1.5} />
            Reset
          </button>
          <button
            onClick={savePrompt}
            className="flex items-center gap-1.5 ml-auto px-2.5 py-1.5 rounded-md text-[9px] font-mono uppercase transition-all"
            style={{
              background: promptSaved ? 'rgba(16,185,129,0.15)' : promptDirty ? 'rgba(139,92,246,0.2)' : dark ? 'rgba(255,255,255,0.05)' : '#f5f5f5',
              border: `1px solid ${promptSaved ? 'rgba(16,185,129,0.3)' : promptDirty ? 'rgba(139,92,246,0.35)' : cardBorder}`,
              color: promptSaved ? '#10B981' : promptDirty ? violet : textMuted,
            }}
          >
            <Save size={10} strokeWidth={1.5} />
            {promptSaved ? 'Gespeichert' : 'Speichern'}
          </button>
        </div>
      </div>

      {/* ── Card 5: SponsorBlock ──────────────────────── */}
      <SectionLabel label="SponsorBlock" />

      {/* Stats pill */}
      <div
        className="flex items-center gap-3 rounded-md px-3 py-2.5 mb-2"
        style={{ background: 'rgba(139,92,246,0.08)', border: '1px solid rgba(139,92,246,0.2)' }}
      >
        <Clock size={13} color={violet} strokeWidth={1.5} />
        <span className="font-mono text-[11px]" style={{ color: violet }}>
          {sponsorBlockStats.totalSkipped} Segmente übersprungen · {sponsorBlockStats.timeSaved} gespart
        </span>
      </div>

      <CardWrap>
        {sponsorBlockCategories.map((cat, i) => (
          <div
            key={cat.key}
            className="flex items-center gap-2.5 px-3 py-2"
            style={{ borderBottom: i < sponsorBlockCategories.length - 1 ? `1px solid ${cardBorder}` : 'none' }}
          >
            <div className="w-2 h-2 rounded-sm flex-shrink-0" style={{ background: cat.color }} />
            <div className="flex-1 min-w-0">
              <span className="text-[11px] truncate" style={{ color: textPrimary }}>{cat.labelDe}</span>
              <div className="text-[8px] font-mono" style={{ color: textMuted }}>{cat.description}</div>
            </div>
            <div className="flex gap-px flex-shrink-0">
              {(['auto', 'manual', 'ignore'] as SBBehavior[]).map((b, bi) => {
                const active = sbBehaviors[cat.key] === b
                return (
                  <button
                    key={b}
                    onClick={() => setSbBehavior(cat.key, b)}
                    className="px-1.5 py-0.5 text-[7px] font-mono uppercase transition-all"
                    style={{
                      background: active ? 'rgba(139,92,246,0.2)' : dark ? 'rgba(255,255,255,0.04)' : '#f0f0f0',
                      color: active ? violet : textMuted,
                      borderRadius: bi === 0 ? '3px 0 0 3px' : bi === 2 ? '0 3px 3px 0' : '0',
                      border: `1px solid ${active ? 'rgba(139,92,246,0.35)' : cardBorder}`,
                      marginRight: bi < 2 ? -1 : 0,
                      zIndex: active ? 1 : 0,
                      position: 'relative',
                    }}
                  >
                    {b === 'auto' ? 'A' : b === 'manual' ? 'M' : 'I'}
                  </button>
                )
              })}
            </div>
          </div>
        ))}
      </CardWrap>

      {/* ── Card 6: Wöchentliche Statistik ───────────── */}
      <SectionLabel label="Statistiken" />
      <div
        className="rounded-md p-3 mb-3 cursor-pointer transition-all"
        style={{ background: cardBg, border: `1px solid ${cardBorder}` }}
        onClick={() => router.push('/variant-3/stats')}
      >
        <div className="flex items-center justify-between mb-2">
          <span className="text-[9px] font-mono uppercase tracking-widest" style={{ color: textMuted }}>Diese Woche</span>
          <ChevronRight size={13} color={textMuted} strokeWidth={1.5} />
        </div>
        <div className="flex items-end gap-1 mb-3">
          <div className="text-3xl font-mono leading-none" style={{ color: violet }}>23</div>
          <div className="text-[9px] font-mono mb-1" style={{ color: textMuted }}>approved</div>
        </div>
        {/* Mini bar chart */}
        <div className="flex items-end gap-1 h-8">
          {[3, 5, 2, 4, 6, 2, 1].map((v, i) => (
            <div key={i} className="flex-1 rounded-sm transition-all" style={{ height: `${(v / 6) * 100}%`, background: `rgba(139,92,246,${0.3 + i * 0.07})` }} />
          ))}
        </div>
        <div className="flex justify-between mt-1">
          {['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So'].map((d) => (
            <span key={d} className="flex-1 text-center text-[7px] font-mono" style={{ color: textMuted }}>{d}</span>
          ))}
        </div>
        <div className="mt-2 text-[9px] font-mono text-right" style={{ color: violet }}>Mehr anzeigen →</div>
      </div>

      {/* Rejected link */}
      <div
        className="rounded-md p-3 cursor-pointer flex items-center gap-3"
        style={{ background: cardBg, border: `1px solid ${cardBorder}` }}
        onClick={() => router.push('/variant-3/rejected')}
      >
        <AlertTriangle size={14} color="#EF4444" strokeWidth={1.5} />
        <div className="flex-1">
          <div className="text-[11px] font-medium" style={{ color: textPrimary }}>Abgelehnte Videos</div>
          <div className="text-[8px] font-mono" style={{ color: textMuted }}>Transparenz über Ablehnungsgründe</div>
        </div>
        <ChevronRight size={13} color={textMuted} strokeWidth={1.5} />
      </div>

    </div>
  )
}
