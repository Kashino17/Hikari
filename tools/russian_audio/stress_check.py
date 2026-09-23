"""
Phonem-Prüfung der Betonung: wav2vec2-Phonemerkenner (facebook/wav2vec2-xlsr-53-espeak-cv-ft)
hört jeden Clip ab. Russisch reduziert unbetontes о zu [a/ə] — hörbar bleibt ein [o]
nur in der betonten Silbe (und bei ё). Stimmt die Zahl gehörter [o] nicht mit der Zahl
betonter о/ё im Text überein, sitzt die Betonung vermutlich falsch oder wurde ein
unbetontes о nicht reduziert. Solche Clips werden mit --fix mehrfach neu erzeugt,
bis Whisper-Text UND o-Zählung stimmen (bestes Ergebnis wird behalten).

  python stress_check.py            # nur prüfen → stress_report.json
  python stress_check.py --fix --model <vosk-model-dir>
"""
import argparse, json, pathlib, re, subprocess, sys, tempfile

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import generate  # noqa: E402  (gleiche Betonungsregeln wie beim Erzeugen)
import qa  # noqa: E402

AUDIO = generate.OUT
OUT = HERE / "stress_report.json"


def expected_o(text):
    n = 0
    for _, _, letters, stress in generate.words_with_stress(text):
        if stress is not None and letters[stress] in "оё":
            n += 1
        n += letters.count("ё") if stress is None or letters[stress] != "ё" else 0
    return n


def observed_o(phones):
    return sum(1 for p in phones.split() if p.startswith(("o", "ɔ")))


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--fix", action="store_true")
    ap.add_argument("--model")
    ap.add_argument("--tries", type=int, default=8)
    args = ap.parse_args()
    sys.argv = sys.argv[:1]
    import phon_model
    manifest = json.loads((AUDIO / "manifest.json").read_text())
    flagged = []
    for name, meta in sorted(manifest.items()):
        if name.endswith("_slow"):
            continue
        exp = expected_o(meta["text"])
        got = observed_o(phon_model.phon(str(AUDIO / f"{name}.ogg")))
        if exp != got:
            flagged.append({"name": name, "text": meta["text"], "expected_o": exp, "observed_o": got})
    print(f"{len(flagged)} auffällig von {sum(1 for n in manifest if not n.endswith('_slow'))}")

    if args.fix and flagged:
        import mlx_whisper
        import soundfile as sf
        from vosk_tts import Model, Synth
        model = Model(model_path=args.model)
        synth = Synth(model)
        prepare = generate.make_preparer(model, pathlib.Path(args.model))
        fixed = []
        for f in flagged:
            meta = manifest[f["name"]]
            best = None
            for t in range(args.tries):
                with tempfile.TemporaryDirectory() as tmp:
                    wav = pathlib.Path(tmp) / "c.wav"
                    ogg = pathlib.Path(tmp) / "c.ogg"
                    sf.write(wav, synth.synth_audio(prepare(meta["text"]), speaker_id=meta["speaker"], speech_rate=1.0), 22050)
                    generate.encode(wav, ogg)
                    o = observed_o(phon_model.phon(str(ogg)))
                    r = mlx_whisper.transcribe(str(ogg), path_or_hf_repo=qa.WHISPER, language="ru",
                                               initial_prompt="Привет, как дела? Два, три, четыре.")
                    ok_text = qa.same(meta["text"], r["text"])
                    score = (ok_text, o == f["expected_o"], -abs(o - f["expected_o"]))
                    if best is None or score > best[0]:
                        best = (score, ogg.read_bytes())
                    if ok_text and o == f["expected_o"]:
                        break
            if best and best[0][1]:
                (AUDIO / f"{f['name']}.ogg").write_bytes(best[1])
                fixed.append(f["name"])
            f["fixed"] = bool(best and best[0][1])
            print(f["name"], "behoben" if f["fixed"] else "NICHT behoben", flush=True)
        print(f"{len(fixed)} von {len(flagged)} behoben")
    OUT.write_text(json.dumps(flagged, ensure_ascii=False, indent=1))


if __name__ == "__main__":
    main()
