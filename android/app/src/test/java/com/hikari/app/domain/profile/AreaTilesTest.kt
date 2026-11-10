package com.hikari.app.domain.profile

import com.hikari.app.domain.russian.RuCard
import com.hikari.app.domain.russian.RuProgress
import java.time.LocalDate
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class AreaTilesTest {
    private val today = 20_000L
    private val titles = mapOf(1 to "Begrüßen", 2 to "Sich vorstellen", 3 to "Im Café")

    private fun tile(p: RuProgress, courseDays: Int = 10, podcastDays: Int = 5) =
        AreaTiles.russian(p, today, courseDays, podcastDays) { titles[it] }

    @Test fun neuerLerner_bekommtLektionEinsMitTitel() {
        val t = tile(RuProgress())
        assertEquals("Lektion 1", t.headline)
        assertEquals("Begrüßen", t.detail)
        assertEquals(0f, t.progress)
        assertFalse(t.done)
    }

    @Test fun naechsteLektion_folgtAufDieLetzteAbgeschlossene() {
        val t = tile(RuProgress(dayStars = mapOf(1 to 3, 2 to 2), courseLastCompletedDay = today - 1))
        assertEquals("Lektion 3", t.headline)
        assertEquals("Im Café", t.detail)
        assertEquals(0.2f, t.progress)
    }

    @Test fun lektionOhneTitel_nenntDenKurs() {
        val t = tile(RuProgress(dayStars = (1..5).associateWith { 3 }, courseLastCompletedDay = today - 1))
        assertEquals("Lektion 6", t.headline)
        assertEquals("Sprechkurs", t.detail)
    }

    @Test fun nachDerLektion_kommenFaelligeSaetze() {
        val p = RuProgress(
            dayStars = mapOf(1 to 3),
            courseLastCompletedDay = today,
            cards = mapOf("a" to RuCard(due = today), "b" to RuCard(due = today - 2), "c" to RuCard(due = today + 3)),
        )
        val t = tile(p)
        assertEquals("2 Sätze wiederholen", t.headline)
        assertEquals("Lektion 1 ist geschafft", t.detail)
        assertFalse(t.done)
    }

    @Test fun einFaelligerSatz_stehtInDerEinzahl() {
        val p = RuProgress(dayStars = mapOf(1 to 3), courseLastCompletedDay = today, cards = mapOf("a" to RuCard(due = today)))
        assertEquals("1 Satz wiederholen", tile(p).headline)
    }

    @Test fun ohneFaelliges_kommtDerPodcast() {
        val p = RuProgress(dayStars = mapOf(1 to 3), courseLastCompletedDay = today, podcastDayStars = mapOf(1 to 3))
        val t = tile(p)
        assertEquals("Podcast, Folge 2", t.headline)
        assertFalse(t.done)
    }

    @Test fun gehoerteFolge_fuehrtZuDenUebungen() {
        val p = RuProgress(dayStars = mapOf(1 to 3), courseLastCompletedDay = today, podcastListened = setOf(1))
        val t = tile(p)
        assertEquals("Podcast, Folge 1", t.headline)
        assertEquals("Übungen zur Folge", t.detail)
    }

    @Test fun allesErledigt_laedtNichtMehrZumStartEin() {
        val p = RuProgress(
            dayStars = mapOf(1 to 3),
            courseLastCompletedDay = today,
            podcastDayStars = mapOf(1 to 3),
            podcastLastCompletedDay = today,
        )
        val t = tile(p)
        assertEquals("Heute geschafft", t.headline)
        assertEquals("Morgen geht es weiter", t.detail)
        assertTrue(t.done)
    }

    @Test fun lernzeit_wirdKurzAngegeben() {
        assertNull(AreaTiles.studyMinutes(0))
        assertNull(AreaTiles.studyMinutes(59))
        assertEquals("1 min", AreaTiles.studyMinutes(60))
        assertEquals("42 min", AreaTiles.studyMinutes(42 * 60 + 30))
        assertEquals("1 Std", AreaTiles.studyMinutes(3600))
        assertEquals("1 Std 5 min", AreaTiles.studyMinutes(3900))
    }

    @Test fun berichtstag_heuteGesternOderDatum() {
        val today = LocalDate.of(2026, 11, 11)
        val berlin = ZoneId.of("Europe/Berlin")
        assertEquals("Heute", AreaTiles.newsDay("2026-11-11T06:00:00+01:00", today, berlin))
        assertEquals("Gestern", AreaTiles.newsDay("2026-11-10T05:30:00Z", today, berlin))
        assertEquals("Sonntag, 8. November", AreaTiles.newsDay("2026-11-08", today, berlin))
        // 23:30 UTC ist in Berlin schon der nächste Tag.
        assertEquals("Heute", AreaTiles.newsDay("2026-11-10T23:30:00Z", today, berlin))
        assertNull(AreaTiles.newsDay("irgendwann", today, berlin))
    }

    // ── Manga ────────────────────────────────────────────────────────────────

    private val onePiece = MangaShelfEntry("One Piece", "c/one-piece.jpg", totalChapters = 762)
    private val berserk = MangaShelfEntry("Berserk", "c/berserk.jpg", totalChapters = 380)
    private val ohneBild = MangaShelfEntry("Akira", null)

    @Test fun manga_nochNichtsAngefangen_zeigtDieErsteReiheMitBild() {
        val t = AreaTiles.manga(emptyList(), listOf(ohneBild, berserk, onePiece))!!
        assertEquals("Berserk", t.title)
        assertEquals("380 Kapitel", t.detail)
        assertEquals(listOf("c/berserk.jpg", "c/one-piece.jpg"), t.covers)
    }

    @Test fun manga_angefangeneReihe_gehtVorUndNenntDasKapitel() {
        val reading = listOf(MangaReading("One Piece", "c/one-piece.jpg", "onepiecetube:one-piece:1094", page = 7))
        val t = AreaTiles.manga(reading, listOf(berserk, onePiece))!!
        assertEquals("One Piece", t.title)
        assertEquals("Kapitel 1094", t.detail)
        assertEquals(listOf("c/one-piece.jpg", "c/berserk.jpg"), t.covers)
    }

    @Test fun manga_ohneLesbareKapitelnummer_nenntDieSeite() {
        val reading = listOf(MangaReading("X", null, "quelle:x:prolog", page = 3))
        assertEquals("Seite 3", AreaTiles.manga(reading, emptyList())!!.detail)
    }

    @Test fun manga_reiheOhneBildUndKapitel_bleibtEhrlich() {
        val t = AreaTiles.manga(emptyList(), listOf(ohneBild))!!
        assertEquals("Akira", t.title)
        assertEquals("Noch nicht angefangen", t.detail)
        assertTrue(t.covers.isEmpty())
    }

    @Test fun manga_leeresRegal_hatNichtsZuZeigen() {
        assertNull(AreaTiles.manga(emptyList(), emptyList()))
    }

    @Test fun manga_hoechstensDreiTitelbilder() {
        val shelf = (1..5).map { MangaShelfEntry("M$it", "c/$it.jpg") }
        assertEquals(listOf("c/1.jpg", "c/2.jpg", "c/3.jpg"), AreaTiles.manga(emptyList(), shelf)!!.covers)
    }

    @Test fun kapitelnummer_ganzOderMitNachkommastelle() {
        assertEquals("1094", AreaTiles.chapterNumber("onepiecetube:one-piece:1094"))
        assertEquals("12.5", AreaTiles.chapterNumber("q:reihe:12.5"))
        assertEquals("7", AreaTiles.chapterNumber("q:reihe:7.0"))
        assertNull(AreaTiles.chapterNumber("q:reihe:extra"))
        assertNull(AreaTiles.chapterNumber("ohne-doppelpunkt"))
    }

    @Test fun spielname_kommtAusDerKennung() {
        assertEquals("Sky Strike", AreaTiles.gameTitle("spaceshooter"))
        assertNull(AreaTiles.gameTitle("unbekannt"))
        assertNull(AreaTiles.gameTitle(null))
    }
}
