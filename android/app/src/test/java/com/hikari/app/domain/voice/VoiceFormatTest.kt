package com.hikari.app.domain.voice

import com.hikari.app.data.api.dto.VoiceSummaryDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceFormatTest {

    private val summary = VoiceSummaryDto(
        title = "Projekt Sonnenschein",
        summary = "Die Stadt teilt das Projekt in zwei Phasen.",
        points = listOf("Budget 18.000 Euro"),
        todos = listOf("Kalkulation bis Freitag schicken"),
        dates = listOf("Montag: Termin mit Frau Keller"),
        questions = emptyList(),
    )

    @Test
    fun `Statuszeile zählt den gerade laufenden Clip`() {
        assertEquals("Transkribiere 1 von 3", VoiceFormat.statusLine("transcribing", 0, 3))
        assertEquals("Transkribiere 3 von 3", VoiceFormat.statusLine("transcribing", 3, 3))
        assertEquals("Transkribiere", VoiceFormat.statusLine("transcribing", 0, 1))
        assertEquals("Wartet (2 vor dir)", VoiceFormat.statusLine("queued", 0, 1, queuePosition = 2))
        assertEquals("Qwen fasst zusammen", VoiceFormat.statusLine("summarizing", 3, 3))
    }

    @Test
    fun `Fortschritt steigt monoton bis fertig`() {
        val steps = listOf(
            VoiceFormat.progress("queued", 0, 2),
            VoiceFormat.progress("transcribing", 0, 2),
            VoiceFormat.progress("transcribing", 1, 2),
            VoiceFormat.progress("summarizing", 2, 2),
            VoiceFormat.progress("done", 2, 2),
        )
        assertEquals(steps.sorted(), steps)
        assertEquals(1f, steps.last())
    }

    @Test
    fun `Dauer mit und ohne Stunden`() {
        assertEquals("1:15", VoiceFormat.duration(75.4))
        assertEquals("0:05", VoiceFormat.duration(4.6))
        assertEquals("1:02:05", VoiceFormat.duration(3725.0))
        assertEquals("", VoiceFormat.duration(null))
        assertEquals("3 Sprachnachrichten · 4:12", VoiceFormat.meta(3, 252.0))
        assertEquals("1 Sprachnachricht", VoiceFormat.meta(1, null))
    }

    @Test
    fun `Klartext enthält nur gefüllte Abschnitte`() {
        val text = VoiceFormat.asText(summary, label = "Mehmet")
        assertTrue(text.startsWith("Projekt Sonnenschein (Mehmet)\n\nDie Stadt"))
        assertTrue(text.contains("Aufgaben:\n• Kalkulation bis Freitag schicken"))
        assertTrue(text.contains("Termine und Zahlen:\n• Montag"))
        assertFalse(text.contains("Offene Fragen"))
    }

    @Test
    fun `Benachrichtigung zeigt Aufgaben aber keine Kernpunkte`() {
        val text = VoiceFormat.notificationText(summary)
        assertTrue(text.contains("Aufgaben"))
        assertFalse(text.contains("Budget"))
    }

    @Test
    fun `Benachrichtigung kürzt die Kurzfassung auf zwei Sätze`() {
        val long = summary.copy(summary = "Erster Satz mit 18.000 Euro. Zweiter Satz! Dritter Satz. Vierter.")
        val text = VoiceFormat.notificationText(long)
        assertTrue(text.startsWith("Erster Satz mit 18.000 Euro. Zweiter Satz!\n\nAufgaben"))
        assertFalse(text.contains("Dritter"))
        assertEquals(
            "Termin am 3. März um 10 Uhr. Danach Abnahme.",
            VoiceFormat.firstSentences("Termin am 3. März um 10 Uhr. Danach Abnahme. Rest.", 2),
        )
    }

    @Test
    fun `Dateinamen werden gesäubert`() {
        assertEquals("PTT-20261106-WA0012.opus", VoiceFormat.safeFileName("PTT-20261106-WA0012.opus", 0))
        assertEquals("x.opus", VoiceFormat.safeFileName("../../etc/x.opus", 0))
        assertEquals("Sprachnachricht-3.audio", VoiceFormat.safeFileName(null, 2))
        assertEquals("Sprachnachricht-1.audio", VoiceFormat.safeFileName("   ", 0))
    }

    @Test
    fun `Fehler werden übersetzt`() {
        assertEquals(
            "LM Studio war nicht erreichbar. Läuft es auf dem Mac?",
            VoiceFormat.errorText("lmstudio_503: down"),
        )
        assertEquals("Auf den Aufnahmen war nichts zu verstehen.", VoiceFormat.errorText("Auf den Aufnahmen war nichts zu verstehen."))
    }
}
