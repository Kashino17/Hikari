'use client'

interface PhoneFrameProps {
  children: React.ReactNode
  className?: string
}

export default function PhoneFrame({ children, className = '' }: PhoneFrameProps) {
  return (
    <div className={`flex items-center justify-center min-h-screen ${className}`}>
      {/* Desktop: show phone frame */}
      <div className="hidden md:block relative">
        {/* Outer bezel */}
        <div
          className="relative bg-neutral-900 rounded-[52px] p-[3px] shadow-2xl"
          style={{
            boxShadow: '0 0 0 1px rgba(255,255,255,0.08), 0 40px 80px rgba(0,0,0,0.6), inset 0 0 0 1px rgba(255,255,255,0.05)',
            width: 396,
          }}
        >
          {/* Inner bezel */}
          <div className="relative bg-black rounded-[50px] overflow-hidden" style={{ width: 390, height: 844 }}>
            {/* Dynamic Island */}
            <div
              className="absolute top-3 left-1/2 -translate-x-1/2 z-50 bg-black rounded-full"
              style={{ width: 120, height: 34 }}
            />
            {/* Screen content */}
            <div className="absolute inset-0 overflow-hidden rounded-[50px]">
              {children}
            </div>
          </div>
        </div>
        {/* Side buttons */}
        <div className="absolute left-[-4px] top-[140px] w-1 h-10 bg-neutral-700 rounded-l-sm" />
        <div className="absolute left-[-4px] top-[190px] w-1 h-16 bg-neutral-700 rounded-l-sm" />
        <div className="absolute left-[-4px] top-[260px] w-1 h-16 bg-neutral-700 rounded-l-sm" />
        <div className="absolute right-[-4px] top-[170px] w-1 h-20 bg-neutral-700 rounded-r-sm" />
      </div>
      {/* Mobile: fill viewport */}
      <div className="md:hidden w-full min-h-screen">
        {children}
      </div>
    </div>
  )
}
