// Buffer und process gibt es im Browser nicht — die Gesprächssteuerung braucht Buffer für Ton (Base64).
import { Buffer } from 'buffer';
export { Buffer };
export const process = globalThis.process ?? { env: {} };
