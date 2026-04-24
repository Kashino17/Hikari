'use client'

import { useHikariStore } from '@/lib/store'
import { mockVideos, allChannels, sponsorBlockCategories, sponsorBlockStats } from '@/lib/mock-data'
import { Clock, BarChart2 } from 'lucide-react'

const WEEK_DAYS = ['Mo', 'Di', 'Mi', 'Do', 'Fr', 'Sa', 'So']
const TIME_SLOTS = ['Morgen', 'Mittag', 'Abend']

// Deterministic heatmap from day+slot
function heatVal(day: number, slot: number) {
  const vals = [
    [1, 2, 4],
    [0, 3, 5],
    [2, 1, 6],
    [3, 4, 3],
    [1, 5, 4],
    [4, 3, 2],
    [2, 1, 1],
  ]
  return vals[day][slot]
}

const CATEGORY_STATS = [
  { label: 'Mathematik', count: 38, color: '#06B6D4' },
  { label: 'Wissenschaft', count: 31, color: '#10B981' },
  { label: 'Gesellschaft', count: 18, color: '#EF4444' },
  { label: 'Philosophie', count: 12, color: '#F59E0B' },
  { label: 'Technik', count: 9, color: '#8B5CF6' },
  { label: 'Geschichte', count: 7, color: '#F97316' },
]

const CHANNEL_STATS = [
  { name: '3Blue1Brown', approved: 138, total: 142 },
  { name: 'Khan Academy', approved: 1198, total: 1204 },
  { name: 'CGP Grey', approved: 86, total: 89 },
  { name: 'Kurzgesagt', approved: 182, total: 198 },
  { name: 'Vsauce', approved: 201, total: 234 },
  { name: 'SpongeLore', approved: 51, total: 67 },
]

const REJECTION_TAGS = [
  { label: 'clickbait', count: 14 },
  { label: 'manipulation', count: 11 },
  { label: 'kein Lernwert', count: 9 },
  { label: 'Padding', count: 8 },
  { label: 'Fehlinformation', count: 7 },
  { label: 'emotionale Überwältigung', count: 6 },
  { label: 'Verschwörung', count: 4 },
  { label: 'unrealistische Versprechen', count: 4 },
  { label: 'Reaktionsformat', count: 3 },
  { label: 'Filler', count: 3 },
  { label: 'keine Quellen', count: 2 },
  { label: 'Werbung', count: 2 },
]

export default function DashboardStats() {
  const { theme } = useHikariStore()
  const dark = theme === 'dark'

  const textPrimary = dark ? '#fff' : '#0A0A0A'
  const textMuted = dark ? 'rgba(255,255,255,0.4)' : 'rgba(0,0,0,0.45)'
  const cardBg = dark ? 'rgba(255,255,255,0.04)' : 'rgba(0,0,0,0.025)'
  const cardBorder = dark ? 'rgba(255,255,255,0.08)' : '#e5e5e5'
  const violet = '#8B5CF6'

  const maxCat = Math.max(...CATEGORY_STATS.map((c) => c.count))

  return (
    <div className="p-3 pb-8" style={{ fontFamily: 'var(--font-geist-sans)' }}>

      {/* Hero row */}
      <div className="grid grid-cols-3 gap-2 mb-3">
        <div
          className="col-span-3 rounded-md p-3"
          style={{ background: 'rgba(139,92,246,0.08)', border: '1px solid rgba(139,92,246,0.2)' }}
        >
          <div className="text-[8px] font-mono uppercase tracking-widest mb-2" style={{ color: 'rgba(139,92,246,0.6)' }}>
            Diese Woche
          </div>
          <div className="flex items-end gap-4">
            <div>
              <div className="text-4xl font-mono leading-none" style={{ color: violet }}>23</div>
              <div className="text-[9px] font-mono mt-1" style={{ color: textMuted }}>approved</div>
            </div>
            <div>
              <div className="text-2xl font-mono leading-none" style={{ color: '#EF4444' }}>9</div>
              <div className="text-[9px] font-mono mt-1" style={{ color: textMuted }}>rejected</div>
            </div>
            <div>
              <div className="text-2xl font-mono leading-none" style={{ color: textPrimary }}>78.3</div>
              <div className="text-[9px] font-mono mt-1" style={{ color: textMuted }}>avg score</div>
            </div>
          </div>
        </div>
      </div>

      {/* Category breakdown */}
      <div className="text-[8px] font-mono uppercase tracking-widest mb-2 px-1" style={{ color: textMuted }}>
        Kategorien
      </div>
      <div
        className="rounded-md p-3 mb-3"
        style={{ background: cardBg, border: `1px solid ${cardBorder}` }}
      >
        {CATEGORY_STATS.map((cat) => (
          <div key={cat.label} className="flex items-center gap-2 mb-2 last:mb-0">
            <span className="text-[9px] font-mono w-20 flex-shrink-0 truncate" style={{ color: textPrimary }}>{cat.label}</span>
            <div className="flex-1 h-2 rounded-sm overflow-hidden" style={{ background: dark ? 'rgba(255,255,255,0.06)' : '#f0f0f0' }}>
              <div
                className="h-full rounded-sm transition-all"
                style={{ width: `${(cat.count / maxCat) * 100}%`, background: cat.color }}
              />
            </div>
            <span className="text-[9px] font-mono w-6 text-right flex-shrink-0" style={{ color: cat.color }}>{cat.count}</span>
          </div>
        ))}
      </div>

      {/* Channel approval table */}
      <div className="text-[8px] font-mono uppercase tracking-widest mb-2 px-1" style={{ color: textMuted }}>
        Kanal-Freigaberate
      </div>
      <div
        className="rounded-md overflow-hidden mb-3"
        style={{ border: `1px solid ${cardBorder}` }}
      >
        {CHANNEL_STATS.map((ch, i) => {
          const rate = Math.round((ch.approved / ch.total) * 100)
          return (
            <div
              key={ch.name}
              className="flex items-center gap-3 px-3 py-2"
              style={{ borderBottom: i < CHANNEL_STATS.length - 1 ? `1px solid ${cardBorder}` : 'none' }}
            >
              <span className="text-[10px] font-medium flex-1 truncate" style={{ color: textPrimary }}>{ch.name}</span>
              <div className="w-20 h-1.5 rounded-sm overflow-hidden flex-shrink-0" style={{ background: dark ? 'rgba(255,255,255,0.06)' : '#f0f0f0' }}>
                <div className="h-full rounded-sm" style={{ width: `${rate}%`, background: rate >= 90 ? '#10B981' : rate >= 75 ? '#F59E0B' : '#EF4444' }} />
              </div>
              <span
                className="text-[9px] font-mono w-8 text-right flex-shrink-0"
                style={{ color: rate >= 90 ? '#10B981' : rate >= 75 ? '#F59E0B' : '#EF4444' }}
              >
                {rate}%
              </span>
            </div>
          )
        })}
      </div>

      {/* Daily heatmap */}
      <div className="text-[8px] font-mono uppercase tracking-widest mb-2 px-1" style={{ color: textMuted }}>
        Aktivitäts-Heatmap
      </div>
      <div
        className="rounded-md p-3 mb-3"
        style={{ background: cardBg, border: `1px solid ${cardBorder}` }}
      >
        <div className="grid gap-1" style={{ gridTemplateColumns: '40px repeat(7, 1fr)' }}>
          <div />
          {WEEK_DAYS.map((d) => (
            <div key={d} className="text-center text-[7px] font-mono pb-1" style={{ color: textMuted }}>{d}</div>
          ))}
          {TIME_SLOTS.map((slot, si) => (
            <>
              <div key={slot} className="text-[7px] font-mono flex items-center" style={{ color: textMuted }}>{slot}</div>
              {WEEK_DAYS.map((_, di) => {
                const val = heatVal(di, si)
                return (
                  <div
                    key={`${di}-${si}`}
                    className="rounded-sm aspect-square"
                    style={{ background: `rgba(139,92,246,${val / 7})` }}
                  />
                )
              })}
            </>
          ))}
        </div>
      </div>

      {/* SponsorBlock widget */}
      <div className="text-[8px] font-mono uppercase tracking-widest mb-2 px-1" style={{ color: textMuted }}>
        SponsorBlock
      </div>
      <div
        className="rounded-md p-3 mb-3 flex items-center gap-3"
        style={{ background: 'rgba(139,92,246,0.07)', border: '1px solid rgba(139,92,246,0.18)' }}
      >
        <Clock size={18} color={violet} strokeWidth={1.5} />
        <div>
          <div className="font-mono text-[20px] leading-none" style={{ color: violet }}>{sponsorBlockStats.timeSaved}</div>
          <div className="text-[9px] font-mono mt-1" style={{ color: textMuted }}>
            {sponsorBlockStats.totalSkipped} Segmente übersprungen
          </div>
        </div>
        <div className="ml-auto text-right">
          <div className="text-[9px] font-mono" style={{ color: textMuted }}>Spitzenreiter</div>
          <div className="text-[11px] font-mono" style={{ color: '#00D400' }}>Sponsor</div>
        </div>
      </div>

      {/* Rejection tags */}
      <div className="text-[8px] font-mono uppercase tracking-widest mb-2 px-1" style={{ color: textMuted }}>
        Ablehnungsgründe
      </div>
      <div
        className="rounded-md p-3"
        style={{ background: cardBg, border: `1px solid ${cardBorder}` }}
      >
        <div className="flex flex-wrap gap-1.5">
          {REJECTION_TAGS.map((tag) => (
            <span
              key={tag.label}
              className="px-2 py-1 rounded-md text-[9px] font-mono"
              style={{
                background: `rgba(239,68,68,${0.06 + (tag.count / 14) * 0.12})`,
                border: `1px solid rgba(239,68,68,${0.15 + (tag.count / 14) * 0.15})`,
                color: `rgba(239,68,68,${0.6 + (tag.count / 14) * 0.4})`,
                fontSize: `${8 + (tag.count / 14) * 3}px`,
              }}
            >
              {tag.label} <span style={{ opacity: 0.6 }}>{tag.count}</span>
            </span>
          ))}
        </div>
      </div>

    </div>
  )
}
