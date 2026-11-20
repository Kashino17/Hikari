import { extname } from "node:path";
import type { FastifyInstance } from "fastify";
import type { CaptureProcessor } from "../tasks/capture.js";
import type { TaskParser } from "../tasks/parse.js";
import { briefing, fitFor, isoDay } from "../tasks/plan.js";
import type { Capture, NewTask, TaskStore } from "../tasks/store.js";

export interface TaskDeps {
  store: TaskStore;
  processor: CaptureProcessor;
  parser: Pick<TaskParser, "split">;
  today?: () => string;
}

const MAX_AUDIO = 50 * 1024 * 1024;
const SOURCES = new Set(["voice", "text", "share", "app"]);

function audioExt(name: string, mime: string): string {
  const e = extname(name).toLowerCase();
  if (/^\.(m4a|mp4|aac|ogg|opus|webm|wav|mp3|amr|3gp)$/.test(e)) return e;
  if (/mp4|m4a|aac/.test(mime)) return ".m4a";
  if (/ogg|opus/.test(mime)) return ".ogg";
  if (/webm/.test(mime)) return ".webm";
  return ".audio";
}

export async function registerTaskRoutes(app: FastifyInstance, deps: TaskDeps) {
  const { store, processor } = deps;
  const today = () => deps.today?.() ?? isoDay();

  const pending = () =>
    store.captures().filter((c) => c.status !== "done" && c.status !== "failed").length;

  app.get("/tasks", async () => ({ today: today(), ...store.state(), pending: pending() }));

  app.get<{ Querystring: { minutes?: string; max?: string } }>("/tasks/briefing", async (req) => {
    const minutes = Number(req.query.minutes);
    if (Number.isFinite(minutes) && minutes > 0) {
      const items = fitFor(store.state(), today(), minutes, Number(req.query.max) || 3);
      return {
        items: items.map((t) => ({ id: t.id, title: t.title, minutes: t.minutes, area: t.area })),
        minutes: items.reduce((n, t) => n + t.minutes, 0),
        open: briefing(store.state(), today()).open,
      };
    }
    return briefing(store.state(), today());
  });

  app.post<{ Body: NewTask }>("/tasks", async (req, reply) => {
    if (!req.body?.title?.trim()) return reply.code(400).send({ error: "title_required" });
    const t = store.addTask({ ...req.body, project: null, source: "app" });
    store.save();
    return reply.code(201).send(t);
  });

  app.patch<{ Params: { id: string }; Body: Record<string, unknown> }>(
    "/tasks/:id",
    async (req, reply) => {
      const body = { ...(req.body ?? {}) };
      if (body.done === true && body.doneAt == null) body.doneAt = Date.now();
      if (body.done === false) body.doneAt = null;
      const t = store.updateTask(req.params.id, body);
      if (!t) return reply.code(404).send({ error: "not_found" });
      store.save();
      return t;
    },
  );

  app.delete<{ Params: { id: string } }>("/tasks/:id", async (req, reply) => {
    if (!store.deleteTask(req.params.id)) return reply.code(404).send({ error: "not_found" });
    store.save();
    return reply.code(204).send();
  });

  /** Hikaris Vorschlag für Schritte. Ohne Modell: ein schlichter Dreischritt. */
  app.post<{ Params: { id: string } }>("/tasks/:id/split-suggest", async (req, reply) => {
    const t = store.task(req.params.id);
    if (!t) return reply.code(404).send({ error: "not_found" });
    try {
      return { steps: await deps.parser.split(t.title, t.minutes), fallback: false };
    } catch {
      const third = Math.max(5, Math.round(t.minutes / 3));
      return {
        steps: [
          { title: "Unterlagen raussuchen", minutes: third },
          { title: t.title, minutes: third },
          { title: "Bestätigung ablegen", minutes: 5 },
        ],
        fallback: true,
      };
    }
  });

  /** Aufgabe durch ein Vorhaben mit den gegebenen Schritten ersetzen. */
  app.post<{ Params: { id: string }; Body: { steps?: { title: string; minutes?: number }[] } }>(
    "/tasks/:id/split",
    async (req, reply) => {
      const t = store.task(req.params.id);
      if (!t) return reply.code(404).send({ error: "not_found" });
      const steps = (req.body?.steps ?? []).filter((s) => s?.title?.trim()).slice(0, 8);
      if (steps.length < 2) return reply.code(400).send({ error: "steps_required" });
      const proj = store.addProject({
        title: t.title,
        area: t.area,
        deadline: t.due,
        goal: t.note ?? "Frist",
        origin: t.title,
      });
      const ids: string[] = [];
      for (const s of steps) {
        const n = store.addTask({
          title: s.title,
          area: t.area,
          kind: t.kind,
          minutes: s.minutes ?? Math.round(t.minutes / steps.length),
          stakes: t.stakes,
          project: proj.id,
          source: t.source,
        });
        proj.steps.push({ task: n.id });
        ids.push(n.id);
      }
      // War die Aufgabe ein Schritt eines Vorhabens, verschwindet sie dort; die neuen Schritte stehen eigenständig.
      store.deleteTask(t.id);
      store.save();
      return reply.code(201).send({ project: proj, tasks: ids });
    },
  );

  // ---- Aufnahmen: Ton oder Text → Aufgaben, im Hintergrund ----

  app.post("/tasks/capture", async (req, reply) => {
    let source = "voice";
    let text: string | null = null;
    let audio: { data: Buffer; ext: string } | null = null;
    if (req.isMultipart()) {
      try {
        for await (const part of req.parts({ limits: { fileSize: MAX_AUDIO, files: 1 } })) {
          if (part.type === "file") {
            const data = await part.toBuffer();
            if (part.file.truncated) return reply.code(413).send({ error: "file_too_large" });
            if (data.length) audio = { data, ext: audioExt(part.filename ?? "", part.mimetype) };
          } else if (part.fieldname === "source") source = String(part.value ?? "voice");
          else if (part.fieldname === "text") text = String(part.value ?? "");
        }
      } catch (err) {
        if ((err as { code?: string }).code === "FST_REQ_FILE_TOO_LARGE")
          return reply.code(413).send({ error: "file_too_large" });
        throw err;
      }
    } else {
      const b = (req.body ?? {}) as { text?: string; source?: string };
      text = b.text ?? null;
      source = b.source ?? "text";
    }
    if (!audio && !text?.trim()) return reply.code(400).send({ error: "empty" });
    const c = store.newCapture(
      (SOURCES.has(source) ? source : "text") as Capture["source"],
      text?.slice(0, 4000) ?? null,
      audio,
    );
    processor.enqueue(c.id);
    return reply.code(201).send(view(c));
  });

  const view = (c: Capture) => ({
    id: c.id,
    status: c.status,
    fallback: c.fallback,
    error: c.error,
    text: c.text,
    created: c.created.flatMap((id) => {
      const t = store.task(id);
      if (!t) return [];
      const p = t.project ? store.project(t.project) : undefined;
      const step = p
        ? p.steps
            .filter((s) => !("fixed" in s && s.fixed))
            .findIndex((s) => "task" in s && s.task === id) + 1
        : null;
      return [
        {
          id: t.id,
          title: t.title,
          area: t.area,
          kind: t.kind,
          minutes: t.minutes,
          due: t.due,
          project: p?.title ?? null,
          step,
        },
      ];
    }),
  });

  app.get<{ Params: { id: string } }>("/tasks/capture/:id", async (req, reply) => {
    const c = store.capture(req.params.id);
    if (!c) return reply.code(404).send({ error: "not_found" });
    return view(c);
  });

  /** Alles, was diese Aufnahme angelegt hat, wieder weg. */
  app.post<{ Params: { id: string } }>("/tasks/capture/:id/undo", async (req, reply) => {
    const c = store.capture(req.params.id);
    if (!c) return reply.code(404).send({ error: "not_found" });
    for (const pid of c.createdProjects) store.deleteProject(pid);
    for (const id of c.created) store.deleteTask(id);
    store.setCapture(c, { created: [], createdProjects: [] });
    return { ok: true };
  });
}
