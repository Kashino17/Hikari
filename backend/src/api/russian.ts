import { execFile } from "node:child_process";
import { createHash } from "node:crypto";
import {
  existsSync,
  mkdirSync,
  mkdtempSync,
  readFileSync,
  readdirSync,
  rmSync,
  statSync,
  writeFileSync,
} from "node:fs";
import { homedir, tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { promisify } from "node:util";
import type { FastifyInstance } from "fastify";
import { PhonemeEnvMissingError, PhonemeService, alignPhonemes } from "../russian/phonemes.js";
import { type VoiceId, VoiceService } from "../russian/voice.js";

const run = promisify(execFile);

/** Repo-Wurzel: src/api bzw. dist/api → backend → Wurzel. */
const ROOT = resolve(fileURLToPath(new URL("../../..", import.meta.url)));
const AUDIO_DIR = join(ROOT, "android/app/src/main/assets/russian/audio");
const SCRIPT = join(ROOT, "tools/russian_audio/phoneme_server.py");
const CACHE_FILE = join(homedir(), ".hikari", "russian-ref-phonemes.json");
const VOICE_SCRIPT = join(ROOT, "tools/russian_audio/tts_server.py");
const VOICE_CACHE = join(homedir(), ".hikari", "tts", "cache");
const VOICE_CACHE_MAX = 4000;

/** Prüft und normalisiert eine Sprech-Anfrage; null = ungültig. */
export function voiceRequest(q: {
  text?: string;
  voice?: string;
  rate?: string;
}): { text: string; voice: VoiceId; rate: number; key: string } | null {
  const text = (q.text ?? "").trim().replace(/\s+/g, " ");
  if (!text || text.length > 400 || !/[А-Яа-яЁё]/.test(text)) return null;
  const voice = q.voice ?? "m";
  if (voice !== "m" && voice !== "f" && voice !== "p") return null;
  const parsed = Number(q.rate ?? "1");
  const rate = Number.isFinite(parsed)
    ? Math.round(Math.min(1.2, Math.max(0.5, parsed)) * 20) / 20
    : 1;
  const key = createHash("sha1").update(`${voice}|${rate}|${text}`).digest("hex").slice(0, 24);
  return { text, voice, rate, key };
}

async function toWav16k(input: string, output: string): Promise<void> {
  await run(
    "ffmpeg",
    ["-y", "-loglevel", "error", "-i", input, "-ac", "1", "-ar", "16000", "-f", "wav", output],
    {
      timeout: 30_000,
    },
  );
}

/** Normale Aufnahme + langsame + (bei Kurssätzen) die andere Stimme — soweit vorhanden. */
export function referenceClips(
  clip: string,
  exists: (name: string) => boolean = (n) => existsSync(join(AUDIO_DIR, `${n}.ogg`)),
): string[] {
  const names = new Set<string>([clip]);
  const swap = clip.endsWith("_m")
    ? `${clip.slice(0, -2)}_f`
    : clip.endsWith("_f")
      ? `${clip.slice(0, -2)}_m`
      : null;
  if (swap) names.add(swap);
  for (const n of [...names]) names.add(`${n}_slow`);
  return [...names].filter(exists);
}

export interface RussianDeps {
  service?: PhonemeService;
  voice?: VoiceService;
  voiceCacheDir?: string;
}

export async function registerRussianRoutes(
  app: FastifyInstance,
  deps: RussianDeps = {},
): Promise<void> {
  const service = deps.service ?? new PhonemeService({ script: SCRIPT });
  const voice = deps.voice ?? new VoiceService({ script: VOICE_SCRIPT });
  const voiceCache = deps.voiceCacheDir ?? VOICE_CACHE;
  const refCache = new Map<string, string[]>();
  try {
    const saved = JSON.parse(readFileSync(CACHE_FILE, "utf8")) as Record<string, string[]>;
    for (const [k, v] of Object.entries(saved)) refCache.set(k, v);
  } catch {
    /* kein Cache — wird beim ersten Abruf aufgebaut */
  }
  const persist = (): void => {
    try {
      writeFileSync(CACHE_FILE, JSON.stringify(Object.fromEntries(refCache)));
    } catch {
      /* nur Beschleunigung */
    }
  };

  async function refPhonemes(clip: string, tmp: string, file?: string): Promise<string[]> {
    const hit = refCache.get(clip);
    if (hit) return hit;
    const src = file ?? join(AUDIO_DIR, `${clip}.ogg`);
    const wav = join(tmp, `ref-${clip}.wav`);
    await toWav16k(src, wav);
    const ph = await service.phonemes(readFileSync(wav));
    refCache.set(clip, ph);
    persist();
    return ph;
  }

  // ── Kursstimme auf Abruf (Kombi-Meister, Gespräch mit Anja, eigene Sätze) ──────────
  // Der Dienst braucht nach dem Start rund eine halbe Minute. Solange er lädt, antwortet
  // die Route sofort mit 503 — die App spricht dann mit der Gerätestimme weiter.

  app.get("/russian/voice/status", async (req) => {
    const warm = (req.query as { warm?: string }).warm;
    if (warm && voice.available) voice.warm();
    return { available: voice.available, ready: voice.available && (await voice.ready()) };
  });

  app.get("/russian/voice", async (req, reply) => {
    const q = voiceRequest(req.query as { text?: string; voice?: string; rate?: string });
    if (!q) return reply.code(400).send({ error: "bad_request" });
    const file = join(voiceCache, `${q.key}.ogg`);
    const send = (data: Buffer) =>
      reply.header("content-type", "audio/ogg").header("cache-control", "max-age=86400").send(data);
    if (existsSync(file)) return send(readFileSync(file));
    if (!voice.available) return reply.code(503).send({ error: "voice_env_missing" });
    if (!(await voice.ready())) {
      voice.warm();
      return reply.code(503).send({ error: "voice_warming" });
    }
    try {
      const data = await voice.speak(q.text, q.voice, q.rate);
      try {
        mkdirSync(voiceCache, { recursive: true });
        writeFileSync(file, data);
        if (Math.random() < 0.02) pruneVoiceCache(voiceCache);
      } catch {
        /* Zwischenspeicher ist nur Beschleunigung */
      }
      return send(data);
    } catch (e) {
      app.log.warn({ err: e }, "russian voice failed");
      return reply.code(500).send({ error: "voice_failed" });
    }
  });

  app.get("/russian/pronunciation/status", async () => {
    if (service.available) service.warm();
    return { available: service.available };
  });

  // Multipart: Feld "clip" (Name des Muttersprachler-Clips) + Datei "file" (Aufnahme des Lernenden).
  app.post("/russian/pronunciation", async (req, reply) => {
    const part = await req.file();
    if (!part) return reply.code(400).send({ error: "file_missing" });
    const clipField = part.fields.clip as { value?: string } | undefined;
    const clip = clipField?.value ?? "";
    // Sätze ohne fertigen Clip (Kombi-Meister, eigene Sätze): "text:<Stimme>:<Satz>" —
    // als Vorlage dient dann die Kursstimme auf Abruf.
    const spoken = /^text:([mfp]):([\s\S]{1,400})$/.exec(clip);
    const textRef = spoken
      ? voiceRequest({ text: spoken[2] ?? "", voice: spoken[1] ?? "m" })
      : null;
    if (spoken && !textRef) return reply.code(400).send({ error: "clip_unknown" });
    if (
      !textRef &&
      (!/^[a-z0-9_]{1,80}$/.test(clip) || !existsSync(join(AUDIO_DIR, `${clip}.ogg`)))
    ) {
      return reply.code(400).send({ error: "clip_unknown" });
    }
    if (textRef && !existsSync(join(voiceCache, `${textRef.key}.ogg`))) {
      if (!voice.available) return reply.code(503).send({ error: "voice_env_missing" });
      if (!(await voice.ready())) {
        voice.warm();
        return reply.code(503).send({ error: "voice_warming" });
      }
    }
    const upload = await part.toBuffer();
    const tmp = mkdtempSync(join(tmpdir(), "hikari-ru-"));
    try {
      const inFile = join(tmp, "take.bin");
      writeFileSync(inFile, upload);
      const userWav = join(tmp, "take.wav");
      await toWav16k(inFile, userWav);
      const user = await service.phonemes(readFileSync(userWav));
      if (user.length === 0) return reply.code(422).send({ error: "nothing_heard" });
      // Die Erkennung ist verrauscht: schon zwei Muttersprachler-Aufnahmen desselben Satzes
      // weichen ~25 % voneinander ab. Darum gegen alle Varianten (andere Stimme, langsam)
      // ausrichten und die nächste nehmen — dann meint eine Abweichung wirklich dich.
      let best: ReturnType<typeof alignPhonemes> | null = null;
      if (textRef) {
        const file = join(voiceCache, `${textRef.key}.ogg`);
        if (!existsSync(file)) {
          mkdirSync(voiceCache, { recursive: true });
          writeFileSync(file, await voice.speak(textRef.text, textRef.voice, textRef.rate));
        }
        best = alignPhonemes(await refPhonemes(`voice:${textRef.key}`, tmp, file), user);
      }
      for (const candidate of textRef ? [] : referenceClips(clip)) {
        const result = alignPhonemes(await refPhonemes(candidate, tmp), user);
        if (!best || result.score > best.score) best = result;
      }
      if (!best) return reply.code(400).send({ error: "clip_unknown" });
      return best;
    } catch (e) {
      if (e instanceof PhonemeEnvMissingError)
        return reply.code(503).send({ error: "phoneme_env_missing" });
      app.log.warn({ err: e }, "russian pronunciation failed");
      return reply.code(500).send({ error: "pronunciation_failed" });
    } finally {
      rmSync(tmp, { recursive: true, force: true });
    }
  });
}

/** Älteste Dateien löschen, wenn der Zwischenspeicher zu groß wird. */
function pruneVoiceCache(dir: string): void {
  const files = readdirSync(dir)
    .filter((f) => f.endsWith(".ogg"))
    .map((f) => ({ f, t: statSync(join(dir, f)).mtimeMs }))
    .sort((a, b) => a.t - b.t);
  for (const { f } of files.slice(0, Math.max(0, files.length - VOICE_CACHE_MAX))) {
    rmSync(join(dir, f), { force: true });
  }
}
