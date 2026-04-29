import type { VideoCategory } from './mock-data'

// Field names mirror future `user_discovery_settings` table columns 1:1
// so the eventual backend sync is a trivial mapping.
// Per-axis weights for the discovery scoring engine. Mirrors backend's
// `ScoreWeights` interface — values 0..1, must sum to 1.0 (validated server-side).
export interface ScoreWeights {
  category: number
  similarity: number
  quality: number
  longForm: number
}

export type ScoreAxis = keyof ScoreWeights

export interface DiscoverySettings {
  discovery_ratio: number // 0..100  — % of feed slots reserved for new channels
  category_weights: Record<VideoCategory, number> // -50..+50  — per-category boost/penalty
  quality_threshold: number // 0..100 — minimum quality score for *discovery* suggestions
  score_weights: ScoreWeights // ranking axis weights (sum to 1.0)
}

export const ALL_CATEGORIES: VideoCategory[] = [
  'science',
  'tech',
  'philosophy',
  'math',
  'society',
  'art',
  'history',
]

export const CATEGORY_LABEL_DE: Record<VideoCategory, string> = {
  science: 'Wissenschaft',
  tech: 'Technologie',
  philosophy: 'Philosophie',
  math: 'Mathematik',
  society: 'Gesellschaft',
  art: 'Kunst',
  history: 'Geschichte',
}

// Defaults match backend `SCORE_WEIGHTS` so the frontend doesn't drift.
export const DEFAULT_SCORE_WEIGHTS: ScoreWeights = {
  category: 0.30,
  similarity: 0.20,
  quality: 0.35,
  longForm: 0.15,
}

export const SCORE_AXIS_LABEL_DE: Record<ScoreAxis, string> = {
  category: 'Kategorie',
  similarity: 'Ähnlichkeit',
  quality: 'Qualität',
  longForm: 'Long-Form',
}

export const SCORE_AXIS_HINT_DE: Record<ScoreAxis, string> = {
  category: 'Wie stark Kategorie-Match zählt',
  similarity: 'Wie stark Ähnlichkeit zu deinem Profil zählt',
  quality: 'Wie stark redaktionelle Qualität zählt',
  longForm: 'Wie stark längere Formate bevorzugt werden (Anti-Doom)',
}

/**
 * Re-balance score weights so they sum to 1.0 while preserving relative
 * proportions. Used as the wire-format guarantee: backend rejects sum != 1.
 */
export function normalizeScoreWeights(w: ScoreWeights): ScoreWeights {
  const sum = w.category + w.similarity + w.quality + w.longForm
  if (sum <= 0) return DEFAULT_SCORE_WEIGHTS
  return {
    category: w.category / sum,
    similarity: w.similarity / sum,
    quality: w.quality / sum,
    longForm: w.longForm / sum,
  }
}

export const DEFAULT_DISCOVERY_SETTINGS: DiscoverySettings = {
  discovery_ratio: 50,
  category_weights: Object.fromEntries(
    ALL_CATEGORIES.map((c) => [c, 0]),
  ) as Record<VideoCategory, number>,
  quality_threshold: 30,
  score_weights: DEFAULT_SCORE_WEIGHTS,
}

export function ratioLabel(ratio: number): string {
  if (ratio <= 10) return 'Nur Gefolgte'
  if (ratio <= 35) return 'Hauptsächlich Gefolgte'
  if (ratio <= 65) return 'Ausgewogen'
  if (ratio <= 90) return 'Hauptsächlich Neues'
  return 'Nur Neues'
}

export function thresholdLabel(threshold: number): string {
  if (threshold <= 20) return 'Mehr Vielfalt'
  if (threshold <= 50) return 'Locker'
  if (threshold <= 75) return 'Streng'
  return 'Sehr streng'
}

const NOTEWORTHY_WEIGHT = 10

function fmt(cat: VideoCategory, w: number): string {
  return `${CATEGORY_LABEL_DE[cat]} (${w > 0 ? '+' : '−'}${Math.abs(w)} %)`
}

export function summarizeDiscoverySettings(s: DiscoverySettings): string {
  const ratio = s.discovery_ratio
  const ratioLbl = ratioLabel(ratio)
  const qual = thresholdLabel(s.quality_threshold).toLowerCase()
  const top = (Object.entries(s.category_weights) as [VideoCategory, number][])
    .filter(([, w]) => Math.abs(w) >= NOTEWORTHY_WEIGHT)
    .sort((a, b) => Math.abs(b[1]) - Math.abs(a[1]))
    .slice(0, 2)

  if (top.length === 0) {
    return `${ratioLbl}, keine Kategorie hervorgehoben, Qualität: ${qual}.`
  }
  if (top.length === 1) {
    return `${ratioLbl}, mit Schwerpunkt ${fmt(top[0][0], top[0][1])}, Qualität: ${qual}.`
  }

  const [a, b] = top
  const pos = [a, b].filter(([, w]) => w > 0)
  const neg = [a, b].filter(([, w]) => w < 0)
  let phrase: string
  if (pos.length === 1 && neg.length === 1) {
    phrase = `bevorzugt ${fmt(pos[0][0], pos[0][1])} und weniger ${fmt(neg[0][0], neg[0][1])}`
  } else if (pos.length === 2) {
    phrase = `bevorzugt ${fmt(a[0], a[1])} und ${fmt(b[0], b[1])}`
  } else {
    phrase = `dämpft ${fmt(a[0], a[1])} und ${fmt(b[0], b[1])}`
  }
  return `Du sortierst aktuell ${phrase} in einen ${ratio} %-Neu-Mix, Qualität: ${qual}.`
}
