/**
 * Skill-Fortschritt (Calisthenics). Eine Stufe gilt als geschafft, wenn ihr Ziel (z. B. 3 × 20 s)
 * an zwei verschiedenen Tagen erreicht wurde — einmal kann Glück sein, zweimal ist Können.
 */
import type { Catalog, Skill } from "./catalog.js";
import { type FitDb, localDay } from "./db.js";

export interface SkillState {
  id: string;
  step: number;
  active: boolean;
  mastered: boolean;
  /** Tage, an denen das Ziel der aktuellen Stufe erreicht wurde */
  hits: number;
  best: number | null;
  lastAt: number | null;
  log: { day: string; step: number; value: number; sets: number[]; feel: number | null }[];
}

export function goalMet(skill: Skill, step: number, sets: number[]): boolean {
  const g = skill.steps[step]?.goal;
  if (!g) return false;
  return sets.filter((v) => v >= g.value).length >= g.sets;
}

export function skillStates(db: FitDb, catalog: Catalog): SkillState[] {
  const rows = new Map(
    (
      db.prepare("SELECT * FROM skill_progress").all() as {
        skill_id: string;
        step: number;
        active: number;
      }[]
    ).map((r) => [r.skill_id, r]),
  );
  return catalog.skills.map((s) => {
    const p = rows.get(s.id);
    const step = Math.min(p?.step ?? 0, s.steps.length);
    const log = (
      db
        .prepare(
          "SELECT day, step, value, sets, feel, at FROM skill_log WHERE skill_id = ? ORDER BY at DESC LIMIT 40",
        )
        .all(s.id) as {
        day: string;
        step: number;
        value: number;
        sets: string;
        feel: number | null;
        at: number;
      }[]
    ).map((r) => ({
      day: r.day,
      step: r.step,
      value: r.value,
      sets: JSON.parse(r.sets || "[]") as number[],
      feel: r.feel,
      at: r.at,
    }));
    const atStep = log.filter((l) => l.step === step);
    const hitDays = new Set(atStep.filter((l) => goalMet(s, step, l.sets)).map((l) => l.day));
    return {
      id: s.id,
      step,
      active: !!p?.active,
      mastered: step >= s.steps.length,
      hits: hitDays.size,
      best: atStep.length ? Math.max(...atStep.map((l) => l.value)) : null,
      lastAt: log[0]?.at ?? null,
      log: log.map(({ at: _at, ...rest }) => rest),
    };
  });
}

export function setSkill(db: FitDb, skillId: string, patch: { active?: boolean; step?: number }) {
  const now = Date.now();
  db.prepare(
    "INSERT INTO skill_progress(skill_id, step, active, started_at, updated_at) VALUES(?, 0, 0, ?, ?) ON CONFLICT(skill_id) DO NOTHING",
  ).run(skillId, now, now);
  if (patch.active !== undefined)
    db.prepare("UPDATE skill_progress SET active = ?, updated_at = ? WHERE skill_id = ?").run(
      patch.active ? 1 : 0,
      now,
      skillId,
    );
  if (patch.step !== undefined)
    db.prepare("UPDATE skill_progress SET step = ?, updated_at = ? WHERE skill_id = ?").run(
      Math.max(0, Math.round(patch.step)),
      now,
      skillId,
    );
}

/** Trägt einen Übungsblock ein; rückt bei zwei erfolgreichen Tagen eine Stufe weiter. */
export function logSkill(
  db: FitDb,
  catalog: Catalog,
  skillId: string,
  step: number,
  sets: number[],
  feel: number | null,
  day = localDay(),
) {
  const skill = catalog.skillById.get(skillId);
  if (!skill) throw new Error("skill");
  const clean = sets
    .map((v) => Math.max(0, Math.round(Number(v) * 10) / 10))
    .filter((v) => Number.isFinite(v))
    .slice(0, 12);
  if (!clean.length) throw new Error("leer");
  setSkill(db, skillId, { active: true });
  db.prepare(
    "INSERT INTO skill_log(skill_id, step, day, value, sets, feel, at) VALUES(?, ?, ?, ?, ?, ?, ?)",
  ).run(skillId, step, day, Math.max(...clean), JSON.stringify(clean), feel, Date.now());
  const met = goalMet(skill, step, clean);
  const before = skillStates(db, catalog).find((s) => s.id === skillId) as SkillState;
  let advanced = false;
  if (met && before.step === step && before.hits >= 2 && step < skill.steps.length) {
    setSkill(db, skillId, { step: step + 1 });
    advanced = true;
  }
  return {
    met,
    advanced,
    state: skillStates(db, catalog).find((s) => s.id === skillId) as SkillState,
  };
}
