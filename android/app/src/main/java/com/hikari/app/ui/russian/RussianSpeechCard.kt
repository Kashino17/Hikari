package com.hikari.app.ui.russian

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.domain.russian.RuAnswerCheck

private val Good = Color(0xFF6EE7B7)
private val Amber = Color(0xFFF59E0B)

/**
 * Einheitliche Aussprache-Auswertung (Feed, Lektion, Intro): Prozentwert,
 * jedes Zielwort als grüner/roter Chip und was die KI tatsächlich gehört hat.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RuSpeechScoreCard(
    sc: RuAnswerCheck.SpeechScore,
    modifier: Modifier = Modifier,
    xpHint: String? = null,
    large: Boolean = false,
) {
    val pct = (sc.score * 100).toInt()
    val title = if (large) 14.sp else 11.5.sp
    val chip = if (large) 16.sp else 11.5.sp
    val small = if (large) 12.5.sp else 11.sp
    val missed = sc.words.count { !it.second }
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (sc.passed) Color(0xFF064E3B).copy(alpha = 0.5f) else Color(0xFF78350F).copy(alpha = 0.5f))
            .border(
                BorderStroke(0.5.dp, if (sc.passed) Color(0xFF10B981) else Amber),
                RoundedCornerShape(10.dp),
            )
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (sc.passed) Icons.Outlined.CheckCircle else Icons.Outlined.Replay,
                null,
                tint = if (sc.passed) Good else Amber,
                modifier = Modifier.size(if (large) 18.dp else 14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                when {
                    sc.passed -> "$pct % · Exzellente Aussprache" + (xpHint?.let { " ($it)" } ?: "")
                    sc.heard.isBlank() -> "Nichts verstanden — nochmal, etwas lauter"
                    else -> "$pct % · Fast geschafft — nochmal versuchen"
                },
                color = if (sc.passed) Good else Amber,
                fontSize = title,
                fontWeight = FontWeight.Bold,
            )
        }
        Spacer(Modifier.height(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            sc.words.forEach { (word, ok) ->
                Box(
                    Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (ok) Color(0xFF065F46) else Color(0xFF991B1B).copy(alpha = 0.8f))
                        .border(0.5.dp, if (ok) Color(0xFF34D399) else Color(0xFFF87171), RoundedCornerShape(6.dp))
                        .padding(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(word, color = Color.White, fontSize = chip, fontWeight = FontWeight.SemiBold)
                }
            }
        }
        if (!sc.passed && missed > 0 && sc.heard.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                if (missed == 1) "Rot: dieses Wort fehlt oder klang anders" else "Rot: diese Wörter fehlen oder klangen anders",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = small,
            )
        }
        if (sc.heard.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text("Gehört: »${sc.heard}«", color = Color.White.copy(alpha = 0.85f), fontSize = small)
        }
    }
}
