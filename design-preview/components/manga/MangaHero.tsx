import Link from 'next/link'
import { Play } from 'lucide-react'
import type { ApiSeries, ApiContinue } from '@/lib/manga-api'

export function MangaHero({
  series,
  cont,
}: {
  series: ApiSeries
  cont?: ApiContinue
}) {
  const ctaHref = cont
    ? `/manga/${encodeURIComponent(cont.seriesId)}/${encodeURIComponent(cont.chapterId)}?page=${cont.pageNumber}`
    : `/manga/${encodeURIComponent(series.id)}`
  const ctaLabel = cont ? 'Weiterlesen' : 'Lesen'

  return (
    <div className="relative aspect-[16/12] sm:aspect-[16/9] overflow-hidden">
      <div className="absolute inset-0 bg-gradient-to-br from-amber-900/30 via-zinc-900 to-black" />
      <div className="absolute inset-0 bg-gradient-to-t from-black via-black/40 to-transparent" />
      <div className="absolute bottom-0 left-0 right-0 p-5">
        <div className="text-[10px] uppercase tracking-widest text-faint mb-2">Manga</div>
        <h1 className="text-3xl font-extrabold text-white">{series.title}</h1>
        {series.author && (
          <div className="text-[11px] text-faint uppercase tracking-wide mt-1">{series.author}</div>
        )}
        <div className="mt-4 flex gap-2">
          <Link
            href={ctaHref}
            className="inline-flex items-center gap-2 bg-accent text-black font-bold rounded px-4 py-2 text-sm"
          >
            <Play size={14} fill="currentColor" /> {ctaLabel}
          </Link>
        </div>
      </div>
    </div>
  )
}
