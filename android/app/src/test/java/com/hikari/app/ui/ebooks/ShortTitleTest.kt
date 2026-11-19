package com.hikari.app.ui.ebooks

import org.junit.Assert.assertEquals
import org.junit.Test

class ShortTitleTest {
    @Test fun `Untertitel fällt weg`() {
        assertEquals("Andrea Delfin", shortTitle("Andrea Delfin: Eine venezianische Novelle"))
        assertEquals("Macbeth", shortTitle("Macbeth"))
    }

    @Test fun `Teile und Bände bleiben unterscheidbar`() {
        assertEquals("Faust (Teil 1)", shortTitle("Faust: Der Tragödie erster Teil"))
        assertEquals("Faust (Teil 2)", shortTitle("Faust: Der Tragödie zweiter Teil"))
        assertEquals("Faust (Teil 1)", shortTitle("Faust: Eine Tragödie [erster Teil]"))
        assertEquals("Ein Kampf um Rom (Band 2)", shortTitle("Ein Kampf um Rom: Historischer Roman. Zweiter Band"))
        assertEquals("Die Zelle und die Gewebe (Band 2)", shortTitle("Die Zelle und die Gewebe (Vol. 2/2)"))
        assertEquals("Briefe (Band 3)", shortTitle("Briefe: Band 3"))
        assertEquals("Italienische Reise (Band 1)", shortTitle("Italienische Reise — Band 1"))
        assertEquals("Römische Geschichte (Buch 8)", shortTitle("Römische Geschichte — Buch 8"))
    }
}
