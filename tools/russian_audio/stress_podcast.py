"""
Betonung für die Podcast-Lernphrasen (assets/russian/podcast.json).

Die Quelle (RusslandJournal) schreibt ohne Betonungszeichen. Ohne sie betont die
Sprachausgabe falsch und die Umschrift in der App rät. Dieses Werkzeug schreibt das
"+" wie im Hauptkurs vor den betonten Vokal:

  1. ITEM   ganzer Satz von Hand (Sonderfälle wie «н+е был»)
  2. HYPHEN Bindestrich-Wörter
  3. WORD   von Hand entschiedene Wörter, bei denen das Wörterbuch mehrere Betonungen kennt
            (любим: «мы л+юбим», nicht das Adjektiv люб+им) — entschieden am Satz
  4. Aussprachewörterbuch des Sprachmodells, wenn es nur EINE Betonung kennt

Außerdem räumt es Anzeige-Reste der Quelle auf (CLEAN, Großbuchstaben als Endungsmarke)
und liefert mit speech_text() den Text, der wirklich gesprochen wird (Ziffern als Wörter,
keine Klammern).

  python stress_podcast.py --model ~/.hikari/tts/models/vosk          # podcast.json umschreiben
  python stress_podcast.py --model ~/.hikari/tts/models/vosk --check  # nur prüfen (Exit 1 bei offenen Wörtern)

build_podcast_course.py ruft apply() nach jedem Neuaufbau auf.
"""
import argparse, collections, json, pathlib, re, sys

HERE = pathlib.Path(__file__).resolve().parent
ASSET = HERE.parents[1] / "android/app/src/main/assets/russian/podcast.json"
VOWELS = "аеёиоуыэюя"
WORD_RE = re.compile(r"[А-Яа-яЁё+]+(?:-[А-Яа-яЁё+]+)*")

# Anzeige-Text aufräumen (Reste aus der Webseite der Quelle).
CLEAN = {
    "rj022_03": "Это урок двадцать второй.",
    "rj046_05": "теперь",
    "rj067_05": "Возьми(те)!",
    "rj076_06": "тысяча",
    "rj079_11": "компакт-диск / СиДи",
    "rj083_07": "«Позвони мне, позвони…»",
    "rj084_01": "работа",
    "rj088_02": "вокзал ➜ куда? ➜ на вокзал",
}

# Ganzer Satz von Hand: «не» zieht hier die Betonung auf sich, das Verb bleibt unbetont.
ITEM = {
    "rj002_08": "Н+е за что!",  # ohne Betonung auf «не» klingt es wie «Ни за что!» (auf keinen Fall)
    "rj033_11": "Вчер+а я н+е был на раб+оте.",
    "rj064_11": "К сожал+ению, прям+ого р+ейса н+е было.",
    "rj086_05": "Как интер+есно! Я там ещё никогд+а н+е был. Говор+ят, что там +очень крас+иво.",
}

# Gesprochener Text, wenn er vom angezeigten abweicht (Betonung wird danach wie üblich gesetzt).
SPEAK = {
    "rj001_06": "А вы?",
    "rj004_03": "вы",
    "rj019_01": "Как пройти?",
    "rj019_02": "Как проехать?",
    "rj021_10": "Десять минут пешком.",
    "rj026_05": "Мы пойдём в университет сегодня в девять часов.",
    "rj032_05": "Моя сестра живёт на улице Пушкина, в доме номер тридцать один, в квартире двадцать три.",
    "rj079_11": "компакт-диск, сид+и",
    "rj079_12": "дивид+и",
    "rj088_02": "вокзал. Куда? На вокзал.",
}

HYPHEN = {
    "по-немецки": "по-нем+ецки", "по-английски": "по-англ+ийски", "по-русски": "по-р+усски",
    "фитнес-клуб": "ф+итнес-клуб", "что-нибудь": "чт+о-нибудь", "что-то": "чт+о-то", "улан-уде": "улан-уд+е",
    "компакт-диск": "комп+акт-диск",
}

# Mehrdeutig im Wörterbuch — entschieden nach den Sätzen, in denen das Wort im Podcastkurs vorkommt.
WORD = {w.replace("+", ""): w for w in """
анастас+ия аэроп+орта аэропорт+у бл+изко брон+ирование брон+ировать заброн+ировать был+а б+ыли б+ыло
б+ыстро в+ечера в+илка в+илки вод+ы вр+емени выход+ить г+ода голуб+очек г+олубь г+оры г+ости далек+о
недалек+о д+арят дел+а д+еньги добр+о д+ома д+ому др+угом +еду жен+ы иван+овой как+ая как+ой клад+и
клад+у ком+у л+ожка л+ожки л+юбим л+юбите м+ало мин+ут мин+ута м+огут мо+я нач+ать н+аше н+очи од+ин
+отпуска п+арке п+исьма поезд+а позвон+им позвон+ишь п+олка п+олку полож+ите полп+ервого помог+и
помог+у пом+очь п+осле пот+ом п+очту проход+ите сейч+ас семь+ю сестр+а сестр+ы скаж+ите с+орок с+очи
сред+ам ср+еду ст+оит с+ыром твор+ог творог+ом теб+я уд+е уж+е +ужин +ужинать ур+ок хор+ошей цвет+а
цвет+ы час+а ч+ерез сид+и дивид+и
""".split()}
# Namen und Lehnwörter, die das Wörterbuch falsch oder gar nicht als Namen kennt.
WORD.update({w.replace("+", ""): w for w in """
барб+аре б+онда г+юнтером г+юнтеру крист+иной дискот+еку с+ара с+аре т+омасу к+арин медв+едевой
дани+элы изаб+ель +юля
""".split()})


def vowel_count(w):
    return sum(c in VOWELS for c in w.lower())


def clean_display(item_id, ru):
    """Großbuchstaben mitten im Wort sind in der Quelle Endungs-Markierungen, keine Schreibung."""
    ru = CLEAN.get(item_id, ru)

    def fix(m):
        w = m.group(0)
        if w.replace("+", "") in ("СиДи", "ДиВиДи") or sum(c.isupper() for c in w) < 2 and not re.search(r"[а-яё][А-ЯЁ]", w):
            return w
        return w.lower()
    return re.sub(r"[А-Яа-яЁё+]+", fix, ru)  # Teile von Bindestrich-Wörtern einzeln (Улан-Уде bleibt groß)


def load_dictionary(model_dir, words):
    """wort → {Betonungs-Ordnungszahl: Gewicht} (nur für die gesuchten Wörter)."""
    dic = collections.defaultdict(dict)
    with open(pathlib.Path(model_dir).expanduser() / "dictionary", encoding="utf-8") as f:
        for line in f:
            p = line.split(maxsplit=2)
            if len(p) == 3 and p[0] in words:
                vs = [x for x in p[2].split() if x[-1] in "01" and x[:-1] in {"a", "e", "i", "o", "u", "y"}]
                pos = next((i for i, x in enumerate(vs) if x.endswith("1")), None)
                if pos is not None:
                    dic[p[0]][pos] = max(dic[p[0]].get(pos, 0.0), float(p[1]))
    return dic


def mark(word, ordinal):
    k = -1
    for i, c in enumerate(word):
        if c.lower() in VOWELS:
            k += 1
            if k == ordinal:
                return word[:i] + "+" + word[i:]
    return word


def recase(orig, form):
    """Großschreibung des Originals auf die betonte Form übertragen."""
    out, i = "", 0
    for c in form:
        if c == "+":
            out += c
            continue
        out += c.upper() if i < len(orig) and orig[i].isupper() else c
        i += 1
    return out


def stress_text(text, dic, unresolved, where=""):
    def rep(m):
        w = m.group(0)
        low = w.lower()
        if "+" in w or "ё" in low or vowel_count(low) < 2:
            return w
        if "-" in low:
            if low in HYPHEN:
                return recase(w, HYPHEN[low])
            unresolved[low].append(where or text)  # Bindestrich-Wörter gehören in HYPHEN
            return w
        if low in WORD:
            return recase(w, WORD[low])
        cands = dic.get(low, {})
        if len(cands) == 1:
            return mark(w, next(iter(cands)))
        unresolved[low].append(where or text)
        return w
    return WORD_RE.sub(rep, text)


def words_of(course):
    out = set()
    for d in course["days"]:
        for it in d["items"]:
            for src in (clean_display(it["id"], it["ru"]), SPEAK.get(it["id"], "")):
                out.update(w.lower() for w in re.findall(r"[А-Яа-яЁё]+", src))
    return out


def apply(course, model_dir):
    """Schreibt die Betonung in course (in place). → {wort: [Fundstellen]} für offene Wörter."""
    dic = load_dictionary(model_dir, words_of(course))
    unresolved = collections.defaultdict(list)
    for d in course["days"]:
        for it in d["items"]:
            if it["id"] in ITEM:
                it["ru"] = ITEM[it["id"]]
                continue
            it["ru"] = stress_text(clean_display(it["id"], it["ru"]), dic, unresolved, f"{it['id']}: {it['ru']}")
    return unresolved


_dic_cache = {}


def speech_text(item, model_dir):
    """Was die Stimme sagt: Ziffern als Wörter, ohne Klammern, Anführungen und Pfeile — mit Betonung."""
    text = SPEAK.get(item["id"])
    if text is None:
        text = item["ru"]
    else:
        key = str(model_dir)
        if key not in _dic_cache:
            _dic_cache[key] = load_dictionary(model_dir, {w.lower() for s in SPEAK.values() for w in re.findall(r"[А-Яа-яЁё]+", s)})
        text = stress_text(text, _dic_cache[key], collections.defaultdict(list))
    text = re.sub(r"[()\"'«»„“]", "", text).replace("➜", ",")
    text = re.sub(r"^\s*(?:\.\.\.|…)\s*", "", text)
    text = re.sub(r"\s*(?:\.{2,}|…)\s*([?!]?)\s*$", lambda m: m.group(1) or ".", text)
    return re.sub(r"\s+", " ", text).strip()


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--model", required=True)
    ap.add_argument("--check", action="store_true")
    args = ap.parse_args()
    course = json.loads(ASSET.read_text())
    unresolved = apply(course, args.model)
    total = sum(len(re.findall(r"\+", it["ru"])) for d in course["days"] for it in d["items"])
    print(f"{total} Betonungen gesetzt, {len(unresolved)} Wörter offen")
    for w, where in sorted(unresolved.items()):
        print(f"  {w}: {where[0]}")
    if not args.check:
        ASSET.write_text(json.dumps(course, ensure_ascii=False, indent=2) + "\n")
    return 1 if unresolved else 0


if __name__ == "__main__":
    sys.exit(main())
