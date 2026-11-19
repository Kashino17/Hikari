package com.hikari.app.ui.ebooks

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BookCoverStyleTest {
    @Test fun gleicherTitelGleichesMuster() {
        assertEquals(coverOrnament("Effi Briest"), coverOrnament("Effi Briest"))
    }

    @Test fun vierMusterWerdenAuchGenutzt() {
        val titles = listOf("Effi Briest", "Siddhartha", "Der Tod in Venedig", "Nachtstücke", "Woyzeck", "Faust", "Max und Moritz", "Die Biene Maja", "Der Prozess", "Emil")
        val used = titles.map(::coverOrnament).toSet()
        assertTrue(used.all { it in 0..3 })
        assertTrue(used.size >= 3, "nur $used")
    }
}
