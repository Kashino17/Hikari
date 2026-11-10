"""
Sieht sich die Clips an, die die Hörprüfung von generate_missing.py nicht bestanden haben
(missing_report_*.json → best_of.unresolved), und sortiert sie:

  HARMLOS   Whisper versteht den Satz exakt; nur die o-Zählung des Lauterkenners weicht ab
            (bei langen Sätzen und langsam gesprochenen Clips rauscht sie).
  PRÜFEN    Whisper hört etwas anderes — mit dem gehörten Text daneben.

  python review_unresolved.py            # alle Berichte
  python review_unresolved.py --retry 6  # PRÜFEN-Clips bis zu sechsmal neu erzeugen, besten behalten
"""
import argparse, glob, json, pathlib, sys, tempfile

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import generate as g  # noqa: E402
import qa  # noqa: E402
from generate_deepdive import speakable  # noqa: E402


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--retry", type=int, default=0)
    ap.add_argument("--model", default=str(pathlib.Path.home() / ".hikari/tts/models/vosk"))
    args = ap.parse_args()
    import mlx_whisper
    items = {}
    for f in sorted(glob.glob(str(HERE / "missing_report_*.json"))):
        for u in json.load(open(f)).get("best_of", {}).get("unresolved", []):
            items[u["name"]] = u
    hear = lambda p: mlx_whisper.transcribe(str(p), path_or_hf_repo=qa.WHISPER, language="ru",  # noqa: E731
                                            initial_prompt="Привет, как дела? Два, три, четыре.")["text"].strip()
    harmless = [u for u in items.values() if u["whisper_ok"]]
    check = [u for u in items.values() if not u["whisper_ok"]]
    print(f"{len(items)} ungelöst: {len(harmless)} harmlos (Whisper exakt), {len(check)} zu prüfen")
    synth = prep = None
    if args.retry and check:
        from vosk_tts import Model, Synth
        import soundfile as sf
        model = Model(model_path=args.model)
        synth, prep = Synth(model), g.make_preparer(model, pathlib.Path(args.model))
        manifest = json.loads((g.OUT / "manifest.json").read_text())
    fixed = 0
    for u in check:
        ogg = g.OUT / f"{u['name']}.ogg"
        text = speakable(u["text"])
        heard = hear(ogg)
        ok = qa.same(text, heard)
        if not ok and synth:
            entry = manifest.get(u["name"], {})
            sid = entry.get("speaker", g.VOICE["f" if "_f" in u["name"] else "m"])
            rate = g.SLOW_RATE if u["name"].endswith("_slow") else 1.0
            with tempfile.TemporaryDirectory() as tmp:
                for _ in range(args.retry):
                    wav, cand = pathlib.Path(tmp) / "x.wav", pathlib.Path(tmp) / "x.ogg"
                    sf.write(wav, synth.synth_audio(prep(text), speaker_id=sid, speech_rate=rate), 22050)
                    g.encode(wav, cand)
                    h2 = hear(cand)
                    if qa.same(text, h2):
                        ogg.write_bytes(cand.read_bytes())
                        heard, ok, fixed = h2, True, fixed + 1
                        break
        print(f"{'OK    ' if ok else 'PRÜFEN'} {u['name']}\n       soll: {text}\n       hört: {heard}")
    if synth:
        print(f"{fixed} Clips durch Neuversuch gelöst")


if __name__ == "__main__":
    sys.exit(main())
