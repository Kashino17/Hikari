import { type MangaSourceAdapter } from "./types.js";
import { onePieceTubeAdapter } from "./onepiece-tube.js";

export const adapters: MangaSourceAdapter[] = [onePieceTubeAdapter];

export function getAdapter(id: string): MangaSourceAdapter | undefined {
  return adapters.find((a) => a.id === id);
}
