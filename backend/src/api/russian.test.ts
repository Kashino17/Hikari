import { execFileSync } from "node:child_process";
import { existsSync, mkdtempSync, readFileSync } from "node:fs";
import { homedir, tmpdir } from "node:os";
import { join } from "node:path";
import fastifyMultipart from "@fastify/multipart";
import Fastify from "fastify";
import { describe, expect, it } from "vitest";
import type { VoiceService } from "../russian/voice.js";
import { referenceClips, registerRussianRoutes, voiceRequest } from "./russian.js";

describe("referenceClips", () => {
  it("nimmt die andere Stimme und die langsamen Varianten, soweit vorhanden", () => {
    const have = new Set(["d03_kak_dela_m", "d03_kak_dela_f", "d03_kak_dela_m_slow"]);
    expect(referenceClips("d03_kak_dela_m", (n) => have.has(n))).toEqual([
      "d03_kak_dela_m",
      "d03_kak_dela_f",
      "d03_kak_dela_m_slow",
    ]);
  });

  it("Labor-Clips: normal + langsam", () => {
    const have = new Set(["lab_abc", "lab_abc_slow"]);
    expect(referenceClips("lab_abc", (n) => have.has(n))).toEqual(["lab_abc", "lab_abc_slow"]);
  });
});

describe("voiceRequest", () => {
  it("nimmt russischen Text und rundet das Tempo auf feste Stufen", () => {
    const q = voiceRequest({ text: "  Как   дел+а? ", voice: "p", rate: "0.71" });
    expect(q).toMatchObject({ text: "Как дел+а?", voice: "p", rate: 0.7 });
    expect(voiceRequest({ text: "Как дел+а?", voice: "p", rate: "0.7" })?.key).toBe(q?.key);
  });

  it("lehnt Leeres, Fremdes und unbekannte Stimmen ab", () => {
    expect(voiceRequest({ text: "" })).toBeNull();
    expect(voiceRequest({ text: "hello world" })).toBeNull();
    expect(voiceRequest({ text: "Привет", voice: "x" })).toBeNull();
    expect(voiceRequest({ text: "д".repeat(401) })).toBeNull();
  });
});

describe("GET /russian/voice", () => {
  const fake = (ready: boolean) => {
    const calls: string[] = [];
    const service = {
      available: true,
      ready: async () => ready,
      warm: () => calls.push("warm"),
      speak: async (text: string, voice: string, rate: number) => {
        calls.push(`speak:${voice}:${rate}:${text}`);
        return Buffer.from("OggS-test");
      },
    } as unknown as VoiceService;
    return { service, calls };
  };
  const build = async (voice: VoiceService) => {
    const app = Fastify();
    await app.register(fastifyMultipart);
    await registerRussianRoutes(app, {
      voice,
      voiceCacheDir: mkdtempSync(join(tmpdir(), "ru-voice-")),
    });
    await app.ready();
    return app;
  };
  const url = `/russian/voice?voice=p&rate=1&text=${encodeURIComponent("Как дел+а?")}`;

  it("spricht, legt das Ergebnis ab und nimmt es beim zweiten Mal aus dem Zwischenspeicher", async () => {
    const { service, calls } = fake(true);
    const app = await build(service);
    const first = await app.inject({ method: "GET", url });
    expect(first.statusCode).toBe(200);
    expect(first.headers["content-type"]).toBe("audio/ogg");
    expect(first.rawPayload.toString()).toBe("OggS-test");
    const second = await app.inject({ method: "GET", url });
    expect(second.statusCode).toBe(200);
    expect(calls).toEqual(["speak:p:1:Как дел+а?"]);
    await app.close();
  });

  it("antwortet sofort mit 503 und startet den Dienst, solange das Modell noch lädt", async () => {
    const { service, calls } = fake(false);
    const app = await build(service);
    const res = await app.inject({ method: "GET", url });
    expect(res.statusCode).toBe(503);
    expect(res.json()).toEqual({ error: "voice_warming" });
    expect(calls).toEqual(["warm"]);
    const status = await app.inject({ method: "GET", url: "/russian/voice/status" });
    expect(status.json()).toEqual({ available: true, ready: false });
    await app.close();
  });

  it("lehnt Anfragen ohne russischen Text ab", async () => {
    const app = await build(fake(true).service);
    const res = await app.inject({ method: "GET", url: "/russian/voice?text=hello" });
    expect(res.statusCode).toBe(400);
    await app.close();
  });
});

const envReady = existsSync(join(homedir(), ".hikari", "phoneme-env", "bin", "python"));

describe.skipIf(!envReady)("POST /russian/pronunciation (echter Erkenner)", () => {
  it("Muttersprachler-Aufnahme (andere Stimme) erreicht hohen Wert", async () => {
    const app = Fastify();
    await app.register(fastifyMultipart);
    await registerRussianRoutes(app);
    await app.ready();

    const audio = join(process.cwd(), "..", "android/app/src/main/assets/russian/audio");
    const take = join(mkdtempSync(join(tmpdir(), "ru-test-")), "take.wav");
    // "Lernender" = weibliche Stimme, langsam — Referenz ist die männliche normale.
    execFileSync("ffmpeg", [
      "-y",
      "-loglevel",
      "error",
      "-i",
      join(audio, "d02_menya_zovut_f_slow.ogg"),
      take,
    ]);

    const boundary = "----hikari";
    const body = Buffer.concat([
      Buffer.from(
        `--${boundary}\r\nContent-Disposition: form-data; name="clip"\r\n\r\nd02_menya_zovut_m\r\n`,
      ),
      Buffer.from(
        `--${boundary}\r\nContent-Disposition: form-data; name="file"; filename="take.wav"\r\nContent-Type: audio/wav\r\n\r\n`,
      ),
      readFileSync(take),
      Buffer.from(`\r\n--${boundary}--\r\n`),
    ]);
    const res = await app.inject({
      method: "POST",
      url: "/russian/pronunciation",
      headers: { "content-type": `multipart/form-data; boundary=${boundary}` },
      payload: body,
    });
    expect(res.statusCode).toBe(200);
    const json = res.json() as { score: number; marks: unknown[] };
    expect(json.marks.length).toBeGreaterThan(3);
    expect(json.score).toBeGreaterThanOrEqual(70);

    // Ein ganz anderer Satz muss deutlich schlechter abschneiden.
    const wrong = join(mkdtempSync(join(tmpdir(), "ru-test-")), "wrong.wav");
    execFileSync("ffmpeg", [
      "-y",
      "-loglevel",
      "error",
      "-i",
      join(audio, "d01_dobryy_vecher_m.ogg"),
      wrong,
    ]);
    const wrongBody = Buffer.concat([
      Buffer.from(
        `--${boundary}\r\nContent-Disposition: form-data; name="clip"\r\n\r\nd02_menya_zovut_m\r\n`,
      ),
      Buffer.from(
        `--${boundary}\r\nContent-Disposition: form-data; name="file"; filename="w.wav"\r\nContent-Type: audio/wav\r\n\r\n`,
      ),
      readFileSync(wrong),
      Buffer.from(`\r\n--${boundary}--\r\n`),
    ]);
    const wrongRes = await app.inject({
      method: "POST",
      url: "/russian/pronunciation",
      headers: { "content-type": `multipart/form-data; boundary=${boundary}` },
      payload: wrongBody,
    });
    const wrongScore = (wrongRes.json() as { score: number }).score;
    expect(wrongScore).toBeLessThan(json.score - 20);

    const bad = await app.inject({
      method: "POST",
      url: "/russian/pronunciation",
      headers: { "content-type": `multipart/form-data; boundary=${boundary}` },
      payload: Buffer.from(
        `--${boundary}\r\nContent-Disposition: form-data; name="clip"\r\n\r\n../../etc/passwd\r\n--${boundary}\r\nContent-Disposition: form-data; name="file"; filename="x.wav"\r\n\r\nabc\r\n--${boundary}--\r\n`,
      ),
    });
    expect(bad.statusCode).toBe(400);
  }, 120_000);
});
