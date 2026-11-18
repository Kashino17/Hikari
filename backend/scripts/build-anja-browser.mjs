// Baut die Gesprächssteuerung von Anja für das Handy (Anja ohne Mac):
//   cd backend && node scripts/build-anja-browser.mjs
// → android/app/src/main/assets/russian-web/vendor/anja-local.js
import { build } from 'esbuild';
import { fileURLToPath } from 'node:url';

const here = (p) => fileURLToPath(new URL(p, import.meta.url));
await build({
  entryPoints: [here('../src/russian/anja-browser.ts')],
  // ANJA_LOCAL_OUT: für den Test, der prüft, ob das Bündel zum Code passt.
  outfile: process.env.ANJA_LOCAL_OUT ?? here('../../android/app/src/main/assets/russian-web/vendor/anja-local.js'),
  bundle: true,
  format: 'esm',
  platform: 'browser',
  target: 'chrome110',
  minify: true,
  legalComments: 'none',
  alias: {
    'node:fs': here('./anja-browser/fs.js'),
    'node:path': here('./anja-browser/path.js'),
    'node:crypto': here('./anja-browser/crypto.js'),
  },
  inject: [here('./anja-browser/inject.js')],
  banner: { js: '// Gebaut aus backend/src/russian/anja-browser.ts (scripts/build-anja-browser.mjs) — nicht von Hand ändern.' },
  // node_modules ist am Mac ein Symlink (node_modules.nosync, gegen iCloud). Ohne das hier
  // folgt esbuild ihm, hält die Pakete für eigenen Code und setzt "use strict" davor —
  // dann passt das Bündel nicht mehr zu dem, das die CI baut.
  preserveSymlinks: true,
  logLevel: 'warning',
});
if (!process.env.ANJA_LOCAL_OUT) console.log('anja-local.js gebaut');
