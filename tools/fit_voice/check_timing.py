#!/usr/bin/env python3
"""Zeitprüfung mit den ECHTEN Clip-Längen (nach der Vertonung).

- Yoga: Ansage + Zwischenansagen müssen in ihren Abschnitt passen und dürfen sich nicht überlappen
  (Zwischenansage startet erst, wenn die vorige fertig ist — sonst überspringt die App sie).
- Atem: Stichwort und Zahlen müssen in eine Sekunde passen, sonst fällt eine Zahl aus.

  python3 tools/fit_voice/check_timing.py
"""
import json, os, subprocess

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "../.."))
VOICE = os.path.join(ROOT, "android/app/src/main/assets/fit-web/voice")
MIND = os.path.join(ROOT, "backend/fitness/mind")
TAIL = 0.39  # weicher Ausklang + Stille am Ende jedes Clips (soften.py) — hörbar ist das nicht
index = json.load(open(os.path.join(VOICE, "index.json"), encoding="utf-8"))
_d = {}


def dur(text):
    cid = index.get(f"t:{text}")
    if not cid:
        return None
    if cid not in _d:
        out = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0",
                              os.path.join(VOICE, cid + ".ogg")], capture_output=True, text=True).stdout
        _d[cid] = max(0.0, float(out or 0) - TAIL)
    return _d[cid]


problems = []
for l in json.load(open(os.path.join(MIND, "yoga-levels.json"), encoding="utf-8")):
    for i, f in enumerate(l["flow"]):
        where = f"Stufe {l['stufe']} #{i} ({f['asana'] or f['phase']}, {f['sekunden']}s)"
        d = dur(f["sage"])
        if d is None:
            problems.append(f"{where}: kein Clip")
            continue
        if d > f["sekunden"] - 0.5:
            problems.append(f"{where}: Ansage {d:.1f}s länger als der Abschnitt")
        free = d
        for z in f.get("zwischen", []):
            dz = dur(z["sage"]) or 0
            if z["nach"] < free + 0.4:
                problems.append(f"{where}: Zwischenansage bei {z['nach']}s, vorige endet erst bei {free:.1f}s → {z['sage'][:50]}")
            if z["nach"] + dz > f["sekunden"] + 1:
                problems.append(f"{where}: Zwischenansage endet bei {z['nach'] + dz:.1f}s → {z['sage'][:50]}")
            free = z["nach"] + dz

c = json.load(open(os.path.join(MIND, "content.json"), encoding="utf-8"))
af = c.get("atem_fuehrung", {})
for z in af.get("zahlen", []):
    d = dur(z)
    if d is None or d > 0.9:
        problems.append(f"Atem: Zahl »{z}« {d}s (soll ≤ 0,9 s)")
for a in c["atem"]:
    for p in a["phasen"]:
        d = dur(p["sage"])
        if d is not None and d > 1.7 and p["sekunden"] >= 3:  # die App wartet bis 0,7 s auf das Stichwort
            problems.append(f"Atem {a['id']}: Stichwort »{p['sage']}« {d:.1f}s — »zwei« fällt aus (App wartet nur 0,7 s)")

for p in problems:
    print(p)
print(f"{len(problems)} Probleme")
