import { createHash } from "node:crypto";
import {
  closeSync,
  createReadStream,
  existsSync,
  mkdirSync,
  openSync,
  readSync,
  writeFileSync,
} from "node:fs";
import { join } from "node:path";
import type Database from "better-sqlite3";
import type { FastifyInstance } from "fastify";
import { authorPhoto } from "../ebooks/authors.js";
import { CATEGORIES } from "../ebooks/categories.js";
import { resolveCover } from "../ebooks/covers.js";
import { ensureBookFile } from "../ebooks/files.js";
import type { PoliteHttp } from "../ebooks/http.js";
import { normalizeForSearch } from "../ebooks/persist.js";
import type { EbookSource } from "../ebooks/sources/types.js";
import { type BookState, mergeState, normalizeState } from "../ebooks/state-merge.js";
import { ebookSyncState, runEbookSync } from "../ebooks/sync.js";

export interface EbookDeps {
  db: Database.Database;
  ebookDir: string;
  coverDir: string;
  http: PoliteHttp;
  /** Für Open Library (langsamer, 1 Anfrage/s); sonst http. */
  coverHttp?: PoliteHttp;
  sources: EbookSource[];
  log?: (msg: string) => void;
}

const SOURCE_NAMES: Record<string, string> = {
  gutenberg: "Project Gutenberg",
  wikisource: "Wikisource",
  bpb: "Bundeszentrale für politische Bildung",
  openaccess: "Open Access (DOAB)",
};
const WORDS_PER_MINUTE = 230;
const MISSING_GRACE_MS = 30 * 24 * 60 * 60 * 1000;
/** Kategorien, die als eigenes Regal auf der Startseite erscheinen (in dieser Reihenfolge). */
const SHELF_CATEGORIES = [
  "krimi",
  "fantastik",
  "maerchen",
  "philosophie",
  "abenteuer",
  "liebe",
  "humor",
  "drama",
  "lyrik",
  "historisch",
  "reisen",
  "wissenschaft",
  "geschichte",
  "biografie",
];
const SHELF_SUBTITLES: Record<string, string> = {
  krimi: "Verbrechen, Gericht, Unheimliches",
  fantastik: "Zauber, Spuk und ferne Welten",
  maerchen: "Märchen und Sagen",
  philosophie: "Große Ideen",
  abenteuer: "Fahrten, Inseln, Wildnis",
  liebe: "Herzklopfen von damals",
  humor: "Zum Lachen",
  drama: "Dramen zum Lesen",
  lyrik: "Gedichte und Balladen",
  historisch: "Romane aus alten Zeiten",
  reisen: "Reiseberichte von damals",
  wissenschaft: "Entdeckungen und Erfindungen",
  geschichte: "Wie es gewesen ist",
  biografie: "Leben und Erinnerungen",
};
/** Haupttitel ohne Untertitel („Faust: Der Tragödie erster Teil“ → „Faust“). */
function shortTitle(t: string): string {
  const i = t.indexOf(": ");
  return i > 0 ? t.slice(0, i) : t;
}
const LENGTH: Record<string, string> = {
  kurz: "e.word_count < 30000",
  mittel: "e.word_count BETWEEN 30000 AND 100000",
  lang: "e.word_count > 100000",
};
const SORT: Record<string, string> = {
  beliebt:
    "e.popularity IS NULL, e.popularity DESC, e.cover_url IS NULL, e.word_count IS NULL, e.word_count DESC, e.title COLLATE NOCASE, e.id",
  titel: "e.title COLLATE NOCASE, e.id",
  autor: "e.authors_json COLLATE NOCASE, e.title COLLATE NOCASE, e.id",
  jahr: "e.year IS NULL, e.year DESC, e.title COLLATE NOCASE, e.id",
  neu: "e.added_at DESC, e.title COLLATE NOCASE, e.id",
};

interface Row {
  id: string;
  source: string;
  title: string;
  authors_json: string;
  year: number | null;
  format: "epub" | "pdf";
  cover_url: string | null;
  word_count: number | null;
  description: string | null;
  source_url: string;
  cover_status: string | null;
  cover_alt_url: string | null;
  work_key: string | null;
  popularity: number | null;
}

/** FTS-Anfrage aus freier Eingabe: jedes Wort als Wortanfang, alle Wörter müssen passen. */
export function ftsQuery(q: string): string | null {
  const tokens = normalizeForSearch(q)
    .split(/[^\p{L}\p{N}]+/u)
    .filter(Boolean)
    .slice(0, 8);
  return tokens.length ? tokens.map((t) => `"${t}"*`).join(" ") : null;
}

export function registerEbookRoutes(app: FastifyInstance, deps: EbookDeps): void {
  const { db } = deps;
  const alive = (a: string) =>
    `${a}.hidden_reason IS NULL AND (${a}.missing_since IS NULL OR ${a}.missing_since > ${Date.now() - MISSING_GRACE_MS})`;
  // Dasselbe Werk mehrfach im Katalog (z. B. zwei Gutenberg-Ausgaben von Faust): nur die beliebtere zeigen.
  db.exec("CREATE INDEX IF NOT EXISTS idx_ebooks_title ON ebooks(title)");
  const visible = () =>
    `${alive("e")} AND (e.id IN (SELECT book_id FROM ebook_state) OR NOT EXISTS (SELECT 1 FROM ebooks d WHERE d.work_key = e.work_key
       AND d.id <> e.id AND ${alive("d")}
       AND (COALESCE(d.popularity, -1) > COALESCE(e.popularity, -1)
            OR (COALESCE(d.popularity, -1) = COALESCE(e.popularity, -1) AND d.id < e.id))))`;
  const catsOf = db.prepare(
    "SELECT category FROM ebook_categories WHERE book_id = ? ORDER BY category",
  );

  // Ein Ersatz-Cover aus Open Library, das auch ein anderes (beliebteres) Werk trägt, gehört diesem —
  // sonst sehen „Faust I“ und „Faust II“ gleich aus.
  const coverTaken = db.prepare(
    `SELECT 1 FROM ebooks d WHERE d.cover_alt_url = ? AND d.cover_status = 'openlibrary'
       AND d.id <> ? AND COALESCE(d.work_key, '') <> COALESCE(?, '') AND ${alive("d")}
       AND (COALESCE(d.popularity, -1) > COALESCE(?, -1)
            OR (COALESCE(d.popularity, -1) = COALESCE(?, -1) AND d.id < ?)) LIMIT 1`,
  );
  const borrowedCover = (r: {
    id: string;
    cover_status: string | null;
    cover_alt_url: string | null;
    work_key: string | null;
    popularity: number | null;
  }) =>
    r.cover_status === "openlibrary" &&
    !!r.cover_alt_url &&
    coverTaken.get(r.cover_alt_url, r.id, r.work_key, r.popularity, r.popularity, r.id) !==
      undefined;

  function card(r: Row) {
    return {
      id: r.id,
      title: r.title,
      authors: JSON.parse(r.authors_json) as string[],
      year: r.year ?? undefined,
      format: r.format,
      // Kein echtes Bild → kein Pfad, die App zeichnet ein Titelbild. v=2: alte Muster-Cover im Bild-Speicher der App verwerfen.
      coverUrl:
        r.cover_status === "none" || (r.cover_status === null && !r.cover_url) || borrowedCover(r)
          ? undefined
          : `/api/ebooks/${encodeURIComponent(r.id)}/cover?v=3`,
      categories: (catsOf.all(r.id) as { category: string }[]).map((c) => c.category),
      source: r.source,
      wordCount: r.word_count ?? undefined,
      minutes: r.word_count ? Math.round(r.word_count / WORDS_PER_MINUTE) : undefined,
    };
  }
  const COLS =
    "e.id, e.source, e.title, e.authors_json, e.year, e.format, e.cover_url, e.word_count, e.description, e.source_url, e.cover_status, e.cover_alt_url, e.work_key, e.popularity";

  app.get<{ Querystring: { because?: string } }>("/api/ebooks/home", async (req) => {
    const newest = db
      .prepare(
        `SELECT ${COLS} FROM ebooks e WHERE ${visible()} ORDER BY e.added_at DESC, e.title LIMIT 80`,
      )
      .all() as Row[];
    const counts = db
      .prepare(
        `SELECT c.category, COUNT(*) AS n FROM ebook_categories c JOIN ebooks e ON e.id = c.book_id
         WHERE ${visible()} GROUP BY c.category`,
      )
      .all() as { category: string; n: number }[];
    const countOf = new Map(counts.map((c) => [c.category, c.n]));
    const inCat = db.prepare(
      `SELECT ${COLS} FROM ebooks e JOIN ebook_categories c ON c.book_id = e.id
       WHERE c.category = ? AND ${visible()} ORDER BY ${SORT.beliebt} LIMIT 12`,
    );
    const rows = CATEGORIES.filter((c) => (countOf.get(c.id) ?? 0) >= 6)
      .sort((a, b) => (countOf.get(b.id) ?? 0) - (countOf.get(a.id) ?? 0))
      .map((c) => ({
        category: c.id,
        label: c.label,
        books: (inCat.all(c.id) as Row[]).map(card),
      }));
    const sync = ebookSyncState();
    // Etwas mehr holen als gezeigt wird: Bücher aus Regalen weiter oben fallen unten wieder raus.
    const pick = (where: string, params: unknown[] = [], order = SORT.beliebt, limit = 12) =>
      (
        db
          .prepare(
            `SELECT ${COLS} FROM ebooks e WHERE ${visible()} AND ${where} ORDER BY ${order} LIMIT ?`,
          )
          .all(...params, limit + 40) as Row[]
      ).map(card);
    const shelves: {
      key: string;
      title: string;
      subtitle: string;
      books: ReturnType<typeof card>[];
    }[] = [];
    // Kein Buch in zwei Regalen: was weiter oben steht, kommt weiter unten nicht nochmal.
    const shown = new Set<string>();
    const add = (
      key: string,
      title: string,
      subtitle: string,
      books: ReturnType<typeof card>[],
      min = 4,
      max = 12,
    ) => {
      const fresh = books.filter((b) => !shown.has(b.id)).slice(0, max);
      if (fresh.length < min) return;
      for (const b of fresh) shown.add(b.id);
      shelves.push({ key, title, subtitle, books: fresh });
    };
    // „Weil du … liest“: zuerst derselbe Autor, dann dieselbe Art Buch.
    const because = req.query.because
      ? (db.prepare(`SELECT ${COLS} FROM ebooks e WHERE e.id = ?`).get(req.query.because) as
          | Row
          | undefined)
      : undefined;
    if (because) {
      const b = card(because);
      const first = b.authors[0];
      const byAuthor = first
        ? pick(
            "e.id <> ? AND EXISTS (SELECT 1 FROM json_each(e.authors_json) a WHERE a.value = ?)",
            [b.id, first],
            SORT.beliebt,
            6,
          )
        : [];
      const cat =
        b.categories.find((c) => c !== "roman" && c !== "erzaehlungen") ?? b.categories[0];
      const seen = new Set([b.id, ...byAuthor.map((x) => x.id)]);
      const byCat = cat
        ? pick(
            "e.id IN (SELECT book_id FROM ebook_categories WHERE category = ?)",
            [cat],
            SORT.beliebt,
            20,
          ).filter((x) => !seen.has(x.id))
        : [];
      add(
        "because",
        `Weil du ${shortTitle(b.title)} liest`,
        "Vom selben Autor und Ähnliches",
        [...byAuthor, ...byCat].slice(0, 12),
        1,
      );
    }
    add(
      "beliebt",
      "Die meistgelesenen",
      "Downloads der letzten 30 Tage",
      pick("e.popularity IS NOT NULL"),
    );
    add(
      "kurz",
      "Kurz & gut",
      "An einem Abend gelesen",
      pick(`e.word_count IS NOT NULL AND e.word_count < ${WORDS_PER_MINUTE * 120}`),
      3,
    );
    for (const id of SHELF_CATEGORIES) {
      const c = CATEGORIES.find((x) => x.id === id);
      if (!c) continue;
      add(
        `kat:${id}`,
        c.label,
        SHELF_SUBTITLES[id] ?? "",
        pick("e.id IN (SELECT book_id FROM ebook_categories WHERE category = ?)", [id]),
        6,
      );
    }
    add("pdf", "Wissen kompakt", "Sachbücher als PDF", pick("e.format = 'pdf'", [], SORT.neu));
    add("neu", "Neu dazugekommen", "Seit dem letzten Abgleich", newest.map(card), 4, 20);
    // Buch des Tages: jeden Tag ein anderes der beliebtesten, möglichst mit echtem Titelbild.
    const candidates = pick("e.popularity IS NOT NULL", [], SORT.beliebt, 40);
    const withCover = candidates.filter((x) => x.coverUrl);
    const pool = withCover.length ? withCover : candidates;
    const day = Math.floor(Date.now() / 86_400_000);
    const featured = pool.length ? pool[day % pool.length] : undefined;
    // Autoren mit mindestens zwei Büchern, die meisten zuerst.
    const authorCount = new Map<string, number>();
    for (const r of db.prepare(`SELECT e.authors_json FROM ebooks e WHERE ${visible()}`).all() as {
      authors_json: string;
    }[])
      for (const a of JSON.parse(r.authors_json) as string[])
        authorCount.set(a, (authorCount.get(a) ?? 0) + 1);
    const authors = [...authorCount]
      .filter(([, n]) => n >= 2)
      .sort((a, b) => b[1] - a[1] || a[0].localeCompare(b[0], "de"))
      .slice(0, 30)
      .map(([name, count]) => ({
        name,
        count,
        // Porträt nur, wenn eins gefunden wurde (sonst zeigt die App den Anfangsbuchstaben).
        photoUrl: authorPhoto(db, name)
          ? `/api/ebooks/author-photo?name=${encodeURIComponent(name)}`
          : undefined,
      }));
    return {
      shelves,
      featured,
      authors,
      newest: newest.slice(0, 20).map(card),
      rows,
      categories: CATEGORIES.filter((c) => countOf.has(c.id)).map((c) => ({
        ...c,
        count: countOf.get(c.id) ?? 0,
      })),
      lastSync: sync.lastRun,
      syncing: sync.running,
    };
  });

  app.get<{
    Querystring: Partial<
      Record<
        | "q"
        | "category"
        | "author"
        | "source"
        | "format"
        | "length"
        | "sort"
        | "state"
        | "offset"
        | "limit",
        string
      >
    >;
  }>("/api/ebooks/list", async (req) => {
    const q = req.query;
    const where = [visible()];
    const params: (string | number)[] = [];
    const fts = q.q ? ftsQuery(q.q) : null;
    if (q.q && !fts) return { total: 0, books: [] };
    if (fts) {
      where.push("e.id IN (SELECT book_id FROM ebooks_fts WHERE ebooks_fts MATCH ?)");
      params.push(fts);
    }
    if (q.category) {
      where.push("e.id IN (SELECT book_id FROM ebook_categories WHERE category = ?)");
      params.push(q.category);
    }
    if (q.author) {
      where.push("EXISTS (SELECT 1 FROM json_each(e.authors_json) a WHERE a.value = ?)");
      params.push(q.author);
    }
    if (q.source) {
      where.push("e.source = ?");
      params.push(q.source);
    }
    if (q.format === "epub" || q.format === "pdf") {
      where.push("e.format = ?");
      params.push(q.format);
    }
    if (q.length && LENGTH[q.length]) where.push(LENGTH[q.length] as string);
    if (q.state === "gemerkt" || q.state === "gelesen") {
      const path = q.state === "gemerkt" ? "$.saved.value" : "$.finished.value";
      where.push(
        `e.id IN (SELECT book_id FROM ebook_state WHERE json_extract(state_json, '${path}') = 1)`,
      );
    }
    const sql = `FROM ebooks e WHERE ${where.join(" AND ")}`;
    const limit = Math.min(Math.max(Number(q.limit) || 30, 1), 100);
    const offset = Math.max(Number(q.offset) || 0, 0);
    const total = (db.prepare(`SELECT COUNT(*) AS n ${sql}`).get(...params) as { n: number }).n;
    const rows = db
      .prepare(
        `SELECT ${COLS} ${sql} ORDER BY ${SORT[q.sort ?? "beliebt"] ?? SORT.beliebt} LIMIT ? OFFSET ?`,
      )
      .all(...params, limit, offset) as Row[];
    return { total, books: rows.map(card) };
  });

  app.get("/api/ebooks/state", async () => {
    const rows = db.prepare("SELECT book_id, state_json FROM ebook_state").all() as {
      book_id: string;
      state_json: string;
    }[];
    return Object.fromEntries(
      rows.map((r) => [r.book_id, normalizeState(JSON.parse(r.state_json))]),
    );
  });

  app.put<{ Params: { id: string } }>("/api/ebooks/state/:id", async (req) => {
    const id = req.params.id;
    const prev = db.prepare("SELECT state_json FROM ebook_state WHERE book_id = ?").get(id) as
      | { state_json: string }
      | undefined;
    const merged: BookState = mergeState(
      prev ? normalizeState(JSON.parse(prev.state_json)) : normalizeState(null),
      normalizeState(req.body),
    );
    db.prepare(
      `INSERT INTO ebook_state (book_id, state_json, updated_at) VALUES (?, ?, ?)
       ON CONFLICT(book_id) DO UPDATE SET state_json = excluded.state_json, updated_at = excluded.updated_at`,
    ).run(id, JSON.stringify(merged), Date.now());
    return merged;
  });

  app.post("/api/ebooks/sync", async (_req, reply) => {
    void runEbookSync({
      db,
      sources: deps.sources,
      http: deps.http,
      log: deps.log ?? (() => {}),
    }).catch(() => {});
    return reply.code(202).send({ started: true });
  });

  app.get<{ Params: { id: string } }>("/api/ebooks/:id", async (req, reply) => {
    const r = db.prepare(`SELECT ${COLS} FROM ebooks e WHERE e.id = ?`).get(req.params.id) as
      | Row
      | undefined;
    if (!r) return reply.code(404).send({ error: "unbekanntes Buch" });
    const file = db
      .prepare("SELECT local_path, bytes FROM ebook_files WHERE book_id = ?")
      .get(r.id) as { local_path: string; bytes: number } | undefined;
    const downloaded = !!file && existsSync(file.local_path);
    const c = card(r);
    const near = (where: string, params: unknown[], limit = 12) =>
      (
        db
          .prepare(
            `SELECT ${COLS} FROM ebooks e WHERE ${visible()} AND e.id <> ? AND ${where} ORDER BY ${SORT.beliebt} LIMIT ?`,
          )
          .all(r.id, ...params, limit) as Row[]
      ).map(card);
    const first = c.authors[0];
    const moreByAuthor = first
      ? near("EXISTS (SELECT 1 FROM json_each(e.authors_json) a WHERE a.value = ?)", [first])
      : [];
    const mainCat =
      c.categories.find((x) => x !== "roman" && x !== "erzaehlungen") ?? c.categories[0];
    const taken = new Set(moreByAuthor.map((x) => x.id));
    const similar = mainCat
      ? near("e.id IN (SELECT book_id FROM ebook_categories WHERE category = ?)", [mainCat], 24)
          .filter((x) => !taken.has(x.id))
          .slice(0, 12)
      : [];
    return {
      ...c,
      moreByAuthor,
      similar,
      description: r.description ?? undefined,
      sourceUrl: r.source_url,
      sourceName: SOURCE_NAMES[r.source] ?? r.source,
      downloaded,
      fileBytes: downloaded ? file?.bytes : undefined,
    };
  });

  // Vorbereiten: der Mac holt das Buch im Hintergrund (Quellen können langsam sein), die App fragt nach.
  const preparing = new Map<string, { status: "loading" | "error"; error?: string }>();
  app.post<{ Params: { id: string } }>("/api/ebooks/:id/prepare", async (req, reply) => {
    const id = req.params.id;
    const book = db.prepare("SELECT hidden_reason FROM ebooks WHERE id = ?").get(id) as
      | { hidden_reason: string | null }
      | undefined;
    if (!book) return reply.code(404).send({ error: "unbekanntes Buch" });
    if (book.hidden_reason)
      return { status: "hidden", error: `${book.hidden_reason} — kein ganzes Buch` };
    const file = db.prepare("SELECT local_path FROM ebook_files WHERE book_id = ?").get(id) as
      | { local_path: string }
      | undefined;
    if (file && existsSync(file.local_path)) return { status: "ready" };
    const cur = preparing.get(id);
    if (cur?.status === "loading") return { status: "loading" };
    preparing.set(id, { status: "loading" });
    ensureBookFile(deps, id)
      .then((r) => {
        preparing.delete(id);
        if ("hidden" in r) deps.log?.(`ebooks: ${id} versteckt — ${r.hidden}`);
      })
      .catch((err: Error) => preparing.set(id, { status: "error", error: err.message }));
    return cur?.status === "error"
      ? { status: "loading", lastError: cur.error }
      : { status: "loading" };
  });

  app.get<{ Params: { id: string } }>("/api/ebooks/:id/file", async (req, reply) => {
    try {
      const r = await ensureBookFile(deps, req.params.id);
      if ("hidden" in r) return reply.code(410).send({ error: `${r.hidden} — kein ganzes Buch` });
      return reply
        .type(r.format === "epub" ? "application/epub+zip" : "application/pdf")
        .send(createReadStream(r.path));
    } catch (err) {
      const msg = (err as Error).message;
      return reply.code(/unbekannt/.test(msg) ? 404 : 502).send({ error: msg });
    }
  });

  const resolving = new Map<string, ReturnType<typeof resolveCover>>();
  // Porträt eines Autors: einmal von Open Library holen, dann von der Platte.
  app.get<{ Querystring: { name?: string } }>("/api/ebooks/author-photo", async (req, reply) => {
    const name = req.query.name ?? "";
    const url = name ? authorPhoto(db, name) : null;
    if (!url) return reply.code(404).send({ error: "kein Foto" });
    const dir = join(deps.coverDir, "authors");
    const path = join(dir, `${createHash("sha1").update(name).digest("hex")}.img`);
    if (!existsSync(path)) {
      const got = await (deps.coverHttp ?? deps.http).get(url, { tries: 1 }).catch(() => null);
      if (!got || got.status !== 200 || got.body.length < 100)
        return reply.code(404).send({ error: "kein Foto" });
      mkdirSync(dir, { recursive: true });
      writeFileSync(path, got.body);
    }
    reply.header("cache-control", "public, max-age=604800");
    return reply.type(sniffImage(path)).send(createReadStream(path));
  });

  app.get<{ Params: { id: string } }>("/api/ebooks/:id/cover", async (req, reply) => {
    const id = req.params.id;
    const r = db
      .prepare(
        "SELECT id, cover_url, cover_status, cover_alt_url, work_key, popularity FROM ebooks WHERE id = ?",
      )
      .get(id) as
      | {
          id: string;
          cover_url: string | null;
          cover_status: string | null;
          cover_alt_url: string | null;
          work_key: string | null;
          popularity: number | null;
        }
      | undefined;
    if (!r || borrowedCover(r)) return reply.code(404).send({ error: "kein Titelbild" });
    let status = r.cover_status;
    let url = status === "openlibrary" ? r.cover_alt_url : r.cover_url;
    if (status === null) {
      // Noch nicht geprüft: jetzt prüfen (erzeugte Gutenberg-Muster aussortieren, Open Library fragen).
      let p = resolving.get(id);
      if (!p) {
        p = resolveCover(db, deps.coverHttp ?? deps.http, id).finally(() => resolving.delete(id));
        resolving.set(id, p);
      }
      const res = await p;
      status = res.status;
      url = res.status === "none" ? null : res.url;
    }
    if (status === "none" || !url) return reply.code(404).send({ error: "kein Titelbild" });
    const dir = join(deps.coverDir, "ebooks");
    const path = join(dir, `${id.replace(/[^A-Za-z0-9_-]/g, "_").slice(0, 150)}.${status}.img`);
    if (!existsSync(path)) {
      const got = await (status === "openlibrary" ? (deps.coverHttp ?? deps.http) : deps.http)
        .get(url, { tries: 1 })
        .catch(() => null);
      if (!got || got.status !== 200 || got.body.length < 100)
        return reply.code(404).send({ error: "kein Titelbild" });
      mkdirSync(dir, { recursive: true });
      writeFileSync(path, got.body);
    }
    reply.header("cache-control", "public, max-age=604800");
    return reply.type(sniffImage(path)).send(createReadStream(path));
  });
}

function sniffImage(path: string): string {
  const head = Buffer.alloc(4);
  const fd = openSync(path, "r");
  readSync(fd, head, 0, 4, 0);
  closeSync(fd);
  if (head[0] === 0x89 && head[1] === 0x50) return "image/png";
  if (head.toString("ascii", 0, 4) === "RIFF") return "image/webp";
  return "image/jpeg";
}
