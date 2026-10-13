package com.hikari.app.domain.russian

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.Serializable

@Serializable
data class RuTheme(val id: String, val title: String)

/** Themen-Zuordnung der Kurssätze: Item-ID → Theme-IDs. */
@Serializable
data class RuPhrasebookMeta(
    val themes: List<RuTheme> = emptyList(),
    val items: Map<String, List<String>> = emptyMap(),
)

/** Reine Such- und Filterlogik des Satzbuchs. */
object RuPhrasebookSearch {

    /** Kleinschreibung, ё=е, ohne Betonungs- und Satzzeichen — damit »Спасибо«, »спасибо!« und »spasibo« gleich suchen. */
    fun norm(s: String): String = s.lowercase()
        .replace('ё', 'е')
        .replace("ß", "ss")
        .replace('ä', 'a').replace('ö', 'o').replace('ü', 'u')
        .filter { it.isLetterOrDigit() || it == ' ' }
        .trim()

    /** Heuristische Umschrift für Lateineingabe: »spasibo« findet »Спасибо«. */
    fun translit(ru: String): String = norm(RussianPhonetics.plain(ru)).map { TRANSLIT[it] ?: it.toString() }.joinToString("")

    private val TRANSLIT = mapOf(
        'а' to "a", 'б' to "b", 'в' to "v", 'г' to "g", 'д' to "d", 'е' to "e", 'ж' to "zh", 'з' to "z",
        'и' to "i", 'й' to "j", 'к' to "k", 'л' to "l", 'м' to "m", 'н' to "n", 'о' to "o", 'п' to "p",
        'р' to "r", 'с' to "s", 'т' to "t", 'у' to "u", 'ф' to "f", 'х' to "h", 'ц' to "ts", 'ч' to "ch",
        'ш' to "sh", 'щ' to "sch", 'ъ' to "", 'ы' to "y", 'ь' to "", 'э' to "e", 'ю' to "yu", 'я' to "ya",
    )

    /** Alle Suchbegriffe müssen irgendwo in Deutsch, Russisch, Umschrift, Notiz oder wörtlicher Übersetzung vorkommen. */
    fun matches(p: RuPhrase, query: String): Boolean {
        val terms = norm(query).split(' ').filter { it.isNotBlank() }
        if (terms.isEmpty()) return true
        val hay = listOf(norm(p.de), norm(p.plain), translit(p.ru), norm(p.note.orEmpty()), norm(p.lit.orEmpty()))
            .joinToString(" ")
        return terms.all { it in hay }
    }
}

/** Übergabe vom Satzbuch an den Vokabeltest: welche Sätze geprüft werden sollen. */
@Singleton
class RuTestSelection @Inject constructor() {
    @Volatile var ids: List<String> = emptyList()
}
