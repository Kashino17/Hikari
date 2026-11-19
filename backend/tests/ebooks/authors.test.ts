import Database from "better-sqlite3";
import { beforeEach, describe, expect, it } from "vitest";
import { applyMigrations } from "../../src/db/migrations.js";
import {
  authorPhoto,
  findAuthorPhoto,
  resolvePendingAuthorPhotos,
} from "../../src/ebooks/authors.js";
import type { PoliteHttp } from "../../src/ebooks/http.js";
import { upsertBook } from "../../src/ebooks/persist.js";

function fakeHttp(
  rules: [(u: string) => boolean, number, string][],
): PoliteHttp & { calls: string[] } {
  const calls: string[] = [];
  return {
    calls,
    async get(u) {
      calls.push(u);
      for (const [m, status, body] of rules) if (m(u)) return { status, body: Buffer.from(body) };
      return { status: 404, body: Buffer.from("") };
    },
  };
}

const search = (docs: object[]) => JSON.stringify({ docs });

describe("findAuthorPhoto", () => {
  it("nimmt den passenden Autor mit den meisten Werken und dessen Foto", async () => {
    const http = fakeHttp([
      [
        (u) => u.includes("search/authors.json"),
        200,
        search([
          { key: "OL9A", name: "Freud sigmund", work_count: 61 },
          { key: "OL1A", name: "Sigmund Freud", work_count: 2034 },
          { key: "OL2A", name: "Sigmund Freud", work_count: 544 },
        ]),
      ],
      [(u) => u.includes("/a/olid/OL1A-M.jpg"), 200, "jpegdata".repeat(50)],
    ]);
    expect(await findAuthorPhoto(http, "Sigmund Freud")).toBe(
      "https://covers.openlibrary.org/a/olid/OL1A-M.jpg?default=false",
    );
  });

  it("anderer Name oder kein Foto → null", async () => {
    const other = fakeHttp([
      [
        (u) => u.includes("search/authors.json"),
        200,
        search([{ key: "OL5A", name: "Gerstäcker-Gesellschaft", work_count: 1 }]),
      ],
    ]);
    expect(await findAuthorPhoto(other, "Friedrich Gerstäcker")).toBeNull();
    const noPhoto = fakeHttp([
      [
        (u) => u.includes("search/authors.json"),
        200,
        search([{ key: "OL6A", name: "Theodor Storm", work_count: 387 }]),
      ],
    ]);
    expect(await findAuthorPhoto(noPhoto, "Theodor Storm")).toBeNull();
  });
});

describe("resolvePendingAuthorPhotos", () => {
  let db: Database.Database;
  beforeEach(() => {
    db = new Database(":memory:");
    applyMigrations(db);
    const book = (id: string, author: string) =>
      upsertBook(
        db,
        {
          source: "gutenberg",
          sourceId: id,
          title: id,
          authors: [author],
          language: "de",
          subjects: [],
          format: "epub",
          fileUrl: "x",
          sourceUrl: "y",
        },
        [],
        1,
      );
    book("1", "Theodor Storm");
    book("2", "Theodor Storm");
    book("3", "Einmalig");
  });

  it("prüft Autoren ab zwei Büchern einmal und merkt sich das Ergebnis", async () => {
    const http = fakeHttp([
      [
        (u) => u.includes("search/authors.json"),
        200,
        search([{ key: "OL3A", name: "Theodor Storm", work_count: 387 }]),
      ],
      [(u) => u.includes("/a/olid/OL3A-M.jpg"), 200, "jpegdata".repeat(50)],
    ]);
    expect(await resolvePendingAuthorPhotos(db, http, { limit: 10, log: () => {} })).toBe(1);
    expect(authorPhoto(db, "Theodor Storm")).toBe(
      "https://covers.openlibrary.org/a/olid/OL3A-M.jpg?default=false",
    );
    expect(authorPhoto(db, "Einmalig")).toBeUndefined();
    const before = http.calls.length;
    await resolvePendingAuthorPhotos(db, http, { limit: 10, log: () => {} });
    expect(http.calls.length).toBe(before);
  });
});
