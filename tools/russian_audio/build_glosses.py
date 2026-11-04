#!/usr/bin/env python3
"""Wort-für-Wort-Übersetzungen (Interlinear-Glossen) für alle Russisch-Sätze.

    python3 build_glosses.py extract <outdir> [--batches N]
        Sammelt jeden russischen Satz aus den Assets (und den fest im Code
        stehenden Journey-Sätzen), zerlegt ihn wie RussianPhonetics.tokenize()
        und schreibt Batches <outdir>/batch_XX.jsonl für die Glossierung.
        Bereits glossierte Sätze (in glosses.json) werden übersprungen.

    python3 build_glosses.py merge <outdir>
        Liest <outdir>/out_XX.json ({id: [gloss, ...]}), prüft, dass jede
        Glossenliste genau so viele Einträge hat wie der Satz Wörter, und
        schreibt assets/russian/glosses.json:

            {"version": 1,
             "s": {"<Satz ohne Betonung>": ["weil", "=", "das", "interessant"]},
             "w": {"<wort klein>": "<häufigste Glosse>"}}

        "=" heißt: gehört zur Glosse des vorigen Wortes (потому что = weil).
        "w" ist der Rückfall für Sätze, die zur Laufzeit entstehen.

    python3 build_glosses.py check
        Meldet Sätze ohne Glosse (Exit 1, falls welche fehlen).
"""
from __future__ import annotations

import collections
import json
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ASSETS = ROOT / "android/app/src/main/assets/russian"
KOTLIN = ROOT / "android/app/src/main/java/com/hikari/app/domain/russian"
OUT = ASSETS / "glosses.json"
SOURCES = ["course.json", "deepdive.json", "patterns.json", "podcast.json", "sounds.json"]
CYR = re.compile("[а-яА-ЯёЁ]")
GERMAN = re.compile("[a-zA-ZäöüÄÖÜß]{3,}")


def plain(s: str) -> str:
    return s.replace("+", "").replace("́", "").strip()


def is_word_char(c: str) -> bool:
    lc = c.lower()
    return c in "+-" or ("а" <= lc <= "я") or lc == "ё"


def tokenize(src: str) -> list[str]:
    """Wörter genau wie RussianPhonetics.tokenize() (ohne Separatoren)."""
    words, i = [], 0
    while i < len(src):
        start = i
        if is_word_char(src[i]) and src[i] != "-":
            while i < len(src) and is_word_char(src[i]):
                i += 1
            end = i
            while end > start and src[end - 1] == "-":
                end -= 1
            words.append(src[start:end])
        else:
            while i < len(src) and not (is_word_char(src[i]) and src[i] != "-"):
                i += 1
    return [w for w in words if CYR.search(w)]


def collect() -> dict[str, str]:
    """Satz (ohne Betonung) → deutsche Übersetzung (falls bekannt)."""
    found: dict[str, str] = {}

    def add(ru: str, de: str | None):
        p = plain(ru)
        if not p or not tokenize(p):
            return
        if p not in found or (not found[p] and de):
            found[p] = de or ""

    def walk(o):
        if isinstance(o, dict):
            for k in ("ru", "ruF"):
                v = o.get(k)
                if isinstance(v, str) and CYR.search(v):
                    de = o.get("deF" if k == "ruF" else "de") or o.get("de")
                    add(v, de if isinstance(de, str) else None)
            for v in o.values():
                if isinstance(v, (dict, list)):
                    walk(v)
        elif isinstance(o, list):
            for v in o:
                if isinstance(v, (dict, list)):
                    walk(v)
                elif isinstance(v, str) and CYR.search(v) and not GERMAN.search(v):
                    add(v, None)  # z. B. Beispielwörter in Lauttipps

    for f in SOURCES:
        walk(json.loads((ASSETS / f).read_text()))
    # Fest im Code stehende Sätze (Journey, Gesprächs-Vorlagen).
    lit = re.compile(r'"((?:[^"\\]|\\.)*)"')
    for kt in sorted(KOTLIN.glob("*.kt")):
        if kt.name in ("RussianPhonetics.kt", "RussianExercises.kt", "RussianDeepdive.kt"):
            continue
        for m in lit.finditer(kt.read_text()):
            s = m.group(1)
            if CYR.search(s) and not GERMAN.search(s):
                add(s, None)
    return found


def load_existing() -> dict:
    if OUT.exists():
        return json.loads(OUT.read_text())
    return {"version": 1, "s": {}, "w": {}}


def extract(outdir: Path, batches: int):
    outdir.mkdir(parents=True, exist_ok=True)
    existing = load_existing()["s"]
    todo = [(ru, de) for ru, de in collect().items() if ru not in existing]
    per = max(1, -(-len(todo) // batches))
    for b in range(0, len(todo), per):
        idx = b // per
        with open(outdir / f"batch_{idx:02d}.jsonl", "w") as fh:
            for n, (ru, de) in enumerate(todo[b:b + per]):
                row = {"id": f"{idx:02d}-{n}", "ru": ru, "de": de, "words": tokenize(ru)}
                fh.write(json.dumps(row, ensure_ascii=False) + "\n")
    print(f"{len(todo)} Sätze → {-(-len(todo) // per) if todo else 0} Batches in {outdir}")


def merge(outdir: Path):
    data = load_existing()
    sentences = data["s"]
    bad = 0
    for batch in sorted(outdir.glob("batch_*.jsonl")):
        out = outdir / batch.name.replace("batch_", "out_").replace(".jsonl", ".json")
        if not out.exists():
            print(f"fehlt: {out.name}")
            continue
        glossed = json.loads(out.read_text())
        for line in batch.read_text().splitlines():
            row = json.loads(line)
            g = glossed.get(row["id"])
            if not isinstance(g, list) or len(g) != len(row["words"]) or \
                    not all(isinstance(x, str) and x.strip() for x in g) or g[0] == "=":
                bad += 1
                print(f"ungültig {row['id']}: {row['ru']} → {g}")
                continue
            sentences[row["ru"]] = [x.strip() for x in g]
    # Rückfall-Wörterbuch: häufigste Einzelglosse je Wortform.
    votes: dict[str, collections.Counter] = collections.defaultdict(collections.Counter)
    for ru, glosses in sentences.items():
        words = tokenize(ru)
        for i, (w, g) in enumerate(zip(words, glosses)):
            nxt = glosses[i + 1] if i + 1 < len(glosses) else None
            if g == "=" or nxt == "=":
                continue  # Teil einer festen Wendung, nicht allein übertragbar
            votes[plain(w).lower()][g] += 1
    data["s"] = dict(sorted(sentences.items()))
    data["w"] = {w: c.most_common(1)[0][0] for w, c in sorted(votes.items())}
    OUT.write_text(json.dumps(data, ensure_ascii=False, separators=(",", ":")))
    print(f"{len(sentences)} Sätze, {len(data['w'])} Wörter → {OUT} ({bad} ungültig)")


def check():
    existing = load_existing()["s"]
    missing = [ru for ru in collect() if ru not in existing]
    for ru in missing[:30]:
        print("ohne Glosse:", ru)
    print(f"{len(missing)} Sätze ohne Glosse")
    sys.exit(1 if missing else 0)


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "check"
    if cmd == "extract":
        n = int(sys.argv[sys.argv.index("--batches") + 1]) if "--batches" in sys.argv else 8
        extract(Path(sys.argv[2]), n)
    elif cmd == "merge":
        merge(Path(sys.argv[2]))
    else:
        check()
