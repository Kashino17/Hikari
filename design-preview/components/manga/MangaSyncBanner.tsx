'use client'
import { useEffect, useState } from 'react'
import { mangaApi, type ApiSyncJob } from '@/lib/manga-api'

export function MangaSyncBanner() {
  const [job, setJob] = useState<ApiSyncJob | null>(null)

  useEffect(() => {
    let cancelled = false
    let interval: ReturnType<typeof setInterval> | null = null

    async function poll() {
      try {
        const jobs = await mangaApi.listJobs()
        const active = jobs.find((j) => j.status === 'running' || j.status === 'queued') ?? null
        if (!cancelled) setJob(active)
      } catch {
        // ignore — backend may be down
      }
    }

    poll()
    interval = setInterval(poll, 2000)
    return () => {
      cancelled = true
      if (interval) clearInterval(interval)
    }
  }, [])

  if (!job) return null
  const total = job.total_chapters || 1
  const pct = Math.round((job.done_chapters / total) * 100)

  return (
    <div className="sticky top-0 z-40 bg-amber-900/30 border-b-hairline px-5 py-2 text-[12px] text-amber-200 flex items-center gap-3">
      <span className="font-mono">{job.done_chapters} / {total}</span>
      <div className="flex-1 h-[2px] bg-white/10">
        <div className="h-full bg-accent transition-all" style={{ width: `${pct}%` }} />
      </div>
      <span className="text-faint uppercase tracking-widest text-[10px]">Sync läuft</span>
    </div>
  )
}
