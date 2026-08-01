package com.hikari.app.ui.news

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals

class RelativeTimeTest {

    private val now: Instant = Instant.parse("2026-08-01T12:00:00Z")

    @Test
    fun `gerade eben bei unter einer Minute`() {
        assertEquals("gerade eben", formatRelativeTime("2026-08-01T11:59:30Z", now))
    }

    @Test
    fun `zukuenftige Zeitstempel sind gerade eben`() {
        assertEquals("gerade eben", formatRelativeTime("2026-08-01T13:00:00Z", now))
    }

    @Test
    fun `Minuten`() {
        assertEquals("vor 5 Min.", formatRelativeTime("2026-08-01T11:55:00Z", now))
    }

    @Test
    fun `Stunden`() {
        assertEquals("vor 2 Std.", formatRelativeTime("2026-08-01T10:00:00Z", now))
    }

    @Test
    fun `Tage`() {
        assertEquals("vor 3 T.", formatRelativeTime("2026-07-29T12:00:00Z", now))
    }

    @Test
    fun `aelter als eine Woche wird Datum`() {
        assertEquals("20.07.2026", formatRelativeTime("2026-07-20T12:00:00Z", now))
    }

    @Test
    fun `ISO mit Offset wird akzeptiert`() {
        assertEquals("vor 1 Std.", formatRelativeTime("2026-08-01T13:00:00+02:00", now))
    }

    @Test
    fun `unlesbarer Zeitstempel gibt leeren String`() {
        assertEquals("", formatRelativeTime("kein-datum", now))
    }
}
