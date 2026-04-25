'use client'

import { useState, useMemo, useEffect, useDeferredValue } from 'react'
import { allChannels } from '@/lib/mock-data'
import { useHikariStore } from '@/lib/store'
import { Search, X, Plus, Check, Trash2 } from 'lucide-react'

function fmtMB(mb: number) {
  if (mb >= 1024) return `${(mb / 1024).toFixed(1)} GB`
  return `${mb} MB`
}

export default function ChannelsPage() {
  const subscribed = useHikariStore((s) => s.subscribedChannelIds)
  const subscribe = useHikariStore((s) => s.subscribe)
  const unsubscribe = useHikariStore((s) => s.unsubscribe)

  const [query, setQuery] = useState('')
  const deferred = useDeferredValue(query)
  const [toast, setToast] = useState<string | null>(null)

  useEffect(() => {
    if (!toast) return
    const t = setTimeout(() => setToast(null), 1800)
    return () => clearTimeout(t)
  }, [toast])

  const subscribedChannels = useMemo(
    () => allChannels.filter((c) => subscribed.includes(c.id)),
    [subscribed],
  )

  const searchResults = useMemo(() => {
    const q = deferred.trim().toLowerCase()
    if (!q) return []
    return allChannels.filter(
      (c) =>
        c.name.toLowerCase().includes(q) ||
        c.handle.toLowerCase().includes(q) ||
        c.description.toLowerCase().includes(q),
    )
  }, [deferred])

  return (
    <div className="min-h-svh">
      {/* Header */}
      <header
        className="sticky top-0 z-20 bg-[var(--color-bg)]/95 backdrop-blur border-b-hairline"
        style={{ paddingTop: 'env(safe-area-inset-top, 0)' }}
      >
        <div className="px-5 py-4">
          <h1 className="text-base font-medium tracking-tight mb-3">Kanäle</h1>
          <div className="relative">
            <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-faint" strokeWidth={1.5} />
            <input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="Kanal suchen…"
              className="w-full bg-surface border-hairline rounded-md pl-9 pr-9 py-2.5 text-sm placeholder:text-faint"
            />
            {query && (
              <button
                onClick={() => setQuery('')}
                className="absolute right-2 top-1/2 -translate-y-1/2 w-6 h-6 flex items-center justify-center text-faint"
              >
                <X size={14} strokeWidth={1.5} />
              </button>
            )}
          </div>
        </div>
      </header>

      {/* Search results */}
      {deferred.trim() && (
        <section className="px-5 py-3">
          <div className="text-faint text-[10px] font-mono uppercase tracking-widest mb-3">
            {searchResults.length === 0 ? 'Nichts gefunden' : `${searchResults.length} Treffer`}
          </div>
          <ul>
            {searchResults.map((c) => {
              const isSub = subscribed.includes(c.id)
              return (
                <li
                  key={c.id}
                  className="flex items-center justify-between py-3 border-b-hairline"
                >
                  <div className="min-w-0 pr-3">
                    <div className="text-[14px] truncate">{c.name}</div>
                    <div className="text-faint text-[11px] truncate">{c.handle} · {c.subscribers}</div>
                  </div>
                  <button
                    onClick={() => {
                      if (isSub) {
                        unsubscribe(c.id)
                        setToast(`${c.name} entfernt`)
                      } else {
                        subscribe(c.id)
                        setToast(`${c.name} hinzugefügt`)
                      }
                    }}
                    className={`flex items-center gap-1.5 px-3 py-1.5 rounded-md text-[11px] transition-colors ${
                      isSub
                        ? 'bg-accent-soft text-accent border border-[var(--color-accent-border)]'
                        : 'bg-surface border-hairline'
                    }`}
                  >
                    {isSub ? <Check size={12} strokeWidth={2} /> : <Plus size={12} strokeWidth={2} />}
                    {isSub ? 'Abonniert' : 'Folgen'}
                  </button>
                </li>
              )
            })}
          </ul>
        </section>
      )}

      {/* Subscribed list (hidden during active search) */}
      {!deferred.trim() && (
        <section className="px-5 py-2">
          <div className="text-faint text-[10px] font-mono uppercase tracking-widest mb-3 mt-2">
            Abonniert · {subscribedChannels.length}
          </div>
          {subscribedChannels.length === 0 ? (
            <div className="text-mute text-sm py-12 text-center">
              Noch keine Kanäle. Suche oben, um welche hinzuzufügen.
            </div>
          ) : (
            <ul>
              {subscribedChannels.map((c) => (
                <li key={c.id} className="py-3 border-b-hairline">
                  <div className="flex items-start justify-between gap-3">
                    <div className="min-w-0 flex-1">
                      <div className="text-[14px] truncate">{c.name}</div>
                      <div className="text-faint text-[11px] mt-0.5 mb-1.5">
                        {c.handle} · {c.subscribers}
                      </div>
                      <div className="flex gap-3 text-[10px] font-mono text-mute">
                        <span><span className="text-accent">{c.approved}</span> ok</span>
                        <span>{c.rejected} abg.</span>
                        <span>{fmtMB(c.diskMB)}</span>
                      </div>
                    </div>
                    <button
                      onClick={() => unsubscribe(c.id)}
                      className="w-8 h-8 flex items-center justify-center text-faint hover:text-[var(--color-danger)] transition-colors"
                      aria-label={`${c.name} entfernen`}
                    >
                      <Trash2 size={14} strokeWidth={1.5} />
                    </button>
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>
      )}

      {/* Toast */}
      {toast && (
        <div className="fixed left-1/2 -translate-x-1/2 bottom-24 z-50 px-4 py-2 bg-surface-2 border-hairline rounded-md text-[12px]">
          {toast}
        </div>
      )}
    </div>
  )
}
