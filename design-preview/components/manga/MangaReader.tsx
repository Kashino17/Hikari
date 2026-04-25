'use client'
import { useEffect, useMemo, useRef, useState } from 'react'
import Link from 'next/link'
import { mangaApi, MANGA_API_BASE, type ApiPage } from '@/lib/manga-api'

interface Props {
  seriesId: string
  chapterId: string
  pages: ApiPage[]
  initialPage: number
  nextChapterId?: string
}

export function MangaReader({ seriesId, chapterId, pages, initialPage, nextChapterId }: Props) {
  const [pageIdx, setPageIdx] = useState(() =>
    Math.max(0, Math.min(pages.length - 1, initialPage - 1)),
  )
  const [chromeVisible, setChromeVisible] = useState(true)
  const [failedPageIds, setFailedPageIds] = useState<Set<string>>(new Set())
  const debounceRef = useRef<ReturnType<typeof setTimeout> | null>(null)
  const chapterSyncTriggeredRef = useRef(false)

  // Persist progress (debounced — sends the latest page 1.5s after user stops flipping).
  useEffect(() => {
    // Skip when the user has scrolled past the last page (Fix #4).
    if (pageIdx >= pages.length) return

    if (debounceRef.current) clearTimeout(debounceRef.current)
    debounceRef.current = setTimeout(() => {
      void mangaApi.setProgress(seriesId, chapterId, pageIdx + 1)
    }, 1500)

    return () => {
      if (debounceRef.current) clearTimeout(debounceRef.current)
    }
  }, [pageIdx, pages.length, seriesId, chapterId])

  // Trigger chapter-only sync when the chapter has no pages at all (queued
  // for sync but not yet downloaded) OR has at least one page with ready=false.
  useEffect(() => {
    if (chapterSyncTriggeredRef.current) return
    if (pages.length === 0 || pages.some((p) => !p.ready)) {
      chapterSyncTriggeredRef.current = true
      void mangaApi.startChapterSync(chapterId)
    }
  }, [pages, chapterId])

  // When the chapter is empty (sync hasn't reached it), poll for pages every
  // 3s and reload the page once they appear.
  useEffect(() => {
    if (pages.length > 0) return
    let cancelled = false
    const id = setInterval(async () => {
      try {
        const fresh = await mangaApi.getChapterPages(chapterId)
        if (!cancelled && fresh.length > 0) {
          window.location.reload()
        }
      } catch {
        // 404 still — sync hasn't reached this chapter yet
      }
    }, 3000)
    return () => {
      cancelled = true
      clearInterval(id)
    }
  }, [pages.length, chapterId])

  // Final flush on pagehide via sendBeacon.
  useEffect(() => {
    const onHide = () => {
      const safePageNumber = Math.min(pageIdx + 1, pages.length)
      const data = JSON.stringify({ chapterId, pageNumber: safePageNumber })
      navigator.sendBeacon?.(
        `${MANGA_API_BASE}/api/manga/progress/${encodeURIComponent(seriesId)}`,
        new Blob([data], { type: 'application/json' }),
      )
    }
    window.addEventListener('pagehide', onHide)
    return () => window.removeEventListener('pagehide', onHide)
  }, [pageIdx, pages.length, seriesId, chapterId])

  // Mark chapter as read once user reaches the last page.
  useEffect(() => {
    if (pageIdx === pages.length - 1) {
      void mangaApi.markRead(chapterId)
    }
  }, [pageIdx, pages.length, chapterId])

  // RTL: tap right = back, tap left = forward, middle = toggle chrome.
  const handleClick = (e: React.MouseEvent<HTMLDivElement>) => {
    const rect = e.currentTarget.getBoundingClientRect()
    const x = e.clientX - rect.left
    const w = rect.width
    if (x < w * 0.33) {
      setPageIdx((i) => Math.min(pages.length, i + 1))
    } else if (x > w * 0.66) {
      setPageIdx((i) => Math.max(0, i - 1))
    } else {
      setChromeVisible((v) => !v)
    }
  }

  const onImgError = (pageId: string) => () => {
    setFailedPageIds((prev) => {
      if (prev.has(pageId)) return prev
      const next = new Set(prev)
      next.add(pageId)
      return next
    })
    // Auto-skip forward (RTL: forward = next index)
    setPageIdx((i) => Math.min(pages.length, i + 1))
  }

  const current = pages[pageIdx]
  const isPastEnd = pageIdx >= pages.length

  // Preload next 2 pages.
  const preloadUrls = useMemo(() => {
    const urls: string[] = []
    for (const offset of [1, 2]) {
      const next = pages[pageIdx + offset]
      if (next?.ready) urls.push(mangaApi.pageUrl(next.id))
    }
    return urls
  }, [pageIdx, pages])

  return (
    <div className="fixed inset-0 bg-black text-white" onClick={handleClick}>
      {chromeVisible && (
        <div className="absolute top-0 left-0 right-0 z-20 flex items-center justify-between px-5 py-3 bg-black/70 backdrop-blur">
          <Link
            href={`/manga/${encodeURIComponent(seriesId)}`}
            className="text-accent text-sm"
            onClick={(e) => e.stopPropagation()}
          >
            ←
          </Link>
          <span
            className="font-mono text-[12px] text-faint"
            onClick={(e) => e.stopPropagation()}
          >
            {Math.min(pageIdx + 1, pages.length)} / {pages.length}
            {failedPageIds.size > 0 && (
              <span className="text-amber-400 ml-2">({failedPageIds.size} missing)</span>
            )}
          </span>
        </div>
      )}

      {pages.length === 0 && (
        <div className="absolute inset-0 flex flex-col items-center justify-center gap-3 px-8 text-center">
          <div className="text-[10px] uppercase tracking-widest text-faint">Kapitel</div>
          <div className="text-base text-white/90">Wird gerade synchronisiert…</div>
          <div className="text-[11px] text-faint max-w-xs">
            Hikari lädt dieses Kapitel von der Quelle. Sobald die Bilder da sind, springt der Reader automatisch los.
          </div>
          <div className="flex gap-1 mt-2">
            <span className="w-1.5 h-1.5 bg-accent rounded-full animate-pulse" />
            <span className="w-1.5 h-1.5 bg-accent/60 rounded-full animate-pulse" style={{ animationDelay: '0.2s' }} />
            <span className="w-1.5 h-1.5 bg-accent/30 rounded-full animate-pulse" style={{ animationDelay: '0.4s' }} />
          </div>
        </div>
      )}
      {pages.length > 0 && !isPastEnd && current && current.ready && (
        <img
          src={mangaApi.pageUrl(current.id)}
          alt={`Page ${current.pageNumber}`}
          className="absolute inset-0 w-full h-full object-contain"
          draggable={false}
          onError={onImgError(current.id)}
        />
      )}
      {pages.length > 0 && !isPastEnd && current && !current.ready && (
        <div className="absolute inset-0 flex items-center justify-center text-faint text-sm">
          Diese Seite wird gerade geladen…
        </div>
      )}
      {isPastEnd && (
        <div className="absolute inset-0 flex flex-col items-center justify-center gap-4 px-8 text-center">
          <div className="text-[10px] uppercase tracking-widest text-faint">Kapitel-Ende</div>
          {nextChapterId ? (
            <Link
              href={`/manga/${encodeURIComponent(seriesId)}/${encodeURIComponent(nextChapterId)}`}
              className="bg-accent text-black font-bold rounded px-5 py-2 text-sm"
              onClick={(e) => e.stopPropagation()}
            >
              Nächstes Kapitel →
            </Link>
          ) : (
            <Link
              href={`/manga/${encodeURIComponent(seriesId)}`}
              className="bg-accent text-black font-bold rounded px-5 py-2 text-sm"
              onClick={(e) => e.stopPropagation()}
            >
              Zur Übersicht
            </Link>
          )}
        </div>
      )}

      {chromeVisible && (
        <div className="absolute bottom-0 left-0 right-0 z-20 px-5 py-3 bg-black/70 backdrop-blur">
          <div className="h-[2px] bg-white/10">
            <div
              className="h-full bg-accent"
              style={{ width: `${(pageIdx / Math.max(1, pages.length - 1)) * 100}%` }}
            />
          </div>
        </div>
      )}

      {preloadUrls.map((u) => (
        <link key={u} rel="prefetch" as="image" href={u} />
      ))}
    </div>
  )
}
