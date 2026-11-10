package com.hikari.app.ui.games

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.EmojiEvents
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.ui.common.TextTab
import com.hikari.app.ui.common.TextTabs
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariSurface
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted

/** Dunkle Symbole auf voll gefärbten Flächen. */
private val Ink = Color(0xFF17171A)

/**
 * Spiele-Übersicht: oben das zuletzt gespielte Spiel zum Weiterspielen, darunter alle Spiele
 * als App-Raster in drei Spalten. Unter jedem Symbol steht der erspielte Stand — wo es noch
 * keinen gibt, worum es geht.
 */
@Composable
fun GamesScreen(
    onBack: () -> Unit,
    onLaunchGame: (gameId: String) -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("hikari_games", Context.MODE_PRIVATE) }
    val read: (String) -> Int = remember { { key -> prefs.getInt(key, 0) } }
    var category by remember { mutableStateOf(GameCategory.ALL) }

    // Nach einem Spiel wird die Seite neu aufgebaut — Stände und »zuletzt gespielt« sind dann frisch.
    val lastPlayed = remember {
        prefs.getString("last_played_game", null)?.let { id -> GameCatalog.firstOrNull { it.id == id } }
    }
    val games = remember(category) { gamesIn(category) }

    fun launch(gameId: String) {
        prefs.edit()
            .putString("last_played_game", gameId)
            .putLong("last_played_time", System.currentTimeMillis())
            .apply()
        onLaunchGame(gameId)
    }

    Column(Modifier.fillMaxSize().background(HikariBg)) {
        GamesTopBar(onBack)

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 96.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(22.dp),
        ) {
            if (lastPlayed != null) {
                item(key = "continue", span = { GridItemSpan(maxLineSpan) }) {
                    ContinueTile(lastPlayed, lastPlayed.stat(read)) { launch(lastPlayed.id) }
                }
            }

            item(key = "tabs", span = { GridItemSpan(maxLineSpan) }) {
                TextTabs(
                    tabs = GameCategory.entries.map { cat ->
                        TextTab(cat.label, if (cat == GameCategory.ALL) null else gamesIn(cat).size)
                    },
                    selected = category.ordinal,
                    onSelect = { category = GameCategory.entries[it] },
                )
            }

            itemsIndexed(games, key = { _, g -> g.id }) { index, game ->
                GameGridCell(game, index, game.stat(read)) { launch(game.id) }
            }
        }
    }
}

@Composable
private fun GamesTopBar(onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.ArrowBack,
                contentDescription = "Zurück",
                tint = HikariText,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(4.dp))
        Column {
            Text("Spiele", color = HikariText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Text(
                "${GameCatalog.size} Spiele, alle ohne Netz spielbar",
                color = HikariTextMuted,
                fontSize = 12.5.sp,
                maxLines = 1,
            )
        }
    }
}

/** Das zuletzt gespielte Spiel, groß und in seiner Farbe: ein Tipp, und es geht weiter. */
@Composable
private fun ContinueTile(game: GameInfo, stat: GameStat, onClick: () -> Unit) {
    val hue = game.color
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .gxPressable(onClick = onClick)
            .clip(shape)
            .background(lerp(HikariSurface, hue, 0.13f))
            .border(1.dp, hue.copy(alpha = 0.24f), shape)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GameAppIcon(game.id, size = 62.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Weiterspielen", color = hue, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
            Text(
                game.title,
                color = HikariText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(1.dp))
            StatLine(
                text = if (stat.earned) stat.spelledOut else game.tagline,
                earned = stat.earned,
                fontSize = 12.5,
                restColor = HikariTextMuted,
            )
        }
        Spacer(Modifier.width(10.dp))
        Box(
            modifier = Modifier.size(42.dp).clip(CircleShape).background(hue),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.PlayArrow, contentDescription = null, tint = Ink, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun GameGridCell(game: GameInfo, index: Int, stat: GameStat, onClick: () -> Unit) {
    GxAppear(index) {
        Column(
            modifier = Modifier.fillMaxWidth().gxPressable(onClick = onClick),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            GameAppIcon(game.id, size = 68.dp)
            Spacer(Modifier.height(9.dp))
            Text(
                game.title,
                color = HikariText,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            StatLine(stat.text, stat.earned, fontSize = 11.0, restColor = HikariTextFaint)
        }
    }
}

/** Erspieltes trägt den Amber-Pokal, alles andere bleibt leise. */
@Composable
private fun StatLine(text: String, earned: Boolean, fontSize: Double, restColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (earned) {
            Icon(
                Icons.Outlined.EmojiEvents,
                contentDescription = null,
                tint = HikariAmber,
                modifier = Modifier.size((fontSize + 1.5).dp),
            )
            Spacer(Modifier.width(3.dp))
        }
        Text(
            text,
            color = if (earned) HikariTextMuted else restColor,
            fontSize = fontSize.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
