import { mangadexAdapter } from "./mangadex.js";
import { onePieceTubeAdapter } from "./onepiece-tube.js";
import type { MangaSourceAdapter } from "./types.js";

export const adapters: MangaSourceAdapter[] = [onePieceTubeAdapter, mangadexAdapter];

export function getAdapter(id: string): MangaSourceAdapter | undefined {
  return adapters.find((a) => a.id === id);
}
