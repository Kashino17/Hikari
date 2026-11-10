"""
Baut assets/russian/plan.json aus plan_src/year_plan.py und course.json.

Plan-Tag ≠ Lektion: jeder fünfte Plan-Tag ist ein Gesprächstag ohne neuen Stoff.
  Lektion L  → Plan-Tag L + (L-1)//4
  Plan-Tag d → Gesprächstag, wenn d % 5 == 0, sonst Lektion d - d//5

  python build_plan.py            # schreiben (bricht ab, wenn eine als gebaut markierte Lektion fehlt)
  python build_plan.py --check    # nur prüfen
"""
import json, pathlib, sys

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE / "plan_src"))
import year_plan as yp  # noqa: E402

ASSETS = HERE.parents[1] / "android/app/src/main/assets/russian"
OUT = ASSETS / "plan.json"


def build():
    course = {d["day"]: d for d in json.loads((ASSETS / "course.json").read_text())["days"]}
    patterns = {p["id"] for p in json.loads((ASSETS / "patterns.json").read_text())["patterns"]}
    errors, days, blocks = [], [], []
    assert len(yp.BLOCKS) == 73, "365 Tage = 73 Blöcke"
    for n, (title, focus, lessons, talk) in enumerate(yp.BLOCKS, 1):
        built = n <= yp.BUILT_BLOCKS
        first = (n - 1) * 5 + 1
        phase = next(p[0] for p in yp.PHASES if p[3] <= n <= p[4])
        blocks.append({"n": n, "phase": phase, "title": title, "focus": focus, "from": first, "to": first + 4})
        ids = [(n - 1) * 4 + k for k in range(1, 5)]
        for k, lesson in enumerate(ids):
            day = {"day": first + k, "block": n, "kind": "lesson", "lesson": lesson}
            if built:
                src = course.get(lesson)
                if not src:
                    errors.append(f"Block {n} ist als gebaut markiert, aber Lektion {lesson} fehlt in course.json")
                    continue
                day.update(title=src["title"], goal=src["goal"])
                pat = yp.PATTERN_OF_LESSON.get(lesson)
                if pat:
                    if pat not in patterns:
                        errors.append(f"Lektion {lesson}: Muster {pat} gibt es nicht")
                    day["pattern"] = pat
            else:
                if len(lessons) != 4:
                    if "--built" not in sys.argv:
                        errors.append(f"Block {n}: vier Lektionen erwartet")
                        continue
                    lessons = [(f"Lektion {i}", "") for i in ids]  # Entwicklungsmodus: Platzhalter
                day.update(title=lessons[k][0], goal=lessons[k][1])
            days.append(day)
        days.append({"day": first + 4, "block": n, "kind": "talk", "title": talk[0], "scene": talk[1], "lessons": ids})
    extra = sorted(set(course) - {d["lesson"] for d in days if d["kind"] == "lesson" and d["block"] <= yp.BUILT_BLOCKS})
    if extra:
        errors.append(f"course.json enthält Lektionen außerhalb der gebauten Blöcke: {extra} (BUILT_BLOCKS erhöhen?)")
    used = list(yp.PATTERN_OF_LESSON.values())
    if len(used) != len(set(used)):
        errors.append("Ein Muster ist zwei Lektionen zugeordnet")
    phases = [{"id": p[0], "title": p[1], "level": p[2], "from": (p[3] - 1) * 5 + 1, "to": p[4] * 5, "goal": p[5]} for p in yp.PHASES]
    plan = {"version": 1, "builtDays": yp.BUILT_BLOCKS * 5, "phases": phases, "blocks": blocks, "days": days}
    return plan, errors


def main():
    if "--built" in sys.argv:  # nur für die Entwicklung: weniger Blöcke als gebaut behandeln
        yp.BUILT_BLOCKS = int(sys.argv[sys.argv.index("--built") + 1])
    plan, errors = build()
    if errors:
        print("\n".join(errors), file=sys.stderr)
        return 1
    assert [d["day"] for d in plan["days"]] == list(range(1, 366))
    if "--check" not in sys.argv:
        OUT.write_text(json.dumps(plan, ensure_ascii=False, indent=1) + "\n")
    print(f"plan.json: {len(plan['days'])} Tage, davon {plan['builtDays']} gebaut; "
          f"{sum(1 for d in plan['days'] if d['kind'] == 'talk')} Gesprächstage, "
          f"{sum(1 for d in plan['days'] if d.get('pattern'))} Lektionen mit Satzbau-Muster")
    return 0


if __name__ == "__main__":
    sys.exit(main())
