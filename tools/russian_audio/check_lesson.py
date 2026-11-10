"""
Prüft neue Kurs-Lektionen, BEVOR sie in course.json landen.

  python check_lesson.py <lektion.json> [...]         # einzelne Lektions-Dateien (ein Tages-Objekt je Datei)
  python check_lesson.py --course                     # alle Lektionen in course.json

Geprüft wird:
  - Aufbau: day, title, goal, sound{title,text,examples}, grammar, items[12–16], dialog{id,title,scene,lines[8]}
  - IDs: d<NN>_… eindeutig, Dialogzeilen d<NN>_dlg_1…, P beginnt (neue Lektionen: 8 Zeilen, abwechselnd P/U)
  - Russisch: kein Latein, jedes mehrsilbige Wort hat genau ein "+" VOR dem betonten Vokal, kein "+" bei ё
  - Betonung gegen das Aussprachewörterbuch des Sprachmodells (~/.hikari/tts/models/vosk/dictionary):
      FEHLER   das Wörterbuch kennt das Wort, aber nicht mit dieser Betonung
      PRÜFEN   das Wörterbuch kennt mehrere Betonungen und die gewählte ist nicht die häufigste
               (bei Formen wie «дел+а / д+ела» am Satz entscheiden)
      UNBEKANNT das Wort steht nicht im Wörterbuch (Name, Tippfehler?)
  - replies / sound.examples zeigen auf vorhandene Sätze

Exit 1 bei FEHLER. PRÜFEN und UNBEKANNT sind Hinweise — jede Zeile ansehen und entscheiden.
"""
import collections, json, pathlib, re, sys

HERE = pathlib.Path(__file__).resolve().parent
COURSE = HERE.parents[1] / "android/app/src/main/assets/russian/course.json"
DICT = pathlib.Path.home() / ".hikari/tts/models/vosk/dictionary"
VOW = "аеёиоуыэюя"
UNSTRESSED_OK = {"обо", "нибудь", "либо", "ото", "изо", "передо"}  # nur als Anhängsel/Vorwort
# Von Hand bestätigt, obwohl das Wörterbuch anders betont (Namen, Lehnwörter, Teil von «по-моему»).
CONFIRMED = {"кад+ир", "+офисе", "+офис", "м+оему", "фастф+уда", "фастф+уд"}
WORD = re.compile(r"[А-Яа-яЁё+]+")


def ru_strings(day):
    for it in day.get("items", []):
        yield it.get("id", "?"), it.get("ru", "")
        if "ruF" in it:
            yield it["id"] + "/F", it["ruF"]
    for ln in day.get("dialog", {}).get("lines", []):
        yield ln.get("id", "?"), ln.get("ru", "")
        if "ruF" in ln:
            yield ln["id"] + "/F", ln["ruF"]


def structure(day, known_ids):
    errs = []
    n = day.get("day")
    pre = f"d{n:02d}_" if isinstance(n, int) else "d??_"
    for k in ("day", "title", "goal", "sound", "grammar", "items", "dialog"):
        if k not in day:
            errs.append(f"Feld fehlt: {k}")
    s = day.get("sound", {})
    if not (s.get("title") and s.get("text")) or not isinstance(s.get("examples"), list):
        errs.append("sound braucht title, text und examples[]")
    items = day.get("items", [])
    if not 12 <= len(items) <= 19:
        errs.append(f"{len(items)} Sätze (erwartet 12–16, höchstens 19)")
    ids = set()
    for it in items:
        i = it.get("id", "")
        if not re.fullmatch(pre + r"[a-z0-9_]+", i):
            errs.append(f"ID passt nicht zu {pre}…: {i!r}")
        if i in ids or i in known_ids:
            errs.append(f"ID doppelt: {i}")
        ids.add(i)
        if not it.get("ru") or not it.get("de"):
            errs.append(f"{i}: ru/de fehlt")
        if ("ruF" in it) != ("deF" in it) and "ruF" not in it:
            errs.append(f"{i}: deF ohne ruF")
    every = ids | known_ids
    for it in items:
        for r in it.get("replies", []):
            if r not in every:
                errs.append(f"{it['id']}: reply {r} gibt es nicht")
    for e in s.get("examples", []) or []:
        if e not in every:
            errs.append(f"sound.examples: {e} gibt es nicht")
    dlg = day.get("dialog", {})
    lines = dlg.get("lines", [])
    if not (dlg.get("id") == pre + "dlg" and dlg.get("title") and dlg.get("scene")):
        errs.append(f"dialog braucht id={pre}dlg, title, scene")
    if not 6 <= len(lines) <= 13:
        errs.append(f"{len(lines)} Dialogzeilen (erwartet 8)")
    if lines and lines[0].get("who") != "P":
        errs.append("Dialog: die Partnerin (P) beginnt")
    for k, ln in enumerate(lines, 1):
        if ln.get("id") != f"{pre}dlg_{k}":
            errs.append(f"Dialogzeile {k}: id muss {pre}dlg_{k} sein")
        if ln.get("who") not in ("P", "U"):
            errs.append(f"Dialogzeile {k}: who muss P oder U sein")
        if not ln.get("ru") or not ln.get("de"):
            errs.append(f"Dialogzeile {k}: ru/de fehlt")
    return errs


def spelling(day):
    errs, words = [], collections.defaultdict(list)
    for where, text in ru_strings(day):
        if re.search(r"[A-Za-z]", text):
            errs.append(f"{where}: lateinische Buchstaben in »{text}«")
        if "́" in text:
            errs.append(f"{where}: Akzentzeichen statt + in »{text}«")
        for token in re.findall(r"[А-Яа-яЁё+\-]+", text):
            for part in token.split("-"):
                if not part or part == "+":
                    continue
                low = part.lower()
                v = sum(c in VOW for c in low)
                plus = part.count("+")
                if plus and not re.fullmatch(r"(?:[^+]|\+[" + VOW + VOW.upper() + "])+", part):
                    errs.append(f"{where}: + steht nicht vor einem Vokal in »{part}«")
                elif "ё" in low and plus:
                    errs.append(f"{where}: ё ist immer betont, + entfernen in »{part}«")
                elif plus > 1:
                    errs.append(f"{where}: mehrere + in »{part}«")
                elif v >= 2 and plus == 0 and "ё" not in low and low not in UNSTRESSED_OK:
                    errs.append(f"{where}: Betonung fehlt in »{part}«")
                elif plus == 1 and v >= 2:
                    words[low].append((where, text))
    return errs, words


def stress_ordinal(form):
    i = form.index("+")
    return sum(c in VOW for c in form[:i])


def dictionary_check(words):
    plain = {w.replace("+", "") for w in words}
    dic = collections.defaultdict(dict)
    with open(DICT, encoding="utf-8") as f:
        for line in f:
            p = line.split(maxsplit=2)
            if len(p) == 3 and p[0] in plain:
                vs = [x for x in p[2].split() if x[-1] in "01" and x[:-1] in {"a", "e", "i", "o", "u", "y"}]
                pos = next((i for i, x in enumerate(vs) if x.endswith("1")), None)
                if pos is not None:
                    dic[p[0]][pos] = max(dic[p[0]].get(pos, 0.0), float(p[1]))
    errs, review, unknown = [], [], []

    def mark(w, o):
        k = -1
        for i, c in enumerate(w):
            if c in VOW:
                k += 1
                if k == o:
                    return w[:i] + "+" + w[i:]
        return w
    for form, uses in sorted(words.items()):
        w = form.replace("+", "")
        cands = dic.get(w)
        where, text = uses[0]
        if not cands:
            unknown.append(f"UNBEKANNT {form}  ({where}: {text})")
            continue
        if form in CONFIRMED:
            continue
        o = stress_ordinal(form)
        top = max(cands, key=cands.get)
        if o not in cands:
            errs.append(f"FEHLER {form} — Wörterbuch: {', '.join(mark(w, c) for c in sorted(cands, key=cands.get, reverse=True))}  ({where}: {text})")
        elif o != top:
            review.append(f"PRÜFEN {form} (Gewicht {cands[o]:.2f}) — häufiger: {mark(w, top)}  ({where}: {text})")
    return errs, review, unknown


def main():
    args = sys.argv[1:]
    course = json.loads(COURSE.read_text())
    if args == ["--course"]:
        days, known = course["days"], set()
    else:
        days = [json.loads(pathlib.Path(a).read_text()) for a in args]
        mine = {d.get("day") for d in days}
        known = {it["id"] for d in course["days"] if d["day"] not in mine for it in d["items"]}
    failed = False
    for day in days:
        others = known | {it["id"] for d in days if d is not day for it in d.get("items", [])}
        errs = structure(day, others if args != ["--course"] else {it["id"] for d in days if d is not day for it in d["items"]})
        sp, words = spelling(day)
        de, review, unknown = dictionary_check(words)
        errs += sp + de
        print(f"== Lektion {day.get('day')}: {day.get('title')} — {len(errs)} Fehler, {len(review)} zu prüfen, {len(unknown)} unbekannt")
        for line in errs + review + unknown:
            print("  " + line)
        failed |= bool(errs)
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
