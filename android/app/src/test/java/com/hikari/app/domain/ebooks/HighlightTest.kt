package com.hikari.app.domain.ebooks

import org.junit.Assert.assertEquals
import org.junit.Test

class HighlightTest {
    @Test fun `neue Stelle wird markiert`() {
        val s = BookState().withHighlight("cfi1", "Text", "gelb", now = 10, newId = "a")
        assertEquals(listOf("gelb"), s.liveHighlights.map { it.color })
    }

    @Test fun `dieselbe Stelle in anderer Farbe ersetzt die alte Markierung statt sie zu verdoppeln`() {
        val s = BookState()
            .withHighlight("cfi1", "Text", "gelb", now = 10, newId = "a")
            .withHighlight("cfi1", "Text", "blau", now = 20, newId = "b")
        assertEquals(1, s.liveHighlights.size)
        assertEquals("blau", s.liveHighlights.single().color)
        assertEquals("a", s.liveHighlights.single().id) // dieselbe Markierung, nur umgefärbt
        assertEquals(20L, s.liveHighlights.single().at) // neuere Zeit, damit der Abgleich sie übernimmt
    }

    @Test fun `gelöschte Markierung an derselben Stelle lebt wieder auf`() {
        val s = BookState(highlights = listOf(Mark("a", "cfi1", text = "T", color = "gelb", at = 1, deleted = true)))
            .withHighlight("cfi1", "T", "rosa", now = 5, newId = "b")
        assertEquals(listOf("rosa"), s.liveHighlights.map { it.color })
    }
}
