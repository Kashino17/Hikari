"""
Erzeugt die Clips für das Lautlabor (Minimalpaare) aus assets/russian/sounds.json.

Gleiche Technik wie generate.py (Vosk TTS, exakte Betonung aus dem Aussprache-
wörterbuch), aber Einzelwörter: Sprecher 4 (männlich), normal und langsam.
Ausgabe: assets/russian/audio/lab_<hash>.ogg und _slow — der Dateiname hängt am
russischen Text (mit "+"), sodass die App ihn ohne Index findet.

  python generate_sounds.py --model models/vosk
"""
import argparse, hashlib, json, pathlib, sys, tempfile

import generate as g

SOUNDS = g.ROOT / "android/app/src/main/assets/russian/sounds.json"


def clip_name(ru):
    return "lab_" + hashlib.sha1(ru.encode()).hexdigest()[:10]


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", required=True)
    args = ap.parse_args()

    from vosk_tts import Model, Synth
    import soundfile as sf

    model_dir = pathlib.Path(args.model)
    model = Model(model_path=str(model_dir))
    synth = Synth(model)
    tts_text = g.make_preparer(model, model_dir)
    data = json.loads(SOUNDS.read_text())
    words = sorted({side["ru"] for gr in data["groups"] for p in gr["pairs"] for side in (p["a"], p["b"])})
    g.OUT.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory() as tmp:
        for w in words:
            for suffix, rate in (("", 1.0), ("_slow", g.SLOW_RATE)):
                audio = synth.synth_audio(tts_text(w), speaker_id=g.VOICE["m"], speech_rate=rate)
                wav = pathlib.Path(tmp) / "x.wav"
                sf.write(wav, audio, 22050)
                g.encode(wav, g.OUT / f"{clip_name(w)}{suffix}.ogg")
            print(w, clip_name(w), flush=True)
    print(f"fertig: {len(words)} Wörter")


if __name__ == "__main__":
    sys.exit(main())
