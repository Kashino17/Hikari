import { mkdtempSync, readdirSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import Fastify from "fastify";
import { describe, expect, it } from "vitest";
import { progressScore, registerRussianBackupRoutes } from "./russian-backup.js";

const progress = (xp: number, days: number[] = [], cards = 0) =>
  JSON.stringify({
    xp,
    dayStars: Object.fromEntries(days.map((d) => [d, 3])),
    podcastDayStars: {},
    podcastListened: [],
    cards: Object.fromEntries(
      Array.from({ length: cards }, (_, i) => [`c${i}`, { box: 1, due: 1 }]),
    ),
  });

const files = (p: string) => ({
  hikari_russian: { progress_v1: { t: "s", v: p }, podcast_pos_1: { t: "l", v: 1234 } },
  hikari_russian_conversation: {},
});

async function setup() {
  const dir = mkdtempSync(join(tmpdir(), "ru-backup-"));
  const app = Fastify();
  let t = Date.UTC(2026, 10, 12, 10);
  await registerRussianBackupRoutes(app, { dir, now: () => t });
  return {
    app,
    dir,
    advance: (ms: number) => {
      t += ms;
    },
  };
}

describe("progressScore", () => {
  it("zählt XP, Karten, Sterne", () => {
    expect(progressScore(progress(100, [1, 2], 5))).toBe(100 + 5 + 20);
    expect(progressScore(null)).toBe(0);
    expect(progressScore("kaputt")).toBe(0);
  });
});

describe("russian backup API", () => {
  it("liefert 404 ohne Sicherung, danach den gespeicherten Stand", async () => {
    const { app } = await setup();
    expect((await app.inject({ method: "GET", url: "/russian/backup" })).statusCode).toBe(404);
    const put = await app.inject({
      method: "PUT",
      url: "/russian/backup",
      payload: { files: files(progress(50, [1])) },
    });
    expect(put.statusCode).toBe(200);
    const got = await app.inject({ method: "GET", url: "/russian/backup" });
    expect(got.json().score).toBe(60);
    expect(got.json().files.hikari_russian.podcast_pos_1).toEqual({ t: "l", v: 1234 });
  });

  it("lässt einen ärmeren Stand nicht über einen reicheren — nur mit force", async () => {
    const { app, dir } = await setup();
    await app.inject({
      method: "PUT",
      url: "/russian/backup",
      payload: { files: files(progress(900, [1, 2, 3], 40)) },
    });
    const poor = await app.inject({
      method: "PUT",
      url: "/russian/backup",
      payload: { files: files(progress(0)) },
    });
    expect(poor.statusCode).toBe(409);
    expect((await app.inject({ method: "GET", url: "/russian/backup" })).json().score).toBe(970);
    expect(readdirSync(dir).some((f) => f.startsWith("rejected-"))).toBe(true);

    const forced = await app.inject({
      method: "PUT",
      url: "/russian/backup?force=1",
      payload: { files: files(progress(0)) },
    });
    expect(forced.statusCode).toBe(200);
    expect((await app.inject({ method: "GET", url: "/russian/backup" })).json().score).toBe(0);
  });

  it("legt Tagesstände an", async () => {
    const { app, advance } = await setup();
    await app.inject({
      method: "PUT",
      url: "/russian/backup",
      payload: { files: files(progress(10)) },
    });
    advance(86_400_000);
    await app.inject({
      method: "PUT",
      url: "/russian/backup",
      payload: { files: files(progress(20)) },
    });
    const days = (await app.inject({ method: "GET", url: "/russian/backup/days" })).json().days;
    expect(days.map((d: { day: string }) => d.day)).toEqual(["2026-11-13", "2026-11-12"]);
    const old = await app.inject({ method: "GET", url: "/russian/backup/days/2026-11-12" });
    expect(old.json().score).toBe(10);
  });

  it("hält Test-Builds (slot=debug) von der echten Sicherung fern", async () => {
    const { app } = await setup();
    await app.inject({
      method: "PUT",
      url: "/russian/backup?slot=debug",
      payload: { files: files(progress(5000, [1, 2, 3])) },
    });
    expect((await app.inject({ method: "GET", url: "/russian/backup" })).statusCode).toBe(404);
    const dbg = await app.inject({ method: "GET", url: "/russian/backup?slot=debug" });
    expect(dbg.json().score).toBe(5030);
  });

  it("weist kaputte Daten ab", async () => {
    const { app } = await setup();
    const bad = await app.inject({
      method: "PUT",
      url: "/russian/backup",
      payload: { files: { x: { k: { t: "z", v: 1 } } } },
    });
    expect(bad.statusCode).toBe(400);
  });
});
