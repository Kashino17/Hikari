import { readFileSync } from "node:fs";
import { join } from "node:path";
import Database from "better-sqlite3";
import { beforeEach, describe, expect, it } from "vitest";
import { applyMigrations } from "../../src/db/migrations.js";
import {
  findOpenLibraryCover,
  isGeneratedCover,
  resolveCover,
  resolvePendingCovers,
} from "../../src/ebooks/covers.js";
import type { PoliteHttp } from "../../src/ebooks/http.js";
import { type RawBook, upsertBook } from "../../src/ebooks/persist.js";

const fx = (n: string) => readFileSync(join(__dirname, "../fixtures/ebooks", n));

function fakeHttp(
  rules: [(u: string) => boolean, number, Buffer | string][],
): PoliteHttp & { calls: string[] } {
  const calls: string[] = [];
  return {
    calls,
    async get(u) {
      calls.push(u);
      for (const [m, status, body] of rules)
        if (m(u)) return { status, body: Buffer.isBuffer(body) ? body : Buffer.from(body) };
      return { status: 404, body: Buffer.from("") };
    },
  };
}

describe("isGeneratedCover", () => {
  it("erkennt Gutenbergs erzeugte Muster-Cover", async () => {
    expect(await isGeneratedCover(fx("covers/generated-1.jpg"))).toBe(true);
    expect(await isGeneratedCover(fx("covers/generated-2.jpg"))).toBe(true);
  });
  it("lässt echte Buchdeckel durch", async () => {
    expect(await isGeneratedCover(fx("covers/real-verwandlung.jpg"))).toBe(false);
    expect(await isGeneratedCover(fx("covers/real-wahlverwandtschaften.jpg"))).toBe(false);
  });
  it("kein Bild → nicht erzeugt (nichts kaputtmachen)", async () => {
    expect(await isGeneratedCover(Buffer.from("<html>404</html>"))).toBe(false);
  });
});

describe("findOpenLibraryCover", () => {
  it("nimmt den Deckel einer deutschen Ausgabe", async () => {
    const http = fakeHttp([
      [(u) => u.includes("search.json"), 200, fx("ol-search-nachtstuecke.json")],
    ]);
    expect(await findOpenLibraryCover(http, "Nachtstücke", ["E. T. A. Hoffmann"])).toBe(
      "https://covers.openlibrary.org/b/id/6873336-L.jpg",
    );
    expect(http.calls[0]).toContain("lang=de");
  });
  it("Werk-Deckel nur, wenn dessen Ausgabe deutsch ist", async () => {
    const woyzeck = fakeHttp([
      [(u) => u.includes("search.json"), 200, fx("ol-search-woyzeck.json")],
      [(u) => u.includes("/books/OL7M.json"), 200, fx("ol-edition-eng.json")],
    ]);
    expect(await findOpenLibraryCover(woyzeck, "Woyzeck", ["Georg Büchner"])).toBeNull();
    const effi = fakeHttp([
      [(u) => u.includes("search.json"), 200, fx("ol-search-effi.json")],
      [(u) => u.includes("/books/OL5M.json"), 200, fx("ol-edition-ger.json")],
    ]);
    expect(await findOpenLibraryCover(effi, "Effi Briest", ["Theodor Fontane"])).toBe(
      "https://covers.openlibrary.org/b/id/756212-L.jpg",
    );
  });
  it("Fehler oder nichts gefunden → null", async () => {
    expect(await findOpenLibraryCover(fakeHttp([]), "X", [])).toBeNull();
  });
});

describe("resolveCover", () => {
  let db: Database.Database;
  beforeEach(() => {
    db = new Database(":memory:");
    applyMigrations(db);
  });
  const book = (id: string, coverUrl?: string): string =>
    upsertBook(
      db,
      {
        source: "gutenberg",
        sourceId: id,
        title: "Nachtstücke",
        authors: ["E. T. A. Hoffmann"],
        language: "de",
        subjects: [],
        format: "epub",
        fileUrl: "x",
        coverUrl,
        sourceUrl: "y",
      } satisfies RawBook,
      [],
      1,
    ).id;
  const status = (id: string) =>
    db.prepare("SELECT cover_status, cover_alt_url FROM ebooks WHERE id = ?").get(id) as {
      cover_status: string | null;
      cover_alt_url: string | null;
    };

  it("erzeugtes Cover → echter Deckel von Open Library", async () => {
    const id = book("1", "https://g.test/1.jpg");
    const http = fakeHttp([
      [(u) => u === "https://g.test/1.jpg", 200, fx("covers/generated-1.jpg")],
      [(u) => u.includes("search.json"), 200, fx("ol-search-nachtstuecke.json")],
    ]);
    expect(await resolveCover(db, http, id)).toEqual({
      status: "openlibrary",
      url: "https://covers.openlibrary.org/b/id/6873336-L.jpg",
    });
    expect(status(id)).toEqual({
      cover_status: "openlibrary",
      cover_alt_url: "https://covers.openlibrary.org/b/id/6873336-L.jpg",
    });
  });
  it("erzeugtes Cover ohne Fund → kein Bild (die App zeichnet eines)", async () => {
    const id = book("2", "https://g.test/2.jpg");
    const http = fakeHttp([
      [(u) => u === "https://g.test/2.jpg", 200, fx("covers/generated-2.jpg")],
    ]);
    expect((await resolveCover(db, http, id)).status).toBe("none");
  });
  it("echter Deckel bleibt", async () => {
    const id = book("3", "https://g.test/3.jpg");
    const http = fakeHttp([
      [(u) => u === "https://g.test/3.jpg", 200, fx("covers/real-verwandlung.jpg")],
    ]);
    expect(await resolveCover(db, http, id)).toEqual({
      status: "source",
      url: "https://g.test/3.jpg",
    });
  });
  it("ohne Quell-Bild wird Open Library gefragt", async () => {
    const id = book("4");
    const http = fakeHttp([
      [(u) => u.includes("search.json"), 200, fx("ol-search-nachtstuecke.json")],
    ]);
    expect((await resolveCover(db, http, id)).status).toBe("openlibrary");
  });
  it("Hintergrundlauf: beliebteste zuerst, höchstens so viele wie erlaubt", async () => {
    for (const [id, pop] of [
      ["a", 5],
      ["b", 50],
      ["c", 20],
    ] as const)
      upsertBook(
        db,
        {
          source: "gutenberg",
          sourceId: id,
          title: id,
          authors: [],
          language: "de",
          subjects: [],
          format: "epub",
          fileUrl: "x",
          sourceUrl: "y",
          popularity: pop,
        },
        [],
        1,
      );
    const http = fakeHttp([]);
    const n = await resolvePendingCovers(db, http, { limit: 2, log: () => {} });
    expect(n).toBe(2);
    const done = db
      .prepare("SELECT id FROM ebooks WHERE cover_status IS NOT NULL ORDER BY id")
      .all();
    expect(done).toEqual([{ id: "gutenberg:b" }, { id: "gutenberg:c" }]);
  });

  it("neuer Abgleich mit gleichem Bild behält das Ergebnis, neues Bild prüft neu", async () => {
    const id = book("5", "https://g.test/5.jpg");
    db.prepare("UPDATE ebooks SET cover_status = 'none' WHERE id = ?").run(id);
    book("5", "https://g.test/5.jpg");
    expect(status(id).cover_status).toBe("none");
    book("5", "https://g.test/5b.jpg");
    expect(status(id).cover_status).toBeNull();
  });
});
