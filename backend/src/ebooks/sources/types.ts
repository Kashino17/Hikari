import type { PoliteHttp } from "../http.js";
import type { RawBook } from "../persist.js";

/** Merker je Quelle (z. B. schon ausgewertete Seiten) — überlebt Neustarts. */
export interface SourceCache {
  get<T>(key: string): T | undefined;
  set(key: string, value: unknown): void;
}

export interface SourceContext {
  http: PoliteHttp;
  cache: SourceCache;
  log: (msg: string) => void;
}

export interface EbookSource {
  readonly id: string;
  readonly name: string;
  listCatalog(ctx: SourceContext): Promise<RawBook[]>;
}

/** Die Quelle lässt uns nicht herein (403) — diesen Lauf überspringen. */
export class SourceBlocked extends Error {
  constructor(
    public readonly source: string,
    public readonly status: number,
  ) {
    super(`${source}: gesperrt (HTTP ${status})`);
    this.name = "SourceBlocked";
  }
}
