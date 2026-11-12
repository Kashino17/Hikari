// Lernstand: Leitner-System, Serie, Sterne, Tagesprogramm, Podcast-Regeln.
// Portierung von RussianProgress.kt, RussianToday.kt, RussianPodcastPlayback.kt.
//
// WICHTIG: Das JSON-Format von `RuProgress` ist bewusst IDENTISCH zu dem, was
// die Android-App in SharedPreferences ("hikari_russian" → "progress_v1")
// speichert. So übernimmt die neue Oberfläche beim Live-Schalten den
// bestehenden Lernstand ohne Migration.

export const emptyProgress = () => ({
  gender: null, voice: null, xp: 0, streak: 0, bestStreak: 0, lastActiveDay: -1,
  dayStars: {}, podcastDayStars: {}, podcastListened: [], podcastLastCompletedDay: -1,
  courseLastCompletedDay: -1, cards: {}, xpByDay: {}, bestBlitz: 0, bestPairsSeconds: 0,
  bestReflex: 0, bestKombi: 0, spokenOk: 0, labBest: {}, favorites: [], patternsDone: [], mySentences: [],
});

/**
 * Karte in gültige Form bringen. Die App (Kotlin) lässt Standardwerte beim Speichern weg
 * (box 1, seen 0, wrong 0) — ohne diese Ergänzung ergab »richtig« box = NaN → null im JSON,
 * und die App konnte den ganzen Lernstand nicht mehr lesen (Verlust bis v0.89.104).
 */
const num = (x, fallback) => (typeof x === 'number' && Number.isFinite(x) ? x : fallback);
export const normalizeCard = (c, today = todayEpochDay()) => ({
  ...c,
  box: Math.min(Math.max(num(c?.box, 1), 1), MAX_BOX),
  due: num(c?.due, today),
  seen: num(c?.seen, 0),
  wrong: num(c?.wrong, 0),
});

export const normalizeProgress = (p) => {
  const out = { ...emptyProgress(), ...(p ?? {}) };
  const today = todayEpochDay();
  out.cards = Object.fromEntries(Object.entries(out.cards ?? {}).map(([id, c]) => [id, normalizeCard(c, today)]));
  return out;
};

/** Heutiger Kalendertag als epochDay (lokale Zeitzone, wie LocalDate.now().toEpochDay()). */
export function todayEpochDay(now = new Date()) {
  return Math.floor(Date.UTC(now.getFullYear(), now.getMonth(), now.getDate()) / 86400000);
}

export const settingsOf = (p) => ({ gender: p.gender ?? 'MALE', voice: p.voice ?? p.gender ?? 'MALE' });

const maxKey = (obj) => { const k = Object.keys(obj).map(Number); return k.length ? Math.max(...k) : 0; };
export const unlockedDay = (p, totalDays) => Math.min(maxKey(p.dayStars) + 1, totalDays);
export const unlockedPodcastDay = (p, totalDays) => Math.min(maxKey(p.podcastDayStars) + 1, totalDays);
export const xpOn = (p, epochDay) => p.xpByDay[epochDay] ?? 0;

// ── Leitner ────────────────────────────────────────────────────────────────

/** Abstand in Tagen je Box. Box 0 wird nie vergeben. */
export const INTERVALS = [0, 1, 2, 4, 7, 14, 30];
export const MAX_BOX = 6;

export const introduce = (existing, today) => existing ?? { box: 1, due: today + INTERVALS[1], seen: 0, wrong: 0 };

/** Richtig → eine Box höher. Falsch → zurück in Box 1 und noch heute erneut fällig. */
export function grade(raw, correct, today) {
  const card = normalizeCard(raw, today);
  if (correct) {
    const box = Math.min(card.box + 1, MAX_BOX);
    return { ...card, box, due: today + INTERVALS[box], seen: (card.seen ?? 0) + 1 };
  }
  return { ...card, box: 1, due: today, seen: (card.seen ?? 0) + 1, wrong: (card.wrong ?? 0) + 1 };
}

export function dueIds(cards, today) {
  return Object.entries(cards).filter(([, c]) => c.due <= today)
    .map(([id, c], i) => ({ id, c, i }))
    .sort((a, b) => a.c.due - b.c.due || a.c.box - b.c.box || a.i - b.i)
    .map((x) => x.id);
}

export function streakAfterActivity(p, today) {
  if (p.lastActiveDay === today) return [p.streak, today];
  if (p.lastActiveDay === today - 1) return [p.streak + 1, today];
  return [1, today];
}

/** Die Serie gilt als gerissen, wenn gestern und heute nichts gelernt wurde. */
export const visibleStreak = (p, today) => (p.lastActiveDay >= today - 1 ? p.streak : 0);

export function stars(correct, total) {
  if (total === 0) return 3;
  const rate = correct / total;
  return rate >= 0.9 ? 3 : rate >= 0.7 ? 2 : 1;
}

/** Karten ab Box 3 gelten als "sicher". */
export const knownCount = (cards) => Object.values(cards).filter((c) => c.box >= 3).length;

/**
 * Sitzungsergebnis einrechnen.
 * result: {xp, graded:{id:bool}, introduced:[id], completedDay?, completedPodcastDay?, stars, spokenOk}
 */
export function applyResult(p, result, today) {
  const cards = { ...p.cards };
  const introduced = new Set(result.introduced ?? []);
  for (const id of introduced) cards[id] = introduce(cards[id], today);
  for (const [id, ok] of Object.entries(result.graded ?? {})) {
    const c = cards[id] ?? introduce(null, today);
    cards[id] = introduced.has(id) ? (ok ? { ...c, seen: (c.seen ?? 0) + 1 } : grade(c, false, today)) : grade(c, ok, today);
  }
  const [streak, last] = streakAfterActivity(p, today);
  const dayStars = { ...p.dayStars };
  if (result.completedDay != null) dayStars[result.completedDay] = Math.max(result.stars ?? 0, dayStars[result.completedDay] ?? 0);
  const podcastDayStars = { ...p.podcastDayStars };
  if (result.completedPodcastDay != null) podcastDayStars[result.completedPodcastDay] = Math.max(result.stars ?? 0, podcastDayStars[result.completedPodcastDay] ?? 0);
  const xpByDay = {};
  for (const [d, v] of Object.entries({ ...p.xpByDay, [today]: xpOn(p, today) + (result.xp ?? 0) })) if (Number(d) > today - 30) xpByDay[d] = v;
  return {
    ...p,
    xp: p.xp + (result.xp ?? 0),
    streak,
    bestStreak: Math.max(p.bestStreak, streak),
    lastActiveDay: last,
    dayStars,
    podcastDayStars,
    podcastLastCompletedDay: result.completedPodcastDay != null ? today : p.podcastLastCompletedDay,
    courseLastCompletedDay: result.completedDay != null ? today : p.courseLastCompletedDay,
    cards,
    xpByDay,
    spokenOk: p.spokenOk + (result.spokenOk ?? 0),
  };
}

// ── Heute dran ─────────────────────────────────────────────────────────────

/** Tagesprogramm über alle Bereiche: Sprechkurs, Wiederholen, Podcast-Kurs. */
export function todayPlan(p, today, courseDays, podcastDays, podcastResumeDay = null) {
  const courseAll = Object.keys(p.dayStars).length >= courseDays;
  const courseToday = p.courseLastCompletedDay === today;
  const courseDay = courseToday ? (maxKey(p.dayStars) || 1) : unlockedDay(p, courseDays);
  const course = { kind: 'COURSE', done: courseToday || courseAll, day: courseDay, allDone: courseAll };

  const due = dueIds(p.cards, today).length;
  const review = { kind: 'REVIEW', done: due === 0, count: due };

  const podcastAll = Object.keys(p.podcastDayStars).length >= podcastDays;
  const podDay = podcastResumeDay ?? unlockedPodcastDay(p, podcastDays);
  const practice = p.podcastListened.includes(podDay);
  const podcast = { kind: 'PODCAST', done: p.podcastLastCompletedDay === today || podcastAll, day: podDay, practice, allDone: podcastAll };
  return [course, review, podcast];
}

// ── Podcast-Player ─────────────────────────────────────────────────────────

export const SPEEDS = [0.75, 0.9, 1, 1.25, 1.5];
const MIN_RESUME_MS = 5000;
const RESUME_REWIND_MS = 3000;
export const LISTENED_FRACTION = 0.9;

/** Startposition beim Öffnen: 0, wenn kaum angefangen oder (fast) zu Ende gehört. */
export function resumeStartMs(savedMs, durationMs) {
  if (savedMs < MIN_RESUME_MS) return 0;
  if (durationMs > 0 && savedMs >= durationMs - MIN_RESUME_MS) return 0;
  return Math.max(0, savedMs - RESUME_REWIND_MS);
}
export const isListened = (positionMs, durationMs) => durationMs > 0 && positionMs >= durationMs * LISTENED_FRACTION;
export function nextSpeed(current) {
  const i = SPEEDS.findIndex((s) => Math.abs(s - current) < 0.001);
  return i < 0 ? 1 : SPEEDS[(i + 1) % SPEEDS.length];
}
export function speedLabel(speed) {
  const s = Math.abs(speed * 10 - Math.round(speed * 10)) < 0.001 ? speed.toFixed(1) : speed.toFixed(2);
  return s.replace('.', ',') + '×';
}
export function formatTime(ms) {
  const total = Math.floor(Math.max(0, ms) / 1000);
  const h = Math.floor(total / 3600);
  const m = Math.floor((total % 3600) / 60);
  const s = total % 60;
  const p2 = (n) => String(n).padStart(2, '0');
  return h > 0 ? `${h}:${p2(m)}:${p2(s)}` : `${m}:${p2(s)}`;
}
