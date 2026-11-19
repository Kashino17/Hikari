/**
 * Lesestand eines Buchs — auf dem Handy offline geändert, auf dem Mac gesichert.
 * Zusammenführen ohne Verlust: je Feld gewinnt die neuere Änderung, Lesezeichen und
 * Markierungen werden über ihre ID vereinigt, Gelöschtes bleibt als Grabstein.
 * Dieselben Regeln (und Testfälle, tests/fixtures/ebooks/merge-cases.json) gelten in der App.
 */
export interface Stamped<T> {
  value: T;
  at: number;
}

export interface Mark {
  id: string;
  cfi: string;
  label?: string;
  text?: string;
  color?: "gelb" | "gruen" | "blau" | "rosa";
  at: number;
  deleted?: boolean;
}

export interface BookState {
  position?: Stamped<{ cfi: string; percent: number; page?: number }>;
  saved?: Stamped<boolean>;
  finished?: Stamped<boolean>;
  bookmarks: Mark[];
  highlights: Mark[];
  lastOpenedAt?: number;
}

/** Bei gleicher Zeit gewinnt b (die Seite, die gerade hereinkommt). */
function newer<T extends { at: number }>(a: T | undefined, b: T | undefined): T | undefined {
  if (!a) return b;
  if (!b) return a;
  return b.at >= a.at ? b : a;
}

function mergeMarks(a: Mark[] = [], b: Mark[] = []): Mark[] {
  const byId = new Map<string, Mark>();
  for (const m of a) byId.set(m.id, m);
  for (const m of b) byId.set(m.id, newer(byId.get(m.id), m) as Mark);
  return [...byId.values()].sort((x, y) => x.at - y.at || x.id.localeCompare(y.id));
}

export function mergeState(a: BookState, b: BookState): BookState {
  const out: BookState = {
    bookmarks: mergeMarks(a.bookmarks, b.bookmarks),
    highlights: mergeMarks(a.highlights, b.highlights),
  };
  const position = newer(a.position, b.position);
  const saved = newer(a.saved, b.saved);
  const finished = newer(a.finished, b.finished);
  if (position) out.position = position;
  if (saved) out.saved = saved;
  if (finished) out.finished = finished;
  const opened = Math.max(a.lastOpenedAt ?? -1, b.lastOpenedAt ?? -1);
  if (opened >= 0) out.lastOpenedAt = opened;
  return out;
}
