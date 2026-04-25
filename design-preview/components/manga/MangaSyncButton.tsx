'use client'
import { useState } from 'react'
import { mangaApi } from '@/lib/manga-api'

export function MangaSyncButton() {
  const [busy, setBusy] = useState(false)
  const [msg, setMsg] = useState<string>('')

  async function trigger() {
    setBusy(true)
    setMsg('')
    try {
      const r = await mangaApi.startSync()
      if (r.status === 409) setMsg('Sync läuft bereits')
      else if (!r.ok) setMsg(`Fehler ${r.status}`)
      else setMsg('Sync gestartet')
    } catch {
      setMsg('Fehler')
    } finally {
      setBusy(false)
    }
  }

  return (
    <div className="flex flex-col gap-2">
      <button
        onClick={trigger}
        disabled={busy}
        className="w-full flex items-center justify-between text-left px-3 py-2.5 rounded-md bg-surface border-hairline transition-colors hover:bg-white/[0.03] text-[13px] disabled:opacity-50"
      >
        Manga sync now
      </button>
      {msg && <div className="text-[11px] text-faint">{msg}</div>}
    </div>
  )
}
