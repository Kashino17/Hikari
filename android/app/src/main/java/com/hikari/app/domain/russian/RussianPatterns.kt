package com.hikari.app.domain.russian

import kotlinx.serialization.Serializable

/** Grammatik-Muster zum Selberbauen — jedes mit emotionaler Szene, Kernsätzen, Bausteinen und Sprechaufgabe. */
@Serializable
data class RuPatterns(val version: Int = 1, val patterns: List<RuPattern> = emptyList())

@Serializable
data class RuPattern(
    val id: String,
    val week: Int = 1,
    val title: String,
    val formula: String,
    val emotion: String = "",
    val hook: String = "",
    val scene: RuScene,
    val core: List<RuCore> = emptyList(),
    val explain: String = "",
    val template: RuTemplate,
    val slots: List<RuSlot> = emptyList(),
    val free: RuFree,
)

@Serializable
data class RuScene(val title: String, val setting: String, val lines: List<RuSceneLine>)

@Serializable
data class RuSceneLine(
    /** "P" = Gesprächspartnerin, "U" = du. */
    val who: String,
    val ru: String,
    val de: String,
    /** Wie man die Zeile spricht (Gefühl, Tempo, Betonung). */
    val how: String = "",
    val ruF: String? = null,
    val deF: String? = null,
) {
    val isUser: Boolean get() = who == "U"
}

@Serializable
data class RuCore(val ru: String, val de: String, val why: String = "")

@Serializable
data class RuTemplate(val ru: String, val de: String)

@Serializable
data class RuSlot(val label: String, val options: List<RuOption>)

@Serializable
data class RuOption(val ru: String, val de: String, val ruF: String? = null, val deF: String? = null)

@Serializable
data class RuFree(val prompt: String, val model: RuCore)

/** Ein vom Lernenden gebauter und gespeicherter Satz. */
@Serializable
data class RuMySentence(
    val ru: String,
    val de: String,
    val patternId: String = "",
    /** "Wem würdest du das sagen?" — der persönliche Anker, an dem der Satz hängt. */
    val note: String = "",
)

object RuPatternLogic {

    /** Setzt die gewählten Bausteine in die Vorlage ein; Sprecherinnen bekommen die weiblichen Formen. */
    fun build(p: RuPattern, choices: List<Int>, gender: RuGender): RuMySentence {
        var ru = p.template.ru
        var de = p.template.de
        p.slots.forEachIndexed { i, slot ->
            val o = slot.options.getOrNull(choices.getOrNull(i) ?: 0) ?: slot.options.first()
            val female = gender == RuGender.FEMALE
            ru = ru.replace("{$i}", if (female && o.ruF != null) o.ruF else o.ru)
            de = de.replace("{$i}", if (female && o.deF != null) o.deF else o.de)
        }
        return RuMySentence(ru = ru, de = de, patternId = p.id)
    }

    fun line(l: RuSceneLine, gender: RuGender): Pair<String, String> =
        if (gender == RuGender.FEMALE && l.ruF != null) l.ruF to (l.deF ?: l.de) else l.ru to l.de

    /** Name des Clips einer Szenenzeile (Partnerin hat eine eigene Stimme). */
    fun lineClip(l: RuSceneLine, gender: RuGender): String {
        val (ru, _) = line(l, gender)
        return if (l.isUser) RuDeepdiveLogic.clipName(ru) else RuDeepdiveLogic.clipName("p|$ru")
    }
}
