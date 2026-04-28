import type { Category } from "../scorer/types.js";

export type CategoryWeights = Record<Category, number>;

export interface DiscoverySettings {
  discoveryRatio: number;
  qualityThreshold: number;
  categoryWeights: CategoryWeights;
  updatedAt: number;
}

export interface DiscoverySettingsUpdate {
  discoveryRatio?: number;
  qualityThreshold?: number;
  categoryWeights?: Partial<CategoryWeights>;
}

export interface ChannelMatchScore {
  channelId: string;
  calculatedScore: number;
  lastUpdated: number;
}

export interface CategoryPreference {
  category: Category;
  weight: number;
  updatedAt: number;
}
