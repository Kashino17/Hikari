package com.hikari.app.domain.whatsapp

import com.hikari.app.domain.whatsapp.WhatsAppMediaMatcher.IncomingFile
import com.hikari.app.domain.whatsapp.WhatsAppMediaMatcher.Message
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WhatsAppMediaMatcherTest {

    private val min = 60_000L
    private val t0 = 1_795_126_659_000L // 20.11. 06:17:39 — Foto A aus der Messung

    @Test
    fun `verspaetete Downloads gehen in Eingangsreihenfolge an ihre Nachrichten`() {
        // Messung 20.11.: A 06:17:39, B 06:18:07 (35 s später gelöscht, nie geladen), C 06:20:48.
        // Dateien kamen erst 06:20:48 (WA0002) und 06:20:49 (WA0003).
        val a = Message(1, t0, null)
        val b = Message(2, t0 + 28_000, deletedAt = t0 + 63_000)
        val c = Message(3, t0 + 189_000, null)
        val first = WhatsAppMediaMatcher.messageFor(t0 + 189_000, listOf(a, b, c))
        assertEquals(a, first)
        val second = WhatsAppMediaMatcher.messageFor(t0 + 190_000, listOf(b, c))
        assertEquals("B war vor dem Download gelöscht und bekommt keine fremde Datei", c, second)
    }

    @Test
    fun `alte neu eingelesene Nachricht schnappt sich keine frische Datei`() {
        val old = Message(1, t0 - 13 * 60 * min, null)
        assertNull(WhatsAppMediaMatcher.messageFor(t0, listOf(old)))
    }

    @Test
    fun `Datei vor der Nachricht gehoert nicht zu ihr`() {
        val later = Message(1, t0 + 2 * min, null)
        assertNull(WhatsAppMediaMatcher.messageFor(t0, listOf(later)))
    }

    @Test
    fun `kleine Uhrabweichung wird toleriert`() {
        val m = Message(1, t0 + 3_000, null)
        assertEquals(m, WhatsAppMediaMatcher.messageFor(t0, listOf(m)))
    }

    @Test
    fun `Loeschung kurz nach dem Download behaelt die Datei`() {
        val m = Message(1, t0, deletedAt = t0 + 10_000)
        assertEquals(m, WhatsAppMediaMatcher.messageFor(t0 + 8_000, listOf(m)))
    }

    @Test
    fun `Datei vor der Benachrichtigung wird der Nachricht nachgereicht`() {
        val m = Message(1, t0, null)
        val early = IncomingFile("/a.jpg", t0 + 1_000)
        val tooOld = IncomingFile("/b.jpg", t0 - 10 * min)
        assertEquals(early, WhatsAppMediaMatcher.fileFor(m, listOf(tooOld, early)))
    }
}
