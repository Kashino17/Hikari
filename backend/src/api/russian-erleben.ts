import { type ChildProcess, spawn } from "node:child_process";
import {
  createReadStream,
  existsSync,
  mkdirSync,
  readFileSync,
  readdirSync,
  renameSync,
  rmSync,
  statSync,
  writeFileSync,
} from "node:fs";
import { join } from "node:path";
import type { FastifyInstance } from "fastify";
import { resolveUv } from "../voice/whisper.js";

/**
 * »Lieder & Szenen« im Russisch-Bereich: echte russische Videos und Lieder zu den Themen des
 * Lernenden, vom Server gesucht, aufbereitet und doppelt geprüft (backend/erleben/prepare.py).
 *
 * Dieser Teil verwaltet nur den Feed:
 *  - fertige Stücke liegen als JSON in <dir>/auto, ihre Videos in <dir>/media
 *  - der Feed zeigt immer FEED_SIZE davon; »Neue« tauscht aus, was nicht angefangen ist
 *  - sinkt der Vorrat unter STOCK, bereitet ein Hintergrundlauf neue Stücke auf
 *  - gesucht wird erst, wenn der Lernende Themen vorgegeben hat
 *  - was der Lernende behalten will (oder durchgearbeitet hat), kommt in die Bibliothek und
 *    bleibt — samt Video — dauerhaft liegen
 *
 * In den Feed kommt nur, was die ganze Prüfkette durchlaufen hat (`checked`), und nur im
 * aktuellen Stand der Aufbereitung (`v`); ältere Stücke bereitet prepare.py von selbst neu auf.
 */

const FEED_SIZE = 3;
const STOCK = 3;
/** Nach einem Lauf ohne Ergebnis (Prüfmodelle leer oder überlastet) erst nach einer Pause wieder suchen. */
const PAUSE_AFTER_EMPTY_MS = 20 * 60 * 1000;
/** Abgelegte Stücke bleiben so lange abrufbar; danach werden sie samt Video gelöscht. */
const KEEP_DISMISSED_MS = 30 * 24 * 3600 * 1000;
/** Tageskontingente der Prüfmodelle (siehe backend/erleben/gemini.py). */
const REVIEW_MODELS = ["gemini-4-hydrogen", "gemini-3.6-flash", "gemini-3.8-flash"];
const REVIEW_PER_DAY = 20;
const HEAR_MODEL = "gemini-3.5-transcribe";
const HEAR_PER_DAY = 25;
const QUOTA_ZONE = "America/Los_Angeles";

export interface ErlebenPiece {
  id: string;
  checked?: boolean;
  [key: string]: unknown;
}

export interface Interests {
  video: string[];
  music: string[];
}

export interface ErlebenFeedState {
  shown: string[];
  /** Bibliothek: behaltene Stücke, die neuesten zuerst. */
  saved: string[];
  ready: number;
  busy: boolean;
  pieces: ErlebenPiece[];
  interests: Interests;
  leftToday: number;
  resetAt: number;
}

export interface ErlebenDeps {
  /** Arbeitsordner, üblicherweise ~/.hikari/erleben. */
  dir: string;
  /** Pfad zu backend/erleben/prepare.py. */
  script: string;
  now?: () => number;
  /** Für Tests: eigener Starter des Aufbereiters. Löst mit der Zahl fertiger Stücke auf. */
  runPrepare?: (count: number) => Promise<number>;
  log?: (msg: string) => void;
}

function readJson<T>(file: string, fallback: T): T {
  try {
    return JSON.parse(readFileSync(file, "utf8")) as T;
  } catch {
    return fallback;
  }
}

function writeAtomic(file: string, data: string): void {
  const tmp = `${file}.tmp`;
  writeFileSync(tmp, data);
  renameSync(tmp, file);
}

/** Themen säubern: Text, höchstens 60 Zeichen, ohne Doppelte, höchstens zwölf je Art. */
export function cleanInterests(raw: unknown): Interests {
  const list = (a: unknown) =>
    Array.isArray(a)
      ? [...new Set(a.map((x) => String(x).trim().slice(0, 60)).filter(Boolean))].slice(0, 12)
      : [];
  const r = (raw ?? {}) as { video?: unknown; music?: unknown };
  return { video: list(r.video), music: list(r.music) };
}

/** Der Tag, nach dem Google die Tageskontingente zählt (Mitternacht Pazifikzeit). */
export function quotaDay(nowMs: number): string {
  return new Intl.DateTimeFormat("en-CA", { timeZone: QUOTA_ZONE }).format(new Date(nowMs));
}

/** Wann das Tageskontingent sich erneuert — als Zeitpunkt, damit die App ihn in Ortszeit zeigen kann. */
export function quotaResetAt(nowMs: number): number {
  const parts = Object.fromEntries(
    new Intl.DateTimeFormat("en-GB", {
      timeZone: QUOTA_ZONE,
      hour: "2-digit",
      minute: "2-digit",
      second: "2-digit",
      hour12: false,
    })
      .formatToParts(new Date(nowMs))
      .map((p) => [p.type, Number(p.value)]),
  );
  const hour = (parts.hour ?? 0) % 24;
  return nowMs + ((24 - hour) * 3600 - (parts.minute ?? 0) * 60 - (parts.second ?? 0)) * 1000;
}

export class ErlebenFeed {
  private readonly autoDir: string;
  private readonly mediaDir: string;
  private readonly feedFile: string;
  private readonly interestsFile: string;
  private readonly candidatesFile: string;
  private readonly usageFile: string;
  private readonly now: () => number;
  private feed: { shown: string[]; dismissed: string[]; saved: string[] };
  private running = false;
  private pauseUntil = 0;

  constructor(private readonly deps: ErlebenDeps) {
    this.autoDir = join(deps.dir, "auto");
    this.mediaDir = join(deps.dir, "media");
    this.feedFile = join(deps.dir, "feed.json");
    this.interestsFile = join(deps.dir, "interests.json");
    this.candidatesFile = join(deps.dir, "candidates.json");
    this.usageFile = join(deps.dir, "usage.json");
    this.now = deps.now ?? Date.now;
    mkdirSync(this.autoDir, { recursive: true });
    mkdirSync(this.mediaDir, { recursive: true });
    this.feed = { shown: [], dismissed: [], saved: [], ...readJson(this.feedFile, {}) };
  }

  mediaFile(name: string): string {
    return join(this.mediaDir, name);
  }

  interests(): Interests {
    return cleanInterests(readJson(this.interestsFile, {}));
  }

  setInterests(raw: unknown): void {
    writeAtomic(this.interestsFile, JSON.stringify(cleanInterests(raw)));
    // Neue Themen, neue Suche: gemerkte Trefferlisten verfallen, der Vorrat bleibt.
    const old = readJson<Record<string, unknown> | null>(this.candidatesFile, null);
    if (old) writeAtomic(this.candidatesFile, JSON.stringify({ ...old, pools: {}, turn: 0 }));
    this.pauseUntil = 0;
  }

  /** Wie viele Stücke heute noch geprüft werden können. */
  leftToday(): number {
    const u = readJson<{ day?: string; count?: Record<string, number> }>(this.usageFile, {});
    const today = quotaDay(this.now());
    const used = (model: string) => (u.day === today ? (u.count?.[model] ?? 0) : 0);
    const reviews = REVIEW_MODELS.reduce((n, m) => n + Math.max(0, REVIEW_PER_DAY - used(m)), 0);
    return Math.min(reviews, Math.max(0, HEAR_PER_DAY - used(HEAR_MODEL)));
  }

  /** Stück in die Bibliothek legen oder wieder herausnehmen. Was dort liegt, wird nie gelöscht. */
  setSaved(id: string, on: boolean): void {
    if (!/^[a-z0-9_]{1,60}$/.test(id)) return;
    const rest = this.feed.saved.filter((x) => x !== id);
    this.feed.saved = on ? [id, ...rest] : rest;
    writeAtomic(this.feedFile, JSON.stringify(this.feed));
  }

  /** Löscht abgelegte Stücke, die älter als 30 Tage sind — ein Video belegt 5 bis 10 MB. */
  private prune(): void {
    const cutoff = this.now() - KEEP_DISMISSED_MS;
    for (const id of this.feed.dismissed) {
      if (this.feed.saved.includes(id)) continue;
      const file = join(this.autoDir, `${id}.json`);
      if (!/^[a-z0-9_]{1,60}$/.test(id) || !existsSync(file) || statSync(file).mtimeMs > cutoff)
        continue;
      for (const f of [file, this.mediaFile(`${id}.mp4`), this.mediaFile(`${id}.jpg`)])
        rmSync(f, { force: true });
    }
  }

  /** Geprüfte Stücke, die ältesten zuerst. */
  pieces(): ErlebenPiece[] {
    return readdirSync(this.autoDir)
      .filter((f) => f.endsWith(".json"))
      .map((f) => ({ f, at: statSync(join(this.autoDir, f)).mtimeMs }))
      .sort((a, b) => a.at - b.at)
      .map(({ f }) => readJson<ErlebenPiece | null>(join(this.autoDir, f), null))
      .filter((p): p is ErlebenPiece => !!p && typeof p.id === "string" && p.checked === true);
  }

  /**
   * Der Feed, wie die App ihn sieht. Mit `keep` werden die gezeigten Stücke ausgetauscht —
   * bis auf die genannten (angefangene Stücke bleiben stehen).
   */
  state(keep: string[] | null = null): ErlebenFeedState {
    this.prune();
    const all = this.pieces();
    const ids = new Set(all.map((p) => p.id));
    if (keep) {
      const gone = this.feed.shown.filter((id) => !keep.includes(id));
      this.feed.dismissed = [...new Set([...this.feed.dismissed, ...gone])];
      this.feed.shown = this.feed.shown.filter((id) => keep.includes(id));
    }
    this.feed.shown = this.feed.shown.filter((id) => ids.has(id));
    const ready = () =>
      all.filter((p) => !this.feed.shown.includes(p.id) && !this.feed.dismissed.includes(p.id));
    for (const p of ready()) {
      if (this.feed.shown.length >= FEED_SIZE) break;
      this.feed.shown.push(p.id);
    }
    writeAtomic(this.feedFile, JSON.stringify(this.feed));
    const stock = ready().length;
    this.topUp(stock);
    return {
      shown: this.feed.shown,
      saved: this.feed.saved,
      ready: stock,
      busy: this.running,
      // Auch Abgelegtes bleibt abrufbar, solange es jemand offen hat.
      pieces: all,
      interests: this.interests(),
      leftToday: this.leftToday(),
      resetAt: quotaResetAt(this.now()),
    };
  }

  /** Vorrat auffüllen — nur mit Themen, nur mit Kontingent, nie zwei Läufe zugleich. */
  private topUp(stock: number): void {
    const wishes = this.interests();
    if (this.running || stock >= STOCK || this.now() < this.pauseUntil) return;
    if (wishes.video.length + wishes.music.length === 0 || this.leftToday() === 0) return;
    this.running = true;
    const run = this.deps.runPrepare ?? ((count) => this.spawnPrepare(count));
    run(STOCK + 1 - stock)
      .catch(() => 0)
      .then((prepared) => {
        this.running = false;
        if (prepared === 0) this.pauseUntil = this.now() + PAUSE_AFTER_EMPTY_MS;
      });
  }

  private spawnPrepare(count: number): Promise<number> {
    return new Promise((resolve) => {
      let child: ChildProcess;
      try {
        child = spawn(
          resolveUv(),
          ["run", "--script", this.deps.script, "next", "--count", String(count)],
          {
            stdio: ["ignore", "pipe", "pipe"],
            env: { ...process.env, PATH: `/opt/homebrew/bin:${process.env.PATH ?? ""}` },
          },
        );
      } catch (e) {
        this.deps.log?.(`erleben: Aufbereitung startet nicht: ${String(e)}`);
        resolve(0);
        return;
      }
      let out = "";
      child.stdout?.on("data", (d) => {
        out += String(d);
      });
      child.stderr?.on("data", (d) => {
        for (const line of String(d).split("\n"))
          if (/^(\[|Thema|Kein Kontingent| {6})/.test(line)) this.deps.log?.(`erleben ${line}`);
      });
      child.on("error", (e) => {
        this.deps.log?.(`erleben: Aufbereitung startet nicht: ${e.message}`);
        resolve(0);
      });
      child.on("exit", () => {
        try {
          const last = out.trim().split("\n").pop() ?? "{}";
          resolve(Number((JSON.parse(last) as { prepared?: number }).prepared ?? 0));
        } catch {
          resolve(0);
        }
      });
    });
  }
}

const MEDIA_TYPES: Record<string, string> = { mp4: "video/mp4", jpg: "image/jpeg" };

export async function registerRussianErlebenRoutes(
  app: FastifyInstance,
  deps: ErlebenDeps,
): Promise<void> {
  const feed = new ErlebenFeed(deps);

  app.get("/russian/erleben/feed", async () => feed.state());

  app.post<{ Body: { keep?: unknown } }>("/russian/erleben/refresh", async (req) => {
    const keep = Array.isArray(req.body?.keep) ? req.body.keep.map(String) : [];
    return feed.state(keep);
  });

  app.post<{ Body: { id?: unknown; on?: unknown } }>("/russian/erleben/save", async (req) => {
    feed.setSaved(String(req.body?.id ?? ""), req.body?.on !== false);
    return feed.state();
  });

  app.post<{ Body: unknown }>("/russian/erleben/interests", async (req) => {
    feed.setInterests(req.body);
    return feed.state();
  });

  /** Video oder Standbild eines Stücks — mit Teilabruf, damit die App zeilengenau springen kann. */
  app.get<{ Params: { file: string } }>("/russian/erleben/media/:file", async (req, reply) => {
    const m = /^([a-z0-9_]{1,60})\.(mp4|jpg)$/.exec(req.params.file);
    if (!m) return reply.code(400).send({ error: "file" });
    const file = feed.mediaFile(req.params.file);
    if (!existsSync(file)) return reply.code(404).send({ error: "not_found" });
    const size = statSync(file).size;
    const type = MEDIA_TYPES[m[2] ?? ""] ?? "application/octet-stream";
    reply.header("accept-ranges", "bytes").header("cache-control", "public, max-age=3600");
    const range = /^bytes=(\d*)-(\d*)$/.exec(req.headers.range ?? "");
    if (range && (range[1] || range[2])) {
      const start = range[1] ? Number(range[1]) : Math.max(0, size - Number(range[2]));
      const end = range[1] && range[2] ? Math.min(Number(range[2]), size - 1) : size - 1;
      if (start > end || start >= size)
        return reply.code(416).header("content-range", `bytes */${size}`).send();
      return reply
        .code(206)
        .type(type)
        .header("content-range", `bytes ${start}-${end}/${size}`)
        .header("content-length", end - start + 1)
        .send(createReadStream(file, { start, end }));
    }
    return reply.type(type).header("content-length", size).send(createReadStream(file));
  });
}
