package com.hikari.app.domain.browser

import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class EpisodeLinkFilterTest {

    private val page = "https://serien.test/solo-leveling/staffel-1"

    @Test
    fun erkenntFolgenNummernAusDerUrl() {
        val links = listOf(
            PageLink("https://serien.test/solo-leveling/folge-1", "Folge 1"),
            PageLink("https://serien.test/solo-leveling/folge-2", "Folge 2"),
            PageLink("https://serien.test/solo-leveling/folge-10", "Folge 10"),
        )
        val out = EpisodeLinkFilter.extract(page, links)
        assertEquals(listOf(1, 2, 10), out.map { it.episode })
    }

    @Test
    fun erkenntStaffelEpisodenSchema() {
        val links = listOf(PageLink("https://serien.test/x/S01E07", "Zur Folge"))
        assertEquals(7, EpisodeLinkFilter.extract(page, links)[0].episode)
    }

    @Test
    fun erkenntFolgennummerAusDemLinktext() {
        val links = listOf(PageLink("https://serien.test/watch/abc123xyz", "Folge 4"))
        assertEquals(4, EpisodeLinkFilter.extract(page, links)[0].episode)
    }

    // Ohne Domain-Bindung landet die halbe Navigationsleiste in der Liste.
    @Test
    fun ignoriertFremdeDomains() {
        val links = listOf(PageLink("https://werbung.test/folge-1", "Folge 1"))
        assertTrue(EpisodeLinkFilter.extract(page, links).isEmpty())
    }

    @Test
    fun ignoriertLinksOhneFolgennummer() {
        val links = listOf(
            PageLink("https://serien.test/impressum", "Impressum"),
            PageLink("https://serien.test/kontakt", "Kontakt"),
        )
        assertTrue(EpisodeLinkFilter.extract(page, links).isEmpty())
    }

    @Test
    fun dedupliziertUndSortiert() {
        val links = listOf(
            PageLink("https://serien.test/folge-3", "Folge 3"),
            PageLink("https://serien.test/folge-3#top", "Folge 3 nochmal"),
            PageLink("https://serien.test/folge-1", "Folge 1"),
        )
        val out = EpisodeLinkFilter.extract(page, links)
        assertEquals(listOf(1, 3), out.map { it.episode })
    }

    @Test
    fun schliesstDieAktuelleSeiteAus() {
        val links = listOf(PageLink("$page#top", "Diese Seite"))
        assertTrue(EpisodeLinkFilter.extract(page, links).isEmpty())
    }

}

/**
 * Seiten hinter einem Bot-Schutz tragen waehrend der Pruefung einen
 * Platzhaltertitel. Wird in genau dem Moment eingesammelt, landet er als
 * Videotitel in der Bibliothek — eine Folge Modern Family hiess deshalb
 * "Security Check" und war unter dem Namen nicht wiederzufinden.
 */
class PageTitleFilterTest {

    @Test
    fun verwirftSchutzseitenTitel() {
        for (t in listOf(
            "Security Check",
            "Just a moment...",
            "Attention Required! | Cloudflare",
            "DDoS-Guard",
            "Bitte warten…",
            "Checking your browser before accessing",
            "Einen Moment bitte",
            "  ACCESS DENIED  ",
        )) {
            assertNull(PageTitleFilter.clean(t), "haette verworfen werden muessen: $t")
        }
    }

    @Test
    fun behaeltEchteTitel() {
        for (t in listOf(
            "Modern Family Staffel 1 Folge 1",
            "Interstellar jetzt streamen",
            "Solo Leveling S01E03",
        )) {
            assertEquals(t, PageTitleFilter.clean(t))
        }
    }

    @Test
    fun verwirftLeereUndZuKurzeTitel() {
        assertNull(PageTitleFilter.clean(""))
        assertNull(PageTitleFilter.clean("   "))
        assertNull(PageTitleFilter.clean("ab"))
        assertNull(PageTitleFilter.clean(null))
    }

    @Test
    fun kuerztUebermaessigLangeTitel() {
        val lang = "x".repeat(400)
        val out = PageTitleFilter.clean(lang)
        assertEquals(200, out?.length)
    }
}
