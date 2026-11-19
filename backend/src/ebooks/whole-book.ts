import { categorize, kindOf } from "./categories.js";
import type { RawBook } from "./persist.js";

/** Mindestlänge eines ganzen Werks in Wörtern, je Gattung. */
export const MIN_WORDS: Record<"prosa" | "drama" | "lyrik", number> = { prosa: 8000, drama: 5000, lyrik: 3000 };

const SNIPPET = /leseprobe|auszug|excerpt|sample chapter|probekapitel/i;
const PERIODICAL = /periodicals|zeitschrift|zeitung|magazine/i;
/** Im Titel nur eindeutige Heft-Angaben — „Das blaue Heft“ oder „Journal einer Reise“ sind Bücher. */
const PERIODICAL_TITLE = /jahrgang|\bheft\s*\d|\bnr\.\s*\d/i;

/** Nur ganze Bücher: keine Leseproben, Unterseiten, Zeitschriften oder zu kurzen Texte. */
export function isWholeBook(raw: RawBook): { ok: true } | { ok: false; reason: string } {
  if (SNIPPET.test(raw.title)) return { ok: false, reason: "Leseprobe oder Auszug" };
  if (raw.source === "wikisource" && raw.title.includes("/")) return { ok: false, reason: "Teil eines Werks" };
  if (raw.subjects.some((s) => PERIODICAL.test(s)) || PERIODICAL_TITLE.test(raw.title))
    return { ok: false, reason: "Zeitschrift" };
  if (raw.wordCount !== undefined) {
    const min = MIN_WORDS[kindOf(categorize(raw.source, raw.subjects))];
    if (raw.wordCount < min) return { ok: false, reason: `zu kurz (${raw.wordCount} Wörter)` };
  }
  return { ok: true };
}
