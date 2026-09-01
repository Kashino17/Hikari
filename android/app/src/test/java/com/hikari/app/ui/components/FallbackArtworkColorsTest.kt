package com.hikari.app.ui.components

import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class FallbackArtworkColorsTest {

    @Test
    fun gleicherTitelLiefertGleicheFarben() {
        assertEquals(
            fallbackArtworkColors("Solo Leveling"),
            fallbackArtworkColors("Solo Leveling"),
        )
    }

    @Test
    fun grossKleinschreibungUndRandLeerzeichenSindEgal() {
        assertEquals(
            fallbackArtworkColors("Solo Leveling"),
            fallbackArtworkColors("  solo leveling "),
        )
    }

    @Test
    fun verschiedeneTitelLiefernVerschiedenePaletten() {
        val titel = listOf(
            "Solo Leveling",
            "One Piece",
            "Breaking Bad",
            "Attack on Titan",
            "Dune",
            "Naruto",
            "Das Boot",
            "Dark",
        )
        val paletten = titel.map(::fallbackArtworkColors)
        assertEquals(
            paletten.size,
            paletten.distinct().size,
            "Jeder Titel sollte eine eigene Palette bekommen: $paletten",
        )
    }

    @Test
    fun leererUndBlankerTitelGebenDieNeutralePalette() {
        val neutral = fallbackArtworkColors("")
        assertEquals(neutral, fallbackArtworkColors("   "))
        assertEquals(neutral, fallbackArtworkColors("\n\t "))
    }

    @Test
    fun neutralePaletteIstGraustufig() {
        // Neutral heißt nicht rein grau — ein leichter Blau-Stich passt zum
        // Anthrazit des Themes (HikariBg). Bunt darf es aber nicht werden.
        val neutral = fallbackArtworkColors("")
        for (argb in listOf(neutral.start, neutral.end, neutral.glow)) {
            val r = ((argb shr 16) and 0xFF).toInt()
            val g = ((argb shr 8) and 0xFF).toInt()
            val b = (argb and 0xFF).toInt()
            val spread = maxOf(r, g, b) - minOf(r, g, b)
            assertTrue(spread <= 10, "Neutral muss nahezu graustufig sein: $argb")
        }
    }

    @Test
    fun alleFarbenSindDeckend() {
        val c = fallbackArtworkColors("Irgendein Titel")
        for (argb in listOf(c.start, c.end, c.glow)) {
            assertEquals(0xFFL, (argb ushr 24) and 0xFF, "Alpha muss deckend sein: $argb")
        }
    }

    @Test
    fun verlaufEndetDunklerAlsErBeginnt() {
        // Cinematic-Look: unten fast schwarz, oben satt — für jede Palette.
        fun helligkeit(argb: Long) =
            ((argb shr 16) and 0xFF) + ((argb shr 8) and 0xFF) + (argb and 0xFF)
        listOf("One Piece", "Dark", "xyz", "Sommer 2024").forEach { titel ->
            val c = fallbackArtworkColors(titel)
            assertTrue(
                helligkeit(c.end) < helligkeit(c.start),
                "Ende sollte dunkler sein als der Anfang ($titel): $c",
            )
            assertTrue(
                helligkeit(c.glow) > helligkeit(c.start),
                "Glanz sollte heller sein als der Anfang ($titel): $c",
            )
        }
    }

    @Test
    fun hsvKonvertierungStimmtFuerEckwerte() {
        assertEquals(0xFFFF0000L, hsvToArgb(0f, 1f, 1f))
        assertEquals(0xFF00FF00L, hsvToArgb(120f, 1f, 1f))
        assertEquals(0xFF0000FFL, hsvToArgb(240f, 1f, 1f))
        assertEquals(0xFFFFFFFFL, hsvToArgb(0f, 0f, 1f))
        assertEquals(0xFF000000L, hsvToArgb(0f, 0f, 0f))
    }

    @Test
    fun hsvNimmtFarbtoeneUeber360Grad() {
        assertEquals(hsvToArgb(10f, 1f, 1f), hsvToArgb(370f, 1f, 1f))
        assertEquals(hsvToArgb(10f, 1f, 1f), hsvToArgb(-350f, 1f, 1f))
    }
}
