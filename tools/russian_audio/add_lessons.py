"""
Hängt geprüfte Lektions-Dateien (ein Tages-Objekt je Datei, siehe check_lesson.py) an course.json an —
im selben Schriftbild wie der Rest der Datei (ein Satz je Zeile) — und trägt die Sätze ins Satzbuch ein.

  python add_lessons.py <d21.json> <d22.json> …
  python add_lessons.py --themes        # nur phrasebook.json für alle Lektionen ohne Thema nachziehen

Jede neue Lektion bekommt ein eigenes Satzbuch-Thema (THEMES unten ergänzen); Fragen kommen zusätzlich
ins Thema »Fragen«. Danach: build_plan.py, build_glosses.py, generate_missing.py (siehe README).
"""
import json, pathlib, sys

ASSETS = pathlib.Path(__file__).resolve().parents[2] / "android/app/src/main/assets/russian"
COURSE = ASSETS / "course.json"
BOOK = ASSETS / "phrasebook.json"

# Lektion → (Themen-ID, Titel im Satzbuch)
THEMES = {
    21: ("city", "Unterwegs in der Stadt"), 22: ("shopping", "Einkaufen & Geld"),
    23: ("guest", "Restaurant & zu Gast"), 24: ("phone", "Telefon & Nachrichten"),
    25: ("past", "Erzählen, was war"), 26: ("origin", "Kindheit & Herkunft"),
    27: ("travel", "Reisen & Erlebnisse"), 28: ("future", "Pläne & Träume"),
    29: ("affection", "Komplimente & Zuneigung"), 30: ("conflict", "Streit & Versöhnung"),
    31: ("health", "Gesundheit & Körper"), 32: ("self", "Wer ich bin"),
}

J = lambda o: json.dumps(o, ensure_ascii=False)  # noqa: E731


def render_day(d):
    known = {"day", "title", "goal", "sound", "grammar", "items", "dialog"}
    extra = set(d) - known
    assert not extra, f"Lektion {d.get('day')}: unbekannte Felder {extra}"
    s, dlg = d["sound"], d["dialog"]
    out = ["    {",
           f'      "day": {d["day"]},',
           f'      "title": {J(d["title"])},',
           f'      "goal": {J(d["goal"])},',
           '      "sound": {',
           f'        "title": {J(s["title"])},',
           f'        "text": {J(s["text"])},',
           f'        "examples": {J(s["examples"])}',
           "      },",
           f'      "grammar": {J(d["grammar"])},',
           '      "items": [',
           ",\n".join("        " + J(it) for it in d["items"]),
           "      ],",
           '      "dialog": {',
           f'        "id": {J(dlg["id"])},',
           f'        "title": {J(dlg["title"])},',
           f'        "scene": {J(dlg["scene"])},',
           '        "lines": [',
           ",\n".join("          " + J(ln) for ln in dlg["lines"]),
           "        ]",
           "      }",
           "    }"]
    return "\n".join(out)


def sync_themes():
    course = json.loads(COURSE.read_text())
    book = json.loads(BOOK.read_text())
    have = {t["id"] for t in book["themes"]}
    added = 0
    for d in course["days"]:
        theme = THEMES.get(d["day"])
        for it in d["items"]:
            if it["id"] in book["items"]:
                continue
            assert theme, f"Lektion {d['day']}: Thema in THEMES ergänzen"
            if theme[0] not in have:
                book["themes"].append({"id": theme[0], "title": theme[1]})
                have.add(theme[0])
            tags = [theme[0]] + (["question"] if it["ru"].rstrip().endswith("?") else [])
            book["items"][it["id"]] = tags
            added += 1
    BOOK.write_text(json.dumps(book, ensure_ascii=False, indent=1) + "\n")
    print(f"phrasebook.json: {added} Sätze eingetragen, {len(book['themes'])} Themen")


def main():
    files = [a for a in sys.argv[1:] if not a.startswith("--")]
    if files:
        text = COURSE.read_text()
        course = json.loads(text)
        last = max(d["day"] for d in course["days"])
        new = sorted((json.loads(pathlib.Path(f).read_text()) for f in files), key=lambda d: d["day"])
        assert [d["day"] for d in new] == list(range(last + 1, last + 1 + len(new))), "Lektionen müssen lückenlos anschließen"
        tail = "\n    }\n  ]\n}\n"
        assert text.endswith(tail), "unerwartetes Dateiende von course.json"
        text = text[: -len(tail)] + "\n    },\n" + ",\n".join(render_day(d) for d in new) + "\n  ]\n}\n"
        json.loads(text)  # muss gültig bleiben
        COURSE.write_text(text)
        print(f"course.json: Lektionen {new[0]['day']}–{new[-1]['day']} angehängt ({sum(len(d['items']) for d in new)} Sätze)")
    sync_themes()


if __name__ == "__main__":
    main()
