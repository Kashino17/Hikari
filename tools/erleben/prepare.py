# /// script
# requires-python = ">=3.11,<3.14"
# dependencies = ["mlx-whisper", "pymorphy3", "deep-translator"]
# ///
"""Bereitet ein YouTube-Video vollautomatisch als Stück für »Lieder & Szenen« auf.

Ohne Sprachmodell (kein Chat-KI-Aufruf), nur feste Werkzeuge:

  yt-dlp + ffmpeg   Video laden, Ausschnitt schneiden, Standbild
  Whisper           Text mit Zeitmarke je Wort (läuft auf dem Mac, wie bei den Sprachnachrichten)
  OpenRussian       Wörterbuch: Wortform → Grundform, Betonung, deutsche Bedeutung, Formangabe
  pymorphy3         Grundform für Wörter, die das Wörterbuch als Form nicht kennt
  Vosk-Wörterbuch   Betonung als Rückfall
  Übersetzer        ganze Zeile Russisch → Deutsch (Übersetzungsdienst, kein Sprachmodell)

Aufrufe:
  uv run --script prepare.py video <youtube-id> [--kind song|scene]
  uv run --script prepare.py next --count 3        # nächste Kandidaten aus sources.json

Ergebnis: ~/.hikari/erleben/auto/<id>.json + media/<id>.mp4|jpg
"""
import argparse
import csv
import hashlib
import json
import os
import re
import subprocess
import sys
import time
from pathlib import Path

HOME = Path.home() / ".hikari" / "erleben"
DICT = HOME / "dict"
WORK = HOME / "work"
MEDIA = HOME / "media"
AUTO = HOME / "auto"
VOSK = Path.home() / ".hikari" / "tts" / "models" / "vosk" / "dictionary"
SOURCES = Path(__file__).with_name("sources.json")
CANDIDATES = HOME / "candidates.json"
MODEL = "mlx-community/whisper-large-v3-turbo"

VOWELS = "аеёиоуыэюя"
MAX_TRANSCRIBE_S = 600          # längere Videos: nur die ersten zehn Minuten
CLIP_MIN, CLIP_MAX = 28, 75     # Länge eines Ausschnitts in Sekunden
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
    **_table("меня:mich, мне:mir, мной:mit mir", "я", "ich"),
    **_table("тебя:dich, тебе:dir, тобой:mit dir", "ты", "du"),
    **_table("его:ihn; sein, ему:ihm, нём:ihm, него:ihn, нему:ihm", "он", "er"),
    **_table("её:sie; ihr, ей:ihr, ней:ihr, неё:sie", "она", "sie"),
    **_table("нас:uns, нам:uns, нами:mit uns", "мы", "wir"),
    **_table("вас:Sie; euch, вам:Ihnen; euch, вами:mit Ihnen", "вы", "Sie; ihr"),
    **_table("их:sie; ihr, им:ihnen, ними:ihnen, них:sie", "они", "sie (mehrere)"),
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
             "не:nicht, ни:kein; weder, да:ja, нет:nein; kein, и:und, а:und; aber, но:aber, или:oder, же:doch, ли:ob, бы:würde, "
             "вот:da; hier, уже:schon, ещё:noch, тоже:auch, так:so, там:dort, тут:hier, здесь:hier, очень:sehr, только:nur, "
             "всё:alles, все:alle, мой:mein, твой:dein, наш:unser, ваш:Ihr, свой:eigen, "
             "в:in, во:in, на:auf, с:mit, со:mit, к:zu, ко:zu, у:bei, о:über, об:über, от:von, до:bis, из:aus, за:für; hinter, "
             "по:entlang; nach, под:unter, над:über, без:ohne, для:für, при:bei, про:über, через:durch, между:zwischen, перед:vor, "
             "можно:man kann, нужно:man muss, надо:man muss, нельзя:man darf nicht, давай:lass uns, спасибо:danke, "
             "пожалуйста:bitte, привет:hallo, пока:tschüss; solange, хорошо:gut, плохо:schlecht, сейчас:jetzt, потом:dann, "
             "теперь:jetzt, всегда:immer, никогда:nie, ничего:nichts, никто:niemand, кто-то:jemand, что-то:etwas, "
             "ой:oh, ах:ach, эх:ach, ну:na, одни:nur; allein, одна:eine; allein, один:ein; allein, раз:einmal; wenn schon, эй:hey, ага:aha, ура:hurra, такой:so ein, такая:so eine, такое:so ein, такие:solche, "
             "самый:der meiste, самая:die meiste, самое:das meiste, сегодня:heute, завтра:morgen, вчера:gestern"),
}
COMMON = {k.replace("ё", "е"): v for k, v in COMMON.items()}


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
        cands = self.forms.get(key)
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

    def stressed(self, word: str, entry: dict | None) -> str:
        """Wort mit + vor dem betonten Vokal; unverändert, wenn die Betonung unbekannt oder überflüssig ist."""
        low = word.lower()
        if sum(c in VOWELS for c in low) < 2 or "ё" in low:
            return word
        form = entry.get("form") if entry else None
        if form and "+" in form and norm(form.replace("+", "")) == norm(word):
            out, i = [], 0
            for c in form:
                if c == "+":
                    out.append("+")
                    continue
                out.append(word[i])
                i += 1
            return "".join(out)
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


def pick_window(lines: list[dict], easy) -> tuple[int, int]:
    """Der Ausschnitt mit den meisten gut erkannten, leichten Wörtern je Sekunde."""
    best, best_score = (0, min(len(lines), 6)), -1.0
    for i in range(len(lines)):
        for j in range(i + 3, len(lines) + 1):
            dur = lines[j - 1]["e"] - lines[i]["s"]
            if dur > CLIP_MAX:
                break
            if dur < CLIP_MIN:
                continue
            gaps = sum(max(0.0, lines[k]["s"] - lines[k - 1]["e"] - 6) for k in range(i + 1, j))
            good = sum(l["conf"] * sum(1 + easy(t["w"]) for t in l["toks"]) for l in lines[i:j] if l["conf"] > 0.5)
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


def translate(texts: list[str]) -> list[str]:
    """Zeilen ins Deutsche. Fällt der Dienst aus, bleibt die Zeile leer — das Stück funktioniert trotzdem Wort für Wort."""
    try:
        from deep_translator import GoogleTranslator, MyMemoryTranslator
    except Exception as e:  # noqa: BLE001
        log("Übersetzer fehlt:", e)
        return ["" for _ in texts]
    services = [lambda: MyMemoryTranslator(source="ru-RU", target="de-DE"), lambda: GoogleTranslator(source="ru", target="de")]
    cache: dict[str, str] = {}
    out = []
    for t in texts:
        if t not in cache:
            cache[t] = ""
            for make in list(services):
                try:
                    cache[t] = (make().translate(t) or "").strip()
                    break
                except Exception as e:  # noqa: BLE001 — Dienst gesperrt oder Tageskontingent leer: nächsten nehmen
                    log("Übersetzer fällt aus:", type(e).__name__)
                    services.remove(make)
            time.sleep(0.2)
        out.append(cache[t])
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


def build(vid: str, kind: str, d: Dictionary) -> dict:
    pid = "yt" + hashlib.sha1(vid.encode()).hexdigest()[:10]
    meta, video = fetch(vid)
    log(f"[{vid}] {meta['title']} — transkribiere …")
    lines = to_lines(transcribe(video))
    if len(lines) < 4:
        raise RuntimeError("zu wenig erkannter Text")
    easy = lambda w: 1.0 if (e := d.lookup(w)) and e["rank"] < 2500 else 0.0
    i, j = pick_window(lines, easy)
    chosen = [l for l in lines[i:j] if l["conf"] > 0.5]
    if len(chosen) < 3:
        raise RuntimeError("Ausschnitt zu unsicher erkannt")
    start = max(0.0, chosen[0]["s"] - 0.8)
    end = chosen[-1]["e"] + 1.0
    dur, ratio = cut(video, pid, start, end)

    all_keys = {norm(t["w"]) for l in chosen for t in l["toks"]}
    d.load_vosk({k for k in all_keys if not d.forms.get(k)})
    name, by = split_title(meta["title"], meta["channel"])
    german = translate([l["text"] for l in chosen] + [name])
    title_de = german.pop()

    out_lines, wordbook = [], {}
    total = easy_n = unknown = 0
    for n, l in enumerate(chosen):
        glosses = []
        shaky = l["conf"] < 0.8
        for t in l["toks"]:
            e = d.lookup(t["w"])
            t["ru"] = d.stressed(t["w"], e)
            glosses.append(e["gloss"] if e else "Name" if t.get("cap") else "?")
            total += 1
            easy_n += 1 if e and e["rank"] < 2500 else 0
            unknown += 0 if e else 1
            shaky = shaky or (not e and not t.get("cap"))
            key = norm(t["w"])
            if key not in wordbook:
                wordbook[key] = ({"b": e["lemma"], "de": e["de"][:90], **({"n": e["note"]} if e.get("note") else {})}
                                 if e else {"b": t["ru"], "de": "vermutlich ein Name" if t.get("cap") else "nicht im Wörterbuch"})
        a = max(0.0, l["s"] - start - 0.3)
        if out_lines:
            a = max(a, out_lines[-1]["b"])
        b = min(dur, l["e"] - start + 0.4)
        if n + 1 < len(chosen):
            b = min(b, max(l["e"] - start + 0.05, chosen[n + 1]["s"] - start - 0.3))
        out_lines.append({"a": round(a, 2), "b": round(b, 2), "ru": stress_text(l["text"], l["toks"], d), "de": german[n],
                          "g": glosses, "t": [round(max(a, t["s"] - start), 2) for t in l["toks"]], "conf": round(l["conf"], 2), **({"unsure": True} if shaky else {})})
    # Wiederholte Zeilen (Refrain) erkennen.
    seen = {}
    for n, l in enumerate(out_lines):
        k = " ".join(norm(w) for w in WORD_RE.findall(l["ru"].replace("+", "")))
        if k in seen:
            l["rep"] = seen[k]
        else:
            seen[k] = n
    conf = sum(l["conf"] for l in chosen) / len(chosen)
    if conf < 0.82 or unknown / max(1, total) > 0.08:
        (MEDIA / f"{pid}.mp4").unlink(missing_ok=True)
        (MEDIA / f"{pid}.jpg").unlink(missing_ok=True)
        raise RuntimeError(f"Text zu unsicher erkannt (Sicherheit {conf:.2f}, {unknown} von {total} Wörtern unbekannt)")
    share = easy_n / max(1, total)
    rate = total / max(1.0, sum(l["e"] - l["s"] for l in chosen))
    level = "Einsteiger" if share > 0.72 and rate < 2.4 else "Mittel" if share > 0.55 else "Anspruchsvoll"
    uniq = [n for n, l in enumerate(out_lines) if "rep" not in l and not l.get("unsure")] or [n for n, l in enumerate(out_lines) if "rep" not in l]
    take = sorted(uniq, key=lambda n: (abs(len(out_lines[n]["g"]) - 4), -out_lines[n]["conf"]))[:3]
    title = stress_text(name, [{"w": w, "ru": d.stressed(w, d.lookup(w))} for w in WORD_RE.findall(name)], d)
    return {
        "id": pid, "yt": vid, "auto": True, "kind": kind, "title": title, "titleDe": title_de or name,
        "part": "Ausschnitt", "by": by, "media": f"{pid}.mp4", "poster": f"{pid}.jpg", "ratio": ratio, "dur": round(dur, 1),
        "mood": "", "level": level, "story": "", "listen": "", "take": sorted(take), "lines": out_lines, "words": wordbook,
        "stats": {"words": total, "easy": round(share, 2), "unknown": unknown, "rate": round(rate, 2),
                  "conf": round(sum(l["conf"] for l in out_lines) / len(out_lines), 2), "from": round(start, 1)},
    }


# ── Nachschub ───────────────────────────────────────────────────────────────

def load_json(p: Path, fallback):
    try:
        return json.loads(p.read_text())
    except Exception:  # noqa: BLE001
        return fallback


def discover(count: int) -> list[tuple[str, str]]:
    """Nächste Kandidaten, reihum aus allen Quellen — damit Lieder und Szenen sich abwechseln."""
    sources = json.loads(SOURCES.read_text())["sources"]
    state = load_json(CANDIDATES, {"pools": {}, "done": [], "turn": 0})
    done = set(state["done"]) | {json.loads(f.read_text()).get("yt") for f in AUTO.glob("*.json")}
    picks = []
    tries = 0
    while len(picks) < count and tries < len(sources) * 3:
        src = sources[state["turn"] % len(sources)]
        state["turn"] += 1
        tries += 1
        pool = state["pools"].get(src["query"])
        if pool is None:
            r = run(["yt-dlp", "--flat-playlist", "--print", "%(id)s\t%(duration)s\t%(view_count)s", f"ytsearch20:{src['query']}"])
            pool = []
            for line in r.stdout.splitlines():
                p = line.split("\t")
                try:
                    dur, views = float(p[1]), float(p[2])
                except (ValueError, IndexError):
                    continue
                if src.get("min", 40) <= dur <= src.get("max", 720):
                    pool.append([p[0], views])
            pool = [v for v, _ in sorted(pool, key=lambda x: -x[1])]
            state["pools"][src["query"]] = pool
        nxt = next((v for v in pool if v not in done), None)
        if nxt:
            done.add(nxt)
            picks.append((nxt, src["kind"]))
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
    todo = [(i, args.kind) for i in args.ids] if args.cmd == "video" else discover(args.count)
    ok = 0
    for vid, kind in todo:
        t0 = time.time()
        try:
            piece = build(vid, kind, d)
            (AUTO / f"{piece['id']}.json").write_text(json.dumps(piece, ensure_ascii=False))
            ok += 1
            log(f"[{vid}] fertig in {time.time() - t0:.0f} s: {len(piece['lines'])} Zeilen, {piece['stats']}")
        except Exception as e:  # noqa: BLE001
            log(f"[{vid}] übersprungen: {e}")
    print(json.dumps({"prepared": ok, "tried": len(todo)}))


if __name__ == "__main__":
    main()
