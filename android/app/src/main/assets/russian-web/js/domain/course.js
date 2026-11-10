// Kursmodell + Übungs-Generator. Portierung von RussianModels.kt,
// RussianCourseRepository.kt (RuCourseIndex) und RussianExercises.kt.
import * as Ph from './phonetics.js';

// ── Phrasen auflösen ───────────────────────────────────────────────────────

function phrase(o) {
  return {
    note: null, lit: null, ...o,
    get display() { return Ph.display(this.ru); },
    get plain() { return Ph.plain(this.ru); },
    get wordCount() { return Ph.plain(this.ru).split(' ').filter((w) => /\p{L}/u.test(w)).length; },
  };
}
export const makePhrase = phrase;

/**
 * Audio-Namensschema: `<id>_m` / `<id>_f`. Items mit ruF haben die männliche
 * Stimme für ru und die weibliche für ruF; sonst entscheidet die Stimm-Einstellung.
 */
export function resolveItem(item, day, settings) {
  const female = settings.gender === 'FEMALE' && item.ruF != null;
  return phrase({
    id: item.id,
    ru: female ? item.ruF : item.ru,
    de: settings.gender === 'FEMALE' && item.deF != null ? item.deF : item.de,
    note: item.note ?? null,
    lit: item.lit ?? null,
    day,
    audio: item.ruF != null ? (female ? `${item.id}_f` : `${item.id}_m`) : settings.voice === 'FEMALE' ? `${item.id}_f` : `${item.id}_m`,
  });
}

/** Dialogzeilen: Partnerin hat ihre eigene Stimme ("p"). `<id>_<Textform>_<Stimme>`. */
export function resolveLine(line, day, settings) {
  const female = settings.gender === 'FEMALE';
  const textVariant = female && line.ruF != null ? 'f' : 'm';
  const isUser = line.who === 'U';
  const voice = !isUser ? 'p' : female ? 'f' : 'm';
  return phrase({
    id: line.id,
    ru: textVariant === 'f' ? line.ruF : line.ru,
    de: female && line.deF != null ? line.deF : line.de,
    note: line.gloss ?? null,
    day,
    audio: `${line.id}_${textVariant}_${voice}`,
  });
}

export class CourseIndex {
  constructor(course) {
    this.course = course;
    this.days = [...course.days].sort((a, b) => a.day - b.day);
    this.itemById = new Map();
    this.dayOfItem = new Map();
    for (const d of this.days) for (const it of d.items) { this.itemById.set(it.id, it); this.dayOfItem.set(it.id, d.day); }
  }
  day(n) { return this.days.find((d) => d.day === n) ?? null; }
  phrase(id, settings) {
    const item = this.itemById.get(id);
    return item ? resolveItem(item, this.dayOfItem.get(id), settings) : null;
  }
  dayPhrases(day, settings) { return day.items.map((it) => resolveItem(it, day.day, settings)); }
  dialog(day, settings) { return day.dialog.lines.map((l) => ({ line: l, isUser: l.who === 'U', phrase: resolveLine(l, day.day, settings) })); }
  phrasesUpTo(maxDay, settings) { return this.days.filter((d) => d.day <= maxDay).flatMap((d) => this.dayPhrases(d, settings)); }
}

// ── Zufall (seedbar, damit abgebrochene Lektionen identisch neu entstehen) ──

export function makeRng(seed) {
  let a = (seed >>> 0) || 0x9e3779b9;
  const next = () => {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
  return {
    next,
    int: (n) => Math.floor(next() * n),
    pick: (arr) => arr[Math.floor(next() * arr.length)],
    shuffled: (arr) => {
      const out = [...arr];
      for (let i = out.length - 1; i > 0; i--) {
        const j = Math.floor(next() * (i + 1));
        [out[i], out[j]] = [out[j], out[i]];
      }
      return out;
    },
  };
}
export const defaultRng = () => makeRng((Math.random() * 0xffffffff) >>> 0);

// ── Übungen ────────────────────────────────────────────────────────────────

const VOWELS = 'аеёиоуыэюя';

/** Reaktionen, die auf fast jede Frage passen — nie als "falsche" Antwort anbieten. */
export const UNIVERSAL_REACTIONS = new Set([
  'd01_da', 'd01_net', 'd01_spasibo', 'd01_pozhaluysta', 'd05_konechno', 'd05_ya_ne_znayu',
  'd05_ya_ne_ponimayu', 'd05_ya_ponimayu', 'd05_povtorite', 'd05_medlennee', 'd05_eshyo_raz',
  'd05_chto', 'd11_da_konechno', 'd11_mozhet_byt', 'd11_ne_mogu', 'd08_ya_tozhe', 'd02_mne_tozhe',
  'd08_klassno', 'd08_kruto', 'd12_pravda', 'd12_seryozno', 'd06_eto_interesno', 'd12_zdorovo',
  'd13_molodets', 'd12_ya_soglasen', 'd03_horosho',
]);

export function norm(s) {
  return Ph.plain(s).toLowerCase().replaceAll('ё', 'е').replace(/[^a-zäöüßа-я0-9 ]/g, ' ')
    .split(' ').filter(Boolean).join(' ');
}
export const normWord = (s) => norm(s).replaceAll(' ', '');

/** Wörter eines Kurstexts (mit "+"), ohne Satzzeichen. */
export function ruWords(ru) {
  return [...ru.matchAll(/[А-Яа-яЁё+][А-Яа-яЁё+\-]*/g)].map((m) => m[0].replace(/-+$/, ''));
}

/** Grobe Silbentrennung: → {syllables, stressed} */
export function syllables(stressedWord) {
  const out = [];
  let cur = '';
  let stressedSyl = -1;
  let pendingStress = false;
  for (const c of stressedWord) {
    if (c === '+') { pendingStress = true; continue; }
    const l = c.toLowerCase();
    if (l === 'й' && cur === '' && out.length) { out[out.length - 1] += c; continue; }
    cur += c;
    if (VOWELS.includes(l)) {
      if (pendingStress || l === 'ё') stressedSyl = out.length;
      pendingStress = false;
      out.push(cur);
      cur = '';
    }
  }
  if (cur && out.length) out[out.length - 1] += cur;
  return { syllables: out.map((s) => s.toLowerCase()), stressed: stressedSyl };
}

/** Zwei Bedeutungen wären als Antwortoptionen mehrdeutig, wenn eine die andere wortweise enthält. */
export function confusable(a, b) {
  const alts = (s) => s.replace(/\([^)]*\)/g, ' ').split('/')
    .map((alt) => new Set(norm(alt.replaceAll("'", ' ')).split(' ').filter(Boolean)))
    .filter((set) => set.size > 0);
  const aa = alts(a);
  const bb = alts(b);
  const containsAll = (x, y) => [...y].every((w) => x.has(w));
  return aa.some((x) => bb.some((y) => containsAll(x, y) || containsAll(y, x)));
}

/** Ablenker mit anderer Bedeutung UND anderem russischen Text. */
export function distractors(target, pool, rng, n = 3) {
  const sameDay = pool.filter((p) => p.day === target.day);
  const others = pool.filter((p) => p.day !== target.day);
  const chosen = [];
  const seenDe = new Set([norm(target.de)]);
  const seenRu = new Set([norm(target.plain)]);
  for (const cand of [...rng.shuffled(sameDay), ...rng.shuffled(others)]) {
    if (chosen.length === n) break;
    if (cand.id === target.id) continue;
    if ([...chosen, target].some((x) => confusable(x.de, cand.de))) continue;
    const de = norm(cand.de);
    if (seenDe.has(de)) continue;
    seenDe.add(de);
    const ru = norm(cand.plain);
    if (seenRu.has(ru)) continue;
    seenRu.add(ru);
    chosen.push(cand);
  }
  return chosen;
}

export const listenChoose = (p, pool, rng) => ({ type: 'listenChoose', phrase: p, options: rng.shuffled([...distractors(p, pool, rng), p]), gradedId: p.id });
export const meaningChoose = (p, pool, rng) => ({ type: 'meaningChoose', phrase: p, options: rng.shuffled([...distractors(p, pool, rng), p]), gradedId: p.id });

export function reply(index, question, item, pool, settings, rng) {
  const correct = item.replies.map((id) => index.phrase(id, settings)).filter(Boolean);
  if (!correct.length) return null;
  const right = rng.pick(correct);
  const replyIds = new Set(item.replies);
  const seen = new Set();
  const wrong = rng.shuffled(pool.filter((p) => !replyIds.has(p.id) && p.id !== question.id && p.day !== question.day &&
      !UNIVERSAL_REACTIONS.has(p.id) && !p.plain.trim().endsWith('?') && p.wordCount >= 2 && norm(p.plain) !== norm(right.plain))
    .filter((p) => { const k = norm(p.de); if (seen.has(k)) return false; seen.add(k); return true; })).slice(0, 3);
  if (wrong.length < 2) return null;
  return { type: 'reply', question, options: rng.shuffled([...wrong, right]), correctIds: replyIds, gradedId: question.id };
}

const PROPER_NOUNS = new Set(['кадир', 'аня', 'германии', 'германию', 'россии', 'москве', 'сергей', 'телеграм']);
function tile(word) {
  const d = Ph.display(word);
  return PROPER_NOUNS.has(Ph.plain(word).toLowerCase()) ? d : Ph.lowFirst(d);
}

export function build(p, pool, rng) {
  const answer = ruWords(p.ru).map(tile);
  const answerKeys = new Set(answer.map(normWord));
  const extra = [];
  const seen = new Set();
  outer: for (const other of rng.shuffled(pool.filter((x) => x.id !== p.id))) {
    for (const w of ruWords(other.ru)) {
      const t = tile(w);
      const k = normWord(t);
      if (answerKeys.has(k) || seen.has(k)) continue;
      seen.add(k);
      extra.push(t);
      if (extra.length === 2) break outer;
    }
  }
  return { type: 'build', phrase: p, answer, tiles: rng.shuffled([...answer, ...extra]), gradedId: p.id };
}

/** Längstes mehrsilbiges Wort des Satzes; seine Silben zum Antippen. */
export function stressTap(p) {
  const candidates = ruWords(p.ru).filter((w) => [...w].filter((c) => VOWELS.includes(c.toLowerCase())).length >= 2 && w.includes('+'));
  if (!candidates.length) return null;
  let word = candidates[0];
  for (const c of candidates) if (c.length > word.length) word = c;
  const s = syllables(word);
  if (s.syllables.length < 2 || s.stressed < 0) return null;
  return { type: 'stressTap', phrase: p, word: Ph.plain(word).toLowerCase(), syllables: s.syllables, stressedIndex: s.stressed, gradedId: p.id };
}

function practiceFor(index, p, pool, settings, speak, slot, rng) {
  const item = index.itemById.get(p.id);
  if (speak) return { type: 'speak', phrase: p, gradedId: p.id };
  if (item && item.replies?.length && slot % 2 === 0) {
    const r = reply(index, p, item, pool, settings, rng);
    if (r) return r;
  }
  if (p.wordCount >= 3) return build(p, pool, rng);
  const st = stressTap(p);
  if (st && slot % 2 === 1) return st;
  return slot % 2 === 0 ? listenChoose(p, pool, rng) : meaningChoose(p, pool, rng);
}
export const practiceExercise = practiceFor;

const chunked = (arr, n) => { const out = []; for (let i = 0; i < arr.length; i += n) out.push(arr.slice(i, i + n)); return out; };

/** Tages-Lektion: Aussprache-Tipp → neue Karten in Dreiergruppen → Übungsrunde → Rollenspiel. */
export function lesson(index, dayNumber, settings, rng = defaultRng()) {
  const day = index.day(dayNumber);
  if (!day) return [];
  const phrases = index.dayPhrases(day, settings);
  const pool = index.phrasesUpTo(dayNumber, settings);
  const out = [];
  out.push({ type: 'soundTip', day, examples: day.sound.examples.map((id) => index.phrase(id, settings)).filter(Boolean), gradedId: null });
  for (const group of chunked(phrases, 3)) {
    for (const p of group) out.push({ type: 'intro', phrase: p, gradedId: null });
    rng.shuffled(group).forEach((p, i) => out.push(i % 2 === 0 ? listenChoose(p, pool, rng) : meaningChoose(p, pool, rng)));
  }
  rng.shuffled(phrases).forEach((p, i) => {
    const speak = i % 2 === 0 || phrases.length <= 4;
    out.push(practiceFor(index, p, pool, settings, speak, i, rng));
  });
  if (dayNumber > 1) {
    rng.shuffled(pool.filter((p) => p.day < dayNumber)).slice(0, 3)
      .forEach((p, idx) => out.push(practiceFor(index, p, pool, settings, idx % 2 === 0, idx + 1, rng)));
  }
  out.push({ type: 'dialog', day, lines: index.dialog(day, settings), gradedId: null });
  return out;
}

/** Podcast: ohne Zusatzdialog, direkt mit den Phrasen der gehörten Folge. */
export function podcastLesson(index, dayNumber, settings, rng = defaultRng()) {
  const day = index.day(dayNumber);
  if (!day) return [];
  const phrases = index.dayPhrases(day, settings);
  const pool = index.phrasesUpTo(dayNumber, settings);
  const out = [];
  for (const group of chunked(phrases, 3)) {
    for (const p of group) out.push({ type: 'intro', phrase: p, gradedId: null });
    rng.shuffled(group).forEach((p, i) => out.push(i % 2 === 0 ? listenChoose(p, pool, rng) : meaningChoose(p, pool, rng)));
  }
  rng.shuffled(phrases).forEach((p, i) => out.push(practiceFor(index, p, pool, settings, i % 2 === 0, i, rng)));
  if (dayNumber > 1) {
    rng.shuffled(pool.filter((p) => p.day < dayNumber)).slice(0, 3)
      .forEach((p, i) => out.push(practiceFor(index, p, pool, settings, i % 2 === 0, i + 1, rng)));
  }
  return out;
}

/** Wiederholung fälliger Karten — eine Übung je Karte. `resolve(id)` liefert die Phrase (Kurs oder Podcast). */
export function review(index, dueIds, maxDay, settings, rng = defaultRng(), limit = 20, resolve = null) {
  const pool = index.phrasesUpTo(maxDay, settings);
  const get = resolve ?? ((id) => index.phrase(id, settings));
  return rng.shuffled(dueIds.slice(0, limit).map(get).filter(Boolean))
    .map((p, i) => practiceFor(index, p, pool, settings, i % 2 === 0, i + 1, rng));
}

export function buildCorrect(ex, chosen) {
  const a = chosen.map(normWord);
  const b = ex.answer.map(normWord);
  return a.length === b.length && a.every((w, i) => w === b[i]);
}
