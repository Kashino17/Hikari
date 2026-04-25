'use client'

import { useState, useMemo } from 'react'
import { useHikariStore } from '@/lib/store'
import { sponsorBlockCategories } from '@/lib/mock-data'
import { buildPrompt, MOOD_OPTIONS, DEPTH_OPTIONS, LANGUAGE_OPTIONS } from '@/lib/prompt-builder'
import { ChipMulti, ChipFreeInput } from '@/components/Chip'
import { Copy, RotateCcw, Edit3, Check, X } from 'lucide-react'
import { MangaSyncButton } from '@/components/manga/MangaSyncButton'

type Tab = 'filter' | 'prompt' | 'system'

export default function TuningPage() {
  const [tab, setTab] = useState<Tab>('filter')

  return (
    <div className="min-h-svh">
      <header
        className="sticky top-0 z-20 bg-[var(--color-bg)]/95 backdrop-blur border-b-hairline"
        style={{ paddingTop: 'env(safe-area-inset-top, 0)' }}
      >
        <div className="px-5 pt-4 pb-3">
          <h1 className="text-base font-medium tracking-tight">Tuning</h1>
          <p className="text-faint text-[11px] mt-0.5">Was die KI für dich aussortiert.</p>
        </div>
        <div className="flex border-t-hairline">
          {(['filter', 'prompt', 'system'] as const).map((t) => {
            const active = tab === t
            const label = t === 'filter' ? 'Filter' : t === 'prompt' ? 'Prompt' : 'System'
            return (
              <button
                key={t}
                onClick={() => setTab(t)}
                className={`flex-1 py-3 text-[11px] uppercase tracking-[0.18em] transition-colors relative ${
                  active ? 'text-accent' : 'text-mute'
                }`}
              >
                {label}
                {active && (
                  <span className="absolute bottom-0 left-0 right-0 h-px bg-accent" />
                )}
              </button>
            )
          })}
        </div>
      </header>

      {tab === 'filter' && <FilterTab />}
      {tab === 'prompt' && <PromptTab />}
      {tab === 'system' && <SystemTab />}
    </div>
  )
}

// ─── FILTER TAB ──────────────────────────────────────────────────────────────

function Section({ label, hint, children }: { label: string; hint?: string; children: React.ReactNode }) {
  return (
    <section className="px-5 py-5 border-b-hairline">
      <div className="text-faint text-[10px] font-mono uppercase tracking-widest mb-1">{label}</div>
      {hint && <div className="text-faint text-[11px] mb-3">{hint}</div>}
      <div className={hint ? '' : 'mt-3'}>{children}</div>
    </section>
  )
}

function FilterTab() {
  const filter = useHikariStore((s) => s.filter)
  const update = useHikariStore((s) => s.updateFilter)
  const reset = useHikariStore((s) => s.resetFilter)

  return (
    <div>
      <Section label="Themen die du magst" hint="Tippen, Enter zum Hinzufügen.">
        <ChipFreeInput
          values={filter.likeTags}
          onChange={(v) => update({ likeTags: v })}
          placeholder="z.B. Mathematik, Geschichte…"
        />
      </Section>

      <Section label="Themen die du nicht magst" hint="Wird hart ausgeschlossen.">
        <ChipFreeInput
          values={filter.dislikeTags}
          onChange={(v) => update({ dislikeTags: v })}
          placeholder="z.B. Drama, Reaction…"
        />
      </Section>

      <Section label="Stimmung" hint="Mehrfachauswahl.">
        <ChipMulti
          options={MOOD_OPTIONS as unknown as string[]}
          values={filter.moodTags}
          onChange={(v) => update({ moodTags: v })}
        />
      </Section>

      <Section label="Stil" hint="Wie tief soll's gehen.">
        <ChipMulti
          options={DEPTH_OPTIONS as unknown as string[]}
          values={filter.depthTags}
          onChange={(v) => update({ depthTags: v })}
        />
      </Section>

      <Section label="Sprachen">
        <ChipMulti
          options={LANGUAGE_OPTIONS.map((l) => l.code)}
          renderLabel={(code) =>
            LANGUAGE_OPTIONS.find((l) => l.code === code)?.label ?? code
          }
          values={filter.languages}
          onChange={(v) => update({ languages: v })}
        />
      </Section>

      <Section label="Dauer" hint={`${Math.round(filter.minDurationSec / 60)}–${Math.round(filter.maxDurationSec / 60)} Minuten`}>
        <div className="flex items-center gap-3 text-[11px]">
          <span className="text-mute w-16 shrink-0">Min</span>
          <input
            type="range"
            min={30}
            max={1800}
            step={30}
            value={filter.minDurationSec}
            onChange={(e) => update({ minDurationSec: Number(e.target.value) })}
            className="flex-1 accent-[var(--color-accent)]"
          />
          <span className="font-mono tabular-nums w-12 text-right text-mute">
            {Math.round(filter.minDurationSec / 60)}m
          </span>
        </div>
        <div className="flex items-center gap-3 text-[11px] mt-2">
          <span className="text-mute w-16 shrink-0">Max</span>
          <input
            type="range"
            min={300}
            max={3600}
            step={60}
            value={filter.maxDurationSec}
            onChange={(e) => update({ maxDurationSec: Number(e.target.value) })}
            className="flex-1 accent-[var(--color-accent)]"
          />
          <span className="font-mono tabular-nums w-12 text-right text-mute">
            {Math.round(filter.maxDurationSec / 60)}m
          </span>
        </div>
      </Section>

      <Section label="Mindest-Score" hint="Videos unterhalb tauchen nicht im Feed auf.">
        <div className="flex items-center gap-3 text-[11px]">
          <input
            type="range"
            min={0}
            max={100}
            step={5}
            value={filter.scoreThreshold}
            onChange={(e) => update({ scoreThreshold: Number(e.target.value) })}
            className="flex-1 accent-[var(--color-accent)]"
          />
          <span className="font-mono tabular-nums w-12 text-right text-accent">
            {filter.scoreThreshold}
          </span>
        </div>
      </Section>

      <Section label="Beispiele" hint="Optional. Klartext, gerne mit Titeln oder URLs.">
        <textarea
          value={filter.examples}
          onChange={(e) => update({ examples: e.target.value })}
          rows={4}
          placeholder={`z.B. „But what is a Neural Network?" von 3Blue1Brown — strukturiert, mathematisch, kein Hype.`}
          className="w-full bg-surface border-hairline rounded-md p-3 text-[12px] placeholder:text-faint resize-y leading-relaxed"
        />
      </Section>

      <div className="px-5 py-6">
        <button
          onClick={reset}
          className="flex items-center gap-2 text-[11px] text-faint hover:text-mute transition-colors"
        >
          <RotateCcw size={12} strokeWidth={1.5} />
          Auf Standard zurücksetzen
        </button>
      </div>
    </div>
  )
}

// ─── PROMPT TAB ──────────────────────────────────────────────────────────────

function PromptTab() {
  const filter = useHikariStore((s) => s.filter)
  const promptOverride = useHikariStore((s) => s.promptOverride)
  const setPromptOverride = useHikariStore((s) => s.setPromptOverride)

  const generated = useMemo(() => buildPrompt(filter), [filter])
  const visible = promptOverride ?? generated

  const [editing, setEditing] = useState(false)
  const [draft, setDraft] = useState(visible)
  const [copied, setCopied] = useState(false)

  function startEdit() {
    setDraft(visible)
    setEditing(true)
  }

  function saveEdit() {
    setPromptOverride(draft === generated ? null : draft)
    setEditing(false)
  }

  function discardEdit() {
    setEditing(false)
  }

  function copy() {
    navigator.clipboard.writeText(visible)
    setCopied(true)
    setTimeout(() => setCopied(false), 1500)
  }

  return (
    <div>
      {/* Status banner */}
      <div className="px-5 py-3 border-b-hairline flex items-center justify-between text-[11px]">
        <span className="text-faint">
          {promptOverride ? (
            <span className="text-accent">Manueller Override aktiv</span>
          ) : (
            'Live aus dem Filter generiert'
          )}
        </span>
        <div className="flex items-center gap-3">
          {promptOverride && !editing && (
            <button
              onClick={() => setPromptOverride(null)}
              className="text-faint hover:text-mute"
            >
              Auto wiederherstellen
            </button>
          )}
          {!editing ? (
            <>
              <button onClick={copy} className="flex items-center gap-1.5 text-faint hover:text-mute">
                {copied ? <Check size={12} className="text-accent" /> : <Copy size={12} strokeWidth={1.5} />}
                {copied ? 'Kopiert' : 'Kopieren'}
              </button>
              <button onClick={startEdit} className="flex items-center gap-1.5 text-faint hover:text-mute">
                <Edit3 size={12} strokeWidth={1.5} />
                Bearbeiten
              </button>
            </>
          ) : (
            <>
              <button onClick={discardEdit} className="flex items-center gap-1.5 text-faint hover:text-mute">
                <X size={12} strokeWidth={1.5} />
                Verwerfen
              </button>
              <button onClick={saveEdit} className="flex items-center gap-1.5 text-accent">
                <Check size={12} strokeWidth={1.5} />
                Speichern
              </button>
            </>
          )}
        </div>
      </div>

      <div className="px-5 py-4">
        {editing ? (
          <textarea
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            rows={28}
            className="w-full bg-surface border-hairline rounded-md p-4 text-[12px] leading-relaxed font-mono resize-y"
          />
        ) : (
          <pre className="text-[12px] leading-relaxed font-mono whitespace-pre-wrap break-words text-white/80">
            {visible}
          </pre>
        )}
      </div>
    </div>
  )
}

// ─── SYSTEM TAB ──────────────────────────────────────────────────────────────

function SystemTab() {
  const backendUrl = useHikariStore((s) => s.backendUrl)
  const setBackendUrl = useHikariStore((s) => s.setBackendUrl)
  const dailyBudget = useHikariStore((s) => s.dailyBudget)
  const setDailyBudget = useHikariStore((s) => s.setDailyBudget)
  const llmProvider = useHikariStore((s) => s.llmProvider)
  const setLlmProvider = useHikariStore((s) => s.setLlmProvider)
  const sbBehaviors = useHikariStore((s) => s.sbBehaviors)
  const setSbBehavior = useHikariStore((s) => s.setSbBehavior)

  const providers = [
    { key: 'lmstudio', label: 'LM Studio', detail: 'Lokal · Qwen 3.6 27B' },
    { key: 'ollama', label: 'Ollama', detail: 'Lokal · konfigurierbar' },
    { key: 'claude', label: 'Anthropic', detail: 'Cloud · Sonnet 4.6' },
  ]

  return (
    <div>
      <Section label="Backend">
        <input
          value={backendUrl}
          onChange={(e) => setBackendUrl(e.target.value)}
          spellCheck={false}
          className="w-full bg-surface border-hairline rounded-md px-3 py-2.5 text-[12px] font-mono"
        />
      </Section>

      <Section label="LLM-Provider">
        <div className="space-y-1">
          {providers.map((p) => {
            const active = llmProvider === p.key
            return (
              <button
                key={p.key}
                onClick={() => setLlmProvider(p.key)}
                className={`w-full flex items-center justify-between text-left px-3 py-2.5 rounded-md transition-colors ${
                  active ? 'bg-accent-soft border border-[var(--color-accent-border)]' : 'bg-surface border-hairline'
                }`}
              >
                <div>
                  <div className="text-[13px]">{p.label}</div>
                  <div className="text-faint text-[11px]">{p.detail}</div>
                </div>
                {active && <Check size={14} className="text-accent" strokeWidth={2} />}
              </button>
            )
          })}
        </div>
      </Section>

      <Section label="Tagesbudget" hint={`Bis zu ${dailyBudget} Videos werden pro Tag gescort.`}>
        <div className="flex items-center gap-3 text-[11px]">
          <input
            type="range"
            min={5}
            max={50}
            step={1}
            value={dailyBudget}
            onChange={(e) => setDailyBudget(Number(e.target.value))}
            className="flex-1 accent-[var(--color-accent)]"
          />
          <span className="font-mono tabular-nums w-10 text-right text-accent">{dailyBudget}</span>
        </div>
      </Section>

      <Section label="SponsorBlock" hint="A = Auto-Skip · M = Manuell · I = Ignorieren">
        <ul>
          {sponsorBlockCategories.map((cat) => {
            const current = sbBehaviors[cat.key] ?? cat.defaultBehavior
            return (
              <li key={cat.key} className="flex items-center justify-between py-2.5 border-b-hairline last:border-0">
                <div className="min-w-0 pr-3">
                  <div className="text-[13px]">{cat.labelDe}</div>
                  <div className="text-faint text-[11px] truncate">{cat.description}</div>
                </div>
                <div className="flex bg-surface border-hairline rounded-md overflow-hidden text-[10px] font-mono shrink-0">
                  {(['auto', 'manual', 'ignore'] as const).map((b) => {
                    const active = current === b
                    const letter = b === 'auto' ? 'A' : b === 'manual' ? 'M' : 'I'
                    return (
                      <button
                        key={b}
                        onClick={() => setSbBehavior(cat.key, b)}
                        className={`w-7 h-7 flex items-center justify-center transition-colors ${
                          active ? 'bg-accent text-black' : 'text-mute'
                        }`}
                      >
                        {letter}
                      </button>
                    )
                  })}
                </div>
              </li>
            )
          })}
        </ul>
      </Section>

      <Section label="Manga">
        <MangaSyncButton />
      </Section>

      <div className="h-8" />
    </div>
  )
}
