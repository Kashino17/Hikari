'use client'

import Link from 'next/link'
import { useEffect, useRef, useState } from 'react'
import { ChevronLeft, RotateCcw, Scale } from 'lucide-react'
import { useHikariStore } from '@/lib/store'
import {
  ALL_CATEGORIES,
  CATEGORY_LABEL_DE,
  ratioLabel,
  thresholdLabel,
  summarizeDiscoverySettings,
  normalizeScoreWeights,
  SCORE_AXIS_LABEL_DE,
  SCORE_AXIS_HINT_DE,
  type ScoreAxis,
} from '@/lib/discovery-tuning'
import { CategorySlider } from '@/components/CategorySlider'

export default function DiscoveryTuningPage() {
  const settings = useHikariStore((s) => s.discoverySettings)
  const update = useHikariStore((s) => s.updateDiscoverySettings)
  const setCategoryWeight = useHikariStore((s) => s.setCategoryWeight)
  const setScoreWeight = useHikariStore((s) => s.setScoreWeight)
  const reset = useHikariStore((s) => s.resetDiscoverySettings)

  const scoreAxes: ScoreAxis[] = ['category', 'similarity', 'quality', 'longForm']
  const sw = settings.score_weights
  const swSum = sw.category + sw.similarity + sw.quality + sw.longForm
  const swSumPct = Math.round(swSum * 100)
  const swValid = Math.abs(swSum - 1) < 0.001

  const [pulse, setPulse] = useState(false)
  const pulseTimer = useRef<ReturnType<typeof setTimeout> | null>(null)
  const isFirst = useRef(true)

  useEffect(() => {
    if (isFirst.current) {
      isFirst.current = false
      return
    }
    setPulse(true)
    if (pulseTimer.current) clearTimeout(pulseTimer.current)
    pulseTimer.current = setTimeout(() => setPulse(false), 800)
    return () => {
      if (pulseTimer.current) clearTimeout(pulseTimer.current)
    }
  }, [settings])

  const followedShare = 100 - settings.discovery_ratio

  return (
    <div className="min-h-svh">
      <header
        className="sticky top-0 z-20 bg-[var(--color-bg)]/95 backdrop-blur border-b-hairline"
        style={{ paddingTop: 'env(safe-area-inset-top, 0)' }}
      >
        <div className="px-5 pt-4 pb-3 flex items-center justify-between">
          <Link
            href="/tuning"
            className="flex items-center gap-1 text-faint text-[11px] hover:text-mute"
          >
            <ChevronLeft size={14} strokeWidth={1.5} />
            Tuning
          </Link>
          <div
            className={`flex items-center gap-1.5 text-[10px] uppercase tracking-[0.18em] transition-opacity ${
              pulse ? 'opacity-100' : 'opacity-0'
            }`}
          >
            <span className="h-1.5 w-1.5 rounded-full bg-accent animate-pulse" />
            <span className="text-accent">Live aktualisiert</span>
          </div>
        </div>
        <div className="px-5 pb-4">
          <h1 className="text-base font-medium tracking-tight">Discovery-Tuning</h1>
          <p className="text-faint text-[11px] mt-0.5">
            Wie der Feed zwischen Gefolgten und Neuem balanciert.
          </p>
        </div>
      </header>

      {/* ── Discovery Ratio ──────────────────────────────────────────── */}
      <section className="px-5 py-5 border-b-hairline">
        <div className="text-faint text-[10px] font-mono uppercase tracking-widest mb-1">
          Discovery Ratio
        </div>
        <div className="text-faint text-[11px] mb-4">
          {ratioLabel(settings.discovery_ratio)} —{' '}
          <span className="text-mute">
            {followedShare}% Gefolgte · {settings.discovery_ratio}% Neues
          </span>
        </div>

        <input
          type="range"
          min={0}
          max={100}
          step={5}
          value={settings.discovery_ratio}
          onChange={(e) => update({ discovery_ratio: Number(e.target.value) })}
          className="w-full accent-[var(--color-accent)]"
        />

        {/* Live-Mix-Bar als visuelles Feedback */}
        <div className="mt-3 flex h-2 rounded-full overflow-hidden bg-surface border-hairline">
          <div
            className="bg-white/25 transition-all duration-200"
            style={{ width: `${followedShare}%` }}
          />
          <div
            className="bg-accent transition-all duration-200"
            style={{ width: `${settings.discovery_ratio}%` }}
          />
        </div>
        <div className="flex justify-between mt-1.5 text-faint text-[10px] font-mono uppercase tracking-wider">
          <span>Gefolgte</span>
          <span>Neu</span>
        </div>
      </section>

      {/* ── Category Tuning ──────────────────────────────────────────── */}
      <section className="px-5 py-5 border-b-hairline">
        <div className="text-faint text-[10px] font-mono uppercase tracking-widest mb-1">
          Category Tuning
        </div>
        <div className="text-faint text-[11px] mb-3">
          Pro Kategorie verstärken (+) oder dämpfen (−). Mitte = neutral.
        </div>
        <div>
          {ALL_CATEGORIES.map((cat) => (
            <CategorySlider
              key={cat}
              label={CATEGORY_LABEL_DE[cat]}
              value={settings.category_weights[cat] ?? 0}
              onChange={(v) => setCategoryWeight(cat, v)}
            />
          ))}
        </div>
      </section>

      {/* ── Score-Weighting (4 axes) ─────────────────────────────────── */}
      <section className="px-5 py-5 border-b-hairline">
        <div className="text-faint text-[10px] font-mono uppercase tracking-widest mb-1">
          Score-Weighting
        </div>
        <div className="text-faint text-[11px] mb-4">
          Wie der Engine die vier Achsen mischt. Werte werden vor dem Senden auf
          Summe 1.0 normalisiert.
        </div>

        <div className="flex flex-col gap-4">
          {scoreAxes.map((axis) => {
            const value = sw[axis]
            return (
              <div key={axis}>
                <div className="flex items-baseline justify-between mb-1">
                  <span className="text-[12px] font-medium">
                    {SCORE_AXIS_LABEL_DE[axis]}
                  </span>
                  <span className="font-mono text-[11px] text-accent tabular-nums">
                    {Math.round(value * 100)}
                  </span>
                </div>
                <input
                  type="range"
                  min={0}
                  max={100}
                  step={5}
                  value={Math.round(value * 100)}
                  onChange={(e) => setScoreWeight(axis, Number(e.target.value) / 100)}
                  className="w-full accent-[var(--color-accent)]"
                />
                <div className="text-faint text-[10px] mt-0.5">
                  {SCORE_AXIS_HINT_DE[axis]}
                </div>
              </div>
            )
          })}
        </div>

        {/* Live sum + normalize CTA — backend rejects sum != 1.0 */}
        <div className="flex items-center justify-between mt-5 pt-3 border-t-hairline">
          <div className="flex items-center gap-2 text-[11px]">
            <Scale size={12} strokeWidth={1.5} className="text-faint" />
            <span className="text-faint">Summe</span>
            <span
              className={`font-mono tabular-nums ${
                swValid ? 'text-accent' : 'text-[var(--color-danger)]'
              }`}
            >
              {swSumPct}%
            </span>
          </div>
          <button
            onClick={() => update({ score_weights: normalizeScoreWeights(sw) })}
            disabled={swValid}
            className="text-[11px] text-mute hover:text-white disabled:opacity-30 disabled:cursor-default transition-colors"
          >
            Auf 100% normalisieren
          </button>
        </div>
      </section>

      {/* ── Quality Threshold ────────────────────────────────────────── */}
      <section className="px-5 py-5 border-b-hairline">
        <div className="text-faint text-[10px] font-mono uppercase tracking-widest mb-1">
          Quality Threshold
        </div>
        <div className="text-faint text-[11px] mb-3">
          Mindest-Qualität für Discovery-Vorschläge —{' '}
          <span className="text-mute">{thresholdLabel(settings.quality_threshold)}</span>
        </div>
        <div className="flex items-center gap-3 text-[11px]">
          <input
            type="range"
            min={0}
            max={100}
            step={5}
            value={settings.quality_threshold}
            onChange={(e) =>
              update({ quality_threshold: Number(e.target.value) })
            }
            className="flex-1 accent-[var(--color-accent)]"
          />
          <span className="font-mono tabular-nums w-12 text-right text-accent">
            {settings.quality_threshold}
          </span>
        </div>
      </section>

      {/* ── Summary ──────────────────────────────────────────────────── */}
      <section className="px-5 py-5 border-b-hairline">
        <div className="text-faint text-[10px] font-mono uppercase tracking-widest mb-2">
          Zusammenfassung
        </div>
        <p className="text-[12px] text-mute leading-relaxed">
          {summarizeDiscoverySettings(settings)}
        </p>
      </section>

      <div className="px-5 py-6">
        <button
          onClick={reset}
          className="flex items-center gap-2 text-[11px] text-faint hover:text-mute transition-colors"
        >
          <RotateCcw size={12} strokeWidth={1.5} />
          Auf Standard zurücksetzen
        </button>
      </div>

      <div className="h-8" />
    </div>
  )
}
