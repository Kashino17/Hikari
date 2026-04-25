import { ChevronRight } from 'lucide-react'

export function MangaRow({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <div className="mb-8">
      <div className="flex items-center justify-between px-5 mb-3">
        <h2 className="text-[15px] font-bold text-white/95 flex items-center gap-1">
          {title} <ChevronRight size={16} className="text-faint" />
        </h2>
      </div>
      <div className="flex gap-3 overflow-x-auto px-5 no-scrollbar">{children}</div>
    </div>
  )
}
