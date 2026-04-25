'use client'
import { useEffect, useMemo, useRef, useState } from 'react'
import Link from 'next/link'
import { motion } from 'framer-motion'
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

  // Page-flip: track an explicit flip state so we can render two layers
  // (current page on top, destination beneath) and avoid AnimatePresence
  // cross-fade flashing.
  const [flipState, setFlipState] = useState<{
    toIdx: number
    dir: 1 | -1
  } | null>(null)

  // Pinch zoom + pan
  const [zoom, setZoom] = useState({ scale: 1, x: 0, y: 0 })
  const pinchRef = useRef<{
    startD: number
    baseScale: number
    baseX: number
    baseY: number
  } | null>(null)
  const panRef = useRef<{
    startX: number
    startY: number
    baseX: number
    baseY: number
  } | null>(null)
  const multitouchRef = useRef(false)

  const touchStartXRef = useRef<number | null>(null)
  const skipClickRef = useRef(false)
  const SWIPE_THRESHOLD = 40
  const ZOOMED_THRESHOLD = 1.05

  const advance = () => {
    if (flipState || zoom.scale > ZOOMED_THRESHOLD) return
    if (pageIdx >= pages.length - 1) {
      // jump to past-end (chapter-end UI)
      setPageIdx(pages.length)
      return
    }
    setFlipState({ toIdx: pageIdx + 1, dir: 1 })
  }
  const goBack = () => {
    if (flipState || zoom.scale > ZOOMED_THRESHOLD) return
    if (pageIdx === 0) return
    setFlipState({ toIdx: pageIdx - 1, dir: -1 })
  }

  const onTouchStart = (e: React.TouchEvent) => {
    if (e.touches.length === 2) {
      const [t1, t2] = [e.touches[0], e.touches[1]]
      pinchRef.current = {
        startD: Math.hypot(t2.clientX - t1.clientX, t2.clientY - t1.clientY),
        baseScale: zoom.scale,
        baseX: zoom.x,
        baseY: zoom.y,
      }
      multitouchRef.current = true
      skipClickRef.current = true
      touchStartXRef.current = null
      panRef.current = null
      return
    }
    if (e.touches.length === 1) {
      if (zoom.scale > ZOOMED_THRESHOLD) {
        // when zoomed in, single finger pans the image
        const t = e.touches[0]
        panRef.current = {
          startX: t.clientX,
          startY: t.clientY,
          baseX: zoom.x,
          baseY: zoom.y,
        }
        skipClickRef.current = true
      } else {
        // at base zoom, single finger may be a swipe-to-flip
        touchStartXRef.current = e.touches[0].clientX
      }
    }
  }

  const onTouchMove = (e: React.TouchEvent) => {
    if (multitouchRef.current && e.touches.length === 2 && pinchRef.current) {
      const [t1, t2] = [e.touches[0], e.touches[1]]
      const d = Math.hypot(t2.clientX - t1.clientX, t2.clientY - t1.clientY)
      const next = pinchRef.current.baseScale * (d / pinchRef.current.startD)
      const clamped = Math.min(4, Math.max(1, next))
      setZoom((z) => ({ ...z, scale: clamped }))
      return
    }
    if (panRef.current && e.touches.length === 1) {
      const t = e.touches[0]
      const dx = t.clientX - panRef.current.startX
      const dy = t.clientY - panRef.current.startY
      setZoom((z) => ({
        ...z,
        x: panRef.current!.baseX + dx,
        y: panRef.current!.baseY + dy,
      }))
    }
  }

  const onTouchEnd = (e: React.TouchEvent) => {
    // any pinch ending (one of two fingers lifted, or both)
    if (multitouchRef.current && e.touches.length < 2) {
      multitouchRef.current = false
      pinchRef.current = null
      if (zoom.scale < ZOOMED_THRESHOLD) {
        setZoom({ scale: 1, x: 0, y: 0 })
      }
      // also clear any in-flight one-finger swipe state
      touchStartXRef.current = null
      return
    }
    // pan end
    if (panRef.current && e.touches.length === 0) {
      panRef.current = null
      return
    }
    // swipe end (only valid at base zoom + no flip in progress)
    const start = touchStartXRef.current
    touchStartXRef.current = null
    if (start == null || zoom.scale > ZOOMED_THRESHOLD || flipState) return
    const end = e.changedTouches[0]?.clientX
    if (end == null) return
    const dx = end - start
    if (dx < -SWIPE_THRESHOLD) {
      skipClickRef.current = true
      advance()
    } else if (dx > SWIPE_THRESHOLD) {
      skipClickRef.current = true
      goBack()
    }
  }

  // Double-tap detection — used while zoomed to reset zoom.
  const lastTapRef = useRef<number>(0)

  const handleClick = (e: React.MouseEvent<HTMLDivElement>) => {
    if (skipClickRef.current) {
      skipClickRef.current = false
      return
    }
    if (zoom.scale > ZOOMED_THRESHOLD) {
      const now = Date.now()
      if (now - lastTapRef.current < 300) {
        setZoom({ scale: 1, x: 0, y: 0 })
        lastTapRef.current = 0
        return
      }
      lastTapRef.current = now
      setChromeVisible((v) => !v)
      return
    }
    if (flipState) {
      setChromeVisible((v) => !v)
      return
    }
    const rect = e.currentTarget.getBoundingClientRect()
    const x = e.clientX - rect.left
    const w = rect.width
    if (x < w * 0.33) advance()
    else if (x > w * 0.66) goBack()
    else setChromeVisible((v) => !v)
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
    <div
      className="fixed inset-0 bg-black text-white z-[60] touch-none select-none overflow-hidden"
      onClick={handleClick}
      onTouchStart={onTouchStart}
      onTouchMove={onTouchMove}
      onTouchEnd={onTouchEnd}
    >
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
      {pages.length > 0 && !isPastEnd && (
        <div
          className="absolute inset-0"
          style={{
            transform: `translate(${zoom.x}px, ${zoom.y}px) scale(${zoom.scale})`,
            transformOrigin: '50% 50%',
            transition:
              multitouchRef.current || panRef.current
                ? 'none'
                : 'transform 180ms ease-out',
            perspective: '1600px',
          }}
        >
          {/* Destination page beneath — only rendered during a flip. */}
          {flipState && pages[flipState.toIdx]?.ready && (
            <img
              key={`under-${flipState.toIdx}`}
              src={mangaApi.pageUrl(pages[flipState.toIdx]!.id)}
              alt=""
              className="absolute inset-0 w-full h-full object-contain"
              draggable={false}
            />
          )}

          {/* Top page — animates rotateY when flipState is set. */}
          {current && current.ready && (
            <motion.img
              key={`top-${pageIdx}`}
              src={mangaApi.pageUrl(current.id)}
              alt={`Page ${current.pageNumber}`}
              className="absolute inset-0 w-full h-full object-contain"
              draggable={false}
              onError={onImgError(current.id)}
              initial={false}
              animate={
                flipState
                  ? {
                      rotateY: flipState.dir > 0 ? -170 : 170,
                      rotateZ: flipState.dir > 0 ? -3 : 3,
                    }
                  : { rotateY: 0, rotateZ: 0 }
              }
              transition={{
                rotateY: { duration: 0.9, ease: [0.45, 0.05, 0.35, 1] },
                rotateZ: { duration: 0.9, ease: [0.45, 0.05, 0.35, 1] },
              }}
              onAnimationComplete={() => {
                if (flipState) {
                  setPageIdx(flipState.toIdx)
                  setFlipState(null)
                }
              }}
              style={{
                transformOrigin:
                  flipState?.dir === 1
                    ? '0% 100%'
                    : flipState?.dir === -1
                    ? '100% 100%'
                    : '50% 50%',
                transformStyle: 'preserve-3d',
                backfaceVisibility: 'hidden',
                boxShadow: '0 8px 40px rgba(0,0,0,0.5)',
              }}
            />
          )}

          {current && !current.ready && (
            <div className="absolute inset-0 flex items-center justify-center text-faint text-sm">
              Diese Seite wird gerade geladen…
            </div>
          )}

          {/* Subtle zoom indicator in the corner */}
          {zoom.scale > ZOOMED_THRESHOLD && (
            <div className="absolute bottom-20 left-1/2 -translate-x-1/2 z-30 px-3 py-1 rounded-full bg-black/70 backdrop-blur text-[10px] uppercase tracking-widest text-faint pointer-events-none">
              Zoom {zoom.scale.toFixed(1)}× · Doppeltipp zum Reset
            </div>
          )}
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
