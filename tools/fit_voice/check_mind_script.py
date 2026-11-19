#!/usr/bin/env python3
"""Prüft neue Sprechtexte für Achtsamkeit (Yoga-Flows, geführte Meditationen) gegen die Regeln.

Regeln (Kadir, 20.11.2026): warm und menschlich, nur Kommas — KEINE Punkte/Ausrufe-/Fragezeichen/
Doppelpunkte/Semikolons (die lassen Rem abrupt enden), keine Ziffern, je Ansage höchstens 24 Wörter.
Zeitbudget: Rem spricht ~2,6 Wörter/s. Eine Ansage muss in ihren Abschnitt passen; Zwischenansagen
(zwischen: [{nach, sage}]) kommen erst, wenn die vorige fertig ist (+3 s Luft), und enden 2 s vor
dem Abschnittsende.

  python3 tools/fit_voice/check_mind_script.py yoga <neu.json>        # {"<stufe>": [{sage, zwischen?}, …]}
  python3 tools/fit_voice/check_mind_script.py yoga-merged             # backend/fitness/mind/yoga-levels.json
  python3 tools/fit_voice/check_mind_script.py guided                  # content.json → gefuehrt
"""
import json, os, re, sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "../.."))
MIND = os.path.join(ROOT, "backend/fitness/mind")
WPS = 2.6
BAD = re.compile(r"[.!?;:]|\d")


def est(t):
    return len(t.split()) / WPS


def text_errors(t, where):
    errs = []
    if not t or not t.strip():
        errs.append(f"{where}: leer")
        return errs
    if BAD.search(t):
        errs.append(f"{where}: verbotenes Zeichen/Ziffer → {t!r}")
    if len(t.split()) > 24:
        errs.append(f"{where}: {len(t.split())} Wörter (max 24) → {t!r}")
    if t.rstrip() != t or t.endswith(","):
        errs.append(f"{where}: Ende ohne Komma/Leerzeichen schreiben → {t!r}")
    return errs


def check_step(sage, zwischen, secs, where):
    errs = text_errors(sage, where)
    if est(sage) > secs - 1.5:
        errs.append(f"{where}: Ansage ~{est(sage):.0f}s passt nicht in {secs}s")
    free_at = est(sage) + 3
    for k, z in enumerate(zwischen or []):
        w = f"{where} zwischen[{k}]"
        errs += text_errors(z.get("sage", ""), w)
        n = z.get("nach")
        if not isinstance(n, (int, float)):
            errs.append(f"{w}: nach fehlt")
            continue
        if n < free_at:
            errs.append(f"{w}: nach={n}s zu früh (frei ab {free_at:.0f}s)")
        if n + est(z["sage"]) > secs - 2:
            errs.append(f"{w}: endet nach {n + est(z['sage']):.0f}s, Abschnitt hat {secs}s")
        free_at = n + est(z["sage"]) + 3
    return errs


def main():
    mode = sys.argv[1]
    levels = {l["stufe"]: l for l in json.load(open(os.path.join(MIND, "yoga-levels.json"), encoding="utf-8"))}
    errs, n_steps, n_z = [], 0, 0
    if mode == "yoga":
        new = json.load(open(sys.argv[2], encoding="utf-8"))
        for st, steps in new.items():
            orig = levels[int(st)]["flow"]
            if len(steps) != len(orig):
                errs.append(f"Stufe {st}: {len(steps)} Schritte statt {len(orig)}")
                continue
            for i, (s, o) in enumerate(zip(steps, orig)):
                n_steps += 1
                n_z += len(s.get("zwischen") or [])
                errs += check_step(s.get("sage", ""), s.get("zwischen"), o["sekunden"], f"Stufe {st} #{i} ({o['asana'] or o['phase']}, {o['sekunden']}s)")
    elif mode == "yoga-merged":
        for st, l in levels.items():
            for i, o in enumerate(l["flow"]):
                n_steps += 1
                n_z += len(o.get("zwischen") or [])
                errs += check_step(o["sage"], o.get("zwischen"), o["sekunden"], f"Stufe {st} #{i} ({o['asana'] or o['phase']}, {o['sekunden']}s)")
    elif mode == "guided":
        c = json.load(open(os.path.join(MIND, "content.json"), encoding="utf-8"))
        for g in c["gefuehrt"]:
            for i, s in enumerate(g["schritte"]):
                n_steps += 1
                errs += text_errors(s["sage"], f"{g['id']} #{i}")
    for e in errs:
        print(e)
    print(f"{n_steps} Abschnitte, {n_z} Zwischenansagen, {len(errs)} Fehler")
    sys.exit(1 if errs else 0)


if __name__ == "__main__":
    main()
