import { writeFileSync, mkdirSync } from "node:fs";
import { join } from "node:path";

const FIXTURES_DIR = "tests/fixtures/onepiece-tube";

async function get(url: string): Promise<string> {
  const r = await fetch(url, {
    headers: {
      "User-Agent":
        "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36",
      Accept: "text/html,application/xhtml+xml",
      "Accept-Language": "de,en;q=0.5",
    },
  });
  if (!r.ok) throw new Error(`${url}: ${r.status}`);
  return r.text();
}

async function main() {
  mkdirSync(FIXTURES_DIR, { recursive: true });

  console.log("Fetching mangaliste …");
  writeFileSync(
    join(FIXTURES_DIR, "mangaliste.html"),
    await get("https://onepiece.tube/manga/kapitel-mangaliste"),
  );

  const seriesUrl = process.argv[2];
  const chapterUrl = process.argv[3];
  if (seriesUrl) {
    console.log(`Fetching series detail: ${seriesUrl}`);
    writeFileSync(
      join(FIXTURES_DIR, "series-one-piece.html"),
      await get(seriesUrl),
    );
  }
  if (chapterUrl) {
    console.log(`Fetching chapter page: ${chapterUrl}`);
    writeFileSync(
      join(FIXTURES_DIR, "chapter-1095.html"),
      await get(chapterUrl),
    );
  }
  console.log("Done.");
}

main().catch((e) => {
  console.error(e);
  process.exit(1);
});
