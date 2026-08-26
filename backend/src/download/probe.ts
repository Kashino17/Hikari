import { execa } from "execa";

/**
 * Liest die Laufzeit einer fertig heruntergeladenen Datei aus.
 *
 * Beim Import aus dem In-App-Browser gibt es keine Metadaten vom Hoster — wir
 * kennen nur die Stream-URL. Ohne diesen Schritt landete eine
 * `duration_seconds = 0` in der Datenbank, was die App als "0 min" anzeigt und
 * schlimmer noch: Der Abspielfortschritt rechnet `position / duration` und
 * teilt damit durch null.
 *
 * Liefert null, wenn sich die Laufzeit nicht ermitteln lässt — ein fehlendes
 * ffprobe darf einen ansonsten erfolgreichen Import nicht scheitern lassen.
 */
export async function probeDurationSeconds(filePath: string): Promise<number | null> {
  try {
    const { stdout } = await execa(
      "ffprobe",
      [
        "-v",
        "error",
        "-show_entries",
        "format=duration",
        "-of",
        "default=noprint_wrappers=1:nokey=1",
        filePath,
      ],
      { timeout: 30_000 },
    );
    const seconds = Number.parseFloat(stdout.trim());
    if (!Number.isFinite(seconds) || seconds <= 0) return null;
    return Math.round(seconds);
  } catch {
    return null;
  }
}
