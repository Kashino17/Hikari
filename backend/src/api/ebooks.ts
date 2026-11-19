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
import { CATEGORIES } from "../ebooks/categories.js";
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
const LENGTH: Record<string, string> = {
  kurz: "e.word_count < 30000",
  mittel: "e.word_count BETWEEN 30000 AND 100000",
  lang: "e.word_count > 100000",
};
const SORT: Record<string, string> = {
  beliebt:
    "e.popularity IS NULL, e.popularity DESC, e.cover_url IS NULL, e.word_count IS NULL, e.word_count DESC, e.title COLLATE NOCASE",
  titel: "e.title COLLATE NOCASE",
  autor: "e.authors_json COLLATE NOCASE, e.title COLLATE NOCASE",
  jahr: "e.year IS NULL, e.year DESC, e.title COLLATE NOCASE",
  neu: "e.added_at DESC, e.title COLLATE NOCASE",
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
  const visible = () =>
    `e.hidden_reason IS NULL AND (e.missing_since IS NULL OR e.missing_since > ${Date.now() - MISSING_GRACE_MS})`;
  const catsOf = db.prepare(
    "SELECT category FROM ebook_categories WHERE book_id = ? ORDER BY category",
  );

  function card(r: Row) {
    return {
      id: r.id,
      title: r.title,
      authors: JSON.parse(r.authors_json) as string[],
      year: r.year ?? undefined,
      format: r.format,
      coverUrl: r.cover_url ? `/api/ebooks/${encodeURIComponent(r.id)}/cover` : undefined,
      categories: (catsOf.all(r.id) as { category: string }[]).map((c) => c.category),
      source: r.source,
      wordCount: r.word_count ?? undefined,
      minutes: r.word_count ? Math.round(r.word_count / WORDS_PER_MINUTE) : undefined,
    };
  }
  const COLS =
    "e.id, e.source, e.title, e.authors_json, e.year, e.format, e.cover_url, e.word_count, e.description, e.source_url";

  app.get("/api/ebooks/home", async () => {
    const newest = db
      .prepare(
        `SELECT ${COLS} FROM ebooks e WHERE ${visible()} ORDER BY e.added_at DESC, e.title LIMIT 20`,
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
    return {
      newest: newest.map(card),
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
        "q" | "category" | "source" | "format" | "length" | "sort" | "state" | "offset" | "limit",
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
    return {
      ...card(r),
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

  app.get<{ Params: { id: string } }>("/api/ebooks/:id/cover", async (req, reply) => {
    const r = db.prepare("SELECT cover_url FROM ebooks WHERE id = ?").get(req.params.id) as
      | { cover_url: string | null }
      | undefined;
    if (!r?.cover_url) return reply.code(404).send({ error: "kein Titelbild" });
    const dir = join(deps.coverDir, "ebooks");
    const path = join(dir, `${req.params.id.replace(/[^A-Za-z0-9_-]/g, "_").slice(0, 150)}.img`);
    if (!existsSync(path)) {
      const got = await deps.http.get(r.cover_url).catch(() => null);
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
