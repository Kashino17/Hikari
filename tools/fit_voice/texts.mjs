// Alle Ansagen des Trainings-Players für Rems Stimme. Schlüssel (de) = was die App anfragt,
// spoken = was Rem sagt (Zahlen ausgeschrieben, Klammern aufgelöst, Schlusspunkt gegen Weiterreden).
//   node tools/fit_voice/texts.mjs > /tmp/fit_texts.json
import { readFileSync } from 'node:fs';

const ex = JSON.parse(readFileSync(new URL('../../backend/fitness/exercises.json', import.meta.url), 'utf8')).exercises;
const ONES = ['', 'ein', 'zwei', 'drei', 'vier', 'fünf', 'sechs', 'sieben', 'acht', 'neun', 'zehn', 'elf', 'zwölf', 'dreizehn', 'vierzehn', 'fünfzehn', 'sechzehn', 'siebzehn', 'achtzehn', 'neunzehn'];
const TENS = ['', '', 'zwanzig', 'dreißig', 'vierzig', 'fünfzig', 'sechzig', 'siebzig', 'achtzig', 'neunzig'];
export function zahl(n) {
  if (n < 20) return n === 1 ? 'eins' : ONES[n];
  if (n < 100) return `${n % 10 ? `${ONES[n % 10]}und` : ''}${TENS[Math.floor(n / 10)]}`;
  return `hundert${n % 100 ? zahl(n % 100) : ''}`;
}
const cap = (s) => s[0].toUpperCase() + s.slice(1);
const out = [];
const add = (de, spoken = de) => out.push({ de, spoken: /[.!?]$/.test(spoken) ? spoken : `${spoken}.` });

// Englische Namen in Lautschrift — so, wie man sie im Training ausspricht (Rem liest sonst deutsch).
const PHON = {
  archer_pullup: 'Artscher-Klimmzug', archer_pushup: 'Artscher-Liegestütz', muscle_up: 'Massel-App', curtsy_lunge: 'Körtsi Lantsch',
  dead_bug: 'Dedd Back', bird_dog: 'Börd Dogg', hollow_rock: 'Hollo Rocks', hollow_hold: 'Hollo Hoold', v_up: 'Wie-App',
  arch_hold: 'Aatsch Hoold', burpee: 'Börpi', half_burpee: 'Börpi ohne Sprung', sprawl: 'Sprool', inchworm: 'Intsch-Wörm',
  planche_lean: 'Plansch Lien', german_hang: 'Dschörmen Häng', worlds_greatest: 'Wörlds Greitest Stretsch',
  skin_the_cat: 'Skinn se Kätt', hip_thrust: 'Hipp Thrast an der Bank', step_up: 'Stepp-App', kb_swing: 'Kettelbell Swing',
  jump_rope: 'Seilspringen', fast_feet: 'Fast Fiet', tuck_lsit: 'L-Sitz gehockt', toes_to_bar: 'Tous tu Baar',
};
for (const e of ex) add(`ex:${e.id}`, PHON[e.id] ?? e.name.replace(/\s*\(([^)]*)\)/g, ', $1').replace('’', "'"));
for (let n = 1; n <= 60; n++) add(`${n} Wdh`, `${cap(n === 1 ? 'eine' : zahl(n))} ${n === 1 ? 'Wiederholung' : 'Wiederholungen'}`);
for (let s = 5; s <= 180; s += 5) {
  const m = Math.floor(s / 60), r = s % 60;
  const min = m === 0 ? '' : m === 1 ? 'Eine Minute' : `${cap(zahl(m))} Minuten`;
  const spoken = s < 60 ? `${cap(zahl(s))} Sekunden` : r === 0 ? min : `${min} und ${zahl(r)} Sekunden`;
  add(`${s} s`, spoken);
}
const PHRASES = {
  'je Seite': 'Je Seite.',
  'Seite wechseln': 'Seite wechseln!',
  'Los gehts': "Los geht's!",
  'Pause': 'Kurze Pause.',
  'Rundenpause': 'Rundenpause. Atme durch.',
  'Gleich': 'Als Nächstes.',
  'Letzte Runde': 'Letzte Runde!',
  'Runde 2': 'Runde zwei.',
  'Runde 3': 'Runde drei.',
  'Runde 4': 'Runde vier.',
  'Runde 5': 'Runde fünf.',
  'Halbzeit': 'Halbzeit, du schaffst das!',
  'Noch zehn': 'Noch zehn Sekunden.',
  'Block warmup': 'Wir starten mit dem Aufwärmen.',
  'Block skill': 'Als Nächstes: dein Skill-Block.',
  'Block main': 'Jetzt kommt der Hauptteil.',
  'Block finisher': 'Zum Schluss der Finisher. Gib alles!',
  'Block cooldown': 'Geschafft. Jetzt locker abkühlen und dehnen.',
  'Fertig': 'Training geschafft! Das war richtig stark, Kadir-kun.',
  'Ziel erreicht': 'Ziel erreicht!',
  'Neue Stufe': 'Neue Stufe freigeschaltet! Ich bin stolz auf dich.',
  'Weiter so': 'Super, weiter so!',
};
for (const [de, spoken] of Object.entries(PHRASES)) add(de, spoken);
console.log(JSON.stringify(out, null, 1));
