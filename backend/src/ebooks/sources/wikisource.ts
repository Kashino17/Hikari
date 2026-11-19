import type { RawBook } from "../persist.js";
import { type EbookSource, SourceBlocked, type SourceContext } from "./types.js";

const API = "https://de.wikisource.org/w/api.php";

/** Gattungs-Kategorien, aus denen Werke kommen (Namen wie auf de.wikisource). */
export const WIKISOURCE_CATEGORIES = [
  "Roman",
  "Erzählung",
  "Novelle",
  "Märchen",
  "Sage",
  "Kriminalliteratur",
  "Drama",
  "Lustspiel",
  "Trauerspiel",
  "Gedichtsammlung",
  "Reisebericht",
  "Autobiographie",
  "Biographie",
  "Kinder- und Jugendliteratur",
  "Abenteuerliteratur",
  "Science-Fiction",
  "Humoreske",
];

const GOOD_STATE = /^(fertig|korrigiert)$/i;
/** Ab so vielen Unterseiten-Links gilt eine Hauptseite als Inhaltsverzeichnis eines ganzen Werks. */
const MIN_SUBPAGES = 3;

interface PageInfo {
  title: string;
  touched?: string | undefined;
  categories: string[];
}
interface Parsed {
  ok: boolean;
  authors: string[];
  year?: number | undefined;
  description?: string | undefined;
  subpages: number;
  words: number;
}

function url(params: Record<string, string>): string {
  return `${API}?${new URLSearchParams({ format: "json", formatversion: "1", ...params })}`;
}

interface WsPage {
  ns?: number;
  title: string;
  touched?: string;
  categories?: { title: string }[];
  revisions?: { slots?: { main?: { "*"?: string } }; "*"?: string }[];
}
interface WsResponse {
  query?: { pages?: Record<string, WsPage> };
  continue?: Record<string, unknown>;
}

async function json(ctx: SourceContext, u: string): Promise<WsResponse> {
  // Wikimedia drosselt Abrufe ohne Kontaktangabe hart (429, x-envoy-ratelimited). Dann diesen Lauf
  // überspringen statt stundenlang zu warten — der nächste Abgleich versucht es wieder.
  let r: { status: number; body: Buffer };
  try {
    r = await ctx.http.get(u, { accept: "application/json", tries: 1 });
  } catch (err) {
    if (/HTTP 429/.test((err as Error).message)) throw new SourceBlocked("wikisource", 429);
    throw err;
  }
  if (r.status !== 200) throw new Error(`wikisource: HTTP ${r.status}`);
  return JSON.parse(r.body.toString("utf8"));
}

/** Felder der Vorlage {{Textdaten …}}. */
export function textdaten(wikitext: string): Record<string, string> {
  const start = wikitext.indexOf("{{Textdaten");
  if (start < 0) return {};
  const body = wikitext.slice(start, wikitext.indexOf("\n}}", start) + 1 || undefined);
  const out: Record<string, string> = {};
  for (const m of body.matchAll(/^\|\s*([A-ZÄÖÜ]+)\s*=\s*(.*)$/gm))
    if (m[1]) out[m[1]] = (m[2] ?? "").trim();
  return out;
}

const unlink = (s: string) =>
  s
    .replace(/\[\[(?:[^|\]]*\|)?([^\]]+)\]\]/g, "$1")
    .replace(/<[^>]+>|'''?|\{\{[^}]*\}\}/g, "")
    .trim();

export function parseWork(title: string, wikitext: string): Parsed {
  const td = textdaten(wikitext);
  const state = (td.BEARBEITUNGSSTAND ?? "").trim();
  const authors = td.AUTOR
    ? unlink(td.AUTOR)
        .split(/\s*(?:,|;| und )\s*/)
        .filter(Boolean)
    : [];
  const year = Number(/\d{4}/.exec(td.ERSCHEINUNGSJAHR ?? "")?.[0]) || undefined;
  const esc = title.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const subpages = new Set(
    [...wikitext.matchAll(new RegExp(`\\[\\[(?:/[^\\]|]+|${esc}/[^\\]|]+)`, "g"))].map((m) => m[0]),
  ).size;
  const text = wikitext
    .replace(/\{\{Textdaten[\s\S]*?\n\}\}/, "")
    .replace(/\{\{[^}]*\}\}|<[^>]+>|\[\[[^\]]*\]\]/g, " ");
  const words = text.split(/\s+/).filter((w) => /\p{L}/u.test(w)).length;
  return {
    ok: GOOD_STATE.test(state),
    authors,
    year,
    description: td.KURZBESCHREIBUNG ? unlink(td.KURZBESCHREIBUNG) || undefined : undefined,
    subpages,
    words,
  };
}

async function membersOf(ctx: SourceContext, category: string): Promise<PageInfo[]> {
  const out: PageInfo[] = [];
  let cont: Record<string, string> = {};
  for (let guard = 0; guard < 100; guard++) {
    const data = await json(
      ctx,
      url({
        action: "query",
        generator: "categorymembers",
        gcmtitle: `Kategorie:${category}`,
        gcmnamespace: "0",
        gcmlimit: "500",
        prop: "info|categories",
        cllimit: "max",
        ...cont,
      }),
    );
    for (const p of Object.values(data?.query?.pages ?? {})) {
      if (p.ns !== 0 || String(p.title).includes("/")) continue;
      out.push({
        title: p.title,
        touched: p.touched,
        categories: (p.categories ?? []).map((c) => String(c.title).replace(/^Kategorie:/, "")),
      });
    }
    if (!data?.continue) break;
    cont = Object.fromEntries(Object.entries(data.continue).map(([k, v]) => [k, String(v)]));
  }
  return out;
}

export const wikisourceSource: EbookSource = {
  id: "wikisource",
  name: "Wikisource",
  async listCatalog(ctx) {
    const pages = new Map<string, PageInfo>();
    for (const cat of WIKISOURCE_CATEGORIES) {
      try {
        for (const p of await membersOf(ctx, cat)) {
          const prev = pages.get(p.title);
          pages.set(
            p.title,
            prev ? { ...p, categories: [...new Set([...prev.categories, ...p.categories])] } : p,
          );
        }
      } catch (err) {
        if (err instanceof SourceBlocked) throw err;
        ctx.log(`wikisource: Kategorie ${cat} — ${(err as Error).message}`);
      }
    }
    const candidates = [...pages.values()].filter(
      (p) => !p.categories.some((c) => /unvollständig|unkorrigiert|fragment/i.test(c)),
    );

    // Wikitext nur für neue oder geänderte Seiten holen, 50 auf einmal.
    const key = (p: PageInfo) => `ws:${p.title}`;
    const need = candidates.filter(
      (p) => ctx.cache.get<{ touched?: string }>(key(p))?.touched !== p.touched,
    );
    for (let i = 0; i < need.length; i += 50) {
      const batch = need.slice(i, i + 50);
      try {
        const data = await json(
          ctx,
          url({
            action: "query",
            prop: "revisions",
            rvprop: "content",
            rvslots: "main",
            titles: batch.map((p) => p.title).join("|"),
          }),
        );
        for (const p of Object.values(data?.query?.pages ?? {})) {
          const rev = p.revisions?.[0];
          const text: string = rev?.slots?.main?.["*"] ?? rev?.["*"] ?? "";
          const info = pages.get(p.title);
          if (!info) continue;
          ctx.cache.set(key(info), { touched: info.touched, parsed: parseWork(p.title, text) });
        }
      } catch (err) {
        if (err instanceof SourceBlocked) throw err;
        ctx.log(`wikisource: Inhalte — ${(err as Error).message}`);
      }
    }

    const out: RawBook[] = [];
    for (const p of candidates) {
      const parsed = ctx.cache.get<{ parsed: Parsed }>(key(p))?.parsed;
      if (!parsed?.ok) continue;
      const wholeByToc = parsed.subpages >= MIN_SUBPAGES;
      out.push({
        source: "wikisource",
        sourceId: p.title,
        title: p.title,
        authors: parsed.authors,
        year: parsed.year,
        description: parsed.description,
        language: "de",
        subjects: p.categories,
        format: "epub",
        fileUrl: `https://ws-export.wmcloud.org/?lang=de&format=epub&page=${encodeURIComponent(p.title)}`,
        sourceUrl: `https://de.wikisource.org/wiki/${encodeURIComponent(p.title.replace(/ /g, "_")).replace(/%2F/g, "/")}`,
        wordCount: wholeByToc ? undefined : parsed.words,
      });
    }
    return out;
  },
};
