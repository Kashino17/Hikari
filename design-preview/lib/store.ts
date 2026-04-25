'use client'

import { create } from 'zustand'
import { persist } from 'zustand/middleware'
import { sponsorBlockCategories } from './mock-data'
import { DEFAULT_FILTER, type FilterConfig } from './prompt-builder'

export type SBBehavior = 'auto' | 'manual' | 'ignore'
export type FeedFilter = 'all' | 'saved' | 'rejected'

interface HikariStore {
  // Subscribed channels
  subscribedChannelIds: string[]
  subscribe: (id: string) => void
  unsubscribe: (id: string) => void

  // Saved videos
  savedVideoIds: string[]
  toggleSaved: (id: string) => void

  // Feed filter (replaces /saved and /rejected routes)
  feedFilter: FeedFilter
  setFeedFilter: (f: FeedFilter) => void

  // Filter config — drives the generated prompt
  filter: FilterConfig
  updateFilter: (patch: Partial<FilterConfig>) => void
  resetFilter: () => void

  // Optional manual override — when set, this overrides the auto-built prompt
  promptOverride: string | null
  setPromptOverride: (p: string | null) => void

  // System
  backendUrl: string
  setBackendUrl: (url: string) => void
  dailyBudget: number
  setDailyBudget: (n: number) => void
  llmProvider: string
  setLlmProvider: (p: string) => void

  // SponsorBlock
  sbBehaviors: Record<string, SBBehavior>
  setSbBehavior: (key: string, b: SBBehavior) => void
}

export const useHikariStore = create<HikariStore>()(
  persist(
    (set) => ({
      subscribedChannelIds: ['ch001', 'ch003', 'ch006', 'ch002'],
      subscribe: (id) =>
        set((s) => ({ subscribedChannelIds: [...s.subscribedChannelIds, id] })),
      unsubscribe: (id) =>
        set((s) => ({
          subscribedChannelIds: s.subscribedChannelIds.filter((x) => x !== id),
        })),

      savedVideoIds: ['v001', 'v003', 'v006', 'v009', 'v012', 'v015'],
      toggleSaved: (id) =>
        set((s) => ({
          savedVideoIds: s.savedVideoIds.includes(id)
            ? s.savedVideoIds.filter((x) => x !== id)
            : [...s.savedVideoIds, id],
        })),

      feedFilter: 'all',
      setFeedFilter: (f) => set({ feedFilter: f }),

      filter: DEFAULT_FILTER,
      updateFilter: (patch) => set((s) => ({ filter: { ...s.filter, ...patch } })),
      resetFilter: () => set({ filter: DEFAULT_FILTER, promptOverride: null }),

      promptOverride: null,
      setPromptOverride: (p) => set({ promptOverride: p }),

      backendUrl: 'http://kadir-laptop.tail1234.ts.net:3000',
      setBackendUrl: (url) => set({ backendUrl: url }),
      dailyBudget: 15,
      setDailyBudget: (n) => set({ dailyBudget: n }),
      llmProvider: 'lmstudio',
      setLlmProvider: (p) => set({ llmProvider: p }),

      sbBehaviors: Object.fromEntries(
        sponsorBlockCategories.map((c) => [c.key, c.defaultBehavior]),
      ),
      setSbBehavior: (key, b) =>
        set((s) => ({ sbBehaviors: { ...s.sbBehaviors, [key]: b } })),
    }),
    { name: 'hikari-minimal-v2' },
  ),
)
