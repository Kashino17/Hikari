package com.hikari.app.domain.russian

import kotlin.random.Random

/** Eine Station in einer Lern-Sitzung. */
sealed interface RuExercise {
    /** Item, das diese Übung bewertet (null = nicht bewertet, z. B. Intro). */
    val gradedId: String?

    /** Aussprache-Schwerpunkt des Tages mit Hörbeispielen. */
    data class SoundTip(val day: RuDay, val examples: List<RuPhrase>) : RuExercise {
        override val gradedId: String? = null
    }

    /** Neue Karte: hören, lesen, verstehen, nachsprechen. */
    data class Intro(val phrase: RuPhrase) : RuExercise {
        override val gradedId: String? = null
    }

    /** Russisch hören → deutsche Bedeutung wählen. */
    data class ListenChoose(val phrase: RuPhrase, val options: List<RuPhrase>) : RuExercise {
        override val gradedId: String get() = phrase.id
    }

    /** Deutsch lesen → russischen Satz wählen (mit Umschrift). */
    data class MeaningChoose(val phrase: RuPhrase, val options: List<RuPhrase>) : RuExercise {
        override val gradedId: String get() = phrase.id
    }

    /** Hören → betonte Silbe antippen. */
    data class StressTap(
        val phrase: RuPhrase,
        /** Das geprüfte Wort (klein, ohne Betonungszeichen). */
        val word: String,
        val syllables: List<String>,
        val stressedIndex: Int,
    ) : RuExercise {
        override val gradedId: String get() = phrase.id
    }

    /** Deutschen Satz aus russischen Wortkacheln bauen. */
    data class Build(
        val phrase: RuPhrase,
        /** Lösung in Reihenfolge (angezeigte Wörter mit Betonung, ohne Satzzeichen). */
        val answer: List<String>,
        /** Gemischte Kacheln inkl. Ablenker. */
        val tiles: List<String>,
    ) : RuExercise {
        override val gradedId: String get() = phrase.id
    }

    /** Satz laut sagen (Spracherkennung) — bei fehlendem Erkenner: selbst vergleichen. */
    data class Speak(val phrase: RuPhrase) : RuExercise {
        override val gradedId: String get() = phrase.id
    }

    /** Frage hören → passende Antwort wählen (mehrere können passen). */
    data class Reply(
        val question: RuPhrase,
        val options: List<RuPhrase>,
        val correctIds: Set<String>,
    ) : RuExercise {
        override val gradedId: String get() = question.id
    }

    /** Rollenspiel des Tages-Dialogs. */
    data class Dialog(val day: RuDay, val lines: List<Pair<RuLine, RuPhrase>>) : RuExercise {
        override val gradedId: String? = null
    }
}

object RuLessonBuilder {

    private const val VOWELS = "аеёиоуыэюя"

    /** Reaktionen, die auf fast jede Frage passen — nie als "falsche" Antwort anbieten. */
    val UNIVERSAL_REACTIONS = setOf(
        "d01_da", "d01_net", "d01_spasibo", "d01_pozhaluysta", "d05_konechno", "d05_ya_ne_znayu",
        "d05_ya_ne_ponimayu", "d05_ya_ponimayu", "d05_povtorite", "d05_medlennee", "d05_eshyo_raz",
        "d05_chto", "d11_da_konechno", "d11_mozhet_byt", "d11_ne_mogu", "d08_ya_tozhe", "d02_mne_tozhe",
        "d08_klassno", "d08_kruto", "d12_pravda", "d12_seryozno", "d06_eto_interesno", "d12_zdorovo",
        "d13_molodets", "d12_ya_soglasen", "d03_horosho",
    )

    /**
     * Tages-Lektion: Aussprache-Tipp → neue Karten in Dreiergruppen (je gleich
     * ein kurzes Abfragen) → gemischte Übungsrunde → Rollenspiel.
     */
    fun lesson(
        index: RuCourseIndex,
        dayNumber: Int,
        settings: RuSettings,
        speechAvailable: Boolean,
        rng: Random = Random.Default,
    ): List<RuExercise> {
        val day = index.day(dayNumber) ?: return emptyList()
        val phrases = index.dayPhrases(day, settings)
        val pool = index.phrasesUpTo(dayNumber, settings)
        val out = mutableListOf<RuExercise>()

        out += RuExercise.SoundTip(day, day.sound.examples.mapNotNull { index.phrase(it, settings) })

        // 1) Neue Karten in Gruppen, direkt abgefragt (Abruf festigt mehr als Wiederlesen).
        phrases.chunked(3).forEach { group ->
            group.forEach { out += RuExercise.Intro(it) }
            group.shuffled(rng).forEachIndexed { i, p ->
                out += if (i % 2 == 0) listenChoose(p, pool, rng) else meaningChoose(p, pool, rng)
            }
        }

        // 2) Übungsrunde: jede Karte in einem anderen Format.
        val practice = phrases.shuffled(rng).mapIndexed { i, p ->
            practiceFor(index, p, pool, settings, speechAvailable && i % 3 == 0, i, rng)
        }
        out += practice

        // 3) Rollenspiel.
        out += RuExercise.Dialog(day, index.dialog(day, settings))
        return out
    }

    /** Wiederholung fälliger Karten — eine Übung je Karte, Format je nach Karte. */
    fun review(
        index: RuCourseIndex,
        dueIds: List<String>,
        maxDay: Int,
        settings: RuSettings,
        speechAvailable: Boolean,
        limit: Int = 20,
        rng: Random = Random.Default,
    ): List<RuExercise> {
        val pool = index.phrasesUpTo(maxDay, settings)
        return dueIds.take(limit).mapNotNull { index.phrase(it, settings) }.shuffled(rng)
            .mapIndexed { i, p -> practiceFor(index, p, pool, settings, speechAvailable && i % 4 == 1, i + 1, rng) }
    }

    private fun practiceFor(
        index: RuCourseIndex,
        p: RuPhrase,
        pool: List<RuPhrase>,
        settings: RuSettings,
        speak: Boolean,
        slot: Int,
        rng: Random,
    ): RuExercise {
        val item = index.itemById[p.id]
        if (speak) return RuExercise.Speak(p)
        if (item != null && item.replies.isNotEmpty() && slot % 2 == 0) {
            reply(index, p, item, pool, settings, rng)?.let { return it }
        }
        if (p.wordCount >= 3) return build(p, pool, rng)
        stressTap(p)?.let { if (slot % 2 == 1) return it }
        return if (slot % 2 == 0) listenChoose(p, pool, rng) else meaningChoose(p, pool, rng)
    }

    // ── Einzelne Formate ─────────────────────────────────────────────────────

    /** Drei Ablenker mit anderer Bedeutung UND anderem russischen Text. */
    fun distractors(target: RuPhrase, pool: List<RuPhrase>, rng: Random, n: Int = 3): List<RuPhrase> {
        val sameDay = pool.filter { it.day == target.day }
        val others = pool.filter { it.day != target.day }
        val chosen = mutableListOf<RuPhrase>()
        val seenDe = mutableSetOf(norm(target.de))
        val seenRu = mutableSetOf(norm(target.plain))
        for (cand in sameDay.shuffled(rng) + others.shuffled(rng)) {
            if (chosen.size == n) break
            if (cand.id == target.id) continue
            if ((chosen + target).any { confusable(it.de, cand.de) }) continue
            if (!seenDe.add(norm(cand.de))) continue
            if (!seenRu.add(norm(cand.plain))) continue
            chosen += cand
        }
        return chosen
    }

    /**
     * Zwei Bedeutungen wären als Antwortoptionen mehrdeutig, wenn eine
     * Variante die andere wortweise enthält: "Guten Tag!" ⊂ "Guten Tag! / Hallo!",
     * "Wie geht's?" ⊂ "Wie geht's dir?". Klammerzusätze zählen nicht.
     */
    fun confusable(a: String, b: String): Boolean {
        fun alts(s: String) = s.replace(Regex("\\([^)]*\\)"), " ").split('/')
            .map { alt -> norm(alt.replace("'", " ")).split(' ').filter { it.isNotBlank() }.toSet() }
            .filter { it.isNotEmpty() }
        val aa = alts(a)
        val bb = alts(b)
        return aa.any { x -> bb.any { y -> x.containsAll(y) || y.containsAll(x) } }
    }

    fun listenChoose(p: RuPhrase, pool: List<RuPhrase>, rng: Random) =
        RuExercise.ListenChoose(p, (distractors(p, pool, rng) + p).shuffled(rng))

    fun meaningChoose(p: RuPhrase, pool: List<RuPhrase>, rng: Random) =
        RuExercise.MeaningChoose(p, (distractors(p, pool, rng) + p).shuffled(rng))

    fun reply(
        index: RuCourseIndex,
        question: RuPhrase,
        item: RuItem,
        pool: List<RuPhrase>,
        settings: RuSettings,
        rng: Random,
    ): RuExercise.Reply? {
        val correct = item.replies.mapNotNull { index.phrase(it, settings) }
        if (correct.isEmpty()) return null
        val right = correct.random(rng)
        val replyIds = item.replies.toSet()
        // Ablenker: Aussagen anderer Tage, die sicher NICHT passen — keine Fragen,
        // keine Allzweck-Reaktionen (Да, Коне́чно …), nichts vom selben Tag
        // (dort stehen oft weitere passende Antworten wie Я уста́л auf Как дела́?).
        val wrong = pool.filter {
            it.id !in replyIds && it.id != question.id && it.day != question.day &&
                it.id !in UNIVERSAL_REACTIONS && !it.plain.trim().endsWith("?") &&
                it.wordCount >= 2 && norm(it.plain) != norm(right.plain)
        }.distinctBy { norm(it.de) }.shuffled(rng).take(3)
        if (wrong.size < 2) return null
        return RuExercise.Reply(question, (wrong + right).shuffled(rng), replyIds)
    }

    /** Eigennamen behalten ihren Großbuchstaben, alles andere wird klein — sonst verrät die Kachel das erste Wort. */
    private val PROPER_NOUNS = setOf("кадир", "аня", "германии", "германию", "россии", "москве", "сергей", "телеграм")

    private fun tile(word: String): String {
        val d = RussianPhonetics.display(word)
        return if (RussianPhonetics.plain(word).lowercase() in PROPER_NOUNS) d else d.replaceFirstChar { it.lowercaseChar() }
    }

    fun build(p: RuPhrase, pool: List<RuPhrase>, rng: Random): RuExercise.Build {
        val answer = words(p.ru).map { tile(it) }
        val answerKeys = answer.map { normWord(it) }.toSet()
        val extra = pool.asSequence().filter { it.id != p.id }.shuffled(rng)
            .flatMap { words(it.ru).asSequence() }
            .map { tile(it) }
            .filter { normWord(it) !in answerKeys }
            .distinctBy { normWord(it) }
            .take(2).toList()
        return RuExercise.Build(p, answer, (answer + extra).shuffled(rng))
    }

    /** Längstes mehrsilbiges Wort des Satzes; seine Silben zum Antippen. */
    fun stressTap(p: RuPhrase): RuExercise.StressTap? {
        val candidates = words(p.ru).filter { w -> w.count { it.lowercaseChar() in VOWELS } >= 2 && w.contains('+') }
        val word = candidates.maxByOrNull { it.length } ?: return null
        val (syllables, stressed) = syllables(word)
        if (syllables.size < 2 || stressed < 0) return null
        return RuExercise.StressTap(p, RussianPhonetics.plain(word).lowercase(), syllables, stressed)
    }

    /**
     * Grobe Silbentrennung für die Betonungsübung: jede Silbe endet mit ihrem
     * Vokal, Konsonanten am Wortende hängen an der letzten Silbe.
     * Liefert (Silben ohne "+", Index der betonten Silbe).
     */
    fun syllables(stressedWord: String): Pair<List<String>, Int> {
        val out = mutableListOf<String>()
        var cur = StringBuilder()
        var stressedSyl = -1
        var pendingStress = false
        for (c in stressedWord) {
            if (c == '+') {
                pendingStress = true
                continue
            }
            // й schließt die Silbe davor (здра-вствуй-те), statt die nächste zu öffnen.
            if (c.lowercaseChar() == 'й' && cur.isEmpty() && out.isNotEmpty()) {
                out[out.size - 1] = out.last() + c
                continue
            }
            cur.append(c)
            if (c.lowercaseChar() in VOWELS) {
                if (pendingStress || c.lowercaseChar() == 'ё') stressedSyl = out.size
                pendingStress = false
                out += cur.toString()
                cur = StringBuilder()
            }
        }
        if (cur.isNotEmpty() && out.isNotEmpty()) out[out.size - 1] = out.last() + cur
        return out.map { it.lowercase() } to stressedSyl
    }

    /** Wörter eines Kurstexts (mit "+"), ohne Satzzeichen. */
    fun words(ru: String): List<String> =
        Regex("[А-Яа-яЁё+][А-Яа-яЁё+\\-]*").findAll(ru).map { it.value.trimEnd('-') }.toList()

    fun norm(s: String): String =
        RussianPhonetics.plain(s).lowercase().replace('ё', 'е').replace(Regex("[^a-zäöüßа-я0-9 ]"), " ")
            .split(' ').filter { it.isNotBlank() }.joinToString(" ")

    fun normWord(s: String): String = norm(s).replace(" ", "")
}

/** Auswertung für Bau- und Sprechübungen. */
object RuAnswerCheck {

    fun buildCorrect(ex: RuExercise.Build, chosen: List<String>): Boolean =
        chosen.map { RuLessonBuilder.normWord(it) } == ex.answer.map { RuLessonBuilder.normWord(it) }

    data class SpeechScore(
        /** Je Zielwort: erkannt? */
        val words: List<Pair<String, Boolean>>,
        val score: Double,
        val heard: String,
    ) {
        /**
         * Bestanden, wenn alle Inhaltswörter (ab 4 Buchstaben) und jede
         * Verneinung stimmen und insgesamt mindestens 75 % erkannt wurden —
         * ein verschlucktes "я" verzeiht die Prüfung, ein falsches
         * "английский" statt "русский" nicht.
         */
        val passed: Boolean
            get() = words.isNotEmpty() && score >= PASS &&
                words.all { (w, ok) -> ok || (w.length < 4 && w !in MUST_MATCH) }
    }

    const val PASS = 0.75
    private val MUST_MATCH = setOf("не", "нет", "да")

    /**
     * Vergleicht die Erkennungs-Hypothesen mit dem Zielsatz. Jedes Zielwort
     * gilt als getroffen, wenn ein gehörtes Wort ihm fast gleicht (kleine
     * Abweichungen verzeiht die Erkennung ohnehin nicht fair — z. B. е/ё,
     * Endungen). Die beste Hypothese zählt.
     */
    fun speech(target: String, hypotheses: List<String>): SpeechScore {
        val targetWords = RuLessonBuilder.norm(target).split(' ').filter { it.isNotBlank() }
        var best: SpeechScore? = null
        for (h in hypotheses.ifEmpty { listOf("") }) {
            val heard = RuLessonBuilder.norm(h).split(' ').filter { it.isNotBlank() }.toMutableList()
            val marks = targetWords.map { tw ->
                val hit = heard.indexOfFirst { similarity(it, tw) >= 0.75 }
                if (hit >= 0) heard.removeAt(hit)
                tw to (hit >= 0)
            }
            val score = if (marks.isEmpty()) 0.0 else marks.count { it.second }.toDouble() / marks.size
            val cand = SpeechScore(marks, score, h)
            if (best == null || cand.score > best.score) best = cand
        }
        return best!!
    }

    fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        val max = maxOf(a.length, b.length)
        if (max == 0) return 1.0
        return 1.0 - levenshtein(a, b).toDouble() / max
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val tmp = dp[j]
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return dp[b.length]
    }
}
