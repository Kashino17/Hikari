package com.hikari.app.domain.russian

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RussianPhrasebookTest {
    private val danke = RuPhrase("d01_spasibo", "Спас+ибо!", "Danke!", "Immer passend.", null, 1, "d01_spasibo_m")
    private val wie = RuPhrase("d03_kak_dela", "Как дел+а?", "Wie geht's?", null, "Wie Dinge?", 3, "d03_kak_dela_m")

    @Test fun findetDeutsch() {
        assertTrue(RuPhrasebookSearch.matches(danke, "danke"))
        assertTrue(RuPhrasebookSearch.matches(wie, "wie geht"))
        assertFalse(RuPhrasebookSearch.matches(danke, "tschüss"))
    }

    @Test fun findetRussischMitBetonungUndYo() {
        assertTrue(RuPhrasebookSearch.matches(danke, "спасибо"))
        assertTrue(RuPhrasebookSearch.matches(danke, "СПАСИБО!"))
    }

    @Test fun findetPerLateinUmschrift() {
        assertTrue(RuPhrasebookSearch.matches(danke, "spasibo"))
        assertTrue(RuPhrasebookSearch.matches(wie, "kak dela"))
    }

    @Test fun suchtInNotizUndWoertlicherUebersetzung() {
        assertTrue(RuPhrasebookSearch.matches(danke, "immer passend"))
        assertTrue(RuPhrasebookSearch.matches(wie, "dinge"))
    }

    @Test fun leereSucheTrifftAlles() {
        assertEquals(true, RuPhrasebookSearch.matches(danke, "  "))
    }
}
