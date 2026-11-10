// Der Lernstand als Steuerung: eine Stelle, die aus allem, was du tust, ableitet,
// wo du stehst — und woran die Übungen als Nächstes arbeiten sollen.
//
//   Sätze (Satzbuch/Karten)  → was sitzt, was wackelt, was fällig ist
//   Fähigkeiten (level.js)   → Niveau und Engpass
//   Lernzeit (timetrack.js)  → welcher Bereich diese Woche zu kurz kam
//
// Daraus kommen: der »nächste Schritt« (Heute, Fortschritt), die Vorschläge für
// kurze Übungen und die Gewichtung, mit der Spiele und Wiederholungen Sätze wählen.
import { D, P, Pl, progress, planState, openLesson, dayNo, nav, testSelection } from './app.js';
import { storage } from './platform.js';
import { timeTrack, MODES } from './timetrack.js';
import { measure, estimate } from './domain/level.js';

/** Niveau-Schätzung für den aktuellen Lernstand. */
export function assess(p = progress.get()) {
  return estimate(measure(p, storage.getJSON('conv_progress')));
}

/** Aktive Sekunden je Bereich in den letzten `days` Tagen. */
export function areaSeconds(days = 7) {
  const today = progress.today();
  const out = { speak: 0, listen: 0, review: 0, words: 0, amber: 0 };
  for (let i = 0; i < days; i++) for (const [k, s] of Object.entries(timeTrack.day(today - i))) { const a = MODES[k]?.[1]; if (a) out[a] += s; }
  return out;
}
const DIM_AREA = { vocab: 'review', speak: 'speak', listen: 'listen', grammar: 'words', talk: 'amber' };

/** Themen mit Lernstand: {id, title, ids, sits, seen, share}. */
export function themeStats(p = progress.get()) {
  return D.themes.map((t) => {
    const ids = Object.entries(D.themeOf).filter(([, ts]) => ts.includes(t.id)).map(([id]) => id);
    const sits = ids.filter((id) => (p.cards[id]?.box ?? 0) >= 3).length;
    const seen = ids.filter((id) => p.cards[id]).length;
    return { ...t, ids, sits, seen, share: ids.length ? sits / ids.length : 0 };
  });
}
/** Angefangene Themen, die noch nicht sitzen — die schwächsten zuerst. */
export function weakThemes(p = progress.get(), n = 3) {
  return themeStats(p).filter((t) => t.seen > 0 && t.share < 0.85).sort((a, b) => a.share - b.share).slice(0, n);
}

/**
 * Der nächste sinnvolle Schritt: die Fähigkeit, die dich am meisten vom nächsten
 * Niveau trennt (bei Gleichstand die, für die du diese Woche am wenigsten Zeit hattest).
 * → { est, dim, title, text, label, go() } oder null (kein nächstes Niveau messbar).
 */
export function focus(p = progress.get()) {
  const est = assess(p);
  if (!est.next || !est.bottleneck) return null;
  const secs = areaSeconds(7);
  const open = est.dims.filter((d) => !d.done);
  const min = Math.min(...open.map((d) => d.frac));
  const dim = open.filter((d) => d.frac - min < 0.05).sort((a, b) => secs[DIM_AREA[a.id]] - secs[DIM_AREA[b.id]])[0];
  const n = dim.missing;
  const unit = dim.unit[n === 1 ? 0 : 1];
  const base = { est, dim, title: `Engpass: ${dim.name}`, text: `Noch ${n} ${unit} bis ${est.next.id}` };
  const today = progress.today();
  if (dim.id === 'vocab') {
    const due = P.dueIds(p.cards, today).length;
    if (due > 0) return { ...base, label: `${due} fällige wiederholen`, go: () => nav('review') };
    const weak = weakThemes(p, 1)[0];
    if (weak) return { ...base, label: `Thema »${weak.title}« testen`, go: () => { testSelection.ids = weak.ids; nav('test'); } };
    const cur = D.plan.currentDay(p, planState.get());
    if (cur != null && Pl.isTalkDay(cur)) return { ...base, label: `Tag ${cur}: Gesprächstag`, go: () => nav(`talkday/${cur}`) };
    const lesson = openLesson(p);
    return { ...base, label: `Tag ${dayNo(lesson)} lernen`, go: () => nav(`lesson/${lesson}`) };
  }
  if (dim.id === 'speak') return { ...base, label: 'Sprech-Reflex starten', go: () => nav('reflex') };
  if (dim.id === 'listen') {
    const day = P.unlockedPodcastDay(p, D.podIndex.days.length);
    const practice = p.podcastListened.includes(day);
    return { ...base, label: practice ? `Übungen zu Lektion ${day}` : `Lektion ${day} hören`, go: () => nav(practice ? `podcastPractice/${day}` : `podcast/${day}`) };
  }
  if (dim.id === 'grammar') {
    const pat = D.patterns.find((x) => !p.patternsDone.includes(x.id));
    return { ...base, label: pat ? 'Nächstes Muster lernen' : 'Muster wiederholen', go: () => nav(pat ? `pattern/${pat.id}` : 'patterns') };
  }
  return { ...base, label: 'Mit Anja sprechen', go: () => nav('anja') };
}

/** Zwei kurze Übungen für »Fünf Minuten Zeit?«: eine für den Engpass, eine für den Bereich mit der wenigsten Zeit. */
export function quickPicks(p = progress.get()) {
  const today = progress.today();
  const byDim = { vocab: ['pairs', 'blitz'], speak: ['reflex', 'talk', 'blind'], listen: ['blitz', 'sounds'], grammar: ['kombi'], talk: ['talk', 'reflex'] };
  const byArea = { speak: ['reflex', 'talk', 'blind'], listen: ['blitz', 'sounds'], review: ['pairs'], words: ['kombi', 'pairs'], amber: ['talk'] };
  const f = focus(p);
  const firstList = byDim[f?.dim.id ?? 'speak'];
  const first = firstList[today % firstList.length];
  const secs = areaSeconds(7);
  const areas = Object.keys(byArea).sort((a, b) => secs[a] - secs[b]);
  for (const a of areas) {
    const second = byArea[a].find((x) => x !== first && !firstList.includes(x));
    if (second) return [first, second];
  }
  return [first, first === 'blitz' ? 'reflex' : 'blitz'];
}

/**
 * Gewicht eines Satzes für die Auswahl in Spielen und Wiederholungen:
 * fällig > wackelig > aktiv > neu > sitzt; Sätze aus schwachen Themen und mit
 * mehreren Fehlern etwas höher.
 */
export function weightFn(p = progress.get()) {
  const today = progress.today();
  const weak = new Set(weakThemes(p).flatMap((t) => t.ids));
  return (id) => {
    const c = p.cards[id];
    let w = !c ? 1.5 : c.due <= today ? 4 : c.box <= 2 ? 3 : c.box === 3 ? 2 : 1;
    if (weak.has(id) && (!c || c.box < 3)) w += 1;
    if (c && c.wrong >= 2) w += 0.5;
    return w;
  };
}
/** Zufällige Reihenfolge, in der schwere Sätze mit höherer Wahrscheinlichkeit vorn stehen. */
export function weightedOrder(items, rng, idOf = (x) => x.id) {
  const w = weightFn();
  return items.map((x) => [x, rng.next() ** (1 / w(idOf(x)))]).sort((a, b) => b[1] - a[1]).map((x) => x[0]);
}

// ── Auswahl für Kurs-Lektionen und Gesprächstage ───────────────────────────

/**
 * Wiederholungen für die Etappe »Üben« einer Lektion — aus dem Lernstand, nicht zufällig:
 * erst, was wackelt oder fällig ist, dann bewusst auch Sätze, die schon sitzen
 * (die am längsten nicht mehr dran waren). Wiederholung macht den Meister.
 */
export function lessonReviewIds(lessonDay, rng, n = 5, p = progress.get()) {
  const pool = D.index.phrasesUpTo(lessonDay - 1, progress.settings());
  if (!pool.length) return [];
  const today = progress.today();
  const card = (x) => p.cards[x.id];
  const seen = pool.filter(card);
  if (seen.length < n) return rng.shuffled(pool).slice(0, Math.min(3, pool.length)).map((x) => x.id);
  const shaky = weightedOrder(seen.filter((x) => card(x).box <= 2 || card(x).due <= today), rng).slice(0, Math.ceil(n / 2));
  // "zuletzt geübt" = fällig minus Abstand der Box: je kleiner, desto länger her
  const last = (x) => card(x).due - P.INTERVALS[card(x).box];
  const solid = rng.shuffled(seen.filter((x) => card(x).box >= 3 && !shaky.includes(x)).sort((a, b) => last(a) - last(b)).slice(0, 8)).slice(0, n - shaky.length);
  const picked = [...shaky, ...solid];
  const rest = weightedOrder(seen.filter((x) => !picked.includes(x)), rng);
  return [...picked, ...rest].slice(0, n).map((x) => x.id);
}

/**
 * Stoff eines Gesprächstags aus den vier Lektionen des Blocks:
 * acht Sätze zum Aufwärmen (wackelige häufiger), zwei Rollenspiele (die mit den wenigsten Sternen)
 * und bis zu fünf Fragen, auf die frei geantwortet wird.
 */
export function talkDaySelection(planDay, rng, p = progress.get()) {
  const s = progress.settings();
  const lessons = (D.plan.day(planDay)?.lessons ?? []).filter((L) => D.index.day(L));
  const phrases = lessons.flatMap((L) => D.index.dayPhrases(D.index.day(L), s));
  const warmIds = weightedOrder(phrases.filter((x) => x.wordCount >= 2), rng).slice(0, 8).map((x) => x.id);
  const dialogDays = rng.shuffled(lessons).sort((a, b) => (p.dayStars[a] ?? 0) - (p.dayStars[b] ?? 0)).slice(0, 2).sort((a, b) => a - b);
  const questions = phrases.filter((x) => (D.index.itemById.get(x.id)?.replies ?? []).length > 0);
  const questionIds = weightedOrder(questions, rng).slice(0, 5).map((x) => x.id);
  return { warmIds, dialogDays, questionIds };
}
