// Alle Sätze der Achtsamkeit für Rems Stimme: Schlüssel »t:<Satz>« (so fragt die App an), gesprochen
// wird der Satz selbst. Quelle: backend/fitness/mind/*.json.
//   node tools/fit_voice/mind_texts.mjs > /tmp/mind_texts.json
import { existsSync, readFileSync } from 'node:fs';

const dir = new URL('../../backend/fitness/mind/', import.meta.url);
const read = (f, fb) => (existsSync(new URL(f, dir)) ? JSON.parse(readFileSync(new URL(f, dir), 'utf8')) : fb);
const asanas = read('asanas.json', []);
const levels = read('yoga-levels.json', []);
const content = read('content.json', {});

const out = new Map();
const add = (t, spoken = t) => {
  const text = String(t ?? '').trim();
  const say = String(spoken ?? '').trim();
  if (!text) return;
  out.set(`t:${text}`, /[.!?…]$/.test(say) ? say : `${say}.`);
};
// Haltungsnamen: kurze Einzelwörter spricht das Modell unzuverlässig → mit Artikel; Ziffern aus.
const SAY = {
  'Kuh': 'Die Kuh', 'Katze': 'Die Katze', 'Dreieck': 'Das Dreieck', 'Brett': 'Das Brett', 'Boot': 'Das Boot', 'Krähe': 'Die Krähe',
  'Bogen': 'Der Bogen', 'Pflug': 'Der Pflug', 'Rad': 'Das Rad', 'Baum': 'Der Baum', 'Stuhl': 'Der Stuhl', 'Adler': 'Der Adler',
  'Taube': 'Die Taube', 'Kamel': 'Das Kamel', 'Lotus': 'Der Lotus', 'Delfin': 'Der Delfin', 'Sphinx': 'Die Sphinx',
  'Krieger I': 'Krieger eins', 'Krieger II': 'Krieger zwei', 'Krieger III': 'Krieger drei', 'Wild Thing': 'Weild Sing',
  'Spagat': 'Der Spagat', 'Girlande': 'Die Girlande', 'Halbmond': 'Der Halbmond', 'Pyramide': 'Die Pyramide',
};
for (const a of asanas) add(a.de, SAY[a.de] ?? a.de);
for (const l of levels) for (const f of l.flow ?? []) add(f.sage);
for (const a of content.atem ?? []) for (const p of a.phasen ?? []) add(p.sage);
for (const a of content.affirmationen ?? []) add(a.text);
for (const g of content.gefuehrt ?? []) for (const s of g.schritte ?? []) add(s.sage);

console.log(JSON.stringify([...out].map(([de, spoken]) => ({ de, spoken })), null, 1));
