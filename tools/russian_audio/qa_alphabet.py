"""
Abschlussprüfung der Alphabet-Clips: Whisper hört jedes Einzelwort ab. Versteht es das
Wort nicht exakt, wird der Clip (normal + langsam) bis zu --tries-mal neu erzeugt und der
erste verstandene Kandidat behalten. Ergebnis: qa_alphabet_report.json.

  python qa_alphabet.py --model <vosk>
"""
import argparse, json, pathlib, sys, tempfile

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import generate as g  # noqa: E402
import generate_alphabet as ga  # noqa: E402
import qa  # noqa: E402

PROMPT = "Привет, как дела? Мама, папа, кот, дом, чай, сыр, борщ."


def heard(path):
    import mlx_whisper
    r = mlx_whisper.transcribe(str(path), path_or_hf_repo=qa.WHISPER, language="ru", initial_prompt=PROMPT)
    return r["text"].strip()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", required=True)
    ap.add_argument("--tries", type=int, default=6)
    args = ap.parse_args()
    sys.argv = sys.argv[:1]

    data = json.loads(ga.ALPHABET.read_text())
    words = sorted({w["ru"] for l in data["letters"] for w in l["words"]} | {w["ru"] for w in data["reading"]})
    plain = lambda w: w.replace("+", "")
    bad = []
    for w in words:
        text = heard(g.OUT / f"{ga.clip_name(w)}.ogg")
        if not qa.same(plain(w), text):
            bad.append({"ru": w, "heard": text})
    print(f"{len(bad)} von {len(words)} nicht exakt verstanden", flush=True)

    if bad:
        import soundfile as sf
        from vosk_tts import Model, Synth
        model_dir = pathlib.Path(args.model)
        model = Model(model_path=str(model_dir))
        synth = Synth(model)
        prepare = g.make_preparer(model, model_dir)
        for b in bad:
            w = b["ru"]
            b["fixed"] = False
            for _ in range(args.tries):
                with tempfile.TemporaryDirectory() as tmp:
                    wav = pathlib.Path(tmp) / "c.wav"
                    ogg = pathlib.Path(tmp) / "c.ogg"
                    sf.write(wav, synth.synth_audio(prepare(w), speaker_id=g.VOICE["m"], speech_rate=1.0), 22050)
                    g.encode(wav, ogg)
                    text = heard(ogg)
                    if qa.same(plain(w), text):
                        (g.OUT / f"{ga.clip_name(w)}.ogg").write_bytes(ogg.read_bytes())
                        b["fixed"] = True
                        break
                    b["last"] = text
            print(w, "behoben" if b["fixed"] else f"NICHT behoben (gehört: {b.get('last', b['heard'])})", flush=True)
    (HERE / "qa_alphabet_report.json").write_text(json.dumps(bad, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    main()
