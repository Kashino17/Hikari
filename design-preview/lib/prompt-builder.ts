/**
 * Form-driven prompt assembly. The Filter tab edits these fields;
 * the Prompt tab renders the result. Single source of truth.
 */
export interface FilterConfig {
  likeTags: string[]
  dislikeTags: string[]
  moodTags: string[]
  depthTags: string[]
  languages: string[]   // 'de' | 'en' | 'jp'
  minDurationSec: number
  maxDurationSec: number
  examples: string
  scoreThreshold: number  // 0-100, only show videos at or above this
}

export const DEFAULT_FILTER: FilterConfig = {
  likeTags: ['Mathematik', 'Philosophie', 'Wissenschaft'],
  dislikeTags: ['Clickbait', 'Drama', 'Reaction'],
  moodTags: ['ruhig', 'durchdacht'],
  depthTags: ['lehrreich', 'tiefgründig'],
  languages: ['de', 'en'],
  minDurationSec: 180,
  maxDurationSec: 1800,
  examples: '',
  scoreThreshold: 70,
}

export const MOOD_OPTIONS = [
  'ruhig', 'energetisch', 'humorvoll', 'ernst', 'durchdacht',
  'inspirierend', 'analytisch', 'persönlich',
] as const

export const DEPTH_OPTIONS = [
  'lehrreich', 'tiefgründig', 'locker', 'schnell',
  'theoretisch', 'praktisch', 'visuell',
] as const

export const LANGUAGE_OPTIONS = [
  { code: 'de', label: 'Deutsch' },
  { code: 'en', label: 'English' },
  { code: 'jp', label: '日本語' },
] as const

function fmtMin(s: number): string {
  const m = Math.round(s / 60)
  return `${m}min`
}

export function buildPrompt(f: FilterConfig): string {
  const parts: string[] = []

  parts.push(
    'Du bist Hikaris KI-Bewerter für Kurzvideos. Bewerte jeden Clip mit einem Score von 0–100 basierend auf den folgenden Kriterien.',
  )

  parts.push('')
  parts.push('## Was der Nutzer mag')
  if (f.likeTags.length) {
    parts.push(`Themen: ${f.likeTags.join(', ')}`)
  }
  if (f.moodTags.length) {
    parts.push(`Stimmung: ${f.moodTags.join(', ')}`)
  }
  if (f.depthTags.length) {
    parts.push(`Stil: ${f.depthTags.join(', ')}`)
  }

  parts.push('')
  parts.push('## Was der Nutzer NICHT mag')
  if (f.dislikeTags.length) {
    parts.push(f.dislikeTags.map((t) => `– ${t}`).join('\n'))
  } else {
    parts.push('– keine speziellen Ausschlüsse')
  }

  parts.push('')
  parts.push('## Harte Filter')
  parts.push(`– Sprachen: ${f.languages.join(', ').toUpperCase()}`)
  parts.push(`– Dauer: ${fmtMin(f.minDurationSec)}–${fmtMin(f.maxDurationSec)}`)
  parts.push(`– Mindest-Score zur Anzeige: ${f.scoreThreshold}/100`)

  if (f.examples.trim()) {
    parts.push('')
    parts.push('## Beispiele für Videos die der Nutzer geliebt hat')
    parts.push(f.examples.trim())
  }

  parts.push('')
  parts.push('## Bewertungslogik')
  parts.push(
    `**Hoher Score (80–100):** Trifft die Kriterien oben präzise, respektiert die Intelligenz des Zuschauers, kein Clickbait, klare Aussage, gute Informationsdichte.`,
  )
  parts.push(
    `**Niedriger Score (0–40):** Reine Unterhaltung ohne Lernwert, irreführende Titel, emotionale Manipulation, Padding, oder verstößt gegen die "NICHT"-Liste.`,
  )

  parts.push('')
  parts.push('## Ausgabe')
  parts.push(
    'Strikt JSON, nichts sonst: { "score": <0-100>, "category": "<math|science|tech|philosophy|society|history|art>", "reasoning": "<ein deutscher Satz, max 20 Wörter>" }',
  )

  return parts.join('\n')
}
