export type Category =
  | "science"
  | "tech"
  | "philosophy"
  | "history"
  | "math"
  | "art"
  | "language"
  | "society"
  | "other";

export interface Score {
  overallScore: number;
  category: Category;
  clickbaitRisk: number;
  educationalValue: number;
  emotionalManipulation: number;
  reasoning: string;
}

export interface ScoredVideo {
  score: Score;
  modelUsed: string;
}

export interface ScoreInput {
  title: string;
  description: string;
  transcript: string | null;
  durationSeconds: number;
  systemPrompt: string;
}

export interface Scorer {
  readonly name: string;
  score(input: ScoreInput): Promise<ScoredVideo>;
}
