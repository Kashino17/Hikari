import type { ChildProcess } from "node:child_process";
import { EventEmitter } from "node:events";
import { PassThrough } from "node:stream";
import { afterEach, describe, expect, it, vi } from "vitest";
import { WhisperWorker } from "./whisper.js";

/** Kindprozess-Attrappe, die jeden Auftrag sofort beantwortet. */
function fakeWorker(answer: (req: { id: string; path: string }) => Record<string, unknown>) {
  let spawned = 0;
  let killed = 0;
  const spawnWorker = () => {
    spawned++;
    const proc = new EventEmitter() as ChildProcess & EventEmitter;
    const stdin = new PassThrough();
    const stdout = new PassThrough();
    Object.assign(proc, { stdin, stdout, stderr: new PassThrough() });
    proc.kill = () => {
      killed++;
      proc.emit("exit", null);
      return true;
    };
    stdout.write('{"ready": true}\n');
    stdin.on("data", (buf: Buffer) => {
      for (const line of buf.toString().split("\n").filter(Boolean)) {
        const req = JSON.parse(line);
        stdout.write(`${JSON.stringify({ id: req.id, ...answer(req) })}\n`);
      }
    });
    return proc;
  };
  return { spawnWorker, spawned: () => spawned, killed: () => killed };
}

afterEach(() => vi.useRealTimers());

describe("WhisperWorker", () => {
  it("startet einmal, transkribiert und entlädt nach Leerlauf", async () => {
    vi.useFakeTimers();
    const fake = fakeWorker((r) => ({
      text: `Text für ${r.path}`,
      duration: 12.5,
      language: "de",
    }));
    const w = new WhisperWorker({
      script: "x.py",
      idleMs: 10 * 60_000,
      spawnWorker: fake.spawnWorker,
    });

    expect(await w.transcribe("/a.opus")).toEqual({
      text: "Text für /a.opus",
      duration: 12.5,
      language: "de",
    });
    expect(await w.transcribe("/b.opus")).toMatchObject({ text: "Text für /b.opus" });
    expect(fake.spawned()).toBe(1);
    expect(w.loaded).toBe(true);

    vi.advanceTimersByTime(9 * 60_000);
    expect(w.loaded).toBe(true);
    vi.advanceTimersByTime(60_000);
    expect(w.loaded).toBe(false);
    expect(fake.killed()).toBe(1);

    // Neuer Auftrag lädt neu
    await w.transcribe("/c.opus");
    expect(fake.spawned()).toBe(2);
    w.stop();
  });

  it("gibt Worker-Fehler als Ablehnung weiter", async () => {
    const fake = fakeWorker(() => ({ error: "RuntimeError: kaputt" }));
    const w = new WhisperWorker({ script: "x.py", idleMs: 1000, spawnWorker: fake.spawnWorker });
    await expect(w.transcribe("/a.opus")).rejects.toThrow("RuntimeError: kaputt");
    w.stop();
  });

  it("lehnt offene Aufträge ab, wenn der Prozess stirbt", async () => {
    let proc: ChildProcess | null = null;
    const w = new WhisperWorker({
      script: "x.py",
      idleMs: 1000,
      spawnWorker: () => {
        const p = new EventEmitter() as ChildProcess & EventEmitter;
        Object.assign(p, {
          stdin: new PassThrough(),
          stdout: new PassThrough(),
          stderr: new PassThrough(),
        });
        p.kill = () => true;
        proc = p;
        return p;
      },
    });
    const pending = w.transcribe("/a.opus");
    (proc as unknown as EventEmitter).emit("exit", 1);
    await expect(pending).rejects.toThrow("whisper_exited_1");
    expect(w.loaded).toBe(false);
  });
});
