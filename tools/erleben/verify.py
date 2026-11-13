"""Prüfinstanz für »Lieder & Szenen«: ein Sprachmodell, das die automatische Aufbereitung gegenliest.

Grundsatz: Das Modell ist nie die einzige Quelle. Es schreibt keinen russischen Text frei um und
erfindet keine Wortbedeutungen — es entscheidet zwischen Vorlagen (zwei Spracherkenner, Einträge
des Wörterbuchs, zwei Übersetzungen) und sagt »weiß nicht«, wenn nichts passt. Jede Antwort hat
ein festes, winziges Format und wird von Code nachgeprüft; was nicht passt, gilt als ungeprüft
und wird dem Lernenden nicht gezeigt.

Welches Modell antwortet, bestimmt das »Backend«:
  cloud.CloudBackend  — kostenlose Cloud-Modelle, mehrere Anbieter in Reihe (siehe cloud.py)
  LocalBackend        — Qwen 3.8 27B direkt über MLX. Am 14.11.2026 gemessen und verworfen:
                        erkennt 12 von 15 Hörfehlern, aber 5 von 8 eigenen Korrekturen waren falsch.
Jedes neue Modell wird zuerst mit eval_verify.py an Zeilen gemessen, deren Wahrheit bekannt ist.
"""
import re
import time
from pathlib import Path

MODELS = Path.home() / ".lmstudio" / "models" / "lmstudio-community"
VARIANTS = {"8bit": MODELS / "Qwen3.8-27B-MLX-8bit", "4bit": MODELS / "Qwen3.8-27B-MLX-4bit"}

SOURCE = {"song": "a song", "scene": "a film or cartoon scene"}

PLAUSIBLE = """You check Russian text that a speech recogniser produced from {source}. The recogniser sometimes mishears: it writes words that do not exist, glues or splits words wrongly, or picks a wrong ending.

Decide whether LINE is what a native speaker would really say or sing here.
- Colloquial, poetic, old-fashioned and dialect Russian is fine. Incomplete sentences are fine. Names are fine. Missing or odd punctuation and capital letters are fine. The letter е instead of ё is fine.
- A line is wrong only if it contains a word that is not Russian, a word that makes no sense in this place, or a grammatical form that cannot be right here.

Answer with exactly one word: OK or BAD."""

ARBITER = """Two speech recognisers heard the same line of {source} differently. Decide which version is what was really said. Judge by Russian grammar, real words and the context.

Answer with exactly one character: A, B, or ? if you cannot decide or both are wrong."""

TRANSLATE = """You translate Russian lines from {source} into German for a German learner of Russian.
- Translate each line faithfully and naturally. Do not add or leave out anything. Keep names.
- Use the other lines as context, but translate line by line: line 3 in Russian is line 3 in German.
- Informal ты is »du«, polite вы is »Sie«.

Answer with the same numbering, one German line per number, nothing else:
1: ...
2: ..."""

SAME = """Two German translations of the same Russian line. Do they say the same thing? Small differences in wording, word order, articles or style do not matter. They differ only if the meaning differs: another fact, another person, another action, something missing or added.

Answer with exactly one word: SAME or DIFFERENT."""

GLOSS = """A German learner reads a Russian line word by word. For each numbered word, pick the dictionary meaning that fits THIS line. The German translation of the whole line is given.

Answer with one line per word, nothing else:  <word number>: <option letter>
Pick »x« only if none of the options fits the word in this line."""

STORY = """You get the German translation of a short excerpt from {source}, titled »{title}«. Write one or two plain German sentences that say what happens or what it is about, for someone about to watch it. Only what the lines themselves say. No opinion, no praise, no guessing about what comes before or after.

Answer with the sentences only."""


def words_of(text: str) -> list[str]:
    return [w.lower().replace("ё", "е") for w in re.findall(r"[А-Яа-яЁё]+(?:-[А-Яа-яЁё]+)*", text)]


def edit_distance(a: str, b: str) -> int:
    prev = list(range(len(b) + 1))
    for i, ca in enumerate(a, 1):
        cur = [i]
        for j, cb in enumerate(b, 1):
            cur.append(min(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + (ca != cb)))
        prev = cur
    return prev[-1]


def close_fix(heard: str, fixed: str) -> bool:
    """Eine Korrektur gilt nur, wenn sie fast gleich klingt: höchstens zwei Wörter berührt, je Wort
    wenige Buchstaben, höchstens ein Wort mehr oder weniger. Sonst ist es eine Erfindung."""
    a, b = words_of(heard), words_of(fixed)
    if not b or abs(len(a) - len(b)) > 1:
        return False
    if len(a) == len(b):
        diffs = [(x, y) for x, y in zip(a, b) if x != y]
        return 0 < len(diffs) <= 2 and all(edit_distance(x, y) <= max(2, len(x) // 3) for x, y in diffs)
    # Ein Wort mehr oder weniger: der Rest muss gleich sein und das Wort kurz (»вы«, »мы«, »и«).
    longer, shorter = (a, b) if len(a) > len(b) else (b, a)
    for k in range(len(longer)):
        if longer[:k] + longer[k + 1:] == shorter:
            return len(longer[k]) <= 3
    return False


class LocalBackend:
    """Qwen direkt über MLX, ohne LM Studio. Geladen erst beim ersten Aufruf, frei beim Prozessende."""

    def __init__(self, variant: str = "8bit", log=lambda *a: None):
        self.path = VARIANTS[variant]
        self.name = f"qwen-{variant}"
        self.log = log
        self.model = None
        self.tok = None
        self._prefix: dict[str, dict] = {}

    def _load(self):
        if self.model is None:
            from mlx_lm import load
            t = time.time()
            self.model, self.tok = load(str(self.path))
            self.log(f"Prüfmodell {self.name} geladen in {time.time() - t:.1f} s")

    def _ids(self, system: str, user: str) -> list[int]:
        msgs = [{"role": "system", "content": system}, {"role": "user", "content": user}]
        try:
            text = self.tok.apply_chat_template(msgs, add_generation_prompt=True, tokenize=False, enable_thinking=False)
        except TypeError:
            text = self.tok.apply_chat_template(msgs, add_generation_prompt=True, tokenize=False)
        return self.tok.encode(text, add_special_tokens=False)

    def ask(self, system: str, user: str, max_tokens: int = 120) -> str:
        """Die Anweisung (immer gleich) wird nur beim ersten Mal durchgerechnet und danach aus dem
        Zwischenspeicher genommen — die Antworten sind nur wenige Wörter lang."""
        self._load()
        from mlx_lm import stream_generate
        from mlx_lm.models.cache import can_trim_prompt_cache, make_prompt_cache, trim_prompt_cache
        from mlx_lm.sample_utils import make_sampler
        ids = self._ids(system, user)
        entry = self._prefix.get(system)
        if entry is None:
            other = self._ids(system, "¤")
            k = 0
            while k < min(len(ids), len(other)) and ids[k] == other[k]:
                k += 1
            cache = make_prompt_cache(self.model)
            if k > 8 and can_trim_prompt_cache(cache):
                for _ in stream_generate(self.model, self.tok, ids[:k], max_tokens=1, prompt_cache=cache):
                    pass
                trim_prompt_cache(cache, 1)
                entry = {"n": k, "cache": cache}
            else:
                entry = {"n": 0, "cache": None}
            self._prefix[system] = entry
        out, n = "", 0
        sampler = make_sampler(temp=0.0)
        if entry["cache"] is not None:
            rest = ids[entry["n"]:]
            for r in stream_generate(self.model, self.tok, rest, max_tokens=max_tokens, sampler=sampler, prompt_cache=entry["cache"]):
                out += r.text
                n = r.generation_tokens
            trim_prompt_cache(entry["cache"], len(rest) + n)
        else:
            for r in stream_generate(self.model, self.tok, ids, max_tokens=max_tokens, sampler=sampler):
                out += r.text
        return out


class Reviewer:
    def __init__(self, backend, log=lambda *a: None):
        self.backend = backend
        self.log = log
        self.calls = 0
        self.seconds = 0.0

    def ask(self, system: str, user: str, max_tokens: int = 120) -> str:
        t = time.time()
        out = self.backend.ask(system, user, max_tokens)
        self.calls += 1
        self.seconds += time.time() - t
        return re.sub(r"<think>.*?</think>", "", out, flags=re.S).strip()

    # ── Was wurde gesagt? ───────────────────────────────────────────────────

    def plausible(self, line: str, before: str, after: str, kind: str) -> bool:
        """Klingt die Zeile wie echtes Russisch an dieser Stelle? Das Modell urteilt nur — eine
        Korrektur schlägt es nicht vor (gemessen: 5 von 8 seiner Korrekturen waren selbst falsch)."""
        user = f"BEFORE: {before or '—'}\nLINE: {line}\nAFTER: {after or '—'}"
        return self.ask(PLAUSIBLE.format(source=SOURCE[kind]), user, 4).strip().upper().startswith("OK")

    def arbitrate(self, a: str, b: str, before: str, after: str, kind: str) -> str | None:
        """Welche der beiden Fassungen stimmt? → "a" | "b" | None. Gefragt wird in beiden
        Reihenfolgen; nur wenn das Modell beide Male dieselbe Fassung wählt, gilt die Antwort."""
        def once(x, y):
            user = f"BEFORE: {before or '—'}\nA: {x}\nB: {y}\nAFTER: {after or '—'}"
            return self.ask(ARBITER.format(source=SOURCE[kind]), user, 4).strip().upper()[:1]
        first, second = once(a, b), once(b, a)
        if first == "A" and second == "B":
            return "a"
        if first == "B" and second == "A":
            return "b"
        return None

    # ── Was bedeutet es? ────────────────────────────────────────────────────

    def translate(self, lines: list[str], kind: str) -> list[str] | None:
        user = "\n".join(f"{i + 1}: {l}" for i, l in enumerate(lines))
        ans = self.ask(TRANSLATE.format(source=SOURCE[kind]), user, 60 + 40 * len(lines))
        got = {int(m.group(1)): m.group(2).strip() for m in re.finditer(r"^\s*(\d+)\s*[:.)]\s*(.+)$", ans, re.M)}
        if sorted(got) != list(range(1, len(lines) + 1)):
            return None
        return [got[i + 1] for i in range(len(lines))]

    def same_meaning(self, ru: str, a: str, b: str) -> bool:
        ans = self.ask(SAME, f"RUSSIAN: {ru}\nA: {a}\nB: {b}", 4).strip().upper()
        return ans.startswith("SAME")

    def pick_glosses(self, ru: str, de: str, options: list[tuple[str, list[str]]]) -> list[int | None] | None:
        """options: je Wort (Wort, [Bedeutungen]). → je Wort der Index der passenden Bedeutung, None = keine passt.
        Wörter mit nur einer Bedeutung werden nicht gefragt."""
        ask_idx = [i for i, (_, opts) in enumerate(options) if len(opts) > 1]
        result: list[int | None] = [0 if opts else None for _, opts in options]
        if not ask_idx:
            return result
        letters = "abcdefgh"
        rows = []
        for n, i in enumerate(ask_idx, 1):
            w, opts = options[i]
            rows.append(f"{n}. {w}: " + "  ".join(f"{letters[k]}) {o}" for k, o in enumerate(opts[:8])))
        ans = self.ask(GLOSS, f"LINE: {ru}\nGERMAN: {de}\n\n" + "\n".join(rows), 8 * len(ask_idx) + 10)
        got = {int(m.group(1)): m.group(2).lower() for m in re.finditer(r"^\s*(\d+)\s*[:.)]\s*([a-hx])\b", ans, re.M)}
        if sorted(got) != list(range(1, len(ask_idx) + 1)):
            return None
        for n, i in enumerate(ask_idx, 1):
            pick = got[n]
            k = letters.index(pick) if pick != "x" else -1
            result[i] = k if 0 <= k < len(options[i][1][:8]) else None
        return result

    def story(self, title: str, german: list[str], kind: str) -> str:
        ans = self.ask(STORY.format(source=SOURCE[kind], title=title), "\n".join(german), 90)
        ans = " ".join(ans.split())
        # Zwei Sätze, keine Aufzählung, keine Anführung des Auftrags.
        sentences = re.findall(r"[^.!?]+[.!?]", ans)[:2]
        return " ".join(s.strip() for s in sentences) if sentences else ""

    def stats(self) -> dict:
        return {"modell": getattr(self.backend, "name", "?"), "aufrufe": self.calls, "sekunden": round(self.seconds, 1),
                "s_je_aufruf": round(self.seconds / self.calls, 2) if self.calls else 0}
