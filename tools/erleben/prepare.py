# /// script
# requires-python = ">=3.11,<3.14"
# dependencies = ["mlx-whisper", "pymorphy3"]
# ///
"""Bereitet ein YouTube-Video vollautomatisch als Stück für »Lieder & Szenen« auf.

Grundsatz: Was der Lernende zu sehen bekommt, haben zwei unabhängige Quellen bestätigt.
Was nur eine Quelle sagt, wird nicht gezeigt.

  Text einer Zeile     Whisper (auf dem Mac) UND Gemini Transcribe hören Wort für Wort dasselbe
  Betonung, Grundform  OpenRussian-Wörterbuch (jede gebeugte Form mit Akzent), Vosk als Rückfall
  Bedeutung im Satz    Gemini wählt unter den Bedeutungen des Wörterbuchs — eigene erfindet es nicht
  Deutsche Zeile       Gemini 4 Hydrogen übersetzt, Gemma übersetzt unabhängig, ein drittes Modell
                       prüft, ob beide dasselbe sagen
  Zeitmarken           Whisper, je Wort

Zeilen, die eine Prüfung nicht bestehen, bleiben im Video hörbar, werden aber weder gezeigt noch
geübt. Besteht weniger als 60 % eines Ausschnitts, wird das ganze Stück verworfen.

Aufrufe:
  uv run --script prepare.py video <youtube-id> [--kind song|scene]
  uv run --script prepare.py next --count 3        # nächste Kandidaten aus den Themen (interests.json)

Ergebnis: ~/.hikari/erleben/auto/<id>.json + media/<id>.mp4|jpg
Schlüssel und Tageskontingente: siehe gemini.py
"""
import argparse
import csv
import difflib
import hashlib
import json
import os
import re
import subprocess
import sys
import time
from pathlib import Path

sys.path.insert(0, str(Path(__file__).parent))
from gemini import Gemini, QuotaReached, HEAR, REVIEW  # noqa: E402

HOME = Path.home() / ".hikari" / "erleben"
DICT = HOME / "dict"
WORK = HOME / "work"
MEDIA = HOME / "media"
AUTO = HOME / "auto"
VOSK = Path.home() / ".hikari" / "tts" / "models" / "vosk" / "dictionary"
INTERESTS = HOME / "interests.json"   # {"video": ["One Piece", …], "music": ["Rock", …]} — vom Lernenden vorgegeben
CANDIDATES = HOME / "candidates.json"
MODEL = "mlx-community/whisper-large-v3-turbo"

VOWELS = "аеёиоуыэюя"
MAX_TRANSCRIBE_S = 600          # längere Videos: nur die ersten zehn Minuten
CLIP_MIN, CLIP_MAX = 28, 75     # Länge eines Ausschnitts in Sekunden
REGION_S = 240                  # so viel hört das zweite Ohr ab; daraus wird der Ausschnitt gewählt
MIN_LINES, MIN_SHARE = 4, 0.7   # so viele Zeilen und so viel Anteil müssen alle Prüfungen bestehen
# Was Whisper bei Stille oder Musik gern erfindet.
HALLUCINATION = re.compile(r"субтитр|продолжение следует|редактор|корректор|подпис|dimatorzok|^\W*$|^песня\W*$|^музыка\W*$", re.I)

log = lambda *a: print(*a, file=sys.stderr, flush=True)
norm = lambda w: w.lower().replace("ё", "е")


# ── Wörterbuch ──────────────────────────────────────────────────────────────

NOUN_CASE = {"nom": "Grundform", "gen": "Genitiv (wessen? kein …)", "dat": "Dativ (wem?)", "acc": "Akkusativ (wen? wohin?)",
             "inst": "Instrumental (mit wem? womit?)", "prep": "Präpositiv (wo? worüber?)"}
VERB_FORM = {
    "imperative_sg": "Aufforderung an eine Person, die du duzt", "imperative_pl": "Aufforderung, höflich oder an mehrere",
    "past_m": "Vergangenheit, wenn ein Mann etwas tat", "past_f": "Vergangenheit, wenn eine Frau etwas tat",
    "past_n": "Vergangenheit (sächlich)", "past_pl": "Vergangenheit, mehrere oder höflich",
    "presfut_sg1": "ich-Form", "presfut_sg2": "du-Form", "presfut_sg3": "er/sie-Form",
    "presfut_pl1": "wir-Form", "presfut_pl2": "Sie-/ihr-Form", "presfut_pl3": "sie-Form (mehrere)",
}
GENDER = {"m": "männlich", "f": "weiblich", "n": "sächlich", "pl": "Mehrzahl"}


def plus(accented: str) -> str:
    """OpenRussian schreibt den Akzent hinter den Vokal (челове'к) — Hikari ein + davor (челов+ек)."""
    out = []
    for c in accented:
        if c == "'" and out:
            out.insert(len(out) - 1, "+")
        else:
            out.append(c)
    return "".join(out)


def form_note(pos: str, col: str) -> str:
    if pos == "noun":
        num, case = col.split("_")
        return f"{NOUN_CASE[case]}, {'Einzahl' if num == 'sg' else 'Mehrzahl'}" if case != "nom" or num == "pl" else ""
    if pos == "verb":
        return VERB_FORM.get(col, "")
    if pos == "adj":
        if col.startswith("short_"):
            return f"Kurzform, {GENDER[col[6:]]}"
        if col.startswith("decl_"):
            _, g, case = col.split("_")
            return f"{GENDER[g]}, {NOUN_CASE[case]}" if not (g == "m" and case == "nom") else ""
        return {"comparative": "Steigerung: mehr …", "superlative": "höchste Steigerung"}.get(col, "")
    return ""


def short_gloss(de: str, en: str) -> str:
    """Erste Bedeutung, ohne Klammerzusätze, kurz genug für die Zeile unter dem Wort."""
    src = de.strip() or en.strip()
    src = re.sub(r"\s*\([^)]*\)?", "", src)
    first = re.split(r"[;,/]|\s[-–]\s", src)[0].strip(" .!?")
    return first[:26] if first else ""


def _table(spec: str, base: str = "", base_de: str = "") -> dict:
    return {f.strip(): (g.strip(), base, base_de) for f, g in (pair.split(":") for pair in spec.split(",") if pair.strip())}


# Die häufigsten Wörter stehen im Wörterbuch nur als »Genitiv von я« — hier steht, was sie im Satz heißen.
# Form → (Bedeutung im Satz, Grundform, deren Bedeutung)
COMMON = {
    **_table("меня:mich; mir, мне:mir, мной:mit mir", "я", "ich"),
    **_table("тебя:dich; dir, тебе:dir, тобой:mit dir", "ты", "du"),
    **_table("его:ihn; sein; es, ему:ihm, нём:ihm, него:ihn; ihm, нему:ihm, ним:ihm; ihnen, им:ihm; ihnen", "он", "er"),
    **_table("её:sie; ihr, ей:ihr, ней:ihr, неё:sie", "она", "sie"),
    **_table("нас:uns, нам:uns, нами:mit uns", "мы", "wir"),
    **_table("вас:Sie; euch; Ihnen, вам:Ihnen; euch, вами:mit Ihnen", "вы", "Sie; ihr"),
    **_table("их:sie; ihr; von ihnen, ними:ihnen, них:sie; ihnen", "они", "sie (mehrere)"),
    **_table("себя:sich, себе:sich, собой:sich", "себя", "sich selbst"),
    **_table("буду:ich werde, будешь:du wirst, будет:wird, будем:wir werden, будете:Sie werden, будут:sie werden, "
             "был:war, была:war, было:war, были:waren, есть:es gibt; ist", "быть", "sein"),
    **_table("моя:meine, моё:mein, мои:meine, моего:meines, моей:meiner, мою:meine, моим:meinem", "мой", "mein"),
    **_table("твоя:deine, твоё:dein, твои:deine, твоего:deines, твоей:deiner, твою:deine", "твой", "dein"),
    **_table("наша:unsere, наше:unser, наши:unsere", "наш", "unser"),
    **_table("ваша:Ihre, ваше:Ihr, ваши:Ihre", "ваш", "Ihr; euer"),
    **_table("своя:eigene, своё:eigenes, свои:eigene, свою:eigene, своей:eigenen, своим:eigenem", "свой", "eigen"),
    **_table("эта:diese, эти:diese, этого:dieses, этой:dieser, эту:diese, этом:diesem", "этот", "dieser"),
    **_table("та:jene, те:jene, той:jener, того:jenes, ту:jene, том:jenem", "тот", "jener"),
    **_table("хочу:ich will, хочешь:du willst, хочет:will, хотим:wir wollen, хотите:Sie wollen, хотят:sie wollen", "хотеть", "wollen"),
    **_table("могу:ich kann, можешь:du kannst, может:kann; vielleicht, можем:wir können, можете:Sie können, могут:sie können", "мочь", "können"),
    **_table("я:ich, ты:du, он:er, она:sie, оно:es, мы:wir, вы:Sie; ihr, они:sie, это:das, этот:dieser, то:das, тот:jener, "
             "что:was; dass, кто:wer, как:wie, где:wo, когда:wann; als, почему:warum, куда:wohin, откуда:woher, зачем:wozu, "
             "не:nicht, ни:kein; weder, да:ja; und; aber, нет:nein; gibt es nicht, и:und, а:und; aber, но:aber, или:oder, же:doch, ли:ob, бы:würde, "
             "вот:da; hier, уже:schon, ещё:noch, тоже:auch, так:so, там:dort, тут:hier, здесь:hier, очень:sehr, только:nur, "
             "все:alle; alles, мой:mein, твой:dein, наш:unser, ваш:Ihr, свой:eigen, "
             "в:in, во:in, на:auf, с:mit, со:mit, к:zu, ко:zu, у:bei, о:über, об:über, от:von, до:bis, из:aus, за:für; hinter, "
             "по:entlang; nach, под:unter, над:über, без:ohne, для:für, при:bei, про:über, через:durch, между:zwischen, перед:vor, "
             "можно:man kann, нужно:man muss, надо:man muss, нельзя:man darf nicht, давай:lass uns, спасибо:danke, "
             "пожалуйста:bitte, привет:hallo, пока:tschüss; solange, хорошо:gut, плохо:schlecht, сейчас:jetzt, потом:dann, "
             "теперь:jetzt, всегда:immer, никогда:nie, ничего:nichts, никто:niemand, кто-то:jemand, что-то:etwas, "
             "ой:oh, ах:ach, эх:ach, ну:na, одни:nur; allein, одна:eine; allein, один:ein; allein, раз:einmal; wenn schon, эй:hey, ага:aha, ура:hurra, такой:so ein, такая:so eine, такое:so ein, такие:solche, "
             "самый:am meisten, самая:am meisten, самое:am meisten, сегодня:heute, завтра:morgen, вчера:gestern"),
}
COMMON = {k.replace("ё", "е"): v for k, v in COMMON.items()}


# Wörter mit eigener Ortsform nach в/на: »в лесу́, на берегу́, в краю́« — betont auf der Endung,
# anders als der gleich geschriebene Dativ (»к ле́су«). Das Wörterbuch führt diese Form nicht.
LOCATIVE = set("лес сад мост год край берег угол пол шкаф аэропорт снег рай бой строй нос рот лоб глаз лёд порт пост ряд тыл плен быт "
               "пот цвет борт бред долг вид ад круг луг мозг пруд бал отпуск ход час вес таз сук стог хлев ток сок чад жар дым пух мёд жир "
               "пир крым дон верх низ бок зад перёд".split())


def stress_index(word: str) -> int | None:
    """Der wievielte Vokal ist betont? Schreibweisen: + davor (Hikari), Akut danach (Lehrbuch), ё."""
    w = word.lower()
    seen = -1
    for i, c in enumerate(w):
        if c in VOWELS:
            seen += 1
            if c == "ё" or (i > 0 and w[i - 1] == "+") or (i + 1 < len(w) and w[i + 1] == "\u0301"):
                return seen
    return None


def put_stress(word: str, idx: int) -> str:
    seen = -1
    for i, c in enumerate(word):
        if c.lower() in VOWELS:
            seen += 1
            if seen == idx:
                return word if c.lower() == "ё" else word[:i] + "+" + word[i:]
    return word


class Dictionary:
    """Wortform → Kandidaten {lemma (mit +), de, gloss, note, rank}. Häufigere Wörter stehen in den Dateien weiter oben."""

    def __init__(self):
        self.forms: dict[str, list[dict]] = {}
        self.lemmas: dict[str, dict] = {}
        csv.field_size_limit(10**7)
        for pos, name in (("other", "others"), ("noun", "nouns"), ("verb", "verbs"), ("adj", "adjectives")):
            with open(DICT / f"{name}.csv", encoding="utf-8") as f:
                for rank, row in enumerate(csv.DictReader(f, delimiter="\t")):
                    bare = (row.get("bare") or "").strip()
                    if not bare or " " in bare:
                        continue
                    de, en = row.get("translations_de") or "", row.get("translations_en") or ""
                    gloss = short_gloss(de, en)
                    if not gloss:
                        continue
                    # Funktionswörter zuerst, sonst nach Häufigkeit.
                    base = {"lemma": plus(row.get("accented") or bare), "de": (de or en).strip(), "gloss": gloss,
                            "rank": rank + (0 if pos == "other" else 300), "pos": pos}
                    self.lemmas.setdefault(norm(bare), base)
                    self._add(bare, {**base, "form": plus(row.get("accented") or bare), "note": ""})
                    for col, val in row.items():
                        if col is None or not isinstance(val, str) or not val or col in ("bare", "accented", "translations_en", "translations_de", "partner", "aspect", "gender", "animate", "indeclinable", "sg_only", "pl_only"):
                            continue
                        for variant in re.split(r"[,;]\s*", val):
                            v = variant.strip()
                            if v and re.fullmatch(r"[А-Яа-яЁё'\-]+", v):
                                self._add(v.replace("'", ""), {**base, "form": plus(v), "note": form_note(pos, col)})
        try:
            import pymorphy3
            self.morph = pymorphy3.MorphAnalyzer()
        except Exception as e:  # noqa: BLE001
            log("pymorphy3 fehlt:", e)
            self.morph = None
        self.vosk: dict[str, int] = {}

    def _add(self, form: str, entry: dict):
        self.forms.setdefault(norm(form), []).append(entry)

    @staticmethod
    def _exact(word: str, cands: list[dict] | None) -> list[dict] | None:
        """»все« und »всё«, »узнает« und »узнаёт« sind verschiedene Wörter: Formen, die auch im ё
        übereinstimmen, gehen vor. Kennt das Wörterbuch das Wort nur mit ё (»елка«), bleibt es dabei."""
        if not cands:
            return cands
        w = word.lower()
        hit = [c for c in cands if (c.get("form") or "").replace("+", "").lower() == w]
        return hit or cands

    def load_vosk(self, wanted: set[str]):
        """Betonte Silbe (Index des Vokals) für Wörter, die OpenRussian nicht kennt."""
        if not VOSK.exists() or not wanted:
            return
        with open(VOSK, encoding="utf-8", errors="ignore") as f:
            for line in f:
                sp = line.split()
                if sp and sp[0] in wanted and sp[0] not in self.vosk:
                    ph = [x for x in sp[2:] if x[-1] in "01"]
                    st = [i for i, x in enumerate(ph) if x.endswith("1")]
                    if len(st) == 1:
                        self.vosk[sp[0]] = st[0]

    def lookup(self, word: str) -> dict | None:
        key = norm(word)
        cands = self._exact(word, self.forms.get(key))
        common = COMMON.get(key)
        if common:
            gloss, base, base_de = common
            hit = min(cands, key=lambda c: c["rank"]) if cands else {}
            lemma = (self.lemmas.get(norm(base)) or {}).get("lemma", base) if base else hit.get("lemma", word.lower())
            note = f"{hit.get('form') or word.lower()} = {gloss}" if base and norm(base) != key else ""
            return {"lemma": lemma, "de": base_de or gloss, "gloss": gloss.split(";")[0], "rank": 0, "pos": "other",
                    "form": hit.get("form"), "note": note}
        if cands:
            return min(cands, key=lambda c: c["rank"])
        if self.morph:
            for p in self.morph.parse(word)[:3]:
                hit = self.lemmas.get(norm(p.normal_form))
                if hit:
                    return {**hit, "form": None, "note": ""}
        return None

    def options(self, word: str, prev: str = "") -> list[tuple[dict, str]]:
        """Alle Bedeutungen, die das Wörterbuch für diese Wortform kennt: [(Eintrag, Bedeutung)].
        Höchstens zwei Grundformen (die häufigsten) und vier Bedeutungen je Grundform."""
        key = norm(word)
        hit = self.lookup(word)
        if not hit:
            return []
        if key in COMMON:
            # Mehrdeutige Kleinwörter (»а« = und/aber, »его« = ihn/sein): alle Lesarten stehen zur Wahl.
            return [(hit, alt.strip()) for alt in COMMON[key][0].split(";")]
        cands = self._exact(word, self.forms.get(key)) or [hit]
        by_lemma: dict[str, dict] = {}
        for c in sorted(cands, key=lambda c: c["rank"]):
            cur = by_lemma.get(c["lemma"])
            # Nach в/на steht die Ortsform — ihre Betonung kann abweichen (в кра+ю).
            better = prev in ("в", "во", "на") and "Präpositiv" in (c.get("note") or "") and "Präpositiv" not in ((cur or {}).get("note") or "")
            if cur is None or better:
                by_lemma[c["lemma"]] = c
        out, seen = [], set()
        for c in list(by_lemma.values())[:2]:
            src = re.sub(r"\s*\([^)]*\)?", "", c["de"])
            n = 0
            for sense in re.split(r"[;,/]", src):
                sense = sense.strip(" .!?")[:30]
                if sense and sense.lower() not in seen and n < 4:
                    seen.add(sense.lower())
                    out.append((c, sense))
                    n += 1
        return out

    def stressed(self, word: str, entry: dict | None) -> str:
        """Wort mit + vor dem betonten Vokal; unverändert, wenn die Betonung unbekannt oder überflüssig ist."""
        low = word.lower()
        if "ё" in low:
            return word
        form = entry.get("form") if entry else None
        if form and norm(form.replace("+", "")) == norm(word) and ("+" in form or "ё" in form.lower()):
            out, i = [], 0
            for c in form:
                if c == "+":
                    out.append("+")
                    continue
                out.append(c.upper() if word[i].isupper() else c.lower())
                i += 1
            # Einsilbige Wörter brauchen kein Betonungszeichen (»взял«), wohl aber ihr ё (»всё«).
            return "".join(out) if sum(c in VOWELS for c in low) >= 2 else "".join(out).replace("+", "")
        if sum(c in VOWELS for c in low) < 2:
            return word
        idx = self.vosk.get(norm(word))
        if idx is not None:
            seen = -1
            for i, c in enumerate(low):
                if c in VOWELS:
                    seen += 1
                    if seen == idx:
                        return word[:i] + "+" + word[i:]
        return word


# ── Video ───────────────────────────────────────────────────────────────────

def run(cmd: list[str], **kw) -> subprocess.CompletedProcess:
    return subprocess.run(cmd, capture_output=True, text=True, **kw)


def fetch(vid: str) -> tuple[dict, Path]:
    WORK.mkdir(parents=True, exist_ok=True)
    video = WORK / f"{vid}.mp4"
    meta_file = WORK / f"{vid}.info.json"
    if not video.exists() or not meta_file.exists():
        r = run(["yt-dlp", "-q", "--no-warnings", "--write-info-json", "--no-playlist",
                 "-f", "bv*[vcodec^=avc1][height<=480]+ba[ext=m4a]/b[ext=mp4]/b", "--merge-output-format", "mp4",
                 "-o", str(WORK / "%(id)s.%(ext)s"), f"https://www.youtube.com/watch?v={vid}"])
        if not video.exists():
            raise RuntimeError(f"Download fehlgeschlagen: {r.stderr.strip()[-300:]}")
    meta = json.loads(meta_file.read_text())
    return {"title": meta.get("title", ""), "channel": meta.get("channel") or meta.get("uploader") or "", "duration": meta.get("duration") or 0}, video


def transcribe(video: Path) -> list[list[dict]]:
    """→ Whispers Abschnitte, je Abschnitt die Wörter mit Zeit und Sicherheit. Wird je Video gemerkt."""
    cache = WORK / f"{video.stem}.words.json"
    if cache.exists():
        return json.loads(cache.read_text())
    import mlx_whisper
    audio = WORK / f"{video.stem}.16k.wav"
    run(["ffmpeg", "-v", "error", "-y", "-i", str(video), "-t", str(MAX_TRANSCRIBE_S), "-ar", "16000", "-ac", "1", str(audio)])
    r = mlx_whisper.transcribe(str(audio), path_or_hf_repo=MODEL, language="ru", word_timestamps=True,
                               condition_on_previous_text=False, verbose=None)
    audio.unlink(missing_ok=True)
    segs = []
    for seg in r["segments"]:
        if HALLUCINATION.search(seg["text"].strip()) or seg.get("no_speech_prob", 0) > 0.6:
            continue
        words = [{"w": w["word"].strip(), "s": float(w["start"]), "e": float(w["end"]), "p": float(w.get("probability", 1.0))}
                 for w in seg.get("words", []) if re.search(r"[А-Яа-яЁё]", w["word"])]
        if words:
            segs.append(words)
    cache.write_text(json.dumps(segs, ensure_ascii=False))
    return segs


WORD_RE = re.compile(r"[А-Яа-яЁё]+(?:-[А-Яа-яЁё]+)*")


def to_lines(segs: list[list[dict]]) -> list[dict]:
    """Whispers Abschnitte sind schon Sinneinheiten — eine Zeile je Abschnitt. Nur lange werden geteilt:
    am Satzzeichen, an einer Pause, spätestens nach zehn Wörtern."""
    def split(ws: list[dict]) -> list[list[dict]]:
        """Zu lange Abschnitte teilen: am Satzzeichen nahe der Mitte, sonst an der längsten Pause."""
        if len(ws) <= 9:
            return [ws]
        mid = len(ws) / 2
        cuts = [k for k in range(3, len(ws) - 2)]
        def badness(k):
            mark = ws[k - 1]["w"][-1:]
            gap = ws[k]["s"] - ws[k - 1]["e"]
            return abs(k - mid) - (6 if mark in ".!?…" else 3 if mark in ",;:" else 0) - min(gap, 1.5) * 4
        k = min(cuts, key=badness)
        return split(ws[:k]) + split(ws[k:])

    groups = []
    for seg in segs:
        cur = []
        for w in seg:
            if cur and w["s"] - cur[-1]["e"] > 1.5:
                groups += split(cur)
                cur = []
            cur.append(w)
        if cur:
            groups += split(cur)
    out = []
    for ws in groups:
        conf = sum(w["p"] for w in ws) / len(ws)
        text = " ".join(w["w"] for w in ws)
        # Ein Whisper-»Wort« kann mehrere enthalten (»кто-то«, »по-русски«) — die Zeit teilen sie sich.
        toks = []
        for w in ws:
            parts = WORD_RE.findall(w["w"])
            for k, part in enumerate(parts):
                toks.append({"w": part, "s": w["s"] + (w["e"] - w["s"]) * k / max(1, len(parts)), "cap": part[:1].isupper() and (k > 0 or w is not ws[0])})
        if toks:
            out.append({"s": ws[0]["s"], "e": ws[-1]["e"], "text": text[0].upper() + text[1:], "toks": toks, "conf": conf})
    return out


def pick_window(lines: list[dict], easy, max_s: float = CLIP_MAX, min_s: float = CLIP_MIN) -> tuple[int, int]:
    """Der Ausschnitt mit den meisten gut erkannten, leichten Wörtern je Sekunde. Zeilen mit
    `agree: False` (vom zweiten Ohr anders gehört) zählen nicht und kosten Punkte."""
    best, best_score = (0, min(len(lines), 6)), -1.0
    for i in range(len(lines)):
        for j in range(i + 3, len(lines) + 1):
            dur = lines[j - 1]["e"] - lines[i]["s"]
            if dur > max_s:
                break
            if dur < min_s:
                continue
            gaps = sum(max(0.0, lines[k]["s"] - lines[k - 1]["e"] - 6) for k in range(i + 1, j))
            good = sum(l["conf"] * sum(1 + easy(t["w"]) for t in l["toks"]) for l in lines[i:j] if l["conf"] > 0.5 and l.get("agree", True))
            good -= 4 * sum(1 for l in lines[i:j] if not l.get("agree", True))
            ends = 0.15 if re.search(r"[.!?…]$", lines[j - 1]["text"]) else 0.0
            starts = 0.1 if i == 0 or re.search(r"[.!?…]$", lines[i - 1]["text"]) else 0.0
            score = good / (dur + 10) - 0.05 * gaps + 0.02 * (j - i) + ends + starts
            if score > best_score:
                best, best_score = (i, j), score
    return best


def cut(video: Path, pid: str, start: float, end: float) -> tuple[float, float]:
    MEDIA.mkdir(parents=True, exist_ok=True)
    probe = run(["ffmpeg", "-ss", str(start + 1), "-t", "4", "-i", str(video), "-vf", "cropdetect=24:2:0", "-f", "null", "-"])
    crops = re.findall(r"crop=(\d+):(\d+):(\d+):(\d+)", probe.stderr)
    vf = ""
    w = h = None
    if crops:
        w, h, x, y = map(int, max(set(crops), key=crops.count))
        if w >= 200 and h >= 200:
            vf = f"crop={w}:{h}:{x}:{y},"
    dur = end - start
    fade = 0.6
    out = MEDIA / f"{pid}.mp4"
    r = run(["ffmpeg", "-v", "error", "-y", "-ss", f"{start:.2f}", "-to", f"{end:.2f}", "-i", str(video),
             "-vf", f"{vf}fade=t=out:st={dur - fade:.2f}:d={fade}", "-af", f"afade=t=in:d=0.12,afade=t=out:st={dur - fade:.2f}:d={fade}",
             "-c:v", "libx264", "-preset", "veryfast", "-crf", "24", "-pix_fmt", "yuv420p", "-profile:v", "main", "-g", "25", "-keyint_min", "25",
             "-c:a", "aac", "-b:a", "112k", "-movflags", "+faststart", str(out)])
    if not out.exists():
        raise RuntimeError(f"Schnitt fehlgeschlagen: {r.stderr[-300:]}")
    run(["ffmpeg", "-v", "error", "-y", "-ss", f"{min(dur * 0.3, 8):.2f}", "-i", str(out), "-frames:v", "1", "-q:v", "3", str(MEDIA / f"{pid}.jpg")])
    size = run(["ffprobe", "-v", "error", "-select_streams", "v:0", "-show_entries", "stream=width,height", "-of", "csv=p=0", str(out)]).stdout.strip().split(",")
    ratio = round(int(size[0]) / int(size[1]), 3) if len(size) == 2 else 1.778
    return dur, ratio


def second_ear(lines: list[dict], heard: str) -> list[bool]:
    """Vergleicht Whispers Zeilen Wort für Wort mit dem, was das zweite Ohr gehört hat.
    → je Zeile True, wenn beide dieselben Wörter in derselben Reihenfolge haben.

    Nebenbei übernimmt jedes bestätigte Wort das ё des zweiten Ohrs: Whisper schreibt »узнает«
    für »узнаёт« und »все« für »всё« — zwei verschiedene Wörter mit verschiedener Betonung."""
    parts = [(t, k) for l in lines for t in l["toks"] for k in range(len(t["w"].split("-")))]
    a = [norm(t["w"].split("-")[k]) for t, k in parts]
    raw = [w for tok in WORD_RE.findall(heard) for w in tok.split("-")]
    b = [norm(w) for w in raw]
    same = [False] * len(a)
    for tag, i1, i2, j1, j2 in difflib.SequenceMatcher(a=a, b=b, autojunk=False).get_opcodes():
        if tag == "equal":
            for i in range(i1, i2):
                same[i] = True
                if "ё" in raw[j1 + i - i1].lower():
                    t, k = parts[i]
                    pieces = t["w"].split("-")
                    pieces[k] = "".join(c.upper() if o.isupper() else c for c, o in zip(raw[j1 + i - i1].lower(), pieces[k]))
                    t["w"] = "-".join(pieces)
        elif tag == "insert" and 0 < i1 < len(a):
            # Das zweite Ohr hat hier ein Wort mehr gehört (»Кто ВЫ и как«) — die Zeile, in der es
            # stünde, gilt als strittig: das Wort davor und das danach verlieren ihre Bestätigung.
            same[i1 - 1] = same[i1] = False
    out, pos = [], 0
    for l in lines:
        n = sum(len(t["w"].split("-")) for t in l["toks"])
        out.append(all(same[pos:pos + n]))
        pos += n
        if out[-1]:
            # Auch der Zeilentext bekommt das ё.
            it = iter(l["toks"])
            l["text"] = WORD_RE.sub(lambda m: next(it, {"w": m.group(0)})["w"], l["text"])
    return out


# ── Stück bauen ─────────────────────────────────────────────────────────────

def stress_text(text: str, toks: list[dict], d: Dictionary) -> str:
    """Setzt in den Zeilentext die Betonung der einzelnen Wörter ein."""
    it = iter(toks)
    def rep(m):
        t = next(it, None)
        return t["ru"] if t and norm(t["w"]) == norm(m.group(0)) else m.group(0)
    return WORD_RE.sub(rep, text)


def split_title(title: str, channel: str) -> tuple[str, str]:
    """»Валентина Толкунова "Миленький ты мой" (1981)« → Titel und Interpret."""
    m = re.search(r'[«"„“]([^»"“”]{3,60})[»"“”]', title)
    if m:
        rest = re.sub(r"\s*\([^)]*\)", "", (title[:m.start()] + title[m.end():])).strip(" -–—|.,")
        return m.group(1).strip(), rest or channel
    clean = re.sub(r"\s*[\(\[][^\)\]]*[\)\]]", "", title)
    clean = re.split(r"\s[|•]\s|\s#", clean)[0].strip(" -–—|.,")
    parts = re.split(r"\s[-–—]\s", clean, maxsplit=1)
    if len(parts) == 2 and re.search(r"[А-Яа-яЁё]", parts[1]):
        return parts[1].strip(), parts[0].strip()
    return clean or title, channel


def build(vid: str, kind: str, d: Dictionary, g: Gemini) -> dict:
    pid = "yt" + hashlib.sha1(vid.encode()).hexdigest()[:10]
    meta, video = fetch(vid)
    log(f"[{vid}] {meta['title']} — transkribiere …")
    lines = to_lines(transcribe(video))
    if len(lines) < 4:
        raise RuntimeError("zu wenig erkannter Text")
    easy = lambda w: 1.0 if (e := d.lookup(w)) and e["rank"] < 2500 else 0.0

    # 1) Zweites Ohr. Es hört einen größeren Abschnitt ab (bis vier Minuten rund um die dichteste
    #    Stelle); der Ausschnitt wird erst danach gewählt — dort, wo beide Ohren übereinstimmen.
    i, j = pick_window(lines, easy, max_s=REGION_S, min_s=min(CLIP_MIN, lines[-1]["e"] - lines[0]["s"] - 1))
    region = lines[i:j]
    r0, r1 = max(0.0, region[0]["s"] - 1.0), region[-1]["e"] + 1.0
    audio = WORK / f"{pid}.mp3"
    run(["ffmpeg", "-v", "error", "-y", "-ss", f"{r0:.2f}", "-to", f"{r1:.2f}", "-i", str(video), "-vn", "-ac", "1", "-ar", "16000", "-b:a", "48k", str(audio)])
    heard = g.hear(audio)
    audio.unlink(missing_ok=True)
    for l, ok in zip(region, second_ear(region, heard)):
        l["agree"] = ok
    if sum(l["agree"] for l in region) < MIN_LINES:
        raise RuntimeError(f"beide Ohren stimmen nur in {sum(l['agree'] for l in region)} von {len(region)} Zeilen überein")
    i, j = pick_window(region, easy)
    chosen = region[i:j]
    agree = [l["agree"] for l in chosen]
    start = max(0.0, chosen[0]["s"] - 0.8)
    end = chosen[-1]["e"] + 1.0
    dur, ratio = cut(video, pid, start, end)

    def discard(reason: str):
        (MEDIA / f"{pid}.mp4").unlink(missing_ok=True)
        (MEDIA / f"{pid}.jpg").unlink(missing_ok=True)
        for n, l in enumerate(chosen):
            log(f"      {why[n] or 'ok':12} {l['text']}")
        raise RuntimeError(reason)

    why: list[str | None] = []
    # 2) Wörterbuch: jede Wortform braucht einen Eintrag (Namen ausgenommen).
    d.load_vosk({norm(t["w"]) for l in chosen for t in l["toks"] if not d.forms.get(norm(t["w"]))})
    for l, ok in zip(chosen, agree):
        prev = ""
        for t in l["toks"]:
            t["opts"] = d.options(t["w"], prev)
            prev = norm(t["w"])
        unknown = [t["w"] for t in l["toks"] if not t["opts"] and not t.get("cap")]
        why.append(None if ok and not unknown else "gehört" if not ok else "wörterbuch")
    good = [n for n, w in enumerate(why) if w is None]
    if len(good) < max(MIN_LINES, MIN_SHARE * len(chosen)):
        discard(f"nur {len(good)} von {len(chosen)} Zeilen von beiden Ohren gleich gehört")

    # 3) Bedeutung: Übersetzung, Wortbedeutung im Satz, Kurzbeschreibung — eine Anfrage.
    name, by = split_title(meta["title"], meta["channel"])
    ask = [{"n": n + 1, "ru": chosen[n]["text"], "words": [{"w": t["w"], "options": [o for _, o in t["opts"]]} for t in chosen[n]["toks"] if len(t["opts"]) > 1]} for n in good]
    rv = g.review(kind, name, ask)
    if rv is None:
        discard("Antwort des Prüfmodells passte nicht ins Format")
    # 4) Gegenprobe der Übersetzung: zweites Modell übersetzt, drittes vergleicht.
    texts = [chosen[n]["text"] for n in good]
    other = g.second(kind, texts)
    verdict = g.same([(ru, a["de"], b) for ru, a, b in zip(texts, rv["lines"], other)]) if other else None
    if verdict is None:
        discard("Gegenprobe der Übersetzung nicht möglich")
    for n, ok in zip(good, verdict):
        if not ok:
            why[n] = "übersetzung"
    final = [n for n in good if why[n] is None]
    if len(final) < max(MIN_LINES, MIN_SHARE * len(chosen)):
        discard(f"nur {len(final)} von {len(chosen)} Zeilen haben alle Prüfungen bestanden")

    answers = {n: r for n, r in zip(good, rv["lines"])}
    marked: dict[int, list[str]] = {}
    for n in final:
        said = [w for tok in re.findall(r"[А-Яа-яЁё\u0301]+(?:-[А-Яа-яЁё\u0301]+)*", answers[n].get("stress", "")) for w in [tok]]
        if [norm(w.replace("\u0301", "")) for w in said] != [norm(t["w"]) for t in chosen[n]["toks"]]:
            why[n] = "betonung"
        else:
            marked[n] = said
    final = [n for n in final if why[n] is None]
    out_lines, wordbook = [], {}
    total = easy_n = 0
    for n, l in enumerate(chosen):
        a = max(0.0, l["s"] - start - 0.3)
        if out_lines:
            a = max(a, out_lines[-1]["b"])
        b = min(dur, l["e"] - start + 0.4)
        if n + 1 < len(chosen):
            b = min(b, max(l["e"] - start + 0.05, chosen[n + 1]["s"] - start - 0.3))
        if why[n] is not None:
            # Nicht bestätigt: die Stelle bleibt im Video, aber ohne Text — nichts Falsches zum Lernen.
            out_lines.append({"a": round(a, 2), "b": round(b, 2), "ok": False, "why": why[n], "ru": "", "de": "", "g": [], "t": []})
            continue
        picks = iter(answers[n]["senses"])
        glosses = []
        stress_clash: list[str] = []
        prev = ""
        for t, said in zip(l["toks"], marked[n]):
            opts = t["opts"]
            k = (next(picks) - 1) if len(opts) > 1 else 0
            entry, sense = opts[k] if 0 <= k < len(opts) else ((opts[0][0], "") if opts else (None, "Name"))
            t["ru"] = d.stressed(t["w"], entry)
            # Betonung: Wörterbuch und Prüfmodell müssen dieselbe Silbe nennen.
            syll = sum(c in VOWELS for c in t["w"].lower())
            mine, theirs = stress_index(t["ru"]), stress_index(said)
            if syll >= 2 and "ё" not in t["ru"].lower() and mine != theirs:
                key = norm(t["w"])
                others = {stress_index(c["form"]): c for c in d.forms.get(key, []) if c.get("form")}
                if theirs is not None and theirs in others:
                    # Eine andere Form desselben Worts trägt die Betonung, die das Modell hört.
                    t["ru"] = put_stress(t["w"], theirs)
                    if entry and others[theirs]["lemma"] == entry["lemma"]:
                        entry = {**entry, "note": others[theirs].get("note", ""), "form": others[theirs]["form"]}
                elif theirs == syll - 1 and prev in ("в", "во", "на") and entry and norm(entry["lemma"].replace("+", "")) in LOCATIVE:
                    t["ru"] = put_stress(t["w"], theirs)
                    entry = {**entry, "note": "Ortsform nach в/на (wo?), betont auf der Endung"}
                elif mine is None and theirs is not None:
                    t["ru"] = put_stress(t["w"], theirs)       # Namen und Wörter ohne Wörterbuch-Betonung
                else:
                    stress_clash.append(t["w"])
            prev = norm(t["w"])
            glosses.append(sense[:26])
            total += 1
            easy_n += 1 if entry and entry["rank"] < 2500 else 0
            key = norm(t["w"])
            if key not in wordbook:
                wordbook[key] = ({"b": entry["lemma"], "de": entry["de"][:90], **({"n": entry["note"]} if entry.get("note") else {})}
                                 if entry else {"b": t["ru"], "de": "ein Name"})
        if stress_clash:
            log(f"      Betonung strittig bei {', '.join(stress_clash)}: {l['text']}")
            why[n] = "betonung"
            out_lines.append({"a": round(a, 2), "b": round(b, 2), "ok": False, "why": "betonung", "ru": "", "de": "", "g": [], "t": []})
            continue
        out_lines.append({"a": round(a, 2), "b": round(b, 2), "ru": stress_text(l["text"], l["toks"], d), "de": answers[n]["de"].strip(),
                          "g": glosses, "t": [round(max(a, t["s"] - start), 2) for t in l["toks"]]})
    # Wiederholte Zeilen (Refrain) erkennen.
    seen = {}
    for n, l in enumerate(out_lines):
        if l.get("ok") is False:
            continue
        k = " ".join(norm(w) for w in WORD_RE.findall(l["ru"].replace("+", "")))
        if k in seen:
            l["rep"] = seen[k]
        else:
            seen[k] = n
    share = easy_n / max(1, total)
    rate = total / max(1.0, sum(chosen[n]["e"] - chosen[n]["s"] for n in final))
    level = "Einsteiger" if share > 0.72 and rate < 2.4 else "Mittel" if share > 0.55 else "Anspruchsvoll"
    uniq = [n for n, l in enumerate(out_lines) if "rep" not in l and l.get("ok") is not False]
    take = sorted(uniq, key=lambda n: abs(len(out_lines[n]["g"]) - 4))[:3]
    title = stress_text(name, [{"w": w, "ru": d.stressed(w, d.lookup(w))} for w in WORD_RE.findall(name)], d)
    story = " ".join(re.findall(r"[^.!?]+[.!?]", rv["story"])[:2]).strip()
    final = [n for n in final if why[n] is None]
    if len(final) < max(MIN_LINES, MIN_SHARE * len(chosen)):
        discard(f"nur {len(final)} von {len(chosen)} Zeilen haben alle Prüfungen bestanden")
    dropped = {k: why.count(k) for k in ("gehört", "wörterbuch", "übersetzung", "betonung") if why.count(k)}
    return {
        "id": pid, "yt": vid, "auto": True, "checked": True, "kind": kind, "title": title, "titleDe": rv["title_de"] or name,
        "part": "Ausschnitt", "by": by, "media": f"{pid}.mp4", "poster": f"{pid}.jpg", "ratio": ratio, "dur": round(dur, 1),
        "mood": "", "level": level, "story": story, "listen": "", "take": sorted(take), "lines": out_lines, "words": wordbook,
        "stats": {"lines": len(chosen), "verified": len(final), "dropped": dropped, "words": total, "easy": round(share, 2),
                  "rate": round(rate, 2), "from": round(start, 1)},
    }


# ── Nachschub ───────────────────────────────────────────────────────────────

def load_json(p: Path, fallback):
    try:
        return json.loads(p.read_text())
    except Exception:  # noqa: BLE001
        return fallback


def discover(count: int, g: Gemini) -> list[tuple[str, str]]:
    """Nächste Kandidaten aus den Themen des Lernenden. Videos und Musik wechseln sich ab, innerhalb
    davon geht es reihum durch die Themen; je Thema macht Gemini aus dem Stichwort russische
    Suchanfragen, YouTube liefert dazu die meistgesehenen Treffer passender Länge."""
    wishes = load_json(INTERESTS, {})
    topics = [("scene", t) for t in wishes.get("video", [])], [("song", t) for t in wishes.get("music", [])]
    order = [x for pair in zip(*topics) for x in pair] + topics[0][len(topics[1]):] + topics[1][len(topics[0]):]
    if not order:
        return []
    state = load_json(CANDIDATES, {"queries": {}, "pools": {}, "done": [], "turn": 0})
    state.setdefault("queries", {})
    done = set(state["done"]) | {json.loads(f.read_text()).get("yt") for f in AUTO.glob("*.json")}
    picks, tries = [], 0
    while len(picks) < count and tries < len(order) * 4:
        kind, topic = order[state["turn"] % len(order)]
        state["turn"] += 1
        tries += 1
        qkey = f"{kind}:{topic.lower()}"
        if qkey not in state["queries"]:
            state["queries"][qkey] = g.queries(topic, music=kind == "song") or [topic]
            log(f"Thema »{topic}«: suche nach {state['queries'][qkey]}")
        queries = state["queries"][qkey]
        # Je Durchgang eine andere Suchanfrage des Themas, damit die Treffer nicht eintönig werden.
        query = queries[(state["turn"] // len(order)) % len(queries)]
        pool = state["pools"].get(query)
        if pool is None:
            lo, hi = (90, 360) if kind == "song" else (40, 900)
            r = run(["yt-dlp", "--flat-playlist", "--print", "%(id)s\t%(duration)s\t%(view_count)s", f"ytsearch15:{query}"])
            pool = []
            for line in r.stdout.splitlines():
                p = line.split("\t")
                try:
                    dur, views = float(p[1]), float(p[2])
                except (ValueError, IndexError):
                    continue
                if lo <= dur <= hi:
                    pool.append([p[0], views])
            pool = [v for v, _ in sorted(pool, key=lambda x: -x[1])]
            state["pools"][query] = pool
        nxt = next((v for v in pool if v not in done), None)
        if nxt:
            done.add(nxt)
            picks.append((nxt, kind, topic))
    state["done"] = sorted(x for x in done if x)
    CANDIDATES.write_text(json.dumps(state, ensure_ascii=False))
    return picks


def main():
    ap = argparse.ArgumentParser()
    sub = ap.add_subparsers(dest="cmd", required=True)
    v = sub.add_parser("video")
    v.add_argument("ids", nargs="+")
    v.add_argument("--kind", default="scene")
    n = sub.add_parser("next")
    n.add_argument("--count", type=int, default=3)
    args = ap.parse_args()
    AUTO.mkdir(parents=True, exist_ok=True)
    d = Dictionary()
    g = Gemini(log)
    todo = [(i, args.kind, "") for i in args.ids] if args.cmd == "video" else discover(args.count, g)
    ok = 0
    for vid, kind, topic in todo:
        if g.left(HEAR) <= 0 or g.left(REVIEW) <= 0:
            log("Tageskontingent bei Gemini aufgebraucht — morgen geht es weiter")
            break
        t0 = time.time()
        try:
            piece = build(vid, kind, d, g)
            piece["topic"] = topic
            (AUTO / f"{piece['id']}.json").write_text(json.dumps(piece, ensure_ascii=False))
            ok += 1
            log(f"[{vid}] fertig in {time.time() - t0:.0f} s: {piece['stats']}")
        except QuotaReached as e:
            log(f"Tageskontingent von {e} aufgebraucht — morgen geht es weiter")
            break
        except Exception as e:  # noqa: BLE001
            log(f"[{vid}] übersprungen: {e}")
    print(json.dumps({"prepared": ok, "tried": len(todo), "left": {"hören": g.left(HEAR), "prüfen": g.left(REVIEW)}}))


if __name__ == "__main__":
    main()
