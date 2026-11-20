import { z } from "zod";
import { extractJsonObject } from "../voice/summarize.js";
import { AREAS, type Area, KINDS, type Kind, addDays, dayDiff } from "./plan.js";

/** Eine erkannte Aufgabe, noch ohne ID. */
export interface ParsedTask {
  title: string;
  area: Area;
  kind: Kind;
  minutes: number;
  due: string | null;
  stakes: number;
  note: string | null;
  /** ID eines bestehenden Vorhabens, Name eines neuen, oder null. */
  project: string | null;
  /** Gesetzt = großes Vorhaben, das in Schritte zerlegt wird. */
  steps: { title: string; minutes: number; kind: Kind }[];
}

export interface ParseContext {
  today: string;
  projects: { id: string; title: string }[];
}

const WD = ["Sonntag", "Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag"];
const weekdayOf = (day: string) => WD[new Date(`${day}T12:00:00`).getDay()];

const parsedSchema = z.object({
  tasks: z
    .array(
      z.object({
        title: z.string().trim().min(2).max(200),
        area: z.enum(AREAS),
        kind: z.enum(KINDS),
        minutes: z.number().int().min(1).max(600),
        due: z.string(),
        stakes: z.number().int().min(0).max(3),
        note: z.string(),
        project: z.string(),
        steps: z
          .array(
            z.object({
              title: z.string().trim().min(2).max(200),
              minutes: z.number().int().min(1).max(600),
              kind: z.enum(KINDS),
            }),
          )
          .max(8),
      }),
    )
    .max(15),
});

const jsonSchema = {
  type: "object",
  additionalProperties: false,
  required: ["tasks"],
  properties: {
    tasks: {
      type: "array",
      maxItems: 15,
      items: {
        type: "object",
        additionalProperties: false,
        required: ["title", "area", "kind", "minutes", "due", "stakes", "note", "project", "steps"],
        properties: {
          title: { type: "string", minLength: 2 },
          area: { type: "string", enum: [...AREAS] },
          kind: { type: "string", enum: [...KINDS] },
          minutes: { type: "integer", minimum: 1, maximum: 600 },
          due: { type: "string", description: "JJJJ-MM-TT oder leer" },
          stakes: { type: "integer", minimum: 0, maximum: 3 },
          note: { type: "string" },
          project: { type: "string" },
          steps: {
            type: "array",
            maxItems: 8,
            items: {
              type: "object",
              additionalProperties: false,
              required: ["title", "minutes", "kind"],
              properties: {
                title: { type: "string", minLength: 2 },
                minutes: { type: "integer", minimum: 1, maximum: 600 },
                kind: { type: "string", enum: [...KINDS] },
              },
            },
          },
        },
      },
    },
  },
};

function systemPrompt(ctx: ParseContext): string {
  const days = Array.from({ length: 14 }, (_, i) => {
    const d = addDays(ctx.today, i);
    return `${weekdayOf(d)} ${d}${i === 0 ? " (heute)" : i === 1 ? " (morgen)" : ""}`;
  }).join("\n");
  const projects = ctx.projects.length
    ? ctx.projects.map((p) => `- ${p.id}: ${p.title}`).join("\n")
    : "(keine)";
  return `Du bekommst ein Diktat oder einen Text, in dem jemand sagt, was er erledigen muss. Mach daraus Aufgaben für seine Aufgabenliste, auf Deutsch.

Heute und die nächsten Tage:
${days}

Laufende Vorhaben (id: Titel):
${projects}

Regeln:
- Eine Aufgabe pro Sache, die zu tun ist. Mehrere Sachen in einem Satz = mehrere Aufgaben.
- title: kurz, beginnt mit dem Gegenstand, endet mit dem Verb („Rechnung an Kunde Berger schreiben“, „Lisa zum Geburtstag anrufen“). Kein „ich muss“, keine Frist im Titel.
- area: amt = Behörden, Visum, Ausweis, Reisen, Flüge; geld = Bank, Konto, Steuer, Versicherung, Zahlungen (privat und geschäftlich); job = Arbeit, Kunden, eigene Firma, Shop; leute = Freunde, Familie, Verabredungen; haus = Haushalt, Einkäufe, Arzttermine, Alltag; ich = Hobbys, Sport, Lernen.
- kind: wo man es erledigt: rechner, telefon (Anrufe), unterwegs (Einkaufen, Abholen, Post, Termine vor Ort), zuhause.
- minutes: realistische Dauer. Anruf 5, kurze Mail 5, Formular 20–40, Einkauf 15.
- due: nur wenn eine Frist oder ein Tag genannt wird, als JJJJ-MM-TT aus der Liste oben. „bis Montag“ = der nächste Montag. Sonst leer.
- stakes: 3 = Strafe, Gebühr oder Frist einer Behörde/Bank hängt dran, 2 = Behörde/Bank/Geld/Kunde, 1 = wäre ärgerlich, 0 = egal.
- note: in 2–5 Wörtern, warum es wichtig ist, nur wenn gesagt („Rücksendefrist“, „Kunde wartet“). Sonst leer.
- project: gehört die Aufgabe zu einem laufenden Vorhaben, dessen id. Ist es selbst ein großes Vorhaben mit mehreren Schritten (Umzug, Visum beantragen, Steuererklärung), ein kurzer Name dafür und die Schritte in steps (2–6 Schritte in sinnvoller Reihenfolge). Sonst leer und steps leer.
- Erfinde keine Aufgaben. Das Diktat kann Hörfehler enthalten: Gemeintes nehmen.
Antworte NUR mit dem JSON-Objekt.`;
}

export interface TaskParserOptions {
  baseUrl: string;
  model: string;
  ttlSeconds: number;
  fetchImpl?: typeof fetch;
}

export class TaskParser {
  private readonly fetchImpl: typeof fetch;
  constructor(private readonly opts: TaskParserOptions) {
    this.fetchImpl = opts.fetchImpl ?? fetch;
  }

  private async chat(
    system: string,
    user: string,
    schema: Record<string, unknown>,
    name: string,
    maxTokens: number,
  ) {
    const res = await this.fetchImpl(`${this.opts.baseUrl}/v1/chat/completions`, {
      method: "POST",
      headers: { "content-type": "application/json" },
      signal: AbortSignal.timeout(5 * 60_000),
      body: JSON.stringify({
        model: this.opts.model,
        ttl: this.opts.ttlSeconds,
        messages: [
          { role: "system", content: system },
          { role: "user", content: user },
        ],
        temperature: 0.1,
        max_tokens: maxTokens,
        stream: false,
        response_format: { type: "json_schema", json_schema: { name, strict: true, schema } },
      }),
    });
    if (!res.ok) throw new Error(`lmstudio_${res.status}`);
    const body = (await res.json()) as {
      choices?: { message?: { content?: string | null; reasoning_content?: string } }[];
    };
    const msg = body.choices?.[0]?.message;
    const raw = msg?.content?.trim() ? msg.content : (msg?.reasoning_content ?? "");
    if (!raw.trim()) throw new Error("lmstudio_empty");
    return extractJsonObject(raw);
  }

  async parse(text: string, ctx: ParseContext): Promise<ParsedTask[]> {
    let lastErr: unknown = null;
    for (let attempt = 0; attempt < 2; attempt++) {
      try {
        const out = parsedSchema.parse(
          await this.chat(systemPrompt(ctx), text, jsonSchema, "tasks", 3000),
        );
        return out.tasks.map((t) => normalize(t, ctx));
      } catch (err) {
        lastErr = err;
      }
    }
    throw lastErr instanceof Error ? lastErr : new Error("parse_failed");
  }

  /** Vorschlag, wie eine große Aufgabe in kleine Schritte zerfällt. */
  async split(title: string, minutes: number): Promise<{ title: string; minutes: number }[]> {
    const schema = {
      type: "object",
      additionalProperties: false,
      required: ["steps"],
      properties: {
        steps: {
          type: "array",
          minItems: 2,
          maxItems: 5,
          items: {
            type: "object",
            additionalProperties: false,
            required: ["title", "minutes"],
            properties: {
              title: { type: "string", minLength: 2 },
              minutes: { type: "integer", minimum: 1, maximum: 300 },
            },
          },
        },
      },
    };
    const sys =
      "Zerlege eine Aufgabe in 2 bis 5 kleine, konkrete Schritte, die man nacheinander abhaken kann. Deutsch, kurz, jeder Schritt beginnt mit dem Gegenstand und endet mit dem Verb. minutes = realistische Dauer je Schritt, zusammen ungefähr die angegebene Gesamtdauer. Antworte NUR mit dem JSON-Objekt.";
    const out = z
      .object({
        steps: z
          .array(
            z.object({
              title: z.string().trim().min(2).max(200),
              minutes: z.number().int().min(1).max(300),
            }),
          )
          .min(2)
          .max(5),
      })
      .parse(
        await this.chat(
          sys,
          `Aufgabe: ${title}\nGesamtdauer: ${minutes} Minuten`,
          schema,
          "split",
          1500,
        ),
      );
    return out.steps;
  }
}

function normalize(
  t: z.infer<typeof parsedSchema>["tasks"][number],
  ctx: ParseContext,
): ParsedTask {
  const due = /^\d{4}-\d{2}-\d{2}$/.test(t.due) && dayDiff(ctx.today, t.due) >= 0 ? t.due : null;
  return {
    title: t.title.replace(/^ich muss\s+/i, "").replace(/^./, (c) => c.toUpperCase()),
    area: t.area,
    kind: t.kind,
    minutes: t.minutes,
    due,
    stakes: t.stakes,
    note: t.note.trim() || null,
    project: t.project.trim() || null,
    steps: t.steps,
  };
}

// ---------------------------------------------------------------------------
// Rückfall ohne LM Studio: einfache Regeln (wie in der Vorschau).

const RX: [Area, RegExp][] = [
  [
    "amt",
    /visum|pass(?!t)|konsulat|botschaft|behörde|\bamt\b|ausweis|führerschein|flug|reise|hotel|anmeld/i,
  ],
  [
    "job",
    /kunde|kampagne|meeting|angebot|projekt|arbeit|chef|team|präsentation|rechnung (für|an) |rechnung.*schreib|shop|bericht|newsletter/i,
  ],
  [
    "geld",
    /bank|konto|überweis|steuer|finanzamt|versicherung|kredit|karte|bezahl|depot|vollmacht|rechnung/i,
  ],
  ["leute", /geburtstag|treffen|freund|mama|papa|bruder|schwester|oma|opa|einladung/i],
  ["haus", /kauf|einkauf|paket|putz|wasch|reparier|wohnung|müll|arzt|abhol|keller|räum/i],
  ["ich", /training|kurs|gitarre|klettern|hobby|\bbuch\b|sport|yoga|lernen/i],
];
const WDS = ["sonntag", "montag", "dienstag", "mittwoch", "donnerstag", "freitag", "samstag"];

function untilWeekday(today: string, wd: number): number {
  const cur = new Date(`${today}T12:00:00`).getDay();
  const d = (wd - cur + 7) % 7;
  return d === 0 ? 7 : d;
}

export function fallbackParse(text: string, ctx: ParseContext): ParsedTask[] {
  const parts = text
    .split(
      /,|;|\.|\n|\bund (?=(?:am|bis|morgen|heute|übermorgen|für|fürs|noch|dann|danach|die|den|das|[a-zäöü]+ (?:anrufen|kaufen|schreiben|buchen)))/i,
    )
    .map((s) => s.trim())
    .filter((s) => s.length > 3);
  return parts.map((raw) => {
    const low = raw.toLowerCase();
    let off: number | null = null;
    if (/übermorgen/.test(low)) off = 2;
    else if (/(^|\s)morgen(\s|$)/.test(low)) off = 1;
    else if (/\bheute\b/.test(low)) off = 0;
    else if (/nächste woche/.test(low)) off = untilWeekday(ctx.today, 1);
    else {
      const wd = WDS.findIndex((w) => low.includes(w));
      if (wd >= 0) off = untilWeekday(ctx.today, wd);
    }
    let area: Area = RX.find(([, r]) => r.test(raw))?.[0] ?? "haus";
    if (/anruf|telefon/i.test(raw) && area === "haus") area = "leute";
    const kind: Kind = /anruf|telefon/i.test(raw)
      ? "telefon"
      : /kauf|abhol|paket|post\b/i.test(raw)
        ? "unterwegs"
        : /schreib|mail|überweis|online|ausfüll|buch|druck|schick/i.test(raw)
          ? "rechner"
          : "zuhause";
    const minutes =
      kind === "telefon"
        ? 5
        : kind === "unterwegs"
          ? 15
          : /rechnung|angebot|antrag/i.test(raw)
            ? 20
            : 10;
    let title = raw
      .replace(/^(und|dann|danach|also|ach ja|ähm)\s+/i, "")
      .replace(/\b(ich muss|ich sollte|ich will|muss ich|ich|noch|unbedingt|auch|bitte)\b/gi, "")
      .replace(
        /(?<![a-zäöüß])(bis |am |nächsten? |zum )?(übermorgen|morgen|heute|montag|dienstag|mittwoch|donnerstag|freitag|samstag|sonntag|nächste woche)(?![a-zäöüß])/gi,
        "",
      )
      .replace(/\s+/g, " ")
      .trim()
      .replace(/^(die|den|das|der|eine?n?)\s+/i, "");
    title = title.charAt(0).toUpperCase() + title.slice(1);
    return {
      title: title || raw,
      area,
      kind,
      minutes,
      due: off == null ? null : addDays(ctx.today, off),
      stakes: (area === "amt" || area === "geld") && off != null ? 2 : 0,
      note: null,
      project: null,
      steps: [],
    };
  });
}
