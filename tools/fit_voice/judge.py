#!/usr/bin/env python3
"""Hörprüfung der Fitness-Ansagen (Rems Stimme): Whisper hört jeden Clip, wir vergleichen mit dem Text.

  ~/.hikari/tts/ttsenv/bin/python3 tools/fit_voice/judge.py <fit_texts.json>
    → druckt durchgefallene Ansagen und schreibt <fit_texts>.failed.json (für gen_rem.py --candidates)
  … judge.py <fit_texts.json> --pick <kandidaten-ordner>
    → hört <id>_<k>.ogg ab und übernimmt je Ansage die beste Variante
"""
import difflib, glob, hashlib, json, os, re, shutil, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from voices import clip_id as _cid  # noqa: E402

os.environ.setdefault("HF_HUB_OFFLINE", "1")
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "../.."))
OUT = os.path.join(ROOT, "android/app/src/main/assets/fit-web/voice")
WHISPER = "mlx-community/whisper-large-v3-mlx"


def clip_id(s, voice="rem"):
    return _cid(s, voice)


def norm(s):
    s = s.lower().replace("ß", "ss").replace("’", "'")
    return re.sub(r"[^a-zäöü0-9 ]", " ", s).split()


def expected_digits(key):
    m = re.fullmatch(r"(\d+) Wdh", key)
    if m:
        return [int(m.group(1))]
    m = re.fullmatch(r"Runde (\d+)", key)
    if m:
        return [int(m.group(1))]
    m = re.fullmatch(r"(\d+) s", key)
    if m:
        s = int(m.group(1))
        return [s] if s < 60 else [s // 60] + ([s % 60] if s % 60 else [])
    return None


def score(item, heard):
    got = norm(heard)
    if not got:
        return 0.0
    want = norm(item["spoken"])
    if len(got) > len(want) + 2:
        return 0.3  # erfundener Zusatz
    exp = expected_digits(item["de"])
    digits = [int(w) for w in got if w.isdigit()]
    if exp is not None and digits:
        # Zahlen als Ziffern gehört: müssen stimmen (»eine Minute« darf ohne 1 sein)
        if [d for d in digits if d != 1] != [d for d in exp if d != 1]:
            return 0.2
        rest = [w for w in got if not w.isdigit()]
        words = [w for w in want if not re.search(r"(zig|ssig|zehn|zwölf|elf|eins|ein|zwei|drei|vier|fünf|sechs|sieben|acht|neun|hundert|eine)$", w)]
        return difflib.SequenceMatcher(None, " ".join(words), " ".join(rest)).ratio() if words else 1.0
    return difflib.SequenceMatcher(None, " ".join(want), " ".join(got)).ratio()


def transcribe(path):
    import mlx_whisper
    return mlx_whisper.transcribe(path, path_or_hf_repo=WHISPER, language="de")["text"].strip()


def main():
    items = json.load(open(sys.argv[1], encoding="utf-8"))
    if "--pick" in sys.argv:
        cand = sys.argv[sys.argv.index("--pick") + 1]
        index_file = os.path.join(OUT, "index.json")
        index = json.load(open(index_file, encoding="utf-8"))
        for it in items:
            cid = clip_id(it["spoken"], it.get("voice", "rem"))
            best, best_s = None, -1
            for f in sorted(glob.glob(os.path.join(cand, f"{cid}_*.ogg"))):
                s = score(it, transcribe(f))
                if s > best_s:
                    best, best_s = f, s
            if best and best_s >= 0.75:
                shutil.copy(best, os.path.join(OUT, cid + ".ogg"))
                index[it["de"]] = cid
                print(f"ok   {best_s:.2f} {it['spoken']}")
            else:
                print(f"FEHL {best_s:.2f} {it['spoken']}")
        json.dump(index, open(index_file, "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
        return
    failed = []
    for it in items:
        f = os.path.join(OUT, clip_id(it["spoken"], it.get("voice", "rem")) + ".ogg")
        if not os.path.exists(f):
            failed.append(it); print(f"fehlt  {it['spoken']}"); continue
        heard = transcribe(f)
        s = score(it, heard)
        if s < 0.75:
            failed.append(it); print(f"{s:.2f}  {it['spoken']!r} → {heard!r}")
    json.dump(failed, open(sys.argv[1] + ".failed.json", "w", encoding="utf-8"), ensure_ascii=False)
    print(f"{len(items) - len(failed)}/{len(items)} bestanden")


if __name__ == "__main__":
    main()
