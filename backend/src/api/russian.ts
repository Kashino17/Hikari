import { execFile } from "node:child_process";
import { existsSync, mkdtempSync, readFileSync, rmSync, writeFileSync } from "node:fs";
import { homedir, tmpdir } from "node:os";
import { join, resolve } from "node:path";
import { fileURLToPath } from "node:url";
import { promisify } from "node:util";
import type { FastifyInstance } from "fastify";
import { PhonemeEnvMissingError, PhonemeService, alignPhonemes } from "../russian/phonemes.js";

const run = promisify(execFile);

/** Repo-Wurzel: src/api bzw. dist/api → backend → Wurzel. */
const ROOT = resolve(fileURLToPath(new URL("../../..", import.meta.url)));
const AUDIO_DIR = join(ROOT, "android/app/src/main/assets/russian/audio");
const SCRIPT = join(ROOT, "tools/russian_audio/phoneme_server.py");
const CACHE_FILE = join(homedir(), ".hikari", "russian-ref-phonemes.json");

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
}

export async function registerRussianRoutes(
  app: FastifyInstance,
  deps: RussianDeps = {},
): Promise<void> {
  const service = deps.service ?? new PhonemeService({ script: SCRIPT });
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

  async function refPhonemes(clip: string, tmp: string): Promise<string[]> {
    const hit = refCache.get(clip);
    if (hit) return hit;
    const src = join(AUDIO_DIR, `${clip}.ogg`);
    const wav = join(tmp, `ref-${clip}.wav`);
    await toWav16k(src, wav);
    const ph = await service.phonemes(readFileSync(wav));
    refCache.set(clip, ph);
    persist();
    return ph;
  }

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
    if (!/^[a-z0-9_]{1,80}$/.test(clip) || !existsSync(join(AUDIO_DIR, `${clip}.ogg`))) {
      return reply.code(400).send({ error: "clip_unknown" });
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
      for (const candidate of referenceClips(clip)) {
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
