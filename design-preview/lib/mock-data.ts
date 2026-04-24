export type VideoCategory = 'science' | 'tech' | 'philosophy' | 'math' | 'society' | 'art' | 'history'

export interface MockVideo {
  videoId: string
  title: string
  channel: string
  durationSec: number
  category: VideoCategory
  thumbnailGradient: string // tailwind gradient classes
  aiScore: number
  aiReasoning: string
  saved: boolean
  seen: boolean
}

export interface MockChannel {
  id: string
  name: string
  handle: string
  totalVideos: number
  approved: number
  rejected: number
  diskMB: number
  category: VideoCategory
  accentColor: string
}

export interface SponsorBlockCategory {
  key: string
  label: string
  labelDe: string
  color: string
  defaultBehavior: 'auto' | 'manual' | 'ignore'
}

export const mockVideos: MockVideo[] = [
  {
    videoId: 'v001',
    title: 'But what is a Neural Network? | Deep learning, chapter 1',
    channel: '3Blue1Brown',
    durationSec: 1204,
    category: 'math',
    thumbnailGradient: 'from-blue-900 via-indigo-800 to-blue-600',
    aiScore: 97,
    aiReasoning: 'Exceptional visual explanation of neural networks with mathematically rigorous yet accessible content — no hype, pure clarity.',
    saved: true,
    seen: true,
  },
  {
    videoId: 'v002',
    title: 'The Ancient Philosophy Behind Stoicism — Marcus Aurelius Explained',
    channel: 'Kurzgesagt',
    durationSec: 892,
    category: 'philosophy',
    thumbnailGradient: 'from-amber-700 via-orange-600 to-yellow-500',
    aiScore: 91,
    aiReasoning: 'Dense historical philosophy made digestible with Kurzgesagt\'s signature visual storytelling — no clickbait framing.',
    saved: false,
    seen: true,
  },
  {
    videoId: 'v003',
    title: 'The Banach–Tarski Paradox',
    channel: 'Vsauce',
    durationSec: 1437,
    category: 'math',
    thumbnailGradient: 'from-slate-800 via-slate-700 to-zinc-600',
    aiScore: 94,
    aiReasoning: 'Deep dive into set theory and infinity that respects viewer intelligence — rare educational depth from a major channel.',
    saved: true,
    seen: false,
  },
  {
    videoId: 'v004',
    title: 'SpongeBob\'s Economy: Why Bikini Bottom Is Doomed',
    channel: 'SpongeLore',
    durationSec: 742,
    category: 'society',
    thumbnailGradient: 'from-cyan-500 via-teal-400 to-emerald-500',
    aiScore: 78,
    aiReasoning: 'Surprisingly rigorous economic analysis using SpongeBob as accessible framing — light but substantive.',
    saved: false,
    seen: false,
  },
  {
    videoId: 'v005',
    title: 'Calculus in 20 Minutes — The Intuition Behind Derivatives',
    channel: 'Khan Academy',
    durationSec: 1223,
    category: 'math',
    thumbnailGradient: 'from-green-700 via-emerald-600 to-teal-500',
    aiScore: 89,
    aiReasoning: 'Crystal-clear calculus fundamentals with no filler — pedagogically precise and genuinely efficient.',
    saved: false,
    seen: true,
  },
  {
    videoId: 'v006',
    title: 'This Equation Will Change How You See the World',
    channel: '3Blue1Brown',
    durationSec: 1552,
    category: 'math',
    thumbnailGradient: 'from-violet-900 via-purple-700 to-indigo-600',
    aiScore: 96,
    aiReasoning: 'Euler\'s formula explored with geometric elegance — high mathematical rigor packaged as pure visual poetry.',
    saved: true,
    seen: false,
  },
  {
    videoId: 'v007',
    title: 'The Fermi Paradox — Where Are All The Aliens?',
    channel: 'Kurzgesagt',
    durationSec: 987,
    category: 'science',
    thumbnailGradient: 'from-sky-900 via-blue-800 to-indigo-900',
    aiScore: 88,
    aiReasoning: 'Balanced, well-sourced exploration of the Fermi paradox without sensationalism — rare for this topic.',
    saved: false,
    seen: true,
  },
  {
    videoId: 'v008',
    title: 'The Problem with Time Zones',
    channel: 'CGP Grey',
    durationSec: 606,
    category: 'society',
    thumbnailGradient: 'from-rose-900 via-red-800 to-orange-700',
    aiScore: 85,
    aiReasoning: 'Concise, genuinely informative breakdown of a surprisingly complex topic — no padding, excellent pacing.',
    saved: false,
    seen: false,
  },
  {
    videoId: 'v009',
    title: 'What IS Color? — The Physics and Philosophy of Perception',
    channel: 'Vsauce',
    durationSec: 1320,
    category: 'science',
    thumbnailGradient: 'from-fuchsia-800 via-pink-700 to-rose-600',
    aiScore: 92,
    aiReasoning: 'Meanders productively through optics, neuroscience, and philosophy — long but every minute earns its place.',
    saved: true,
    seen: false,
  },
  {
    videoId: 'v010',
    title: 'Why Every Map You\'ve Seen Is Wrong',
    channel: 'CGP Grey',
    durationSec: 518,
    category: 'history',
    thumbnailGradient: 'from-lime-700 via-green-700 to-teal-800',
    aiScore: 83,
    aiReasoning: 'Sharp, witty cartography lesson — respects the viewer\'s ability to handle nuance.',
    saved: false,
    seen: true,
  },
  {
    videoId: 'v011',
    title: 'Spongebob and the Collapse of the Service Economy',
    channel: 'SpongeLore',
    durationSec: 834,
    category: 'society',
    thumbnailGradient: 'from-yellow-600 via-amber-500 to-orange-400',
    aiScore: 76,
    aiReasoning: 'Creative sociological lens applied to animation — modest educational value but well-argued and fun.',
    saved: false,
    seen: false,
  },
  {
    videoId: 'v012',
    title: 'How Big Is Infinity? — Cantor\'s Diagonalization',
    channel: '3Blue1Brown',
    durationSec: 1098,
    category: 'math',
    thumbnailGradient: 'from-blue-800 via-cyan-700 to-teal-600',
    aiScore: 98,
    aiReasoning: 'Cantor\'s theorem explained through animation that makes the uncountable feel viscerally real — a masterclass.',
    saved: true,
    seen: false,
  },
  {
    videoId: 'v013',
    title: 'Atomic Habits — The Science Behind Behavior Change',
    channel: 'Kurzgesagt',
    durationSec: 776,
    category: 'science',
    thumbnailGradient: 'from-orange-800 via-red-700 to-rose-700',
    aiScore: 80,
    aiReasoning: 'Well-cited neuroscience of habit formation — slightly promotional but the core content is solid.',
    saved: false,
    seen: true,
  },
  {
    videoId: 'v014',
    title: 'Thermodynamics and the Arrow of Time',
    channel: 'Vsauce',
    durationSec: 1621,
    category: 'science',
    thumbnailGradient: 'from-zinc-800 via-neutral-700 to-stone-600',
    aiScore: 93,
    aiReasoning: 'Entropy explained without dumbing down — connects physics to philosophy with unusual intellectual honesty.',
    saved: false,
    seen: false,
  },
  {
    videoId: 'v015',
    title: 'Linear Algebra and Why Matrices Make Sense',
    channel: 'Khan Academy',
    durationSec: 1402,
    category: 'math',
    thumbnailGradient: 'from-emerald-800 via-green-700 to-lime-600',
    aiScore: 87,
    aiReasoning: 'Geometric intuition for matrix operations done properly — fills a gap most textbooks ignore.',
    saved: true,
    seen: true,
  },
]

export const mockChannels: MockChannel[] = [
  {
    id: 'ch001',
    name: '3Blue1Brown',
    handle: '@3blue1brown',
    totalVideos: 142,
    approved: 138,
    rejected: 4,
    diskMB: 2340,
    category: 'math',
    accentColor: '#3B82F6',
  },
  {
    id: 'ch002',
    name: 'SpongeLore',
    handle: '@spongelore',
    totalVideos: 67,
    approved: 51,
    rejected: 16,
    diskMB: 890,
    category: 'society',
    accentColor: '#06B6D4',
  },
  {
    id: 'ch003',
    name: 'Kurzgesagt',
    handle: '@kurzgesagt',
    totalVideos: 198,
    approved: 182,
    rejected: 16,
    diskMB: 3120,
    category: 'science',
    accentColor: '#F59E0B',
  },
  {
    id: 'ch004',
    name: 'Khan Academy',
    handle: '@khanacademy',
    totalVideos: 1204,
    approved: 1198,
    rejected: 6,
    diskMB: 18200,
    category: 'math',
    accentColor: '#10B981',
  },
  {
    id: 'ch005',
    name: 'CGP Grey',
    handle: '@cgpgrey',
    totalVideos: 89,
    approved: 86,
    rejected: 3,
    diskMB: 1560,
    category: 'society',
    accentColor: '#EF4444',
  },
  {
    id: 'ch006',
    name: 'Vsauce',
    handle: '@vsauce',
    totalVideos: 234,
    approved: 201,
    rejected: 33,
    diskMB: 4780,
    category: 'science',
    accentColor: '#8B5CF6',
  },
]

export const sponsorBlockCategories: SponsorBlockCategory[] = [
  { key: 'sponsor', label: 'Sponsor', labelDe: 'Sponsor', color: '#00D400', defaultBehavior: 'auto' },
  { key: 'selfpromo', label: 'Self-Promotion', labelDe: 'Eigenwerbung', color: '#FFFF00', defaultBehavior: 'auto' },
  { key: 'interaction', label: 'Interaction Reminder', labelDe: 'Interaktions-Erinnerung', color: '#CC00FF', defaultBehavior: 'ignore' },
  { key: 'intro', label: 'Intro', labelDe: 'Intro', color: '#00FFFF', defaultBehavior: 'auto' },
  { key: 'outro', label: 'Outro/Credits', labelDe: 'Outro/Abspann', color: '#0202ED', defaultBehavior: 'auto' },
  { key: 'preview', label: 'Preview/Recap', labelDe: 'Vorschau/Rückblick', color: '#008FD6', defaultBehavior: 'manual' },
  { key: 'hook', label: 'Hook/Teaser', labelDe: 'Hook/Teaser', color: '#FF9900', defaultBehavior: 'ignore' },
  { key: 'filler', label: 'Filler Tangent', labelDe: 'Filler/Abschweifung', color: '#7300FF', defaultBehavior: 'manual' },
  { key: 'music_offtopic', label: 'Non-Music Off-Topic', labelDe: 'Musik-Abschnitt', color: '#FF9900', defaultBehavior: 'ignore' },
  { key: 'poi_highlight', label: 'Highlight', labelDe: 'Highlight', color: '#FF1684', defaultBehavior: 'ignore' },
]

export const sponsorBlockStats = {
  totalSkipped: 171,
  timeSaved: '2h 56min',
  topCategory: 'sponsor',
}
