// Kurzschreibweise für Übungen.
// use: Buchstaben w (Aufwärmen), m (Hauptteil), c (Abkühlen). u: 'r' = Wiederholungen, 's' = Sekunden.
export function E(o) {
  const use = [...(o.use ?? 'm')].map((c) => ({ w: 'warmup', m: 'main', c: 'cooldown' })[c]);
  return {
    id: o.id,
    name: o.name,
    ...(o.en ? { en: o.en } : {}),
    pattern: o.p,
    muscles: o.mu ?? [],
    ...(o.sec?.length ? { secondary: o.sec } : {}),
    equipment: o.eq ?? [],
    level: o.lv,
    unit: o.u === 's' ? 'sec' : 'reps',
    ...(o.side ? { perSide: true } : {}),
    ...(o.jump ? { jump: true } : {}),
    stress: o.st ?? [],
    use,
    base: o.base,
    ...(o.u !== 's' ? { secPerRep: o.spr ?? 3 } : {}),
    ...(o.rg ? { regress: o.rg } : {}),
    ...(o.pg ? { progress: o.pg } : {}),
    steps: o.steps,
    mistakes: o.err,
    ...(o.tips?.length ? { tips: o.tips } : {}),
    ...(o.br ? { breathing: o.br } : {}),
  };
}
