# /// script
# requires-python = ">=3.11,<3.14"
# dependencies = ["mlx-whisper"]
# ///
"""Whisper-Worker für Hikari-Sprachnachrichten.

Läuft als Kindprozess des Servers (gestartet per `uv run --script`) und spricht
zeilenweise JSON über stdin/stdout:

  -> {"id": "1", "path": "/pfad/zur/datei.opus", "language": null}
  <- {"id": "1", "text": "...", "duration": 123.4, "language": "de"}
  <- {"id": "1", "error": "..."}

Das Modell wird beim ersten Auftrag geladen und bleibt im Speicher, solange der
Prozess lebt. Entladen = Prozess beenden; das übernimmt der Server nach
Leerlauf (voice/whisper.ts). stdout gehört allein dem Protokoll, alles andere
geht nach stderr.
"""

import json
import os
import sys

MODEL = os.environ.get("HIKARI_WHISPER_MODEL", "mlx-community/whisper-large-v3-turbo")


def main() -> None:
    import mlx_whisper
    from mlx_whisper.audio import SAMPLE_RATE, load_audio

    print(json.dumps({"ready": True, "model": MODEL}), flush=True)
    for line in sys.stdin:
        line = line.strip()
        if not line:
            continue
        req_id = None
        try:
            req = json.loads(line)
            req_id = req.get("id")
            audio = load_audio(req["path"])
            duration = len(audio) / SAMPLE_RATE
            if duration < 0.3:
                out = {"text": "", "duration": duration, "language": None}
            else:
                result = mlx_whisper.transcribe(
                    audio,
                    path_or_hf_repo=MODEL,
                    language=req.get("language") or None,
                    # Ohne Rückkopplung verfängt sich Whisper in langen Aufnahmen
                    # nicht in Wiederholungsschleifen.
                    condition_on_previous_text=False,
                    verbose=None,
                )
                out = {
                    "text": result.get("text", "").strip(),
                    "duration": duration,
                    "language": result.get("language"),
                }
        except Exception as exc:  # noqa: BLE001 — jeder Fehler geht als Antwort zurück
            out = {"error": f"{type(exc).__name__}: {exc}"[:500]}
        out["id"] = req_id
        print(json.dumps(out, ensure_ascii=False), flush=True)


if __name__ == "__main__":
    main()
