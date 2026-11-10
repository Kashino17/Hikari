package com.hikari.app.domain.feed

import com.hikari.app.domain.russian.RuLabWord
import kotlinx.serialization.Serializable

/** Ein Buchstabe des russischen Alphabets, wie ihn der Feed beibringt. */
@Serializable
data class RuLetter(
    /** Kleinbuchstabe — zugleich der Schlüssel im Lernstand. */
    val l: String,
    val u: String,
    /** Laut in der Umschrift, die die App überall verwendet. */
    val sound: String,
    /** Deutsche Eselsbrücke zum Laut (»wie in …«). */
    val like: String,
    val group: String,
    val tip: String,
    val words: List<RuLabWord> = emptyList(),
) {
    /** Zeichen ohne eigenen Laut (ь, ъ) taugen nicht für Laut-Fragen. */
    val hasSound: Boolean get() = group != "signs"
}

@Serializable
data class RuLetterGroup(val id: String, val title: String, val hint: String)

@Serializable
data class RuAlphabet(
    val version: Int = 1,
    val groups: List<RuLetterGroup> = emptyList(),
    val letters: List<RuLetter> = emptyList(),
    /** Internationale Wörter zum Lesenüben. */
    val reading: List<RuLabWord> = emptyList(),
) {
    fun group(id: String): RuLetterGroup? = groups.firstOrNull { it.id == id }
    fun letter(l: String): RuLetter? = letters.firstOrNull { it.l == l }
}

/**
 * Lernstand je Buchstabe: 0 = noch nicht vorgestellt, 1 = vorgestellt,
 * bis [MAX] = sitzt. Richtig → +1, falsch → −1 (nie unter 1).
 */
object RuAlphabetLogic {
    const val MAX = 4

    /** Ab so vielen sicheren Buchstaben gilt Kyrillisch als lesbar (von 33). */
    const val READS_CYRILLIC = 28

    fun known(progress: Map<String, Int>): Set<String> = progress.filterValues { it >= 1 }.keys

    /** Der nächste noch nicht vorgestellte Buchstabe in Lehrreihenfolge. */
    fun nextLetter(alphabet: RuAlphabet, progress: Map<String, Int>): RuLetter? =
        alphabet.letters.firstOrNull { (progress[it.l] ?: 0) < 1 }

    fun graded(progress: Map<String, Int>, letter: String, correct: Boolean): Map<String, Int> {
        val now = progress[letter] ?: 0
        val next = if (correct) (now + 1).coerceAtMost(MAX) else (now - 1).coerceAtLeast(1)
        return progress + (letter to next.coerceAtLeast(1))
    }

    /** Vorgestellte Buchstaben, die schwächsten zuerst — die brauchen die Übung. */
    fun weakest(alphabet: RuAlphabet, progress: Map<String, Int>): List<RuLetter> =
        alphabet.letters.filter { (progress[it.l] ?: 0) >= 1 }.sortedBy { progress[it.l] ?: 0 }

    private fun letters(word: RuLabWord): Set<String> =
        word.plain.lowercase().filter { it.isLetter() }.map { it.toString() }.toSet()

    /** Wörter, die sich allein mit den vorgestellten Buchstaben lesen lassen. */
    fun readable(alphabet: RuAlphabet, progress: Map<String, Int>): List<RuLabWord> {
        val known = known(progress)
        val all = (alphabet.reading + alphabet.letters.flatMap { it.words }).distinctBy { it.ru }
        return all.filter { w -> letters(w).let { it.isNotEmpty() && known.containsAll(it) } }
    }

    /** Wie viele der 33 Buchstaben sitzen (Stufe 3 und höher). */
    fun mastered(progress: Map<String, Int>): Int = progress.count { it.value >= 3 }
}
