"""Einzelne Clips gezielt neu erzeugen, bis Whisper sie exakt versteht (und die o-Zahl stimmt).
   python fix_clips.py --model <dir> name1 name2 …   (Namen wie in manifest.json)"""
import argparse, json, pathlib, tempfile
import soundfile as sf
import generate


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", required=True)
    ap.add_argument("--tries", type=int, default=16)
    ap.add_argument("--speaker", type=int, default=None, help="andere Stimme für diese Clips (im Manifest vermerkt)")
    ap.add_argument("names", nargs="+")
    a = ap.parse_args()
    from vosk_tts import Model, Synth
    model = Model(model_path=a.model)
    synth = Synth(model)
    prepare = generate.make_preparer(model, pathlib.Path(a.model))
    judge = generate.make_judge()
    manifest = json.loads((generate.OUT / "manifest.json").read_text())
    for name in a.names:
        meta = manifest[name]
        if a.speaker is not None:
            meta["speaker"] = a.speaker
            meta["hash"] = generate.hashlib.sha1(
                f"{generate.MODEL_TAG}|{a.speaker}|{meta['rate']}|{meta['text']}".encode()).hexdigest()[:16]
        best = None
        with tempfile.TemporaryDirectory() as tmp:
            for t in range(a.tries):
                wav, ogg = pathlib.Path(tmp) / "c.wav", pathlib.Path(tmp) / f"c{t}.ogg"
                sf.write(wav, synth.synth_audio(prepare(meta["text"]), speaker_id=meta["speaker"], speech_rate=meta["rate"]), 22050)
                generate.encode(wav, ogg)
                score = judge(ogg, meta["text"])
                if best is None or score > best[0]:
                    best = (score, ogg)
                if score[0] and score[1]:
                    break
            (generate.OUT / f"{name}.ogg").write_bytes(best[1].read_bytes())
        (generate.OUT / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=1, sort_keys=True))
        print(name, "Whisper ok" if best[0][0] else "Whisper NICHT ok", "| o ok" if best[0][1] else "| o abweichend", f"({t + 1} Versuche)")


if __name__ == "__main__":
    main()
