import { type ChildProcess, spawn } from "node:child_process";
import { existsSync } from "node:fs";
import { createInterface } from "node:readline";

export interface Transcript {
  text: string;
  /** Länge der Aufnahme in Sekunden. */
  duration: number;
  /** Von Whisper erkannte Sprache, z. B. "de". */
  language: string | null;
}

export interface WhisperOptions {
  /** Pfad zu voice/whisper_worker.py. */
  script: string;
  /** Leerlaufzeit, nach der der Prozess (und damit das Modell) beendet wird. */
  idleMs: number;
  /** Feste Sprache ("de") oder null = Whisper erkennt selbst. */
  language?: string | null;
  /** Obergrenze pro Datei. */
  timeoutMs?: number;
  /** Für Tests: eigener Prozessstarter. */
  spawnWorker?: () => ChildProcess;
  log?: (msg: string) => void;
}

interface Pending {
  resolve: (t: Transcript) => void;
  reject: (e: Error) => void;
  timer: NodeJS.Timeout;
}

/** uv liegt bei Homebrew; der Supervisor startet den Server ohne Login-PATH. */
export function resolveUv(env: NodeJS.ProcessEnv = process.env): string {
  if (env.HIKARI_UV_BIN) return env.HIKARI_UV_BIN;
  for (const p of ["/opt/homebrew/bin/uv", "/usr/local/bin/uv"]) if (existsSync(p)) return p;
  return "uv";
}

/**
 * Hält einen Whisper-Prozess, solange Arbeit da ist. Der Prozess startet beim
 * ersten Auftrag, lädt das Modell einmal und wird nach `idleMs` ohne Auftrag
 * beendet. Mit ihm verschwindet das Modell aus dem RAM.
 */
export class WhisperWorker {
  private proc: ChildProcess | null = null;
  private pending = new Map<string, Pending>();
  private nextId = 1;
  private idleTimer: NodeJS.Timeout | null = null;
  private lastUsedAt: number | null = null;

  constructor(private readonly opts: WhisperOptions) {}

  get loaded(): boolean {
    return this.proc !== null;
  }

  status() {
    return {
      loaded: this.loaded,
      lastUsedAt: this.lastUsedAt,
      idleMinutes: Math.round(this.opts.idleMs / 60_000),
    };
  }

  private start(): ChildProcess {
    const p =
      this.opts.spawnWorker?.() ??
      spawn(resolveUv(), ["run", "--quiet", "--script", this.opts.script], {
        stdio: ["pipe", "pipe", "pipe"],
        env: { ...process.env, PYTHONUNBUFFERED: "1" },
      });
    this.opts.log?.("whisper: Prozess gestartet");
    const lines = createInterface({ input: p.stdout! });
    lines.on("line", (line) => this.onLine(line));
    // stderr verwerfen (Fortschrittsbalken von uv/HF), aber leerlesen,
    // sonst blockiert der Prozess bei vollem Puffer.
    p.stderr?.on("data", () => {});
    p.on("exit", (code) => {
      if (this.proc !== p) return;
      this.proc = null;
      this.opts.log?.(`whisper: Prozess beendet (code ${code})`);
      this.failAll(new Error(`whisper_exited_${code ?? "signal"}`));
    });
    p.on("error", (err) => {
      if (this.proc !== p) return;
      this.proc = null;
      this.failAll(err);
    });
    return p;
  }

  private onLine(line: string) {
    let msg: {
      id?: string;
      text?: string;
      duration?: number;
      language?: string | null;
      error?: string;
    };
    try {
      msg = JSON.parse(line);
    } catch {
      return; // fremde Ausgabe auf stdout ignorieren
    }
    if (!msg.id) return; // z. B. {"ready": true}
    const p = this.pending.get(msg.id);
    if (!p) return;
    this.pending.delete(msg.id);
    clearTimeout(p.timer);
    if (msg.error) p.reject(new Error(msg.error));
    else
      p.resolve({
        text: msg.text ?? "",
        duration: msg.duration ?? 0,
        language: msg.language ?? null,
      });
    this.armIdle();
  }

  private failAll(err: Error) {
    for (const [, p] of this.pending) {
      clearTimeout(p.timer);
      p.reject(err);
    }
    this.pending.clear();
  }

  private armIdle() {
    if (this.idleTimer) clearTimeout(this.idleTimer);
    this.idleTimer = null;
    if (this.pending.size > 0) return; // während Arbeit nie entladen
    this.idleTimer = setTimeout(() => {
      this.opts.log?.("whisper: Leerlauf, Modell wird entladen");
      this.stop();
    }, this.opts.idleMs);
    this.idleTimer.unref();
  }

  transcribe(path: string): Promise<Transcript> {
    if (this.idleTimer) clearTimeout(this.idleTimer);
    this.idleTimer = null;
    this.lastUsedAt = Date.now();
    if (!this.proc) this.proc = this.start();
    const id = String(this.nextId++);
    const proc = this.proc;
    return new Promise<Transcript>((resolve, reject) => {
      const timer = setTimeout(
        () => {
          this.pending.delete(id);
          reject(new Error("whisper_timeout"));
          // Ein hängender Prozess bekommt keine zweite Chance.
          if (this.proc === proc) this.stop();
        },
        this.opts.timeoutMs ?? 30 * 60_000,
      );
      this.pending.set(id, { resolve, reject, timer });
      proc.stdin!.write(`${JSON.stringify({ id, path, language: this.opts.language ?? null })}\n`);
    });
  }

  /** Prozess beenden — gibt das Modell sofort frei. */
  stop(): void {
    if (this.idleTimer) clearTimeout(this.idleTimer);
    this.idleTimer = null;
    const p = this.proc;
    this.proc = null;
    if (p) {
      p.stdin?.end();
      p.kill();
    }
    this.failAll(new Error("whisper_stopped"));
  }
}
