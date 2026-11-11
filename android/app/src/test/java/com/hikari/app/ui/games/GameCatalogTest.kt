package com.hikari.app.ui.games

import com.hikari.app.domain.profile.AreaTiles
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Test

class GameCatalogTest {
    private fun stat(id: String, saved: Map<String, Int> = emptyMap()): GameStat =
        GameCatalog.first { it.id == id }.stat { saved[it] ?: 0 }

    @Test fun punktzahl_bekommtTausenderpunkt() {
        assertEquals("26.348", formatScore(26348))
        assertEquals("19", formatScore(19))
        assertEquals("1.000.000", formatScore(1_000_000))
    }

    @Test fun rekord_zaehltAlsErspielt_undWirdAusgeschrieben() {
        val s = stat("blockblast", mapOf("blockblast_highscore" to 26348))
        assertEquals(GameStat("26.348", earned = true, record = true), s)
        assertEquals("Rekord 26.348", s.spelledOut)
    }

    @Test fun ohneStand_stehtDortWorumEsGeht() {
        val s = stat("blockblast")
        assertFalse(s.earned)
        assertEquals("8×8 Rätsel", s.text)
        assertEquals("8×8 Rätsel", s.spelledOut)
    }

    @Test fun beiMehrerenSpielarten_zaehltDerBesteRekord() {
        assertEquals("4.864", stat("2048", mapOf("g2048_best_classic" to 1200, "g2048_best_relaxed" to 4864)).text)
        assertEquals("19", stat("snake", mapOf("snake_best_blitz" to 19, "snake_best_classic" to 7)).text)
    }

    @Test fun colorSort_zaehltErstAbLevelZwei() {
        assertEquals(GameStat("Level 1", earned = false), stat("colorsort"))
        assertEquals(GameStat("Level 1", earned = false), stat("colorsort", mapOf("cs_max_level" to 1)))
        assertEquals(GameStat("Level 12", earned = true), stat("colorsort", mapOf("cs_max_level" to 12)))
    }

    @Test fun siegeUndGeloeste_stehenInEinzahlUndMehrzahl() {
        assertEquals("1 Sieg", stat("tictactoe", mapOf("ttt_wins_total" to 1)).text)
        assertEquals("5 Siege", stat("tictactoe", mapOf("ttt_wins_total" to 5)).text)
        assertEquals("7 gelöst", stat("sudoku", mapOf("sudoku_solved_total" to 7)).text)
        assertFalse(stat("sudoku", mapOf("sudoku_solved_total" to 7)).record)
    }

    @Test fun hungryHole_zeigtErstDieLevelReiseDannDenRekord() {
        assertEquals(GameStat("15 Level in 3D", earned = false), stat("fruithole"))
        assertEquals(GameStat("Level 3", earned = true), stat("fruithole", mapOf(HOLE_LEVELS_DONE to 3, HOLE_BEST to 900)))
        assertEquals(
            GameStat("2.340", earned = true, record = true),
            stat("fruithole", mapOf(HOLE_LEVELS_DONE to 15, HOLE_BEST to 2340)),
        )
        assertEquals(GameStat("Alle Level", earned = true), stat("fruithole", mapOf(HOLE_LEVELS_DONE to 15)))
        // Nur Zeitjagd gespielt, noch kein Level geschafft:
        assertEquals("1.200", stat("fruithole", mapOf(HOLE_BEST to 1200)).text)
    }

    @Test fun hungryHole_kurzfassungDesSpielstands() {
        assertEquals(HoleSummary(levelsDone = 3, best = 1340), holeSummary("""{"stars":[3,1,0,2,0],"best":1340,"sound":true}"""))
        assertEquals(HoleSummary(0, 0), holeSummary("{}"))
        assertEquals(null, holeSummary("kein json"))
    }

    @Test fun kategorien_teilenAlleSpieleAuf() {
        assertEquals(GameCatalog, gamesIn(GameCategory.ALL))
        val sorted = GameCategory.entries.filter { it != GameCategory.ALL }.sumOf { gamesIn(it).size }
        assertEquals(GameCatalog.size, sorted)
        assertTrue(gamesIn(GameCategory.ARCADE).all { it.category == GameCategory.ARCADE })
    }

    @Test fun profilKachel_kenntJedesSpielUnterDemselbenNamen() {
        assertEquals(GameCatalog.size, AreaTiles.GAME_COUNT)
        assertEquals(GameCatalog.size, GameCatalog.map { it.id }.distinct().size)
        for (game in GameCatalog) assertEquals(game.title, AreaTiles.gameTitle(game.id))
    }
}
