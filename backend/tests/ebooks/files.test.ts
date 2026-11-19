import { existsSync, mkdtempSync, readFileSync, readdirSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import Database from "better-sqlite3";
import { beforeEach, describe, expect, it } from "vitest";
import { applyMigrations } from "../../src/db/migrations.js";
import { MIN_PDF_PAGES, countPdfPages, ensureBookFile } from "../../src/ebooks/files.js";
import type { PoliteHttp } from "../../src/ebooks/http.js";
import { type BookFormat, upsertBook } from "../../src/ebooks/persist.js";

const fx = (n: string) => readFileSync(join(__dirname, "../fixtures/ebooks", n));

describe("ensureBookFile", () => {
  let db: Database.Database;
  let dir: string;
  beforeEach(() => {
    db = new Database(":memory:");
    applyMigrations(db);
    dir = mkdtempSync(join(tmpdir(), "ebooks-"));
  });
  const add = (id: string, format: BookFormat = "epub") =>
    upsertBook(
      db,
      {
        source: "t",
        sourceId: id,
        title: id,
        authors: [],
        language: "de",
        subjects: ["DE Prosa"],
        format,
        fileUrl: `https://x.test/${id}`,
        sourceUrl: "y",
      },
      ["roman"],
      1,
    ).id;
  const http = (body: Buffer, calls = { n: 0 }): PoliteHttp => ({
    get: async () => {
      calls.n++;
      await new Promise((r) => setTimeout(r, 10));
      return { status: 200, body };
    },
  });

  it("lädt ein ganzes EPUB, misst die Wörter und merkt sich die Datei", async () => {
    const id = add("lang");
    const r = await ensureBookFile({ db, ebookDir: dir, http: http(fx("long.epub")) }, id);
    expect("path" in r && existsSync(r.path)).toBe(true);
    const row = db.prepare("SELECT measured_words FROM ebook_files WHERE book_id = ?").get(id) as {
      measured_words: number;
    };
    expect(row.measured_words).toBeGreaterThanOrEqual(9000);
    const book = db.prepare("SELECT word_count, hidden_reason FROM ebooks WHERE id = ?").get(id);
    expect(book).toEqual({ word_count: row.measured_words, hidden_reason: null });
  });

  it("zu kurzes EPUB → hidden_reason gesetzt, Datei bleibt", async () => {
    const id = add("kurz");
    const r = await ensureBookFile({ db, ebookDir: dir, http: http(fx("short.epub")) }, id);
    expect(r).toEqual({ hidden: expect.stringMatching(/zu kurz/) });
    expect(
      (
        db.prepare("SELECT hidden_reason FROM ebooks WHERE id = ?").get(id) as {
          hidden_reason: string;
        }
      ).hidden_reason,
    ).toMatch(/zu kurz/);
    expect(readdirSync(dir).some((f) => f.endsWith(".epub"))).toBe(true);
  });

  it("PDF: zu wenige Seiten → versteckt, genug Seiten → ok", async () => {
    expect(countPdfPages(fx("long.pdf"))).toBe(80);
    expect(countPdfPages(fx("short.pdf"))).toBe(3);
    expect(MIN_PDF_PAGES).toBe(40);
    const kurz = add("pkurz", "pdf");
    expect(await ensureBookFile({ db, ebookDir: dir, http: http(fx("short.pdf")) }, kurz)).toEqual({
      hidden: expect.stringMatching(/zu kurz/),
    });
    const lang = add("plang", "pdf");
    const r = await ensureBookFile({ db, ebookDir: dir, http: http(fx("long.pdf")) }, lang);
    expect(r).toMatchObject({ format: "pdf" });
  });

  it("Abbruch mitten im Laden hinterlässt keine fertige Datei", async () => {
    const id = add("weg");
    const broken: PoliteHttp = { get: async () => Promise.reject(new Error("Netz weg")) };
    await expect(ensureBookFile({ db, ebookDir: dir, http: broken }, id)).rejects.toThrow(
      /Netz weg/,
    );
    expect(readdirSync(dir)).toEqual([]);
    expect(db.prepare("SELECT * FROM ebook_files").all()).toEqual([]);
  });

  it("zwei gleichzeitige Aufrufe laden einmal", async () => {
    const id = add("doppelt");
    const calls = { n: 0 };
    const deps = { db, ebookDir: dir, http: http(fx("long.epub"), calls) };
    const [a, b] = await Promise.all([ensureBookFile(deps, id), ensureBookFile(deps, id)]);
    expect(calls.n).toBe(1);
    expect(a).toEqual(b);
  });

  it("fällt auf einen anderen Gutenberg-Spiegel zurück", async () => {
    const id = upsertBook(
      db,
      {
        source: "gutenberg",
        sourceId: "7",
        title: "x",
        authors: [],
        language: "de",
        subjects: ["DE Prosa"],
        format: "epub",
        fileUrl: "https://gutenberg.nabasny.com/cache/epub/7/pg7-images-3.epub",
        sourceUrl: "y",
      },
      ["roman"],
      1,
    ).id;
    const calls: string[] = [];
    const http: PoliteHttp = {
      get: async (u) => {
        calls.push(u);
        return u.includes("nabasny")
          ? { status: 404, body: Buffer.from("") }
          : { status: 200, body: fx("long.epub") };
      },
    };
    const r = await ensureBookFile({ db, ebookDir: dir, http }, id);
    expect("path" in r).toBe(true);
    expect(calls).toEqual([
      "https://gutenberg.nabasny.com/cache/epub/7/pg7-images-3.epub",
      "https://gutenberg.pglaf.org/cache/epub/7/pg7-images-3.epub",
    ]);
  });

  it("zweiter Aufruf nimmt die vorhandene Datei", async () => {
    const id = add("da");
    const calls = { n: 0 };
    const deps = { db, ebookDir: dir, http: http(fx("long.epub"), calls) };
    await ensureBookFile(deps, id);
    await ensureBookFile(deps, id);
    expect(calls.n).toBe(1);
  });
});
