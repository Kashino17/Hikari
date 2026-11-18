import {
  existsSync,
  mkdirSync,
  mkdtempSync,
  readFileSync,
  utimesSync,
  writeFileSync,
} from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import Fastify from "fastify";
import { afterEach, describe, expect, it, vi } from "vitest";
import {
  type RunPlan,
  cleanInterests,
  lastQuotaReset,
  nextRunAt,
  quotaDay,
  quotaResetAt,
  registerRussianErlebenRoutes,
  runDue,
} from "./russian-erleben.js";

const MIN = 60 * 1000;
const HOUR = 60 * MIN;

interface SetupOpts {
  /** Ohne Angabe: kein Zeitplan, feste Uhr (14.11. 10:00 UTC = 02:00 in Kalifornien). */
  schedule?: boolean;
  /** Fertige Stücke je Abschnitt (Standard: so viele wie angefordert). Liste = je Abschnitt. */
  prepared?: number | number[];
  interests?: { video: string[]; music: string[] };
  lastRunAt?: number;
}

async function setup(opts: SetupOpts = {}) {
  const dir = mkdtempSync(join(tmpdir(), "ru-erleben-"));
  mkdirSync(join(dir, "auto"), { recursive: true });
  mkdirSync(join(dir, "media"), { recursive: true });
  if (opts.interests) writeFileSync(join(dir, "interests.json"), JSON.stringify(opts.interests));
  if (opts.lastRunAt !== undefined)
    writeFileSync(
      join(dir, "runs.json"),
      JSON.stringify({
        last: {
          startedAt: opts.lastRunAt,
          finishedAt: opts.lastRunAt + MIN,
          catchUp: false,
          prepared: 0,
          ids: [],
        },
      }),
    );
  let t = Date.UTC(2026, 10, 14, 10);
  const clock = opts.schedule ? () => Date.now() : () => t;
  /** Jeder Aufruf des Aufbereiters: Anzahl, Plan, zugehöriger Lauf (Startzeit). */
  const calls: { count: number; plan: RunPlan; run: number }[] = [];
  let made = 0;
  let stamp = 1_700_000_000;
  const piece = (id: string, extra: Record<string, unknown> = {}) => {
    const file = join(dir, "auto", `${id}.json`);
    writeFileSync(file, JSON.stringify({ id, checked: true, ...extra }));
    stamp += 10; // Reihenfolge = Alter der Datei
    utimesSync(file, stamp, stamp);
  };
  /** Verbrauchtes Kontingent: jedes versuchte Stück kostet einmal Hören. */
  const spend = (n: number) => {
    const file = join(dir, "usage.json");
    const day = quotaDay(clock());
    let u = { day, count: {} as Record<string, number> };
    try {
      const old = JSON.parse(readFileSync(file, "utf8"));
      if (old.day === day) u = old;
    } catch {}
    u.count["gemini-3.5-transcribe"] = (u.count["gemini-3.5-transcribe"] ?? 0) + n;
    writeFileSync(file, JSON.stringify(u));
  };
  let runStart = 0;
  const app = Fastify();
  const feedObj = await registerRussianErlebenRoutes(app, {
    dir,
    script: "/nicht/vorhanden/prepare.py",
    now: clock,
    schedule: opts.schedule ?? false,
    seed: (start, batch) => {
      runStart = start;
      return start + batch;
    },
    runPrepare: async (count, plan) => {
      const i = calls.length;
      calls.push({ count, plan, run: runStart });
      const want = Array.isArray(opts.prepared)
        ? (opts.prepared[i] ?? 0)
        : (opts.prepared ?? count);
      const n = Math.min(count, want);
      for (let k = 0; k < n; k++) piece(`neu${++made}`, { kind: "scene", topic: "Anime" });
      spend(count);
      return n;
    },
  });
  const feed = async () => (await app.inject({ url: "/russian/erleben/feed" })).json();
  const post = async (path: string, body: unknown) =>
    (await app.inject({ method: "POST", url: path, payload: body as object })).json();
  const postRaw = (path: string, body: unknown) =>
    app.inject({ method: "POST", url: path, payload: body as object });
  return {
    app,
    feedObj,
    dir,
    calls,
    /** Wie viele Tagesläufe den Aufbereiter aufgerufen haben. */
    runs: () => new Set(calls.map((c) => c.run)).size,
    piece,
    feed,
    post,
    postRaw,
    advance: (ms: number) => {
      t += ms;
    },
  };
}

/** Nur Uhr und Wecker vortäuschen — Fastify braucht echte setImmediate. */
const fake = (now: number) =>
  vi.useFakeTimers({ now, toFake: ["setTimeout", "clearTimeout", "Date"] });

afterEach(() => {
  vi.useRealTimers();
});

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

describe("Tageskontingent und Startzeit", () => {
  it("zählt den Tag nach Pazifikzeit", () => {
    // 14.11. 05:00 UTC ist in Kalifornien noch der 13.11. (21 Uhr).
    expect(quotaDay(Date.UTC(2026, 10, 14, 5))).toBe("2026-11-13");
    expect(quotaDay(Date.UTC(2026, 10, 14, 9))).toBe("2026-11-14");
  });

  it("nennt die nächste Mitternacht in Kalifornien", () => {
    const now = Date.UTC(2026, 10, 14, 5, 30); // 21:30 dort
    expect(quotaResetAt(now)).toBe(Date.UTC(2026, 10, 14, 8));
    expect(lastQuotaReset(now)).toBe(Date.UTC(2026, 10, 13, 8));
    // Genau um Mitternacht: das ist die letzte Erneuerung, die nächste kommt in 24 h.
    expect(lastQuotaReset(Date.UTC(2026, 10, 14, 8))).toBe(Date.UTC(2026, 10, 14, 8));
    expect(quotaResetAt(Date.UTC(2026, 10, 14, 8))).toBe(Date.UTC(2026, 10, 15, 8));
  });

  it("stimmt an den Tagen der Zeitumstellung (23- und 25-Stunden-Tag)", () => {
    // Ende der Sommerzeit 1.11.2026: 01:30 PDT (vor der Umstellung um 2 Uhr).
    const fall = Date.UTC(2026, 10, 1, 8, 30);
    expect(lastQuotaReset(fall)).toBe(Date.UTC(2026, 10, 1, 7)); // 00:00 PDT
    expect(quotaResetAt(fall)).toBe(Date.UTC(2026, 10, 2, 8)); // 00:00 PST — 25-h-Tag
    // Beginn der Sommerzeit 8.3.2026: um 05:00 PDT.
    const spring = Date.UTC(2026, 2, 8, 12);
    expect(lastQuotaReset(spring)).toBe(Date.UTC(2026, 2, 8, 8)); // 00:00 PST
    expect(quotaResetAt(spring)).toBe(Date.UTC(2026, 2, 9, 7)); // 00:00 PDT — 23-h-Tag
    expect(quotaResetAt(Date.UTC(2026, 2, 7, 20))).toBe(Date.UTC(2026, 2, 8, 8));
    // Jahreswechsel
    expect(quotaResetAt(Date.UTC(2026, 11, 31, 20))).toBe(Date.UTC(2027, 0, 1, 8));
  });

  it("hängt nicht von der Zeitzone des Rechners ab", () => {
    const old = process.env.TZ;
    const probe = [
      Date.UTC(2026, 10, 1, 8, 30),
      Date.UTC(2026, 2, 8, 12),
      Date.UTC(2026, 10, 18, 23),
    ];
    const calc = () => probe.map((p) => [quotaDay(p), lastQuotaReset(p), quotaResetAt(p)]);
    try {
      process.env.TZ = "Europe/Berlin";
      const berlin = calc();
      for (const tz of ["Asia/Tokyo", "America/New_York", "Pacific/Auckland", "UTC"]) {
        process.env.TZ = tz;
        expect(new Date(Date.UTC(2026, 0, 1)).getTimezoneOffset()).not.toBeNaN();
        expect(calc()).toEqual(berlin);
      }
    } finally {
      if (old === undefined) Reflect.deleteProperty(process.env, "TZ");
      else process.env.TZ = old;
    }
  });

  it("Lauf fällig erst fünf Minuten nach der Erneuerung, und nur einmal seit ihr", () => {
    const reset = Date.UTC(2026, 10, 14, 8);
    expect(runDue(reset + 2 * MIN, 0)).toBe(false);
    expect(nextRunAt(reset + 2 * MIN, 0)).toBe(reset + 5 * MIN);
    expect(runDue(reset + 5 * MIN, 0)).toBe(true);
    expect(runDue(reset + 5 * MIN, reset - 3 * 24 * HOUR)).toBe(true);
    expect(runDue(reset + 6 * HOUR, reset + 5 * MIN)).toBe(false);
    expect(nextRunAt(reset + 6 * HOUR, reset + 5 * MIN)).toBe(reset + 24 * HOUR + 5 * MIN);
  });
});

describe("Tageslauf", () => {
  const topics = { video: ["Anime"], music: [] };

  it("läuft einmal kurz nach Mitternacht in Kalifornien und füllt dazwischen nichts nach", async () => {
    fake(Date.UTC(2026, 10, 14, 10)); // 02:00 PST
    const s = await setup({
      schedule: true,
      interests: topics,
      lastRunAt: Date.UTC(2026, 10, 14, 8, 5),
    });
    expect(s.runs()).toBe(0); // der heutige Lauf war schon
    // Den ganzen Tag über: Feed, »Neue«, Themen ändern — nichts startet.
    for (let h = 0; h < 21; h++) {
      await vi.advanceTimersByTimeAsync(HOUR);
      await s.feed();
      await s.post("/russian/erleben/refresh", { keep: [] });
    }
    await s.post("/russian/erleben/interests", { video: ["Anime", "Memes"], music: [] });
    expect(s.calls).toHaveLength(0);
    // 15.11. 00:04 PST: noch nicht.
    await vi.advanceTimersByTimeAsync(Date.UTC(2026, 10, 15, 8, 4) - Date.now());
    expect(s.runs()).toBe(0);
    await vi.advanceTimersByTimeAsync(2 * MIN);
    expect(s.runs()).toBe(1);
    const f = await s.feed();
    // Kontingent 25× Hören → fünf Abschnitte à fünf, dann Schluss.
    expect(s.calls.map((c) => c.count)).toEqual([5, 5, 5, 5, 5]);
    expect(f.todayCount).toBe(25);
    expect(f.lastRunAt).toBe(Date.UTC(2026, 10, 15, 8, 5));
    expect(f.lastRun).toMatchObject({ catchUp: false, prepared: 25 });
    expect(f.nextRunAt).toBe(Date.UTC(2026, 10, 16, 8, 5));
    expect(f.leftToday).toBe(0);
    // Der Rest des Tages: kein weiterer Lauf; am nächsten Tag der nächste.
    await vi.advanceTimersByTimeAsync(23 * HOUR);
    expect(s.runs()).toBe(1);
    await vi.advanceTimersByTimeAsync(2 * HOUR);
    expect(s.runs()).toBe(2);
    await s.app.close();
  });

  it("hört auf, sobald ein Abschnitt nichts mehr besteht", async () => {
    fake(Date.UTC(2026, 10, 14, 9));
    const s = await setup({ schedule: true, interests: topics, prepared: [3, 0, 4] });
    await vi.advanceTimersByTimeAsync(0);
    await s.feedObj.idle();
    expect(s.calls.map((c) => c.count)).toEqual([5, 5]);
    expect((await s.feed()).todayCount).toBe(3);
    await s.app.close();
  });

  it("holt nach mehreren verpassten Tagen genau einen Lauf nach", async () => {
    fake(Date.UTC(2026, 10, 18, 15)); // 18.11. 07:00 PST
    const s = await setup({
      schedule: true,
      interests: topics,
      lastRunAt: Date.UTC(2026, 10, 13, 8, 5), // fünf Erneuerungen verpasst
    });
    await vi.advanceTimersByTimeAsync(0);
    await s.feedObj.idle();
    expect(s.runs()).toBe(1);
    const f = await s.feed();
    expect(f.lastRun.catchUp).toBe(true);
    expect(f.lastRunAt).toBe(Date.UTC(2026, 10, 18, 15));
    // Bis zur nächsten Erneuerung (19.11. 08:00 UTC) nichts mehr …
    await vi.advanceTimersByTimeAsync(Date.UTC(2026, 10, 19, 8, 4) - Date.now());
    expect(s.runs()).toBe(1);
    // … dann der reguläre Lauf.
    await vi.advanceTimersByTimeAsync(2 * MIN);
    expect(s.runs()).toBe(2);
    await s.app.close();
  });

  it("Neustart nach dem heutigen Lauf: kein zweiter Lauf", async () => {
    fake(Date.UTC(2026, 10, 14, 20));
    const s = await setup({
      schedule: true,
      interests: topics,
      lastRunAt: Date.UTC(2026, 10, 14, 8, 5),
    });
    await vi.advanceTimersByTimeAsync(3 * HOUR);
    expect(s.runs()).toBe(0);
    await s.app.close();
  });

  it("Start zwischen Erneuerung und Startzeit: kein Nachholer, nur der reguläre Lauf", async () => {
    fake(Date.UTC(2026, 10, 14, 8, 2));
    const s = await setup({
      schedule: true,
      interests: topics,
      lastRunAt: Date.UTC(2026, 10, 11, 8, 5),
    });
    await vi.advanceTimersByTimeAsync(MIN);
    expect(s.runs()).toBe(0);
    await vi.advanceTimersByTimeAsync(3 * MIN);
    expect(s.runs()).toBe(1);
    await s.feedObj.idle();
    expect((await s.feed()).lastRun.catchUp).toBe(false);
    await vi.advanceTimersByTimeAsync(10 * HOUR);
    expect(s.runs()).toBe(1);
    await s.app.close();
  });

  it("ohne Themen wird nichts gesucht; die ersten Themen am selben Tag starten den Tageslauf", async () => {
    fake(Date.UTC(2026, 10, 14, 10));
    const s = await setup({ schedule: true });
    await vi.advanceTimersByTimeAsync(0);
    let f = await s.feed();
    expect(f.lastRun.skipped).toBe("no-topics");
    expect(s.calls).toHaveLength(0);
    f = await s.post("/russian/erleben/interests", topics);
    expect(f.busy).toBe(true);
    await s.feedObj.idle();
    expect(s.runs()).toBe(1);
    // Weitere Themenänderungen am selben Tag: kein zweiter Lauf.
    await s.post("/russian/erleben/interests", { video: ["Anime", "Memes"], music: [] });
    await vi.advanceTimersByTimeAsync(HOUR);
    expect(s.runs()).toBe(1);
    await s.app.close();
  });

  it("ohne Kontingent versucht der Lauf nichts", async () => {
    const s = await setup({ interests: topics });
    writeFileSync(
      join(s.dir, "usage.json"),
      JSON.stringify({ day: "2026-11-14", count: { "gemini-3.5-transcribe": 25 } }),
    );
    await s.feedObj.runDaily();
    const f = await s.feed();
    expect(f.leftToday).toBe(0);
    expect(f.lastRun.skipped).toBe("no-quota");
    expect(s.calls).toHaveLength(0);
  });

  it("»Neue« (refresh) bereitet nie etwas auf, auch bei leerem Vorrat", async () => {
    const s = await setup({ interests: topics, lastRunAt: Date.UTC(2026, 10, 14, 8, 5) });
    s.piece("a");
    await s.feed();
    const f = await s.post("/russian/erleben/refresh", { keep: [] });
    expect(f.ready).toBe(0);
    expect(f.busy).toBe(false);
    expect(s.calls).toHaveLength(0);
  });

  it("gibt dem Aufbereiter den Plan mit: Themenfolge, Sperren, gelöschte Videos, Schwierigkeit", async () => {
    const s = await setup({ interests: { video: ["Anime", "Memes"], music: ["Rock"] } });
    s.piece("x", { yt: "vidX", kind: "scene", topic: "Memes", channel: "Spam", level: "Mittel" });
    s.piece("y", { yt: "vidY", kind: "scene", topic: "Memes", channel: "Spam", level: "Mittel" });
    await s.post("/russian/erleben/rate", {
      id: "x",
      rating: "down",
      reasons: ["Kaum Russisch", "Zu schwer"],
    });
    await s.post("/russian/erleben/delete", { id: "y" });
    await s.feedObj.runDaily();
    const plan = s.calls[0]?.plan as RunPlan;
    expect(plan.blocked).toEqual(["spam"]);
    expect(plan.avoid.sort()).toEqual(["vidX", "vidY"]);
    expect(plan.levelTarget).toBe(0);
    expect(plan.easyWeight).toBe(2);
    expect(plan.order).toHaveLength(20);
    expect(plan.order.filter((p) => p.explore)).toHaveLength(4);
    // Der Plan liegt auch als Datei für prepare.py bereit.
    expect(JSON.parse(readFileSync(join(s.dir, "plan.json"), "utf8")).blocked).toEqual(["spam"]);
  });
});

describe("Feed", () => {
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

  it("Sammlung: Abgelegtes bleibt für immer samt Video (keine 30-Tage-Frist mehr)", async () => {
    const s = await setup();
    for (const id of ["a", "b", "c", "d"]) s.piece(id);
    writeFileSync(join(s.dir, "media", "a.mp4"), "x");
    await s.feed();
    await s.post("/russian/erleben/refresh", { keep: ["b"] }); // a und c abgelegt
    s.advance(400 * 24 * HOUR);
    const f = await s.feed();
    expect(f.pieces.map((p: { id: string }) => p.id)).toEqual(["a", "b", "c", "d"]);
    expect(existsSync(join(s.dir, "media", "a.mp4"))).toBe(true);
  });

  it("Bibliothek: Merken und wieder herausnehmen, neueste zuerst", async () => {
    const s = await setup();
    for (const id of ["a", "b", "c", "d"]) s.piece(id);
    await s.feed();
    let f = await s.post("/russian/erleben/save", { id: "a" });
    expect(f.saved).toEqual(["a"]);
    f = await s.post("/russian/erleben/save", { id: "c" });
    expect(f.saved).toEqual(["c", "a"]);
    f = await s.post("/russian/erleben/save", { id: "a", on: false });
    expect(f.saved).toEqual(["c"]);
    expect((await s.post("/russian/erleben/save", { id: "../x" })).saved).toEqual(["c"]);
  });

  it("liefert alle bisherigen Felder und die neuen für die Sammlung", async () => {
    const s = await setup({ interests: { video: ["Anime"], music: [] } });
    s.piece("alt", { kind: "song", topic: "Rock", by: "Кино", level: "Mittel", dur: 42 });
    await s.feedObj.runDaily();
    await s.post("/russian/erleben/save", { id: "alt" });
    const f = await s.feed();
    for (const k of [
      "shown",
      "saved",
      "ready",
      "busy",
      "pieces",
      "interests",
      "leftToday",
      "resetAt",
    ])
      expect(f).toHaveProperty(k);
    expect(f.resetAt).toBe(Date.UTC(2026, 10, 15, 8));
    expect(f.lastRunAt).toBe(Date.UTC(2026, 10, 14, 10));
    expect(f.nextRunAt).toBe(Date.UTC(2026, 10, 15, 8, 5));
    expect(f.todayIds).toHaveLength(25);
    expect(f.todayCount).toBe(25);
    expect(f.pieces).toHaveLength(26);
    // Sammlung: neueste zuerst, mit Zustand je Stück.
    expect(f.collection).toHaveLength(26);
    expect(f.collection[0]).toMatchObject({ id: "neu25", isNew: true, saved: false, rating: null });
    expect(f.collection[25]).toMatchObject({
      id: "alt",
      kind: "song",
      topic: "Rock",
      by: "Кино",
      dur: 42,
      isNew: false,
      saved: true,
    });
    expect(f.suggestions).toEqual([
      { id: "song:кино", kind: "song", topic: "Кино", why: "Ein Lied von Кино hat dir gefallen" },
    ]);
    expect(f.taste.topics).toEqual([{ kind: "scene", topic: "Anime", weight: 1, exposure: 25 }]);
  });
});

describe("Rückmeldungen", () => {
  const files = (dir: string, id: string) =>
    [
      join(dir, "auto", `${id}.json`),
      join(dir, "media", `${id}.mp4`),
      join(dir, "media", `${id}.jpg`),
    ].map((f) => existsSync(f));
  const withMedia = (
    s: Awaited<ReturnType<typeof setup>>,
    id: string,
    extra: Record<string, unknown> = {},
  ) => {
    s.piece(id, { yt: `yt_${id}`, kind: "scene", topic: "Anime", channel: "Kanal", ...extra });
    writeFileSync(join(s.dir, "media", `${id}.mp4`), "x");
    writeFileSync(join(s.dir, "media", `${id}.jpg`), "x");
  };
  const signals = (dir: string) => JSON.parse(readFileSync(join(dir, "signals.json"), "utf8"));

  it("»gut« markiert, null nimmt zurück — das Stück bleibt", async () => {
    const s = await setup({ interests: { video: ["Anime"], music: [] } });
    withMedia(s, "a");
    let f = await s.post("/russian/erleben/rate", { id: "a", rating: "up" });
    expect(f.collection[0]).toMatchObject({ id: "a", rating: "up" });
    expect(f.taste.topics[0].weight).toBe(1.5);
    f = await s.post("/russian/erleben/rate", { id: "a", rating: null });
    expect(f.collection[0].rating).toBeNull();
    expect(files(s.dir, "a")).toEqual([true, true, true]);
  });

  it("»nicht gut« löscht Stück, Video und Standbild und merkt sich Gründe für den nächsten Lauf", async () => {
    const s = await setup({ interests: { video: ["Anime"], music: [] } });
    withMedia(s, "a", { level: "Anspruchsvoll" });
    withMedia(s, "b");
    await s.feed();
    await s.post("/russian/erleben/save", { id: "a" });
    const f = await s.post("/russian/erleben/rate", {
      id: "a",
      rating: "down",
      reasons: ["Zu schwer", "Langweilig", "Unsinn"],
    });
    expect(files(s.dir, "a")).toEqual([false, false, false]);
    expect(files(s.dir, "b")).toEqual([true, true, true]);
    expect(f.pieces.map((p: { id: string }) => p.id)).toEqual(["b"]);
    expect(f.shown).toEqual(["b"]);
    expect(f.saved).toEqual([]);
    const sig = signals(s.dir);
    expect(sig.marks.a).toMatchObject({
      rating: "down",
      reasons: ["zu schwer", "langweilig"],
      info: { yt: "yt_a", topic: "Anime", channel: "Kanal", level: "Anspruchsvoll" },
    });
    expect(sig.gone.a).toMatchObject({ yt: "yt_a", why: "down" });
    expect(f.taste.levelTarget).toBe(1); // »zu schwer« bei Anspruchsvoll → Mittel
    expect(f.taste.topics[0].weight).toBeLessThan(1); // langweilig
    // Gelöschtes lässt sich nicht noch einmal bewerten.
    expect((await s.postRaw("/russian/erleben/rate", { id: "a", rating: "up" })).statusCode).toBe(
      404,
    );
  });

  it("Löschen ohne Bewertung entfernt alles, ist aber kein Signal", async () => {
    const s = await setup({ interests: { video: ["Anime"], music: [] } });
    for (const id of ["a", "b", "c"]) withMedia(s, id);
    const before = (await s.feed()).taste;
    await s.post("/russian/erleben/delete", { id: "a" });
    const f = await s.post("/russian/erleben/delete", { id: "b" });
    expect(files(s.dir, "a")).toEqual([false, false, false]);
    expect(f.pieces.map((p: { id: string }) => p.id)).toEqual(["c"]);
    // Zweimal gelöscht vom selben Kanal: keine Sperre, Gewicht unverändert, kein Schwierigkeitshinweis.
    expect(f.taste.blockedChannels).toEqual([]);
    expect(f.taste.topics[0].weight).toBe(before.topics[0].weight);
    expect(f.taste.levelTarget).toBe(1);
    expect(signals(s.dir).marks).toEqual({});
    expect(signals(s.dir).gone.a).toMatchObject({ yt: "yt_a", why: "delete" });
    expect((await s.postRaw("/russian/erleben/delete", { id: "a" })).statusCode).toBe(404);
    expect((await s.postRaw("/russian/erleben/delete", { id: "../auto" })).statusCode).toBe(404);
  });

  it("Kanalsperre: zweimal »nicht gut« vom selben Kanal, »kaum Russisch« sofort", async () => {
    const s = await setup({ interests: { video: ["Anime"], music: [] } });
    withMedia(s, "a", { channel: "Kanal Eins" });
    withMedia(s, "b", { channel: "kanal eins" });
    withMedia(s, "c", { channel: "Zwei" });
    let f = await s.post("/russian/erleben/rate", { id: "a", rating: "down" });
    expect(f.taste.blockedChannels).toEqual([]);
    f = await s.post("/russian/erleben/rate", { id: "b", rating: "down" });
    expect(f.taste.blockedChannels).toEqual(["kanal eins"]);
    f = await s.post("/russian/erleben/rate", {
      id: "c",
      rating: "down",
      reasons: ["Kaum Russisch"],
    });
    expect(f.taste.blockedChannels).toEqual(["kanal eins", "zwei"]);
  });

  it("Signale: weggeklickt, angesehen, durchgearbeitet, Selbsteinschätzung", async () => {
    const s = await setup({ interests: { video: ["Anime"], music: [] } });
    withMedia(s, "a", { level: "Mittel" });
    expect(await s.post("/russian/erleben/signal", { id: "a", kind: "skip" })).toEqual({
      ok: true,
    });
    await s.post("/russian/erleben/signal", { id: "a", kind: "watched" });
    await s.post("/russian/erleben/signal", { id: "a", kind: "worked", understood: 1 });
    const f = await s.feed();
    expect(f.collection[0]).toMatchObject({ skips: 1, watched: 1, worked: 1, understood: 1 });
    expect(f.taste.levelTarget).toBe(0.25); // wenig verstanden bei Mittel
    // Durchgearbeitet zählt (+0,25), Wegklicken nicht mehr; aber »Mittel« liegt weit über dem
    // neuen Ziel 0,25 → 1,25 / (1 + 0,5 · 0,75).
    expect(f.taste.topics[0].weight).toBe(0.909);
    const bad = async (body: unknown) =>
      (await s.postRaw("/russian/erleben/signal", body)).statusCode;
    expect(await bad({ id: "a", kind: "liked" })).toBe(400);
    expect(await bad({ id: "a", kind: "worked", understood: 4 })).toBe(400);
    expect(await bad({ id: "fehlt", kind: "skip" })).toBe(404);
  });

  it("weist unbekannte Bewertungen ab", async () => {
    const s = await setup();
    withMedia(s, "a");
    expect(
      (await s.postRaw("/russian/erleben/rate", { id: "a", rating: "super" })).statusCode,
    ).toBe(400);
  });
});

describe("Themenvorschläge", () => {
  it("annehmen nimmt das Thema auf (gesucht ab dem nächsten Lauf), ablehnen für immer", async () => {
    const s = await setup({
      interests: { video: ["Anime"], music: ["Rock"] },
      lastRunAt: Date.UTC(2026, 10, 14, 8, 5),
    });
    s.piece("a", { kind: "song", topic: "Rock", by: "Кино", channel: "KinoVEVO" });
    s.piece("b", { kind: "scene", topic: "Anime", channel: "Мосфильм" });
    await s.post("/russian/erleben/save", { id: "a" });
    let f = await s.post("/russian/erleben/save", { id: "b" });
    expect(f.suggestions.map((x: { id: string }) => x.id)).toEqual(["scene:мосфильм", "song:кино"]);
    f = await s.post("/russian/erleben/suggestion", { id: "song:кино", accept: true });
    expect(f.interests.music).toEqual(["Rock", "Кино"]);
    expect(s.calls).toHaveLength(0); // nicht sofort gesucht
    f = await s.post("/russian/erleben/suggestion", { id: "scene:мосфильм", accept: false });
    expect(f.suggestions).toEqual([]);
    expect(f.interests.video).toEqual(["Anime"]);
    expect(
      (await s.postRaw("/russian/erleben/suggestion", { id: "scene:мосфильм", accept: true }))
        .statusCode,
    ).toBe(404);
    // Der nächste Lauf erkundet das neue Thema zuerst.
    await s.feedObj.runDaily();
    expect(s.calls[0]?.plan.order[4]).toEqual({ kind: "song", topic: "Кино", explore: true });
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
