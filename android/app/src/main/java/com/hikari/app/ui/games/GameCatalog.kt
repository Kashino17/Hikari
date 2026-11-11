package com.hikari.app.ui.games

import androidx.compose.ui.graphics.Color
import java.util.Locale

enum class GameCategory(val label: String) {
    ALL("Alle"),
    ARCADE("Arcade"),
    PUZZLE("Rätsel"),
    TACTICS("Taktik"),
}

/**
 * Die Zeile unter einem Spiel. [earned] heißt: Das ist ein erspielter Stand (Rekord, Level,
 * Siege) — sonst steht dort nur, worum es geht. [record] unterscheidet die reine Punktzahl,
 * die ausgeschrieben »Rekord 26.348« heißt, von Ständen wie »Level 12«.
 */
data class GameStat(
    val text: String,
    val earned: Boolean,
    val record: Boolean = false,
) {
    /** Ausgeschrieben, für Stellen mit Platz. */
    val spelledOut: String get() = if (record) "Rekord $text" else text
}

data class GameInfo(
    val id: String,
    val title: String,
    val tagline: String,
    val category: GameCategory,
    /** Farbe des Spiels — tönt die »Weiterspielen«-Kachel und färbt deren Start-Knopf. */
    val color: Color,
    /** Liest den Stand aus den gespeicherten Zahlen; [read] liefert 0, wo nichts steht. */
    val stat: (read: (String) -> Int) -> GameStat,
)

/** So viele Level hat die Reise in Hungry Hole (tools/hole3d/src/levels.js). */
internal const val HOLE_LEVEL_COUNT = 15

/** Punktzahl mit Tausenderpunkt: 26348 → »26.348«. */
internal fun formatScore(score: Int): String = String.format(Locale.GERMAN, "%,d", score)

private fun record(score: Int, otherwise: String): GameStat =
    if (score > 0) GameStat(formatScore(score), earned = true, record = true)
    else GameStat(otherwise, earned = false)

private fun earned(count: Int, text: String, otherwise: String): GameStat =
    if (count > 0) GameStat(text, earned = true) else GameStat(otherwise, earned = false)

/** Alle Spiele in der Reihenfolge des Rasters. Die Schlüssel gehören den einzelnen Spielen. */
val GameCatalog: List<GameInfo> = listOf(
    GameInfo(
        id = "blockblast",
        title = "Block Blast",
        tagline = "Reihen räumen auf 8×8",
        category = GameCategory.TACTICS,
        color = Color(0xFFFBBF24),
        stat = { read -> record(read("blockblast_highscore"), "8×8 Rätsel") },
    ),
    GameInfo(
        id = "fruitmerge",
        title = "Fruit Merge",
        tagline = "Früchte verschmelzen",
        category = GameCategory.TACTICS,
        color = Color(0xFF4ADE80),
        stat = { read -> record(read("fruitmerge_highscore"), "Ketten-Combos") },
    ),
    GameInfo(
        id = "spaceshooter",
        title = "Sky Strike",
        tagline = "Weltraum-Shooter",
        category = GameCategory.ARCADE,
        color = Color(0xFF22D3EE),
        stat = { read -> record(read("spaceshooter_highscore"), "5 Sektoren") },
    ),
    GameInfo(
        id = "fruithole",
        title = "Hungry Hole",
        tagline = "Die Tafel leer fressen, in 3D",
        category = GameCategory.ARCADE,
        color = Color(0xFFA78BFA),
        stat = { read ->
            // Erst zählt die Level-Reise, danach der Rekord der Zeitjagd.
            val levels = read(HOLE_LEVELS_DONE)
            val best = read(HOLE_BEST)
            when {
                levels in 1 until HOLE_LEVEL_COUNT -> GameStat("Level $levels", earned = true)
                best > 0 -> record(best, "")
                levels >= HOLE_LEVEL_COUNT -> GameStat("Alle Level", earned = true)
                else -> GameStat("$HOLE_LEVEL_COUNT Level in 3D", earned = false)
            }
        },
    ),
    GameInfo(
        id = "colorsort",
        title = "Color Sort",
        tagline = "Farben sortieren",
        category = GameCategory.PUZZLE,
        color = Color(0xFFF472B6),
        stat = { read ->
            val level = read("cs_max_level")
            earned(level - 1, "Level $level", "Level 1")
        },
    ),
    GameInfo(
        id = "2048",
        title = "2048",
        tagline = "Zahlen verschmelzen",
        category = GameCategory.PUZZLE,
        color = Color(0xFFFB923C),
        stat = { read ->
            record(maxOf(read("g2048_best_classic"), read("g2048_best_relaxed")), "3×3 bis 8×8")
        },
    ),
    GameInfo(
        id = "sudoku",
        title = "Sudoku",
        tagline = "Klassisches Zahlenrätsel",
        category = GameCategory.PUZZLE,
        color = Color(0xFF2DD4BF),
        stat = { read ->
            val solved = read("sudoku_solved_total")
            earned(solved, "$solved gelöst", "4 Stufen")
        },
    ),
    GameInfo(
        id = "snake",
        title = "Snake",
        tagline = "Der Wisch-Klassiker",
        category = GameCategory.ARCADE,
        color = Color(0xFFA3E635),
        stat = { read ->
            record(
                maxOf(read("snake_best_classic"), read("snake_best_blitz"), read("snake_best_portal")),
                "Klassik & Blitz",
            )
        },
    ),
    GameInfo(
        id = "tictactoe",
        title = "Tic-Tac-Toe",
        tagline = "Gegen die KI oder zu zweit",
        category = GameCategory.TACTICS,
        color = Color(0xFF60A5FA),
        stat = { read ->
            val wins = read("ttt_wins_total")
            earned(wins, if (wins == 1) "1 Sieg" else "$wins Siege", "1 gegen 1 & KI")
        },
    ),
)

/** Spiele einer Kategorie; [GameCategory.ALL] lässt alle durch. */
fun gamesIn(category: GameCategory): List<GameInfo> =
    if (category == GameCategory.ALL) GameCatalog else GameCatalog.filter { it.category == category }
