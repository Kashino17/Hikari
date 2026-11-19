import Database from "better-sqlite3";
import { beforeEach, describe, expect, it } from "vitest";
import { applyMigrations } from "../../src/db/migrations.js";
import type { PoliteHttp } from "../../src/ebooks/http.js";
import type { RawBook } from "../../src/ebooks/persist.js";
import type { EbookSource } from "../../src/ebooks/sources/types.js";
import { dbSourceCache, runEbookSync, shouldSyncOnStart } from "../../src/ebooks/sync.js";

const noHttp: PoliteHttp = { get: async () => ({ status: 500, body: Buffer.from("") }) };
const raw = (source: string, id: string, title = `Buch ${id}`): RawBook => ({
  source,
  sourceId: id,
  title,
  authors: ["A"],
  language: "de",
  subjects: ["Detective stories"],
  format: "epub",
  fileUrl: "x",
  sourceUrl: "y",
});
const src = (id: string, list: () => Promise<RawBook[]>): EbookSource => ({
  id,
  name: id,
  listCatalog: list,
});

describe("runEbookSync", () => {
  let db: Database.Database;
  beforeEach(() => {
    db = new Database(":memory:");
    applyMigrations(db);
  });

  it("Quelle wirft → Einträge dieser Quelle bleiben unverändert, andere werden aktualisiert", async () => {
    let fail = false;
    const a = src("a", async () => {
      if (fail) throw new Error("kaputt");
      return [raw("a", "1"), raw("a", "2")];
    });
    const b = src("b", async () => [raw("b", "1", fail ? "Neu" : "Alt")]);
    await runEbookSync({ db, sources: [a, b], http: noHttp, log: () => {}, now: () => 1 });
    fail = true;
    const res = await runEbookSync({
      db,
      sources: [a, b],
      http: noHttp,
      log: () => {},
      now: () => 2,
    });
    expect(res.a?.error).toMatch(/kaputt/);
    const rows = db.prepare("SELECT id, title, missing_since FROM ebooks ORDER BY id").all();
    expect(rows).toEqual([
      { id: "a:1", title: "Buch 1", missing_since: null },
      { id: "a:2", title: "Buch 2", missing_since: null },
      { id: "b:1", title: "Neu", missing_since: null },
    ]);
  });

  it("zweiter Lauf zählt added 0, Bruchstücke werden übersprungen", async () => {
    const a = src("a", async () => [raw("a", "1"), raw("a", "2", "Leseprobe: Der Prozess")]);
    const first = await runEbookSync({
      db,
      sources: [a],
      http: noHttp,
      log: () => {},
      now: () => 1,
    });
    expect(first.a).toMatchObject({ found: 2, added: 1, skipped: 1 });
    const second = await runEbookSync({
      db,
      sources: [a],
      http: noHttp,
      log: () => {},
      now: () => 2,
    });
    expect(second.a).toMatchObject({ found: 2, added: 0, skipped: 1 });
    const cats = db.prepare("SELECT category FROM ebook_categories WHERE book_id = 'a:1'").all();
    expect(cats).toEqual([{ category: "krimi" }]);
  });

  it("Merker einer Quelle überleben den Lauf in der Datenbank", async () => {
    const a = src("a", async () => []);
    await runEbookSync({ db, sources: [a], http: noHttp, log: () => {}, now: () => 1 });
    const cache = dbSourceCache(db, "a");
    cache.set("k", { touched: "t1" });
    expect(dbSourceCache(db, "a").get<{ touched: string }>("k")).toEqual({ touched: "t1" });
    expect(dbSourceCache(db, "b").get("k")).toBeUndefined();
  });

  it("zwei gleichzeitige Läufe laufen nur einmal", async () => {
    let calls = 0;
    const a = src("a", async () => {
      calls++;
      await new Promise((r) => setTimeout(r, 20));
      return [];
    });
    await Promise.all([
      runEbookSync({ db, sources: [a], http: noHttp, log: () => {} }),
      runEbookSync({ db, sources: [a], http: noHttp, log: () => {} }),
    ]);
    expect(calls).toBe(1);
  });

  it("beim Start abgleichen, wenn der Katalog leer ist oder der letzte Lauf über 24 h her ist", async () => {
    const day = 24 * 60 * 60 * 1000;
    expect(shouldSyncOnStart(db, 10 * day)).toBe(true);
    const a = src("a", async () => [raw("a", "1")]);
    await runEbookSync({ db, sources: [a], http: noHttp, log: () => {}, now: () => 10 * day });
    expect(shouldSyncOnStart(db, 10 * day + day / 2)).toBe(false);
    expect(shouldSyncOnStart(db, 11 * day + 1)).toBe(true);
  });
});
