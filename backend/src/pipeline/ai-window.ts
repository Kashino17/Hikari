/**
 * Zeitfenster, in dem das Sprachmodell Hintergrundarbeit leisten darf.
 *
 * Das Bewerten neuer Videos lief bisher im Minutentakt und die Themensuche
 * alle zwei Stunden. Auf einem Laptop heißt das: llama.cpp mit mehreren
 * hundert Prozent CPU-Last über Stunden, das Gerät wird heiß und die Lüfter
 * laufen durch. Für eine Aufgabe, die niemand sofort braucht — der Vorrat wird
 * ohnehin auf Tage vorausgeplant.
 *
 * Deshalb sammelt sich die Arbeit in der Warteschlange und wird in einem
 * festen Fenster abgearbeitet, üblicherweise nachts.
 *
 * Ausdrücklich NICHT betroffen ist alles, was der Nutzer selbst auslöst: eine
 * Link-Analyse beim Import, ein Import aus dem Browser. Diese Wege rufen das
 * Modell direkt und warten auf die Antwort — sie hier zu sperren würde eine
 * Aktion blockieren, die gerade jemand angestoßen hat.
 */
export interface AiWindow {
  startMinutes: number;
  endMinutes: number;
}

/** Kein Fenster gesetzt = jederzeit erlaubt (das frühere Verhalten). */
export const ALWAYS_OPEN: AiWindow | null = null;

/**
 * Liest ein Fenster der Form "22:00-02:00".
 *
 * "always" (oder ein leerer Wert) schaltet die Begrenzung ab. Bei unlesbarer
 * Eingabe wird ebenfalls abgeschaltet statt zu raten: Ein falsch verstandenes
 * Fenster würde die Hintergrundarbeit dauerhaft stilllegen, ohne dass jemand
 * den Grund sieht.
 */
export function parseAiWindow(raw: string | undefined): AiWindow | null {
  const value = raw?.trim().toLowerCase();
  if (!value || value === "always" || value === "immer") return null;

  const m = /^(\d{1,2}):(\d{2})\s*-\s*(\d{1,2}):(\d{2})$/.exec(value);
  if (!m) return null;

  const [sh, sm, eh, em] = [m[1], m[2], m[3], m[4]].map((v) => Number(v));
  if (sh === undefined || sm === undefined || eh === undefined || em === undefined) return null;
  if (sh > 23 || eh > 23 || sm > 59 || em > 59) return null;

  const startMinutes = sh * 60 + sm;
  const endMinutes = eh * 60 + em;
  // Start gleich Ende wäre ein Fenster von null Länge — als "immer" lesen.
  if (startMinutes === endMinutes) return null;
  return { startMinutes, endMinutes };
}

/**
 * Steht das Fenster gerade offen?
 *
 * Ein Fenster über Mitternacht (22:00–02:00) ist der Normalfall, nicht die
 * Ausnahme — die Prüfung dreht sich dann um.
 */
export function isAiWindowOpen(now: Date, window: AiWindow | null): boolean {
  if (!window) return true;
  const minutes = now.getHours() * 60 + now.getMinutes();
  const { startMinutes, endMinutes } = window;
  return startMinutes < endMinutes
    ? minutes >= startMinutes && minutes < endMinutes
    : minutes >= startMinutes || minutes < endMinutes;
}

/** Für Logs und die Anzeige in der App: "22:00–02:00" bzw. "immer". */
export function describeAiWindow(window: AiWindow | null): string {
  if (!window) return "immer";
  const fmt = (m: number) =>
    `${String(Math.floor(m / 60)).padStart(2, "0")}:${String(m % 60).padStart(2, "0")}`;
  return `${fmt(window.startMinutes)}–${fmt(window.endMinutes)}`;
}

/** Minuten bis zum nächsten Öffnen — für eine verständliche Logzeile. */
export function minutesUntilOpen(now: Date, window: AiWindow | null): number {
  if (!window || isAiWindowOpen(now, window)) return 0;
  const minutes = now.getHours() * 60 + now.getMinutes();
  const diff = window.startMinutes - minutes;
  return diff > 0 ? diff : diff + 24 * 60;
}
