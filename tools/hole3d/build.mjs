// Baut das Spiel nach android/app/src/main/assets/hole3d (game.js + index.html; foods.glb kommt aus pack_assets.py).
import { copyFileSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { build } from 'esbuild';

const here = dirname(fileURLToPath(import.meta.url));
const out = join(here, '../../android/app/src/main/assets/hole3d');
mkdirSync(out, { recursive: true });

const result = await build({
  entryPoints: [join(here, 'src/main.js')],
  outfile: join(out, 'game.js'),
  bundle: true,
  format: 'iife',
  minify: true,
  target: ['chrome90'],
  legalComments: 'none',
  metafile: true,
});
copyFileSync(join(here, 'src/index.html'), join(out, 'index.html'));
const bytes = Object.values(result.metafile.outputs)[0].bytes;
console.log(`game.js ${(bytes / 1024).toFixed(0)} KB → ${out}`);
