package com.hikari.app.ui.russian

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.domain.russian.RuProsody
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextMuted
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Legt Tonhöhenverlauf (Melodie) von Original und eigener Aufnahme übereinander
 * und nennt Melodie-, Rhythmus- und Tempo-Befund. [takeStamp] wechselt mit jeder
 * neuen Aufnahme (z. B. File.lastModified), damit neu gerechnet wird.
 */
@Composable
internal fun RuProsodyCard(refClip: String, take: File, takeStamp: Long, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var result by remember(refClip, takeStamp) { mutableStateOf<RuProsody.Comparison?>(null) }
    var failed by remember(refClip, takeStamp) { mutableStateOf(false) }

    LaunchedEffect(refClip, takeStamp) {
        val cmp = withContext(Dispatchers.Default) {
            val ref = RussianAudioDecoder.decodeAsset(context, refClip)
            val mine = RussianAudioDecoder.decodeFile(take)
            if (ref == null || mine == null) null
            else RuProsody.compare(RuProsody.analyze(ref), RuProsody.analyze(mine))
        }
        result = cmp
        failed = cmp == null
    }

    val c = result
    Column(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(HikariCardBg)
            .padding(12.dp),
    ) {
        if (c == null) {
            Text(
                if (failed) "Melodie konnte nicht ausgewertet werden — sprich etwas lauter und länger." else "Melodie wird verglichen …",
                color = HikariTextMuted,
                fontSize = 12.5.sp,
            )
            return@Column
        }
        Canvas(Modifier.fillMaxWidth().height(84.dp)) {
            val all = (c.refCurve + c.userCurve)
            val lo = all.minOrNull() ?: -1f
            val hi = (all.maxOrNull() ?: 1f).coerceAtLeast(lo + 2f)
            fun path(curve: FloatArray, color: Color, w: Float) {
                for (i in 0 until curve.size - 1) {
                    val x0 = size.width * i / (curve.size - 1)
                    val x1 = size.width * (i + 1) / (curve.size - 1)
                    val y0 = size.height * (1 - (curve[i] - lo) / (hi - lo))
                    val y1 = size.height * (1 - (curve[i + 1] - lo) / (hi - lo))
                    drawLine(color, Offset(x0, y0), Offset(x1, y1), strokeWidth = w, cap = StrokeCap.Round)
                }
            }
            path(c.refCurve, HikariAmber, 5f)
            path(c.userCurve, Color.White, 5f)
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Legend(HikariAmber, "Original")
            Legend(Color.White, "Du")
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Melodie ${c.melody} %  ·  Rhythmus ${c.rhythm} %  ·  ${RuProsody.tempoText(c.tempoRatio)}",
            color = HikariText,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(2.dp))
        Text(advice(c), color = HikariTextMuted, fontSize = 12.5.sp)
    }
}

@Composable
private fun Legend(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Box(Modifier.width(14.dp).height(3.dp).background(color))
        Spacer(Modifier.width(6.dp))
        Text(label, color = HikariTextMuted, fontSize = 11.5.sp)
    }
}

private fun advice(c: RuProsody.Comparison): String = when {
    c.melody >= 75 && c.rhythm >= 70 -> "Sehr nah am Original — Satzmelodie und Betonung sitzen."
    c.melody < 50 -> "Die Melodie weicht ab: Folge dem Verlauf der amberfarbenen Linie — wo sie steigt, geht die Stimme hoch, am Satzende meist runter."
    c.rhythm < 50 -> "Der Rhythmus weicht ab: Betone die lauten Silben des Originals stärker und sprich die schwachen kurz und leise."
    else -> "Schon ähnlich. Hör das Original nochmal und achte auf die Stellen, an denen die Linien auseinanderlaufen."
}
