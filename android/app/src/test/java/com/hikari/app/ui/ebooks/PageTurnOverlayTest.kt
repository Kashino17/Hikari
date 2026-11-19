package com.hikari.app.ui.ebooks

import com.hikari.app.ui.manga.PageAnim
import com.hikari.app.ui.manga.turnOverlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Blätter-Ebenen über dem Buch: die untere Seite liegt immer still, die obere bewegt sich je nach Art. */
class PageTurnOverlayTest {
    private val w = 1000f

    @Test fun `untere Seite liegt in jeder Art still`() {
        for (anim in listOf(PageAnim.BOOK, PageAnim.SLIDE, PageAnim.FADE))
            for (p in listOf(0f, 0.3f, 0.8f)) assertEquals(0f, turnOverlay(top = false, p = p, width = w, anim = anim).translationX, 0.01f)
    }

    @Test fun `Blättern dreht die obere Seite um den linken Rücken`() {
        val t = turnOverlay(top = true, p = 0.5f, width = w, anim = PageAnim.BOOK)
        assertEquals(0f, t.translationX, 0.01f)
        assertEquals(0f, t.pivotX, 0f)
        assertTrue(t.rotationY < 0f)
    }

    @Test fun `Gleiten schiebt die obere Seite nach links hinaus`() {
        assertEquals(-400f, turnOverlay(top = true, p = 0.4f, width = w, anim = PageAnim.SLIDE).translationX, 0.01f)
    }

    @Test fun `Ruhig blendet die obere Seite aus`() {
        assertEquals(0.4f, turnOverlay(top = true, p = 0.6f, width = w, anim = PageAnim.FADE).alpha, 0.01f)
    }
}
