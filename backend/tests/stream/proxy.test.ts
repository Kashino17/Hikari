import Fastify from "fastify";
import { expect, test } from "vitest";
import { proxyMediaStream } from "../../src/stream/proxy.js";

function appWith(
  resolveUrl: (force: boolean) => Promise<string | undefined>,
  fetchImpl: typeof fetch,
) {
  const app = Fastify();
  app.get("/s/:id", (req, reply) =>
    proxyMediaStream(reply, req.headers.range, resolveUrl, "video", {
      fetchImpl,
      retryDelaysMs: [],
    }),
  );
  return app;
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

test("Auflösung scheitert zweimal ⇒ 502 extraction failed", async () => {
  const app = appWith(async () => undefined, fetch);
  const res = await app.inject({ url: "/s/x" });
  expect(res.statusCode).toBe(502);
  expect(res.json()).toEqual({ error: "video extraction failed" });
});
