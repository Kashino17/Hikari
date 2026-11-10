package com.hikari.app.domain.russian

/**
 * Sprachniveau aus dem Lernstand — dieselben Richtwerte wie in der Statistik des
 * Russisch-Bereichs (assets/russian-web/js/domain/level.js). Ein Niveau ist erreicht,
 * wenn ALLE Fähigkeiten sein Ziel erfüllen.
 */
object RuLevel {
    data class Measure(val vocab: Int, val speak: Int, val listen: Int, val grammar: Int, val talk: Int)

    private val ORDER = listOf("A0", "A1", "A2", "B1", "B2")
    private val TARGETS = mapOf(
        "A1" to Measure(80, 60, 5, 3, 0),
        "A2" to Measure(200, 250, 20, 10, 0),
        "B1" to Measure(450, 700, 50, 25, 40),
        "B2" to Measure(950, 1600, 100, 25, 200),
    )

    fun measure(p: RuProgress, talk: Int = 0) = Measure(
        vocab = RuSrs.knownCount(p.cards),
        speak = p.spokenOk,
        listen = p.podcastDayStars.size,
        grammar = p.patternsDone.size,
        talk = talk,
    )

    fun estimate(m: Measure): String {
        var level = ORDER.first()
        for (id in ORDER.drop(1)) {
            val t = TARGETS.getValue(id)
            val reached = m.vocab >= t.vocab && m.speak >= t.speak && m.listen >= t.listen &&
                m.grammar >= t.grammar && m.talk >= t.talk
            if (reached) level = id else break
        }
        return level
    }
}
