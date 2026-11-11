import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { KeepAwake, isMediaRequest } from "./keep-awake.js";

function fakeSpawn() {
  const calls: string[][] = [];
  const kills: number[] = [];
  const handlers: Record<string, (() => void)[]> = {};
  const spawn = (cmd: string, args: string[]) => {
    calls.push([cmd, ...args]);
    const n = calls.length;
    return {
      kill: () => {
        kills.push(n);
        return true;
      },
      on(event: string, cb: () => void) {
        handlers[event] = [...(handlers[event] ?? []), cb];
        return this;
      },
    } as never;
  };
  const emit = (event: string) => {
    for (const cb of handlers[event] ?? []) cb();
  };
  return { spawn, calls, kills, emit };
}

describe("Wachhalten beim Streamen", () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it("startet beim ersten Abruf und bleibt bis kurz nach dem letzten wach", () => {
    const f = fakeSpawn();
    const keep = new KeepAwake({ spawn: f.spawn, platform: "darwin", pid: 4711, lingerMs: 1000 });
    const endA = keep.begin();
    expect(f.calls).toEqual([["caffeinate", "-i", "-w", "4711"]]);
    const endB = keep.begin();
    expect(f.calls).toHaveLength(1); // ein Wachhalter reicht für alle Abrufe

    endA();
    vi.advanceTimersByTime(5000);
    expect(keep.awake).toBe(true); // B läuft noch

    endB();
    vi.advanceTimersByTime(999);
    expect(keep.awake).toBe(true); // Nachlauf: Pause, nächste Folge
    vi.advanceTimersByTime(1);
    expect(keep.awake).toBe(false);
    expect(f.kills).toEqual([1]);
  });

  it("ein neuer Abruf im Nachlauf hält weiter wach, ohne neu zu starten", () => {
    const f = fakeSpawn();
    const keep = new KeepAwake({ spawn: f.spawn, platform: "darwin", lingerMs: 1000 });
    keep.begin()();
    vi.advanceTimersByTime(600);
    const end = keep.begin();
    vi.advanceTimersByTime(5000);
    expect(keep.awake).toBe(true);
    expect(f.calls).toHaveLength(1);
    end();
    end(); // doppeltes Ende (fertig UND Verbindung zu) zählt nur einmal
    vi.advanceTimersByTime(1000);
    expect(keep.awake).toBe(false);
  });

  it("startet neu, wenn der Wachhalter von selbst endet", () => {
    const f = fakeSpawn();
    const keep = new KeepAwake({ spawn: f.spawn, platform: "darwin", lingerMs: 1000 });
    const end = keep.begin();
    f.emit("exit");
    expect(keep.awake).toBe(false);
    keep.begin();
    expect(f.calls).toHaveLength(2);
    end();
  });

  it("tut außerhalb von macOS nichts", () => {
    const f = fakeSpawn();
    const keep = new KeepAwake({ spawn: f.spawn, platform: "linux" });
    keep.begin()();
    expect(f.calls).toEqual([]);
    expect(keep.awake).toBe(false);
  });

  it("zählt nur Abrufe, bei denen Bild oder Ton fließt", () => {
    expect(isMediaRequest("/videos/sniff_5cc8d7cfdddfd747.mp4")).toBe(true);
    expect(isMediaRequest("/stream/video/dQw4w9WgXcQ")).toBe(true);
    expect(isMediaRequest("/music/audio/dQw4w9WgXcQ?x=1")).toBe(true);
    expect(isMediaRequest("/music/video/dQw4w9WgXcQ")).toBe(true);
    expect(isMediaRequest("/videos/sniff_1/full")).toBe(false);
    expect(isMediaRequest("/library")).toBe(false);
    expect(isMediaRequest("/music/search?q=x")).toBe(false);
    expect(isMediaRequest("/covers/poster_ted.jpg")).toBe(false);
  });
});
