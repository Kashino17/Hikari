import { execFileSync } from "node:child_process";
import { existsSync, mkdtempSync, readFileSync } from "node:fs";
import { homedir, tmpdir } from "node:os";
import { join } from "node:path";
import fastifyMultipart from "@fastify/multipart";
import Fastify from "fastify";
import { describe, expect, it } from "vitest";
import { referenceClips, registerRussianRoutes } from "./russian.js";

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
