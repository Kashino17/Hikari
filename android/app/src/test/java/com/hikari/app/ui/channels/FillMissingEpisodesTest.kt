package com.hikari.app.ui.channels

import kotlin.test.assertEquals
import org.junit.Test

class FillMissingEpisodesTest {
    private fun ready(url: String, ep: Int?, season: Int? = 1, series: String? = "Ted") =
        ImportCardState.Ready(url = url, title = "", seriesTitle = series, season = season, episode = ep)

    private fun List<ImportCardState>.eps() = map { (it as ImportCardState.Ready).episode }

    @Test fun fuelltNachbarnOhneNummer() {
        val out = fillMissingEpisodeNumbers(listOf(ready("a", 1), ready("b", null), ready("c", null)), null)
        assertEquals(listOf(1, 2, 3), out.eps())
    }

    @Test fun vergibtKeineBereitsBelegteNummer() {
        val out = fillMissingEpisodeNumbers(listOf(ready("a", 1), ready("b", null), ready("c", 2)), null)
        assertEquals(listOf(1, null, 2), out.eps())
    }

    @Test fun fuelltNichtUeberStaffelgrenzen() {
        val out = fillMissingEpisodeNumbers(listOf(ready("a", 5, season = 1), ready("b", null, season = 2)), null)
        assertEquals(listOf(5, null), out.eps())
    }

    @Test fun gescheiterteKarteUnterbrichtDieKette() {
        val out = fillMissingEpisodeNumbers(
            listOf(ready("a", 1), ImportCardState.Failed("x", "boom"), ready("c", null)),
            null,
        )
        assertEquals(1, (out[0] as ImportCardState.Ready).episode)
        assertEquals(null, (out[2] as ImportCardState.Ready).episode)
    }
}
