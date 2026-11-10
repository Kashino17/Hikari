"""
Kursstimme auf Abruf — für Sätze, die erst beim Üben entstehen (Kombi-Meister, Gespräch mit Anja,
eigene Sätze). Dieselben Stimmen und dieselbe Betonungslogik wie generate.py.

Der Hikari-Server startet dieses Skript bei Bedarf selbst (backend/src/russian/voice.ts, Port 3941)
und beendet es nach einigen Minuten Leerlauf. Von Hand:

  ~/.hikari/tts/ttsenv/bin/python tts_server.py 3941
  curl -s localhost:3941/health
  curl -s -X POST localhost:3941/speak -d '{"text":"Как дел+а?","voice":"p","rate":1.0}' -o /tmp/x.ogg

Texte ohne "+" bekommen ihre Betonung automatisch: zuerst aus dem Kurs (glosses.json → "st"),
sonst die häufigste Betonung aus dem Aussprachewörterbuch des Sprachmodells.
"""
import json, os, pathlib, re, sys, tempfile, threading
from http.server import BaseHTTPRequestHandler, HTTPServer

HERE = pathlib.Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import generate as g  # noqa: E402
from generate_deepdive import speakable  # noqa: E402

MODEL_DIR = pathlib.Path(os.environ.get("HIKARI_TTS_MODEL", "~/.hikari/tts/models/vosk")).expanduser()
GLOSSES = g.ROOT / "android/app/src/main/assets/russian/glosses.json"
WORD = re.compile(r"[А-Яа-яЁё+]+")
LOCK = threading.Lock()
STATE = {}


def load():
    from vosk_tts import Model, Synth
    model = Model(model_path=str(MODEL_DIR))
    full = g.load_dictionary(MODEL_DIR)
    STATE.update(synth=Synth(model), full=full, prepare=g.make_preparer(model, MODEL_DIR, None, full),
                 st=json.loads(GLOSSES.read_text()).get("st", {}) if GLOSSES.exists() else {})


def auto_stress(text):
    """Fehlende Betonungen ergänzen (Wörter mit "+" oder ё bleiben unangetastet)."""
    st, full = STATE["st"], STATE["full"]

    def rep(m):
        w = m.group(0)
        low = w.lower()
        if "+" in w or "ё" in low or sum(c in g.VOWELS for c in low) < 2:
            return w
        form = st.get(low.replace("ё", "е"))
        if not form:
            cands = [(wt, g.stress_ordinal_of_phones(ph.split())) for wt, ph in full.get(low, [])]
            cands = [c for c in cands if c[1] is not None]
            if not cands:
                return w
            ordinal, k = max(cands)[1], -1
            for i, c in enumerate(low):
                if c in g.VOWELS:
                    k += 1
                    if k == ordinal:
                        return w[:i] + "+" + w[i:]
            return w
        out, i = "", 0
        for c in form:
            if c == "+":
                out += c
                continue
            out += w[i] if i < len(w) else c
            i += 1
        return out if out.replace("+", "").lower() == low else w
    return WORD.sub(rep, text)


def speak(text, voice, rate):
    import soundfile as sf
    with LOCK, tempfile.TemporaryDirectory() as tmp:
        prepared = STATE["prepare"](speakable(auto_stress(text)))
        audio = STATE["synth"].synth_audio(prepared, speaker_id=g.VOICE[voice], speech_rate=rate)
        wav, ogg = pathlib.Path(tmp) / "x.wav", pathlib.Path(tmp) / "x.ogg"
        sf.write(wav, audio, 22050)
        g.encode(wav, ogg)
        return ogg.read_bytes()


class Handler(BaseHTTPRequestHandler):
    def log_message(self, *a):
        pass

    def _json(self, code, body):
        data = json.dumps(body).encode()
        self.send_response(code)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)

    def do_GET(self):
        self._json(200, {"ok": True}) if self.path == "/health" else self._json(404, {"error": "not_found"})

    def do_POST(self):
        if self.path != "/speak":
            return self._json(404, {"error": "not_found"})
        try:
            body = json.loads(self.rfile.read(int(self.headers.get("Content-Length", "0"))) or b"{}")
            text = str(body.get("text", "")).strip()
            voice = body.get("voice", "m")
            rate = min(1.2, max(0.5, float(body.get("rate", 1.0))))
            if not text or len(text) > 400 or not re.search(r"[А-Яа-яЁё]", text) or voice not in g.VOICE:
                return self._json(400, {"error": "bad_request"})
            data = speak(text, voice, rate)
        except Exception as e:  # noqa: BLE001 — der Dienst soll weiterlaufen
            return self._json(500, {"error": "speak_failed", "detail": str(e)[:200]})
        self.send_response(200)
        self.send_header("Content-Type", "audio/ogg")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)


if __name__ == "__main__":
    port = int(sys.argv[1]) if len(sys.argv) > 1 else 3941
    load()  # erst lauschen, wenn das Modell geladen ist — /health heißt dann wirklich »bereit«
    HTTPServer(("127.0.0.1", port), Handler).serve_forever()
