#!/usr/bin/env python3
"""Hörprüfung der Coach-Ansagen in Rems Stimme: Whisper hört zu, wir vergleichen.

Läuft in der Umgebung mit mlx_whisper (~/.hikari/tts/ttsenv):

  ttsenv/bin/python coach_judge.py check  <coach_texts.json>            → Liste der Fehl-Ansagen (JSON auf stdout)
  ttsenv/bin/python coach_judge.py pick   <coach_texts.json> <kandidaten-ordner>
        → hört alle Kandidaten <id>_<n>.ogg ab und kopiert je Ansage den besten nach coach/<id>.ogg

Bewertet wird nur der Inhalt nach »Sag auf Russisch …:«. Zahlen schreibt Whisper als
Ziffern — das ist kein Fehler, solange nichts Erfundenes dazukommt (»fünf« → »5 Worte« fällt durch).
"""
import difflib
import glob
import hashlib
import json
import os
import re
import shutil
import sys

os.environ.setdefault("HF_HUB_OFFLINE", "1")
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "../.."))
OUT = os.path.join(ROOT, "android/app/src/main/assets/russian/coach")
WHISPER = "mlx-community/whisper-large-v3-mlx"
PASS = 0.8

NUM = re.compile(r"\d|eins|zwei|drei|vier|fünf|sechs|sieben|acht|neun|zehn|elf|zwölf|zig|ßig|hundert|tausend|erste|zweite|dritte", re.I)


def clip_id(spoken):
    return hashlib.sha1(spoken.encode("utf-8")).hexdigest()[:12]


def words(s):
    s = s.lower().replace("ß", "ss").replace("­", "")
    return re.sub(r"[^a-zäöü0-9 ]", " ", s).split()


def content(s):
    """Alles nach der Einleitung »Sag auf Russisch[, Hinweis]:«."""
    w = words(s)
    # Whisper hört den Anfang manchmal als »sagt«/»sark« — gilt trotzdem als Einleitung.
    if len(w) >= 3 and w[0].startswith("sa") and w[1:3] == ["auf", "russisch"]:
        w = w[3:]
    elif w[:2] == ["auf", "russisch"]:
        w = w[2:]
    return w


def score(spoken, heard):
    target, got = content(spoken), content(heard)
    # Hinweise aus der Einleitung (»locker«, »höflich«) stehen im Ziel-Inhalt nicht — aus beidem raus.
    hints = words(spoken.split(":", 1)[0])[3:] if ":" in spoken else []
    target = [w for w in target if w not in hints] or target
    got = [w for w in got if w not in hints]
    if not got:
        return 0.0
    # Zahlwörter ↔ Ziffern angleichen (»2 Minuten« / »zwei Minuten«).
    small = {"eins": "1", "ein": "1", "zwei": "2", "drei": "3", "vier": "4", "fünf": "5", "sechs": "6",
             "sieben": "7", "acht": "8", "neun": "9", "zehn": "10"}
    target = [small.get(w, w) for w in target]
    got = [small.get(w, w) for w in got]
    # Rem hat etwas dazuerfunden (»Fußball spielen Sie nicht«). Lange Sätze dürfen etwas schwanken.
    if len(got) > len(target) + (2 if len(target) >= 8 else 1):
        return 0.3
    ratio = difflib.SequenceMatcher(None, " ".join(target), " ".join(got)).ratio()
    if NUM.search(" ".join(target)) and any(re.search(r"\d", w) for w in got):
        # Zahl als Ziffern gehört: gut, solange alle übrigen Wörter auch im Ziel vorkommen.
        rest = [w for w in got if not re.fullmatch(r"\d+", w)]
        if all(any(difflib.SequenceMatcher(None, w, t).ratio() > 0.7 for t in target) for w in rest):
            ratio = max(ratio, 0.9)
    return ratio


def transcribe(path):
    import mlx_whisper
    return mlx_whisper.transcribe(path, path_or_hf_repo=WHISPER, language="de")["text"].strip()


def main():
    mode, texts = sys.argv[1], json.load(open(sys.argv[2], encoding="utf-8"))
    if mode == "check":
        bad = []
        for x in texts:
            f = os.path.join(OUT, clip_id(x["spoken"]) + ".ogg")
            if not os.path.exists(f):
                bad.append(x)
                continue
            heard = transcribe(f)
            s = score(x["spoken"], heard)
            if s < PASS:
                bad.append(x)
                print(f"!! {s:.2f} {x['spoken']} | {heard}", file=sys.stderr, flush=True)
        json.dump(bad, sys.stdout, ensure_ascii=False)
        print(f"{len(bad)} von {len(texts)} durchgefallen", file=sys.stderr)
    elif mode == "pick":
        cand_dir = sys.argv[3]
        still_bad = []
        for x in texts:
            cid = clip_id(x["spoken"])
            best, best_s, best_h = None, -1.0, ""
            for f in sorted(glob.glob(os.path.join(cand_dir, cid + "_*.ogg"))):
                heard = transcribe(f)
                s = score(x["spoken"], heard)
                if s > best_s:
                    best, best_s, best_h = f, s, heard
                if s >= 0.95:
                    break
            if best and best_s >= PASS:
                shutil.copy(best, os.path.join(OUT, cid + ".ogg"))
                print(f"OK {best_s:.2f} {x['spoken']} | {best_h}", file=sys.stderr, flush=True)
            else:
                still_bad.append(x)
                print(f"!! {best_s:.2f} {x['spoken']} | {best_h}", file=sys.stderr, flush=True)
        json.dump(still_bad, sys.stdout, ensure_ascii=False)
        print(f"{len(still_bad)} weiterhin durchgefallen", file=sys.stderr)


if __name__ == "__main__":
    main()
