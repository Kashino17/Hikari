package com.hikari.app.ui.games

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariPrimary
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextMuted

data class GameInfo(
    val id: String,
    val title: String,
    val description: String,
    val icon: @Composable () -> Unit,
    val color: Color,
    val launch: @Composable (onBack: () -> Unit) -> Unit,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamesScreen(
    onBack: () -> Unit,
    onLaunchGame: (gameId: String) -> Unit,
) {
    val games = listOf(
        GameInfo(
            "neon-catch", "Neon Catch",
            "Fange fallende Kristalle mit dem Korb. Grün = Punkte, Rot = Gefahr!",
            { Icon(Icons.Default.Diamond, null, tint = Color(0xFF4ADE80), modifier = Modifier.size(28.dp)) },
            Color(0xFF4ADE80),
            { onBack -> NeonCatchGame(onBack) },
        ),
        GameInfo(
            "memory-grid", "Memory Grid",
            "Finde die passenden Farbpaare auf dem 4x3 Spielfeld!",
            { Icon(Icons.Default.GridOn, null, tint = Color(0xFF60A5FA), modifier = Modifier.size(28.dp)) },
            Color(0xFF60A5FA),
            { onBack -> MemoryGridGame(onBack) },
        ),
        GameInfo(
            "reflex-tap", "Reflex Tap",
            "Tippe die Kreise so schnell wie möglich. Combo-System für Bonuspunkte!",
            { Icon(Icons.Default.Fingerprint, null, tint = Color(0xFFFF8A65), modifier = Modifier.size(28.dp)) },
            Color(0xFFFF8A65),
            { onBack -> ReflexTapGame(onBack) },
        ),
        GameInfo(
            "word-chain", "Wortkette",
            "Bilde Wörter aus den Buchstaben im Raster. Mindestens 2 Buchstaben!",
            { Icon(Icons.Default.TextFields, null, tint = Color(0xFFA78BFA), modifier = Modifier.size(28.dp)) },
            Color(0xFFA78BFA),
            { onBack -> WordChainGame(onBack) },
        ),
        GameInfo(
            "orbit", "Orbit",
            "Weiche Asteroiden auf der Umlaufbahn aus. Links/rechts tippen zum Drehen!",
            { Icon(Icons.Default.Circle, null, tint = Color(0xFFFBBF24), modifier = Modifier.size(28.dp)) },
            Color(0xFFFBBF24),
            { onBack -> OrbitGame(onBack) },
        ),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Hikari Spiele", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Default.ArrowBack, "Zurück")
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
        Column(Modifier.fillMaxSize().padding(padding)) {
            Spacer(Modifier.height(24.dp))
            Text("Mini-Spiele", fontWeight = FontWeight.Bold, fontSize = 24.sp, color = HikariText,
                modifier = Modifier.padding(horizontal = 20.dp))
            Spacer(Modifier.height(4.dp))
            Text("5 kleine Spiele für zwischendurch", fontSize = 14.sp, color = HikariTextMuted,
                modifier = Modifier.padding(horizontal = 20.dp))

            Spacer(Modifier.height(20.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(games) { game ->
                    GameCard(
                        game = game,
                        onClick = { onLaunchGame(game.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GameCard(game: GameInfo, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        color = HikariCardBg,
        shape = RoundedCornerShape(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(52.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(game.color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                game.icon()
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(game.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = HikariText)
                Spacer(Modifier.height(4.dp))
                Text(game.description, fontSize = 12.sp, color = HikariTextMuted,
                    textAlign = TextAlign.Justify, maxLines = 2)
            }
            Icon(Icons.Default.ChevronRight, null, tint = HikariTextMuted)
        }
    }
}
