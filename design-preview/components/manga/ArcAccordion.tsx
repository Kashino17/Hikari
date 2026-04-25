'use client'
import { useState } from 'react'
import { ChevronDown, ChevronRight } from 'lucide-react'
import type { ApiArc, ApiChapter } from '@/lib/manga-api'
import { ChapterRow } from './ChapterRow'

export function ArcAccordion({
  seriesId,
  arcs,
  chapters,
  initialExpanded,
}: {
  seriesId: string
  arcs: ApiArc[]
  chapters: ApiChapter[]
  initialExpanded?: string
}) {
  const [expanded, setExpanded] = useState<Set<string>>(
    initialExpanded ? new Set([initialExpanded]) : new Set(arcs.length > 0 ? [arcs[0].id] : []),
  )
  const toggle = (id: string) =>
    setExpanded((prev) => {
      const next = new Set(prev)
      if (next.has(id)) next.delete(id)
      else next.add(id)
      return next
    })

  // Group chapters by arc id; chapters without arc go into a synthetic group.
  const byArc = new Map<string, ApiChapter[]>()
  for (const arc of arcs) byArc.set(arc.id, [])
  const orphanArcId = '__no_arc__'
  byArc.set(orphanArcId, [])
  for (const ch of chapters) {
    const key = ch.arcId ?? orphanArcId
    if (!byArc.has(key)) byArc.set(key, [])
    byArc.get(key)!.push(ch)
  }

  if (arcs.length === 0) {
    return (
      <div>
        {chapters.map((ch) => (
          <ChapterRow key={ch.id} seriesId={seriesId} chapter={ch} />
        ))}
      </div>
    )
  }

  return (
    <div>
      {arcs.map((arc) => {
        const chs = byArc.get(arc.id) ?? []
        const open = expanded.has(arc.id)
        return (
          <div key={arc.id} className="border-b border-white/5">
            <button
              onClick={() => toggle(arc.id)}
              className="w-full flex items-center justify-between px-5 py-3 text-left"
            >
              <div className="flex items-center gap-2">
                {open ? (
                  <ChevronDown size={14} className="text-faint" />
                ) : (
                  <ChevronRight size={14} className="text-faint" />
                )}
                <span className="text-[13px] font-bold text-white">{arc.title}</span>
              </div>
              <span className="text-faint text-[10px] font-mono">
                CH {arc.chapterStart}–{arc.chapterEnd}
              </span>
            </button>
            {open && (
              <div>
                {chs.map((ch) => (
                  <ChapterRow key={ch.id} seriesId={seriesId} chapter={ch} />
                ))}
              </div>
            )}
          </div>
        )
      })}
      {(byArc.get(orphanArcId) ?? []).length > 0 && (
        <div className="px-5 pt-4">
          <div className="text-[10px] uppercase tracking-widest text-faint mb-2">Sonstige</div>
          {(byArc.get(orphanArcId) ?? []).map((ch) => (
            <ChapterRow key={ch.id} seriesId={seriesId} chapter={ch} />
          ))}
        </div>
      )}
    </div>
  )
}
