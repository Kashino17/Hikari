import { type ChildProcess, spawn } from "node:child_process";
import { existsSync } from "node:fs";
import { homedir } from "node:os";
import { join } from "node:path";

/** Ein Soll-Phonem des Muttersprachler-Clips und was der Lernende dazu gesagt hat. */
export interface PhonemeMark {
  ref: string;
  /** Gehörtes Phonem an dieser Stelle (null = ausgelassen). */
  user: string | null;
  status: "ok" | "sub" | "del";
}

export interface PronunciationResult {
  /** 0–100: Anteil getroffener Soll-Phoneme. */
  score: number;
  marks: PhonemeMark[];
  /** Phoneme, die du zusätzlich gesagt hast. */
  inserted: number;
  user: string[];
  ref: string[];
}

/** Längenzeichen/Betonungsmarken sind zwischen zwei Aufnahmen zu unzuverlässig. */
const normalize = (p: string): string => p.replace(/[ːˈˌ]/g, "");

/** Levenshtein-Ausrichtung zweier Phonemfolgen (Soll = ref, Gehört = user). */
export function alignPhonemes(ref: string[], user: string[]): PronunciationResult {
  const a = ref.map(normalize);
  const b = user.map(normalize);
  const n = a.length;
  const m = b.length;
  const w = m + 1;
  const dp = new Array<number>((n + 1) * w).fill(0);
  const at = (i: number, j: number): number => dp[i * w + j] ?? 0;
  for (let i = 0; i <= n; i++) dp[i * w] = i;
  for (let j = 0; j <= m; j++) dp[j] = j;
  for (let i = 1; i <= n; i++) {
    for (let j = 1; j <= m; j++) {
      dp[i * w + j] = Math.min(
        at(i - 1, j) + 1,
        at(i, j - 1) + 1,
        at(i - 1, j - 1) + (a[i - 1] === b[j - 1] ? 0 : 1),
      );
    }
  }
  const marks: PhonemeMark[] = [];
  let inserted = 0;
  let i = n;
  let j = m;
  while (i > 0 || j > 0) {
    if (i > 0 && j > 0 && a[i - 1] === b[j - 1] && at(i, j) === at(i - 1, j - 1)) {
      marks.push({ ref: ref[i - 1] ?? "", user: user[j - 1] ?? null, status: "ok" });
      i--;
      j--;
    } else if (i > 0 && j > 0 && at(i, j) === at(i - 1, j - 1) + 1) {
      marks.push({ ref: ref[i - 1] ?? "", user: user[j - 1] ?? null, status: "sub" });
      i--;
      j--;
    } else if (i > 0 && at(i, j) === at(i - 1, j) + 1) {
      marks.push({ ref: ref[i - 1] ?? "", user: null, status: "del" });
      i--;
    } else {
      inserted++;
      j--;
    }
  }
  marks.reverse();
  const ok = marks.filter((x) => x.status === "ok").length;
  const score = n === 0 ? 0 : Math.round((ok / n) * 100);
  return { score, marks, inserted, user, ref };
}

export interface PhonemeServiceOptions {
  /** Python des Setups (tools/russian_audio/setup_phoneme_server.sh). */
  python?: string;
  script: string;
  port?: number;
  idleMs?: number;
}

export class PhonemeEnvMissingError extends Error {
  constructor() {
    super("phoneme_env_missing");
  }
}

/** Startet den Python-Erkenner bei Bedarf und beendet ihn nach Leerlauf (RAM). */
export class PhonemeService {
  private proc: ChildProcess | null = null;
  private starting: Promise<void> | null = null;
  private idleTimer: NodeJS.Timeout | null = null;
  private readonly python: string;
  private readonly port: number;
  private readonly idleMs: number;

  constructor(private readonly opts: PhonemeServiceOptions) {
    this.python =
      opts.python ??
      process.env.HIKARI_PHONEME_PYTHON ??
      join(
        process.env.HIKARI_PHONEME_ENV ?? join(homedir(), ".hikari", "phoneme-env"),
        "bin",
        "python",
      );
    this.port = opts.port ?? 3940;
    this.idleMs = opts.idleMs ?? 15 * 60_000;
  }

  get available(): boolean {
    return existsSync(this.python) && existsSync(this.opts.script);
  }

  private async healthy(): Promise<boolean> {
    try {
      const r = await fetch(`http://127.0.0.1:${this.port}/health`, {
        signal: AbortSignal.timeout(1500),
      });
      return r.ok;
    } catch {
      return false;
    }
  }

  private async ensure(): Promise<void> {
    if (!this.available) throw new PhonemeEnvMissingError();
    if (this.proc && (await this.healthy())) return;
    if (!this.starting) {
      this.starting = (async () => {
        const p = spawn(this.python, [this.opts.script, String(this.port)], {
          stdio: ["ignore", "pipe", "pipe"],
        });
        this.proc = p;
        p.on("exit", () => {
          if (this.proc === p) this.proc = null;
        });
        p.stderr?.on("data", () => {});
        const deadline = Date.now() + 180_000;
        while (Date.now() < deadline) {
          if (await this.healthy()) return;
          if (p.exitCode !== null) break;
          await new Promise((r) => setTimeout(r, 500));
        }
        p.kill();
        this.proc = null;
        throw new Error("phoneme_service_start_failed");
      })().finally(() => {
        this.starting = null;
      });
    }
    await this.starting;
  }

  /** Erkenner im Hintergrund hochfahren (Modell laden dauert), Fehler egal. */
  warm(): void {
    this.touch();
    void this.ensure().catch(() => {});
  }

  private touch(): void {
    if (this.idleTimer) clearTimeout(this.idleTimer);
    this.idleTimer = setTimeout(() => this.stop(), this.idleMs);
    this.idleTimer.unref();
  }

  stop(): void {
    this.proc?.kill();
    this.proc = null;
  }

  /** WAV (16 kHz mono) → Phonemfolge. */
  async phonemes(wav: Buffer): Promise<string[]> {
    await this.ensure();
    this.touch();
    const r = await fetch(`http://127.0.0.1:${this.port}/phonemes`, {
      method: "POST",
      body: wav,
      headers: { "Content-Type": "audio/wav" },
      signal: AbortSignal.timeout(60_000),
    });
    const body = (await r.json()) as { phonemes?: string[]; error?: string };
    if (!r.ok || !body.phonemes) throw new Error(body.error ?? "phoneme_failed");
    return body.phonemes;
  }
}
