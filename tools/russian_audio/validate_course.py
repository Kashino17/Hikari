"""Prüft course.json: Kyrillisch sauber, jede mehrsilbige Wortform genau eine Betonung, IDs eindeutig, Verweise gültig."""
import json, re, sys, pathlib

COURSE = pathlib.Path(__file__).resolve().parents[2] / "android/app/src/main/assets/russian/course.json"
VOWELS = "аеёиоуыэюяАЕЁИОУЫЭЮЯ"
course = json.loads(COURSE.read_text())
errors, ids = [], set()

def check_ru(where, text):
    if re.search(r"[A-Za-z]", text):
        errors.append(f"{where}: lateinische Buchstaben in '{text}'")
    for word in re.findall(r"[\w+\-]+", text):
        for part in word.split("-"):
            if not part:
                continue
            vowels = sum(1 for c in part if c in VOWELS)
            plus = part.count("+")
            if "+" in part and not re.fullmatch(r"(?:[^+]|\+[" + VOWELS + "])+", part):
                errors.append(f"{where}: + nicht vor Vokal in '{part}'")
            if "ё" in part.lower() and plus:
                errors.append(f"{where}: ё plus + in '{part}'")
            if vowels >= 2 and plus == 0 and "ё" not in part.lower():
                errors.append(f"{where}: keine Betonung in '{part}'")
            if plus > 1:
                errors.append(f"{where}: mehrere Betonungen in '{part}'")

def reg(i):
    if i in ids:
        errors.append(f"doppelte id {i}")
    ids.add(i)

for d in course["days"]:
    for it in d["items"]:
        reg(it["id"])
        check_ru(it["id"], it["ru"])
        if "ruF" in it:
            check_ru(it["id"] + "/F", it["ruF"])
    for ln in d["dialog"]["lines"]:
        reg(ln["id"])
        check_ru(ln["id"], ln["ru"])
        if "ruF" in ln:
            check_ru(ln["id"] + "/F", ln["ruF"])
        if ln["who"] not in ("P", "U"):
            errors.append(f"{ln['id']}: who={ln['who']}")
    for field in ("grammar",):
        if re.search(r"[а-яё][a-z]|[a-z][а-яё]", d.get(field, ""), re.I):
            errors.append(f"Tag {d['day']} {field}: gemischte Schrift")
item_ids = {it["id"] for d in course["days"] for it in d["items"]}
for d in course["days"]:
    for ex in d["sound"]["examples"]:
        if ex not in item_ids:
            errors.append(f"Tag {d['day']}: Beispiel {ex} fehlt")
    for it in d["items"]:
        for r in it.get("replies", []):
            if r not in item_ids:
                errors.append(f"{it['id']}: Antwort {r} fehlt")
print("\n".join(errors) or "OK")
print(f"{len(item_ids)} Items, {len(ids) - len(item_ids)} Dialogzeilen")
sys.exit(1 if errors else 0)
