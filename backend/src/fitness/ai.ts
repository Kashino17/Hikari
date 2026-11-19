/**
 * KI für den Fitness-Bereich (Coach, Ernährungs-Coach, Foto-Tracker, Übersetzungen).
 *
 * Nur Google Gemini, kostenlose Stufe. Jedes Modell hat ein eigenes Tageskontingent je Projekt —
 * der Fitness-Bereich nimmt deshalb bewusst Modelle, die Russisch (Lieder & Szenen, Anja) NICHT
 * benutzt, damit sich die Bereiche nicht gegenseitig das Kontingent wegessen. Reihenfolge = Vorrang;
 * bei 429/503/Zeitüberschreitung geht es zum nächsten Modell. Gemma hat 14.400/Tag und fängt alles auf.
 */
import { existsSync, mkdirSync, readFileSync, writeFileSync } from "node:fs";
import { dirname } from "node:path";

const URL_BASE = "https://generativelanguage.googleapis.com/v1beta/models";

export type AiTask = "coach" | "vision" | "text" | "bulk";

/** [Modell, eigene Tagesgrenze]. Die Grenzen liegen knapp unter Googles Gratis-Limits. */
export const CHAINS: Record<AiTask, [string, number][]> = {
  // Trainingsplan + Rückblick: das beste freie Modell zuerst.
  coach: [
    ["gemini-3-flash-preview", 18],
    ["gemini-3.7-flash", 18],
    ["gemini-3.5-flash", 18],
    ["gemma-4-31b-it", 5000],
    ["gemini-3.1-flash-lite-preview", 120],
  ],
  // Essensfotos.
  vision: [
    ["gemini-3.5-flash", 18],
    ["gemini-3-flash-preview", 18],
    ["gemini-3.1-flash-lite-preview", 120],
    ["gemma-4-31b-it", 5000],
  ],
  // Kurze Texte (Ernährungs-Coach, Lebensmittel zuordnen).
  text: [
    ["gemini-3.1-flash-lite-preview", 120],
    ["gemma-4-31b-it", 5000],
    ["gemini-3.1-flash-lite", 120],
  ],
  // Viele gleichartige Anfragen (Rezepte aufbereiten). NIE gemini-flash-lite-latest: das ist
  // 3.5 Flash-Lite und teilt sich das Kontingent mit Anja.
  bulk: [
    ["gemma-4-26b-a4b-it", 5000],
    ["gemma-4-31b-it", 5000],
    ["gemini-3.1-flash-lite", 220],
  ],
};

export interface AiDeps {
  key: () => string | null;
  usageFile: string;
  request?: typeof fetch;
  log?: (msg: string) => void;
  now?: () => Date;
}

export interface AskOptions {
  task: AiTask;
  system: string;
  /** Text der Anfrage (wird bei Objekten als JSON geschickt). */
  input: unknown;
  image?: { mime: string; base64: string };
  maxTokens?: number;
  temperature?: number;
  timeoutMs?: number;
  think?: "low" | "high";
}

interface Usage {
  day: string;
  count: Record<string, number>;
}

/** Googles Tag wechselt um Mitternacht Pazifikzeit. */
export function pacificDay(d: Date): string {
  return new Intl.DateTimeFormat("en-CA", {
    timeZone: "America/Los_Angeles",
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
  }).format(d);
}

/** Erstes vollständiges JSON-Objekt/-Array aus einer Modellantwort (auch in ```json-Blöcken). */
export function firstJson(text: string): unknown {
  const t = text
    .trim()
    .replace(/^```(?:json)?\s*/i, "")
    .replace(/```\s*$/, "");
  try {
    return JSON.parse(t);
  } catch {
    /* weiter unten suchen */
  }
  const start = t.search(/[[{]/);
  if (start < 0) throw new Error("no_json");
  const open = t[start];
  const close = open === "{" ? "}" : "]";
  let depth = 0;
  let inStr = false;
  for (let i = start; i < t.length; i++) {
    const c = t[i];
    if (inStr) {
      if (c === "\\") i++;
      else if (c === '"') inStr = false;
      continue;
    }
    if (c === '"') inStr = true;
    else if (c === open) depth++;
    else if (c === close) {
      depth--;
      if (depth === 0) return JSON.parse(t.slice(start, i + 1));
    }
  }
  throw new Error("no_json");
}

export class FitnessAi {
  private cool = new Map<string, number>();
  constructor(private deps: AiDeps) {}

  private now() {
    return this.deps.now?.() ?? new Date();
  }

  private usage(): Usage {
    const day = pacificDay(this.now());
    try {
      const u = JSON.parse(readFileSync(this.deps.usageFile, "utf8")) as Usage;
      if (u.day === day) return u;
    } catch {
      /* neu */
    }
    return { day, count: {} };
  }

  private count(model: string, set?: number) {
    const u = this.usage();
    u.count[model] = set ?? (u.count[model] ?? 0) + 1;
    mkdirSync(dirname(this.deps.usageFile), { recursive: true });
    writeFileSync(this.deps.usageFile, JSON.stringify(u));
  }

  available(): boolean {
    return !!this.deps.key();
  }

  status() {
    const u = this.usage();
    return {
      hasKey: this.available(),
      day: u.day,
      used: u.count,
    };
  }

  /** Fragt die Modellkette der Aufgabe der Reihe nach; gibt geparstes JSON + das Modell zurück. */
  async ask<T>(opts: AskOptions, parse: (raw: unknown) => T): Promise<{ value: T; model: string }> {
    const key = this.deps.key();
    if (!key) throw new Error("no_key");
    const request = this.deps.request ?? fetch;
    const chain = CHAINS[opts.task];
    let lastError = "exhausted";
    for (const [model, cap] of chain) {
      const u = this.usage();
      if ((u.count[model] ?? 0) >= cap) continue;
      if ((this.cool.get(model) ?? 0) > Date.now()) continue;
      const isGemma = model.startsWith("gemma");
      const parts: unknown[] = [];
      if (opts.image)
        parts.push({ inline_data: { mime_type: opts.image.mime, data: opts.image.base64 } });
      const inputText = typeof opts.input === "string" ? opts.input : JSON.stringify(opts.input);
      // Gemma kennt keine systemInstruction → vorn an die Anfrage hängen.
      parts.push({ text: isGemma ? `${opts.system}\n\n---\n\n${inputText}` : inputText });
      const body: Record<string, unknown> = {
        contents: [{ role: "user", parts }],
        generationConfig: {
          temperature: opts.temperature ?? 0.4,
          // Gemma denkt vor der Antwort; die Gedanken zählen mit → großzügiger Rahmen.
          maxOutputTokens: (opts.maxTokens ?? 4096) + (isGemma ? 6000 : 0),
          ...(isGemma ? {} : { responseMimeType: "application/json" }),
          // Gemini 3.x denkt sonst lange nach (gemessen: 60–180 s für einen Trainingsplan).
          thinkingConfig: { thinkingLevel: isGemma ? "minimal" : (opts.think ?? "low") },
        },
      };
      if (!isGemma) body.systemInstruction = { parts: [{ text: opts.system }] };
      try {
        const res = await request(`${URL_BASE}/${model}:generateContent`, {
          method: "POST",
          headers: { "content-type": "application/json", "x-goog-api-key": key },
          signal: AbortSignal.timeout(opts.timeoutMs ?? 90_000),
          body: JSON.stringify(body),
        });
        if (res.status === 429) {
          // Tageslimit (quotaId …PerDay…) → Modell für heute aus; sonst nur Minutenlimit → kurz Pause.
          const body = await res.text().catch(() => "");
          if (/PerDay/i.test(body)) {
            this.count(model, cap);
            lastError = "quota";
            this.deps.log?.(`[fitness-ai] ${model}: Tageskontingent erschöpft`);
          } else {
            this.cool.set(model, Date.now() + 65_000);
            lastError = "rate";
          }
          continue;
        }
        if (!res.ok) {
          lastError = `http_${res.status}`;
          // Überlastet → 3 Minuten in Ruhe lassen.
          this.cool.set(model, Date.now() + 180_000);
          this.deps.log?.(`[fitness-ai] ${model}: ${lastError}`);
          continue;
        }
        this.count(model);
        const json = (await res.json()) as {
          candidates?: { content?: { parts?: { text?: string; thought?: boolean }[] } }[];
        };
        const text = (json.candidates?.[0]?.content?.parts ?? [])
          .filter((p) => !p.thought)
          .map((p) => p.text ?? "")
          .join("");
        try {
          return { value: parse(firstJson(text)), model };
        } catch (e) {
          lastError = `parse:${e instanceof Error ? e.message.slice(0, 60) : "?"}`;
          this.deps.log?.(`[fitness-ai] ${model}: ${lastError}`);
        }
      } catch (e) {
        lastError = e instanceof Error ? e.message.slice(0, 80) : "failed";
        this.cool.set(model, Date.now() + 60_000);
        this.deps.log?.(`[fitness-ai] ${model}: ${lastError}`);
      }
    }
    throw new Error(lastError);
  }
}

export function ensureDir(file: string) {
  if (!existsSync(dirname(file))) mkdirSync(dirname(file), { recursive: true });
}
