import Database from "better-sqlite3";
import { beforeEach, describe, expect, it } from "vitest";
import { applyMigrations } from "../../src/db/migrations.js";
import {
  type RawBook,
  markSourceSeen,
  normalizeForSearch,
  upsertBook,
} from "../../src/ebooks/persist.js";

function book(over: Partial<RawBook> = {}): RawBook {
  return {
    source: "gutenberg",
    sourceId: "100",
    title: "Das Fräulein von Scuderi",
    authors: ["E. T. A. Hoffmann"],
    language: "de",
    subjects: ["DE Prosa"],
    format: "epub",
    fileUrl: "https://example.test/100.epub",
    sourceUrl: "https://example.test/100",
    ...over,
  };
}

describe("ebooks persist", () => {
  let db: Database.Database;
  beforeEach(() => {
    db = new Database(":memory:");
    applyMigrations(db);
  });

  it("neues Buch bekommt addedAt, zweites upsert nicht", () => {
    const a = upsertBook(db, book(), ["krimi"], 1000);
    const b = upsertBook(db, book({ title: "Das Fräulein von Scuderi (neu)" }), ["krimi"], 2000);
    expect(a).toEqual({ id: "gutenberg:100", isNew: true });
    expect(b.isNew).toBe(false);
    const row = db
      .prepare("SELECT added_at, updated_at, title FROM ebooks WHERE id = ?")
      .get("gutenberg:100") as {
      added_at: number;
      updated_at: number;
      title: string;
    };
    expect(row.added_at).toBe(1000);
    expect(row.updated_at).toBe(2000);
    expect(row.title).toBe("Das Fräulein von Scuderi (neu)");
  });

  it("Kategorien werden ersetzt, nicht angehängt", () => {
    upsertBook(db, book(), ["krimi", "erzaehlungen"], 1);
    upsertBook(db, book(), ["roman"], 2);
    const cats = db
      .prepare("SELECT category FROM ebook_categories WHERE book_id = ?")
      .all("gutenberg:100");
    expect(cats).toEqual([{ category: "roman" }]);
  });

  it("FTS findet Titel und Autor, auch mit ß und Umlauten", () => {
    upsertBook(db, book(), ["krimi"], 1);
    upsertBook(
      db,
      book({ sourceId: "200", title: "Die Straße", authors: ["Max Müller"] }),
      ["roman"],
      1,
    );
    const find = (q: string) =>
      (
        db.prepare("SELECT book_id FROM ebooks_fts WHERE ebooks_fts MATCH ?").all(q) as {
          book_id: string;
        }[]
      ).map((r) => r.book_id);
    expect(find("scuderi")).toEqual(["gutenberg:100"]);
    expect(find(`${normalizeForSearch("Fräul")}*`)).toEqual(["gutenberg:100"]);
    expect(find(normalizeForSearch("strasse"))).toEqual(["gutenberg:200"]);
    expect(find(normalizeForSearch("Müller"))).toEqual(["gutenberg:200"]);
  });

  it("markSourceSeen löscht nichts", () => {
    upsertBook(db, book(), [], 1);
    upsertBook(db, book({ sourceId: "200" }), [], 1);
    markSourceSeen(db, "gutenberg", ["gutenberg:100"], 5000);
    const rows = db.prepare("SELECT id, missing_since FROM ebooks ORDER BY id").all();
    expect(rows).toEqual([
      { id: "gutenberg:100", missing_since: null },
      { id: "gutenberg:200", missing_since: 5000 },
    ]);
  });

  it("normalizeForSearch macht aus Umlauten und ß einfache Buchstaben", () => {
    expect(normalizeForSearch("Größe Übel Ärger")).toBe("grosse ubel arger");
  });
});
