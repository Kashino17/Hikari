package com.hikari.app.domain.feed

import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/** Prüft die Inhaltsdateien unter assets/feed: lesbar, vollständig, jede Karte mit echter Vertiefung. */
class FeedContentTest {
    private val dir = File("src/main/assets/feed")

    private fun cards(module: MindfulModuleType): List<MindfulCard> =
        FeedContentParser.parse(module, File(dir, FeedContentParser.FILES.getValue(module)).readText())

    @Test fun jederBereichHatGenugKartenFuerWochenOhneWiederholung() {
        for ((module, file) in FeedContentParser.FILES) {
            val list = cards(module)
            assertTrue(list.size >= 20, "$file: nur ${list.size} Karten")
            assertEquals(list.size, list.map { it.id }.toSet().size, "$file: doppelte IDs")
            assertTrue(list.all { it.type == module && it.id.startsWith("${module.id}:") }, file)
        }
        val total = FeedContentParser.FILES.keys.sumOf { cards(it).size }
        assertTrue(total >= 330, "insgesamt nur $total Karten")
    }

    @Test fun jedeKarteHatEineGeschriebeneVertiefungMitNeuemInhalt() {
        for ((module, file) in FeedContentParser.FILES) {
            for (card in cards(module)) {
                val deep = card.resolveDeepDive()
                assertTrue(deep.isAuthored, "${card.id}: keine Vertiefung")
                assertTrue(deep.title.isNotBlank() && deep.keyInsight.isNotBlank(), "${card.id}: Kopf fehlt")
                assertTrue(deep.sections.size in 2..3, "${card.id}: ${deep.sections.size} Abschnitte")
                for (section in deep.sections) {
                    assertTrue(section.title.isNotBlank(), "${card.id}: Abschnitt ohne Überschrift")
                    assertTrue(section.text.split(' ').size >= 45, "${card.id}: Abschnitt »${section.title}« ist zu dünn")
                }
                assertTrue(!deep.practicalApplication.isNullOrBlank(), "${card.id}: nichts zum Ausprobieren")
                assertTrue(deep.relatedTakeaways.isNotEmpty(), "${card.id}: keine Merksätze")
                assertTrue(file.isNotBlank())
            }
        }
    }

    @Test fun quizKartenHabenVierAntwortenUndVerteilteLoesungen() {
        val puzzles = cards(MindfulModuleType.BRAIN_PUZZLE).filterIsInstance<BrainPuzzleCardItem>()
        val geo = cards(MindfulModuleType.GEOGRAPHY).filterIsInstance<GeographyCardItem>()
        assertTrue(puzzles.all { it.options.size == 4 && it.correctIndex in 0..3 && it.options.toSet().size == 4 })
        assertTrue(geo.all { it.options.size == 4 && it.correctIndex in 0..3 && it.options.toSet().size == 4 })
        // Steht die Lösung immer an derselben Stelle, lernt man die Stelle statt der Antwort.
        assertEquals(4, puzzles.map { it.correctIndex }.toSet().size)
        assertEquals(4, geo.map { it.correctIndex }.toSet().size)
    }

    @Test fun kartenTexteBleibenKurzGenugFuerEineSeite() {
        val history = cards(MindfulModuleType.HISTORY).filterIsInstance<HistoryCardItem>()
        assertTrue(history.all { it.description.length <= 340 && it.whyItMatters.length <= 220 })
        val quotes = cards(MindfulModuleType.QUOTE).filterIsInstance<QuoteCardItem>()
        assertTrue(quotes.all { it.quote.length <= 200 && it.author.isNotBlank() })
        val science = cards(MindfulModuleType.SCIENCE).filterIsInstance<ScienceCardItem>()
        assertTrue(science.all { it.coreExplanation.length <= 340 })
    }
}
