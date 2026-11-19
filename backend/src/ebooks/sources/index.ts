import { bpbSource } from "./bpb.js";
import { gutenbergSource } from "./gutenberg.js";
import { openAccessSource } from "./openaccess.js";
import type { EbookSource } from "./types.js";

/**
 * Aktive Quellen. Wikisource bleibt aus (Kadir, 19.11.): Wikimedia verlangt eine Kontaktadresse,
 * die wir nicht mitschicken. Der Adapter (wikisource.ts) liegt für später bereit.
 */
export const sources: EbookSource[] = [gutenbergSource, bpbSource, openAccessSource];
