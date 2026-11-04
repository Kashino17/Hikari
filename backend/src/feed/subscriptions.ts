import type Database from "better-sqlite3";
import { MANUAL_CHANNEL_ID } from "../import/manual-import.js";

/**
 * Der Feed besteht ausschließlich aus abonnierten Kanälen: status
 * 'subscribed' UND aktiv. Keine Bewertung, keine Entdeckung, keine eigenen
 * Vorschläge — was ein Abo hochlädt, landet im Feed; alles andere nie.
 *
 * Der Pseudo-Kanal der manuellen Importe gilt technisch ebenfalls als aktiv,
 * gehört aber in die Bibliothek, nicht in den Feed — deshalb ausgenommen.
 */
export const SUBSCRIBED_CHANNEL_IDS_SQL = `SELECT id FROM channels
  WHERE status = 'subscribed' AND is_active = 1 AND id <> '${MANUAL_CHANNEL_ID}'`;

/** SQL-Bedingung: `column` (eine Kanal-ID) gehört zu einem abonnierten Kanal. */
export function subscribedChannelCondition(column: string): string {
  return `${column} IN (${SUBSCRIBED_CHANNEL_IDS_SQL})`;
}

export function isSubscribedChannel(db: Database.Database, channelId: string): boolean {
  return (
    db
      .prepare(`SELECT 1 AS ok FROM channels WHERE id = ? AND id IN (${SUBSCRIBED_CHANNEL_IDS_SQL})`)
      .get(channelId) !== undefined
  );
}
