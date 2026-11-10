"""
Erzeugt die Einzelwort-Clips für das Alphabet im Feed aus assets/russian/alphabet.json
(Beispielwörter der Buchstaben und die Lesewörter).

Gleiche Technik und gleicher Dateiname wie generate_sounds.py (lab_<sha1(ru)[:10]>.ogg,
normal + _slow), damit die App die Clips ohne Index findet. Vorhandene Clips bleiben.

  python generate_alphabet.py --check --model <vosk>   # nur Betonung gegen das Wörterbuch
  python generate_alphabet.py --model <vosk>           # fehlende Clips erzeugen
"""
import argparse, hashlib, json, pathlib, sys, tempfile

import generate as g

ALPHABET = g.ROOT / "android/app/src/main/assets/russian/alphabet.json"


def clip_name(ru):
    return "lab_" + hashlib.sha1(ru.encode()).hexdigest()[:10]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", required=True)
    ap.add_argument("--check", action="store_true")
    args = ap.parse_args()

    from vosk_tts import Model, Synth
    import soundfile as sf

    model_dir = pathlib.Path(args.model)
    model = Model(model_path=str(model_dir))
    report = {"no_dict_match": {}, "dict_disagrees": {}}
    tts_text = g.make_preparer(model, model_dir, report)
    data = json.loads(ALPHABET.read_text())
    words = sorted({w["ru"] for l in data["letters"] for w in l["words"]} | {w["ru"] for w in data["reading"]})
    prepared = {w: tts_text(w) for w in words}
    print(json.dumps(report, ensure_ascii=False, indent=1))
    if args.check:
        return 1 if report["dict_disagrees"] else 0

    synth = Synth(model)
    g.OUT.mkdir(parents=True, exist_ok=True)
    made = 0
    with tempfile.TemporaryDirectory() as tmp:
        for w in words:
            for suffix, rate in (("", 1.0), ("_slow", g.SLOW_RATE)):
                out = g.OUT / f"{clip_name(w)}{suffix}.ogg"
                if out.exists():
                    continue
                audio = synth.synth_audio(prepared[w], speaker_id=g.VOICE["m"], speech_rate=rate)
                wav = pathlib.Path(tmp) / "x.wav"
                sf.write(wav, audio, 22050)
                g.encode(wav, out)
                made += 1
            print(w, clip_name(w), flush=True)
    print(f"fertig: {len(words)} Wörter, {made} neue Clips")


if __name__ == "__main__":
    sys.exit(main())
