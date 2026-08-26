/**
 * Fortschritt eines laufenden yt-dlp-Downloads.
 *
 * yt-dlp schreibt seinen Fortschritt normalerweise als überschriebene Zeile
 * mit Steuerzeichen — unbrauchbar zum Auswerten. Mit `--newline` und einem
 * eigenen `--progress-template` kommt stattdessen je Aktualisierung eine
 * saubere Zeile, die sich zeilenweise lesen lässt.
 */
export interface DownloadProgress {
  downloadedBytes: number;
  /** Bei HLS oft unbekannt — dann liefert yt-dlp nur eine Schätzung. */
  totalBytes: number | null;
  speedBps: number | null;
  etaSeconds: number | null;
  /** HLS/DASH laden Fragmente; daraus kommt der verlässlichere Fortschritt. */
  fragmentIndex: number | null;
  fragmentCount: number | null;
}

/** Präfix, an dem eine Fortschrittszeile erkannt wird. */
export const PROGRESS_MARKER = "HKPROG";

/**
 * Die yt-dlp-Argumente, die den maschinenlesbaren Fortschritt einschalten.
 *
 * Reihenfolge der Felder muss zu [parseProgressLine] passen.
 */
export const PROGRESS_ARGS: string[] = [
  "--newline",
  "--progress-template",
  [
    PROGRESS_MARKER,
    "%(progress.downloaded_bytes)s",
    "%(progress.total_bytes)s",
    "%(progress.total_bytes_estimate)s",
    "%(progress.speed)s",
    "%(progress.eta)s",
    "%(progress.fragment_index)s",
    "%(progress.fragment_count)s",
  ].join("|"),
];

/** yt-dlp schreibt "NA" für alles, was es (noch) nicht weiß. */
function num(raw: string | undefined): number | null {
  if (!raw || raw === "NA" || raw === "None") return null;
  const n = Number(raw);
  return Number.isFinite(n) ? n : null;
}

/**
 * Wertet eine einzelne Ausgabezeile aus. Liefert null für alles, was keine
 * Fortschrittszeile ist — yt-dlp schreibt zwischendurch reichlich anderes.
 */
export function parseProgressLine(line: string): DownloadProgress | null {
  const trimmed = line.trim();
  if (!trimmed.startsWith(`${PROGRESS_MARKER}|`)) return null;

  const parts = trimmed.split("|");
  const downloaded = num(parts[1]);
  if (downloaded === null) return null;

  return {
    downloadedBytes: downloaded,
    // Die echte Gesamtgröße bevorzugen, sonst die Schätzung.
    totalBytes: num(parts[2]) ?? num(parts[3]),
    speedBps: num(parts[4]),
    etaSeconds: num(parts[5]),
    fragmentIndex: num(parts[6]),
    fragmentCount: num(parts[7]),
  };
}

/**
 * Anteil des Downloads zwischen 0 und 1, oder null wenn er sich nicht
 * bestimmen lässt.
 *
 * Bei HLS ist die Bytegröße bis zum Schluss unbekannt oder grob geschätzt,
 * die Fragmentzählung dagegen exakt — deshalb hat sie Vorrang.
 */
export function progressFraction(p: DownloadProgress): number | null {
  if (p.fragmentCount && p.fragmentCount > 0 && p.fragmentIndex !== null) {
    return Math.min(1, p.fragmentIndex / p.fragmentCount);
  }
  if (p.totalBytes && p.totalBytes > 0) {
    return Math.min(1, p.downloadedBytes / p.totalBytes);
  }
  return null;
}
