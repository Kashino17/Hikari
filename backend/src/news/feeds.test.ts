import { describe, expect, it } from "vitest";
import { NEWS_TOPICS, findTopic, hashId, parseFeedItems, stripHtml } from "./feeds.js";

const RSS_WITH_MEDIA_CONTENT = `<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0" xmlns:media="http://search.yahoo.com/mrss/">
  <channel>
    <title>tagesschau.de</title>
    <item>
      <title>Bundestag beschließt neues Gesetz</title>
      <link>https://www.tagesschau.de/inland/gesetz-101.html</link>
      <description>Kurzbeschreibung mit &lt;b&gt;HTML&lt;/b&gt; drin.</description>
      <pubDate>Fri, 01 Aug 2025 09:30:00 +0200</pubDate>
      <media:content url="https://img.tagesschau.de/bild-101.jpg" medium="image"/>
    </item>
  </channel>
</rss>`;

const RSS_WITH_CONTENT_IMG = `<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/">
  <channel>
    <title>Spektrum</title>
    <item>
      <title>Neue Exoplaneten entdeckt</title>
      <link>https://www.spektrum.de/news/exoplaneten/123</link>
      <description>Astronomen melden Fund.</description>
      <pubDate>Thu, 31 Jul 2025 15:00:00 GMT</pubDate>
      <content:encoded><![CDATA[<p>Langer Text</p><p><img src="https://www.spektrum.de/img/exo.jpg" alt=""/></p>]]></content:encoded>
    </item>
    <item>
      <title>Artikel ohne Bild</title>
      <link>https://www.spektrum.de/news/ohne-bild/124</link>
      <description>Nur Text.</description>
      <pubDate>Thu, 31 Jul 2025 14:00:00 GMT</pubDate>
    </item>
  </channel>
</rss>`;

const RSS_WITH_ENCLOSURE = `<?xml version="1.0" encoding="UTF-8"?>
<rss version="2.0">
  <channel>
    <title>BBC</title>
    <item>
      <title>World news story</title>
      <link>http://feeds.bbci.co.uk/news/world-123</link>
      <description>Story description.</description>
      <pubDate>Thu, 31 Jul 2025 12:00:00 GMT</pubDate>
      <enclosure url="https://ichef.bbci.co.uk/news/img.jpg" type="image/jpeg" length="0"/>
    </item>
  </channel>
</rss>`;

const ATOM_FEED = `<?xml version="1.0" encoding="UTF-8"?>
<feed xmlns="http://www.w3.org/2005/Atom" xmlns:media="http://search.yahoo.com/mrss/">
  <title>heise online</title>
  <entry>
    <title>Neuer Chip vorgestellt</title>
    <link rel="alternate" href="https://www.heise.de/news/chip-456.html"/>
    <id>https://www.heise.de/news/chip-456.html</id>
    <published>2025-07-31T10:00:00Z</published>
    <summary>Hersteller zeigt neuen Chip.</summary>
    <media:thumbnail url="https://www.heise.de/imgs/chip.jpg"/>
  </entry>
</feed>`;

describe("parseFeedItems", () => {
  it("parses RSS 2.0 with media:content image", () => {
    const items = parseFeedItems(RSS_WITH_MEDIA_CONTENT, "tagesschau.de");
    expect(items).toHaveLength(1);
    expect(items[0]).toEqual({
      id: hashId("https://www.tagesschau.de/inland/gesetz-101.html"),
      title: "Bundestag beschließt neues Gesetz",
      url: "https://www.tagesschau.de/inland/gesetz-101.html",
      description: "Kurzbeschreibung mit HTML drin.",
      source: "tagesschau.de",
      publishedAt: "2025-08-01T07:30:00.000Z",
      imageUrl: "https://img.tagesschau.de/bild-101.jpg",
    });
  });

  it("extracts the first img from content:encoded", () => {
    const items = parseFeedItems(RSS_WITH_CONTENT_IMG, "spektrum.de");
    expect(items).toHaveLength(2);
    expect(items[0].imageUrl).toBe("https://www.spektrum.de/img/exo.jpg");
    expect(items[0].title).toBe("Neue Exoplaneten entdeckt");
    expect(items[0].publishedAt).toBe("2025-07-31T15:00:00.000Z");
    expect(items[1].imageUrl).toBeUndefined();
  });

  it("accepts image enclosures", () => {
    const items = parseFeedItems(RSS_WITH_ENCLOSURE, "feeds.bbci.co.uk");
    expect(items[0].imageUrl).toBe("https://ichef.bbci.co.uk/news/img.jpg");
  });

  it("parses Atom entries with media:thumbnail", () => {
    const items = parseFeedItems(ATOM_FEED, "heise.de");
    expect(items).toHaveLength(1);
    expect(items[0]).toMatchObject({
      title: "Neuer Chip vorgestellt",
      url: "https://www.heise.de/news/chip-456.html",
      description: "Hersteller zeigt neuen Chip.",
      publishedAt: "2025-07-31T10:00:00.000Z",
      imageUrl: "https://www.heise.de/imgs/chip.jpg",
    });
  });

  it("returns [] for non-feed XML", () => {
    expect(parseFeedItems("<html><body>nope</body></html>", "x")).toEqual([]);
  });
});

describe("stripHtml", () => {
  it("removes tags and collapses whitespace", () => {
    expect(stripHtml("<p>Hallo <b>Welt</b></p>  &amp; mehr")).toBe("Hallo Welt & mehr");
  });
});

describe("topic registry", () => {
  it("has unique keys and required shape", () => {
    const keys = NEWS_TOPICS.map((t) => t.key);
    expect(new Set(keys).size).toBe(keys.length);
    for (const t of NEWS_TOPICS) {
      expect(t.label.length).toBeGreaterThan(0);
      expect(["de", "en"]).toContain(t.lang);
      expect(t.feeds.length).toBeGreaterThan(0);
    }
  });

  it("finds topics by key or label, case-insensitive", () => {
    expect(findTopic("Politik")?.key).toBe("politik");
    expect(findTopic("TECHNOLOGIE")?.key).toBe("technologie");
    expect(findTopic("nope")).toBeUndefined();
  });
});
