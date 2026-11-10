"""
Ergänzt NUR fehlende Clips — in denselben Stimmen wie der übrige Kurs. Vorhandene
Clips werden nie überschrieben (die Sprachausgabe ist zufallsbasiert; ein geprüfter
Clip soll bleiben, wie er ist).

Quellen (--only wählt eine aus):
  course    course.json: Sätze aller Tage (_m/_f) und Dialogzeilen, normal + _slow
  podcast   podcast.json: Lernphrasen aller Lektionen (_m/_f), normal + _slow
  patterns  Satzbau: Szenen, Kernsätze, Beispiele und ALLE baubaren Sätze (dd_<sha1>), normal + _slow
  deepdive  Vertiefungen (dd_<sha1>), normal + _slow

Kurs- und Podcast-Clips heißen nach der Satz-ID. Wurde der Text seit der Erzeugung geändert
(Hash in manifest.json passt nicht mehr), gilt der Clip als veraltet und wird neu erzeugt.

Podcast-Phrasen bekommen ihre Betonung von stress_podcast.py (vorher laufen lassen). Jeder Clip wird bis zu --best-of-mal erzeugt; behalten wird
ein Kandidat, den Whisper exakt versteht und dessen Zahl hörbarer [o] zur Betonung passt
(dieselbe Prüfung wie generate.py).

  python generate_missing.py --model ~/.hikari/tts/models/vosk --only course
  python generate_missing.py --model … --only podcast --shard 0/2   # zwei Läufe parallel: 0/2 und 1/2
  python generate_missing.py --finalize       # manifest.json um alle vorhandenen Clips ergänzen
  python generate_missing.py --list           # nur zählen, was fehlt
  python generate_missing.py --prune          # dd_-Clips löschen, die kein Text mehr verwendet
"""
import argparse, hashlib, itertools, json, pathlib, re, sys, tempfile, time

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
sys.path.insert(0, str(HERE / "pattern_src"))
import generate as g  # noqa: E402
from generate_deepdive import clip_name, speakable  # noqa: E402

ASSETS = g.ROOT / "android/app/src/main/assets/russian"


def course_jobs():
    """Gleiche Namen und Stimmen wie generate.py."""
    course = json.loads((ASSETS / "course.json").read_text())
    jobs = []
    for d in course["days"]:
        for it in d["items"]:
            jobs.append((f"{it['id']}_m", it["ru"], g.VOICE["m"]))
            jobs.append((f"{it['id']}_f", it.get("ruF", it["ru"]), g.VOICE["f"]))
        for ln in d["dialog"]["lines"]:
            variants = [("m", ln["ru"])] + ([("f", ln["ruF"])] if "ruF" in ln else [])
            if ln["who"] == "P":
                for tv, txt in variants:
                    jobs.append((f"{ln['id']}_{tv}_p", txt, g.VOICE["p"]))
            else:
                jobs.append((f"{ln['id']}_m_m", ln["ru"], g.VOICE["m"]))
                tv, txt = variants[-1]
                jobs.append((f"{ln['id']}_{tv}_f", txt, g.VOICE["f"]))
    return jobs


MODEL_DIR = pathlib.Path.home() / ".hikari/tts/models/vosk"


def podcast_jobs():
    """Podcast-Phrasen tragen ihre Betonung seit stress_podcast.py selbst; gesprochen wird speech_text()."""
    import stress_podcast as sp
    pod = json.loads((ASSETS / "podcast.json").read_text())
    jobs = []
    for d in pod["days"]:
        for it in d["items"]:
            text = sp.speech_text(it, MODEL_DIR)
            jobs.append((f"{it['id']}_m", text, g.VOICE["m"]))
            jobs.append((f"{it['id']}_f", text, g.VOICE["f"]))
    return jobs


def pattern_jobs():
    """Szenen/Kernsätze wie generate_patterns.py — plus jede Kombination der Bausteine."""
    import generate_patterns as gp
    jobs = [(name, text, sid) for name, (text, sid) in gp.clips().items()]
    seen = {name for name, _, _ in jobs}
    for p in gp.PATTERNS:
        slots = p.get("slots", [])
        for combo in itertools.product(*[range(len(s["options"])) for s in slots]):
            for female in (False, True):
                ru = p["template"]["ru"]
                uses_f = False
                for i, oi in enumerate(combo):
                    o = slots[i]["options"][oi]
                    if female and o.get("ruF"):
                        uses_f = True
                    ru = ru.replace("{%d}" % i, o["ruF"] if female and o.get("ruF") else o["ru"])
                if female and not uses_f:
                    continue
                name = clip_name(ru)
                if name not in seen:
                    seen.add(name)
                    jobs.append((name, ru, g.VOICE["f" if female else "m"]))
    return jobs


def deepdive_jobs():
    import generate_deepdive as gd
    return [(clip_name(ru), ru, g.VOICE["f" if fem else "m"]) for ru, fem in gd.all_strings().items()]


def all_jobs(only):
    jobs = []
    if only in (None, "deepdive"):
        jobs += deepdive_jobs()
    if only in (None, "course"):
        jobs += course_jobs()
    if only in (None, "podcast"):
        jobs += podcast_jobs()
    if only in (None, "patterns"):
        jobs += pattern_jobs()
    seen, uniq = set(), []
    for j in jobs:
        if j[0] not in seen:
            seen.add(j[0])
            uniq.append(j)
    return [(n, t, s, 1.0) for n, t, s in uniq] + [(n + "_slow", t, s, g.SLOW_RATE) for n, t, s in uniq]


def is_stale(job, manifest):
    """Clip fehlt — oder sein Text wurde seit der Erzeugung geändert (nur bei ID-benannten Clips prüfbar)."""
    name, text, sid, rate = job
    if not (g.OUT / f"{name}.ogg").exists():
        return True
    # Nur der TEXT zählt: einzelne Clips wurden von Hand mit einer anderen Stimme nachgebessert
    # (fix_clips.py) und tragen deshalb einen anderen Hash — die bleiben, wie sie sind.
    entry = manifest.get(name)
    return bool(entry) and entry["text"] != text


def prune():
    used = {j[0] for j in all_jobs(None)}
    gone = [f for f in g.OUT.glob("dd_*.ogg") if f.stem not in used]
    for f in gone:
        f.unlink()
    print(f"{len(gone)} verwaiste dd_-Clips gelöscht")


def clip_hash(text, sid, rate):
    return hashlib.sha1(f"{g.MODEL_TAG}|{sid}|{rate}|{text}".encode()).hexdigest()[:16]


def finalize():
    """manifest.json um alle vorhandenen Clips ergänzen; bestehende Einträge bleiben unverändert."""
    path = g.OUT / "manifest.json"
    manifest = json.loads(path.read_text())
    added = 0
    for name, text, sid, rate in all_jobs(None):
        if not (g.OUT / f"{name}.ogg").exists() or name.startswith("dd_"):
            continue  # dd_-Clips tragen den Text-Hash schon im Namen
        if manifest.get(name, {}).get("text") != text:
            manifest[name] = {"text": text, "speaker": sid, "rate": rate, "hash": clip_hash(text, sid, rate)}
            added += 1
    path.write_text(json.dumps(manifest, ensure_ascii=False, indent=1, sort_keys=True))
    print(f"manifest.json: {added} Einträge ergänzt, {len(manifest)} gesamt")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--model")
    ap.add_argument("--only", choices=["course", "podcast", "patterns", "deepdive"])
    ap.add_argument("--best-of", type=int, default=3)
    ap.add_argument("--shard", default="0/1", help="i/n — verteilt die Arbeit auf n parallele Läufe")
    ap.add_argument("--limit", type=int, default=0)
    ap.add_argument("--list", action="store_true")
    ap.add_argument("--finalize", action="store_true")
    ap.add_argument("--prune", action="store_true")
    ap.add_argument("--fast-judge", action="store_true",
                    help="Hörprüfung mit whisper-large-v3-turbo (rund dreimal schneller; für große Mengen)")
    args = ap.parse_args()
    if args.fast_judge:
        import qa
        qa.WHISPER = "mlx-community/whisper-large-v3-turbo"

    if args.finalize:
        return finalize()
    if args.prune:
        return prune()
    jobs = all_jobs(args.only)
    manifest = json.loads((g.OUT / "manifest.json").read_text())
    todo = [j for j in jobs if is_stale(j, manifest)]
    si, sn = (int(x) for x in args.shard.split("/"))
    todo = [j for k, j in enumerate(todo) if k % sn == si]
    if args.limit:
        todo = todo[: args.limit]
    print(f"{len(jobs)} Clips in der Quelle, {len(todo)} fehlen (Anteil {args.shard})", flush=True)
    if args.list or not todo:
        return 0

    from vosk_tts import Model, Synth
    import soundfile as sf
    model_dir = pathlib.Path(args.model).expanduser()
    model = Model(model_path=str(model_dir))
    synth = Synth(model)
    report = {"no_dict_match": {}, "dict_disagrees": {}}
    tts_text = g.make_preparer(model, model_dir, report)
    judge = g.make_judge() if args.best_of > 1 else None
    stats = {"first_try": 0, "retried_ok": 0, "unresolved": []}
    t0 = time.time()
    done = {}
    with tempfile.TemporaryDirectory() as tmp:
        for i, (name, text, sid, rate) in enumerate(todo):
            prepared = tts_text(speakable(text))
            best = None
            attempt = 0
            for attempt in range(args.best_of if judge else 1):
                audio = synth.synth_audio(prepared, speaker_id=sid, speech_rate=rate)
                wav = pathlib.Path(tmp) / "x.wav"
                cand = pathlib.Path(tmp) / f"c{attempt}.ogg"
                sf.write(wav, audio, 22050)
                g.encode(wav, cand)
                if not judge:
                    best = (None, cand.read_bytes())
                    break
                score = judge(cand, speakable(text))
                if best is None or score > best[0]:
                    best = (score, cand.read_bytes())
                if score[0] and score[1]:
                    break
            (g.OUT / f"{name}.ogg").write_bytes(best[1])
            done[name] = {"text": text, "speaker": sid, "rate": rate, "hash": clip_hash(text, sid, rate)}
            if judge:
                ok = best[0][0] and best[0][1]
                if ok and attempt == 0:
                    stats["first_try"] += 1
                elif ok:
                    stats["retried_ok"] += 1
                else:
                    stats["unresolved"].append({"name": name, "text": text, "whisper_ok": bool(best[0][0]), "o_ok": bool(best[0][1])})
            if i % 50 == 0:
                rate_s = (time.time() - t0) / (i + 1)
                print(f"{i}/{len(todo)} {name}  {rate_s:.1f} s/Clip  Rest ca. {rate_s * (len(todo) - i) / 60:.0f} Min.", flush=True)

    # Neu erzeugte ID-Clips sofort im Manifest vermerken (sonst gälten ersetzte Clips weiter als veraltet).
    manifest = json.loads((g.OUT / "manifest.json").read_text())
    manifest.update({n: e for n, e in done.items() if not n.startswith("dd_")})
    (g.OUT / "manifest.json").write_text(json.dumps(manifest, ensure_ascii=False, indent=1, sort_keys=True))
    report["best_of"] = stats
    tag = f"{args.only or 'all'}_{si}of{sn}"
    (HERE / f"missing_report_{tag}.json").write_text(json.dumps(report, ensure_ascii=False, indent=1, sort_keys=True))
    print(f"fertig: {len(todo)} Clips in {(time.time() - t0) / 60:.1f} Min. — {stats['first_try']} beim ersten Mal gut, "
          f"{stats['retried_ok']} nach Neuversuch, {len(stats['unresolved'])} ungelöst; "
          f"ohne Wörterbuch-Treffer: {len(report['no_dict_match'])}, Betonung weicht ab: {len(report['dict_disagrees'])}", flush=True)
    return 0


if __name__ == "__main__":
    sys.exit(main())
