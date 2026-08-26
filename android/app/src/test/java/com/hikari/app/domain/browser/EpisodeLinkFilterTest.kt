package com.hikari.app.domain.browser

import kotlin.test.assertEquals
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
