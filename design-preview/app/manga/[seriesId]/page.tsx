import { notFound } from 'next/navigation'
import Link from 'next/link'
import { Play } from 'lucide-react'
import { mangaApi } from '@/lib/manga-api'
import { ArcAccordion } from '@/components/manga/ArcAccordion'

interface Props {
  params: Promise<{ seriesId: string }>
}

export default async function MangaSeriesPage({ params }: Props) {
  const { seriesId } = await params
  const decoded = decodeURIComponent(seriesId)
  const detail = await mangaApi.getSeries(decoded).catch(() => null)
  if (!detail) notFound()

  const cont = (await mangaApi.getContinue().catch(() => [])).find((c) => c.seriesId === decoded)
  const ctaChapter = cont?.chapterId ?? detail.chapters[0]?.id
  const ctaLabel = cont ? 'Weiterlesen' : 'Lesen'

  return (
    <main className="pb-16">
      <div className="relative aspect-[16/12] sm:aspect-[16/9] overflow-hidden">
        <div className="absolute inset-0 bg-gradient-to-br from-amber-900/30 via-zinc-900 to-black" />
        <div className="absolute inset-0 bg-gradient-to-t from-black via-black/40 to-transparent" />
        <Link href="/manga" className="absolute top-4 left-4 text-accent text-sm">←</Link>
        <div className="absolute bottom-0 left-0 right-0 p-5">
          <div className="text-[10px] uppercase tracking-widest text-faint mb-2">Manga</div>
          <h1 className="text-3xl font-extrabold text-white">{detail.title}</h1>
          {detail.author && (
            <div className="text-[11px] text-faint uppercase tracking-wide mt-1">{detail.author}</div>
          )}
          <div className="mt-3 flex gap-2">
            {ctaChapter && (
              <Link
                href={`/manga/${encodeURIComponent(detail.id)}/${encodeURIComponent(ctaChapter)}${cont ? `?page=${cont.pageNumber}` : ''}`}
                className="inline-flex items-center gap-2 bg-accent text-black font-bold rounded px-4 py-2 text-sm"
              >
                <Play size={14} fill="currentColor" /> {ctaLabel}
              </Link>
            )}
          </div>
        </div>
      </div>

      <ArcAccordion
        seriesId={detail.id}
        arcs={detail.arcs}
        chapters={detail.chapters}
        initialExpanded={
          cont
            ? detail.chapters.find((c) => c.id === cont.chapterId)?.arcId
            : undefined
        }
      />
    </main>
  )
}
