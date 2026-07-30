import { type MangaSourceAdapter } from "./types.ts";
import { onePieceTubeAdapter } from "./onepiece-tube.ts";

export const adapters: MangaSourceAdapter[] = [onePieceTubeAdapter];

export function getAdapter(id: string): MangaSourceAdapter | undefined {
  return adapters.find((a) => a.id === id);
}
