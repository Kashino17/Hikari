import { type ChildProcess, spawn as nodeSpawn } from "node:child_process";
import type { FastifyInstance } from "fastify";

/**
 * Hält den Mac wach, solange jemand Video oder Musik vom Server holt.
 *
 * Auf Akku legt sich das MacBook nach einer Minute ohne Bedienung schlafen — mitten im Film
 * bricht dann der Stream zum Fernseher oder Handy ab. Netzwerkverkehr allein hält macOS nicht
 * wach. Deshalb läuft während einer Wiedergabe (und kurz danach, für Pausen und den nächsten
 * Titel) ein `caffeinate -i`. Ohne laufende Wiedergabe schläft der Rechner wie gewohnt.
 *
 * Den zugeklappten Deckel überstimmt das nicht. Abschalten: KEEP_AWAKE=off.
 */

type Spawn = (cmd: string, args: string[]) => Pick<ChildProcess, "kill" | "on">;
type Log = { info: (o: object, m: string) => void; warn: (o: object, m: string) => void };

export interface KeepAwakeOpts {
  spawn?: Spawn;
  platform?: NodeJS.Platform;
  pid?: number;
  /** So lange nach dem letzten Abruf bleibt der Rechner noch wach. */
  lingerMs?: number;
  log?: Log;
}

export const KEEP_AWAKE_LINGER_MS = 5 * 60 * 1000;

export class KeepAwake {
  private readonly spawn: Spawn;
  private readonly enabled: boolean;
  private readonly pid: number;
  private readonly lingerMs: number;
  private readonly log: Log | undefined;
  private active = 0;
  private child: Pick<ChildProcess, "kill" | "on"> | null = null;
  private timer: ReturnType<typeof setTimeout> | null = null;

  constructor(opts: KeepAwakeOpts = {}) {
    this.spawn = opts.spawn ?? ((cmd, args) => nodeSpawn(cmd, args, { stdio: "ignore" }));
    this.enabled = (opts.platform ?? process.platform) === "darwin";
    this.pid = opts.pid ?? process.pid;
    this.lingerMs = opts.lingerMs ?? KEEP_AWAKE_LINGER_MS;
    this.log = opts.log;
  }

  /** Läuft gerade ein Wachhalter? */
  get awake(): boolean {
    return this.child !== null;
  }

  /** Ein Abruf beginnt. Die zurückgegebene Funktion meldet sein Ende (mehrfach aufrufbar). */
  begin(): () => void {
    if (!this.enabled) return () => {};
    this.active++;
    if (this.timer) {
      clearTimeout(this.timer);
      this.timer = null;
    }
    this.start();
    let ended = false;
    return () => {
      if (ended) return;
      ended = true;
      this.active--;
      if (this.active > 0) return;
      this.timer = setTimeout(() => this.stop(), this.lingerMs);
      this.timer.unref?.();
    };
  }

  private start(): void {
    if (this.child) return;
    try {
      // -i: kein Leerlauf-Schlaf. -w: endet von selbst, falls der Server stirbt.
      const child = this.spawn("caffeinate", ["-i", "-w", String(this.pid)]);
      this.child = child;
      child.on("exit", () => {
        if (this.child === child) this.child = null;
      });
      child.on("error", (err) => {
        if (this.child === child) this.child = null;
        this.log?.warn({ err }, "keep-awake failed");
      });
      this.log?.info({}, "keep-awake on (media streaming)");
    } catch (err) {
      this.child = null;
      this.log?.warn({ err }, "keep-awake failed");
    }
  }

  /** Beendet den Wachhalter sofort (Leerlauf vorbei oder Server fährt herunter). */
  stop(): void {
    if (this.timer) {
      clearTimeout(this.timer);
      this.timer = null;
    }
    const child = this.child;
    this.child = null;
    if (!child) return;
    try {
      child.kill();
    } catch {
      // schon weg
    }
    this.log?.info({}, "keep-awake off");
  }
}

/** Abrufe, bei denen wirklich Bild oder Ton fließt — nicht jede kleine JSON-Anfrage. */
export function isMediaRequest(url: string): boolean {
  const path = url.split("?")[0] ?? "";
  return (
    /^\/videos\/[^/]+\.mp4$/.test(path) ||
    path.startsWith("/stream/") ||
    path.startsWith("/music/audio/") ||
    path.startsWith("/music/video/")
  );
}

/** Hängt den Wachhalter an alle Medien-Abrufe des Servers. */
export function registerKeepAwake(app: FastifyInstance, keepAwake: KeepAwake): void {
  app.addHook("onRequest", (req, reply, done) => {
    if (isMediaRequest(req.url)) {
      const end = keepAwake.begin();
      // 'close' kommt, wenn die Antwort fertig ist ODER die Verbindung abreißt.
      reply.raw.once("close", end);
    }
    done();
  });
  app.addHook("onClose", async () => keepAwake.stop());
}
