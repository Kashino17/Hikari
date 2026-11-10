package com.hikari.app.domain.feed

import com.hikari.app.domain.russian.RuLabWord
import com.hikari.app.domain.russian.RuLessonBuilder
import com.hikari.app.domain.russian.RuPhrase
import com.hikari.app.domain.russian.RussianPhonetics
import kotlin.random.Random

/**
 * Ein Mini-Spiel im Feed. Anders als die Wissenskarten kommt es nicht aus einer Datei,
 * sondern wird jedes Mal neu erzeugt — es gibt also nie zweimal dieselbe Aufgabe.
 */
sealed interface FeedGame {
    val id: String
    val module: MindfulModuleType
    /** Beschriftung des Chips über dem Spiel. */
    val label: String
}

/** Wie die Frage einer Auswahl-Aufgabe gesetzt wird. */
enum class ChoiceStyle { NUMBERS, LETTER, WORD, PHRASE, LISTEN, TEXT }

/** Verknüpft eine Übung mit dem Satz im Russisch-Kurs, damit die Antwort dort zählt. */
data class RuGrade(val phraseId: String, val due: Boolean)

/** Eine Frage, vier Antworten — Zahlenreihen, Buchstaben, Lesewörter, Sätze, Hörübungen. */
data class ChoiceGame(
    override val id: String,
    override val module: MindfulModuleType,
    override val label: String,
    val style: ChoiceStyle,
    val prompt: String,
    /** Groß gesetzter Gegenstand der Frage (Zahlenreihe, Buchstabe, Wort, Satz). */
    val display: String? = null,
    val options: List<String>,
    /** Kleine zweite Zeile je Antwort (z. B. Umschrift). */
    val optionSubs: List<String> = emptyList(),
    val correctIndex: Int,
    val explanation: String = "",
    /** Clip zur Frage. Bei LISTEN ist er die Frage, sonst eine Hörhilfe nach der Antwort. */
    val audio: String? = null,
    /** Russischer Text (mit "+"), der nach der Antwort aufgeschlüsselt wird. */
    val revealRu: String? = null,
    val revealDe: String? = null,
    val grade: RuGrade? = null,
    /** Geübter Buchstabe — die Antwort zählt für den Alphabet-Lernstand. */
    val letter: String? = null,
    /** Umschrift unter dem russischen Text der Frage — Lesehilfe, solange das Alphabet nicht sitzt. */
    val displaySub: String? = null,
    /** Woher der Satz stammt und auf welchem Niveau geübt wird. */
    val context: String? = null,
) : FeedGame

/** Stellt einen neuen Buchstaben vor und fragt ihn gleich einmal ab. */
data class LetterIntroGame(
    override val id: String,
    val letter: RuLetter,
    val group: RuLetterGroup?,
    /** Der wievielte Buchstabe das ist (1-basiert) und wie viele es gibt. */
    val position: Int,
    val total: Int,
    val options: List<String>,
    val correctIndex: Int,
) : FeedGame {
    override val module = MindfulModuleType.LANGUAGE
    override val label = "KYRILLISCH · NEUER BUCHSTABE"
}

/** Satz aus Wortkacheln zusammensetzen. */
data class BuildGame(
    override val id: String,
    val de: String,
    val ru: String,
    val answer: List<String>,
    val tiles: List<String>,
    val audio: String?,
    val grade: RuGrade?,
    val context: String? = null,
) : FeedGame {
    override val module = MindfulModuleType.LANGUAGE
    override val label = "RUSSISCH · SATZBAU"
}

data class GamePair(val left: String, val right: String, val audio: String? = null, val leftSub: String? = null)

/** Paare zuordnen: links Russisch oder Buchstaben, rechts die Bedeutung oder der Laut. */
data class PairsGame(
    override val id: String,
    override val label: String,
    val prompt: String,
    val pairs: List<GamePair>,
    /** Reihenfolge der rechten Spalte (Indizes in [pairs]). */
    val rightOrder: List<Int>,
    /** Große Buchstaben statt Wörter in der linken Spalte. */
    val letters: Boolean = false,
) : FeedGame {
    override val module = MindfulModuleType.LANGUAGE
}

/** Eine Runde des Farb-Wort-Tests: Das Wort [word] ist in der Farbe [ink] geschrieben. */
data class StroopRound(val word: Int, val ink: Int)

/** Farb-Wort-Test: die Schriftfarbe benennen, nicht das Wort lesen. */
data class StroopGame(override val id: String, val rounds: List<StroopRound>) : FeedGame {
    override val module = MindfulModuleType.BRAIN_PUZZLE
    override val label = "FARB-WORT-TEST"

    companion object {
        val COLOR_NAMES = listOf("Rot", "Blau", "Grün", "Gelb")
    }
}

/** Muster merken: [cells] leuchten kurz auf einem [size]×[size]-Feld auf. */
data class MemoryGridGame(
    override val id: String,
    val size: Int,
    val cells: Set<Int>,
    val level: Int,
) : FeedGame {
    override val module = MindfulModuleType.BRAIN_PUZZLE
    override val label = "MUSTERGEDÄCHTNIS"
}

/** Kettenrechnen: Die Schritte erscheinen nacheinander, gerechnet wird im Kopf. */
data class ChainMathGame(
    override val id: String,
    val start: Int,
    val steps: List<String>,
    /** Zwischenstand nach jedem Schritt — für die Auflösung. */
    val trail: List<Int>,
    val result: Int,
    val options: List<Int>,
) : FeedGame {
    override val module = MindfulModuleType.SPEED_MATH
    override val label = "KETTENRECHNEN"
    val correctIndex: Int get() = options.indexOf(result)
}

/** Adlerauge: in einem Feld gleicher Zeichen das eine andere finden. */
data class OddOneOutGame(
    override val id: String,
    val common: String,
    val odd: String,
    val columns: Int,
    val rows: Int,
    val oddIndex: Int,
    /** Was man aus dem Unterschied lernt (bei kyrillischen Zeichen die Laute). */
    val note: String,
) : FeedGame {
    override val module = MindfulModuleType.BRAIN_PUZZLE
    override val label = "ADLERAUGE"
}

/** Zahlen merken: Die Ziffern erscheinen nacheinander und werden danach eingetippt. */
data class DigitSpanGame(override val id: String, val digits: List<Int>, val level: Int) : FeedGame {
    override val module = MindfulModuleType.BRAIN_PUZZLE
    override val label = "ZAHLEN MERKEN"
}

/** Reihenfolge merken: Felder leuchten nacheinander auf und werden in derselben Folge angetippt. */
data class SequenceGame(override val id: String, val size: Int, val order: List<Int>, val level: Int) : FeedGame {
    override val module = MindfulModuleType.BRAIN_PUZZLE
    override val label = "REIHENFOLGE"
}

/** Buchstabensalat: aus gemischten Buchstaben das Wort legen. */
data class AnagramGame(override val id: String, val word: String, val letters: List<Char>, val hint: String) : FeedGame {
    override val module = MindfulModuleType.BRAIN_PUZZLE
    override val label = "BUCHSTABENSALAT"
}

/** Blitzblick: Punkte blitzen kurz auf — wie viele waren es? Positionen im Einheitsquadrat. */
data class DotsGame(
    override val id: String,
    val dots: List<Pair<Float, Float>>,
    val options: List<Int>,
) : FeedGame {
    override val module = MindfulModuleType.BRAIN_PUZZLE
    override val label = "BLITZBLICK"
    val correctIndex: Int get() = options.indexOf(dots.size)
}

/** Erzeugt die Spiele (pur, mit vorgegebenem Zufall testbar). */
object FeedGameFactory {

    private fun nonce(rng: Random) = rng.nextInt(0, Int.MAX_VALUE).toString(36)

    // ── Zahlenreihe ─────────────────────────────────────────────────────────

    internal data class Series(val shown: List<Int>, val next: Int, val rule: String)

    internal fun series(rng: Random): Series = when (rng.nextInt(7)) {
        0 -> {
            val a = rng.nextInt(2, 30)
            val d = rng.nextInt(3, 13)
            val t = List(6) { a + d * it }
            Series(t.take(5), t[5], "Jede Zahl ist um $d größer als die davor.")
        }
        1 -> {
            val a = rng.nextInt(1, 6)
            val q = if (rng.nextBoolean()) 2 else 3
            var v = a
            val t = List(6) { i -> if (i == 0) v else { v *= q; v } }
            Series(t.take(5), t[5], "Jede Zahl ist das ${if (q == 2) "Doppelte" else "Dreifache"} der vorigen.")
        }
        2 -> {
            val a = rng.nextInt(1, 15)
            val s = rng.nextInt(1, 4)
            var v = a
            val t = List(7) { i -> if (i == 0) v else { v += s * i; v } }
            Series(t.take(6), t[6], "Die Abstände wachsen: +$s, +${2 * s}, +${3 * s} und so weiter.")
        }
        3 -> {
            val up = rng.nextInt(5, 13)
            val down = rng.nextInt(1, 5)
            var v = rng.nextInt(3, 20)
            val t = List(7) { i -> if (i == 0) v else { v += if (i % 2 == 1) up else -down; v } }
            Series(t.take(6), t[6], "Abwechselnd +$up und −$down.")
        }
        4 -> {
            val n = rng.nextInt(1, 7)
            val k = rng.nextInt(0, 4)
            val t = List(6) { (n + it) * (n + it) + k }
            Series(
                t.take(5), t[5],
                if (k == 0) "Das sind die Quadratzahlen: $n², ${n + 1}², ${n + 2}² und so weiter."
                else "Quadratzahlen plus $k: $n² + $k, ${n + 1}² + $k und so weiter.",
            )
        }
        5 -> {
            val t = mutableListOf(rng.nextInt(1, 6), rng.nextInt(1, 7))
            repeat(5) { t += t[t.size - 1] + t[t.size - 2] }
            Series(t.take(6), t[6], "Jede Zahl ist die Summe der beiden davor.")
        }
        else -> {
            val add = rng.nextInt(1, 5)
            var v = rng.nextInt(1, 6)
            val t = List(7) { i -> if (i == 0) v else { v = if (i % 2 == 1) v * 2 else v + add; v } }
            Series(t.take(6), t[6], "Abwechselnd verdoppeln und $add dazuzählen.")
        }
    }

    /** Die richtige Zahl plus drei, die nah genug liegen, um nicht sofort auszuscheiden. */
    internal fun numberOptions(correct: Int, rng: Random): List<Int> {
        val offsets = listOf(-10, -6, -4, -3, -2, -1, 1, 2, 3, 4, 6, 10).shuffled(rng)
        val wrong = offsets.map { correct + it }.filter { it > 0 }.distinct().take(3)
        return (wrong + correct).shuffled(rng)
    }

    fun numberSeries(rng: Random): ChoiceGame {
        val s = series(rng)
        val options = numberOptions(s.next, rng)
        return ChoiceGame(
            id = "game:series:${nonce(rng)}",
            module = MindfulModuleType.BRAIN_PUZZLE,
            label = "ZAHLENREIHE",
            style = ChoiceStyle.NUMBERS,
            prompt = "Welche Zahl kommt als Nächstes?",
            display = s.shown.joinToString("  ·  ") + "  ·  ?",
            options = options.map { it.toString() },
            correctIndex = options.indexOf(s.next),
            explanation = s.rule,
        )
    }

    // ── Kettenrechnen ───────────────────────────────────────────────────────

    fun chainMath(rng: Random, stepCount: Int = 4): ChainMathGame {
        val start = rng.nextInt(4, 16)
        var v = start
        val steps = mutableListOf<String>()
        val trail = mutableListOf<Int>()
        var lastKind = -1
        repeat(stepCount) {
            val kinds = buildList {
                add(0)
                if (v > 6) add(1)
                if (v <= 40) add(2)
                if (v % 2 == 0 && v >= 8) add(3)
            }.filter { it != lastKind || it == 0 }
            when (kinds.random(rng).also { lastKind = it }) {
                0 -> { val n = rng.nextInt(3, 16); v += n; steps += "+ $n" }
                1 -> { val n = rng.nextInt(2, minOf(10, v - 1)); v -= n; steps += "− $n" }
                2 -> { val n = if (v <= 20 && rng.nextBoolean()) 3 else 2; v *= n; steps += "× $n" }
                else -> { v /= 2; steps += "÷ 2" }
            }
            trail += v
        }
        return ChainMathGame("game:chain:${nonce(rng)}", start, steps, trail, v, numberOptions(v, rng))
    }

    // ── Farb-Wort-Test ──────────────────────────────────────────────────────

    fun stroop(rng: Random, rounds: Int = 8): StroopGame {
        val colors = StroopGame.COLOR_NAMES.indices.toList()
        var lastInk = -1
        val list = List(rounds) {
            val ink = colors.filter { it != lastInk }.random(rng).also { lastInk = it }
            // Meist widersprechen sich Wort und Farbe — genau das macht die Aufgabe schwer.
            val word = if (rng.nextInt(5) == 0) ink else colors.filter { it != ink }.random(rng)
            StroopRound(word, ink)
        }
        return StroopGame("game:stroop:${nonce(rng)}", list)
    }

    // ── Mustergedächtnis ────────────────────────────────────────────────────

    const val MEMORY_MIN_LEVEL = 3
    const val MEMORY_MAX_LEVEL = 9

    fun memoryGrid(rng: Random, level: Int): MemoryGridGame {
        val lv = level.coerceIn(MEMORY_MIN_LEVEL, MEMORY_MAX_LEVEL)
        val size = 4
        val cells = (0 until size * size).shuffled(rng).take(lv).toSet()
        return MemoryGridGame("game:memory:${nonce(rng)}", size, cells, lv)
    }

    // ── Adlerauge ───────────────────────────────────────────────────────────

    private val LATIN_PAIRS = listOf("O" to "Q", "E" to "F", "M" to "N", "b" to "d", "p" to "q", "8" to "3", "6" to "9", "C" to "G", "V" to "U")
    private val CYRILLIC_PAIRS = listOf("ш" to "щ", "и" to "й", "ь" to "ъ", "е" to "ё", "л" to "п", "ц" to "щ", "з" to "э", "б" to "в", "ь" to "ы", "н" to "и", "г" to "т", "о" to "ю")

    fun oddOneOut(rng: Random, alphabet: RuAlphabet? = null): OddOneOutGame {
        val columns = 6
        val rows = 6
        val index = rng.nextInt(columns * rows)
        val cyrillic = alphabet != null && alphabet.letters.isNotEmpty()
        val (a, b) = (if (cyrillic) CYRILLIC_PAIRS else LATIN_PAIRS).random(rng).let { if (rng.nextBoolean()) it else it.second to it.first }
        val note = if (cyrillic) {
            fun describe(l: String): String {
                val letter = alphabet!!.letter(l) ?: return l.uppercase()
                return if (letter.hasSound) "${letter.u} klingt wie »${letter.sound}«" else "${letter.u} ist das ${if (l == "ь") "Weichheitszeichen" else "Härtezeichen"}"
            }
            "${describe(a)}, ${describe(b)}."
        } else {
            "Solche Suchaufgaben trainieren, Wichtiges von fast Gleichem zu trennen."
        }
        val show: (String) -> String = { if (cyrillic) it.uppercase() else it }
        return OddOneOutGame("game:odd:${nonce(rng)}", show(a), show(b), columns, rows, index, note)
    }

    // ── Alphabet ────────────────────────────────────────────────────────────

    /** Laut und Eselsbrücke als eine Antwortzeile. */
    internal fun soundOption(letter: RuLetter) = "${letter.sound}  ·  ${letter.like}"

    /** Lateinische Doppelgänger: Wer В sieht, denkt an b — genau diese Antwort muss zur Wahl stehen. */
    private val TRAPS = mapOf("в" to "б", "р" to "п", "с" to "к", "у" to "й", "н" to "х", "х" to "к", "е" to "э", "г" to "р")

    private fun soundDistractors(alphabet: RuAlphabet, letter: RuLetter, rng: Random): List<RuLetter> {
        val pool = alphabet.letters.filter { it.hasSound && it.l != letter.l && it.sound != letter.sound }
        val trap = TRAPS[letter.l]?.let { t -> pool.firstOrNull { it.l == t } }
        return (listOfNotNull(trap) + pool.shuffled(rng)).distinctBy { it.sound }.take(3)
    }

    private const val SIGN_SOFT = "Macht den Konsonanten davor weich"
    private const val SIGN_HARD = "Trennt mit einer winzigen Pause"

    fun letterIntro(alphabet: RuAlphabet, letter: RuLetter, rng: Random): LetterIntroGame {
        // Zeichen ohne Laut (ь, ъ): gefragt wird, was sie bewirken.
        val correct = when {
            letter.hasSound -> letter.sound
            letter.l == "ь" -> SIGN_SOFT
            else -> SIGN_HARD
        }
        val options = if (letter.hasSound) {
            (soundDistractors(alphabet, letter, rng) + letter).shuffled(rng).map { it.sound }
        } else {
            listOf(SIGN_SOFT, SIGN_HARD, "Klingt wie ein kurzes i", "Verdoppelt den Vokal danach").shuffled(rng)
        }
        return LetterIntroGame(
            id = "game:letter:${letter.l}:${nonce(rng)}",
            letter = letter,
            group = alphabet.group(letter.group),
            position = alphabet.letters.indexOf(letter) + 1,
            total = alphabet.letters.size,
            options = options,
            correctIndex = options.indexOf(correct),
        )
    }

    /** Buchstabe sehen → Laut wählen. */
    fun letterSound(alphabet: RuAlphabet, letter: RuLetter, rng: Random): ChoiceGame {
        val options = (soundDistractors(alphabet, letter, rng) + letter).shuffled(rng)
        return ChoiceGame(
            id = "game:abc-sound:${letter.l}:${nonce(rng)}",
            module = MindfulModuleType.LANGUAGE,
            label = "KYRILLISCH · BUCHSTABE",
            style = ChoiceStyle.LETTER,
            prompt = "Wie klingt dieser Buchstabe?",
            display = "${letter.u} ${letter.l}",
            options = options.map { soundOption(it) },
            correctIndex = options.indexOf(letter),
            explanation = letter.tip,
            letter = letter.l,
        )
    }

    /** Laut lesen → Buchstaben wählen. */
    fun soundLetter(alphabet: RuAlphabet, letter: RuLetter, rng: Random): ChoiceGame {
        val options = (soundDistractors(alphabet, letter, rng) + letter).shuffled(rng)
        return ChoiceGame(
            id = "game:abc-letter:${letter.l}:${nonce(rng)}",
            module = MindfulModuleType.LANGUAGE,
            label = "KYRILLISCH · LAUT",
            style = ChoiceStyle.LETTER,
            prompt = "Welcher Buchstabe klingt wie »${letter.sound}« (${letter.like})?",
            options = options.map { "${it.u} ${it.l}" },
            correctIndex = options.indexOf(letter),
            explanation = letter.tip,
            letter = letter.l,
        )
    }

    /** Ein Wort entziffern, das man aus dem Deutschen kennt. */
    fun readWord(word: RuLabWord, others: List<RuLabWord>, rng: Random): ChoiceGame? {
        val wrong = others.filter { it.de != word.de && it.ru != word.ru }.distinctBy { it.de }.shuffled(rng).take(3)
        if (wrong.size < 3) return null
        val options = (wrong + word).shuffled(rng)
        return ChoiceGame(
            id = "game:abc-read:${word.plain}:${nonce(rng)}",
            module = MindfulModuleType.LANGUAGE,
            label = "KYRILLISCH · LESEN",
            style = ChoiceStyle.WORD,
            prompt = "Was steht hier?",
            display = word.display,
            options = options.map { it.de },
            correctIndex = options.indexOf(word),
            explanation = "Gelesen: ${RussianPhonetics.transliterateFlat(word.ru)}",
            audio = word.clip,
            revealRu = word.ru,
            revealDe = word.de,
        )
    }

    fun letterPairs(letters: List<RuLetter>, rng: Random): PairsGame? {
        val chosen = letters.filter { it.hasSound }.distinctBy { it.sound }.shuffled(rng).take(4)
        if (chosen.size < 4) return null
        return PairsGame(
            id = "game:abc-pairs:${nonce(rng)}",
            label = "KYRILLISCH · PAARE",
            prompt = "Welcher Laut gehört zu welchem Buchstaben?",
            pairs = chosen.map { GamePair("${it.u} ${it.l}", it.sound) },
            rightOrder = chosen.indices.shuffled(rng),
            letters = true,
        )
    }

    // ── Russisch-Übungen aus dem Kurs ───────────────────────────────────────

    /** Bedeutung ohne Klammerzusätze — die verraten sonst die Lösung. */
    internal fun cleanDe(de: String) = de.replace(Regex("\\s*\\([^)]*\\)"), "").trim()

    /** Russisch lesen → deutsche Bedeutung wählen. */
    fun meaning(p: RuPhrase, pool: List<RuPhrase>, due: Boolean, rng: Random): ChoiceGame? {
        val wrong = RuLessonBuilder.distractors(p, pool, rng)
        if (wrong.size < 3) return null
        val options = (wrong + p).shuffled(rng)
        return ChoiceGame(
            id = "game:ru-meaning:${p.id}:${nonce(rng)}",
            module = MindfulModuleType.LANGUAGE,
            label = if (due) "RUSSISCH · WIEDERHOLUNG" else "RUSSISCH · ÜBUNG",
            style = ChoiceStyle.PHRASE,
            prompt = "Was bedeutet das?",
            display = p.display,
            displaySub = RussianPhonetics.transliterateFlat(p.ru),
            options = options.map { cleanDe(it.de) },
            correctIndex = options.indexOf(p),
            audio = p.audio,
            revealRu = p.ru,
            revealDe = p.de,
            grade = RuGrade(p.id, due),
        )
    }

    /** Deutsch lesen → russischen Satz wählen. */
    fun recall(p: RuPhrase, pool: List<RuPhrase>, due: Boolean, rng: Random): ChoiceGame? {
        val wrong = RuLessonBuilder.distractors(p, pool, rng)
        if (wrong.size < 3) return null
        val options = (wrong + p).shuffled(rng)
        return ChoiceGame(
            id = "game:ru-recall:${p.id}:${nonce(rng)}",
            module = MindfulModuleType.LANGUAGE,
            label = if (due) "RUSSISCH · WIEDERHOLUNG" else "RUSSISCH · ÜBUNG",
            style = ChoiceStyle.PHRASE,
            prompt = "Wie sagst du das auf Russisch?",
            display = cleanDe(p.de),
            options = options.map { it.display },
            optionSubs = options.map { RussianPhonetics.transliterateFlat(it.ru) },
            correctIndex = options.indexOf(p),
            audio = p.audio,
            revealRu = p.ru,
            revealDe = p.de,
            grade = RuGrade(p.id, due),
        )
    }

    /** Russisch hören → deutsche Bedeutung wählen. */
    fun listen(p: RuPhrase, pool: List<RuPhrase>, due: Boolean, rng: Random): ChoiceGame? {
        val wrong = RuLessonBuilder.distractors(p, pool, rng)
        if (wrong.size < 3) return null
        val options = (wrong + p).shuffled(rng)
        return ChoiceGame(
            id = "game:ru-listen:${p.id}:${nonce(rng)}",
            module = MindfulModuleType.LANGUAGE,
            label = if (due) "RUSSISCH · WIEDERHOLUNG" else "RUSSISCH · HÖREN",
            style = ChoiceStyle.LISTEN,
            prompt = "Was hörst du?",
            options = options.map { cleanDe(it.de) },
            correctIndex = options.indexOf(p),
            audio = p.audio,
            revealRu = p.ru,
            revealDe = p.de,
            grade = RuGrade(p.id, due),
        )
    }

    /** Satz aus Kacheln bauen — nur für Sätze mit zwei bis sechs Wörtern sinnvoll. */
    fun build(p: RuPhrase, pool: List<RuPhrase>, due: Boolean, rng: Random): BuildGame? {
        if (p.wordCount !in 2..6) return null
        val ex = RuLessonBuilder.build(p, pool, rng)
        if (ex.answer.size < 2) return null
        return BuildGame(
            id = "game:ru-build:${p.id}:${nonce(rng)}",
            de = cleanDe(p.de),
            ru = p.ru,
            answer = ex.answer,
            tiles = ex.tiles,
            audio = p.audio,
            grade = RuGrade(p.id, due),
        )
    }

    fun phrasePairs(phrases: List<RuPhrase>, rng: Random): PairsGame? {
        val chosen = mutableListOf<RuPhrase>()
        for (p in phrases.filter { it.wordCount <= 3 }.shuffled(rng)) {
            if (chosen.size == 4) break
            if (chosen.any { RuLessonBuilder.confusable(it.de, p.de) || it.plain == p.plain }) continue
            chosen += p
        }
        if (chosen.size < 4) return null
        return PairsGame(
            id = "game:ru-pairs:${nonce(rng)}",
            label = "RUSSISCH · PAARE",
            prompt = "Was gehört zusammen?",
            pairs = chosen.map { GamePair(it.display, cleanDe(it.de), it.audio, RussianPhonetics.transliterateFlat(it.ru)) },
            rightOrder = chosen.indices.shuffled(rng),
        )
    }

    // ── Zahlen merken, Reihenfolge ──────────────────────────────────────────

    const val DIGITS_START = 5
    const val DIGITS_MIN = 4
    const val DIGITS_MAX = 10
    const val SEQUENCE_START = 4
    const val SEQUENCE_MIN = 3
    const val SEQUENCE_MAX = 9

    /** Zufallsfolge ohne zwei gleiche Werte hintereinander. */
    private fun run(rng: Random, length: Int, values: Int): List<Int> {
        val out = mutableListOf<Int>()
        repeat(length) { out += generateSequence { rng.nextInt(values) }.first { it != out.lastOrNull() } }
        return out
    }

    fun digitSpan(rng: Random, level: Int): DigitSpanGame {
        val lv = level.coerceIn(DIGITS_MIN, DIGITS_MAX)
        return DigitSpanGame("game:digits:${nonce(rng)}", run(rng, lv, 10), lv)
    }

    fun sequence(rng: Random, level: Int): SequenceGame {
        val lv = level.coerceIn(SEQUENCE_MIN, SEQUENCE_MAX)
        return SequenceGame("game:sequence:${nonce(rng)}", 3, run(rng, lv, 9), lv)
    }

    // ── Buchstabensalat ─────────────────────────────────────────────────────

    /** Wort und Hinweis. Bewusst ohne Wörter, deren Buchstaben ein zweites geläufiges Wort ergeben (Lampe/Ampel/Palme). */
    internal val ANAGRAM_WORDS: List<Pair<String, String>> = listOf(
        "WOLKE" to "am Himmel", "STURM" to "Wetter", "BLUME" to "im Garten", "FLUSS" to "Gewässer", "STRAND" to "am Meer",
        "WÜSTE" to "Landschaft", "GIPFEL" to "im Gebirge", "SCHNEE" to "Winter", "DONNER" to "Gewitter", "BLITZ" to "Gewitter",
        "KÜSTE" to "am Meer", "QUELLE" to "Gewässer", "HÜGEL" to "Landschaft", "PLANET" to "Weltall", "KOMET" to "Weltall",
        "TIGER" to "Tier", "ZEBRA" to "Tier", "FUCHS" to "Tier", "HAMSTER" to "Tier", "DELFIN" to "Tier", "PINGUIN" to "Tier",
        "GIRAFFE" to "Tier", "KAMEL" to "Tier", "BIBER" to "Tier", "PANDA" to "Tier", "HIRSCH" to "Tier", "STORCH" to "Vogel",
        "FROSCH" to "Tier", "SPINNE" to "Tier", "AMEISE" to "Insekt", "ADLER" to "Vogel",
        "APFEL" to "Obst", "BIRNE" to "Obst", "TOMATE" to "Gemüse", "GURKE" to "Gemüse", "ZWIEBEL" to "Gemüse", "NUDELN" to "Essen",
        "BUTTER" to "aufs Brot", "HONIG" to "süß", "PFEFFER" to "Gewürz", "ZUCKER" to "süß", "KUCHEN" to "Gebäck", "SUPPE" to "Essen",
        "KIRSCHE" to "Obst", "TRAUBE" to "Obst", "MANGO" to "Obst", "ZITRONE" to "Obst", "KAROTTE" to "Gemüse", "PILZE" to "im Wald",
        "STUHL" to "Möbel", "FENSTER" to "am Haus", "SPIEGEL" to "im Bad", "KOFFER" to "auf Reisen", "BRILLE" to "auf der Nase",
        "SCHERE" to "Werkzeug", "HAMMER" to "Werkzeug", "PINSEL" to "zum Malen", "TELLER" to "auf dem Tisch", "LÖFFEL" to "Besteck",
        "FLASCHE" to "zum Trinken", "KERZE" to "Licht", "RADIO" to "Gerät", "KAMERA" to "Gerät", "GITARRE" to "Instrument",
        "TROMMEL" to "Instrument", "GEIGE" to "Instrument", "KLAVIER" to "Instrument", "FAHRRAD" to "unterwegs", "ROLLER" to "unterwegs",
        "BRÜCKE" to "Bauwerk", "TREPPE" to "im Haus", "SCHLÜSSEL" to "für die Tür", "TASCHE" to "zum Tragen", "MANTEL" to "Kleidung",
        "STIEFEL" to "Kleidung", "MÜTZE" to "Kleidung", "HANDSCHUH" to "Kleidung",
        "SCHULE" to "Ort", "MUSEUM" to "Ort", "BAHNHOF" to "Ort", "MARKT" to "Ort", "KÜCHE" to "im Haus", "KELLER" to "im Haus",
        "BALKON" to "am Haus", "BÄCKER" to "Beruf", "LEHRER" to "Beruf", "PILOT" to "Beruf", "GÄRTNER" to "Beruf", "RICHTER" to "Beruf",
        "FÖRSTER" to "Beruf", "FREUDE" to "Gefühl", "GEDULD" to "Tugend", "TRAUM" to "im Schlaf", "GLÜCK" to "Gefühl",
        "HOFFNUNG" to "Gefühl", "FREIHEIT" to "Wert", "WISSEN" to "im Kopf", "RÄTSEL" to "zum Knobeln", "GEDANKE" to "im Kopf",
        "REISE" to "unterwegs", "MUSIK" to "zum Hören", "SPRACHE" to "zum Reden", "ZUKUNFT" to "Zeit", "SOMMER" to "Jahreszeit",
        "WINTER" to "Jahreszeit", "HERBST" to "Jahreszeit", "MORGEN" to "Tageszeit", "FREUND" to "Mensch",
    )

    /** [used] sammelt die Wörter der Sitzung, damit keins zweimal kommt. */
    fun anagram(rng: Random, used: MutableSet<String> = mutableSetOf()): AnagramGame {
        val (word, hint) = ANAGRAM_WORDS.filter { it.first !in used }.ifEmpty { ANAGRAM_WORDS }.random(rng)
        used += word
        var letters = word.toList()
        repeat(20) { if (letters.joinToString("") == word) letters = word.toList().shuffled(rng) }
        return AnagramGame("game:anagram:$word:${nonce(rng)}", word, letters, hint)
    }

    // ── Blitzblick ──────────────────────────────────────────────────────────

    fun dots(rng: Random): DotsGame {
        val count = rng.nextInt(6, 15)
        val dots = mutableListOf<Pair<Float, Float>>()
        var guard = 0
        // Mindestabstand, damit sich keine Punkte überdecken und man wirklich zählen könnte.
        while (dots.size < count && guard++ < 4000) {
            val p = (0.08f + rng.nextFloat() * 0.84f) to (0.08f + rng.nextFloat() * 0.84f)
            if (dots.none { (x, y) -> (x - p.first) * (x - p.first) + (y - p.second) * (y - p.second) < 0.016f }) dots += p
        }
        val n = dots.size
        val wrong = listOf(-3, -2, -1, 1, 2, 3).shuffled(rng).map { n + it }.filter { it > 2 }.take(3)
        return DotsGame("game:dots:${nonce(rng)}", dots, (wrong + n).shuffled(rng))
    }

    // ── Logik-Schluss ───────────────────────────────────────────────────────

    private val NONSENSE = listOf(
        "Blip" to "Blips", "Zork" to "Zorks", "Mulp" to "Mulps", "Drax" to "Draxe", "Grump" to "Grumps",
        "Fenn" to "Fenns", "Quoll" to "Quolls", "Tarp" to "Tarps", "Wimp" to "Wimps", "Nork" to "Norks",
    )
    private val NAMES = listOf("Pim", "Lumo", "Tavi", "Rexo", "Juno")
    internal const val FOLLOWS = "Ja, das folgt zwingend"
    internal const val FOLLOWS_NOT = "Nein, das folgt nicht"

    internal data class Syllogism(val premises: List<String>, val conclusion: String, val valid: Boolean, val why: String)

    internal fun syllogismOf(kind: Int, rng: Random): Syllogism {
        val (a, b, c) = NONSENSE.shuffled(rng).take(3)
        val x = NAMES.random(rng)
        return when (kind % 10) {
            0 -> Syllogism(listOf("Alle ${a.second} sind ${b.second}.", "Alle ${b.second} sind ${c.second}."), "Alle ${a.second} sind ${c.second}.", true,
                "Die Kette trägt: Wer ${a.first} ist, ist ${b.first}, und jeder ${b.first} ist ${c.first}.")
            1 -> Syllogism(listOf("Alle ${a.second} sind ${b.second}.", "Alle ${c.second} sind ${b.second}."), "Alle ${a.second} sind ${c.second}.", false,
                "Beide Gruppen liegen in den ${b.second}, müssen sich aber nicht berühren. Alle Hunde sind Tiere, alle Katzen sind Tiere, trotzdem ist kein Hund eine Katze.")
            2 -> Syllogism(listOf("Alle ${a.second} sind ${b.second}.", "Kein ${b.first} ist ein ${c.first}."), "Kein ${a.first} ist ein ${c.first}.", true,
                "Jeder ${a.first} ist ein ${b.first}, und unter den ${b.second} gibt es keinen ${c.first}.")
            3 -> Syllogism(listOf("Einige ${a.second} sind ${b.second}.", "Alle ${b.second} sind ${c.second}."), "Einige ${a.second} sind ${c.second}.", true,
                "Die ${a.second}, die ${b.second} sind, sind damit auch ${c.second}.")
            4 -> Syllogism(listOf("Alle ${a.second} sind ${b.second}.", "Einige ${b.second} sind ${c.second}."), "Einige ${a.second} sind ${c.second}.", false,
                "Die ${b.second}, die ${c.second} sind, können genau die sein, die keine ${a.second} sind.")
            5 -> Syllogism(listOf("Kein ${a.first} ist ein ${b.first}.", "Kein ${b.first} ist ein ${c.first}."), "Kein ${a.first} ist ein ${c.first}.", false,
                "Aus zwei Verneinungen folgt nichts. Kein Fisch ist ein Vogel, kein Vogel ist ein Hai, und doch ist ein Hai ein Fisch.")
            6 -> Syllogism(listOf("Alle ${a.second} sind ${b.second}.", "$x ist ein ${b.first}."), "$x ist ein ${a.first}.", false,
                "Umkehrschluss: Alle ${a.second} sind ${b.second}, aber nicht jeder ${b.first} ist ein ${a.first}.")
            7 -> Syllogism(listOf("Alle ${a.second} sind ${b.second}.", "$x ist kein ${b.first}."), "$x ist kein ${a.first}.", true,
                "Wäre $x ein ${a.first}, müsste $x auch ein ${b.first} sein. Das ist ausgeschlossen.")
            8 -> Syllogism(listOf("Einige ${a.second} sind ${b.second}.", "Einige ${b.second} sind ${c.second}."), "Einige ${a.second} sind ${c.second}.", false,
                "Die beiden »einige« können verschiedene ${b.second} meinen. Eine Überschneidung ist möglich, aber nicht zwingend.")
            else -> Syllogism(listOf("Alle ${a.second} sind ${b.second}.", "Einige ${a.second} sind ${c.second}."), "Einige ${b.second} sind ${c.second}.", true,
                "Die ${a.second}, die ${c.second} sind, sind zugleich ${b.second}. Also gibt es ${b.second}, die ${c.second} sind.")
        }
    }

    fun syllogism(rng: Random): ChoiceGame {
        val s = syllogismOf(rng.nextInt(10), rng)
        return ChoiceGame(
            id = "game:logic:${nonce(rng)}",
            module = MindfulModuleType.BRAIN_PUZZLE,
            label = "LOGIK-SCHLUSS",
            style = ChoiceStyle.TEXT,
            prompt = "Folgt der Schluss zwingend aus den beiden Sätzen?",
            display = s.premises.joinToString("\n") + "\n\nAlso: " + s.conclusion,
            options = listOf(FOLLOWS, FOLLOWS_NOT),
            correctIndex = if (s.valid) 0 else 1,
            explanation = s.why,
        )
    }

    // ── Zeit-Rechnen ────────────────────────────────────────────────────────

    private val WEEKDAYS = listOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag")

    fun timePuzzle(rng: Random): ChoiceGame = if (rng.nextBoolean()) {
        val start = rng.nextInt(7)
        val days = generateSequence { rng.nextInt(10, 101) }.first { it % 7 != 0 }
        val answer = (start + days) % 7
        val options = (listOf(answer) + (0 until 7).filter { it != answer }.shuffled(rng).take(3)).shuffled(rng)
        ChoiceGame(
            id = "game:weekday:${nonce(rng)}",
            module = MindfulModuleType.BRAIN_PUZZLE,
            label = "ZEIT-RECHNEN",
            style = ChoiceStyle.TEXT,
            prompt = "Rechne im Kopf",
            display = "Heute ist ${WEEKDAYS[start]}.\nWelcher Wochentag ist in $days Tagen?",
            options = options.map { WEEKDAYS[it] },
            correctIndex = options.indexOf(answer),
            explanation = "$days Tage sind ${days / 7} volle Wochen und ${days % 7} Tage. Volle Wochen ändern den Wochentag nicht, also ${days % 7} Tage weiterzählen.",
        )
    } else {
        val from = rng.nextInt(6 * 60, 18 * 60)
        val span = generateSequence { rng.nextInt(45, 301) }.first { it % 5 == 0 && it % 60 != 0 }
        val to = from + span
        fun clock(m: Int) = "${m / 60}:${(m % 60).toString().padStart(2, '0')}"
        val wrong = listOf(-60, -20, -10, 10, 20, 60).shuffled(rng).map { span + it }.filter { it > 0 }.take(3)
        val options = (wrong + span).shuffled(rng)
        ChoiceGame(
            id = "game:minutes:${nonce(rng)}",
            module = MindfulModuleType.BRAIN_PUZZLE,
            label = "ZEIT-RECHNEN",
            style = ChoiceStyle.TEXT,
            prompt = "Rechne im Kopf",
            display = "Von ${clock(from)} Uhr bis ${clock(to)} Uhr.\nWie viele Minuten sind das?",
            options = options.map { "$it Minuten" },
            correctIndex = options.indexOf(span),
            explanation = "Erst bis zur vollen Stunde auffüllen, dann die ganzen Stunden, dann den Rest: zusammen $span Minuten (${span / 60} Std. ${span % 60} Min.).",
        )
    }

    // ── Prozent, Größer oder kleiner ────────────────────────────────────────

    fun percent(rng: Random): ChoiceGame {
        val p = listOf(5, 10, 15, 20, 25, 30, 40, 60, 75).random(rng)
        val base = rng.nextInt(2, 41) * 20
        val result = p * base / 100
        val options = numberOptions(result, rng)
        val tenth = base / 10
        return ChoiceGame(
            id = "game:percent:${nonce(rng)}",
            module = MindfulModuleType.SPEED_MATH,
            label = "PROZENT IM KOPF",
            style = ChoiceStyle.NUMBERS,
            prompt = "Wie viel ist das?",
            display = "$p % von $base",
            options = options.map { it.toString() },
            correctIndex = options.indexOf(result),
            explanation = when (p) {
                5 -> "10 % sind $tenth, die Hälfte davon ist $result."
                10 -> "10 % heißt: Komma eine Stelle nach links. $base wird zu $result."
                15 -> "10 % sind $tenth, 5 % die Hälfte davon (${tenth / 2}). Zusammen $result."
                25 -> "25 % ist ein Viertel: $base geteilt durch 4 ist $result."
                75 -> "Ein Viertel sind ${base / 4}, drei Viertel also $result."
                else -> "10 % sind $tenth. Mal ${p / 10} ergibt $result."
            },
        )
    }

    private fun de(v: Double): String =
        if (v == Math.floor(v)) v.toLong().toString() else ((Math.round(v * 100) / 100.0).toString()).replace('.', ',')

    fun compare(rng: Random): ChoiceGame {
        // Linke Seite: ein Ausdruck zum Rechnen; rechte Seite: eine Zahl knapp daneben oder genau gleich.
        val (leftText, left) = when (rng.nextInt(4)) {
            0 -> { val a = rng.nextInt(12, 20); val b = rng.nextInt(4, 10); "$a × $b" to (a * b).toDouble() }
            1 -> { val d = listOf(4, 5, 8, 10, 20).random(rng); val n = rng.nextInt(1, d); "$n/$d" to n.toDouble() / d }
            2 -> { val p = listOf(15, 20, 25, 30, 40).random(rng); val base = rng.nextInt(2, 16) * 20; "$p % von $base" to p * base / 100.0 }
            else -> { val a = rng.nextInt(11, 20); "$a²" to (a * a).toDouble() }
        }
        val relation = rng.nextInt(5) // 0,1 = rechts kleiner · 2,3 = rechts größer · 4 = gleich
        val step = if (left < 1) listOf(0.05, 0.1, 0.15).random(rng) else listOf(2.0, 3.0, 5.0, 8.0).random(rng)
        val right = when {
            relation == 4 -> left
            relation <= 1 -> left - step
            else -> left + step
        }.let { Math.round(it * 100) / 100.0 }.coerceAtLeast(0.0)
        val correct = when {
            Math.abs(left - right) < 1e-9 -> 2
            left > right -> 0
            else -> 1
        }
        return ChoiceGame(
            id = "game:compare:${nonce(rng)}",
            module = MindfulModuleType.SPEED_MATH,
            label = "GRÖSSER ODER KLEINER",
            style = ChoiceStyle.NUMBERS,
            prompt = "Was ist größer?",
            display = "$leftText   ?   ${de(right)}",
            options = listOf("Links ist größer", "Rechts ist größer", "Beide sind gleich"),
            correctIndex = correct,
            explanation = "$leftText = ${de(left)}. " + when (correct) {
                0 -> "Das ist mehr als ${de(right)}."
                1 -> "Das ist weniger als ${de(right)}."
                else -> "Genau so viel steht rechts."
            },
        )
    }

    // ── Russisch: Betonung, Gehör, Lücke ────────────────────────────────────

    /** Hören → betonte Silbe antippen. */
    fun stress(p: RuPhrase, due: Boolean, rng: Random): ChoiceGame? {
        val tap = RuLessonBuilder.stressTap(p) ?: return null
        return ChoiceGame(
            id = "game:ru-stress:${p.id}:${nonce(rng)}",
            module = MindfulModuleType.LANGUAGE,
            label = if (due) "RUSSISCH · WIEDERHOLUNG" else "RUSSISCH · BETONUNG",
            style = ChoiceStyle.PHRASE,
            prompt = "Hör hin: Welche Silbe ist betont?",
            display = tap.word,
            options = tap.syllables,
            correctIndex = tap.stressedIndex,
            explanation = "Die betonte Silbe klingt länger und klarer. Unbetonte Vokale werden kürzer und verwaschen.",
            audio = p.audio,
            revealRu = p.ru,
            revealDe = p.de,
            grade = RuGrade(p.id, due),
        )
    }

    /** Hören → den richtigen russischen Satz unter vier geschriebenen finden. */
    fun spell(p: RuPhrase, pool: List<RuPhrase>, due: Boolean, rng: Random): ChoiceGame? {
        val wrong = RuLessonBuilder.distractors(p, pool, rng)
        if (wrong.size < 3) return null
        val options = (wrong + p).shuffled(rng)
        return ChoiceGame(
            id = "game:ru-spell:${p.id}:${nonce(rng)}",
            module = MindfulModuleType.LANGUAGE,
            label = if (due) "RUSSISCH · WIEDERHOLUNG" else "RUSSISCH · GEHÖR",
            style = ChoiceStyle.LISTEN,
            prompt = "Welcher Satz ist das?",
            options = options.map { it.display },
            optionSubs = options.map { RussianPhonetics.transliterateFlat(it.ru) },
            correctIndex = options.indexOf(p),
            audio = p.audio,
            revealRu = p.ru,
            revealDe = p.de,
            grade = RuGrade(p.id, due),
        )
    }

    /** Ein Wort des Satzes fehlt — welches? */
    fun gap(p: RuPhrase, pool: List<RuPhrase>, due: Boolean, rng: Random): ChoiceGame? {
        val words = RuLessonBuilder.words(p.ru)
        if (words.size < 3) return null
        val candidates = words.indices.filter { RussianPhonetics.plain(words[it]).count { c -> c.isLetter() } >= 3 }
        val index = candidates.randomOrNull(rng) ?: return null
        val target = words[index]
        fun key(w: String) = RuLessonBuilder.normWord(w)
        val own = words.map { key(it) }.toSet()
        val wrong = pool.asSequence().filter { it.id != p.id }.shuffled(rng)
            .flatMap { RuLessonBuilder.words(it.ru).asSequence() }
            .filter { key(it) !in own && RussianPhonetics.plain(it).count { c -> c.isLetter() } >= 2 }
            .distinctBy { key(it) }
            .take(3).toList()
        if (wrong.size < 3) return null
        fun show(w: String) = RussianPhonetics.display(w).replaceFirstChar { it.lowercaseChar() }
        val options = (wrong + target).shuffled(rng)
        val sentence = words.mapIndexed { i, w -> if (i == index) "＿＿＿" else RussianPhonetics.display(w) }.joinToString(" ")
        return ChoiceGame(
            id = "game:ru-gap:${p.id}:${nonce(rng)}",
            module = MindfulModuleType.LANGUAGE,
            label = if (due) "RUSSISCH · WIEDERHOLUNG" else "RUSSISCH · LÜCKE",
            style = ChoiceStyle.PHRASE,
            prompt = "»${cleanDe(p.de)}« Welches Wort fehlt?",
            display = sentence,
            options = options.map { show(it) },
            optionSubs = options.map { RussianPhonetics.transliterateFlat(it) },
            correctIndex = options.indexOf(target),
            audio = p.audio,
            revealRu = p.ru,
            revealDe = p.de,
            grade = RuGrade(p.id, due),
        )
    }
}
