'use client'

interface CategorySliderProps {
  label: string
  value: number // -50..+50
  onChange: (v: number) => void
  min?: number
  max?: number
  step?: number
  snapZone?: number // |v| ≤ snapZone snaps to 0
}

export function CategorySlider({
  label,
  value,
  onChange,
  min = -50,
  max = 50,
  step = 5,
  snapZone = 4,
}: CategorySliderProps) {
  const positive = value > 0
  const negative = value < 0
  const sign = positive ? '+' : negative ? '−' : ''
  const magnitude = Math.abs(value)
  const valueColor = positive
    ? 'text-accent'
    : negative
      ? 'text-rose-400'
      : 'text-faint'

  function handleChange(e: React.ChangeEvent<HTMLInputElement>) {
    const raw = Number(e.target.value)
    onChange(Math.abs(raw) <= snapZone ? 0 : raw)
  }

  return (
    <div className="flex items-center gap-3 py-2.5 border-b-hairline last:border-0">
      <span className="text-[12px] flex-1 truncate">{label}</span>
      <div className="relative flex-[1.6] flex items-center">
        {/* center axis */}
        <span
          aria-hidden
          className="absolute left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 h-3 w-px bg-[var(--color-border)]"
        />
        <input
          type="range"
          min={min}
          max={max}
          step={step}
          value={value}
          onChange={handleChange}
          className="w-full accent-[var(--color-accent)]"
        />
      </div>
      <span
        className={`font-mono tabular-nums w-12 text-right text-[11px] ${valueColor}`}
      >
        {value === 0 ? '0' : `${sign}${magnitude}%`}
      </span>
    </div>
  )
}
