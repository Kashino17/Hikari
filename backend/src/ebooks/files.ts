import { existsSync, mkdirSync, renameSync, rmSync, statSync, writeFileSync } from "node:fs";
import { join } from "node:path";
import type Database from "better-sqlite3";
import { execa } from "execa";
import { categorize, kindOf } from "./categories.js";
import type { PoliteHttp } from "./http.js";
import { type BookFormat, hideBook } from "./persist.js";
import { gutenbergAlternates } from "./sources/gutenberg.js";
import { MIN_WORDS } from "./whole-book.js";

/** Ein PDF unter so vielen Seiten ist kein ganzes Buch (Flyer, Leseprobe, Inhaltsverzeichnis). */
export const MIN_PDF_PAGES = 40;

/**
 * Seitenzahl eines PDF: Seiten-Objekte zählen; stecken sie in komprimierten Objektströmen
 * (PDF 1.5+), gilt der /Count des Seitenbaums. Nichts gefunden → null (unbekannt, nicht „zu kurz“).
 */
export function countPdfPages(pdf: Buffer): number | null {
  const text = pdf.toString("latin1");
  const pages = (text.match(/\/Type\s*\/Page(?![s\w])/g) ?? []).length;
  if (pages > 0) return pages;
  const counts = [
    ...text.matchAll(
      /\/Type\s*\/Pages\b[^>]*?\/Count\s+(\d+)|\/Count\s+(\d+)[^>]*?\/Type\s*\/Pages\b/g,
    ),
  ].map((m) => Number(m[1] ?? m[2]));
  return counts.length ? Math.max(...counts) : null;
}

/** Wörter des Fließtexts eines EPUB (alle (x)html-Dateien, Tags entfernt). */
export async function countEpubWords(path: string): Promise<number> {
  const r = await execa("unzip", ["-p", path, "*.xhtml", "*.html", "*.htm"], {
    reject: false,
    encoding: "utf8",
    maxBuffer: 512 * 1024 * 1024,
  });
  const text = String(r.stdout ?? "")
    .replace(/<(script|style|head)[\s\S]*?<\/\1>/gi, " ")
    .replace(/<[^>]+>/g, " ")
    .replace(/&[a-z#0-9]+;/gi, " ");
  return text.split(/\s+/).filter((w) => /\p{L}/u.test(w)).length;
}

const safe = (id: string) => id.replace(/[^A-Za-z0-9_-]/g, "_").slice(0, 150);
const inflight = new Map<
  string,
  Promise<{ path: string; format: BookFormat } | { hidden: string }>
>();

/**
 * Sorgt dafür, dass das Buch auf dem Mac liegt: laden (atomar), Länge prüfen.
 * Zu kurz → aus dem Katalog verstecken; die Datei bleibt (wer sie hat, kann sie lesen).
 */
export function ensureBookFile(
  deps: { db: Database.Database; ebookDir: string; http: PoliteHttp },
  id: string,
): Promise<{ path: string; format: BookFormat } | { hidden: string }> {
  const running = inflight.get(id);
  if (running) return running;
  const p = load(deps, id).finally(() => inflight.delete(id));
  inflight.set(id, p);
  return p;
}

async function load(
  { db, ebookDir, http }: { db: Database.Database; ebookDir: string; http: PoliteHttp },
  id: string,
): Promise<{ path: string; format: BookFormat } | { hidden: string }> {
  const book = db
    .prepare(
      "SELECT id, source, format, file_url, subjects_json, hidden_reason FROM ebooks WHERE id = ?",
    )
    .get(id) as
    | {
        id: string;
        source: string;
        format: BookFormat;
        file_url: string;
        subjects_json: string;
        hidden_reason: string | null;
      }
    | undefined;
  if (!book) throw new Error(`unbekanntes Buch: ${id}`);
  const known = db.prepare("SELECT local_path FROM ebook_files WHERE book_id = ?").get(id) as
    | { local_path: string }
    | undefined;
  if (known && existsSync(known.local_path)) {
    return book.hidden_reason
      ? { hidden: book.hidden_reason }
      : { path: known.local_path, format: book.format };
  }

  let r: { status: number; body: Buffer } = { status: 0, body: Buffer.alloc(0) };
  let lastError: unknown = null;
  for (const url of [book.file_url, ...gutenbergAlternates(book.file_url)]) {
    try {
      // Ein Versuch je Spiegel: der nächste Spiegel ist die Wiederholung (sonst Minuten Wartezeit).
      r = await http.get(url, { tries: 1 });
      if (r.status === 200 && r.body.length > 0) break;
    } catch (err) {
      lastError = err;
    }
  }
  if (r.status !== 200 || r.body.length === 0) {
    throw lastError instanceof Error && r.status === 0
      ? lastError
      : new Error(`Laden fehlgeschlagen (HTTP ${r.status})`);
  }
  mkdirSync(ebookDir, { recursive: true });
  const path = join(ebookDir, `${safe(id)}.${book.format}`);
  const part = `${path}.part`;
  try {
    writeFileSync(part, r.body);
    renameSync(part, path);
  } catch (err) {
    rmSync(part, { force: true });
    throw err;
  }

  let words: number | null = null;
  let tooShort: string | null = null;
  if (book.format === "epub") {
    words = await countEpubWords(path);
    const min =
      MIN_WORDS[kindOf(categorize(book.source, JSON.parse(book.subjects_json) as string[]))];
    if (words < min) tooShort = `zu kurz (${words} Wörter)`;
  } else {
    const pages = countPdfPages(r.body);
    if (pages !== null && pages < MIN_PDF_PAGES) tooShort = `zu kurz (${pages} Seiten)`;
  }
  db.prepare(
    `INSERT INTO ebook_files (book_id, local_path, bytes, measured_words, fetched_at) VALUES (?, ?, ?, ?, ?)
     ON CONFLICT(book_id) DO UPDATE SET local_path = excluded.local_path, bytes = excluded.bytes,
       measured_words = excluded.measured_words, fetched_at = excluded.fetched_at`,
  ).run(id, path, statSync(path).size, words, Date.now());
  if (words !== null) db.prepare("UPDATE ebooks SET word_count = ? WHERE id = ?").run(words, id);
  if (tooShort) {
    hideBook(db, id, tooShort);
    return { hidden: tooShort };
  }
  return { path, format: book.format };
}
