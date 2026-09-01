import { mkdir, writeFile } from "node:fs/promises";
import { join } from "node:path";
import { execa } from "execa";
import { probeDurationSeconds } from "../download/probe.js";
import { pickSeekSeconds } from "./frames.js";

/**
 * Sorgt dafür, dass ein importiertes Video ein Thumbnail hat.
 *
 * Bislang wurde die Remote-URL des Hosters 1:1 in die Datenbank geschrieben
 * (verfällt, blockt Referer) — und beim Browser-Import stand schlicht `null`
 * drin, weshalb diese Videos überall als dunkle Fläche ankamen. Jetzt wird das
 * Bild einmalig auf dem Server abgelegt und die App lädt es von dort. Hat der
 * Hoster gar keins, schneidet ffmpeg ein Standbild aus der fertigen Datei.
 *
 * Rückgabe: der relative Pfad (`/covers/…`), den die App gegen ihre
 * Backend-URL auflöst — oder null, wenn nichts zu holen war. Ein fehlendes
 * Thumbnail darf einen erfolgreichen Import niemals scheitern lassen.
 */
export interface ThumbnailOptions {
  /** Referer der Herkunftsseite — manche Hoster liefern Bilder nur damit. */
  referer?: string | undefined;
}

const THUMB_UA =
  "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/139.0.0.0 Safari/537.36";

const MIN_IMAGE_BYTES = 2048;

function extForContentType(contentType: string): string | null {
  const mime = contentType.split(";")[0]?.trim().toLowerCase();
  switch (mime) {
    case "image/jpeg":
    case "image/jpg":
      return "jpg";
    case "image/png":
      return "png";
    case "image/webp":
      return "webp";
    default:
      return null;
  }
}

async function downloadRemoteThumbnail(
  remoteUrl: string,
  destBase: string,
  coverDir: string,
  referer?: string,
): Promise<string | null> {
  try {
    const headers: Record<string, string> = { "User-Agent": THUMB_UA };
    if (referer) headers.Referer = referer;
    const response = await fetch(remoteUrl, {
      headers,
      redirect: "follow",
      signal: AbortSignal.timeout(10_000),
    });
    if (!response.ok) return null;
    const ext = extForContentType(response.headers.get("content-type") ?? "");
    if (!ext) return null;
    const buffer = Buffer.from(await response.arrayBuffer());
    if (buffer.length < MIN_IMAGE_BYTES) return null;
    const filename = `${destBase}.${ext}`;
    await writeFile(join(coverDir, filename), buffer);
    return filename;
  } catch {
    return null;
  }
}

/**
 * Standbild aus der fertigen Datei. Nicht bei Sekunde 0 (oft schwarz oder
 * Intro), sondern zufällig zwischen Minute 3 und 7 — das trifft eher Inhalt
 * und gibt jeder Folge ein eigenes Bild statt überall derselben Intro-Phase.
 */
async function extractFrame(
  filePath: string,
  destBase: string,
  coverDir: string,
): Promise<string | null> {
  try {
    const duration = await probeDurationSeconds(filePath);
    const seek = pickSeekSeconds(duration ?? 30);
    const filename = `${destBase}.jpg`;
    await execa(
      "ffmpeg",
      [
        "-v",
        "error",
        "-ss",
        String(seek),
        "-i",
        filePath,
        "-frames:v",
        "1",
        "-vf",
        "scale=640:-2",
        "-q:v",
        "4",
        "-y",
        join(coverDir, filename),
      ],
      { timeout: 30_000 },
    );
    return filename;
  } catch {
    return null;
  }
}

export async function ensureImportThumbnail(
  videoId: string,
  filePath: string,
  remoteUrl: string | null | undefined,
  coverDir: string,
  opts?: ThumbnailOptions,
): Promise<string | null> {
  try {
    await mkdir(coverDir, { recursive: true });
    const destBase = `vid_${videoId}`;

    if (remoteUrl) {
      const remote = await downloadRemoteThumbnail(remoteUrl, destBase, coverDir, opts?.referer);
      if (remote) return `/covers/${remote}`;
    }

    const frame = await extractFrame(filePath, destBase, coverDir);
    return frame ? `/covers/${frame}` : null;
  } catch {
    return null;
  }
}
