import { type ChildProcess, spawn } from "node:child_process";
import { existsSync } from "node:fs";
import { homedir } from "node:os";
import { join } from "node:path";

/** Sprach-Umgebung oder Modell fehlen (siehe tools/russian_audio/README.md). */
export class VoiceEnvMissingError extends Error {
  constructor() {
    super("voice_env_missing");
  }
}

export type VoiceId = "m" | "f" | "p";

export interface VoiceServiceOptions {
  script: string;
  python?: string;
  model?: string;
  port?: number;
  idleMs?: number;
}

/**
 * Kursstimme auf Abruf: startet tools/russian_audio/tts_server.py bei Bedarf und beendet
 * es nach Leerlauf wieder. Das Laden des Sprachmodells dauert rund eine halbe Minute —
 * darum blockiert `speak` nie auf den Start: Wer nicht warten kann, fragt vorher `ready()`.
 */
export class VoiceService {
  private proc: ChildProcess | null = null;
  private starting: Promise<void> | null = null;
  private idleTimer: NodeJS.Timeout | null = null;
  private readonly python: string;
  private readonly model: string;
  private readonly port: number;
  private readonly idleMs: number;

  constructor(private readonly opts: VoiceServiceOptions) {
    const base = join(homedir(), ".hikari", "tts");
    this.python =
      opts.python ?? process.env.HIKARI_TTS_PYTHON ?? join(base, "ttsenv", "bin", "python");
    this.model = opts.model ?? process.env.HIKARI_TTS_MODEL ?? join(base, "models", "vosk");
    this.port = opts.port ?? 3941;
    this.idleMs = opts.idleMs ?? 10 * 60_000;
  }

  get available(): boolean {
    return existsSync(this.python) && existsSync(this.opts.script) && existsSync(this.model);
  }

  /** Läuft der Dienst und ist das Modell geladen? */
  async ready(): Promise<boolean> {
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
    if (!this.available) throw new VoiceEnvMissingError();
    if (await this.ready()) return;
    if (!this.starting) {
      this.starting = (async () => {
        const p = spawn(this.python, [this.opts.script, String(this.port)], {
          stdio: ["ignore", "ignore", "pipe"],
          env: { ...process.env, HIKARI_TTS_MODEL: this.model },
        });
        this.proc = p;
        p.on("exit", () => {
          if (this.proc === p) this.proc = null;
        });
        p.stderr?.on("data", () => {});
        const deadline = Date.now() + 180_000;
        while (Date.now() < deadline) {
          if (await this.ready()) return;
          if (p.exitCode !== null) break;
          await new Promise((r) => setTimeout(r, 500));
        }
        p.kill();
        this.proc = null;
        throw new Error("voice_service_start_failed");
      })().finally(() => {
        this.starting = null;
      });
    }
    await this.starting;
  }

  /** Dienst im Hintergrund hochfahren, Fehler egal. */
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

  /** Text → Ogg/Opus in der Kursstimme. Wartet auf den Start, falls der Dienst noch lädt. */
  async speak(text: string, voice: VoiceId, rate: number): Promise<Buffer> {
    await this.ensure();
    this.touch();
    const r = await fetch(`http://127.0.0.1:${this.port}/speak`, {
      method: "POST",
      body: JSON.stringify({ text, voice, rate }),
      headers: { "Content-Type": "application/json" },
      signal: AbortSignal.timeout(30_000),
    });
    if (!r.ok) throw new Error("voice_failed");
    return Buffer.from(await r.arrayBuffer());
  }
}
