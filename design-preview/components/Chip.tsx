'use client'

import { useState, useRef, type KeyboardEvent } from 'react'
import { X } from 'lucide-react'

interface ChipMultiProps {
  options: string[]
  values: string[]
  onChange: (next: string[]) => void
  renderLabel?: (option: string) => string
}

export function ChipMulti({ options, values, onChange, renderLabel }: ChipMultiProps) {
  function toggle(opt: string) {
    onChange(values.includes(opt) ? values.filter((v) => v !== opt) : [...values, opt])
  }
  return (
    <div className="flex flex-wrap gap-1.5">
      {options.map((opt) => {
        const active = values.includes(opt)
        return (
          <button
            key={opt}
            onClick={() => toggle(opt)}
            className={`px-3 py-1.5 rounded-full text-[12px] transition-colors ${
              active
                ? 'bg-accent text-black font-medium'
                : 'bg-surface border-hairline text-mute hover:text-white/80'
            }`}
          >
            {renderLabel ? renderLabel(opt) : opt}
          </button>
        )
      })}
    </div>
  )
}

interface ChipFreeInputProps {
  values: string[]
  onChange: (next: string[]) => void
  placeholder?: string
}

export function ChipFreeInput({ values, onChange, placeholder }: ChipFreeInputProps) {
  const [draft, setDraft] = useState('')
  const ref = useRef<HTMLInputElement>(null)

  function add(raw: string) {
    const v = raw.trim()
    if (!v) return
    if (values.includes(v)) return
    onChange([...values, v])
  }

  function onKey(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key === 'Enter' || e.key === ',') {
      e.preventDefault()
      add(draft)
      setDraft('')
    } else if (e.key === 'Backspace' && draft === '' && values.length > 0) {
      onChange(values.slice(0, -1))
    }
  }

  return (
    <div
      onClick={() => ref.current?.focus()}
      className="flex flex-wrap gap-1.5 bg-surface border-hairline rounded-md p-2 min-h-[44px] cursor-text"
    >
      {values.map((v) => (
        <span
          key={v}
          className="inline-flex items-center gap-1 pl-2.5 pr-1 py-1 rounded-full bg-accent-soft text-accent text-[12px] border border-[var(--color-accent-border)]"
        >
          {v}
          <button
            onClick={(e) => {
              e.stopPropagation()
              onChange(values.filter((x) => x !== v))
            }}
            className="w-4 h-4 flex items-center justify-center rounded-full hover:bg-black/20"
            aria-label={`${v} entfernen`}
          >
            <X size={10} strokeWidth={2} />
          </button>
        </span>
      ))}
      <input
        ref={ref}
        value={draft}
        onChange={(e) => setDraft(e.target.value)}
        onKeyDown={onKey}
        onBlur={() => { if (draft.trim()) { add(draft); setDraft('') } }}
        placeholder={values.length === 0 ? placeholder : ''}
        className="flex-1 min-w-[80px] bg-transparent text-[13px] placeholder:text-faint px-1"
      />
    </div>
  )
}
