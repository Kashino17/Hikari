import { mkdtempSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import fastifyMultipart from "@fastify/multipart";
import Fastify, { type FastifyInstance } from "fastify";
import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { VoiceJobStore, VoiceProcessor } from "../voice/jobs.js";
import { WhisperWorker } from "../voice/whisper.js";
import { registerVoiceRoutes } from "./voice.js";

const B = "----hikari-voice";
function multipart(files: { name: string; body: string }[], label?: string): Buffer {
  const parts: Buffer[] = [];
  if (label) {
    parts.push(
      Buffer.from(`--${B}\r\nContent-Disposition: form-data; name="label"\r\n\r\n${label}\r\n`),
    );
  }
  for (const f of files) {
    parts.push(
      Buffer.from(
        `--${B}\r\nContent-Disposition: form-data; name="audio"; filename="${f.name}"\r\nContent-Type: audio/ogg\r\n\r\n${f.body}\r\n`,
      ),
    );
  }
  parts.push(Buffer.from(`--${B}--\r\n`));
  return Buffer.concat(parts);
}

describe("voice routes", () => {
  let dir: string;
  let app: FastifyInstance;
  let processor: VoiceProcessor;
  let release: () => void = () => {};

  beforeEach(async () => {
    dir = mkdtempSync(join(tmpdir(), "hikari-voice-api-"));
    const store = new VoiceJobStore(dir);
    processor = new VoiceProcessor({
      store,
      transcribe: async (p) => ({
        text: `gehört: ${p.split("/").pop()}`,
        duration: 61,
        language: "de",
      }),
      summarize: async () => {
        await new Promise<void>((r) => {
          release = r;
        });
        return { title: "T", summary: "S", points: [], todos: ["A"], dates: [], questions: [] };
      },
    });
    app = Fastify();
    await app.register(fastifyMultipart, { limits: { fileSize: 10 * 1024 * 1024 } });
    await registerVoiceRoutes(app, {
      store,
      processor,
      whisper: new WhisperWorker({ script: "x.py", idleMs: 1000 }),
      lmstudio: { baseUrl: "http://lm", model: "qwen/qwen3.8-27b" },
      fetchImpl: (async () =>
        new Response(
          JSON.stringify({ data: [{ id: "qwen/qwen3.8-27b", state: "loaded" }] }),
        )) as typeof fetch,
      unloadLlm: async () => {},
    });
  });
  afterEach(async () => {
    release();
    await processor.idle();
    await app.close();
    rmSync(dir, { recursive: true, force: true });
  });

  it("nimmt mehrere Sprachnachrichten an und liefert die Zusammenfassung", async () => {
    const res = await app.inject({
      method: "POST",
      url: "/voice/jobs",
      headers: { "content-type": `multipart/form-data; boundary=${B}` },
      payload: multipart(
        [
          { name: "PTT-20261106-WA0007.opus", body: "zwei" },
          { name: "PTT-20261106-WA0003.opus", body: "eins" },
        ],
        "Mehmet",
      ),
    });
    expect(res.statusCode).toBe(201);
    const job = res.json();
    expect(job.total).toBe(2);
    expect(job.label).toBe("Mehmet");
    expect(job.clips.map((c: { name: string }) => c.name)).toEqual([
      "PTT-20261106-WA0003.opus",
      "PTT-20261106-WA0007.opus",
    ]);

    // Während Qwen arbeitet: Löschen verweigert, Status sagt busy
    await new Promise((r) => setTimeout(r, 20));
    expect((await app.inject({ method: "GET", url: `/voice/jobs/${job.id}` })).json().status).toBe(
      "summarizing",
    );
    expect((await app.inject({ method: "DELETE", url: `/voice/jobs/${job.id}` })).statusCode).toBe(
      409,
    );
    expect((await app.inject({ method: "GET", url: "/voice/status" })).json()).toMatchObject({
      busy: true,
      llm: { loaded: true, reachable: true },
    });

    release();
    await processor.idle();
    const done = (await app.inject({ method: "GET", url: `/voice/jobs/${job.id}` })).json();
    expect(done.status).toBe("done");
    expect(done.duration).toBe(122);
    expect(done.summary.todos).toEqual(["A"]);
    expect(done.clips[0].text).toBe("gehört: 01.opus");

    const list = (await app.inject({ method: "GET", url: "/voice/jobs" })).json();
    expect(list).toHaveLength(1);
    expect(list[0]).toMatchObject({ title: "T", todoCount: 1 });
    expect(list[0].clips).toBeUndefined();

    expect((await app.inject({ method: "DELETE", url: `/voice/jobs/${job.id}` })).statusCode).toBe(
      204,
    );
    expect((await app.inject({ method: "GET", url: `/voice/jobs/${job.id}` })).statusCode).toBe(
      404,
    );
  });

  it("lehnt Anfragen ohne Audio ab", async () => {
    const res = await app.inject({
      method: "POST",
      url: "/voice/jobs",
      headers: { "content-type": `multipart/form-data; boundary=${B}` },
      payload: multipart([], "nur Text"),
    });
    expect(res.statusCode).toBe(400);
    expect(res.json().error).toBe("no_audio");
  });

  it("nimmt Dateien über dem globalen 10-MB-Limit an", async () => {
    const big = "x".repeat(11 * 1024 * 1024);
    const res = await app.inject({
      method: "POST",
      url: "/voice/jobs",
      headers: { "content-type": `multipart/form-data; boundary=${B}` },
      payload: multipart([{ name: "lang.m4a", body: big }]),
    });
    expect(res.statusCode).toBe(201);
  });
});
