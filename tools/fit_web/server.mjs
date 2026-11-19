// Vorschau der Fitness-Oberfläche im Browser: liefert assets/fit-web aus und reicht /api/* an den
// Hikari-Server (oder scripts/fitness-dev.ts) durch.  node tools/fit_web/server.mjs [--api http://127.0.0.1:3940] [--port 3082]
import { createReadStream, existsSync, statSync } from 'node:fs';
import { createServer, request } from 'node:http';
import { extname, join, normalize } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = fileURLToPath(new URL('../../android/app/src/main/assets/fit-web', import.meta.url));
const arg = (k, d) => { const i = process.argv.indexOf(k); return i > 0 ? process.argv[i + 1] : d; };
const API = new URL(arg('--api', 'http://127.0.0.1:3940'));
const PORT = Number(arg('--port', '3082'));
const HOST = arg('--host', '127.0.0.1');
const TYPES = { '.html': 'text/html; charset=utf-8', '.js': 'text/javascript; charset=utf-8', '.css': 'text/css; charset=utf-8', '.woff2': 'font/woff2', '.svg': 'image/svg+xml', '.png': 'image/png', '.json': 'application/json', '.ogg': 'audio/ogg' };

createServer((req, res) => {
  const url = new URL(req.url, 'http://x');
  if (url.pathname.startsWith('/api/')) {
    const p = request({ hostname: API.hostname, port: API.port, path: url.pathname.slice(4) + url.search, method: req.method, headers: { ...req.headers, host: API.host } }, (r) => { res.writeHead(r.statusCode, r.headers); r.pipe(res); });
    p.on('error', () => { res.writeHead(502, { 'content-type': 'application/json' }); res.end('{"error":"backend_unreachable"}'); });
    req.pipe(p);
    return;
  }
  const file = normalize(join(root, url.pathname === '/' ? 'index.html' : url.pathname));
  if (!file.startsWith(root) || !existsSync(file) || statSync(file).isDirectory()) { res.writeHead(404); res.end(); return; }
  res.writeHead(200, { 'content-type': TYPES[extname(file)] ?? 'application/octet-stream', 'cache-control': 'no-store' });
  createReadStream(file).pipe(res);
}).listen(PORT, HOST, () => console.log(`Fitness-Vorschau: http://${HOST}:${PORT}/  (API ${API.href})`));
