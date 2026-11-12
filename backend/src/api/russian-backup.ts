import {
  existsSync,
  mkdirSync,
  readFileSync,
  readdirSync,
  renameSync,
  rmSync,
  writeFileSync,
} from "node:fs";
import { join } from "node:path";
import type { FastifyInstance } from "fastify";

/**
 * Sicherung des Russisch-Lernstands auf dem Mac.
 *
 * Der Lernstand lebt auf dem Handy in SharedPreferences ("hikari_russian"). Bis v0.89.102
 * gab es davon keine einzige Kopie — ging die Datei verloren oder wurde sie mit einem leeren
 * Stand überschrieben, war alles weg. Die App schickt deshalb nach jeder Änderung den
 * kompletten Stand hierher; beim Start vergleicht sie und holt sich den Server-Stand zurück,
 * wenn er reicher ist als der auf dem Gerät.
 *
 * Ablage: <dir>/latest.json (bester aktueller Stand) und <dir>/days/YYYY-MM-DD.json
 * (letzter Stand je Tag, 120 Tage). Ein ärmerer Stand ersetzt latest.json nur mit
 * force=1 — so kann ein frisch zurückgesetztes Handy die Sicherung nicht überschreiben.
 */

/** Ein Eintrag der SharedPreferences mit Typ, damit er beim Zurückspielen gleich bleibt. */
export type PrefValue = { t: "s" | "l" | "i" | "f" | "b"; v: string | number | boolean };

export interface RussianBackup {
  savedAt: number;
  score: number;
  appVersion?: string | undefined;
  /** Datei → Schlüssel → Wert, z. B. { hikari_russian: { progress_v1: {t:"s", v:"{…}"} } } */
  files: Record<string, Record<string, PrefValue>>;
}

const KEEP_DAYS = 120;

/**
 * Wie viel gelernt wurde — wächst bei normalem Lernen nur. XP steigen mit jeder Übung,
 * Karten, Sterne und gehörte Folgen kommen nur dazu. Fällt die Zahl, wurde etwas gelöscht.
 */
export function progressScore(progressJson: string | null | undefined): number {
  if (!progressJson) return 0;
  try {
    const p = JSON.parse(progressJson) as Record<string, unknown>;
    const size = (x: unknown) =>
      Array.isArray(x) ? x.length : x && typeof x === "object" ? Object.keys(x).length : 0;
    const xp = typeof p.xp === "number" && Number.isFinite(p.xp) ? Math.max(0, p.xp) : 0;
    return (
      xp +
      size(p.cards) +
      10 * size(p.dayStars) +
      10 * size(p.podcastDayStars) +
      size(p.podcastListened)
    );
  } catch {
    return 0;
  }
}

export function backupScore(files: RussianBackup["files"]): number {
  const raw = files?.hikari_russian?.progress_v1?.v;
  return progressScore(typeof raw === "string" ? raw : null);
}

function validFiles(x: unknown): x is RussianBackup["files"] {
  if (!x || typeof x !== "object") return false;
  for (const entries of Object.values(x as Record<string, unknown>)) {
    if (!entries || typeof entries !== "object") return false;
    for (const e of Object.values(entries as Record<string, unknown>)) {
      const pv = e as PrefValue;
      if (!pv || typeof pv !== "object" || !["s", "l", "i", "f", "b"].includes(pv.t)) return false;
      if (!["string", "number", "boolean"].includes(typeof pv.v)) return false;
    }
  }
  return true;
}

function writeAtomic(file: string, data: string): void {
  const tmp = `${file}.tmp`;
  writeFileSync(tmp, data);
  renameSync(tmp, file);
}

function readBackup(file: string): RussianBackup | null {
  try {
    return existsSync(file) ? (JSON.parse(readFileSync(file, "utf8")) as RussianBackup) : null;
  } catch {
    return null;
  }
}

const dayStamp = (ms: number) => new Date(ms).toISOString().slice(0, 10);

export async function registerRussianBackupRoutes(
  app: FastifyInstance,
  deps: { dir: string; now?: () => number },
): Promise<void> {
  const now = deps.now ?? Date.now;
  /**
   * Fach je Gerätesorte: "main" = die echte App vom Handy, "debug" = Test-Builds auf
   * Emulatoren. So landet nie ein Test-Lernstand in der echten Sicherung.
   */
  const slotDir = (slot: string | undefined) => {
    const name = slot && /^[a-z0-9-]{1,32}$/.test(slot) ? slot : "main";
    const base = name === "main" ? deps.dir : join(deps.dir, "slots", name);
    mkdirSync(join(base, "days"), { recursive: true });
    return { base, daysDir: join(base, "days"), latestFile: join(base, "latest.json") };
  };
  slotDir("main");

  app.get<{ Querystring: { slot?: string } }>("/russian/backup", async (req, reply) => {
    const { latestFile } = slotDir(req.query.slot);
    const b = readBackup(latestFile);
    if (!b) return reply.code(404).send({ error: "no_backup" });
    return b;
  });

  /** Übersicht der Tagesstände — zum Nachsehen und für ein gezieltes Zurückholen. */
  app.get<{ Querystring: { slot?: string } }>("/russian/backup/days", async (req) => {
    const { daysDir } = slotDir(req.query.slot);
    const days = readdirSync(daysDir)
      .filter((f) => f.endsWith(".json"))
      .sort()
      .reverse()
      .map((f) => {
        const b = readBackup(join(daysDir, f));
        return { day: f.slice(0, -5), savedAt: b?.savedAt ?? 0, score: b?.score ?? 0 };
      });
    return { days };
  });

  app.get<{ Params: { day: string }; Querystring: { slot?: string } }>(
    "/russian/backup/days/:day",
    async (req, reply) => {
      const { daysDir } = slotDir(req.query.slot);
      if (!/^\d{4}-\d{2}-\d{2}$/.test(req.params.day))
        return reply.code(400).send({ error: "day" });
      const b = readBackup(join(daysDir, `${req.params.day}.json`));
      if (!b) return reply.code(404).send({ error: "no_backup" });
      return b;
    },
  );

  app.put<{
    Querystring: { force?: string; slot?: string };
    Body: { files?: unknown; appVersion?: string };
  }>("/russian/backup", { bodyLimit: 8 * 1024 * 1024 }, async (req, reply) => {
    const { base, daysDir, latestFile } = slotDir(req.query.slot);
    const files = req.body?.files;
    if (!validFiles(files)) return reply.code(400).send({ error: "files" });
    const score = backupScore(files);
    const savedAt = now();
    const backup: RussianBackup = {
      savedAt,
      score,
      appVersion: typeof req.body?.appVersion === "string" ? req.body.appVersion : undefined,
      files,
    };
    const body = JSON.stringify(backup);
    const latest = readBackup(latestFile);
    const force = req.query.force === "1";

    // Ein ärmerer Stand landet nie über einem reicheren — außer ausdrücklich gewollt.
    if (latest && score < latest.score && !force) {
      // Trotzdem aufheben: unter eigenem Namen, damit nichts unbemerkt verloren geht.
      writeAtomic(join(base, `rejected-${savedAt}.json`), body);
      const rejected = readdirSync(base)
        .filter((f) => f.startsWith("rejected-"))
        .sort();
      for (const f of rejected.slice(0, Math.max(0, rejected.length - 20)))
        rmSync(join(base, f), { force: true });
      req.log.warn({ score, latestScore: latest.score }, "russian backup: ärmerer Stand abgelehnt");
      return reply.code(409).send({ error: "poorer", score, latestScore: latest.score });
    }

    writeAtomic(latestFile, body);
    const dayFile = join(daysDir, `${dayStamp(savedAt)}.json`);
    const prevDay = readBackup(dayFile);
    if (!prevDay || score >= prevDay.score || force) writeAtomic(dayFile, body);

    // Alte Tagesstände räumen.
    const all = readdirSync(daysDir)
      .filter((f) => f.endsWith(".json"))
      .sort();
    for (const f of all.slice(0, Math.max(0, all.length - KEEP_DAYS)))
      rmSync(join(daysDir, f), { force: true });
    return { ok: true, savedAt, score };
  });
}
