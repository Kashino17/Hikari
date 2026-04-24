'use client'

import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import { sponsorBlockCategories } from './mock-data'

export type SBBehavior = 'auto' | 'manual' | 'ignore'
export type ThemeMode = 'dark' | 'light'

interface HikariStore {
  // Theme
  theme: ThemeMode
  setTheme: (t: ThemeMode) => void

  // Subscribed channels (IDs)
  subscribedChannelIds: string[]
  subscribe: (id: string) => void
  unsubscribe: (id: string) => void

  // Saved videos (IDs)
  savedVideoIds: string[]
  toggleSaved: (id: string) => void

  // SponsorBlock behaviors
  sbBehaviors: Record<string, SBBehavior>
  setSbBehavior: (key: string, b: SBBehavior) => void

  // Settings
  backendUrl: string
  setBackendUrl: (url: string) => void
  dailyBudget: number
  setDailyBudget: (n: number) => void
  llmProvider: string
  setLlmProvider: (p: string) => void
  scoringPrompt: string
  setScoringPrompt: (p: string) => void
}

const DEFAULT_PROMPT = `Du bist Hikaris KI-Bewerter für Kurzvideos. Bewerte jeden Clip von 0–100.

Hohe Punktzahl (80–100): Lehrt etwas Substantielles, respektiert die Intelligenz des Zuschauers, kein Clickbait, klare Aussage, gute Informationsdichte, keine Manipulation.

Niedrige Punktzahl (0–40): Reine Unterhaltung ohne Lernwert, irreführende Titel, emotionale Manipulation, übertriebene Reaktionen, Padding/Filler.

Ausgabe als JSON: { "score": <0-100>, "category": "<math|science|tech|philosophy|society|history|art>", "reasoning": "<1 Satz auf Deutsch>" }`.trim()

export const useHikariStore = create<HikariStore>()(
  persist(
    (set) => ({
      theme: 'dark',
      setTheme: (t) => set({ theme: t }),

      subscribedChannelIds: ['ch001', 'ch003', 'ch006', 'ch002'],
      subscribe: (id) => set((s) => ({ subscribedChannelIds: [...s.subscribedChannelIds, id] })),
      unsubscribe: (id) => set((s) => ({ subscribedChannelIds: s.subscribedChannelIds.filter((x) => x !== id) })),

      savedVideoIds: ['v001', 'v003', 'v006', 'v009', 'v012', 'v015'],
      toggleSaved: (id) =>
        set((s) => ({
          savedVideoIds: s.savedVideoIds.includes(id)
            ? s.savedVideoIds.filter((x) => x !== id)
            : [...s.savedVideoIds, id],
        })),

      sbBehaviors: Object.fromEntries(sponsorBlockCategories.map((c) => [c.key, c.defaultBehavior])),
      setSbBehavior: (key, b) => set((s) => ({ sbBehaviors: { ...s.sbBehaviors, [key]: b } })),

      backendUrl: 'http://kadir-laptop.tail1234.ts.net:3000',
      setBackendUrl: (url) => set({ backendUrl: url }),
      dailyBudget: 15,
      setDailyBudget: (n) => set({ dailyBudget: n }),
      llmProvider: 'lmstudio',
      setLlmProvider: (p) => set({ llmProvider: p }),
      scoringPrompt: DEFAULT_PROMPT,
      setScoringPrompt: (p) => set({ scoringPrompt: p }),
    }),
    { name: 'hikari-dashboard-demo' }
  )
)
