package com.hikari.app.domain.russian

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Kursinhalt aus assets/russian/course.json. Russische Texte stehen mit "+"
 * vor dem betonten Vokal (Silero-Notation) — dieselbe Quelle erzeugt die
 * Audios, die Anzeige mit Akut und die Umschrift.
 */
@Serializable
data class RuCourse(val version: Int, val days: List<RuDay>)

@Serializable
data class RuDay(
    val day: Int,
    val title: String,
    val goal: String,
    val sound: RuSoundTip,
    val grammar: String? = null,
    val items: List<RuItem>,
    val dialog: RuDialog,
)

@Serializable
data class RuSoundTip(val title: String, val text: String, val examples: List<String>)

@Serializable
data class RuItem(
    val id: String,
    val ru: String,
    val de: String,
    /** Weibliche Form, falls sich der Satz für Sprecherinnen ändert (устал → устала). */
    val ruF: String? = null,
    val deF: String? = null,
    val note: String? = null,
    /** Wörtliche Übersetzung — hilft, den Satzbau zu verstehen. */
    val lit: String? = null,
    /** IDs passender Antworten — für das Antwort-Training. */
    val replies: List<String> = emptyList(),
)

@Serializable
data class RuDialog(
    val id: String,
    val title: String,
    val scene: String,
    val lines: List<RuLine>,
    /** Name der Gesprächspartnerin in den Sprechblasen. */
    val partner: String = "Anja",
)

@Serializable
data class RuLine(
    val id: String,
    /** "P" = Gesprächspartnerin, "U" = du. */
    val who: String,
    val ru: String,
    val de: String,
    val ruF: String? = null,
    val deF: String? = null,
    /** Kurze Erklärung neuer Wörter, die (noch) nicht im Kurs vorkommen. */
    val gloss: String? = null,
) {
    val isUser: Boolean get() = who == "U"
}

/** Grammatisches Geschlecht für Ich-Sätze (я устал / я устала). */
enum class RuGender { MALE, FEMALE }

/**
 * Ein Lern-Satz, aufgelöst für den Lernenden: Geschlechtsform gewählt,
 * Audio-Datei bestimmt. Items und Dialogzeilen landen beide hier.
 */
data class RuPhrase(
    val id: String,
    val ru: String,
    val de: String,
    val note: String? = null,
    val lit: String? = null,
    val day: Int,
    /** Datei unter assets/russian/audio/ ohne Endung. */
    val audio: String,
) {
    val display: String get() = RussianPhonetics.display(ru)
    val plain: String get() = RussianPhonetics.plain(ru)
    val wordCount: Int get() = plain.split(' ').count { w -> w.any { it.isLetter() } }
}

val ruJson = Json { ignoreUnknownKeys = true }

fun parseRuCourse(text: String): RuCourse = ruJson.decodeFromString(RuCourse.serializer(), text)

/**
 * Audio-Namensschema (vom Generator in tools/russian_audio erzeugt):
 * `<id>_m` / `<id>_f` = männliche/weibliche Stimme für Grundform bzw. ruF.
 * Items ohne ruF haben beide Stimmen; welche spielt, entscheidet die Stimme-
 * Einstellung. Items mit ruF haben die männliche Stimme für ru und die
 * weibliche für ruF — so passt die Stimme immer zur Grammatik.
 */
fun RuItem.resolve(day: Int, gender: RuGender, voice: RuGender): RuPhrase {
    val female = gender == RuGender.FEMALE && ruF != null
    return RuPhrase(
        id = id,
        ru = if (female) ruF!! else ru,
        de = if (gender == RuGender.FEMALE && deF != null) deF else de,
        note = note,
        lit = lit,
        day = day,
        audio = when {
            ruF != null -> if (female) "${id}_f" else "${id}_m"
            voice == RuGender.FEMALE -> "${id}_f"
            else -> "${id}_m"
        },
    )
}

/**
 * Dialogzeilen: Die Partnerin (Anja) hat immer ihre eigene Stimme ("p"),
 * deine Zeilen spricht die Stimme deines Geschlechts. Dateiname:
 * `<id>_<Textform m|f>_<Stimme m|f|p>`.
 */
fun RuLine.resolve(day: Int, gender: RuGender): RuPhrase {
    val female = gender == RuGender.FEMALE
    val textVariant = if (female && ruF != null) "f" else "m"
    val voice = when {
        !isUser -> "p"
        female -> "f"
        else -> "m"
    }
    return RuPhrase(
        id = id,
        ru = if (textVariant == "f") ruF!! else ru,
        de = if (female && deF != null) deF else de,
        note = gloss,
        day = day,
        audio = "${id}_${textVariant}_$voice",
    )
}
