package com.hikari.app.ui.russian

import com.hikari.app.domain.russian.RuPhrase
import org.junit.Assert.assertEquals
import org.junit.Test

class RussianBlitzReviewTest {
    private fun phrase(id: String) = RuPhrase(id = id, ru = "прив+ет", de = "hallo $id", day = 1, audio = id)

    @Test fun jederSatzNurEinmalUndSchlechtestesErgebnisZaehlt() {
        val heard = mutableListOf<BlitzHeard>()
        heard.record(phrase("a"), true)
        heard.record(phrase("b"), true)
        heard.record(phrase("a"), false) // später falsch → bleibt falsch
        heard.record(phrase("b"), null)  // später unbeantwortet → schlechter als richtig
        heard.record(phrase("a"), true)  // wieder richtig → ändert nichts mehr
        assertEquals(listOf("a" to false, "b" to null), heard.map { it.phrase.id to it.correct })
    }

    @Test fun abschlusslisteZeigtFalschZuerstDannUnbeantwortetDannRichtig() {
        val heard = mutableListOf<BlitzHeard>()
        heard.record(phrase("r1"), true)
        heard.record(phrase("f1"), false)
        heard.record(phrase("r2"), true)
        heard.record(phrase("u1"), null)
        heard.record(phrase("f2"), false)
        assertEquals(listOf("f1", "f2", "u1", "r1", "r2"), heard.forReview().map { it.phrase.id })
    }
}
