import Link from 'next/link'
import type { ApiChapter } from '@/lib/manga-api'

export function ChapterRow({
  seriesId,
  chapter,
}: {
  seriesId: string
  chapter: ApiChapter
}) {
  return (
    <Link
      href={`/manga/${encodeURIComponent(seriesId)}/${encodeURIComponent(chapter.id)}`}
      className="flex items-center justify-between py-3 border-b border-white/5 hover:bg-white/[0.02]"
    >
      <div className="flex items-center gap-3 px-5">
        <span className="font-mono text-[11px] text-faint w-12">CH {chapter.number}</span>
        <span className="text-[13px] text-white/90 line-clamp-1">{chapter.title ?? ''}</span>
      </div>
      {chapter.isRead === 1 && (
        <span className="text-accent text-[10px] uppercase tracking-widest pr-5">Read</span>
      )}
    </Link>
  )
}
