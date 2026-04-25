import { notFound } from 'next/navigation'
import { mangaApi } from '@/lib/manga-api'
import { MangaReader } from '@/components/manga/MangaReader'

interface Props {
  params: Promise<{ seriesId: string; chapterId: string }>
  searchParams: Promise<{ page?: string }>
}

export default async function MangaReaderPage({ params, searchParams }: Props) {
  const { seriesId, chapterId } = await params
  const sp = await searchParams
  const decodedSeries = decodeURIComponent(seriesId)
  const decodedChapter = decodeURIComponent(chapterId)

  const [pages, detail] = await Promise.all([
    mangaApi.getChapterPages(decodedChapter).catch(() => []),
    mangaApi.getSeries(decodedSeries).catch(() => null),
  ])

  if (!detail) notFound()

  // Verify the chapter actually exists in the series (and isn't just a typo'd
  // URL). If not in chapters list, 404. If it exists but pages are empty, the
  // reader will render a "syncing" state and trigger a chapter-only sync.
  const chapterKnown = detail.chapters.some((c) => c.id === decodedChapter)
  if (!chapterKnown) notFound()

  const sorted = [...detail.chapters].sort((a, b) => a.number - b.number)
  const idx = sorted.findIndex((c) => c.id === decodedChapter)
  const nextChapter = idx >= 0 ? sorted[idx + 1] : undefined

  const initialPage = sp.page ? Number(sp.page) : 1

  return (
    <MangaReader
      seriesId={decodedSeries}
      chapterId={decodedChapter}
      pages={pages}
      initialPage={Number.isFinite(initialPage) ? initialPage : 1}
      nextChapterId={nextChapter?.id}
    />
  )
}
