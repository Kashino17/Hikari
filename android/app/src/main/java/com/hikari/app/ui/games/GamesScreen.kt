package com.hikari.app.ui.games

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextMuted

data class GameInfo(
    val id: String,
    val title: String,
    val description: String,
    val icon: @Composable () -> Unit,
    val color: Color,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamesScreen(
    onBack: () -> Unit,
    onLaunchGame: (gameId: String) -> Unit,
) {
    val games = listOf(
        GameInfo(
            "blockblast", "Block Blast",
            "Blöcke aufs 8×8-Feld, Reihen abräumen, Booster zünden — jetzt mit Abenteuer-Leveln und Zeitrausch!",
            { Icon(Icons.Default.Extension, null, tint = Color(0xFFFBBF24), modifier = Modifier.size(28.dp)) },
            Color(0xFFFBBF24),
        ),
        GameInfo(
            "fruitmerge", "Fruit Merge",
            "Früchte verschmelzen bis zur 🍉 — mit Ketten-Combos, Power-ups, Zen-Modus und Herausforderungs-Leveln!",
            { Text("🍉", fontSize = 24.sp) },
            Color(0xFF4ADE80),
        ),
        GameInfo(
            "spaceshooter", "Sky Strike",
            "Weltraum-Shooter mit Bossen, Hangar-Upgrades, Boss-Rush und einer Kampagne durch 5 Sektoren!",
            { Icon(Icons.Default.RocketLaunch, null, tint = Color(0xFF22D3EE), modifier = Modifier.size(28.dp)) },
            Color(0xFF22D3EE),
        ),
        GameInfo(
            "fruithole", "Hungry Hole",
            "Dein schwarzes Loch hat Hunger: Power-ups schlucken, Bomben entschärfen — plus Rush Hour und Welten-Reise!",
            { Text("🕳️", fontSize = 24.sp) },
            Color(0xFFA78BFA),
        ),
        GameInfo(
            "tictactoe", "Tic-Tac-Toe",
            "Der Klassiker gegen KI oder zu zweit — jetzt mit Ultimate-Brett, Bolt-Modus und Best-of-Serien.",
            { Icon(Icons.Default.Close, null, tint = Color(0xFF60A5FA), modifier = Modifier.size(28.dp)) },
            Color(0xFF60A5FA),
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
            Text("5 Spiele, je 3 Modi — mit Leveln, Erfolgen und Statistiken", fontSize = 14.sp, color = HikariTextMuted,
                modifier = Modifier.padding(horizontal = 20.dp))

            Spacer(Modifier.height(20.dp))

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(games) { index, game ->
                    GameCard(
                        game = game,
                        index = index,
                        onClick = { onLaunchGame(game.id) },
                    )
                }
            }
        }
    }
}

@Composable
private fun GameCard(game: GameInfo, index: Int, onClick: () -> Unit) {
    GxAppear(index) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .padding(vertical = 4.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(lerp(HikariCardBg, game.color, 0.06f), HikariCardBg)
                    )
                )
                .border(
                    1.dp,
                    Brush.horizontalGradient(
                        listOf(game.color.copy(alpha = 0.25f), Color.White.copy(alpha = 0.05f))
                    ),
                    RoundedCornerShape(20.dp),
                )
                .gxPressable(onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(52.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(
                        Brush.radialGradient(
                            listOf(game.color.copy(alpha = 0.28f), game.color.copy(alpha = 0.10f))
                        )
                    ),
                contentAlignment = Alignment.Center,
            ) {
                game.icon()
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(game.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = HikariText)
                Spacer(Modifier.height(4.dp))
                Text(game.description, fontSize = 12.sp, color = HikariTextMuted, maxLines = 2, lineHeight = 16.sp)
            }
            Icon(Icons.Default.ChevronRight, null, tint = game.color.copy(alpha = 0.7f))
        }
    }
}
