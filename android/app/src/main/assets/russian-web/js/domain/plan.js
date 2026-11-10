// Jahresplan des Sprechkurses: 365 Plan-Tage in Blöcken zu fünf Tagen
// (vier Lektionen, dann ein Gesprächstag ohne neuen Stoff), Etappen und Zeitschätzung.
//
// Plan-Tag ≠ Lektion. Der Lernstand zählt Lektionen (dayStars[Lektion]); der Plan
// rechnet um:  Lektion L → Plan-Tag L + ⌊(L−1)/4⌋,  Plan-Tag d → Gesprächstag, wenn d % 5 = 0.
// Gesprächstage und das Lerntempo stehen in einem eigenen Speicher ("plan_v1"),
// damit das Format des Lernstands unverändert bleibt.

export const isTalkDay = (d) => d % 5 === 0;
export const lessonOfDay = (d) => (isTalkDay(d) ? null : d - Math.floor(d / 5));
export const dayOfLesson = (L) => L + Math.floor((L - 1) / 4);

export const emptyState = () => ({ talk: {}, pace: { est: 0, act: 0, n: 0 } });
export const normalizeState = (s) => ({ ...emptyState(), ...(s ?? {}), pace: { ...emptyState().pace, ...(s?.pace ?? {}) } });

export class Plan {
  constructor(json) {
    this.json = json;
    this.days = json.days;
    this.builtDays = json.builtDays;
    this.phases = json.phases;
    this.blocks = json.blocks;
    this.byDay = new Map(json.days.map((d) => [d.day, d]));
  }
  day(n) { return this.byDay.get(n) ?? null; }
  ofLesson(L) { return this.day(dayOfLesson(L)); }
  block(n) { return this.blocks[n - 1] ?? null; }
  phaseOf(d) { return this.phases.find((p) => d >= p.from && d <= p.to) ?? null; }
  isBuilt(d) { return d <= this.builtDays; }
  get builtLessons() { return this.builtDays - Math.floor(this.builtDays / 5); }

  /** Sterne eines Plan-Tags (0 = offen). */
  stars(d, p, st) {
    const L = lessonOfDay(d);
    return L != null ? (p.dayStars[L] ?? 0) : (st.talk[d] ?? 0);
  }

  /**
   * Der Tag, der als Nächstes dran ist — oder null, wenn alles Gebaute geschafft ist.
   * Ein offener Gesprächstag hält nur auf, solange danach noch keine Lektion gemacht wurde
   * (wer vor der Einführung der Gesprächstage weiter war, wird nicht zurückgeworfen).
   */
  currentDay(p, st) {
    const maxLesson = Math.max(0, ...Object.keys(p.dayStars).map(Number));
    for (let d = 1; d <= this.builtDays; d++) {
      const L = lessonOfDay(d);
      if (L != null) { if (!p.dayStars[L]) return d; continue; }
      if (!st.talk[d] && maxLesson <= lessonOfDay(d - 1)) return d;
    }
    return null;
  }
  /** Gesprächstage, die offen geblieben sind, obwohl es danach schon weiterging. */
  skippedTalks(p, st) {
    const cur = this.currentDay(p, st) ?? this.builtDays + 1;
    return this.days.filter((d) => d.kind === 'talk' && d.day < cur && !st.talk[d.day]).map((d) => d.day);
  }
  doneCount(p, st) {
    let n = 0;
    for (let d = 1; d <= this.builtDays; d++) if (this.stars(d, p, st) > 0) n++;
    return n;
  }
}

// ── Zeitschätzung ──────────────────────────────────────────────────────────

/** Durchschnittliche Sekunden je Schritt (gemessen an ruhigem Durcharbeiten mit Nachsprechen). */
export const STEP_SECONDS = {
  soundTip: 50, intro: 24, listenChoose: 11, meaningChoose: 12, reply: 17, stressTap: 10, build: 24, speak: 24,
  answer: 30, pattern: 210, stageBreak: 0,
};
export const stepSeconds = (s) => (s.type === 'dialog' ? 20 + s.lines.length * 17 : STEP_SECONDS[s.type] ?? 15);

export const STAGES = {
  LESSON: [
    { id: 'meet', title: 'Kennenlernen', hint: 'Neue Sätze hören und verstehen', icon: 'hearing' },
    { id: 'drill', title: 'Üben', hint: 'Sprechen, bauen, wiederholen', icon: 'mic' },
    { id: 'use', title: 'Anwenden', hint: 'Im Gespräch benutzen', icon: 'forum' },
  ],
  PODCAST: [
    { id: 'meet', title: 'Kennenlernen', hint: 'Die Phrasen der Folge', icon: 'hearing' },
    { id: 'drill', title: 'Üben', hint: 'Sprechen, bauen, wiederholen', icon: 'mic' },
  ],
  TALKDAY: [
    { id: 'warm', title: 'Aufwärmen', hint: 'Bekannte Sätze aus dem Kopf sagen', icon: 'bolt' },
    { id: 'play', title: 'Rollenspiele', hint: 'Zwei Gespräche aus diesem Block', icon: 'forum' },
    { id: 'free', title: 'Frei antworten', hint: 'Anja fragt, du antwortest ohne Vorlage', icon: 'record_voice_over' },
  ],
};

/** Sekunden je Etappe für fertige Schritte: [s0, s1, …]. */
export function stageSeconds(steps, stageCount) {
  const out = Array.from({ length: stageCount }, () => 0);
  for (const s of steps) if (s.stage != null && s.stage < stageCount) out[s.stage] += stepSeconds(s);
  return out;
}

/** Schätzung für eine Lektion, ohne sie aufzubauen (für Listen): Sekunden je Etappe. */
export function lessonEstimate(day, hasPattern) {
  const n = day.items.length;
  const meet = STEP_SECONDS.soundTip + n * STEP_SECONDS.intro + n * 11.5;
  const drill = n * 21 + 5 * 20;
  const use = (hasPattern ? STEP_SECONDS.pattern : 0) + 20 + day.dialog.lines.length * 17;
  return [meet, drill, use];
}
export function podcastEstimate(day) {
  const n = day.items.length;
  return [n * STEP_SECONDS.intro + n * 11.5, n * 21 + 3 * 20];
}
/** Gesprächstag: acht Sätze, zwei Rollenspiele (je ~8 Zeilen), fünf freie Antworten. */
export const talkEstimate = () => [8 * STEP_SECONDS.speak, 2 * (20 + 8 * 17), 5 * STEP_SECONDS.answer];

/** Persönlicher Faktor: wie viel länger oder kürzer du brauchst als die Schätzung (ab der ersten Etappe). */
export function paceFactor(st) {
  const { est, act, n } = st.pace;
  if (!n || est < 120) return 1;
  return Math.min(1.8, Math.max(0.6, act / est));
}
export const addPace = (st, est, act) => ({ ...st, pace: { est: st.pace.est * 0.85 + est, act: st.pace.act * 0.85 + act, n: st.pace.n + 1 } });

export const toMinutes = (seconds, factor = 1) => Math.max(1, Math.round((seconds * factor) / 60));
export const minutesLabel = (seconds, factor = 1) => `ca. ${toMinutes(seconds, factor)} Min.`;
