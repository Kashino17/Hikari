import { bpbSource } from "./bpb.js";
import { gutenbergSource } from "./gutenberg.js";
import { openAccessSource } from "./openaccess.js";
import type { EbookSource } from "./types.js";
import { wikisourceSource } from "./wikisource.js";

export const sources: EbookSource[] = [
  gutenbergSource,
  wikisourceSource,
  bpbSource,
  openAccessSource,
];
