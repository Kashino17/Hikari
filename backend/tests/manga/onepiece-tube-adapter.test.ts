import { readFileSync } from "node:fs";
import { dirname, join } from "node:path";
import { fileURLToPath } from "node:url";
import { beforeEach, expect, test, vi } from "vitest";
import { onePieceTubeAdapter } from "../../src/manga/sources/onepiece-tube.js";

const __dirname = dirname(fileURLToPath(import.meta.url));
const FIXTURES = join(__dirname, "..", "fixtures", "onepiece-tube");

beforeEach(() => {
  vi.restoreAllMocks();
});

function stubFetch(html: string) {
  vi.stubGlobal(
    "fetch",
    vi.fn(async () => ({
      ok: true,
      text: async () => html,
    })),
  );
}

test("listSeries returns exactly one series for One Piece", async () => {
  stubFetch(readFileSync(join(FIXTURES, "mangaliste.html"), "utf8"));
  const series = await onePieceTubeAdapter.listSeries();
  expect(series).toHaveLength(1);
  expect(series[0].title).toBe("One Piece");
  expect(series[0].sourceSlug).toBe("one-piece");
  expect(series[0].sourceUrl).toBe("https://onepiece.tube/manga/kapitel-mangaliste");
  expect(series[0].author).toBe("Eiichiro Oda");
  expect(series[0].status).toBe("ongoing");
});

test("fetchSeriesDetail returns all chapters with complete arcs", async () => {
  stubFetch(readFileSync(join(FIXTURES, "mangaliste.html"), "utf8"));
  const detail = await onePieceTubeAdapter.fetchSeriesDetail(
    "https://onepiece.tube/manga/kapitel-mangaliste",
  );
  expect(detail.chapters.length).toBe(1181);
  // must be monotonically increasing — no dupes, no reverse
  for (let i = 1; i < detail.chapters.length; i++) {
    expect(detail.chapters[i].number).toBeGreaterThan(detail.chapters[i - 1].number);
  }
  expect(detail.chapters[0]).toMatchObject({
    number: 1,
    isAvailable: false,
    pageCount: 0,
  });
  expect(detail.chapters.at(-1)).toMatchObject({
    number: 1181,
    title: "Götter und Teufel",
    isAvailable: true,
    pageCount: 12,
  });
  // chapter sourceUrl must be absolute
  expect(detail.chapters[0].sourceUrl).toMatch(/^https?:\/\//);
  // arcs are present and every arc contains at least metadata chapters.
  expect(detail.arcs.length).toBeGreaterThan(0);
  expect(detail.arcs[0].arcOrder).toBe(0);
  expect(detail.arcs.every((arc) => arc.chapterNumbers.length > 0)).toBe(true);
  expect(detail.arcs.at(-1)).toMatchObject({
    title: "Elban Arc",
    chapterNumbers: expect.arrayContaining([1181]),
  });
});

test("fetchSeriesDetail correctly parses German DD.MM.YYYY dates", async () => {
  stubFetch(readFileSync(join(FIXTURES, "mangaliste.html"), "utf8"));
  const detail = await onePieceTubeAdapter.fetchSeriesDetail(
    "https://onepiece.tube/manga/kapitel-mangaliste",
  );
  // chapters with publishedAt should have epoch-ms numbers
  const withDate = detail.chapters.find((c) => c.publishedAt !== undefined);
  expect(withDate).toBeDefined();
  if (!withDate) throw new Error("Expected at least one chapter with a date");
  expect(typeof withDate.publishedAt).toBe("number");
  expect(withDate.publishedAt).toBeGreaterThan(0);
});

test("fetchChapterPages returns ordered image URLs from chapter fixture", async () => {
  stubFetch(readFileSync(join(FIXTURES, "chapter-1095.html"), "utf8"));
  const pages = await onePieceTubeAdapter.fetchChapterPages(
    "https://onepiece.tube/manga/kapitel/1095/1",
  );
  expect(pages.length).toBeGreaterThan(0);
  expect(pages[0].pageNumber).toBe(1);
  expect(pages.map((p) => p.pageNumber)).toEqual(
    pages.map((_, i) => i + 1), // monotonic 1..N
  );
  // PNG urls
  for (const p of pages) {
    expect(p.sourceUrl).toMatch(/^https?:\/\//);
    expect(p.sourceUrl).toMatch(/\.(png|jpe?g|webp)$/i);
  }
});

test("SourceLayoutError thrown when __data missing", async () => {
  stubFetch("<html><body>no data here</body></html>");
  await expect(onePieceTubeAdapter.listSeries()).rejects.toThrow();
});
