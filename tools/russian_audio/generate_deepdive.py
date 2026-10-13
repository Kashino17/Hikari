"""
Baut assets/russian/deepdive.json aus deepdive_src/*.py und erzeugt alle Audio-Clips dafür.

Clip-Name: audio/dd_<sha1(ru)[:10]>.ogg (+ _slow) — der Name hängt am russischen Text (mit "+"),
die App findet ihn ohne Index. Weibliche Sätze (de enthält "Frau"/"weiblich"/"(f") bekommen die
weibliche Stimme, alle anderen die männliche (wie im Kurs).

  python generate_deepdive.py --model ~/.hikari/tts/models/vosk          # JSON + fehlende Clips
  python generate_deepdive.py --json-only                                  # nur das JSON
"""
import argparse, hashlib, json, pathlib, re, sys, tempfile

sys.path.insert(0, str(pathlib.Path(__file__).parent / "deepdive_src"))
import part1, part2
import generate as g

OUT_JSON = g.ROOT / "android/app/src/main/assets/russian/deepdive.json"
ENTRIES = {**part1.ENTRIES, **part2.ENTRIES}


def clip_name(ru):
    return "dd_" + hashlib.sha1(ru.encode()).hexdigest()[:10]


def speakable(ru):
    """Der Clip-Name hängt am Originaltext; gesprochen wird er ohne Schrägstrich/Auslassungspunkte."""
    return ru.replace(" / ", ", ").replace("/", ", ").replace("…", ".")


def is_female(de):
    return bool(re.search(r"Frau|weiblich|\(f\b|\bf\)|Schöne\b|Freundin|Studentin|Ärztin", de))


def all_strings():
    seen = {}
    for e in ENTRIES.values():
        pools = list(e.get("variants", [])) + list(e.get("examples", [])) + list((e.get("gender") or {}).get("forms", []))
        for x in pools:
            seen.setdefault(x["ru"], is_female(x["de"]))
    return seen


def build_json():
    data = {"version": 1, "entries": []}
    for eid, e in ENTRIES.items():
        out = {"id": eid, "register": e.get("register", ""), "summary": e.get("summary", "")}
        out["variants"] = e.get("variants", [])
        out["examples"] = e.get("examples", [])
        if e.get("gender"):
            out["gender"] = e["gender"]
        if e.get("grammar"):
            out["grammar"] = e["grammar"]
        data["entries"].append(out)
    OUT_JSON.write_text(json.dumps(data, ensure_ascii=False, indent=1) + "\n")
    print(f"{OUT_JSON.name}: {len(data['entries'])} Einträge")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--model")
    ap.add_argument("--json-only", action="store_true")
    args = ap.parse_args()
    build_json()
    if args.json_only:
        return 0
    from vosk_tts import Model, Synth
    import soundfile as sf

    model_dir = pathlib.Path(args.model).expanduser()
    model = Model(model_path=str(model_dir))
    synth = Synth(model)
    report = {"no_dict_match": {}, "dict_disagrees": {}}
    tts_text = g.make_preparer(model, model_dir, report)
    g.OUT.mkdir(parents=True, exist_ok=True)
    todo = [(ru, f) for ru, f in all_strings().items() if not (g.OUT / f"{clip_name(ru)}_slow.ogg").exists()]
    print(f"{len(todo)} neue Texte")
    with tempfile.TemporaryDirectory() as tmp:
        for n, (ru, fem) in enumerate(todo, 1):
            sid = g.VOICE["f" if fem else "m"]
            for suffix, rate in (("", 1.0), ("_slow", g.SLOW_RATE)):
                audio = synth.synth_audio(tts_text(speakable(ru)), speaker_id=sid, speech_rate=rate)
                wav = pathlib.Path(tmp) / "x.wav"
                sf.write(wav, audio, 22050)
                g.encode(wav, g.OUT / f"{clip_name(ru)}{suffix}.ogg")
            print(f"[{n}/{len(todo)}] {ru}", flush=True)
    rp = pathlib.Path(__file__).parent / "deepdive_report.json"
    rp.write_text(json.dumps(report, ensure_ascii=False, indent=1))
    print("Betonung weicht vom Wörterbuch ab:", len(report["dict_disagrees"]), "· ohne Treffer:", len(report["no_dict_match"]))


if __name__ == "__main__":
    sys.exit(main())
