"""
Baut assets/russian/patterns.json aus pattern_src/*.py, prüft Betonung/Wörterbuch und erzeugt die Clips.

Clip-Namen: dd_<sha1(ru)[:10]> (Du-Sätze, männliche Stimme; ruF weibliche Stimme),
Gesprächspartnerin: dd_<sha1("p|"+ru)[:10]> (Stimme 0). Normal + _slow.

  python generate_patterns.py --check --model ~/.hikari/tts/models/vosk   # Schreibung + Wörterbuch-Abgleich
  python generate_patterns.py --model ~/.hikari/tts/models/vosk           # JSON + fehlende Clips
"""
import argparse, json, pathlib, re, sys, tempfile

sys.path.insert(0, str(pathlib.Path(__file__).parent / "pattern_src"))
import patterns1
import patterns2
import generate as g
from generate_deepdive import clip_name, speakable

OUT_JSON = g.ROOT / "android/app/src/main/assets/russian/patterns.json"
PATTERNS = patterns1.PATTERNS + patterns2.PATTERNS
VOW = "аеёиоуыэюяАЕЁИОУЫЭЮЯ"


def partner_clip(ru):
    return clip_name("p|" + ru)


def clips():
    """{clipName: (text, voice)}"""
    out = {}
    for p in PATTERNS:
        for ln in p["scene"]["lines"]:
            if ln["who"] == "P":
                for t in (ln["ru"], ln.get("ruF")):
                    if t:
                        out[partner_clip(t)] = (t, g.VOICE["p"])
            else:
                out[clip_name(ln["ru"])] = (ln["ru"], g.VOICE["m"])
                if ln.get("ruF"):
                    out[clip_name(ln["ruF"])] = (ln["ruF"], g.VOICE["f"])
        for c in p["core"] + [p["free"]["model"]]:
            out[clip_name(c["ru"])] = (c["ru"], g.VOICE["m"])
    return out


def ru_strings():
    for p in PATTERNS:
        for i, ln in enumerate(p["scene"]["lines"]):
            yield f"{p['id']}/l{i}", ln["ru"]
            if ln.get("ruF"):
                yield f"{p['id']}/l{i}F", ln["ruF"]
        for i, c in enumerate(p["core"]):
            yield f"{p['id']}/c{i}", c["ru"]
        yield p["id"] + "/model", p["free"]["model"]["ru"]
        yield p["id"] + "/tpl", re.sub(r"\{\d\}", "", p["template"]["ru"])
        for si, s in enumerate(p["slots"]):
            for oi, o in enumerate(s["options"]):
                yield f"{p['id']}/s{si}o{oi}", o["ru"]
                if o.get("ruF"):
                    yield f"{p['id']}/s{si}o{oi}F", o["ruF"]


def check(model_dir=None):
    errs = []
    for where, text in ru_strings():
        for w in re.findall(r"[\w+\-]+", text):
            for part in w.split("-"):
                if not part:
                    continue
                if re.search(r"[A-Za-z]", part):
                    errs.append(f"{where}: Latein in '{part}'")
                v = sum(c in VOW for c in part)
                pl = part.count("+")
                if pl and not re.fullmatch(r"(?:[^+]|\+[" + VOW + "])+", part):
                    errs.append(f"{where}: + nicht vor Vokal in '{part}'")
                if "ё" in part.lower() and pl:
                    errs.append(f"{where}: ё plus + in '{part}'")
                if v >= 2 and pl == 0 and "ё" not in part.lower():
                    errs.append(f"{where}: keine Betonung in '{part}'")
                if pl > 1:
                    errs.append(f"{where}: mehrere Betonungen in '{part}'")
    if model_dir:
        full = g.load_dictionary(pathlib.Path(model_dir).expanduser())
        for where, text in ru_strings():
            for s, e, l, st in g.words_with_stress(text):
                w = g.vowel_ordinal(l, st)
                c = full.get(l, [])
                if c and w is not None and not (set(str(g.stress_ordinal_of_phones(x[1].split())) for x in c) == {'None'}) and not any(g.stress_ordinal_of_phones(x[1].split()) == w for x in c):
                    dic = sorted({str(g.stress_ordinal_of_phones(x[1].split())) for x in c})
                    errs.append(f"{where}: Betonung von '{l}' weicht vom Wörterbuch ab ({dic}) in '{text}'")
    return errs


def build_json():
    OUT_JSON.write_text(json.dumps({"version": 1, "patterns": PATTERNS}, ensure_ascii=False, indent=1) + "\n")
    print(f"{OUT_JSON.name}: {len(PATTERNS)} Muster")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--model")
    ap.add_argument("--check", action="store_true")
    args = ap.parse_args()
    errs = check(args.model)
    print("\n".join(errs) or "OK")
    if errs:
        return 1
    build_json()
    if args.check:
        return 0
    from vosk_tts import Model, Synth
    import soundfile as sf

    model_dir = pathlib.Path(args.model).expanduser()
    model = Model(model_path=str(model_dir))
    synth = Synth(model)
    tts_text = g.make_preparer(model, model_dir, {"no_dict_match": {}, "dict_disagrees": {}})
    g.OUT.mkdir(parents=True, exist_ok=True)
    todo = [(n, tv) for n, tv in clips().items() if not (g.OUT / f"{n}_slow.ogg").exists()]
    print(len(todo), "neue Clips")
    with tempfile.TemporaryDirectory() as tmp:
        for k, (name, (text, sid)) in enumerate(todo, 1):
            for suffix, rate in (("", 1.0), ("_slow", g.SLOW_RATE)):
                audio = synth.synth_audio(tts_text(speakable(text)), speaker_id=sid, speech_rate=rate)
                wav = pathlib.Path(tmp) / "x.wav"
                sf.write(wav, audio, 22050)
                g.encode(wav, g.OUT / f"{name}{suffix}.ogg")
            print(f"[{k}/{len(todo)}] {text}", flush=True)


if __name__ == "__main__":
    sys.exit(main())
