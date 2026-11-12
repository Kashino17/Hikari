#!/usr/bin/env python3
"""Ansagen des Blind-Coachs in Rems Stimme erzeugen (Qwen3-TTS, Stimmklon).

Rems Stimme kommt aus dem TMS-Terminal-Projekt: dasselbe Modell, dieselbe
Referenzaufnahme (~/.tms-terminal/voice_24k_15s.wav + .txt). Ausführen mit dessen
Python-Umgebung, die mlx_audio enthält:

  node tools/russian_audio/coach_texts.mjs > /tmp/coach_texts.json
  ~/Developer/tms-terminal/server/audio/.venv-tts/bin/python3 \
      tools/russian_audio/gen_coach_rem.py /tmp/coach_texts.json

Schreibt android/app/src/main/assets/russian/coach/<id>.ogg (Opus, mono) und
index.json ({ de-Text: id }). Bereits vorhandene Dateien werden übersprungen —
das Skript lässt sich jederzeit abbrechen und neu starten.
"""
import hashlib
import json
import os
import subprocess
import sys
import tempfile
import time
import wave

os.environ.setdefault("HF_HUB_OFFLINE", "1")

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "../.."))
OUT = os.path.join(ROOT, "android/app/src/main/assets/russian/coach")
MODEL = "mlx-community/Qwen3-TTS-12Hz-1.7B-Base-8bit"
REF = os.path.expanduser("~/.tms-terminal/voice_24k_15s.wav")
REF_TEXT = os.path.expanduser("~/.tms-terminal/voice_24k_15s.txt")


def clip_id(spoken: str) -> str:
    return hashlib.sha1(spoken.encode("utf-8")).hexdigest()[:12]


def main():
    if len(sys.argv) > 2 and sys.argv[2] == "--candidates":
        return candidates(sys.argv[1], sys.argv[3], int(sys.argv[4]))
    items = json.load(open(sys.argv[1], encoding="utf-8"))
    limit = int(sys.argv[2]) if len(sys.argv) > 2 else None
    os.makedirs(OUT, exist_ok=True)

    import numpy as np
    from mlx_audio.tts.utils import load_model

    ref_text = open(REF_TEXT, encoding="utf-8").read().strip()
    model = load_model(MODEL)

    index_file = os.path.join(OUT, "index.json")
    index = json.load(open(index_file, encoding="utf-8")) if os.path.exists(index_file) else {}
    todo = [x for x in items if not os.path.exists(os.path.join(OUT, clip_id(x["spoken"]) + ".ogg"))]
    if limit:
        todo = todo[:limit]
    print(f"{len(items)} Ansagen, {len(todo)} zu erzeugen", flush=True)

    t0 = time.time()
    for n, x in enumerate(todo, 1):
        cid = clip_id(x["spoken"])
        try:
            parts = []
            rate = 24000
            for r in model.generate(text=x["spoken"], ref_audio=REF, ref_text=ref_text, lang_code="german"):
                parts.append(np.array(r.audio))
                rate = int(getattr(r, "sample_rate", rate) or rate)
            audio = np.concatenate(parts)
            secs = len(audio) / rate
            # Ausreißer (Modell redet weiter oder bricht ab) nicht übernehmen.
            words = len(x["spoken"].split())
            if secs < 0.8 or secs > 3.0 + words * 0.9:
                print(f"  ! {secs:.1f}s unplausibel für {words} Wörter: {x['spoken']}", flush=True)
                continue
            pcm = (np.clip(audio, -1.0, 1.0) * 32767.0).astype("<i2").tobytes()
            with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
                with wave.open(tmp.name, "wb") as w:
                    w.setnchannels(1)
                    w.setsampwidth(2)
                    w.setframerate(rate)
                    w.writeframes(pcm)
            subprocess.run(
                ["ffmpeg", "-y", "-loglevel", "error", "-i", tmp.name,
                 "-af", "silenceremove=start_periods=1:start_threshold=-50dB,areverse,silenceremove=start_periods=1:start_threshold=-50dB,areverse",
                 "-ac", "1", "-c:a", "libopus", "-b:a", "28k", os.path.join(OUT, cid + ".ogg")],
                check=True,
            )
            os.unlink(tmp.name)
        except Exception as e:  # noqa: BLE001 — eine kaputte Ansage hält den Rest nicht auf
            print(f"  ! Fehler bei {x['spoken']}: {e}", flush=True)
            continue
        index[x["de"]] = cid
        if n % 20 == 0 or n == len(todo):
            json.dump(index, open(index_file, "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
            el = time.time() - t0
            print(f"  {n}/{len(todo)} — {el / n:.1f} s je Ansage, noch ~{el / n * (len(todo) - n) / 60:.0f} min", flush=True)

    # Index auch für schon vorhandene Dateien vollständig halten.
    for x in items:
        if os.path.exists(os.path.join(OUT, clip_id(x["spoken"]) + ".ogg")):
            index[x["de"]] = clip_id(x["spoken"])
    json.dump(index, open(index_file, "w", encoding="utf-8"), ensure_ascii=False, separators=(",", ":"))
    print(f"fertig: {len(index)} im Index", flush=True)


def synth(model, ref_text, text, out_path):
    """Ein Text → Opus-Datei. Rückgabe: Dauer in Sekunden."""
    import numpy as np
    parts, rate = [], 24000
    for r in model.generate(text=text, ref_audio=REF, ref_text=ref_text, lang_code="german"):
        parts.append(np.array(r.audio))
        rate = int(getattr(r, "sample_rate", rate) or rate)
    audio = np.concatenate(parts)
    pcm = (np.clip(audio, -1.0, 1.0) * 32767.0).astype("<i2").tobytes()
    with tempfile.NamedTemporaryFile(suffix=".wav", delete=False) as tmp:
        with wave.open(tmp.name, "wb") as w:
            w.setnchannels(1)
            w.setsampwidth(2)
            w.setframerate(rate)
            w.writeframes(pcm)
    subprocess.run(
        ["ffmpeg", "-y", "-loglevel", "error", "-i", tmp.name,
         "-af", "silenceremove=start_periods=1:start_threshold=-50dB,areverse,silenceremove=start_periods=1:start_threshold=-50dB,areverse",
         "-ac", "1", "-c:a", "libopus", "-b:a", "28k", out_path],
        check=True,
    )
    os.unlink(tmp.name)
    return len(audio) / rate


def candidates(list_file, out_dir, n):
    """Für durchgefallene Ansagen je n Varianten erzeugen (<id>_<k>.ogg) — coach_judge.py pick wählt.

    Kurze Ansagen bekommen einen Schlusspunkt: ohne ihn redet das Modell gern weiter
    (»kaufen« → »Kauf nach dem Kaffee«)."""
    from mlx_audio.tts.utils import load_model
    items = json.load(open(list_file, encoding="utf-8"))
    os.makedirs(out_dir, exist_ok=True)
    ref_text = open(REF_TEXT, encoding="utf-8").read().strip()
    model = load_model(MODEL)
    for x in items:
        cid = clip_id(x["spoken"])
        text = x["spoken"] if x["spoken"].rstrip()[-1:] in ".!?…" else x["spoken"].rstrip() + "."
        for k in range(n):
            try:
                synth(model, ref_text, text, os.path.join(out_dir, f"{cid}_{k}.ogg"))
            except Exception as e:  # noqa: BLE001
                print(f"  ! {x['spoken']}: {e}", flush=True)
        print(f"  {x['spoken']}", flush=True)


if __name__ == "__main__":
    main()
