import Database from "better-sqlite3";
import Fastify from "fastify";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { registerVideosRoutes } from "../../src/api/videos.js";
import { applyMigrations } from "../../src/db/migrations.js";

describe("POST /videos/:id/download", () => {
  let db: Database.Database;
  beforeEach(() => {
    db = new Database(":memory:");
    applyMigrations(db);
    db.prepare("INSERT INTO channels (id,url,title,added_at) VALUES ('c1','x','C',0)").run();
    db.prepare(
      `INSERT INTO videos (id, channel_id, title, published_at, duration_seconds, discovered_at)
       VALUES ('v1', 'c1', 't', 0, 600, 0)`,
    ).run();
  });

  it("unbekannt ⇒ 404; queued ⇒ 202 + Row nach Abschluss; ready ⇒ 200", async () => {
    let resolveDl: (v: { filePath: string; fileSizeBytes: number }) => void = () => {};
    const download = vi.fn(
      () =>
        new Promise<{ filePath: string; fileSizeBytes: number }>((res) => {
          resolveDl = res;
        }),
    );
    const app = Fastify();
    await registerVideosRoutes(app, {
      db,
      videoDir: "/tmp",
      coverDir: "/tmp",
      extractor: null,
      download: download as never,
    });

    expect((await app.inject({ method: "POST", url: "/videos/nix/download" })).statusCode).toBe(404);

    const q = await app.inject({ method: "POST", url: "/videos/v1/download" });
    expect(q.statusCode).toBe(202);
    expect(q.json()).toEqual({ status: "queued" });
    // Doppel-POST waehrend des Laufs startet keinen zweiten Download
    await app.inject({ method: "POST", url: "/videos/v1/download" });
    expect(download).toHaveBeenCalledTimes(1);

    resolveDl({ filePath: "/tmp/v1.mp4", fileSizeBytes: 123 });
    await new Promise((r) => setTimeout(r, 20)); // fire-and-forget abschliessen lassen
    expect(
      db.prepare("SELECT file_path FROM downloaded_videos WHERE video_id='v1'").get(),
    ).toEqual({ file_path: "/tmp/v1.mp4" });

    const ready = await app.inject({ method: "POST", url: "/videos/v1/download" });
    expect(ready.statusCode).toBe(200);
    expect(ready.json()).toEqual({ status: "ready" });
  });
});
