import { bpbSource } from "./bpb.js";
import { gutenbergSource } from "./gutenberg.js";
import { openAccessSource } from "./openaccess.js";
import type { EbookSource } from "./types.js";
import { wikisourceSource } from "./wikisource.js";

/** Reihenfolge: verlässliche Quellen zuerst, die oft gedrosselte Wikisource zuletzt. */
export const sources: EbookSource[] = [
  gutenbergSource,
  bpbSource,
  openAccessSource,
  wikisourceSource,
];
