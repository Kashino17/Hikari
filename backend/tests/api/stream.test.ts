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
  // Client ohne Range bekommt 200 — upstream fragt der Proxy trotzdem mit
  // bytes=0- an (ohne Range drosselt googlevideo auf Abspieltempo).
  expect(r2.statusCode).toBe(200);
  expect(calls).toBe(1);
});

test("prefetch löst URLs seriell im Voraus auf und überspringt Cache-Treffer", async () => {
  const resolved: string[] = [];
  const ytDlp = (async (args: string[]) => {
    const url = args[args.length - 1] ?? "";
    resolved.push(url.split("v=")[1] ?? "");
    return { stdout: "https://gv/video\n", stderr: "" };
  }) as StreamDeps["ytDlp"];
  const app = Fastify();
  const prefetch = registerStreamRoutes(app, { ytDlp, fetchImpl: okFetch, retryDelaysMs: [] });

  prefetch(["aaaaaaaaaaa", "bbbbbbbbbbb", "nix"]);
  await new Promise((r) => setTimeout(r, 50));
  expect(resolved).toEqual(["aaaaaaaaaaa", "bbbbbbbbbbb"]); // ungültige ID ignoriert

  // Zweiter Lauf: beide bereits im Cache ⇒ kein weiterer yt-dlp-Aufruf.
  prefetch(["aaaaaaaaaaa", "bbbbbbbbbbb"]);
  await new Promise((r) => setTimeout(r, 50));
  expect(resolved).toHaveLength(2);

  // Und der Stream selbst nutzt die vorgewärmte URL ohne neue Auflösung
  // (ohne Client-Range antwortet der Proxy 200 = gechunkter Voll-Stream).
  const res = await app.inject({ url: "/stream/video/aaaaaaaaaaa" });
  expect(res.statusCode).toBe(200);
  expect(resolved).toHaveLength(2);
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
  expect(res.statusCode).toBe(200);
  expect(calls).toBe(0);
});

// Regression: Der Stream-Endpunkt liess ausschliesslich YouTube-IDs durch
// (exakt 11 Zeichen). Jedes manuell importierte Video traegt aber eine
// praefixierte interne ID — "voe_q33qerdkmle2", "sniff_5cc8d7cfdddfd747" —
// und bekam deshalb 400 statt seiner Datei. Dass die Datei einwandfrei auf der
// Platte lag, half nichts: Der Player kam nie bis zum Datei-Fallback und
// zeigte dauerhaft "Wird geladen…".
test("interne Import-IDs werden ausgeliefert, nicht mit 400 abgewiesen", async () => {
  const dir = mkdtempSync(join(tmpdir(), "videos-"));
  const ytDlp = (async () => {
    throw new Error("kein YouTube-Video");
  }) as StreamDeps["ytDlp"];

  for (const id of ["voe_q33qerdkmle2", "sniff_5cc8d7cfdddfd747", "manual_abc-123"]) {
    writeFileSync(join(dir, `${id}.mp4`), "x");
    const app = buildApp({ ytDlp, videoDir: dir, retryDelaysMs: [] });
    const res = await app.inject({ url: `/stream/video/${id}` });
    expect(res.statusCode, `${id} sollte ausgeliefert werden`).toBe(302);
    expect(res.headers.location).toBe(`/videos/${id}.mp4`);
  }
});

// Die ID landet in einem Dateipfad — Punkte und Schrägstriche muessen
// weiterhin abgewiesen werden, sonst waere ein Ausbruch aus dem Videoordner
// moeglich.
test("Pfad-Ausbruchsversuche bleiben abgewiesen", async () => {
  const app = buildApp({ ytDlp: (async () => ({ stdout: "", stderr: "" })) as StreamDeps["ytDlp"] });
  for (const bad of ["..%2f..%2fetc%2fpasswd", "a.b", "..", "%2e%2e", "a%2Fb"]) {
    const res = await app.inject({ url: `/stream/video/${bad}` });
    // 400 aus der Validierung, 404 wenn Fastify den Pfad schon beim Routing
    // verwirft — abgewiesen ist beides, und darauf kommt es hier an.
    expect(res.statusCode, `${bad} durfte nicht durchgehen`).toBeGreaterThanOrEqual(400);
    expect(res.statusCode).toBeLessThan(500);
  }
});

// Fuer eine interne ID ist die YouTube-Aufloesung sinnlos und kostet laut
// Messung 5–11 s — genau die Wartezeit, die als "Wird geladen…" sichtbar wird.
test("liefert lokale Dateien ohne YouTube-Aufloesung aus", async () => {
  const dir = mkdtempSync(join(tmpdir(), "videos-"));
  writeFileSync(join(dir, "sniff_abc123.mp4"), "x");
  let resolveCalls = 0;
  const ytDlp = (async () => {
    resolveCalls++;
    return { stdout: "https://gv/video\n", stderr: "" };
  }) as StreamDeps["ytDlp"];

  const app = buildApp({ ytDlp, videoDir: dir, retryDelaysMs: [] });
  const res = await app.inject({ url: "/stream/video/sniff_abc123" });

  expect(res.statusCode).toBe(302);
  expect(resolveCalls, "yt-dlp darf für eine interne ID nicht bemüht werden").toBe(0);
});
