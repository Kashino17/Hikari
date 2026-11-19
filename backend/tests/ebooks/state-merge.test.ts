import { readFileSync } from "node:fs";
import { join } from "node:path";
import { describe, expect, it } from "vitest";
import { type BookState, mergeState } from "../../src/ebooks/state-merge.js";

const cases = JSON.parse(
  readFileSync(join(__dirname, "../fixtures/ebooks/merge-cases.json"), "utf8"),
) as {
  name: string;
  a: BookState;
  b: BookState;
  expected: BookState;
}[];

describe("mergeState (gemeinsame Fälle mit der App)", () => {
  for (const c of cases) it(c.name, () => expect(mergeState(c.a, c.b)).toEqual(c.expected));
  it("es gibt mindestens 6 Fälle", () => expect(cases.length).toBeGreaterThanOrEqual(6));
});
