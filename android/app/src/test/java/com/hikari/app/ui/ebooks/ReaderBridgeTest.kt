package com.hikari.app.ui.ebooks

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class ReaderBridgeTest {
    @Test fun relocateWirdGelesen() {
        val r = ReaderMessages.relocate(
            """{"cfi":"epubcfi(/6/8!/4/2)","fraction":0.25,"chapter":"Zweites Kapitel","minutesLeftInChapter":7,"page":120,"pages":480}""",
        )
        assertEquals(ReaderLocation("epubcfi(/6/8!/4/2)", 0.25, "Zweites Kapitel", 7, 120, 480), r)
    }

    @Test fun kaputtesJsonGibtNullStattAbsturz() {
        assertNull(ReaderMessages.relocate("{kaputt"))
        assertNull(ReaderMessages.relocate("""{"fraction":0.5}"""))
        assertNull(ReaderMessages.selection("null"))
        assertEquals(emptyList(), ReaderMessages.toc("nicht json"))
    }

    @Test fun inhaltsverzeichnisUndAuswahl() {
        val toc = ReaderMessages.toc("""[{"label":"Erstes Kapitel","href":"c1.xhtml","depth":0},{"label":"Teil","href":"c1.xhtml#a","depth":1}]""")
        assertEquals(listOf(TocEntry("Erstes Kapitel", "c1.xhtml", 0), TocEntry("Teil", "c1.xhtml#a", 1)), toc)
        assertEquals(TextSelection("epubcfi(/6/4)", "Ein Satz."), ReaderMessages.selection("""{"cfi":"epubcfi(/6/4)","text":"Ein Satz."}"""))
    }

    @Test fun einstellungenAlsGueltigesJsonAuchMitStandardwerten() {
        fun parse(s: String) = kotlinx.serialization.json.Json.parseToJsonElement(s).let { it as kotlinx.serialization.json.JsonObject }
        fun kotlinx.serialization.json.JsonObject.str(k: String) = (this[k] as kotlinx.serialization.json.JsonPrimitive).content
        val obj = parse(ReaderMessages.settingsJson(ReaderSettings(), columns = 2))
        assertEquals("papier", obj.str("theme"))
        assertEquals("19", obj.str("size"))
        assertEquals("2", obj.str("columns"))
        val nacht = parse(ReaderMessages.settingsJson(ReaderSettings(theme = "nacht", size = 22), columns = 1))
        assertEquals("nacht", nacht.str("theme"))
        assertEquals("1", nacht.str("columns"))
    }
}
