"""Prüft deepdive_src: jedes russische Wort in `ru`-Feldern einsilbig oder mit genau einer Betonung, kein Latein; Warnungen für Fließtexte."""
import re, sys, pathlib
sys.path.insert(0, str(pathlib.Path(__file__).parent / "deepdive_src"))
import part1, part2, part3
VOW = "аеёиоуыэюяАЕЁИОУЫЭЮЯ"
ENT = {**part1.ENTRIES, **part2.ENTRIES, **part3.ENTRIES}
course = __import__("json").loads((pathlib.Path(__file__).resolve().parents[2] / "android/app/src/main/assets/russian/course.json").read_text())
ids = {i["id"] for d in course["days"] for i in d["items"]}

def check_ru(where, text, errs):
    if re.search(r"[A-Za-z]", text): errs.append(f"{where}: Latein in '{text}'")
    for w in re.findall(r"[\w+\-]+", text):
        for part in w.split("-"):
            if not part or part == 'нибудь': continue
            v = sum(c in VOW for c in part); pl = part.count("+")
            if pl and not re.fullmatch(r"(?:[^+]|\+[" + VOW + "])+", part): errs.append(f"{where}: + nicht vor Vokal in '{part}'")
            if "ё" in part.lower() and pl: errs.append(f"{where}: ё plus + in '{part}'")
            if v >= 2 and pl == 0 and "ё" not in part.lower(): errs.append(f"{where}: keine Betonung in '{part}'")
            if pl > 1: errs.append(f"{where}: mehrere Betonungen in '{part}'")

def texts(e):
    for k in ("summary", "grammar"):
        if e.get(k): yield k, e[k]
    if e.get("gender"):
        yield "gender.text", e["gender"]["text"]
errs, warns = [], []
for eid, e in ENT.items():
    if eid not in ids: errs.append(f"{eid}: kein Kurs-Item")
    for i, v in enumerate(e.get("variants", [])): check_ru(f"{eid}/v{i}", v["ru"], errs)
    for i, x in enumerate(e.get("examples", [])): check_ru(f"{eid}/e{i}", x["ru"], errs)
    for i, v in enumerate((e.get("gender") or {}).get("forms", [])): check_ru(f"{eid}/g{i}", v["ru"], errs)
    # Kyrillische Wörter in deutschen Texten ohne Betonung (≥2 Vokale) → Warnung
    for k, t in texts(e):
        for w in re.findall(r"[А-Яа-яЁё+]+", t):
            v = sum(c in VOW for c in w)
            if v >= 2 and "+" not in w and "ё" not in w.lower(): warns.append(f"{eid}/{k}: '{w}' ohne Betonung")
print("\n".join(errs) or "OK")
if warns: print(f"-- {len(warns)} Hinweise (Fließtext) --\n" + "\n".join(warns[:60]))
print(f"{len(ENT)} Einträge; Varianten {sum(len(e.get('variants',[])) for e in ENT.values())}, Beispiele {sum(len(e.get('examples',[])) for e in ENT.values())}")
sys.exit(1 if errs else 0)
