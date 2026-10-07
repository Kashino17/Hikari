package com.hikari.app.domain.browser

import kotlin.test.assertEquals
import org.junit.Test

class EpisodeLinkSeasonTest {
    private val page = "https://serienstream.to/serie/ted/staffel-1"

    @Test fun fremdeStaffelFaelltNichtMitGleicherNummerZusammen() {
        val links = listOf(
            PageLink("https://serienstream.to/serie/ted/staffel-1/episode-3", "3"),
            PageLink("https://serienstream.to/serie/ted/staffel-2/episode-3", "3"),
        )
        val out = EpisodeLinkFilter.extract(page, links)
        assertEquals(listOf("https://serienstream.to/serie/ted/staffel-1/episode-3"), out.map { it.url })
    }

    @Test fun gleichesLabelAndereUrlBleibtGetrennt() {
        val links = listOf(
            PageLink("https://serienstream.to/serie/ted/staffel-1/episode-1", "Pilot"),
            PageLink("https://serienstream.to/serie/ted/staffel-1/episode-2", "Pilot"),
        )
        assertEquals(listOf(1, 2), EpisodeLinkFilter.extract(page, links).map { it.episode })
    }

    @Test fun seasonOfLiestNurDenPfad() {
        assertEquals(2, EpisodeLinkFilter.seasonOf("https://x.test/serie/a/staffel-2/episode-1"))
        assertEquals(null, EpisodeLinkFilter.seasonOf("https://x.test/serie/a/episode-1?s=5"))
    }
}
