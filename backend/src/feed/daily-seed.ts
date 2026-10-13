/**
 * Kleiner deterministischer Zufallsgenerator (mulberry32). Gleicher Startwert →
 * gleiche Folge, damit sich Mixe in Tests reproduzieren lassen.
 */
export function mulberry32(seed: number): () => number {
  let a = seed >>> 0;
  return () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = Math.imul(a ^ (a >>> 15), 1 | a);
    t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/** Startwert aus dem Kalenderdatum (YYYY-MM-DD → YYYYMMDD): derselbe Tag, derselbe Mix. */
export function dailySeed(mixDate: string): number {
  return Number(mixDate.replaceAll("-", ""));
}

/** Fisher-Yates mit festem Startwert: gleiche Eingabe + gleicher Tag → gleiche Reihenfolge. */
export function seededShuffle<T>(items: T[], seed: number): T[] {
  const out = [...items];
  const random = mulberry32(seed);
  for (let i = out.length - 1; i > 0; i--) {
    const j = Math.floor(random() * (i + 1));
    const a = out[i] as T;
    out[i] = out[j] as T;
    out[j] = a;
  }
  return out;
}
