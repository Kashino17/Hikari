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
enum class ChoiceStyle { NUMBERS, LETTER, WORD, PHRASE, LISTEN }

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
}
