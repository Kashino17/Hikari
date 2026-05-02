import { execa } from "execa";
import { mkdtemp, readFile, rm } from "node:fs/promises";
import { tmpdir } from "node:os";
import { basename, join } from "node:path";

export interface Caption {
  start: number;  // seconds, clip-local (0 = clip start)
  end: number;
  text: string;   // single word, leading whitespace trimmed
}

export interface TranscriberConfig {
  model?: string;   // whisper model name; default "base"
  language?: string; // ISO-639-1; default "de"
  // execaFn injectable for tests
  execFn?: typeof execa;
  fsReadFn?: typeof readFile;
}

interface WhisperJson {
  segments: Array<{
    words?: Array<{ word: string; start: number; end: number }>;
  }>;
}

/**
 * Run whisper on the given audio/video file, return word-level captions.
 * Throws on any whisper failure — caller decides whether to retry or just
 * skip captions for this clip (NULL is acceptable in DB).
 */
export async function transcribe(
  filePath: string,
  config: TranscriberConfig = {},
): Promise<Caption[]> {
  const model = config.model ?? "base";
  const language = config.language ?? "de";
  const execFn = config.execFn ?? execa;
  const fsRead = config.fsReadFn ?? readFile;
  const outDir = await mkdtemp(join(tmpdir(), "hikari-whisper-"));
  try {
    await execFn("whisper", [
      filePath,
      "--model", model,
      "--language", language,
      "--word_timestamps", "True",
      "--output_format", "json",
      "--output_dir", outDir,
      "--verbose", "False",
    ], { timeout: 5 * 60_000 });
    // whisper writes <basename-without-ext>.json
    const stem = basename(filePath).replace(/\.[^.]+$/, "");
    const jsonPath = join(outDir, stem + ".json");
    const raw = await fsRead(jsonPath, "utf8");
    const parsed = JSON.parse(raw) as WhisperJson;
    const captions: Caption[] = [];
    for (const seg of parsed.segments ?? []) {
      for (const w of seg.words ?? []) {
        const text = w.word.trim();
        if (!text) continue;
        captions.push({ start: w.start, end: w.end, text });
      }
    }
    return captions;
  } finally {
    await rm(outDir, { recursive: true, force: true });
  }
}
