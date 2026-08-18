import Fastify from "fastify";
import { expect, test } from "vitest";
import { proxyMediaStream } from "../../src/stream/proxy.js";

function appWith(
  resolveUrl: (force: boolean) => Promise<string | undefined>,
  fetchImpl: typeof fetch,
  chunkBytes?: number,
) {
  const app = Fastify();
  app.get("/s/:id", (req, reply) =>
    proxyMediaStream(reply, req.headers.range, resolveUrl, "video", {
      fetchImpl,
      retryDelaysMs: [],
      ...(chunkBytes !== undefined ? { chunkBytes } : {}),
    }),
  );
  return app;
}

// googlevideo blockt offene Ranges ("bytes=N-") mit 403 — ein Chunk-Server,
// der nur begrenzte Ranges beantwortet, wie der echte Upstream.
function chunkServer(data: string, seenRanges: string[]): typeof fetch {
  return (async (_url: unknown, init?: RequestInit) => {
    const headers = init?.headers as Record<string, string> | undefined;
    const range = headers?.range ?? "";
    seenRanges.push(range);
    const m = /^bytes=(\d+)-(\d+)$/.exec(range);
    if (!m) return new Response("open range blocked", { status: 403 });
    const from = Number(m[1]);
    const to = Math.min(Number(m[2]), data.length - 1);
    return new Response(data.slice(from, to + 1), {
      status: 206,
      headers: {
        "content-range": `bytes ${from}-${to}/${data.length}`,
        "content-type": "video/mp4",
      },
    });
  }) as typeof fetch;
}

test("reicht Range durch, sendet Browser-UA und spiegelt 206 + Content-Range", async () => {
  const seen: { range?: string; ua?: string } = {};
  const fetchImpl = (async (_url: unknown, init?: RequestInit) => {
    const headers = init?.headers as Record<string, string> | undefined;
    seen.range = headers?.range;
    seen.ua = headers?.["user-agent"];
    return new Response("ab", {
      status: 206,
      headers: { "content-range": "bytes 0-1/2", "content-type": "video/mp4" },
    });
  }) as typeof fetch;
  const app = appWith(async () => "https://gv/ok", fetchImpl);
  const res = await app.inject({ url: "/s/x", headers: { range: "bytes=0-1" } });
  expect(seen.range).toBe("bytes=0-1");
  // googlevideo liefert ohne browserartigen User-Agent 403 (beobachtet 2026-08).
  expect(seen.ua).toMatch(/^Mozilla\/5\.0/);
  expect(res.statusCode).toBe(206);
  expect(res.headers["content-range"]).toBe("bytes 0-1/2");
});

test("403 vom Upstream ⇒ zweite Auflösung mit force=true", async () => {
  const forces: boolean[] = [];
  const urls = ["https://gv/alt", "https://gv/frisch"];
  const fetchImpl = (async (url: unknown) =>
    new Response(String(url).includes("frisch") ? "ok" : "", {
      status: String(url).includes("frisch") ? 200 : 403,
    })) as typeof fetch;
  const app = appWith(async (force) => {
    forces.push(force);
    return urls[forces.length - 1];
  }, fetchImpl);
  const res = await app.inject({ url: "/s/x" });
  expect(forces).toEqual([false, true]);
  expect(res.statusCode).toBe(200);
});

test("offener Range (bytes=0-) ⇒ begrenzte Upstream-Chunks, 206 mit vollem Content-Range", async () => {
  const seen: string[] = [];
  const app = appWith(async () => "https://gv/ok", chunkServer("abcdefghij", seen), 4);
  const res = await app.inject({ url: "/s/x", headers: { range: "bytes=0-" } });
  expect(res.statusCode).toBe(206);
  expect(res.headers["content-range"]).toBe("bytes 0-9/10");
  expect(res.headers["content-length"]).toBe("10");
  expect(res.body).toBe("abcdefghij");
  // erst der Passthrough-Versuch (403), dann begrenzte Chunks
  expect(seen).toEqual(["bytes=0-", "bytes=0-3", "bytes=4-7", "bytes=8-9"]);
});

test("offener Range ab Offset (bytes=6-) ⇒ Chunks ab Offset, Content-Range 6-9/10", async () => {
  const seen: string[] = [];
  const app = appWith(async () => "https://gv/ok", chunkServer("abcdefghij", seen), 4);
  const res = await app.inject({ url: "/s/x", headers: { range: "bytes=6-" } });
  expect(res.statusCode).toBe(206);
  expect(res.headers["content-range"]).toBe("bytes 6-9/10");
  expect(res.body).toBe("ghij");
  expect(seen).toEqual(["bytes=6-", "bytes=6-9"]);
});

test("kein Range ⇒ gechunkter Voll-Stream als 200 mit Content-Length", async () => {
  const seen: string[] = [];
  const app = appWith(async () => "https://gv/ok", chunkServer("abcdefghij", seen), 4);
  const res = await app.inject({ url: "/s/x" });
  expect(res.statusCode).toBe(200);
  expect(res.headers["content-length"]).toBe("10");
  expect(res.body).toBe("abcdefghij");
  expect(seen).toEqual(["", "bytes=0-3", "bytes=4-7", "bytes=8-9"]);
});

test("Auflösung scheitert zweimal ⇒ 502 extraction failed", async () => {
  const app = appWith(async () => undefined, fetch);
  const res = await app.inject({ url: "/s/x" });
  expect(res.statusCode).toBe(502);
  expect(res.json()).toEqual({ error: "video extraction failed" });
});
