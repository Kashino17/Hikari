import { mkdtempSync, writeFileSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import Fastify from "fastify";
import { expect, test } from "vitest";
import { registerStreamRoutes, type StreamDeps } from "../../src/api/stream.js";

const VID = "dQw4w9WgXcQ";

function buildApp(deps: StreamDeps) {
  const app = Fastify();
  registerStreamRoutes(app, deps);
  return app;
}

const okFetch = (async () =>
  new Response("bytes", {
    status: 206,
    headers: { "content-range": "bytes 0-4/5" },
  })) as typeof fetch;

test("löst per yt-dlp auf, proxied mit Range und cached die URL (1x yt-dlp für 2 Requests)", async () => {
  let calls = 0;
  const ytDlp = (async () => {
    calls++;
    return { stdout: "https://gv/video\n", stderr: "" };
  }) as StreamDeps["ytDlp"];
  const app = buildApp({ ytDlp, fetchImpl: okFetch, retryDelaysMs: [] });
  const r1 = await app.inject({ url: `/stream/video/${VID}`, headers: { range: "bytes=0-4" } });
  const r2 = await app.inject({ url: `/stream/video/${VID}` });
  expect(r1.statusCode).toBe(206);
  expect(r1.headers["content-range"]).toBe("bytes 0-4/5");
  expect(r2.statusCode).toBe(206);
  expect(calls).toBe(1);
});

test("ungültige videoId ⇒ 400", async () => {
  const app = buildApp({});
  const res = await app.inject({ url: "/stream/video/nix!gut" });
  expect(res.statusCode).toBe(400);
});

test("Manifest-URL (m3u8) wird verworfen ⇒ 502 ohne Datei-Fallback", async () => {
  const ytDlp = (async () => ({
    stdout: "https://gv/x.m3u8\n",
    stderr: "",
  })) as StreamDeps["ytDlp"];
  const app = buildApp({ ytDlp, fetchImpl: okFetch, retryDelaysMs: [] });
  const res = await app.inject({ url: `/stream/video/${VID}` });
  expect(res.statusCode).toBe(502);
});

test("Auflösung scheitert + Serverdatei existiert ⇒ 302 auf /videos/<id>.mp4", async () => {
  const dir = mkdtempSync(join(tmpdir(), "videos-"));
  writeFileSync(join(dir, `${VID}.mp4`), "x");
  const ytDlp = (async () => {
    throw new Error("yt-dlp down");
  }) as StreamDeps["ytDlp"];
  const app = buildApp({ ytDlp, videoDir: dir, retryDelaysMs: [] });
  const res = await app.inject({ url: `/stream/video/${VID}` });
  expect(res.statusCode).toBe(302);
  expect(res.headers.location).toBe(`/videos/${VID}.mp4`);
});

test("Auflösung scheitert ohne Serverdatei ⇒ 502", async () => {
  const ytDlp = (async () => {
    throw new Error("yt-dlp down");
  }) as StreamDeps["ytDlp"];
  const app = buildApp({ ytDlp, retryDelaysMs: [] });
  const res = await app.inject({ url: `/stream/video/${VID}` });
  expect(res.statusCode).toBe(502);
});

test("persistiert aufgelöste URLs beim onClose in die Cache-Datei", async () => {
  const dir = mkdtempSync(join(tmpdir(), "streamcache-"));
  const path = join(dir, "video-stream-cache.json");
  const ytDlp = (async () => ({
    stdout: "https://gv/video\n",
    stderr: "",
  })) as StreamDeps["ytDlp"];
  const app = buildApp({ ytDlp, fetchImpl: okFetch, streamCachePath: path, retryDelaysMs: [] });
  await app.inject({ url: `/stream/video/${VID}` });
  await app.close();

  // Neue Instanz liest den persistierten Cache — yt-dlp darf nicht mehr laufen.
  let calls = 0;
  const countingYtDlp = (async () => {
    calls++;
    return { stdout: "https://gv/neu\n", stderr: "" };
  }) as StreamDeps["ytDlp"];
  const app2 = buildApp({
    ytDlp: countingYtDlp,
    fetchImpl: okFetch,
    streamCachePath: path,
    retryDelaysMs: [],
  });
  const res = await app2.inject({ url: `/stream/video/${VID}` });
  expect(res.statusCode).toBe(206);
  expect(calls).toBe(0);
});
