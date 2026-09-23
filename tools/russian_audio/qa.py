"""
Qualitätsprüfung der erzeugten Audios mit Whisper large-v3 (MLX, lokal).

Für jede Datei wird transkribiert und mit dem Kurstext verglichen (klein,
ohne Satzzeichen, ё→е). Ergebnis: tools/russian_audio/qa_report.json mit
Trefferquote, Abweichungen und mittlerer Log-Wahrscheinlichkeit je Sprecher.

  python qa.py                       # alle Audios laut manifest.json
  python qa.py --voices <dir>        # Stimmenauswahl-Lauf (<id>__s<N>.ogg)
  python qa.py --only-normal         # langsame Varianten überspringen
"""
import argparse, json, pathlib, re, statistics, sys

ROOT = pathlib.Path(__file__).resolve().parents[2]
COURSE = ROOT / "android/app/src/main/assets/russian/course.json"
AUDIO = ROOT / "android/app/src/main/assets/russian/audio"
OUT = pathlib.Path(__file__).resolve().parent / "qa_report.json"
WHISPER = "mlx-community/whisper-large-v3-mlx"

# Eigennamen, die Whisper legitim anders schreibt.
NAME_VARIANTS = {"кадир": {"кадир", "кадыр", "кадыра", "кадиру"}}


NUMS = {"1": "один", "2": "два", "3": "три", "4": "четыре", "5": "пять", "6": "шесть", "7": "семь",
        "8": "восемь", "9": "девять", "10": "десять", "20": "двадцать", "26": "двадцать шесть",
        "30": "тридцать", "40": "сорок", "100": "сто"}


def norm(s):
    s = re.sub(r"\d+", lambda m: " " + NUMS.get(m.group(0), m.group(0)) + " ", s)
    s = s.replace("+", "").replace("́", "").lower().replace("ё", "е")
    s = re.sub(r"[^а-я0-9 -]", " ", s).replace("-", " ")
    return s.split()


def same(expected, got):
    e, g = norm(expected), norm(got)
    if len(e) != len(g):
        return False
    for a, b in zip(e, g):
        if a == b:
            continue
        if a in NAME_VARIANTS and b in NAME_VARIANTS[a]:
            continue
        return False
    return True


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--voices", default=None)
    ap.add_argument("--only-normal", action="store_true")
    args = ap.parse_args()
    import mlx_whisper

    course = json.loads(COURSE.read_text())
    items = {it["id"]: it for d in course["days"] for it in d["items"]}

    jobs = []  # (file, text, speaker)
    if args.voices:
        for f in sorted(pathlib.Path(args.voices).glob("*.ogg")):
            iid, sid = f.stem.rsplit("__s", 1)
            jobs.append((f, items[iid]["ru"], int(sid)))
    else:
        manifest = json.loads((AUDIO / "manifest.json").read_text())
        for name, meta in sorted(manifest.items()):
            if args.only_normal and name.endswith("_slow"):
                continue
            jobs.append((AUDIO / f"{name}.ogg", meta["text"], meta["speaker"]))

    results, per_speaker = [], {}
    for i, (f, text, sid) in enumerate(jobs):
        # Zahlwörter ausschreiben lassen: Prompt mit russischem Text in Worten.
        r = mlx_whisper.transcribe(str(f), path_or_hf_repo=WHISPER, language="ru",
                                   initial_prompt="Привет, как дела? Два, три, четыре.")
        got = r["text"].strip()
        lp = statistics.mean(s["avg_logprob"] for s in r["segments"]) if r["segments"] else -9
        ok = same(text, got)
        results.append({"file": f.name, "expected": text.replace("+", ""), "got": got, "ok": ok, "logprob": round(lp, 3)})
        st = per_speaker.setdefault(sid, {"n": 0, "ok": 0, "lp": []})
        st["n"] += 1; st["ok"] += ok; st["lp"].append(lp)
        if i % 100 == 0:
            print(f"{i}/{len(jobs)}", flush=True)

    summary = {str(k): {"n": v["n"], "ok_rate": round(v["ok"] / v["n"], 3), "mean_logprob": round(statistics.mean(v["lp"]), 3)}
               for k, v in sorted(per_speaker.items())}
    fails = [r for r in results if not r["ok"]]
    OUT.write_text(json.dumps({"summary": summary, "failures": fails}, ensure_ascii=False, indent=1))
    print(json.dumps(summary, indent=1))
    print(f"{len(fails)} Abweichungen von {len(results)}")


if __name__ == "__main__":
    sys.exit(main())
