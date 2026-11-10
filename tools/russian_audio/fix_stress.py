"""
Einmalige Korrektur falsch gesetzter Betonungen (Abgleich mit dem Aussprachewörterbuch,
von Hand geprüft am 2026-11-10). Schreibt course.json sowie die Quellen in deepdive_src/
und pattern_src/ um. Danach generate_deepdive.py / generate_patterns.py / generate_missing.py laufen lassen.

  python fix_stress.py          # anwenden
  python fix_stress.py --dry    # nur zählen
"""
import pathlib, re, sys

HERE = pathlib.Path(__file__).resolve().parent
ROOT = HERE.parents[1]
FILES = [ROOT / "android/app/src/main/assets/russian/course.json",
         *sorted((HERE / "deepdive_src").glob("*.py")), *sorted((HERE / "pattern_src").glob("*.py"))]

FIX = {
    # falsch → richtig ("+" steht vor dem betonten Vokal)
    "анал+изируем": "анализ+ируем", "будн+ях": "б+уднях", "верн+ость": "в+ерность", "глубок+ое": "глуб+окое",
    "дел+овые": "делов+ые", "дешев+ле": "деш+евле", "занят+ы": "з+аняты", "им+енно": "+именно",
    "ин+огда": "иногд+а", "каж+ется": "к+ажется", "к+огда": "когд+а", "мас+ок": "м+асок",
    "масштабир+овать": "масштаб+ировать", "масштабн+о": "масшт+абно", "медл+енно": "м+едленно",
    "настр+оение": "настро+ение", "наст+упает": "наступ+ает", "н+его": "нег+о", "норм+ой": "н+ормой",
    "обид+еть": "об+идеть", "ост+аюсь": "оста+юсь", "отв+ечаешь": "отвеч+аешь", "отв+ечаю": "отвеч+аю",
    "пом+огло": "помогл+о", "пом+огу": "помог+у", "пон+ял": "п+онял", "п+оступают": "поступ+ают",
    "прав+ы": "пр+авы", "приех+ал": "при+ехал", "псих+ика": "пс+ихика", "ря+дом": "р+ядом",
    "свеж+ую": "св+ежую", "сд+аюсь": "сда+юсь", "с+ильнее": "сильн+ее", "созд+авать": "создав+ать",
    "созд+аёт": "создаёт", "сост+ояние": "состо+яние", "ст+араешься": "стар+аешься",
    "счастл+ив": "сч+астлив", "счастл+ива": "сч+астлива", "тяж+ело": "тяжел+о", "уб+еждён": "убеждён",
    "формир+уют": "форм+ируют", "хол+одно": "х+олодно", "яз+ыке": "язык+е", "ясн+ость": "+ясность",
    "д+ела": "дел+а",          # «Как дела?» — Mehrzahl; «на с+амом д+еле» ist eine andere Form
    "мысл+ения": "мышл+ения",  # Schreibfehler: das Wort heißt мышление
}
# Fehlende Betonungen (nur ganze, bisher unbetonte Wörter)
ADD = {
    "выполняешь": "выполн+яешь", "если": "+если", "насыщенная": "нас+ыщенная", "невероятно": "неверо+ятно",
    "ого": "ог+о", "открыто": "откр+ыто", "продуктивно": "продукт+ивно", "сильная": "с+ильная",
    "совсем": "совс+ем", "стратегические": "стратег+ические", "точно": "т+очно", "чувствуешь": "ч+увствуешь",
}
CYR = "А-Яа-яЁё"


def cased(orig, repl):
    first = next((c for c in orig if c != "+"), "")
    if first.isupper():
        i = 1 if repl.startswith("+") else 0
        return repl[:i] + repl[i].upper() + repl[i + 1:]
    return repl


def apply(text):
    n = 0
    # ё ist immer betont — ein zusätzliches "+" davor ist überflüssig und stört die Prüfung.
    text, n = re.subn(r"\+(?=[ёЁ])", "", text)
    guard = f"[{CYR}+]"
    for table in (FIX, ADD):
        for wrong, right in table.items():
            pat = re.compile(rf"(?<!{guard})(?<!{guard}-){re.escape(wrong)}(?!-?{guard})", re.I)

            def rep(m):
                nonlocal n
                n += 1
                return cased(m.group(0), right)
            text = pat.sub(rep, text)
    return text, n


def main():
    dry = "--dry" in sys.argv
    for f in FILES:
        src = f.read_text()
        out, n = apply(src)
        print(f"{f.relative_to(ROOT)}: {n} Korrekturen")
        if not dry and out != src:
            f.write_text(out)


if __name__ == "__main__":
    main()
