package com.hikari.app.ui.manga

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MangaPageMotionTest {
    private val w = 1000f

    @Test fun `ruhende Seite bleibt unverändert`() {
        for (anim in PageAnim.entries) {
            val t = pageTransform(idx = 3, position = 3f, width = w, direction = ReadDirection.RTL, anim = anim)
            assertEquals(0f, t.translationX, 0.01f)
            assertEquals(0f, t.rotationY, 0.01f)
            assertEquals(1f, t.alpha, 0.01f)
            assertEquals(0f, t.shade, 0.01f)
        }
    }

    @Test fun `Blättern - obere Seite dreht sich um den Rücken, die nächste liegt still darunter`() {
        // Halb umgeblättert von Seite 3 auf 4, japanische Leserichtung: Rücken rechts.
        val top = pageTransform(3, 3.5f, w, ReadDirection.RTL, PageAnim.BOOK)
        val under = pageTransform(4, 3.5f, w, ReadDirection.RTL, PageAnim.BOOK)
        assertTrue("obere Seite liegt oben", top.z > under.z)
        assertEquals(1f, top.pivotX, 0f)
        assertTrue(top.rotationY > 0f)
        // Pager hat Seite 3 um 0,5 Seiten verschoben (rückwärts-Layout: nach rechts) — wird aufgehoben.
        assertEquals(-500f, top.translationX, 0.01f)
        assertEquals(500f, under.translationX, 0.01f)
        assertTrue("darunter abgedunkelt, solange die Seite noch nicht weg ist", under.shade > 0f)
    }

    @Test fun `westliche Leserichtung dreht um den linken Rücken in die andere Richtung`() {
        val top = pageTransform(3, 3.5f, w, ReadDirection.LTR, PageAnim.BOOK)
        assertEquals(0f, top.pivotX, 0f)
        assertTrue(top.rotationY < 0f)
        assertEquals(500f, top.translationX, 0.01f)
    }

    @Test fun `über 90 Grad wird die Seite unsichtbar statt gespiegelt`() {
        val top = pageTransform(3, 3.95f, w, ReadDirection.RTL, PageAnim.BOOK)
        assertEquals(0f, top.alpha, 0f)
    }

    @Test fun `Gleiten - obere Seite fährt mit, die nächste bleibt liegen`() {
        val top = pageTransform(3, 3.3f, w, ReadDirection.LTR, PageAnim.SLIDE)
        val under = pageTransform(4, 3.3f, w, ReadDirection.LTR, PageAnim.SLIDE)
        assertEquals(0f, top.translationX, 0.01f)
        assertEquals(-700f, under.translationX, 0.01f)
    }

    @Test fun `Ruhig - beide liegen still, die obere blendet aus`() {
        val top = pageTransform(3, 3.25f, w, ReadDirection.RTL, PageAnim.FADE)
        val under = pageTransform(4, 3.25f, w, ReadDirection.RTL, PageAnim.FADE)
        assertEquals(-250f, top.translationX, 0.01f)
        assertEquals(750f, under.translationX, 0.01f)
        assertEquals(0.75f, top.alpha, 0.01f)
    }

    @Test fun `Tippen - Ränder blättern je nach Leserichtung, die Mitte öffnet das Menü`() {
        assertEquals(TapAction.NEXT, tapAction(0.1f, ReadDirection.RTL, tapTurn = true, menuOpen = false))
        assertEquals(TapAction.PREV, tapAction(0.9f, ReadDirection.RTL, tapTurn = true, menuOpen = false))
        assertEquals(TapAction.NEXT, tapAction(0.9f, ReadDirection.LTR, tapTurn = true, menuOpen = false))
        assertEquals(TapAction.MENU, tapAction(0.5f, ReadDirection.RTL, tapTurn = true, menuOpen = false))
        // knapp neben dem Rand (25 %) blättert nicht mehr
        assertEquals(TapAction.MENU, tapAction(0.25f, ReadDirection.RTL, tapTurn = true, menuOpen = false))
    }

    @Test fun `Tippen - offenes Menü wird nur geschlossen, ausgeschaltet blättert nichts`() {
        assertEquals(TapAction.CLOSE_MENU, tapAction(0.05f, ReadDirection.RTL, tapTurn = true, menuOpen = true))
        assertEquals(TapAction.MENU, tapAction(0.05f, ReadDirection.RTL, tapTurn = false, menuOpen = false))
    }

    @Test fun `Einstellungen überstehen fehlerhafte oder alte Daten`() {
        assertEquals(MangaReaderSettings(), MangaReaderSettings.decode("{kaputt"))
        assertEquals(MangaReaderSettings(), MangaReaderSettings.decode(null))
        val s = MangaReaderSettings(direction = ReadDirection.VERTICAL, anim = PageAnim.FADE, tapTurn = false)
        assertEquals(s, MangaReaderSettings.decode(s.encode()))
        // Unbekannte Felder aus späteren Versionen stören nicht.
        assertEquals(MangaReaderSettings(anim = PageAnim.SLIDE), MangaReaderSettings.decode("""{"anim":"SLIDE","neu":1}"""))
    }
}
