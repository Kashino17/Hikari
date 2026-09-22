import { existsSync } from "node:fs";
import { mkdir, readdir, unlink } from "node:fs/promises";
import { join } from "node:path";
import { execa } from "execa";
import { probeDurationSeconds } from "../download/probe.js";

/**
 * Einzelbilder aus fertigen Videodateien — schnell und gecacht.
 *
 * Zwei Anwendungen:
 *  - Import-Thumbnail (persistent, Prefix `f_`): zufälliger Frame zwischen
 *    Minute 3 und 7 (Intro und Abspann bleiben außen vor). Die Datei wird
 *    von videos.thumbnail_url / series.thumbnail_url referenziert und darf
 *    deshalb NIE automatisch gelöscht werden.
 *  - Resume-Frame (flüchtig, Prefix `r_`): das Standbild an der Sekunde, an
 *    der der Nutzer aufgehört hat zu schauen (Netflix-Stil). Wird lazily
 *    über GET /videos/:id/frame erzeugt; überholte Positionen desselben
 *    Videos werden weggeräumt.
 *
 * Tempo: `-ss` steht VOR `-i` (Keyframe-Sprung statt Decodieren ab Sekunde 0),
 * dazu nur ein Frame und 640px Breite — ein Cache-Miss kostet typischerweise
 * deutlich unter einer Sekunde, ein Cache-Hit nur einen Dateizugriff.
 */

/** Sekunden werden auf 5 s gerundet, damit der Cache nicht pro Sekunde wächst. */
const BUCKET_SECONDS = 5;
const FRAMES_DIR = "frames";

/**
 * Suchposition für das automatische Thumbnail: zufällig zwischen Minute 3
 * und 7. Bei kürzeren Videos proportional früher, nie bei 0 (Schwarz/Intro).
 */
export function pickSeekSeconds(
  durationSeconds: number,
  random: () => number = Math.random,
): number {
  const duration = Math.max(1, durationSeconds);
  if (duration >= 7 * 60) return Math.round(180 + random() * 240);
  if (duration >= 3 * 60) return Math.round(180 + random() * (duration * 0.9 - 180));
  return Math.max(1, Math.round(duration * 0.1));
}

function bucketOf(seconds: number): number {
  return Math.max(0, Math.floor(seconds / BUCKET_SECONDS) * BUCKET_SECONDS);
}

function frameFilename(videoId: string, bucket: number, transient: boolean): string {
  return `${transient ? "r" : "f"}_${videoId}_${bucket}.jpg`;
}

/** Schneidet genau einen Frame. Wirft bei ffmpeg-Fehlern — Aufrufer fängt ab. */
async function extractFrameAt(
  filePath: string,
  outFile: string,
  seconds: number,
): Promise<void> {
  await execa(
    "ffmpeg",
    [
      "-v",
      "error",
      "-ss",
      String(seconds),
      "-i",
      filePath,
      "-frames:v",
      "1",
      "-vf",
      "scale=640:-2",
      "-q:v",
      "4",
      "-y",
      outFile,
    ],
    { timeout: 30_000 },
  );
}

/**
 * Liefert den Dateinamen des gecachten Frames für [seconds] (relativ zu
 * coverDir/frames), erzeugt ihn bei Bedarf.
 *
 * transient = true kennzeichnet flüchtige Resume-Frames (`r_`): überholte
 * Resume-Positionen desselben Videos werden weggeräumt, sonst sammelt sich
 * Müll. Persistente Thumbnails (`f_`) werden dagegen nie angefasst — sie
 * werden von videos/series in der DB referenziert, ein Löschen liefe auf
 * 404s und leere Cover hinaus.
 *
 * Gibt null zurück, wenn die Extraktion scheitert.
 */
export async function ensureFrame(
  videoId: string,
  filePath: string,
  seconds: number,
  coverDir: string,
  opts: { transient?: boolean } = {},
): Promise<string | null> {
  try {
    const transient = opts.transient ?? false;
    const dir = join(coverDir, FRAMES_DIR);
    await mkdir(dir, { recursive: true });
    const bucket = bucketOf(seconds);
    const filename = frameFilename(videoId, bucket, transient);
    if (existsSync(join(dir, filename))) return filename;

    if (transient) {
      // Aufräumen: ältere Resume-Frames desselben Videos sind überholt.
      for (const f of await readdir(dir)) {
        if (f.startsWith(`r_${videoId}_`) && f !== filename) {
          await unlink(join(dir, f)).catch(() => {});
        }
      }
    }

    await extractFrameAt(filePath, join(dir, filename), bucket);
    return filename;
  } catch {
    return null;
  }
}

/**
 * Standbild fürs Import-Thumbnail an der Zufallsposition (Min. 3–7).
 * Gibt den Dateinamen relativ zu coverDir zurück oder null.
 */
export async function extractRandomFrame(
  videoId: string,
  filePath: string,
  coverDir: string,
): Promise<string | null> {
  try {
    const duration = await probeDurationSeconds(filePath);
    const seek = pickSeekSeconds(duration ?? 30);
    return await ensureFrame(videoId, filePath, seek, coverDir);
  } catch {
    return null;
  }
}
