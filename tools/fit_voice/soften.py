#!/usr/bin/env python3
"""Rems Sätze weich ausklingen lassen.

Das Sprachmodell (Qwen3-TTS) hört oft mitten im letzten Laut auf: gemessen enden viele Clips im
letzten 20-ms-Fenster noch bei −9…−15 dB unter der Spitze — das klingt wie ein harter Schnitt
(»papp«). Hier wird jeder Clip am Ende verlängert, der Satz selbst bleibt unangetastet:

- stimmhaftes Ende (Vokal, m, n, l): die letzte Stimmperiode wird periodisch fortgesetzt und klingt
  in ~170 ms aus (exponentiell, zum Schluss etwas dumpfer) — wie ein natürlich auslaufender Laut;
- stimmloses Ende (s, sch, t, Rauschen): weiche Blende über die letzten 45 ms;
- danach 220 ms Stille (Ansagen setzen nicht mehr »auf Kante« an die nächste).

Bereits behandelte Clips (Stille am Ende) werden übersprungen.

  ~/.hikari/tts/ttsenv/bin/python3 tools/fit_voice/soften.py            # alle Clips in fit-web/voice
  ~/.hikari/tts/ttsenv/bin/python3 tools/fit_voice/soften.py a.ogg b.ogg --out <ordner>   # Probe
"""
import os, subprocess, sys
import numpy as np

SR = 24000
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "../.."))
VOICE = os.path.join(ROOT, "android/app/src/main/assets/fit-web/voice")


def decode(path):
    raw = subprocess.run(["ffmpeg", "-nostdin", "-v", "error", "-i", path, "-ac", "1", "-ar", str(SR), "-f", "f32le", "-"],
                         capture_output=True, check=True).stdout
    return np.frombuffer(raw, dtype=np.float32).astype(np.float64)


def encode(x, path):
    subprocess.run(["ffmpeg", "-nostdin", "-v", "error", "-y", "-f", "f64le", "-ar", str(SR), "-ac", "1", "-i", "-",
                    "-c:a", "libopus", "-b:a", "40k", "-application", "audio", path],
                   input=np.clip(x, -1, 1).astype(np.float64).tobytes(), check=True)


def rms_db(x):
    return 20 * np.log10(np.sqrt(np.mean(x ** 2)) + 1e-9)


def already_soft(x):
    tail = x[-int(0.15 * SR):]
    return len(x) > SR * 0.3 and rms_db(tail) - rms_db(x) < -45


def period(seg):
    """Grundperiode (Samples) über Autokorrelation, 70–400 Hz; None = stimmlos."""
    seg = seg - seg.mean()
    if rms_db(seg) < -60:
        return None
    ac = np.correlate(seg, seg, "full")[len(seg) - 1:]
    lo, hi = SR // 400, SR // 70
    if hi >= len(ac):
        return None
    t = lo + int(np.argmax(ac[lo:hi]))
    return t if ac[t] / (ac[0] + 1e-12) > 0.55 else None


def soften(x):
    x = x.copy()
    end_lvl = rms_db(x[-int(0.02 * SR):]) - rms_db(x)  # wie laut endet der Clip (relativ)?
    t = period(x[-int(0.06 * SR):])
    tail_n = int(0.17 * SR)
    if t is not None and end_lvl > -32:
        # Letzte Periode fortsetzen. Eine kleine Überblendung (eine Periode) glättet die Naht.
        cyc = x[-t:]
        reps = int(np.ceil(tail_n / t)) + 1
        ext = np.tile(cyc, reps)[:tail_n]
        env = np.exp(-np.arange(tail_n) / (0.038 * SR))           # ~−45 dB nach 170 ms
        env *= 0.5 * (1 + np.cos(np.pi * np.arange(tail_n) / tail_n)) ** 0.25
        ext *= env
        # zum Ende hin dumpfer: einfacher Tiefpass mit wachsendem Glättungsfaktor
        out = np.empty_like(ext)
        y = ext[0]
        for i, v in enumerate(ext):
            a = 0.85 - 0.6 * i / tail_n
            y = a * v + (1 - a) * y
            out[i] = y
        x = np.concatenate([x, out])
    else:
        # Stimmlos oder schon leise: weiche Blende über die letzten 45 ms.
        n = min(len(x) // 4, int(0.045 * SR))
        x[-n:] *= 0.5 * (1 + np.cos(np.pi * np.arange(n) / n))
    # Letzte Millisekunden sicher auf null, dann Stille.
    k = int(0.004 * SR)
    x[-k:] *= np.linspace(1, 0, k)
    return np.concatenate([x, np.zeros(int(0.22 * SR))])


def main():
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    out_dir = sys.argv[sys.argv.index("--out") + 1] if "--out" in sys.argv else None
    if out_dir:
        args = [a for a in args if a != out_dir]
    files = args or sorted(os.path.join(VOICE, f) for f in os.listdir(VOICE) if f.endswith(".ogg"))
    done = skipped = 0
    for f in files:
        x = decode(f)
        if already_soft(x):
            skipped += 1
            continue
        y = soften(x)
        dst = os.path.join(out_dir, os.path.basename(f)) if out_dir else f
        tmp = dst + ".tmp.ogg"
        encode(y, tmp)
        os.replace(tmp, dst)
        done += 1
    print(f"weich gemacht: {done}, schon weich: {skipped}")


if __name__ == "__main__":
    main()
