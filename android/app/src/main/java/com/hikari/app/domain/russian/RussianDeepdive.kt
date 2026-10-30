package com.hikari.app.domain.russian

import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import kotlinx.serialization.Serializable

/** Vertiefung zu einem Kurs-Satz: Varianten, Beispiele mit Erklärung, Geschlechtsformen, Grammatik. */
@Serializable
data class RuDeepdive(val version: Int = 1, val entries: List<RuDeepEntry> = emptyList())

@Serializable
data class RuDeepEntry(
    val id: String,
    val register: String = "",
    val summary: String = "",
    val variants: List<RuVariant> = emptyList(),
    val examples: List<RuExample> = emptyList(),
    val gender: RuGenderNote? = null,
    val grammar: String? = null,
)

/** Eine Alternative zum Satz — mit Hinweis, wann man sie sagt. */
@Serializable
data class RuVariant(val ru: String, val de: String, val use: String = "")

@Serializable
data class RuExample(val ru: String, val de: String, val why: String = "")

@Serializable
data class RuGenderNote(val title: String, val text: String, val forms: List<RuVariant> = emptyList())

object RuDeepdiveLogic {

    /** Merkt sich zur Laufzeit den russischen Klartext zu dynamischen Clip-IDs (für TTS-Fallback). */
    private val clipTexts = ConcurrentHashMap<String, String>()

    /** Clip-Name wie in tools/russian_audio/generate_deepdive.py: dd_<sha1(ru)[:10]>. */
    fun clipName(ru: String): String {
        val clip = "dd_" + MessageDigest.getInstance("SHA-1").digest(ru.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }.take(10)
        val clean = RussianPhonetics.plain(ru.removePrefix("p|")).trim()
        if (clean.isNotEmpty()) {
            clipTexts[clip] = clean
        }
        return clip
    }

    fun textForClip(clip: String): String? = clipTexts[clip.removeSuffix("_slow")]

    /** Text als Lernsatz, damit Anhören und Sprechprüfung dieselben Bausteine nutzen. */
    fun asPhrase(ru: String, de: String, note: String? = null, day: Int = 0): RuPhrase {
        val clip = clipName(ru)
        return RuPhrase(id = "dd:$clip", ru = ru, de = de, note = note, lit = null, day = day, audio = clip)
    }

    private val STOP = setOf(
        "что", "это", "как", "для", "или", "при", "мне", "тебе", "тебя", "меня", "вас", "вам", "вы", "ты",
        "его", "её", "мой", "моя", "они", "она", "оно", "там", "тут", "нет", "уже", "еще", "ещё", "все", "всё",
    )

    /** Inhaltswörter (ab 3 Buchstaben) eines Satzes, ohne Betonungszeichen. */
    fun keyWords(ru: String): List<String> =
        RuPhrasebookSearch.norm(RussianPhonetics.plain(ru)).split(' ')
            .filter { it.length >= 3 && it !in STOP }

    /**
     * Andere Kurssätze, die ein Wort dieses Satzes enthalten — zeigt dasselbe Wort in neuem Zusammenhang.
     * Wortgleiche Treffer vor Stammtreffern (gemeinsame ersten 4 Buchstaben).
     */
    fun related(target: RuPhrase, pool: List<RuPhrase>, limit: Int = 5): List<RuPhrase> {
        val keys = keyWords(target.ru)
        if (keys.isEmpty()) return emptyList()
        fun score(p: RuPhrase): Int {
            val words = keyWords(p.ru)
            var s = 0
            for (k in keys) {
                if (k in words) s += 3
                else if (k.length >= 5 && words.any { it.length >= 5 && it.take(4) == k.take(4) }) s += 1
            }
            return s
        }
        return pool.filter { it.id != target.id }
            .map { it to score(it) }
            .filter { it.second > 0 }
            .sortedWith(compareByDescending<Pair<RuPhrase, Int>> { it.second }.thenBy { it.first.day })
            .take(limit)
            .map { it.first }
    }
}
