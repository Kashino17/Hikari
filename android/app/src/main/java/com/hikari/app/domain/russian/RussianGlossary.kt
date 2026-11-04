package com.hikari.app.domain.russian

import android.content.res.AssetManager
import kotlinx.serialization.Serializable

/**
 * Wort-für-Wort-Bedeutungen aus assets/russian/glosses.json (erzeugt von
 * tools/russian_audio/build_glosses.py).
 *
 * `s` ordnet jedem Kurs-Satz (ohne Betonungszeichen) eine Glosse pro Wort zu,
 * im Kontext des Satzes übersetzt (что = »was« oder »dass«). "=" heißt: das
 * Wort gehört zur Glosse davor (потому что = »weil«). `w` ist der Rückfall
 * für Sätze, die erst zur Laufzeit entstehen — die häufigste Bedeutung der
 * Wortform über alle Sätze.
 */
@Serializable
data class RuGlossData(
    val version: Int = 1,
    val s: Map<String, List<String>> = emptyMap(),
    val w: Map<String, String> = emptyMap(),
)

/** Ein Abschnitt der Anzeige: ein Wort oder eine feste Wendung aus mehreren. */
data class RuGlossChunk(
    val words: List<RussianPhonetics.WordReading>,
    /** Deutsche Bedeutung; null, wenn das Wort unbekannt ist. */
    val gloss: String?,
)

object RussianGlossary {
    const val JOIN = "="

    @Volatile private var data: RuGlossData? = null

    fun load(assets: AssetManager): RuGlossData =
        data ?: synchronized(this) {
            data ?: runCatching {
                assets.open("russian/glosses.json").bufferedReader().use { r ->
                    ruJson.decodeFromString(RuGlossData.serializer(), r.readText())
                }
            }.getOrDefault(RuGlossData()).also { data = it }
        }

    /** Für Tests. */
    fun install(d: RuGlossData) { data = d }

    /** Glosse je Wort von [ru] (Betonungszeichen egal), null = unbekannt. */
    fun glossesFor(ru: String, d: RuGlossData = data ?: RuGlossData()): List<String?> {
        val words = RussianPhonetics.words(ru)
        val exact = d.s[RussianPhonetics.plain(ru).trim()]
        if (exact != null && exact.size == words.size) return exact
        // Zur Laufzeit gebaute Sätze (Kombi-Training, Muster) bestehen meist aus
        // bekannten Sätzen: längste bekannte Wortfolge zuerst, sonst Einzelwort.
        val index = indexOf(d)
        val keys = words.map { norm(it.plain) }
        val out = MutableList<String?>(words.size) { null }
        var i = 0
        while (i < words.size) {
            var matched = 0
            for (j in words.size downTo i + 2) {
                val g = index[keys.subList(i, j).joinToString(" ")] ?: continue
                for (k in g.indices) out[i + k] = if (k == 0 && g[k] == JOIN) null else g[k]
                matched = j - i
                break
            }
            if (matched == 0) {
                out[i] = d.w[words[i].plain.lowercase()] ?: d.w[keys[i]]
                matched = 1
            }
            i += matched
        }
        return out
    }

    private fun norm(w: String) = w.lowercase().replace('ё', 'е')

    @Volatile private var index: Pair<RuGlossData, Map<String, List<String>>>? = null

    /** Kurs-Sätze nach ihrer Wortfolge (klein, ohne Satzzeichen). */
    private fun indexOf(d: RuGlossData): Map<String, List<String>> {
        index?.let { (src, map) -> if (src === d) return map }
        val map = HashMap<String, List<String>>(d.s.size * 2)
        for ((sentence, glosses) in d.s) {
            val key = RussianPhonetics.wordKeys(sentence).joinToString(" ")
            if (key.count { it == ' ' } + 1 == glosses.size) map.putIfAbsent(key, glosses)
        }
        index = d to map
        return map
    }

    /** Satz → Abschnitte für die Wort-für-Wort-Zeile. */
    fun chunks(ru: String, d: RuGlossData = data ?: RuGlossData()): List<RuGlossChunk> {
        val words = RussianPhonetics.words(ru)
        val glosses = glossesFor(ru, d)
        val out = mutableListOf<RuGlossChunk>()
        words.forEachIndexed { i, w ->
            val g = glosses.getOrNull(i)
            if (g == JOIN && out.isNotEmpty()) {
                val last = out.removeAt(out.size - 1)
                out += last.copy(words = last.words + w)
            } else {
                out += RuGlossChunk(listOf(w), g?.takeUnless { it == JOIN })
            }
        }
        return out
    }
}
