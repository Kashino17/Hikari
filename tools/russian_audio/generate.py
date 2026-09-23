"""
Erzeugt die Russisch-Audios für Hikari aus android/app/src/main/assets/russian/course.json.

TTS: Vosk TTS 0.9 multi (Apache 2.0, alphacep) — offline, 5 Sprecher, Betonung
pro Vokal steuerbar (Phoneme a0/a1 …). Jedes Wort bekommt exakt die Betonung aus
course.json: Wir suchen im Vosk-Aussprachewörterbuch (2 Mio. Einträge) den
Eintrag mit genau dieser Betonung — das liefert nebenbei die Sonderlaute (что →
sh t o). Findet sich keiner, wird regelbasiert konvertiert und das Wort im
Bericht gemeldet (Hinweis auf einen möglichen Betonungsfehler im Kurs).

Aufruf:
  python generate.py --model <vosk-tts-ru-0.9-multi> [--only-voices] [--speakers 0,3]
Ausgabe:
  assets/russian/audio/<name>.ogg   (Opus, mono)
  assets/russian/audio/manifest.json   Name → Texthash (für den Aktualitätstest)
  tools/russian_audio/report.json      Wörterbuch-Abgleich
"""
import argparse, hashlib, json, pathlib, re, subprocess, sys, tempfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
COURSE = ROOT / "android/app/src/main/assets/russian/course.json"
OUT = ROOT / "android/app/src/main/assets/russian/audio"
REPORT = pathlib.Path(__file__).resolve().parent / "report.json"

VOWELS = "аеёиоуыэюя"
CLITICS = {"в", "во", "на", "по", "за", "из", "с", "со", "к", "ко", "у", "о", "об", "от",
           "до", "без", "при", "и", "а", "но", "не", "ни", "же", "ли", "бы"}

# Sprecher-IDs im Modell: female_0..2 = 0..2, male_0..1 = 3..4
# Per QA gewählt: Whisper-Verständlichkeit (alle ≥ 96,8 %) UND Vokalreduktion laut
# Phonemerkenner — Sprecher 4/1 sprechen unbetontes о am natürlichsten als [a/ə]
# (5 % bzw. 1 % unreduziert gegenüber 10 % bei Sprecher 3).
VOICE = {"m": 4, "f": 1, "p": 0}
SLOW_RATE = 0.7
MODEL_TAG = "vosk-tts-ru-0.9-multi"


def words_with_stress(text):
    """[(start, end, word, stressIndex|None)] — dieselben Regeln wie RussianPhonetics.kt."""
    out, after_ne = [], False
    single = len(re.findall(r"[А-Яа-яЁё+][А-Яа-яЁё+\-]*", text)) == 1
    for m in re.finditer(r"[А-Яа-яЁё+][А-Яа-яЁё+\-]*", text):
        raw = m.group(0).rstrip("-")
        letters, stress, explicit = "", None, False
        for c in raw:
            if c == "+":
                stress, explicit = len(letters), True
            else:
                letters += c.lower()
        if stress is None and "ё" in letters:
            stress = letters.index("ё")
        # Satzzeichen zwischen letztem Wort und diesem beenden die не-Regel
        if out:
            gap = text[out[-1][1]:m.start()]
            if any(p in gap for p in ".,!?;:…"):
                after_ne = False
        vc = sum(1 for c in letters if c in VOWELS)
        # что als Konjunktion ("потому что", ", что …") ist unbetont.
        prev_gap = text[out[-1][1]:m.start()] if out else ""
        conjunction = letters == "что" and out and (out[-1][2] == "потому" or "," in prev_gap)
        # Allein stehende Wörter (Karte "но") sind immer betont.
        if stress is None and vc == 1 and (single or letters not in CLITICS) and not after_ne and not conjunction:
            stress = next(i for i, c in enumerate(letters) if c in VOWELS)
        if explicit:
            after_ne = letters == "не"
        out.append((m.start(), m.start() + len(raw), letters, stress))
    return out


def vowel_ordinal(letters, idx):
    return None if idx is None else sum(1 for c in letters[:idx] if c in VOWELS)


def stress_ordinal_of_phones(phones):
    vs = [p for p in phones if p[-1] in "01" and p[:-1] in {"a", "e", "i", "o", "u", "y"}]
    for i, p in enumerate(vs):
        if p.endswith("1"):
            return i
    return None


def load_dictionary(model_dir):
    dic = {}
    with open(model_dir / "dictionary", encoding="utf-8") as f:
        for line in f:
            parts = line.split(maxsplit=2)
            if len(parts) == 3:
                dic.setdefault(parts[0], []).append((float(parts[1]), parts[2].strip()))
    return dic


def make_preparer(model, model_dir, report=None, full_dic=None):
    """Liefert tts_text(text): ersetzt jedes Wort durch ein Token mit fester Aussprache in model.dic."""
    from vosk_tts.g2p import convert
    if report is None:
        report = {"no_dict_match": {}, "dict_disagrees": {}}
    if full_dic is None:
        full_dic = load_dictionary(pathlib.Path(model_dir))

    def tts_text(text):
        parts, last = [], 0
        for start, end, letters, stress in words_with_stress(text):
            parts.append(text[last:start])
            last = end
            want = vowel_ordinal(letters, stress)
            token = letters if stress is None else letters[:stress] + "+" + letters[stress:]
            cands = full_dic.get(letters, [])
            match = [c for c in cands if stress_ordinal_of_phones(c[1].split()) == want]
            if match:
                phones = max(match)[1]
            elif want is None and cands:
                # Unbetonte Form: Laute des Wörterbuchs (что → sh t o), nur ohne Betonung.
                phones = " ".join(p[:-1] + "0" if p[-1] in "01" else p for p in max(cands)[1].split())
            else:
                phones = convert(token)
                if cands:
                    report["dict_disagrees"][letters] = {
                        "ours": want, "dict": sorted({str(stress_ordinal_of_phones(c[1].split())) for c in cands}),
                        "text": text,
                    }
                else:
                    report["no_dict_match"][letters] = {"ours": want, "text": text, "phones": phones}
            model.dic[token] = phones
            parts.append(token)
        parts.append(text[last:])
        return "".join(parts)

    return tts_text


def encode(wav, ogg):
    """Stille vorn/hinten kappen (sofortiger Einsatz beim Tippen), 80 ms Luft lassen; Opus 32 kbit/s."""
    subprocess.run([
        "ffmpeg", "-y", "-loglevel", "error", "-i", str(wav),
        "-af", "silenceremove=start_periods=1:start_threshold=-50dB:start_silence=0.08,"
               "areverse,silenceremove=start_periods=1:start_threshold=-50dB:start_silence=0.12,areverse",
        "-c:a", "libopus", "-b:a", "32k", "-ac", "1", str(ogg),
    ], check=True)


def make_judge():
    """Bewertet einen Kandidaten: (Whisper versteht exakt, o-Zahl stimmt, -o-Abweichung, Log-Wahrsch.)."""
    import mlx_whisper
    import qa
    import phon_model
    from stress_check import expected_o, observed_o

    def judge(ogg, text):
        r = mlx_whisper.transcribe(str(ogg), path_or_hf_repo=qa.WHISPER, language="ru",
                                   initial_prompt="Привет, как дела? Два, три, четыре.")
        lp = sum(s_["avg_logprob"] for s_ in r["segments"]) / max(1, len(r["segments"]))
        diff = abs(observed_o(phon_model.phon(str(ogg))) - expected_o(text))
        return (qa.same(text, r["text"]), diff == 0, -diff, lp)

    return judge


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", required=True)
    ap.add_argument("--speakers", default=None, help="Stimmenauswahl-Lauf: alle Items mit diesen IDs")
    ap.add_argument("--voice-dir", default=None)
    ap.add_argument("--best-of", type=int, default=4,
                    help="Kandidaten je Clip; behalten wird einer, den Whisper exakt versteht und dessen o-Zahl stimmt")
    args = ap.parse_args()

    from vosk_tts import Model, Synth

    model_dir = pathlib.Path(args.model)
    model = Model(model_path=str(model_dir))
    synth = Synth(model)
    full_dic = load_dictionary(model_dir)
    course = json.loads(COURSE.read_text())

    report = {"no_dict_match": {}, "dict_disagrees": {}}
    tts_text = make_preparer(model, model_dir, report, full_dic)

    jobs = []  # (name, text, speaker, rate)
    if args.speakers:
        for sid in [int(s) for s in args.speakers.split(",")]:
            for d in course["days"]:
                for it in d["items"]:
                    jobs.append((f"{it['id']}__s{sid}", it["ru"], sid, 1.0))
        out_dir = pathlib.Path(args.voice_dir)
    else:
        out_dir = OUT
        for d in course["days"]:
            for it in d["items"]:
                jobs.append((f"{it['id']}_m", it["ru"], VOICE["m"], 1.0))
                jobs.append((f"{it['id']}_f", it.get("ruF", it["ru"]), VOICE["f"], 1.0))
            for ln in d["dialog"]["lines"]:
                variants = [("m", ln["ru"])] + ([("f", ln["ruF"])] if "ruF" in ln else [])
                if ln["who"] == "P":
                    for tv, txt in variants:
                        jobs.append((f"{ln['id']}_{tv}_p", txt, VOICE["p"], 1.0))
                else:
                    jobs.append((f"{ln['id']}_m_m", ln["ru"], VOICE["m"], 1.0))
                    tv, txt = variants[-1]
                    jobs.append((f"{ln['id']}_{tv}_f", txt, VOICE["f"], 1.0))
        jobs += [(n + "_slow", t, s, SLOW_RATE) for (n, t, s, _) in list(jobs)]

    out_dir.mkdir(parents=True, exist_ok=True)
    manifest = {}
    import soundfile as sf
    judge = make_judge() if args.best_of > 1 and not args.speakers else None
    stats = {"first_try": 0, "retried_ok": 0, "unresolved": []}
    with tempfile.TemporaryDirectory() as tmp:
        for i, (name, text, sid, rate) in enumerate(jobs):
            prepared = tts_text(text)
            best = None
            for attempt in range(args.best_of if judge else 1):
                audio = synth.synth_audio(prepared, speaker_id=sid, speech_rate=rate)
                wav = pathlib.Path(tmp) / f"{name}.wav"
                cand = pathlib.Path(tmp) / f"{name}_{attempt}.ogg"
                sf.write(wav, audio, 22050)
                encode(wav, cand)
                if not judge:
                    best = (None, cand)
                    break
                score = judge(cand, text)
                if best is None or score > best[0]:
                    best = (score, cand)
                if score[0] and score[1]:
                    break
            (out_dir / f"{name}.ogg").write_bytes(best[1].read_bytes())
            if judge:
                ok = best[0][0] and best[0][1]
                if ok and attempt == 0:
                    stats["first_try"] += 1
                elif ok:
                    stats["retried_ok"] += 1
                else:
                    stats["unresolved"].append({"name": name, "text": text, "whisper_ok": best[0][0], "o_ok": best[0][1]})
            manifest[name] = {
                "text": text, "speaker": sid, "rate": rate,
                "hash": hashlib.sha1(f"{MODEL_TAG}|{sid}|{rate}|{text}".encode()).hexdigest()[:16],
            }
            if i % 100 == 0:
                print(f"{i}/{len(jobs)} {name}", flush=True)

    if judge:
        report["best_of"] = stats
        print(f"Best-of: {stats['first_try']} beim ersten Mal gut, {stats['retried_ok']} nach Neuversuch, "
              f"{len(stats['unresolved'])} ungelöst")
    if not args.speakers:
        (OUT / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=1, sort_keys=True))
    REPORT.write_text(json.dumps(report, ensure_ascii=False, indent=1, sort_keys=True))
    print(f"fertig: {len(jobs)} Clips; ohne Wörterbuch-Treffer: {len(report['no_dict_match'])}, "
          f"Betonung weicht ab: {len(report['dict_disagrees'])}")


if __name__ == "__main__":
    sys.exit(main())
