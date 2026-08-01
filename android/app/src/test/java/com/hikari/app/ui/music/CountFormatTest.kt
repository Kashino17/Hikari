package com.hikari.app.ui.music

import org.junit.Assert.assertEquals
import org.junit.Test

class CountFormatTest {

    @Test
    fun `small numbers stay plain`() {
        assertEquals("0", formatCountDE(0))
        assertEquals("999", formatCountDE(999))
    }

    @Test
    fun `thousands use Tsd`() {
        assertEquals("850 Tsd.", formatCountDE(850_000))
        assertEquals("1 Tsd.", formatCountDE(1_000))
        assertEquals("1,2 Tsd.", formatCountDE(1_234))
    }

    @Test
    fun `millions use Mio`() {
        assertEquals("4,5 Mio.", formatCountDE(4_520_000))
        assertEquals("12 Mio.", formatCountDE(12_000_000))
    }

    @Test
    fun `billions use Mrd`() {
        assertEquals("1,2 Mrd.", formatCountDE(1_200_000_000))
        assertEquals("3 Mrd.", formatCountDE(3_000_000_000))
    }

    @Test
    fun `labels for the artist page`() {
        assertEquals("4,5 Mio. Abonnenten", formatSubscribersDE(4_520_000))
        assertEquals("850 Tsd. Abonnenten", formatSubscribersDE(850_000))
        assertEquals("1,8 Mrd. Aufrufe", formatViewsDE(1_800_000_000))
    }
}
