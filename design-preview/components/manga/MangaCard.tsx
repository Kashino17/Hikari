import Link from 'next/link'
import { type ApiSeries } from '@/lib/manga-api'

export function MangaCard({ series, progress }: { series: ApiSeries; progress?: number }) {
  return (
    <Link
      href={`/manga/${encodeURIComponent(series.id)}`}
      className="flex-none w-32 group cursor-pointer"
    >
      <div className="relative aspect-[2/3] rounded-md overflow-hidden border-hairline bg-surface">
        <div className="absolute inset-0 bg-gradient-to-br from-amber-900/40 via-zinc-800 to-zinc-900 flex items-end p-2">
          <span className="text-[11px] font-bold text-white/90 leading-tight">{series.title}</span>
        </div>
        {progress !== undefined && (
          <div className="absolute bottom-0 left-0 right-0 h-[2px] bg-white/20">
            <div className="h-full bg-accent" style={{ width: `${progress * 100}%` }} />
          </div>
        )}
      </div>
      <div className="mt-2 text-[11px] font-medium text-white/90 line-clamp-2">{series.title}</div>
    </Link>
  )
}
