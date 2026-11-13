// Hikari · Russisch — Vorschau-Server für die Web-Oberfläche des Russisch-Bereichs.
//
// Die Oberfläche selbst liegt in android/app/src/main/assets/russian-web und wird in
// der App von einer WebView aus den Assets geladen (ui/russian/RussianWebScreen.kt).
// Dieser Server liefert dieselben Dateien an einen Browser aus — zum Entwerfen und
// Ansehen am Rechner oder per Tailscale am Handy, ohne die App neu zu bauen:
//
//   /                → die Oberfläche (assets/russian-web)
//   /data/<name>.json → Kursinhalte (assets/russian)
//   /audio/<clip>.ogg → Muttersprachler-Clips (assets/russian/audio)
//   /api/*           → Hikari-Server (Aussprache-Prüfung, Gespräch, Podcast-Audio …)
//   /store           → Lernstand der Vorschau (~/.hikari/russian-web/store.json)
//   /media/<datei>   → Videos und Standbilder für »Lieder & Szenen« (~/.hikari/erleben/media)
//   /erleben/feed    → Nachschub: Stücke, die tools/erleben/prepare.py selbst aufbereitet hat
//   /erleben/refresh → neue Stücke in den Feed holen (POST {keep:[ids]})
//
// Start: node tools/russian_web/server.mjs   (Port 3079)
// Tests: node --test tools/russian_web/test/  (braucht test/parity.json, siehe dort)
import { createServer } from 'node:http';
import { spawn } from 'node:child_process';
import { createReadStream, existsSync, mkdirSync, readFileSync, readdirSync, renameSync, statSync, writeFileSync } from 'node:fs';
import { readFile } from 'node:fs/promises';
import { homedir } from 'node:os';
import { extname, join, normalize } from 'node:path';
import { Readable } from 'node:stream';
import { gzipSync } from 'node:zlib';

const PORT = Number(process.env.PORT ?? 3079);
const BACKEND = process.env.HIKARI_BACKEND ?? 'http://127.0.0.1:3939';
const HERE = new URL('.', import.meta.url).pathname;
const ROOT = new URL('../../android/app/src/main/assets/russian-web/', import.meta.url).pathname;
const ASSETS = process.env.HIKARI_ASSETS ?? new URL('../../android/app/src/main/assets/russian', import.meta.url).pathname;
// Clips dürfen aus einem anderen Klon kommen (schlanker Arbeitsbaum ohne die 9.500 Audiodateien).
const AUDIO_ASSETS = process.env.HIKARI_AUDIO_ASSETS ?? ASSETS;
const MEDIA = process.env.HIKARI_ERLEBEN_MEDIA ?? join(homedir(), '.hikari', 'erleben', 'media');
const STORE_DIR = process.env.HIKARI_STORE_DIR ?? join(homedir(), '.hikari', 'russian-web');
const STORE_FILE = join(STORE_DIR, 'store.json');

const TYPES = {
  '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.mjs': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8', '.json': 'application/json; charset=utf-8', '.svg': 'image/svg+xml',
  '.ogg': 'audio/ogg', '.woff2': 'font/woff2', '.png': 'image/png', '.jpg': 'image/jpeg', '.mp4': 'video/mp4', '.webmanifest': 'application/manifest+json',
};
const DATA = new Set(['course', 'podcast', 'phrasebook', 'deepdive', 'patterns', 'sounds', 'glosses', 'plan', 'erleben']);

// ── Nachschub für »Lieder & Szenen« ─────────────────────────────────────────
// prepare.py legt fertige Stücke in AUTO ab. Der Feed zeigt immer FEED_SIZE davon; »Neue Stücke«
// tauscht sie gegen die nächsten aus dem Vorrat. Sinkt der Vorrat, bereitet der Server im
// Hintergrund neue auf — so steht beim nächsten Tippen schon etwas bereit.
const ERLEBEN = join(homedir(), '.hikari', 'erleben');
const AUTO = join(ERLEBEN, 'auto');
const FEED_FILE = join(ERLEBEN, 'feed.json');
const PREPARE = new URL('../erleben/prepare.py', import.meta.url).pathname;
const UV = ['/opt/homebrew/bin/uv', '/usr/local/bin/uv'].find((f) => existsSync(f)) ?? 'uv';
const FEED_SIZE = 3;
const STOCK = 3;
mkdirSync(AUTO, { recursive: true });
let feed = { shown: [], dismissed: [] };
try { feed = { ...feed, ...JSON.parse(readFileSync(FEED_FILE, 'utf8')) }; } catch { /* erster Start */ }
let prepareJob = null;
const saveFeed = () => writeFileSync(FEED_FILE, JSON.stringify(feed));
function autoPieces() {
  return readdirSync(AUTO).filter((f) => f.endsWith('.json'))
    .map((f) => ({ f, at: statSync(join(AUTO, f)).mtimeMs })).sort((a, b) => a.at - b.at)
    .flatMap(({ f }) => { try { return [JSON.parse(readFileSync(join(AUTO, f), 'utf8'))]; } catch { return []; } });
}
function topUp(stock) {
  if (prepareJob || stock >= STOCK) return;
  prepareJob = spawn(UV, ['run', '--script', PREPARE, 'next', '--count', String(STOCK + 1 - stock)], { stdio: ['ignore', 'ignore', 'pipe'], env: { ...process.env, PATH: `/opt/homebrew/bin:${process.env.PATH ?? ''}` } });
  prepareJob.stderr.on('data', (d) => { for (const l of String(d).split('\n')) if (l.startsWith('[')) console.log(new Date().toISOString(), 'erleben', l); });
  prepareJob.on('exit', () => { prepareJob = null; });
  prepareJob.on('error', (e) => { console.error('erleben: Aufbereitung startet nicht', e.message); prepareJob = null; });
}
function feedState(keep = null) {
  const all = autoPieces();
  const ids = new Set(all.map((p) => p.id));
  if (keep) {
    feed.dismissed = [...new Set([...feed.dismissed, ...feed.shown.filter((id) => !keep.includes(id))])];
    feed.shown = feed.shown.filter((id) => keep.includes(id));
  }
  feed.shown = feed.shown.filter((id) => ids.has(id));
  const ready = () => all.filter((p) => !feed.shown.includes(p.id) && !feed.dismissed.includes(p.id));
  for (const p of ready()) { if (feed.shown.length >= FEED_SIZE) break; feed.shown.push(p.id); }
  saveFeed();
  const stock = ready().length;
  topUp(stock);
  // Auch Abgelegtes bleibt abrufbar, solange es jemand offen hat.
  return { shown: feed.shown, ready: stock, busy: !!prepareJob, pieces: all };
}

// ── Lernstand ──────────────────────────────────────────────────────────────
mkdirSync(STORE_DIR, { recursive: true });
let store = { rev: 0, values: {} };
try { store = JSON.parse(readFileSync(STORE_FILE, 'utf8')); } catch { /* erster Start */ }
function saveStore() {
  store.rev++;
  const tmp = `${STORE_FILE}.tmp`;
  writeFileSync(tmp, JSON.stringify(store));
  renameSync(tmp, STORE_FILE);
}
const readBody = (req, limit = 4 * 1024 * 1024) => new Promise((resolve, reject) => {
  const chunks = [];
  let size = 0;
  req.on('data', (c) => { size += c.length; if (size > limit) { reject(new Error('too large')); req.destroy(); } else chunks.push(c); });
  req.on('end', () => resolve(Buffer.concat(chunks)));
  req.on('error', reject);
});

function send(req, res, status, body, type, extra = {}) {
  const headers = { 'content-type': type, ...extra };
  let out = typeof body === 'string' ? Buffer.from(body) : body;
  if (out.length > 1024 && /gzip/.test(req.headers['accept-encoding'] ?? '') && /json|javascript|css|html|svg/.test(type)) {
    out = gzipSync(out);
    headers['content-encoding'] = 'gzip';
  }
  headers['content-length'] = out.length;
  res.writeHead(status, headers);
  res.end(req.method === 'HEAD' ? undefined : out);
}
const json = (req, res, status, obj) => send(req, res, status, JSON.stringify(obj), TYPES['.json'], { 'cache-control': 'no-store' });

/** Datei mit Range-Unterstützung (Safari verlangt sie für Audio). */
function sendFile(req, res, file, type, cache) {
  let st;
  try { st = statSync(file); } catch { res.writeHead(404).end('not found'); return; }
  const headers = { 'content-type': type, 'accept-ranges': 'bytes', 'cache-control': cache };
  const m = /^bytes=(\d*)-(\d*)$/.exec(req.headers.range ?? '');
  if (m && (m[1] || m[2])) {
    const start = m[1] ? Number(m[1]) : Math.max(0, st.size - Number(m[2]));
    const end = m[1] && m[2] ? Math.min(Number(m[2]), st.size - 1) : st.size - 1;
    if (start > end || start >= st.size) { res.writeHead(416, { 'content-range': `bytes */${st.size}` }).end(); return; }
    res.writeHead(206, { ...headers, 'content-range': `bytes ${start}-${end}/${st.size}`, 'content-length': end - start + 1 });
    createReadStream(file, { start, end }).pipe(res);
    return;
  }
  res.writeHead(200, { ...headers, 'content-length': st.size });
  if (req.method === 'HEAD') res.end(); else createReadStream(file).pipe(res);
}

/** Reicht eine Anfrage an den Hikari-Server durch (Streaming, Range, Multipart). */
async function proxy(req, res, path) {
  const headers = {};
  for (const h of ['range', 'content-type', 'accept', 'authorization']) if (req.headers[h]) headers[h] = req.headers[h];
  const ctrl = new AbortController();
  res.on('close', () => ctrl.abort());
  try {
    const hasBody = !['GET', 'HEAD'].includes(req.method);
    const up = await fetch(BACKEND + path, {
      method: req.method, headers, signal: ctrl.signal, redirect: 'follow',
      body: hasBody ? await readBody(req, 12 * 1024 * 1024) : undefined,
    });
    const out = {};
    for (const h of ['content-type', 'content-length', 'content-range', 'accept-ranges', 'cache-control']) {
      const v = up.headers.get(h);
      if (v) out[h] = v;
    }
    // fetch() hat komprimierte Antworten bereits entpackt — Länge stimmt dann nicht mehr.
    if (up.headers.get('content-encoding')) delete out['content-length'];
    res.writeHead(up.status, out);
    if (!up.body || req.method === 'HEAD') { res.end(); return; }
    Readable.fromWeb(up.body).on('error', () => res.destroy()).pipe(res);
  } catch (e) {
    if (ctrl.signal.aborted) return;
    if (!res.headersSent) json(req, res, 502, { error: 'backend_unreachable', detail: String(e?.message ?? e) });
    else res.destroy();
  }
}

createServer(async (req, res) => {
  try {
    const url = new URL(req.url, 'http://x');
    const path = decodeURIComponent(url.pathname);

    if (path.startsWith('/api/')) return proxy(req, res, path.slice(4) + url.search);

    if (path === '/erleben/feed' && req.method === 'GET') return json(req, res, 200, feedState());
    if (path === '/erleben/refresh' && req.method === 'POST') {
      const body = JSON.parse((await readBody(req)).toString('utf8') || '{}');
      return json(req, res, 200, feedState(Array.isArray(body.keep) ? body.keep.map(String) : []));
    }

    if (path === '/store') {
      if (req.method === 'GET') return json(req, res, 200, store);
      if (req.method === 'POST') {
        // {set:{k:v}, remove:[k]} — ein Aufruf für mehrere Änderungen
        const patch = JSON.parse((await readBody(req)).toString('utf8') || '{}');
        for (const [k, v] of Object.entries(patch.set ?? {})) store.values[k] = v;
        for (const k of patch.remove ?? []) delete store.values[k];
        saveStore();
        return json(req, res, 200, { rev: store.rev });
      }
      return json(req, res, 405, { error: 'method' });
    }

    let m = /^\/data\/([a-z]+)\.json$/.exec(path);
    if (m && DATA.has(m[1])) {
      const body = await readFile(join(ASSETS, `${m[1]}.json`));
      return send(req, res, 200, body, TYPES['.json'], { 'cache-control': 'no-cache' });
    }
    if (path === '/clips.json') {
      // Welche Clips es gibt — die App fragt nie ins Leere und weiß, wann sie vorlesen lassen muss.
      const names = readdirSync(join(AUDIO_ASSETS, 'audio')).filter((f) => f.endsWith('.ogg')).map((f) => f.slice(0, -4));
      return send(req, res, 200, JSON.stringify(names), TYPES['.json'], { 'cache-control': 'no-cache' });
    }
    m = /^\/audio\/([a-z0-9_]{1,120})\.ogg$/.exec(path);
    if (m) return sendFile(req, res, join(AUDIO_ASSETS, 'audio', `${m[1]}.ogg`), TYPES['.ogg'], 'public, max-age=604800');
    // Ansagen des Blind-Coachs in Rems Stimme (gen_coach_rem.py).
    if (path === '/data/coach/index.json') {
      const f = join(AUDIO_ASSETS, 'coach', 'index.json');
      return send(req, res, 200, existsSync(f) ? await readFile(f) : '{}', TYPES['.json'], { 'cache-control': 'no-cache' });
    }
    m = /^\/coach\/([a-f0-9]{12})\.ogg$/.exec(path);
    if (m) return sendFile(req, res, join(AUDIO_ASSETS, 'coach', `${m[1]}.ogg`), TYPES['.ogg'], 'public, max-age=604800');

    m = /^\/media\/([a-z0-9_]{1,60})\.(mp4|jpg)$/.exec(path);
    if (m) return sendFile(req, res, join(MEDIA, `${m[1]}.${m[2]}`), TYPES[`.${m[2]}`], 'public, max-age=3600');

    if (path === '/healthz') return json(req, res, 200, { ok: true, assets: existsSync(join(ASSETS, 'course.json')) });

    if (path === '/_grid.html') return send(req, res, 200, await readFile(join(HERE, '_grid.html')), TYPES['.html'], { 'cache-control': 'no-store' });
    const rel = normalize(path === '/' ? '/index.html' : path);
    if (rel.includes('..')) { res.writeHead(400).end(); return; }
    const file = join(ROOT, rel);
    const type = TYPES[extname(file)] ?? 'application/octet-stream';
    if (/font|image/.test(type)) return sendFile(req, res, file, type, 'public, max-age=604800');
    const body = await readFile(file);
    return send(req, res, 200, body, type, { 'cache-control': 'no-store' });
  } catch (e) {
    if (e?.code === 'ENOENT') { res.writeHead(404).end('not found'); return; }
    console.error(new Date().toISOString(), req.method, req.url, e);
    if (!res.headersSent) res.writeHead(500).end('error'); else res.destroy();
  }
}).listen(PORT, '127.0.0.1', () => console.log(`Hikari Russisch (Web) auf :${PORT} — Assets: ${ASSETS}`));
