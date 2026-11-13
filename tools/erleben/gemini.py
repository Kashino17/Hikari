"""Google Gemini als Prüfinstanz für »Lieder & Szenen« (kostenlose Stufe, Schlüssel in ~/.hikari/erleben/keys.env).

Vier Aufgaben, jede mit dem Modell, das dafür gemessen wurde, und jede mit eigenem Tageskontingent
(abgelesen am 14.11.2026 in AI Studio, Projekt auf »Kostenlose Stufe«):

  hear()       gemini-3.5-transcribe   3/min,  25/Tag   zweites Ohr neben Whisper
  review()     gemini-4-hydrogen       5/min,  20/Tag   Übersetzung, Bedeutung im Satz, Kurzbeschreibung
               gemini-3.6-flash        5/min,  20/Tag   zweiter Topf, sobald Hydrogen für heute leer ist
                                                        (gemessen: 40/41 richtige durchgelassen, 14/15 Fehler, 10/10)
               gemini-3.8-flash        5/min,  20/Tag   dritter Topf; oft überlastet, dann wird er übersprungen
  confirm()    gemini-3.5-flash-lite                    zweite Meinung zu Wortbedeutungen außerhalb des Wörterbuchs
  second()     gemma-4-31b-it         30/min, 14.400/Tag unabhängige zweite Übersetzung
  faithful()   gemini-3.5-flash-lite  15/min, 500/Tag   gibt die Übersetzung die Zeile richtig wieder? (Gemma als zweite Stimme)

Googles Tag beginnt um Mitternacht Pazifikzeit — der Zähler in usage.json zählt deshalb nach diesem
Kalender, nicht nach der Uhr des Macs. Er bleibt unter den Tageslimits; zwischen zwei Anfragen an dasselbe Modell
wird gewartet, damit das Minutenlimit nicht reißt. Ist ein Kontingent leer, hört die Aufbereitung
für heute auf — ungeprüft wird nichts fertiggestellt.
"""
import base64
import hashlib
import json
import time
import urllib.error
import urllib.request
from datetime import datetime
from pathlib import Path
from zoneinfo import ZoneInfo

from cloud import HOME, USAGE, load_keys

API = "https://generativelanguage.googleapis.com/v1beta/models"
# Modell → (Anfragen je Minute, Anfragen je Tag). Die Tageswerte sind Googles echte Limits: lehnt Google
# vorher ab, gleicht sich der Zähler an (_spent) und das nächste Modell der Kette übernimmt.
LIMITS = {
    "gemini-3.5-transcribe": (3, 25),
    "gemini-4-hydrogen": (5, 20),
    "gemini-3.6-flash": (5, 20),
    "gemini-3.8-flash": (5, 20),    # dritter Topf auf Kadirs Wunsch; ungemessen, weil am 14.11. dauernd überlastet
    "gemma-4-31b-it": (20, 2000),
    "gemini-3.5-flash-lite": (12, 400),
}
HEAR, REVIEW, REVIEW2, REVIEW3, SECOND, JUDGE = LIMITS


def quota_day() -> str:
    """Der Tag, nach dem Google die Tageskontingente zählt."""
    return datetime.now(ZoneInfo("America/Los_Angeles")).strftime("%Y-%m-%d")
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
        return u if u.get("day") == quota_day() else {"day": quota_day(), "count": {}}

    def left(self, model: str) -> int:
        return LIMITS[model][1] - self._usage()["count"].get(model, 0)

    def reviews_left(self) -> int:
        """Wie viele Stücke heute noch geprüft werden können (beide Töpfe zusammen)."""
        return sum(max(0, self.left(m)) for m in (REVIEW, REVIEW2, REVIEW3))

    def _spent(self, model: str):
        """Google meldet das Tageskontingent als leer — den eigenen Zähler angleichen."""
        u = self._usage()
        u["count"][model] = LIMITS[model][1]
        USAGE.write_text(json.dumps(u))

    def _count(self, model: str):
        u = self._usage()
        u["count"][model] = u["count"].get(model, 0) + 1
        HOME.mkdir(parents=True, exist_ok=True)
        USAGE.write_text(json.dumps(u))

    # ── Anfrage ──────────────────────────────────────────────────────────────

    def call(self, model: str, parts: list[dict], schema: dict | None = None, system: str | None = None, max_tokens: int = 8000,
             patient: bool = True) -> dict:
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
        for attempt in range(3 if patient else 1):
            self.last[model] = time.time()
            try:
                with urllib.request.urlopen(req, timeout=240 if patient else 90) as r:
                    data = json.loads(r.read())
                self._count(model)
                if (data.get("candidates") or [{}])[0].get("finishReason") in (None, "STOP"):
                    CACHE.mkdir(parents=True, exist_ok=True)
                    cached.write_text(json.dumps(data))
                return data
            except urllib.error.HTTPError as e:
                detail = e.read().decode(errors="ignore")[:300]
                if e.code == 429 and "PerDay" in detail:
                    self._spent(model)
                    raise QuotaReached(model) from e
                if e.code in (429, 500, 503) and attempt < 2 and patient:
                    self.log(f"{model}: HTTP {e.code}, neuer Versuch in {20 * (attempt + 1)} s")
                    time.sleep(20 * (attempt + 1))
                    continue
                raise RuntimeError(f"{model}: HTTP {e.code} {detail}") from e
            except (TimeoutError, urllib.error.URLError) as e:
                if attempt < 2 and patient:
                    continue
                raise RuntimeError(f"{model}: keine Antwort ({type(e).__name__})") from e
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
        """lines: [{n, ru}] → {"lines": {n: {"de", "stress", "words": [{"w", "de"}]}}, "story", "title_de"}.
        Das Modell übersetzt die Zeile UND jedes einzelne Wort im Zusammenhang selbst; jede Wortbedeutung
        bestätigt danach ein zweites Modell (confirm). Zeilen, deren Antwort nicht passt, fehlen im Ergebnis."""
        system = (
            f"You prepare a Russian {'song' if kind == 'song' else 'film or video scene'} titled »{title}« for a German beginner who learns Russian from it. "
            "Whatever you write will be learned by heart, so it must be right.\n\n"
            "For every LINE give:\n"
            "- de: a faithful, natural German translation of exactly this line. Do not add or leave out anything. Keep names. Informal ты is »du«, polite вы is »Sie«. Use the other lines as context.\n"
            "- stress: the same Russian line, letters unchanged, with an acute accent (U+0301) directly after the stressed vowel of every word that has two or more syllables. Do not mark words with one syllable or words with ё. "
            "The line may show е where the word is really spoken with ё: write ё there (все → всё when it means »alles«), and only there.\n"
            "- words: one entry for EVERY word of the line, in order, none skipped (a hyphenated word like по-настоящему or кто-то is one word).\n"
            "    w: the word exactly as it stands in the line.\n"
            "    de: what this word means IN THIS LINE, in German, 1 to 3 words, matching the form used here (возьми → nimm, тебя → dich, в лесу → im Wald: в = in, лесу = Wald). "
            "It must fit your translation of the line. For a name write Name. "
            "If the word belongs to a fixed expression begun by the previous word, write = and put the meaning of the whole expression at its first word "
            "(«в противном случае»: в → andernfalls, противном → =, случае → =).\n\n"
            "story: one or two plain German sentences saying what happens in these lines, for someone about to watch. Only what the lines say. No opinion, no praise.\n"
            "title_de: the title in German, short (keep names as they are)."
        )
        schema = {"type": "OBJECT", "required": ["lines", "story", "title_de"], "properties": {
            "story": {"type": "STRING"}, "title_de": {"type": "STRING"},
            "lines": {"type": "ARRAY", "items": {"type": "OBJECT", "required": ["n", "de", "stress", "words"], "properties": {
                "n": {"type": "INTEGER"}, "de": {"type": "STRING"}, "stress": {"type": "STRING"},
                "words": {"type": "ARRAY", "items": {"type": "OBJECT", "required": ["w", "de"], "properties": {"w": {"type": "STRING"}, "de": {"type": "STRING"}}}}}}}}}
        rows = [f"LINE {l['n']}: {l['ru']}" for l in lines]
        out = None
        # Hydrogen zuerst; ist sein Tageskontingent leer, übernimmt 3.6 Flash, danach 3.8 Flash.
        for model in (REVIEW, REVIEW2, REVIEW3):
            if self.left(model) <= 0:
                continue
            try:
                out = json.loads(self.text(self.call(model, [{"text": "\n".join(rows)}], schema=schema, system=system, max_tokens=20000,
                                                     patient=model != REVIEW3)))
                break
            except QuotaReached:
                continue
            except RuntimeError as e:
                # Überlastet oder keine Antwort: das nächste Modell der Kette versucht es.
                self.log(f"{model} nicht erreichbar ({str(e)[:60]}) — nächstes Prüfmodell")
            except (json.JSONDecodeError, KeyError):
                return None
        if out is None:
            raise QuotaReached("allen Prüfmodellen (leer oder überlastet)")
        ok = {}
        for g in out.get("lines", []):
            if isinstance(g.get("n"), int) and str(g.get("de", "")).strip() and isinstance(g.get("words"), list):
                ok[g["n"]] = {"de": g["de"].strip(), "stress": str(g.get("stress", "")),
                              "words": [{"w": str(w.get("w", "")), "de": str(w.get("de", "")).strip()} for w in g["words"] if isinstance(w, dict)]}
        return {"lines": ok, "story": str(out.get("story", "")).strip(), "title_de": str(out.get("title_de", "")).strip()}

    def _verdicts(self, model: str, system: str, blocks: list[str]) -> list[bool] | None:
        """Je Eintrag ein Ja/Nein von `model`. None, wenn die Antwort nicht zu den Einträgen passt."""
        schema = {"type": "ARRAY", "items": {"type": "OBJECT", "required": ["n", "ok"], "properties": {"n": {"type": "INTEGER"}, "ok": {"type": "BOOLEAN"}}}}
        out: list[bool] = []
        for start in range(0, len(blocks), 40):
            part = blocks[start:start + 40]
            user = "\n\n".join(f"{i + 1}.\n{b}" for i, b in enumerate(part))
            try:
                if model == SECOND:      # Gemma kennt kein Antwortschema — Format in der Frage vorgeben
                    raw = self.text(self.call(model, [{"text": system + "\nAnswer with a JSON array of objects {\"n\": number, \"ok\": true or false}, one per item, nothing else.\n\n" + user}], max_tokens=4000))
                else:
                    raw = self.text(self.call(model, [{"text": user}], schema=schema, system=system, max_tokens=4000))
                arr = json.loads(raw[raw.find("["):raw.rfind("]") + 1])
                got = {x.get("n"): bool(x.get("ok")) for x in arr if isinstance(x, dict)}
            except (json.JSONDecodeError, KeyError, ValueError, RuntimeError):
                return None
            if sorted(got) != list(range(1, len(part) + 1)):
                return None
            out += [got[i + 1] for i in range(len(part))]
        return out

    def _judge(self, system: str, blocks: list[str]) -> list[bool] | None:
        """Zwei Kontrollmodelle. Abgelehnt ist ein Eintrag erst, wenn BEIDE ihn ablehnen: das Prüfmodell
        ist das stärkere, ein einzelnes schwächeres soll ihm nichts Richtiges streichen (gemessen:
        Flash-Lite lehnte »стремительного = rasant« ab)."""
        if not blocks:
            return []
        first = self._verdicts(JUDGE, system, blocks)
        if first is None:
            first = self._verdicts(SECOND, system, blocks)
            return first
        doubt = [i for i, ok in enumerate(first) if not ok]
        if doubt:
            again = self._verdicts(SECOND, system, [blocks[i] for i in doubt])
            if again is not None:
                for i, ok in zip(doubt, again):
                    first[i] = ok
        return first

    def confirm(self, items: list[tuple[str, str, str, str]]) -> list[bool] | None:
        """(russische Zeile, deutsche Zeile, Wort oder Wendung, Bedeutung) → stimmt die Bedeutung an dieser Stelle?"""
        system = ("For each item you get a Russian line, its German translation, one WORD or expression from the line, and a German MEANING proposed for it. "
                  "Decide whether MEANING is a correct German rendering of WORD as it is used in this line (ok = true). Small differences are fine: a base form "
                  "instead of the inflected one or the other way round, a synonym, a slightly different wording than in the translation, an article more or less. "
                  "ok = false only if MEANING means something else, or belongs to a different word of the line.")
        return self._judge(system, [f"LINE: {ru}\nGERMAN: {de}\nWORD: {w}\nMEANING: {m}" for ru, de, w, m in items])

    def faithful(self, triples: list[tuple[str, str, str]]) -> list[bool] | None:
        """(Russisch, Übersetzung A, unabhängige Übersetzung B) → gibt A die russische Zeile richtig wieder?"""
        system = ("For each item you get a Russian line (often only part of a sentence), a German translation A, and an independent second translation B for comparison. "
                  "Decide whether A renders the Russian line correctly (ok = true). Wording, word order, style and how a sentence fragment is completed do not matter. "
                  "ok = false only if A says something the Russian line does not say, leaves out part of its meaning, or confuses who does what "
                  "(du instead of Sie counts). If A and B differ in meaning, check which of them matches the Russian line.")
        return self._judge(system, [f"RUSSIAN: {ru}\nA: {a}\nB: {b}" for ru, a, b in triples])

    def second(self, kind: str, lines: list[str]) -> list[str] | None:
        """Unabhängige zweite Übersetzung von einem anderen Modell (Gemma; antwortet es nicht brauchbar, Flash-Lite)."""
        prompt = (f"Translate these Russian lines from a {'song' if kind == 'song' else 'film scene'} into German, faithfully, line by line. "
                  "Informal ты is »du«, polite вы is »Sie«. Answer with a JSON array of strings, one German line per Russian line, same order, nothing else.\n\n"
                  + "\n".join(f"{i + 1}. {l}" for i, l in enumerate(lines)))
        for model in (SECOND, JUDGE):
            try:
                raw = self.text(self.call(model, [{"text": prompt}], max_tokens=6000))
                arr = json.loads(raw[raw.find("["):raw.rfind("]") + 1])
                if isinstance(arr, list) and len(arr) == len(lines):
                    return [str(x) for x in arr]
            except (json.JSONDecodeError, ValueError, RuntimeError):
                pass
        return None

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
