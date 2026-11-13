"""Google Gemini als Prüfinstanz für »Lieder & Szenen« (kostenlose Stufe, Schlüssel in ~/.hikari/erleben/keys.env).

Vier Aufgaben, jede mit dem Modell, das dafür gemessen wurde, und jede mit eigenem Tageskontingent
(abgelesen am 14.11.2026 in AI Studio, Projekt auf »Kostenlose Stufe«):

  hear()       gemini-3.5-transcribe   3/min,  25/Tag   zweites Ohr neben Whisper
  review()     gemini-4-hydrogen       5/min,  20/Tag   Übersetzung, Bedeutung im Satz, Kurzbeschreibung
  second()     gemma-4-31b-it         30/min, 14.400/Tag unabhängige zweite Übersetzung
  same()       gemini-3.5-flash-lite  15/min, 500/Tag   sagen beide Übersetzungen dasselbe?

Der Zähler in usage.json bleibt unter den Tageslimits; zwischen zwei Anfragen an dasselbe Modell
wird gewartet, damit das Minutenlimit nicht reißt. Ist ein Kontingent leer, hört die Aufbereitung
für heute auf — ungeprüft wird nichts fertiggestellt.
"""
import base64
import hashlib
import json
import time
import urllib.error
import urllib.request
from datetime import date
from pathlib import Path

from cloud import HOME, USAGE, load_keys

API = "https://generativelanguage.googleapis.com/v1beta/models"
# Modell → (Anfragen je Minute, Anfragen je Tag, die wir uns erlauben)
LIMITS = {
    "gemini-3.5-transcribe": (3, 22),
    "gemini-4-hydrogen": (5, 18),
    "gemma-4-31b-it": (20, 2000),
    "gemini-3.5-flash-lite": (12, 400),
}
HEAR, REVIEW, SECOND, JUDGE = LIMITS
# Jede Antwort wird gemerkt: dieselbe Frage kostet beim zweiten Mal kein Kontingent.
CACHE = HOME / "work" / "gemini"


class QuotaReached(RuntimeError):
    """Tageskontingent eines Modells ist aufgebraucht."""


class Gemini:
    def __init__(self, log=lambda *a: None):
        self.key = load_keys().get("GEMINI_API_KEY", "")
        if not self.key:
            raise RuntimeError("GEMINI_API_KEY fehlt in ~/.hikari/erleben/keys.env")
        self.log = log
        self.last: dict[str, float] = {}

    # ── Kontingent ───────────────────────────────────────────────────────────

    def _usage(self) -> dict:
        try:
            u = json.loads(USAGE.read_text())
        except Exception:  # noqa: BLE001
            u = {}
        return u if u.get("day") == str(date.today()) else {"day": str(date.today()), "count": {}}

    def left(self, model: str) -> int:
        return LIMITS[model][1] - self._usage()["count"].get(model, 0)

    def _count(self, model: str):
        u = self._usage()
        u["count"][model] = u["count"].get(model, 0) + 1
        HOME.mkdir(parents=True, exist_ok=True)
        USAGE.write_text(json.dumps(u))

    # ── Anfrage ──────────────────────────────────────────────────────────────

    def call(self, model: str, parts: list[dict], schema: dict | None = None, system: str | None = None, max_tokens: int = 8000) -> dict:
        body_key = hashlib.sha1(json.dumps([model, parts, schema, system, max_tokens], sort_keys=True).encode()).hexdigest()
        cached = CACHE / f"{body_key}.json"
        if cached.exists():
            return json.loads(cached.read_text())
        if self.left(model) <= 0:
            raise QuotaReached(model)
        gap = 60.0 / LIMITS[model][0] + 0.5
        wait = self.last.get(model, 0) + gap - time.time()
        if wait > 0:
            time.sleep(wait)
        config: dict = {"temperature": 0, "maxOutputTokens": max_tokens}
        if schema:
            config.update(responseMimeType="application/json", responseSchema=schema)
        body: dict = {"contents": [{"role": "user", "parts": parts}], "generationConfig": config}
        if system:
            body["systemInstruction"] = {"parts": [{"text": system}]}
        req = urllib.request.Request(f"{API}/{model}:generateContent", data=json.dumps(body).encode(),
                                     headers={"content-type": "application/json", "x-goog-api-key": self.key})
        for attempt in range(3):
            self.last[model] = time.time()
            try:
                with urllib.request.urlopen(req, timeout=240) as r:
                    data = json.loads(r.read())
                self._count(model)
                if (data.get("candidates") or [{}])[0].get("finishReason") in (None, "STOP"):
                    CACHE.mkdir(parents=True, exist_ok=True)
                    cached.write_text(json.dumps(data))
                return data
            except urllib.error.HTTPError as e:
                detail = e.read().decode(errors="ignore")[:300]
                if e.code == 429 and "PerDay" in detail:
                    raise QuotaReached(model) from e
                if e.code in (429, 500, 503) and attempt < 2:
                    self.log(f"{model}: HTTP {e.code}, neuer Versuch in {20 * (attempt + 1)} s")
                    time.sleep(20 * (attempt + 1))
                    continue
                raise RuntimeError(f"{model}: HTTP {e.code} {detail}") from e
        raise RuntimeError(f"{model}: keine Antwort")

    @staticmethod
    def text(data: dict) -> str:
        parts = (data.get("candidates") or [{}])[0].get("content", {}).get("parts", [])
        return "".join(p.get("text") or p.get("audioTranscription", {}).get("text", "") for p in parts if not p.get("thought")).strip()

    # ── Die vier Aufgaben ────────────────────────────────────────────────────

    def hear(self, audio: Path) -> str:
        """Was wird gesagt oder gesungen? Reines Hören, ohne Whispers Text zu kennen."""
        data = self.call(HEAR, [{"text": "Transcribe the Russian speech or singing exactly as spoken. Russian text only."},
                                {"inline_data": {"mime_type": "audio/mp3", "data": base64.b64encode(audio.read_bytes()).decode()}}], max_tokens=2000)
        return self.text(data)

    def review(self, kind: str, title: str, lines: list[dict]) -> dict | None:
        """lines: [{n, ru, words: [{w, options: [str]}]}] — nur Wörter mit mehreren Wörterbuch-Bedeutungen.
        → {"lines": [{"n", "de", "senses": [int]}], "story": str, "title_de": str} oder None, wenn die Antwort nicht passt."""
        rows = []
        for l in lines:
            row = [f"LINE {l['n']}: {l['ru']}"]
            for k, w in enumerate(l["words"], 1):
                row.append(f"  W{k} {w['w']}: " + "  ".join(f"{i}) {o}" for i, o in enumerate(w["options"], 1)))
            rows.append("\n".join(row))
        system = (
            f"You prepare a Russian {'song' if kind == 'song' else 'film or cartoon scene'} titled »{title}« for a German beginner who learns Russian from it. "
            "Whatever you write will be learned by heart, so it must be right.\n\n"
            "For every LINE give:\n"
            "- de: a faithful, natural German translation of exactly this line. Do not add or leave out anything. Keep names. Informal ты is »du«, polite вы is »Sie«. Use the other lines as context.\n"
            "- stress: the same Russian line, letters unchanged, with an acute accent (U+0301) directly after the stressed vowel of every word that has two or more syllables. Do not mark words with one syllable or words with ё.\n"
            "- senses: for each listed word W1, W2, … of that line (in this order) the number of the dictionary meaning that fits the word IN THIS LINE. Use 0 if none of the listed meanings fits. If a line lists no words, senses is empty.\n\n"
            "story: one or two plain German sentences saying what happens in these lines, for someone about to watch. Only what the lines say. No opinion, no praise.\n"
            "title_de: the title in German (keep names as they are)."
        )
        schema = {"type": "OBJECT", "required": ["lines", "story", "title_de"], "properties": {
            "story": {"type": "STRING"}, "title_de": {"type": "STRING"},
            "lines": {"type": "ARRAY", "items": {"type": "OBJECT", "required": ["n", "de", "stress", "senses"], "properties": {
                "n": {"type": "INTEGER"}, "de": {"type": "STRING"}, "stress": {"type": "STRING"}, "senses": {"type": "ARRAY", "items": {"type": "INTEGER"}}}}}}}
        try:
            out = json.loads(self.text(self.call(REVIEW, [{"text": "\n\n".join(rows)}], schema=schema, system=system, max_tokens=12000)))
        except (json.JSONDecodeError, KeyError):
            return None
        got = {l.get("n"): l for l in out.get("lines", [])}
        for l in lines:
            g = got.get(l["n"])
            if not g or not str(g.get("de", "")).strip() or len(g.get("senses", [])) != len(l["words"]):
                return None
            if any(not isinstance(s, int) or s < 0 or s > len(w["options"]) for s, w in zip(g["senses"], l["words"])):
                return None
        return {"lines": [got[l["n"]] for l in lines], "story": str(out.get("story", "")).strip(), "title_de": str(out.get("title_de", "")).strip()}

    def second(self, kind: str, lines: list[str]) -> list[str] | None:
        """Unabhängige zweite Übersetzung von einem anderen Modell."""
        prompt = (f"Translate these Russian lines from a {'song' if kind == 'song' else 'film scene'} into German, faithfully, line by line. "
                  "Informal ты is »du«, polite вы is »Sie«. Answer with a JSON array of strings, one German line per Russian line, same order, nothing else.\n\n"
                  + "\n".join(f"{i + 1}. {l}" for i, l in enumerate(lines)))
        raw = self.text(self.call(SECOND, [{"text": prompt}], max_tokens=4000))
        start, end = raw.find("["), raw.rfind("]")
        try:
            arr = json.loads(raw[start:end + 1])
        except (json.JSONDecodeError, ValueError):
            return None
        return [str(x) for x in arr] if isinstance(arr, list) and len(arr) == len(lines) else None

    def same(self, triples: list[tuple[str, str, str]]) -> list[bool] | None:
        """(Russisch, Übersetzung A, Übersetzung B) → sagen A und B dasselbe?"""
        system = ("For each item you get a Russian line and two German translations A and B. Decide whether A and B say the same thing. "
                  "Differences in wording, word order, articles, tense nuance or style do not matter. They differ only if the meaning differs: "
                  "another fact, another person (du instead of Sie counts), another action, something missing or added. Answer for every item.")
        schema = {"type": "ARRAY", "items": {"type": "OBJECT", "required": ["n", "same"], "properties": {"n": {"type": "INTEGER"}, "same": {"type": "BOOLEAN"}}}}
        user = "\n\n".join(f"{i + 1}.\nRUSSIAN: {ru}\nA: {a}\nB: {b}" for i, (ru, a, b) in enumerate(triples))
        try:
            arr = json.loads(self.text(self.call(JUDGE, [{"text": user}], schema=schema, system=system, max_tokens=3000)))
        except (json.JSONDecodeError, KeyError):
            return None
        got = {x.get("n"): bool(x.get("same")) for x in arr if isinstance(x, dict)}
        return [got[i + 1] for i in range(len(triples))] if sorted(got) == list(range(1, len(triples) + 1)) else None

    def queries(self, topic: str, music: bool) -> list[str]:
        """Aus einem Thema des Lernenden (»One Piece«, »True Crime«, »Rock«) werden russische YouTube-Suchanfragen."""
        what = ("Russian-language songs (sung in Russian, clear vocals, official videos or live performances)" if music
                else "videos in spoken Russian (original Russian content, or the Russian dub if the topic is foreign, e.g. anime); clear speech, short videos or single scenes rather than hour-long streams")
        prompt = (f"A German beginner learns Russian by watching YouTube. Their interest: »{topic}«. "
                  f"Give 4 YouTube search queries, written in Russian, that find {what} about this interest. "
                  "Vary them (different angles of the interest). Answer with a JSON array of 4 strings, nothing else.")
        schema = {"type": "ARRAY", "items": {"type": "STRING"}}
        try:
            arr = json.loads(self.text(self.call(JUDGE, [{"text": prompt}], schema=schema, max_tokens=600)))
        except (json.JSONDecodeError, KeyError):
            return []
        return [str(q).strip() for q in arr if str(q).strip()][:4] if isinstance(arr, list) else []
