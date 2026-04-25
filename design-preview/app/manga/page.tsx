import { mangaApi } from '@/lib/manga-api'
import { MangaHero } from '@/components/manga/MangaHero'
import { MangaRow } from '@/components/manga/MangaRow'
import { MangaCard } from '@/components/manga/MangaCard'
import { MangaSyncBanner } from '@/components/manga/MangaSyncBanner'

export default async function MangaHomePage() {
  const [series, cont] = await Promise.all([
    mangaApi.listSeries().catch(() => []),
    mangaApi.getContinue().catch(() => []),
  ])

  if (series.length === 0) {
    return (
      <main className="pb-16">
        <MangaSyncBanner />
        <div className="px-5 pt-20 text-center">
          <div className="text-[10px] uppercase tracking-widest text-faint mb-3">Manga</div>
          <h1 className="text-2xl font-bold text-white mb-3">Noch keine Mangas</h1>
          <p className="text-sm text-faint">
            Trigger den Sync im Tuning-Tab → System.
          </p>
        </div>
      </main>
    )
  }

  const continueIds = new Set(cont.map((c) => c.seriesId))
  const heroSeries = cont[0]
    ? series.find((s) => s.id === cont[0].seriesId) ?? series[0]
    : series[0]

  return (
    <main className="pb-16">
      <MangaSyncBanner />
      <MangaHero series={heroSeries} cont={cont[0]} />

      {cont.length > 0 && (
        <MangaRow title="Weiterlesen">
          {cont.map((c) => {
            const s = series.find((x) => x.id === c.seriesId)
            if (!s) return null
            return <MangaCard key={c.seriesId} series={s} progress={0.5} />
          })}
        </MangaRow>
      )}

      <MangaRow title="Alle Mangas">
        {series.map((s) => (
          <MangaCard key={s.id} series={s} progress={continueIds.has(s.id) ? 0.5 : undefined} />
        ))}
      </MangaRow>
    </main>
  )
}
