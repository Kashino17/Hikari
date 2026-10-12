"""
Kleiner lokaler Phonem-Erkenner für die Aussprachebewertung (Hikari).

wav2vec2-xlsr-53-espeak-cv-ft liefert aus Sprache eine IPA-Phonemfolge. Der
Hikari-Server (backend/src/api/russian.ts) schickt 16-kHz-Mono-WAV hierher und
vergleicht die Folge des Lernenden mit der des Muttersprachler-Clips.

  POST /phonemes   Body: WAV-Bytes  →  {"phonemes": ["m", "ɨ", ...]}
  GET  /health     →  {"ok": true}
"""
import io
import json
import sys
from http.server import BaseHTTPRequestHandler, HTTPServer

import numpy as np
import soundfile as sf
import torch
from transformers import AutoModelForCTC, AutoProcessor

NAME = "facebook/wav2vec2-xlsr-53-espeak-cv-ft"
PORT = int(sys.argv[1]) if len(sys.argv) > 1 else 3940

proc = AutoProcessor.from_pretrained(NAME)
model = AutoModelForCTC.from_pretrained(NAME).eval()
print("modell bereit", flush=True)


def phonemes(wav_bytes: bytes):
    a, sr = sf.read(io.BytesIO(wav_bytes), dtype="float32")
    if a.ndim > 1:
        a = a.mean(1)
    if sr != 16000:
        raise ValueError(f"erwarte 16 kHz, bekam {sr}")
    if len(a) < 1600:
        return []
    a = (a - a.mean()) / (a.std() + 1e-7)
    x = proc(a, sampling_rate=16000, return_tensors="pt").input_values
    with torch.no_grad():
        ids = model(x).logits.argmax(-1)
    return proc.batch_decode(ids)[0].split()


class Handler(BaseHTTPRequestHandler):
    def _json(self, code, obj):
        body = json.dumps(obj, ensure_ascii=False).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(body)))
        self.end_headers()
        self.wfile.write(body)

    def do_GET(self):
        self._json(200, {"ok": True}) if self.path == "/health" else self._json(404, {"error": "not found"})

    def do_POST(self):
        if self.path != "/phonemes":
            return self._json(404, {"error": "not found"})
        n = int(self.headers.get("Content-Length", "0"))
        try:
            self._json(200, {"phonemes": phonemes(self.rfile.read(n))})
        except Exception as e:  # noqa: BLE001
            self._json(400, {"error": str(e)})

    def log_message(self, *args):
        pass


if __name__ == "__main__":
    HTTPServer(("127.0.0.1", PORT), Handler).serve_forever()
