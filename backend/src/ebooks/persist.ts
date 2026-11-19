import type Database from "better-sqlite3";

export type BookFormat = "epub" | "pdf";

/** Ein Buch, wie eine Quelle es meldet — vor Kategorien und Filter. */
export interface RawBook {
  source: string;
  sourceId: string;
  title: string;
  authors: string[];
  year?: number | undefined;
  language: "de";
  description?: string | undefined;
  subjects: string[];
  format: BookFormat;
  fileUrl: string;
  coverUrl?: string | undefined;
  sourceUrl: string;
  wordCount?: number | undefined;
  popularity?: number | undefined;
}

/** Für Index UND Suchanfrage gleich: klein, ä→a, ö→o, ü→u, ß→ss. */
export function normalizeForSearch(s: string): string {
  return s
    .toLowerCase()
    .replace(/ä/g, "a")
    .replace(/ö/g, "o")
    .replace(/ü/g, "u")
    .replace(/ß/g, "ss");
}

export function bookId(source: string, sourceId: string): string {
  return `${source}:${sourceId}`;
}

export function upsertBook(
  db: Database.Database,
  raw: RawBook,
  categories: string[],
  now: number,
): { id: string; isNew: boolean } {
  const id = bookId(raw.source, raw.sourceId);
  const tx = db.transaction(() => {
    const exists = db.prepare("SELECT 1 FROM ebooks WHERE id = ?").get(id) !== undefined;
    const vals = {
      id,
      source: raw.source,
      source_id: raw.sourceId,
      title: raw.title,
      authors_json: JSON.stringify(raw.authors),
      year: raw.year ?? null,
      description: raw.description ?? null,
      subjects_json: JSON.stringify(raw.subjects),
      format: raw.format,
      file_url: raw.fileUrl,
      cover_url: raw.coverUrl ?? null,
      source_url: raw.sourceUrl,
      word_count: raw.wordCount ?? null,
      popularity: raw.popularity ?? null,
      now,
    };
    if (exists) {
      db.prepare(
        `UPDATE ebooks SET title=@title, authors_json=@authors_json, year=@year, description=@description,
           subjects_json=@subjects_json, format=@format, file_url=@file_url, cover_url=@cover_url,
           source_url=@source_url, word_count=COALESCE(@word_count, word_count), popularity=@popularity,
           updated_at=@now, missing_since=NULL
         WHERE id=@id`,
      ).run(vals);
    } else {
      db.prepare(
        `INSERT INTO ebooks (id, source, source_id, title, authors_json, year, description, subjects_json, format,
           file_url, cover_url, source_url, word_count, popularity, added_at, updated_at)
         VALUES (@id, @source, @source_id, @title, @authors_json, @year, @description, @subjects_json, @format,
           @file_url, @cover_url, @source_url, @word_count, @popularity, @now, @now)`,
      ).run(vals);
    }
    db.prepare("DELETE FROM ebook_categories WHERE book_id = ?").run(id);
    const addCat = db.prepare(
      "INSERT OR IGNORE INTO ebook_categories (book_id, category) VALUES (?, ?)",
    );
    for (const c of categories) addCat.run(id, c);
    db.prepare("DELETE FROM ebooks_fts WHERE book_id = ?").run(id);
    db.prepare(
      "INSERT INTO ebooks_fts (book_id, title, authors, description) VALUES (?, ?, ?, ?)",
    ).run(
      id,
      normalizeForSearch(raw.title),
      normalizeForSearch(raw.authors.join(" ")),
      normalizeForSearch(raw.description ?? ""),
    );
    return { id, isNew: !exists };
  });
  return tx();
}

/** Was die Quelle diesmal nicht mehr gemeldet hat, bekommt missing_since — gelöscht wird nie. */
export function markSourceSeen(
  db: Database.Database,
  source: string,
  seenIds: string[],
  now: number,
): void {
  const seen = new Set(seenIds);
  const rows = db.prepare("SELECT id, missing_since FROM ebooks WHERE source = ?").all(source) as {
    id: string;
    missing_since: number | null;
  }[];
  const mark = db.prepare("UPDATE ebooks SET missing_since = ? WHERE id = ?");
  const tx = db.transaction(() => {
    for (const r of rows) {
      if (seen.has(r.id)) {
        if (r.missing_since !== null) mark.run(null, r.id);
      } else if (r.missing_since === null) {
        mark.run(now, r.id);
      }
    }
  });
  tx();
}

export function hideBook(db: Database.Database, id: string, reason: string): void {
  db.prepare("UPDATE ebooks SET hidden_reason = ? WHERE id = ?").run(reason, id);
}
