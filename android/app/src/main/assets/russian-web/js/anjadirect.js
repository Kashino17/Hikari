// Anja ohne Mac (Bauplan 18.11.2026): Ist der Mac nicht erreichbar, aber Internet da, läuft Anjas
// Gesprächssteuerung im Handy (vendor/anja-local.js, gebaut aus backend/src/russian/anja-browser.ts)
// und spricht direkt mit Google — mit dem Schlüssel, der verschlüsselt auf dem Gerät liegt.
// Die Oberfläche ruft dieselben Wege wie sonst; apiHook entscheidet, wer antwortet.
import { apiHook, anjaKeys, storage } from './platform.js';
import { linkNow, markNoServer, onLink } from './app.js';

let local = null;
async function engine() {
  if (!local) {
    const m = await import('../vendor/anja-local.js');
    local = m.createLocalAnja({
      key: () => anjaKeys.active(),
      store: { get: (k) => storage.get(k), set: (k, v) => storage.set(k, v) },
      log: (msg) => console.log(msg),
    });
  }
  return local;
}

const ID_IN_PATH = /^\/russian\/anja\/live\/(?:audio|turn)\/([^/?]+)/;
const idOf = (path, body) => path.match(ID_IN_PATH)?.[1] ?? (typeof body?.id === 'string' ? body.id : null);
const isAnja = (path) => path.startsWith('/russian/anja/') && !path.startsWith('/russian/anja/key');

/** Kann Anja gerade ohne Mac? (Internet da, Schlüssel auf dem Handy) */
export const directPossible = () => linkNow() !== 'offline' && !!anjaKeys.info()?.ready;

apiHook.route = (method, path, body) => {
  if (!isAnja(path)) return null;
  const id = idOf(path, body);
  // Ein Gespräch bleibt, wo es begonnen hat.
  if (id) return local?.owns(id) ? local.handle(method, path, body) : null;
  if (linkNow() === 'noserver' && directPossible()) return engine().then((e) => e.handle(method, path, body));
  return null;
};

apiHook.fallback = (method, path, body) => {
  if (!isAnja(path) || idOf(path, body) || !directPossible()) return null;
  markNoServer();
  return engine().then((e) => e.handle(method, path, body));
};

// Schlüssel vom Mac übernehmen, solange er erreichbar ist (er kann sich dort ändern).
let synced = false;
async function syncKey() {
  if (synced || linkNow() !== 'online') return;
  try {
    const r = await fetch('/api/russian/anja/key', { cache: 'no-store' });
    // Älterer Mac-Server ohne diesen Weg, oder Mac gerade weg: in zehn Minuten noch einmal.
    if (!r.ok) { setTimeout(syncKey, 10 * 60_000); return; }
    const { key } = await r.json();
    if (typeof key === 'string' && key.length > 10) { anjaKeys.save('mac', key); synced = true; }
  } catch { /* nächstes Mal */ }
}
onLink((v) => { if (v === 'online') syncKey(); });
setTimeout(syncKey, 1500);
