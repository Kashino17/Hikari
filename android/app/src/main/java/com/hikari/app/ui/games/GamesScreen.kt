package com.hikari.app.ui.games

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Grid3x3
import androidx.compose.material.icons.filled.Grid4x4
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timeline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariAmberSoft
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariBorder
import com.hikari.app.ui.theme.HikariBorderStrong
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariSurface
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted

enum class GameCategory(val label: String, val icon: String) {
    ALL("Alle", "🎮"),
    ARCADE("Arcade & Action", "⚡"),
    PUZZLE("Rätsel & Denksport", "🧠"),
    TACTICS("Taktik & Klassiker", "🎯"),
}

data class GameInfo(
    val id: String,
    val title: String,
    val tagline: String,
    val description: String,
    val category: GameCategory,
    val icon: @Composable () -> Unit,
    val color: Color,
    val getStat: (SharedPreferences) -> String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamesScreen(
    onBack: () -> Unit,
    onLaunchGame: (gameId: String) -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("hikari_games", Context.MODE_PRIVATE) }
    var selectedCategory by remember { mutableStateOf(GameCategory.ALL) }

    val allGames = remember {
        listOf(
            GameInfo(
                id = "blockblast",
                title = "Block Blast",
                tagline = "8×8 Reihen räumen",
                description = "Blöcke platzieren, Zeilen & Spalten sprengen, Booster zünden — mit Abenteuer-Leveln und Zeitrausch.",
                category = GameCategory.TACTICS,
                icon = { Icon(Icons.Default.Extension, null, tint = Color(0xFFFBBF24), modifier = Modifier.size(28.dp)) },
                color = Color(0xFFFBBF24),
                getStat = { p ->
                    val hs = p.getInt("blockblast_highscore", 0)
                    if (hs > 0) "Rekord: $hs" else "8×8 Rätsel"
                },
            ),
            GameInfo(
                id = "fruitmerge",
                title = "Fruit Merge",
                tagline = "Suika Wassermelone",
                description = "Früchte fallen lassen & verschmelzen — mit Ketten-Combos, Power-ups, Zen-Modus und Herausforderungen.",
                category = GameCategory.TACTICS,
                icon = { Text("🍉", fontSize = 24.sp) },
                color = Color(0xFF4ADE80),
                getStat = { p ->
                    val hs = p.getInt("fruitmerge_highscore", 0)
                    if (hs > 0) "Rekord: $hs" else "Ketten-Combos"
                },
            ),
            GameInfo(
                id = "spaceshooter",
                title = "Sky Strike",
                tagline = "Weltraum-Shooter",
                description = "Kämpfe dich durch 5 galaktische Sektoren mit gewaltigen Bossen, Hangar-Upgrades und Boss-Rush.",
                category = GameCategory.ARCADE,
                icon = { Icon(Icons.Default.RocketLaunch, null, tint = Color(0xFF22D3EE), modifier = Modifier.size(28.dp)) },
                color = Color(0xFF22D3EE),
                getStat = { p ->
                    val hs = p.getInt("spaceshooter_highscore", 0)
                    if (hs > 0) "Rekord: $hs" else "5 Sektoren"
                },
            ),
            GameInfo(
                id = "fruithole",
                title = "Hungry Hole",
                tagline = "Black Hole Arcade",
                description = "Schlucke Power-ups, weiche Bomben aus und fresse die Stadt leer — plus Rush Hour & Welten-Reise.",
                category = GameCategory.ARCADE,
                icon = { Text("🕳️", fontSize = 24.sp) },
                color = Color(0xFFA78BFA),
                getStat = { p ->
                    val hs = p.getInt("fruithole_highscore", 0)
                    if (hs > 0) "Rekord: $hs" else "Rush-Modus"
                },
            ),
            GameInfo(
                id = "colorsort",
                title = "Color Sort",
                tagline = "Wasser-Farbensortieren",
                description = "Farbschichten in Reagenzgläsern sortieren — Level-Reise mit 3 bis 11 Farben und Zufallsrätsel.",
                category = GameCategory.PUZZLE,
                icon = { Icon(Icons.Default.Science, null, tint = Color(0xFFF472B6), modifier = Modifier.size(28.dp)) },
                color = Color(0xFFF472B6),
                getStat = { p ->
                    val lv = p.getInt("cs_max_level", 1)
                    if (lv > 1) "Level $lv erreicht" else "Level 1 Start"
                },
            ),
            GameInfo(
                id = "2048",
                title = "2048",
                tagline = "Zahlen-Verschmelzen",
                description = "Kacheln wischen und addieren — von 3×3 bis 5×5 oder entspannt auf dem 8×8-Riesenbrett mit Rückgängig.",
                category = GameCategory.PUZZLE,
                icon = { Icon(Icons.Default.Grid4x4, null, tint = Color(0xFFFB923C), modifier = Modifier.size(28.dp)) },
                color = Color(0xFFFB923C),
                getStat = { p ->
                    val b = maxOf(p.getInt("g2048_best_classic", 0), p.getInt("g2048_best_relaxed", 0))
                    if (b > 0) "Rekord: $b" else "3×3 bis 8×8"
                },
            ),
            GameInfo(
                id = "sudoku",
                title = "Sudoku",
                tagline = "Klassisches Zahlenrätsel",
                description = "Vier Schwierigkeiten mit garantierten eindeutigen Lösungen, Notizen, Hinweisen, Fehlergrenze & Timer.",
                category = GameCategory.PUZZLE,
                icon = { Icon(Icons.Default.Grid3x3, null, tint = Color(0xFF2DD4BF), modifier = Modifier.size(28.dp)) },
                color = Color(0xFF2DD4BF),
                getStat = { p ->
                    val solved = p.getInt("sudoku_solved_total", 0)
                    if (solved > 0) "$solved gelöst" else "4 Stufen"
                },
            ),
            GameInfo(
                id = "snake",
                title = "Snake",
                tagline = "Retro-Wischklassiker",
                description = "Kultiges Gameplay mit präziser Wischsteuerung, Bonus-Früchten, Portal-Rändern und rasantem Blitz-Modus.",
                category = GameCategory.ARCADE,
                icon = { Icon(Icons.Default.Timeline, null, tint = Color(0xFFA3E635), modifier = Modifier.size(28.dp)) },
                color = Color(0xFFA3E635),
                getStat = { p ->
                    val b = maxOf(
                        p.getInt("snake_best_classic", 0),
                        p.getInt("snake_best_blitz", 0),
                        p.getInt("snake_best_portal", 0),
                    )
                    if (b > 0) "Rekord: $b" else "Klassik & Blitz"
                },
            ),
            GameInfo(
                id = "tictactoe",
                title = "Tic-Tac-Toe",
                tagline = "Klassik & Ultimate",
                description = "Gegen die smarte KI oder zu zweit an einem Gerät — mit 3×3, Ultimate-Großbrett und Bolt-Modus.",
                category = GameCategory.TACTICS,
                icon = { Icon(Icons.Default.Close, null, tint = Color(0xFF60A5FA), modifier = Modifier.size(28.dp)) },
                color = Color(0xFF60A5FA),
                getStat = { p ->
                    val w = p.getInt("ttt_wins_total", 0)
                    val g = p.getInt("ttt_games_total", 0)
                    if (w > 0) "$w Siege ($g Runden)" else "1 vs 1 & KI"
                },
            ),
        )
    }

    fun handleLaunch(gameId: String) {
        prefs.edit()
            .putString("last_played_game", gameId)
            .putLong("last_played_time", System.currentTimeMillis())
            .apply()
        onLaunchGame(gameId)
    }

    val lastPlayedId = remember { prefs.getString("last_played_game", null) }
    val lastPlayedGame = remember(lastPlayedId) {
        allGames.firstOrNull { it.id == lastPlayedId } ?: allGames.first()
    }

    val filteredGames = remember(selectedCategory) {
        if (selectedCategory == GameCategory.ALL) allGames
        else allGames.filter { it.category == selectedCategory }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "HIKARI SPIELE",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            letterSpacing = 1.2.sp,
                            fontFamily = FontFamily.Monospace,
                            color = HikariText,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = HikariText)
                    }
                },
                actions = {
                    Box(
                        modifier = Modifier
                            .padding(end = 16.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(HikariSurfaceHigh)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            "9 OFFLINE",
                            color = HikariAmber,
                            fontSize = 10.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = HikariBg,
                    titleContentColor = HikariText,
                ),
            )
        },
        containerColor = HikariBg,
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // ── 1. Hero Spotlight: Zuletzt gespielt / Highlight ──────────────
            item {
                QuickResumeBanner(
                    game = lastPlayedGame,
                    isResumed = lastPlayedId != null,
                    statText = lastPlayedGame.getStat(prefs),
                    onPlay = { handleLaunch(lastPlayedGame.id) },
                )
            }

            // ── 2. Feature-Badges: 100% Offline, Keine Werbung ───────────────
            item {
                ArcadeFeaturesRow()
            }

            // ── 3. Filter-Chips für Kategorien ───────────────────────────────
            item {
                CategoriesRow(
                    selected = selectedCategory,
                    onSelect = { selectedCategory = it },
                )
            }

            // ── 4. Liste der Spiele nach Kategorie ───────────────────────────
            itemsIndexed(filteredGames, key = { _, g -> g.id }) { index, game ->
                ModernGameCard(
                    game = game,
                    index = index,
                    statText = game.getStat(prefs),
                    onClick = { handleLaunch(game.id) },
                )
            }
        }
    }
}

@Composable
private fun QuickResumeBanner(
    game: GameInfo,
    isResumed: Boolean,
    statText: String,
    onPlay: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(
                    listOf(
                        lerp(HikariCardBg, game.color, 0.16f),
                        HikariCardBg,
                    ),
                ),
            )
            .border(
                1.dp,
                Brush.horizontalGradient(
                    listOf(game.color.copy(alpha = 0.45f), HikariBorderStrong),
                ),
                RoundedCornerShape(22.dp),
            )
            .clickable { onPlay() }
            .padding(18.dp),
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                // Label Pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(game.color.copy(alpha = 0.16f))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(
                        if (isResumed) "ZULETZT GESPIELT" else "HIGHLIGHT TIPP",
                        color = game.color,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                }

                // Stat Badge
                Text(
                    statText,
                    color = HikariTextMuted,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Medium,
                )
            }

            Spacer(Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Glowing Icon
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            Brush.radialGradient(
                                listOf(game.color.copy(alpha = 0.35f), game.color.copy(alpha = 0.12f)),
                            ),
                        )
                        .border(1.dp, game.color.copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    game.icon()
                }

                Spacer(Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        game.title,
                        color = HikariText,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        game.tagline,
                        color = HikariTextMuted,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Spacer(Modifier.width(10.dp))

                // Play Button CTA
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(game.color)
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            if (isResumed) "Weiter" else "Starten",
                            color = Color.Black,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ArcadeFeaturesRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FeatureTile(title = "9 Spiele", subtitle = "100% Offline", modifier = Modifier.weight(1f))
        FeatureTile(title = "Highscores", subtitle = "Lokal gesichert", modifier = Modifier.weight(1f))
        FeatureTile(title = "Zero Ads", subtitle = "Purer Spielspaß", modifier = Modifier.weight(1f))
    }
}

@Composable
private fun FeatureTile(title: String, subtitle: String, modifier: Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(HikariSurfaceHigh.copy(alpha = 0.5f))
            .border(0.5.dp, HikariBorder, RoundedCornerShape(14.dp))
            .padding(vertical = 10.dp, horizontal = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                title,
                color = HikariText,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                subtitle,
                color = HikariTextFaint,
                fontSize = 10.sp,
            )
        }
    }
}

@Composable
private fun CategoriesRow(
    selected: GameCategory,
    onSelect: (GameCategory) -> Unit,
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GameCategory.entries.forEach { cat ->
            val isSelected = selected == cat
            val bgColor by animateColorAsState(
                targetValue = if (isSelected) HikariSurfaceHigh else HikariCardBg,
                animationSpec = tween(180),
                label = "cat-bg",
            )
            val borderColor by animateColorAsState(
                targetValue = if (isSelected) HikariAmber else HikariBorder,
                animationSpec = tween(180),
                label = "cat-border",
            )

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(bgColor)
                    .border(if (isSelected) 1.2.dp else 0.5.dp, borderColor, RoundedCornerShape(12.dp))
                    .clickable { onSelect(cat) }
                    .padding(horizontal = 12.dp, vertical = 7.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Text(cat.icon, fontSize = 13.sp)
                Text(
                    cat.label,
                    color = if (isSelected) HikariText else HikariTextFaint,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun ModernGameCard(
    game: GameInfo,
    index: Int,
    statText: String,
    onClick: () -> Unit,
) {
    GxAppear(index) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(lerp(HikariCardBg, game.color, 0.08f), HikariCardBg),
                    ),
                )
                .border(
                    1.dp,
                    Brush.horizontalGradient(
                        listOf(game.color.copy(alpha = 0.32f), Color.White.copy(alpha = 0.04f)),
                    ),
                    RoundedCornerShape(20.dp),
                )
                .gxPressable(onClick = onClick)
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Radial Icon Container
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.radialGradient(
                            listOf(game.color.copy(alpha = 0.32f), game.color.copy(alpha = 0.10f)),
                        ),
                    )
                    .border(0.5.dp, game.color.copy(alpha = 0.25f), RoundedCornerShape(16.dp)),
                contentAlignment = Alignment.Center,
            ) {
                game.icon()
            }

            Spacer(Modifier.width(14.dp))

            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        game.title,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        color = HikariText,
                    )
                    // Live Stat Pill
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(game.color.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 1.5.dp),
                    ) {
                        Text(
                            statText,
                            color = game.color,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    game.description,
                    fontSize = 11.5.sp,
                    color = HikariTextMuted,
                    maxLines = 2,
                    lineHeight = 15.sp,
                )
            }

            Spacer(Modifier.width(8.dp))

            // Launch Chevron Pill
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(HikariSurfaceHigh)
                    .border(0.5.dp, HikariBorder, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = game.color,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
