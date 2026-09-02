package com.hikari.app.ui.games

import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test

class TwentyFortyEightLogicTest {

    private fun board(vararg rows: IntArray): G2Board {
        var id = 1
        val tiles = ArrayList<G2Tile>()
        rows.forEachIndexed { r, row -> row.forEachIndexed { c, v -> if (v != 0) tiles += G2Tile(id++, r, c, v) } }
        return G2Board(rows.size, tiles, 0, id)
    }

    private fun grid(b: G2Board): List<List<Int>> {
        val g = MutableList(b.size) { MutableList(b.size) { 0 } }
        for (t in b.live) g[t.r][t.c] = t.v
        return g
    }

    @Test
    fun linksSchiebenVerschmilztNurEinmalProPaar() {
        val b = board(intArrayOf(2, 2, 4, 0), intArrayOf(4, 4, 4, 4), intArrayOf(0, 0, 0, 0), intArrayOf(2, 0, 0, 2))
        val m = assertNotNull(g2Move(b, G2Dir.LEFT))
        assertEquals(listOf(4, 4, 0, 0), grid(m)[0])
        assertEquals(listOf(8, 8, 0, 0), grid(m)[1])
        assertEquals(listOf(4, 0, 0, 0), grid(m)[3])
        assertEquals(4 + 16 + 4, m.score)
        // Zwei Geister pro Verschmelzung, vier Verschmelzungen
        assertEquals(8, m.tiles.count { it.ghost })
    }

    @Test
    fun keineBewegungLiefertNull() {
        val b = board(intArrayOf(2, 4, 8, 0), intArrayOf(0, 0, 0, 0), intArrayOf(0, 0, 0, 0), intArrayOf(0, 0, 0, 0))
        assertNull(g2Move(b, G2Dir.LEFT))
        assertNull(g2Move(b, G2Dir.UP))
        assertNotNull(g2Move(b, G2Dir.RIGHT))
        assertNotNull(g2Move(b, G2Dir.DOWN))
        // Volle Reihe ohne gleiche Nachbarn: kein Zug nach rechts
        val full = board(intArrayOf(2, 4, 8, 16), intArrayOf(0, 0, 0, 0), intArrayOf(0, 0, 0, 0), intArrayOf(0, 0, 0, 0))
        assertNull(g2Move(full, G2Dir.RIGHT))
    }

    @Test
    fun vollesBrettOhneNachbarnIstGameOver() {
        val full = board(intArrayOf(2, 4, 2), intArrayOf(4, 2, 4), intArrayOf(2, 4, 2))
        assertTrue(!g2CanMove(full))
        val mergeable = board(intArrayOf(2, 4, 2), intArrayOf(4, 2, 4), intArrayOf(2, 4, 4))
        assertTrue(g2CanMove(mergeable))
    }

    @Test
    fun spielstandUeberlebtSerialisierung() {
        val b = g2NewBoard(4, Random(7))
        val back = assertNotNull(g2Deserialize(4, g2Serialize(b)))
        assertEquals(grid(b), grid(back))
        assertEquals(b.score, back.score)
    }
}

class ColorSortLogicTest {

    @Test
    fun giessenNurAufGleicheFarbeOderInsLeere() {
        val t: CsTubes = listOf(listOf(0, 1), listOf(2), emptyList(), listOf(1, 1, 1, 1))
        assertEquals(0, csCanPour(t, 0, 1)) // 1 auf 2 → nein
        assertEquals(1, csCanPour(t, 0, 2)) // ins Leere → eine Schicht (nur die oberste 1)
        assertEquals(0, csCanPour(t, 0, 3)) // Ziel voll
        assertEquals(0, csCanPour(t, 2, 0)) // Quelle leer
        val after = csPour(t, 0, 2, 1)
        assertEquals(listOf(0), after[0])
        assertEquals(listOf(1), after[2])
    }

    @Test
    fun levelSindLoesbarUndLoeserpfadFuehrtZumZiel() {
        for (level in 1..14) {
            val tubes = csGenerate(csColorsForLevel(level), level * 7919L + 17)
            assertTrue(tubes.none { csComplete(it) }, "Level $level startet nicht mit fertiger Röhre")
            val path = assertNotNull(csSolve(tubes), "Level $level ist lösbar")
            var s = tubes
            for ((from, to) in path) {
                val n = csCanPour(s, from, to)
                assertTrue(n > 0, "Zug $from→$to in Level $level ist gültig")
                s = csPour(s, from, to, n)
            }
            assertTrue(csIsSolved(s), "Level $level: Lösungspfad endet sortiert")
        }
    }

    @Test
    fun zufallsraetselMitZehnFarbenIstSchnellGenug() {
        val t0 = System.currentTimeMillis()
        val tubes = csGenerate(10, 12345L)
        assertNotNull(csSolve(tubes))
        assertTrue(System.currentTimeMillis() - t0 < 8000, "Generieren + Lösen unter 8 s")
    }
}

class SudokuLogicTest {

    private fun rowsColsBoxesValid(g: IntArray): Boolean {
        for (i in 0 until 9) {
            val row = (0 until 9).map { g[i * 9 + it] }
            val col = (0 until 9).map { g[it * 9 + i] }
            val box = (0 until 9).map { g[(i / 3 * 3 + it / 3) * 9 + i % 3 * 3 + it % 3] }
            if (row.toSet().size != 9 || col.toSet().size != 9 || box.toSet().size != 9) return false
        }
        return true
    }

    @Test
    fun jedeSchwierigkeitLiefertEindeutigesRaetsel() {
        for (diff in SdDiff.entries) {
            val t0 = System.currentTimeMillis()
            val (puzzle, solution) = sdGenerate(diff, Random(42))
            val ms = System.currentTimeMillis() - t0
            assertTrue(rowsColsBoxesValid(solution), "${diff.label}: Lösung gültig")
            assertEquals(1, sdCountSolutions(puzzle.copyOf(), 2), "${diff.label}: genau eine Lösung")
            for (i in 0 until 81) if (puzzle[i] != 0) assertEquals(solution[i], puzzle[i])
            val givens = puzzle.count { it != 0 }
            assertTrue(givens <= diff.givens + 6, "${diff.label}: $givens Vorgaben (Ziel ${diff.givens})")
            assertTrue(ms < 6000, "${diff.label}: Generierung in $ms ms")
            println("${diff.label}: $givens Vorgaben, $ms ms")
        }
    }

    @Test
    fun spielstandUeberlebtSerialisierung() {
        val (puzzle, solution) = sdGenerate(SdDiff.EASY, Random(1))
        val cells = puzzle.copyOf().also { it[0] = 5 }
        val notes = IntArray(81).also { it[3] = 0b1010 }
        val s = sdSerialize(SdDiff.EASY, puzzle, solution, cells, notes, 123, 1, 2)
        val back = assertNotNull(sdDeserialize(s))
        assertEquals(SdDiff.EASY, back.diff)
        assertTrue(back.cells.contentEquals(cells))
        assertTrue(back.notes.contentEquals(notes))
        assertEquals(123, back.elapsed)
        assertEquals(1, back.mistakes)
        assertEquals(2, back.hints)
    }

    @Test
    fun peersEnthaltenZeileSpalteBlockOhneSichSelbst() {
        val p = sdPeers(40) // Mitte
        assertEquals(20, p.size)
        assertTrue(40 !in p)
        assertTrue(36 in p && 4 in p && 30 in p)
    }
}
