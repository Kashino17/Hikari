// Tagesbericht im Discover-Stil (Kadir 20.11.2026): eigene Stichwörter → Bing News → jeden Artikel
// komplett laden → nur frei lesbare Volltexte behalten → in Bausteine zerlegen (Text, Bilder, Videos),
// damit die App den ganzen Artikel selbst anzeigt. Bezahlschranken und Anrisse fliegen raus.
import { createHash } from "node:crypto";
import { mkdir, readFile, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { Readability } from "@mozilla/readability";
import { XMLParser } from "fast-xml-parser";
import { parseHTML } from "linkedom";

export type Block =
  | { t: "p"; text: string }
  | { t: "h"; text: string }
  | { t: "img"; src: string; caption: string; lead?: boolean }
  | { t: "video"; type: "youtube" | "vimeo"; id: string; caption: string }
  | { t: "video"; type: "file"; src: string; caption: string }
  | { t: "quote"; text: string }
  | { t: "list"; ordered: boolean; items: string[] };

export interface Article {
  id: string;
  url: string;
  ok: boolean;
  reason: string | null;
  title: string;
  byline: string;
  site: string;
  favicon: string | null;
  lang: string;
  excerpt: string;
  readMin: number;
  words: number;
  blocks: Block[];
}

export interface DiscoverItem {
  id: string;
  topic: string;
  title: string;
  site: string;
  favicon: string | null;
  teaser: string;
  image: string | null;
  images: number;
  videos: number;
  readMin: number;
  at: number;
  lang: string;
}

export interface Discover {
  topics: string[];
  items: DiscoverItem[];
  stats: { candidates: number; kept: number; dropped: Record<string, number> };
  builtAt: number;
}

const UA =
  "Mozilla/5.0 (Macintosh; Intel Mac OS X 14_0) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0 Safari/537.36";
const PER_QUERY = 10;
const MIN_CHARS = 1200;
const FEED_TTL = 30 * 60 * 1000;
const MAX_AGE = 4 * 24 * 3600 * 1000;
export const DEFAULT_TOPICS = [
  "OpenAI",
  "ChatGPT",
  "Claude",
  "Anthropic",
  "KI-Agenten China",
  "Künstliche Intelligenz",
  "Gaming",
  "Politik",
];

const JUNK_TITLE =
  /(test (und|&) vergleich|bestenliste|kaufen|kaufberatung|deals?\b|gutschein|rabatt|angebot|im test 20\d\d|vpn f(ü|ue)r|liveticker|quiz)/i;
const PAY_PHRASES = [
  /jetzt (weiter)?lesen mit/i,
  /dieser artikel ist (exklusiv|nur) f(ü|ue)r/i,
  /\bmit (spiegel|zeit|faz|sz|welt|heise|golem|t3n)\s?(\+|plus|pro)\b/i,
  /abonnent(en|innen)? (lesen|weiterlesen)/i,
  /artikel freischalten/i,
  /subscribe to (continue|read)/i,
  /to continue reading/i,
  /premium[- ]inhalt/i,
  /bereits abonnent/i,
  /f(ü|ue)r abonnenten/i,
];
const PLACEHOLDER_IMG =
  /logo|default|fallback|placeholder|og[-_]?image|social[-_]?share|\/fb\.(png|jpg)|share[-_]?image|favicon|icon/i;
const NOISE_P =
  /^(more from|mehr zum thema|mehr dazu|lesen sie auch|auch interessant|related|weitere artikel|anzeige|werbung|read more|see also|newsletter|folgen sie|follow us|teilen|share)\b/i;
const NOISE_IMG = /avatar|gravatar|author|autor|profile|logo|icon|[?&](w|width)=\d{2,3}(&|$)/i;

// Kleinste gemeinsame DOM-Form, die linkedom liefert — reicht für unsere Zugriffe.
interface El {
  tagName: string;
  textContent: string | null;
  children: ArrayLike<El> & Iterable<El>;
  getAttribute(name: string): string | null;
  querySelector(sel: string): El | null;
  querySelectorAll(sel: string): ArrayLike<El> & Iterable<El>;
}
interface Doc {
  documentElement: El | null;
  body: El;
  querySelector(sel: string): El | null;
  querySelectorAll(sel: string): ArrayLike<El> & Iterable<El>;
}

export const hashId = (s: string): string =>
  createHash("sha1").update(s).digest("hex").slice(0, 16);
const clean = (s: string): string => s.replace(/\s+/g, " ").trim();
function abs(u: string | null | undefined, base: string): string | null {
  if (!u) return null;
  try {
    return new URL(u, base).href;
  } catch {
    return null;
  }
}

// ── reine Hilfen (getestet) ─────────────────────────────────────────────────

/** »Titel | World News« und »Titel - CNET« → »Titel«. */
export function stripSite(title: string, site: string): string {
  let t = title.replace(/\s+\|\s+[^|]{2,30}$/, "").trim();
  t = t
    .replace(/\s+[-–—]\s+([^-–—]{2,40})$/, (m, tail: string) =>
      site && tail.toLowerCase().includes(site.toLowerCase().slice(0, 4)) ? "" : m,
    )
    .trim();
  return t.length >= 15 ? t : title;
}

export function videoFrom(
  src: string,
  base: string,
): { type: "youtube" | "vimeo"; id: string } | { type: "file"; src: string } | null {
  const u = abs(src, base);
  if (!u) return null;
  const yt = u.match(
    /(?:youtube(?:-nocookie)?\.com\/(?:embed|watch\?v=|shorts)\/?|youtu\.be\/)([\w-]{11})/,
  );
  if (yt?.[1]) return { type: "youtube", id: yt[1] };
  const vm = u.match(/player\.vimeo\.com\/video\/(\d+)/);
  if (vm?.[1]) return { type: "vimeo", id: vm[1] };
  if (/\.(mp4|webm|m3u8)(\?|$)/i.test(u)) return { type: "file", src: u };
  return null;
}

/** Lärm raus: Autoren-Kasten, »Mehr von …«, Profilbilder, alles nach dem letzten richtigen Absatz. */
export function tidy(blocks: Block[]): Block[] {
  const kept = blocks.filter((b) => {
    if (b.t === "p")
      return !NOISE_P.test(b.text) && (b.text.length >= 40 || /[.!?:"“”»«]$/.test(b.text));
    if (b.t === "img") return b.lead === true || !NOISE_IMG.test(b.src);
    return true;
  });
  let last = -1;
  kept.forEach((b, i) => {
    if (b.t === "p" && b.text.length >= 120) last = i;
  });
  return last >= 0 ? kept.slice(0, last + 1) : kept;
}

/** Passt der Artikel zum Stichwort? Ein Wortteil (ab 3 Zeichen) muss in Titel oder Anfang stehen. */
export function matchesTopic(topic: string, head: string): boolean {
  const tokens = topic
    .toLowerCase()
    .split(/[\s-]+/)
    .filter((w) => w.length >= 3);
  const h = head.toLowerCase();
  return tokens.length === 0 || tokens.some((w) => h.includes(w.replace(/e?n$/, "")));
}

/** Neueste zuerst, aber dasselbe Thema möglichst nicht zweimal hintereinander. */
export function diversify<T extends { topic: string; at: number }>(list: T[]): T[] {
  const rest = [...list].sort((a, b) => b.at - a.at);
  const out: T[] = [];
  while (rest.length) {
    const prev = out[out.length - 1]?.topic;
    const k = rest.findIndex((i) => i.topic !== prev);
    const item = rest.splice(k < 0 || k > 3 ? 0 : k, 1)[0];
    if (item) out.push(item);
  }
  return out;
}

// ── Erkennen ────────────────────────────────────────────────────────────────

function paywalled(doc: Doc, html: string): string | null {
  for (const s of doc.querySelectorAll('script[type="application/ld+json"]')) {
    if (/"isAccessibleForFree"\s*:\s*(false|"false"|"False")/.test(s.textContent ?? ""))
      return "isAccessibleForFree";
  }
  const tier = doc.querySelector('meta[property="article:content_tier"]')?.getAttribute("content");
  if (tier && /locked|metered/i.test(tier)) return "content_tier";
  if (
    /class="[^"]*(paywall|piano-|plenigo|laterpay|tp-modal|heiseplus|spiegelplus|abo-teaser)/i.test(
      html,
    )
  )
    return "paywall-class";
  return null;
}

function isArticle(doc: Doc): boolean {
  const ogType = doc.querySelector('meta[property="og:type"]')?.getAttribute("content") ?? "";
  if (/article/i.test(ogType)) return true;
  for (const s of doc.querySelectorAll('script[type="application/ld+json"]')) {
    if (
      /"@type"\s*:\s*\[?\s*"(NewsArticle|Article|BlogPosting|ReportageNewsArticle|AnalysisNewsArticle|TechArticle|LiveBlogPosting)"/.test(
        s.textContent ?? "",
      )
    )
      return true;
  }
  return false;
}

type Video = Extract<Block, { t: "video" }>;
const videoKey = (v: { type: string; id?: string; src?: string }): string => v.id ?? v.src ?? "";

/** YouTube/Vimeo im Artikel, auch hinter Zustimmungs-Platzhaltern (data-Attribute). */
function embeddedVideos(doc: Doc, base: string): Video[] {
  const root = doc.querySelector("article") ?? doc.querySelector("main");
  if (!root) return [];
  const out: Video[] = [];
  for (const el of root.querySelectorAll(
    "iframe, video, lite-youtube, [data-src], [data-youtube-id], [data-video-id], [data-embed-src]",
  )) {
    const id = el.getAttribute("videoid") ?? el.getAttribute("data-youtube-id");
    const v =
      id && /^[\w-]{11}$/.test(id)
        ? ({ type: "youtube", id } as const)
        : videoFrom(
            el.getAttribute("src") ??
              el.getAttribute("data-src") ??
              el.getAttribute("data-embed-src") ??
              el.querySelector("source")?.getAttribute("src") ??
              "",
            base,
          );
    if (v && !out.some((o) => videoKey(o) === videoKey(v)))
      out.push({ t: "video", ...v, caption: "" } as Video);
  }
  return out.slice(0, 4);
}

function pickSrc(img: El, base: string): string | null {
  const cands = [
    img.getAttribute("data-src"),
    img.getAttribute("data-lazy-src"),
    img.getAttribute("data-original"),
    img.getAttribute("src"),
  ];
  const set = img.getAttribute("data-srcset") ?? img.getAttribute("srcset");
  if (set) {
    const best = set
      .split(",")
      .map((p) => p.trim().split(/\s+/))
      .sort((a, b) => Number.parseInt(b[1] ?? "0") - Number.parseInt(a[1] ?? "0"))[0];
    if (best?.[0]) cands.unshift(best[0]);
  }
  const src = cands.find(
    (c) => c && !c.startsWith("data:") && !/pixel|spacer|1x1|blank\.gif/i.test(c),
  );
  return src ? abs(src, base) : null;
}

function toBlocks(contentHtml: string, base: string): Block[] {
  const { document } = parseHTML(
    `<!doctype html><html><body>${contentHtml}</body></html>`,
  ) as unknown as {
    document: Doc;
  };
  const blocks: Block[] = [];
  const seenImg = new Set<string>();
  const pushImg = (src: string | null, caption: string | null) => {
    if (!src || seenImg.has(src)) return;
    seenImg.add(src);
    blocks.push({ t: "img", src, caption: caption ?? "" });
  };
  const pushVideo = (src: string, caption: string) => {
    const v = videoFrom(src, base);
    if (v) blocks.push({ t: "video", ...v, caption } as Video);
  };
  const walk = (node: El) => {
    for (const el of Array.from(node.children)) {
      const tag = el.tagName.toLowerCase();
      if (tag === "p") {
        for (const i of el.querySelectorAll("img"))
          pushImg(pickSrc(i, base), i.getAttribute("alt"));
        const tx = clean(el.textContent ?? "");
        if (tx.length > 1) blocks.push({ t: "p", text: tx });
      } else if (/^h[1-6]$/.test(tag)) {
        const tx = clean(el.textContent ?? "");
        if (tx) blocks.push({ t: "h", text: tx });
      } else if (tag === "figure" || tag === "picture") {
        const img = el.querySelector("img");
        const cap = clean(el.querySelector("figcaption")?.textContent ?? "");
        const fr = el.querySelector("iframe,video,source");
        if (fr) pushVideo(fr.getAttribute("src") ?? fr.getAttribute("data-src") ?? "", cap);
        if (img) pushImg(pickSrc(img, base), cap || img.getAttribute("alt"));
      } else if (tag === "img") {
        pushImg(pickSrc(el, base), el.getAttribute("alt"));
      } else if (tag === "iframe" || tag === "video") {
        pushVideo(
          el.getAttribute("src") ??
            el.getAttribute("data-src") ??
            el.querySelector("source")?.getAttribute("src") ??
            "",
          "",
        );
      } else if (tag === "blockquote") {
        const tx = clean(el.textContent ?? "");
        if (tx) blocks.push({ t: "quote", text: tx });
      } else if (tag === "ul" || tag === "ol") {
        const items = Array.from(el.querySelectorAll("li"))
          .map((li) => clean(li.textContent ?? ""))
          .filter(Boolean);
        if (items.length) blocks.push({ t: "list", ordered: tag === "ol", items });
      } else {
        walk(el);
      }
    }
  };
  walk(document.body);
  return blocks;
}

// ── Motor ───────────────────────────────────────────────────────────────────

interface Candidate {
  topic: string;
  title: string;
  url: string;
  pubDate: string;
  lang: string;
}

const MARKETS = [
  { mkt: "de-DE", lang: "de", sort: false },
  { mkt: "de-DE", lang: "de", sort: true },
  { mkt: "en-US", lang: "en", sort: true },
];

export class NewsDiscover {
  private readonly dir: string;
  private readonly feedCache = new Map<string, { at: number; items: Candidate[] }>();
  private readonly inflight = new Map<string, Promise<Article>>();
  private readonly xml = new XMLParser({ ignoreAttributes: false, attributeNamePrefix: "" });
  private last: Discover | null = null;
  private building: Promise<Discover> | null = null;

  constructor(
    dataDir: string,
    private readonly fetchImpl: typeof fetch = fetch,
  ) {
    this.dir = join(dataDir, "news");
  }

  private async get(url: string, ms = 12000): Promise<{ text: string; url: string }> {
    const r = await this.fetchImpl(url, {
      headers: { "user-agent": UA, "accept-language": "de-DE,de;q=0.9,en;q=0.7" },
      redirect: "follow",
      signal: AbortSignal.timeout(ms),
    });
    if (!r.ok) throw new Error(`${r.status}`);
    return { text: await r.text(), url: r.url || url };
  }

  async topics(): Promise<string[]> {
    try {
      const list = JSON.parse(await readFile(join(this.dir, "topics.json"), "utf8")) as unknown;
      return Array.isArray(list) ? list.map(String) : DEFAULT_TOPICS;
    } catch {
      return DEFAULT_TOPICS;
    }
  }

  async setTopics(list: string[]): Promise<string[]> {
    const cleaned = [...new Set(list.map((t) => String(t).trim()).filter(Boolean))].slice(0, 30);
    await mkdir(this.dir, { recursive: true });
    await writeFile(join(this.dir, "topics.json"), JSON.stringify(cleaned, null, 1));
    this.last = null; // neu bauen
    return cleaned;
  }

  private async searchOne(q: string, m: (typeof MARKETS)[number]): Promise<Candidate[]> {
    const key = `${q}|${m.mkt}|${m.sort}`;
    const hit = this.feedCache.get(key);
    if (hit && Date.now() - hit.at < FEED_TTL) return hit.items;
    const sort = m.sort ? `&qft=${encodeURIComponent('sortbydate="1"')}` : "";
    const { text } = await this.get(
      `https://www.bing.com/news/search?q=${encodeURIComponent(q)}&format=rss&mkt=${m.mkt}&setlang=${m.lang}${sort}`,
    );
    const parsed = this.xml.parse(text) as { rss?: { channel?: { item?: unknown } } };
    const raw = parsed.rss?.channel?.item;
    const list = (Array.isArray(raw) ? raw : raw ? [raw] : []) as {
      title?: unknown;
      link?: string;
      pubDate?: string;
    }[];
    const items = list.map((it) => {
      let url = it.link ?? "";
      try {
        url = new URL(url).searchParams.get("url") ?? url;
      } catch {}
      return {
        topic: q,
        title: String(it.title ?? ""),
        url,
        pubDate: it.pubDate ?? "",
        lang: m.lang,
      };
    });
    this.feedCache.set(key, { at: Date.now(), items });
    return items;
  }

  /** Artikel laden, prüfen und zerlegen — Ergebnis (auch Ablehnungen) liegt auf der Platte. */
  async article(url: string): Promise<Article> {
    const id = hashId(url);
    const file = join(this.dir, "a", `${id}.json`);
    try {
      return JSON.parse(await readFile(file, "utf8")) as Article;
    } catch {}
    const running = this.inflight.get(id);
    if (running) return running;
    const p = this.extract(id, url).then(async (res) => {
      await mkdir(join(this.dir, "a"), { recursive: true });
      await writeFile(file, JSON.stringify(res));
      return res;
    });
    this.inflight.set(id, p);
    try {
      return await p;
    } finally {
      this.inflight.delete(id);
    }
  }

  async cached(id: string): Promise<Article | null> {
    if (!/^[a-f0-9]{16}$/.test(id)) return null;
    try {
      return JSON.parse(await readFile(join(this.dir, "a", `${id}.json`), "utf8")) as Article;
    } catch {
      return null;
    }
  }

  private async extract(id: string, url: string): Promise<Article> {
    const fail = (reason: string): Article => ({
      id,
      url,
      ok: false,
      reason,
      title: "",
      byline: "",
      site: "",
      favicon: null,
      lang: "",
      excerpt: "",
      readMin: 0,
      words: 0,
      blocks: [],
    });
    try {
      const { text: html, url: finalUrl } = await this.get(url);
      const { document } = parseHTML(html) as unknown as { document: Doc };
      const meta = (sel: string) => document.querySelector(sel)?.getAttribute("content") ?? "";
      const pay = paywalled(document, html);
      const article = isArticle(document);
      const vids = embeddedVideos(document, finalUrl);
      const icons = Array.from(
        document.querySelectorAll(
          'link[rel~="icon"], link[rel="apple-touch-icon"], link[rel="shortcut icon"]',
        ),
      );
      const size = (e: El) =>
        Number.parseInt(e.getAttribute("sizes") ?? "") ||
        (/apple/.test(e.getAttribute("rel") ?? "") ? 180 : 16);
      const icon = icons.sort((a, b) => size(b) - size(a))[0];
      const favicon = abs(icon?.getAttribute("href") ?? "/favicon.ico", finalUrl);
      const host = new URL(finalUrl).hostname.replace(/^www\./, "");
      const og = {
        image: abs(meta('meta[property="og:image"]'), finalUrl),
        video: meta('meta[property="og:video"]') || meta('meta[property="og:video:url"]'),
        site: meta('meta[property="og:site_name"]') || host,
        lang: document.documentElement?.getAttribute("lang") ?? "",
      };
      // Readability verändert das Dokument — deshalb erst danach parsen, was wir oben brauchten.
      const art = new Readability(
        document as unknown as ConstructorParameters<typeof Readability>[0],
        {
          charThreshold: 400,
          keepClasses: false,
        },
      ).parse();
      const text = clean(art?.textContent ?? "");
      let reason = pay;
      if (!art || text.length < MIN_CHARS) reason ??= "zu kurz";
      if (!reason && !article) reason = "kein Artikel";
      if (!reason && PAY_PHRASES.some((r) => r.test(text.slice(-600)))) reason = "Abo-Hinweis";
      const blocks = art?.content ? tidy(toBlocks(art.content, finalUrl)) : [];
      if (og.image && !blocks.some((b) => b.t === "img" && b.src === og.image))
        blocks.unshift({ t: "img", src: og.image, caption: "", lead: true });
      for (const v of vids)
        if (!blocks.some((b) => b.t === "video" && videoKey(b) === videoKey(v)))
          blocks.splice(Math.min(2, blocks.length), 0, v);
      if (og.video) {
        const v = videoFrom(og.video, finalUrl);
        if (v && !blocks.some((b) => b.t === "video" && videoKey(b) === videoKey(v)))
          blocks.splice(1, 0, { t: "video", ...v, caption: "" } as Video);
      }
      if (blocks.filter((b) => b.t === "img").length > 25) reason ??= "Übersichtsseite";
      const words = text.split(" ").length;
      return {
        id,
        url: finalUrl,
        ok: !reason,
        reason,
        title: stripSite(clean(art?.title ?? ""), og.site),
        byline: clean(art?.byline ?? ""),
        site: og.site,
        favicon,
        lang: og.lang.slice(0, 2).toLowerCase(),
        excerpt: clean(art?.excerpt ?? ""),
        readMin: Math.max(1, Math.round(words / 200)),
        words,
        blocks,
      };
    } catch (e) {
      return fail(`Fehler ${(e as Error).message}`);
    }
  }

  /** Fertiger Tagesbericht; nach 30 Minuten wird im Hintergrund neu gebaut. */
  async discover(force = false): Promise<Discover> {
    const fresh = this.last && Date.now() - this.last.builtAt < FEED_TTL;
    if (this.last && !force) {
      // Fehler im Hintergrund dürfen den Server nicht reißen — dann bleibt der alte Bericht.
      if (!fresh) this.rebuild().catch(() => {});
      return this.last;
    }
    return this.rebuild();
  }

  /** Höchstens ein Bau gleichzeitig; wer währenddessen fragt, wartet auf denselben. */
  private rebuild(): Promise<Discover> {
    if (!this.building) {
      this.building = this.build().finally(() => {
        this.building = null;
      });
    }
    return this.building;
  }

  private async build(): Promise<Discover> {
    const topics = await this.topics();
    const found = (
      await Promise.all(
        topics.map((t) =>
          Promise.all(MARKETS.map((m) => this.searchOne(t, m).catch(() => [] as Candidate[]))).then(
            (l) => l.flatMap((x) => x.slice(0, PER_QUERY)),
          ),
        ),
      )
    ).flat();
    const seenUrl = new Set<string>();
    const cands = found.filter((c) => c.url && !seenUrl.has(c.url) && seenUrl.add(c.url));
    const arts: { c: Candidate; a: Article }[] = new Array(cands.length);
    let next = 0;
    await Promise.all(
      Array.from({ length: 8 }, async () => {
        while (next < cands.length) {
          const k = next++;
          const c = cands[k];
          if (c) arts[k] = { c, a: await this.article(c.url) };
        }
      }),
    );

    const dropped: Record<string, number> = {};
    const drop = (r: string) => {
      dropped[r] = (dropped[r] ?? 0) + 1;
    };
    const seenTitle = new Set<string>();
    const items: DiscoverItem[] = [];
    for (const entry of arts) {
      if (!entry) continue;
      const { c, a } = entry;
      if (!a.ok) {
        drop(a.reason ?? "?");
        continue;
      }
      const title = a.title || c.title;
      if (title.length < 20 || JUNK_TITLE.test(title)) {
        drop("Werbung/Übersicht");
        continue;
      }
      if (c.pubDate && Date.now() - Date.parse(c.pubDate) > MAX_AGE) {
        drop("zu alt");
        continue;
      }
      const lang = (a.lang || c.lang).slice(0, 2);
      if (lang && lang !== "de" && lang !== "en") {
        drop("andere Sprache");
        continue;
      }
      const firstP = a.blocks.find((b) => b.t === "p");
      if (!matchesTopic(c.topic, `${title} ${a.excerpt} ${firstP?.t === "p" ? firstP.text : ""}`)) {
        drop("passt nicht zum Thema");
        continue;
      }
      const key = title
        .toLowerCase()
        .replace(/[\p{P}\p{S}]+/gu, " ")
        .replace(/\s+/g, " ")
        .trim();
      if (seenTitle.has(key)) continue;
      seenTitle.add(key);
      const paras = a.blocks.flatMap((b) => (b.t === "p" ? [b.text] : []));
      const firstImg = a.blocks.find((b) => b.t === "img");
      items.push({
        id: a.id,
        topic: c.topic,
        title,
        site: a.site,
        favicon: a.favicon,
        teaser: (a.excerpt.length > 80 ? a.excerpt : paras.slice(0, 2).join(" "))
          .replace(/\s+\|\s+[^|]{2,30}$/, "")
          .slice(0, 320),
        image: firstImg?.t === "img" ? firstImg.src : null,
        images: a.blocks.filter((b) => b.t === "img").length,
        videos: a.blocks.filter((b) => b.t === "video").length,
        readMin: a.readMin,
        at: Date.parse(c.pubDate) || 0,
        lang,
      });
    }
    // Platzhalter-Bilder: Dateiname verrät es, oder dasselbe Bild steckt in mehreren Artikeln.
    const uses = new Map<string, number>();
    for (const i of items) if (i.image) uses.set(i.image, (uses.get(i.image) ?? 0) + 1);
    for (const i of items) {
      if (!i.image || (!PLACEHOLDER_IMG.test(i.image) && (uses.get(i.image) ?? 0) < 2)) continue;
      const a = arts.find((x) => x?.a.id === i.id)?.a;
      const other = a?.blocks.find(
        (b) => b.t === "img" && b.src !== i.image && !PLACEHOLDER_IMG.test(b.src),
      );
      i.image = other?.t === "img" ? other.src : null;
    }
    const result: Discover = {
      topics,
      items: diversify(items),
      stats: { candidates: cands.length, kept: items.length, dropped },
      builtAt: Date.now(),
    };
    this.last = result;
    return result;
  }
}
