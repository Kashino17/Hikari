#!/usr/bin/env python3
"""Lädt die Übungsvideos einmal herunter (privater Gebrauch) und schreibt backend/fitness/media.json.

Quellen (Recherche 19.11.2026):
  sworkit:<slug>   — Schleife, eine Person vor weißem Hintergrund (Seite sworkit.com/exercise/<slug>)
  giphy:<id>       — Freeletics-Schleifen aus deren Technik-Artikeln
  edb:<id>         — ExerciseDB OSS (AscendAPI), 3D-Figur, nur 180 px → für Skills ohne bessere Quelle
  yt:<id>@<start>-<end> — Ausschnitt aus einem Tutorial (yt-dlp, h264)

Ziel: ~/.hikari/fitness/media/<übung>.mp4 (+ .jpg Standbild). Aufruf:
  python3 backend/fitness/tools/fetch_media.py [--only id1,id2] [--force]
"""
import json
import os
import re
import subprocess
import sys
import time
import urllib.request
from pathlib import Path

HERE = Path(__file__).resolve().parent
SOURCES = json.loads((HERE / "media_sources.json").read_text())
OUT = Path(os.path.expanduser("~/.hikari/fitness/media"))
OUT.mkdir(parents=True, exist_ok=True)
UA = {"User-Agent": "Hikari-Fitness/1.0 (private)"}


def get(url, binary=False):
    req = urllib.request.Request(url, headers=UA)
    with urllib.request.urlopen(req, timeout=60) as r:
        data = r.read()
    return data if binary else data.decode("utf-8", "replace")


def ffmpeg(*args):
    subprocess.run(["ffmpeg", "-y", "-loglevel", "error", *args], check=True)


def poster(mp4: Path, jpg: Path):
    ffmpeg("-ss", "0.6", "-i", str(mp4), "-frames:v", "1", "-vf", "scale=480:-2", "-q:v", "4", str(jpg))


def normalize(src: Path, dst: Path, crop: str | None = None, width=640):
    """h264, ohne Ton, ≤ 30 fps, schnelle Startfähigkeit (faststart)."""
    vf = []
    if crop:
        vf.append(crop)
    vf.append(f"scale='min({width},iw)':-2:flags=lanczos")
    vf.append("fps=30")
    ffmpeg("-i", str(src), "-an", "-vf", ",".join(vf), "-c:v", "libx264", "-profile:v", "main", "-pix_fmt", "yuv420p",
           "-preset", "slow", "-crf", "26", "-movflags", "+faststart", str(dst))


def fetch(ex_id: str, src: str, force=False):
    mp4 = OUT / f"{ex_id}.mp4"
    jpg = OUT / f"{ex_id}.jpg"
    if mp4.exists() and jpg.exists() and not force:
        return {"video": f"{ex_id}.mp4", "image": f"{ex_id}.jpg", "credit": credit(src)}
    tmp = OUT / f".{ex_id}.src"
    kind, _, ref = src.partition(":")
    if kind == "sworkit":
        html = get(f"https://sworkit.com/exercise/{ref}")
        m = re.search(r"https://storage\.googleapis\.com/sworkit-assets/video/ios/[^\"'\s]+\.mp4", html)
        if not m:
            raise RuntimeError(f"kein Video auf der Seite {ref}")
        tmp.write_bytes(get(m.group(0), binary=True))
        normalize(tmp, mp4)
    elif kind == "giphy":
        tmp.write_bytes(get(f"https://media.giphy.com/media/{ref}/giphy.mp4", binary=True))
        normalize(tmp, mp4)
    elif kind == "edb":
        tmp.write_bytes(get(f"https://static.exercisedb.dev/media/{ref}.gif", binary=True))
        normalize(tmp, mp4, width=360)
        time.sleep(7)  # ExerciseDB drosselt nach ~10 schnellen Abrufen
    elif kind == "yt":
        vid, _, span = ref.partition("@")
        start, _, end = span.partition("-")
        raw = OUT / f".{ex_id}.yt.mp4"
        subprocess.run(["yt-dlp", "-4", "-q", "--no-warnings", "-f", "bv*[height<=720][vcodec^=avc1]/bv*[height<=720]",
                        "--download-sections", f"*{start}-{end}", "--force-keyframes-at-cuts",
                        "-o", str(raw), f"https://www.youtube.com/watch?v={vid}"], check=True)
        normalize(raw, mp4, width=720)
        raw.unlink(missing_ok=True)
    else:
        raise RuntimeError(f"unbekannte Quelle {src}")
    tmp.unlink(missing_ok=True)
    poster(mp4, jpg)
    return {"video": f"{ex_id}.mp4", "image": f"{ex_id}.jpg", "credit": credit(src)}


def credit(src: str) -> str:
    kind = src.split(":")[0]
    return {"sworkit": "Sworkit", "giphy": "Freeletics", "edb": "ExerciseDB (AscendAPI)", "yt": "YouTube"}.get(kind, kind)


def main():
    only = None
    force = "--force" in sys.argv
    if "--only" in sys.argv:
        only = set(sys.argv[sys.argv.index("--only") + 1].split(","))
    out_file = HERE.parent / "media.json"
    result = json.loads(out_file.read_text()).get("media", {}) if out_file.exists() else {}
    for ex_id, src in SOURCES.items():
        if ex_id.startswith("_") or (only and ex_id not in only):
            continue
        try:
            result[ex_id] = fetch(ex_id, src, force)
            print(f"ok   {ex_id:28} {src}")
        except Exception as e:  # weiter mit der nächsten Übung
            print(f"FEHL {ex_id:28} {src}: {e}")
    out_file.write_text(json.dumps({"media": dict(sorted(result.items()))}, indent=1, ensure_ascii=False) + "\n")


if __name__ == "__main__":
    main()
