'use client'

import { useState, useEffect, useRef } from 'react'
import { allChannels } from '@/lib/mock-data'
import { useHikariStore } from '@/lib/store'
import { Search, MoreVertical, X } from 'lucide-react'

function ChannelAvatar({ name, accentColor, size = 36 }: { name: string; accentColor: string; size?: number }) {
  const letter = name.charAt(0).toUpperCase()
  return (
    <div
      className="flex-shrink-0 flex items-center justify-center rounded-full font-bold"
      style={{
        width: size,
        height: size,
        background: `${accentColor}22`,
        border: `1.5px solid ${accentColor}44`,
        color: accentColor,
        fontSize: size * 0.38,
        fontFamily: 'var(--font-geist-mono)',
      }}
    >
      {letter}
    </div>
  )
}

interface ToastMsg { id: number; text: string }

function Toast({ msg, onDone }: { msg: ToastMsg; onDone: () => void }) {
  useEffect(() => {
    const t = setTimeout(onDone, 2200)
    return () => clearTimeout(t)
  }, [onDone])
  return (
    <div
      className="fixed bottom-24 left-1/2 -translate-x-1/2 z-50 px-4 py-2 rounded-md font-mono text-[11px] tracking-wide animate-pulse"
      style={{ background: 'rgba(139,92,246,0.25)', border: '1px solid rgba(139,92,246,0.5)', color: '#8B5CF6', backdropFilter: 'blur(12px)', whiteSpace: 'nowrap' }}
    >
      {msg.text}
    </div>
  )
}

function PopoverMenu({
  channelId,
  onClose,
  onRemove,
  dark,
}: { channelId: string; onClose: () => void; onRemove: () => void; dark: boolean }) {
  const menuRef = useRef<HTMLDivElement>(null)
  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) onClose()
    }
    document.addEventListener('mousedown', handler)
    return () => document.removeEventListener('mousedown', handler)
  }, [onClose])

  const cardBg = dark ? '#1a1a1a' : '#fff'
  const cardBorder = dark ? 'rgba(255,255,255,0.1)' : '#e5e5e5'
  const textPrimary = dark ? '#fff' : '#0A0A0A'
  const textMuted = dark ? 'rgba(255,255,255,0.4)' : 'rgba(0,0,0,0.45)'

  return (
    <div
      ref={menuRef}
      className="absolute right-0 top-8 z-40 rounded-md overflow-hidden"
      style={{ background: cardBg, border: `1px solid ${cardBorder}`, minWidth: 148, boxShadow: '0 8px 24px rgba(0,0,0,0.3)' }}
    >
      {[
        { label: 'Jetzt scannen', action: () => onClose() },
        { label: 'Statistik', action: () => onClose() },
        { label: 'Entfernen', action: onRemove, danger: true },
      ].map((item) => (
        <button
          key={item.label}
          onClick={item.action}
          className="w-full text-left px-3 py-2.5 text-[11px] font-mono transition-colors"
          style={{ color: item.danger ? '#EF4444' : textPrimary, borderBottom: `1px solid ${cardBorder}` }}
        >
          {item.label}
        </button>
      ))}
    </div>
  )
}

export default function DashboardChannels() {
  const { subscribedChannelIds, subscribe, unsubscribe, theme } = useHikariStore()
  const dark = theme === 'dark'

  const [query, setQuery] = useState('')
  const [debouncedQuery, setDebouncedQuery] = useState('')
  const [toasts, setToasts] = useState<ToastMsg[]>([])
  const [openMenu, setOpenMenu] = useState<string | null>(null)
  const toastId = useRef(0)

  // Debounce
  useEffect(() => {
    const t = setTimeout(() => setDebouncedQuery(query.trim().toLowerCase()), 300)
    return () => clearTimeout(t)
  }, [query])

  const textPrimary = dark ? '#fff' : '#0A0A0A'
  const textMuted = dark ? 'rgba(255,255,255,0.4)' : 'rgba(0,0,0,0.45)'
  const cardBg = dark ? 'rgba(255,255,255,0.04)' : 'rgba(0,0,0,0.025)'
  const cardBorder = dark ? 'rgba(255,255,255,0.08)' : '#e5e5e5'
  const violet = '#8B5CF6'

  const subscribedChannels = allChannels.filter((c) => subscribedChannelIds.includes(c.id))
  const searchResults = debouncedQuery
    ? allChannels.filter(
        (c) =>
          !subscribedChannelIds.includes(c.id) &&
          (c.name.toLowerCase().includes(debouncedQuery) ||
            c.description.toLowerCase().includes(debouncedQuery) ||
            c.handle.toLowerCase().includes(debouncedQuery))
      )
    : []

  const addToast = (text: string) => {
    const id = ++toastId.current
    setToasts((t) => [...t, { id, text }])
  }

  const handleSubscribe = (channelId: string) => {
    subscribe(channelId)
    const ch = allChannels.find((c) => c.id === channelId)
    addToast(`Kanal abonniert: ${ch?.name}`)
    setQuery('')
  }

  function formatMB(mb: number) {
    if (mb >= 1000) return `${(mb / 1000).toFixed(1)} GB`
    return `${mb} MB`
  }

  return (
    <div className="p-3 pb-6" style={{ fontFamily: 'var(--font-geist-sans)' }}>
      {/* Toasts */}
      {toasts.map((msg) => (
        <Toast key={msg.id} msg={msg} onDone={() => setToasts((t) => t.filter((x) => x.id !== msg.id))} />
      ))}

      {/* Search input */}
      <div
        className="flex items-center gap-2 rounded-md px-3 mb-3"
        style={{ background: cardBg, border: `1px solid ${query ? 'rgba(139,92,246,0.35)' : cardBorder}`, height: 40 }}
      >
        <Search size={13} color={query ? violet : textMuted} strokeWidth={1.5} />
        <input
          className="flex-1 bg-transparent outline-none text-sm font-mono"
          style={{ color: textPrimary }}
          placeholder="YouTube-Kanal suchen…"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
        {query && (
          <button onClick={() => setQuery('')}>
            <X size={12} color={textMuted} strokeWidth={1.5} />
          </button>
        )}
      </div>

      {/* Search results */}
      {debouncedQuery && (
        <div className="mb-4">
          <div className="text-[8px] font-mono uppercase tracking-widest mb-2 px-1" style={{ color: textMuted }}>
            Suchergebnisse {searchResults.length > 0 ? `— ${searchResults.length} gefunden` : '— keine Treffer'}
          </div>
          {searchResults.length === 0 ? (
            <div
              className="rounded-md p-3 text-center text-[11px] font-mono"
              style={{ background: cardBg, border: `1px solid ${cardBorder}`, color: textMuted }}
            >
              Kein Kanal gefunden
            </div>
          ) : (
            <div className="space-y-2">
              {searchResults.map((ch) => (
                <div
                  key={ch.id}
                  className="flex items-center gap-3 rounded-md px-3 py-2.5"
                  style={{ background: cardBg, border: `1px solid ${cardBorder}` }}
                >
                  <ChannelAvatar name={ch.name} accentColor={ch.accentColor} size={36} />
                  <div className="flex-1 min-w-0">
                    <div className="text-sm font-medium leading-tight truncate" style={{ color: textPrimary }}>
                      {ch.name}
                    </div>
                    <div className="text-[9px] font-mono mt-0.5" style={{ color: textMuted }}>
                      {ch.subscribers} · {ch.description}
                    </div>
                  </div>
                  <button
                    onClick={() => handleSubscribe(ch.id)}
                    className="flex-shrink-0 px-2.5 py-1.5 rounded-md text-[10px] font-mono uppercase tracking-widest text-white"
                    style={{ background: violet, border: '1px solid rgba(139,92,246,0.6)' }}
                  >
                    Abo
                  </button>
                </div>
              ))}
            </div>
          )}
          <div className="my-4" style={{ borderTop: `1px solid ${cardBorder}` }} />
        </div>
      )}

      {/* Meine Kanäle header */}
      <div className="flex items-center justify-between mb-2 px-1">
        <span className="text-[8px] font-mono uppercase tracking-widest" style={{ color: textMuted }}>
          Meine Kanäle
        </span>
        <span className="text-[8px] font-mono" style={{ color: violet }}>
          {subscribedChannels.length}
        </span>
      </div>

      {/* Subscribed channel rows */}
      <div className="space-y-1.5">
        {subscribedChannels.map((ch) => {
          const approvalRate = Math.round((ch.approved / ch.totalVideos) * 100)
          return (
            <div
              key={ch.id}
              className="rounded-md overflow-hidden"
              style={{ border: `1px solid ${cardBorder}` }}
            >
              <div
                className="flex items-center gap-3 px-3 py-2.5"
                style={{ background: cardBg }}
              >
                <ChannelAvatar name={ch.name} accentColor={ch.accentColor} size={32} />
                <div className="flex-1 min-w-0">
                  <div className="text-[12px] font-medium truncate" style={{ color: textPrimary }}>
                    {ch.name}
                  </div>
                  <div className="flex items-center gap-2 mt-0.5">
                    <span className="text-[8px] font-mono" style={{ color: '#10B981' }}>
                      {ch.approved} ok
                    </span>
                    <span className="text-[8px] font-mono" style={{ color: '#EF4444' }}>
                      {ch.rejected} rej
                    </span>
                    <span className="text-[8px] font-mono" style={{ color: textMuted }}>
                      {formatMB(ch.diskMB)}
                    </span>
                  </div>
                </div>

                {/* Approval bar */}
                <div className="flex flex-col items-end gap-1 mr-2">
                  <span className="text-[8px] font-mono" style={{ color: approvalRate >= 90 ? '#10B981' : approvalRate >= 70 ? '#F59E0B' : '#EF4444' }}>
                    {approvalRate}%
                  </span>
                  <div className="w-12 h-1 rounded-sm overflow-hidden" style={{ background: dark ? 'rgba(255,255,255,0.08)' : '#e5e5e5' }}>
                    <div
                      className="h-full rounded-sm"
                      style={{
                        width: `${approvalRate}%`,
                        background: approvalRate >= 90 ? '#10B981' : approvalRate >= 70 ? '#F59E0B' : '#EF4444',
                      }}
                    />
                  </div>
                </div>

                {/* Kebab menu */}
                <div className="relative">
                  <button
                    className="w-7 h-7 flex items-center justify-center rounded-md"
                    style={{ background: 'transparent', border: `1px solid ${cardBorder}` }}
                    onClick={() => setOpenMenu(openMenu === ch.id ? null : ch.id)}
                  >
                    <MoreVertical size={12} color={textMuted} strokeWidth={1.5} />
                  </button>
                  {openMenu === ch.id && (
                    <PopoverMenu
                      channelId={ch.id}
                      dark={dark}
                      onClose={() => setOpenMenu(null)}
                      onRemove={() => {
                        unsubscribe(ch.id)
                        setOpenMenu(null)
                        addToast(`${ch.name} entfernt`)
                      }}
                    />
                  )}
                </div>
              </div>
            </div>
          )
        })}
      </div>

      {subscribedChannels.length === 0 && (
        <div
          className="rounded-md p-6 text-center"
          style={{ background: cardBg, border: `1px dashed ${cardBorder}` }}
        >
          <div className="text-[11px] font-mono" style={{ color: textMuted }}>
            Keine abonnierten Kanäle
          </div>
        </div>
      )}
    </div>
  )
}
