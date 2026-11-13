import { existsSync, mkdirSync, mkdtempSync, utimesSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import Fastify from "fastify";
import { describe, expect, it } from "vitest";
import {
  cleanInterests,
  quotaDay,
  quotaResetAt,
  registerRussianErlebenRoutes,
} from "./russian-erleben.js";

async function setup(opts: { prepared?: number } = {}) {
  const dir = mkdtempSync(join(tmpdir(), "ru-erleben-"));
  mkdirSync(join(dir, "auto"), { recursive: true });
  mkdirSync(join(dir, "media"), { recursive: true });
  let t = Date.UTC(2026, 10, 14, 10);
  const runs: number[] = [];
  let finish: (n: number) => void = () => {};
  const app = Fastify();
  await registerRussianErlebenRoutes(app, {
    dir,
    script: "/nicht/vorhanden/prepare.py",
    now: () => t,
    runPrepare: (count) => {
      runs.push(count);
      return new Promise<number>((resolve) => {
        finish = resolve;
        if (opts.prepared !== undefined) resolve(opts.prepared);
      });
    },
  });
  let stamp = 1_700_000_000;
  const piece = (id: string, extra: Record<string, unknown> = {}) => {
    const file = join(dir, "auto", `${id}.json`);
    writeFileSync(file, JSON.stringify({ id, checked: true, ...extra }));
    stamp += 10; // Reihenfolge = Alter der Datei
    utimesSync(file, stamp, stamp);
  };
  const feed = async () => (await app.inject({ url: "/russian/erleben/feed" })).json();
  const post = async (path: string, body: unknown) =>
    (await app.inject({ method: "POST", url: path, payload: body as object })).json();
  return {
    app,
    dir,
    runs,
    piece,
    feed,
    post,
    finish: (n: number) => finish(n),
    advance: (ms: number) => {
      t += ms;
    },
  };
}

describe("cleanInterests", () => {
  it("kürzt, entfernt Leeres und Doppeltes, begrenzt auf zwölf", () => {
    const many = Array.from({ length: 20 }, (_, i) => `Thema ${i}`);
    const c = cleanInterests({
      video: [" One Piece ", "One Piece", "", 7, ...many],
      music: "Rock",
    });
    expect(c.video.slice(0, 2)).toEqual(["One Piece", "7"]);
    expect(c.video).toHaveLength(12);
    expect(c.music).toEqual([]);
    expect(cleanInterests(null)).toEqual({ video: [], music: [] });
  });
});

describe("Tageskontingent", () => {
  it("zählt den Tag nach Pazifikzeit", () => {
    // 14.11. 05:00 UTC ist in Kalifornien noch der 13.11. (21 Uhr).
    expect(quotaDay(Date.UTC(2026, 10, 14, 5))).toBe("2026-11-13");
    expect(quotaDay(Date.UTC(2026, 10, 14, 9))).toBe("2026-11-14");
  });

  it("nennt die nächste Mitternacht in Kalifornien", () => {
    const now = Date.UTC(2026, 10, 14, 5, 30); // 21:30 dort
    expect(quotaResetAt(now)).toBe(Date.UTC(2026, 10, 14, 8));
  });
});

describe("Feed", () => {
  it("ohne Themen wird nichts gesucht", async () => {
    const s = await setup();
    const f = await s.feed();
    expect(f.shown).toEqual([]);
    expect(f.busy).toBe(false);
    expect(s.runs).toEqual([]);
  });

  it("zeigt drei geprüfte Stücke, die ältesten zuerst, und nie ungeprüfte", async () => {
    const s = await setup();
    s.piece("a");
    s.piece("b");
    s.piece("roh", { checked: false });
    s.piece("c");
    s.piece("d");
    const f = await s.feed();
    expect(f.shown).toEqual(["a", "b", "c"]);
    expect(f.ready).toBe(1);
    expect(f.pieces.map((p: { id: string }) => p.id)).toEqual(["a", "b", "c", "d"]);
  });

  it("»Neue« tauscht aus, was nicht angefangen ist — Angefangenes bleibt", async () => {
    const s = await setup();
    for (const id of ["a", "b", "c", "d", "e"]) s.piece(id);
    await s.feed();
    const f = await s.post("/russian/erleben/refresh", { keep: ["b"] });
    expect(f.shown).toEqual(["b", "d", "e"]);
    expect(f.ready).toBe(0);
    // Abgelegtes kommt nicht wieder.
    expect((await s.feed()).shown).toEqual(["b", "d", "e"]);
  });

  it("löscht abgelegte Stücke samt Video nach 30 Tagen, nie gezeigte oder frische", async () => {
    const s = await setup();
    for (const id of ["a", "b", "c", "d"]) s.piece(id);
    writeFileSync(join(s.dir, "media", "a.mp4"), "x");
    writeFileSync(join(s.dir, "media", "b.mp4"), "x");
    await s.feed();
    await s.post("/russian/erleben/refresh", { keep: ["b"] }); // a und c abgelegt
    // Die Dateien stammen aus dem Jahr 2023 — längst über die Frist.
    const f = await s.feed();
    expect(f.pieces.map((p: { id: string }) => p.id)).toEqual(["b", "d"]);
    expect(existsSync(join(s.dir, "media", "a.mp4"))).toBe(false);
    expect(existsSync(join(s.dir, "media", "b.mp4"))).toBe(true);
  });

  it("füllt den Vorrat erst mit Themen auf und nie in zwei Läufen zugleich", async () => {
    const s = await setup();
    s.piece("a");
    await s.feed();
    expect(s.runs).toEqual([]);
    const f = await s.post("/russian/erleben/interests", { video: ["One Piece"], music: [] });
    expect(f.interests).toEqual({ video: ["One Piece"], music: [] });
    expect(f.busy).toBe(true);
    expect(s.runs).toEqual([4]); // Vorrat 0 → vier Stücke anfordern
    await s.feed();
    expect(s.runs).toHaveLength(1);
  });

  it("pausiert nach einem Lauf ohne Ergebnis und sucht bei neuen Themen sofort wieder", async () => {
    const s = await setup({ prepared: 0 });
    await s.post("/russian/erleben/interests", { video: ["Anime"], music: [] });
    await new Promise((r) => setTimeout(r, 5));
    await s.feed();
    expect(s.runs).toHaveLength(1); // Pause
    s.advance(21 * 60 * 1000);
    await s.feed();
    expect(s.runs).toHaveLength(2);
    await new Promise((r) => setTimeout(r, 5));
    await s.post("/russian/erleben/interests", { video: ["Anime", "Memes"], music: [] });
    expect(s.runs).toHaveLength(3);
  });

  it("sucht nicht, wenn das Tageskontingent leer ist", async () => {
    const s = await setup();
    writeFileSync(
      join(s.dir, "usage.json"),
      JSON.stringify({ day: "2026-11-14", count: { "gemini-3.5-transcribe": 25 } }),
    );
    const f = await s.post("/russian/erleben/interests", { video: ["Anime"], music: [] });
    expect(f.leftToday).toBe(0);
    expect(s.runs).toEqual([]);
  });
});

describe("Medien", () => {
  it("liefert Teilabrufe und weist fremde Namen ab", async () => {
    const s = await setup();
    writeFileSync(join(s.dir, "media", "yt123.mp4"), Buffer.from("0123456789"));
    const whole = await s.app.inject({ url: "/russian/erleben/media/yt123.mp4" });
    expect(whole.statusCode).toBe(200);
    expect(whole.headers["content-type"]).toBe("video/mp4");
    const part = await s.app.inject({
      url: "/russian/erleben/media/yt123.mp4",
      headers: { range: "bytes=2-5" },
    });
    expect(part.statusCode).toBe(206);
    expect(part.headers["content-range"]).toBe("bytes 2-5/10");
    expect(part.body).toBe("2345");
    expect((await s.app.inject({ url: "/russian/erleben/media/..%2Fusage.json" })).statusCode).toBe(
      400,
    );
    expect((await s.app.inject({ url: "/russian/erleben/media/fehlt.mp4" })).statusCode).toBe(404);
  });
});
