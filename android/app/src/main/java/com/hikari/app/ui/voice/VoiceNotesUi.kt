package com.hikari.app.ui.voice

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hikari.app.data.api.dto.VoiceJobListItemDto
import com.hikari.app.data.api.dto.VoiceStatusDto
import com.hikari.app.domain.voice.VoiceFormat
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariBorder
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariDanger
import com.hikari.app.ui.theme.HikariSurface
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Eigene Bereichsfarbe für Sprachnachrichten (KI/Sprache), neben WhatsApp-Grün. */
val VoiceViolet = Color(0xFFA78BFA)
val VoiceVioletSoft = Color(0x26A78BFA)
val VoiceSky = Color(0xFF60A5FA)
val VoiceSkySoft = Color(0x2660A5FA)

internal fun voiceDate(ms: Long): String {
    val now = Calendar.getInstance()
    val then = Calendar.getInstance().apply { timeInMillis = ms }
    val time = SimpleDateFormat("HH:mm", Locale.GERMAN).format(Date(ms))
    val sameYear = now.get(Calendar.YEAR) == then.get(Calendar.YEAR)
    val dayDiff = now.get(Calendar.DAY_OF_YEAR) - then.get(Calendar.DAY_OF_YEAR)
    return when {
        sameYear && dayDiff == 0 -> "Heute, $time"
        sameYear && dayDiff == 1 -> "Gestern, $time"
        else -> SimpleDateFormat("EEE, d. MMM, HH:mm", Locale.GERMAN).format(Date(ms))
    }
}

/** Inhalt des Tabs "Sprachnachrichten" in der WhatsApp-Ecke. */
@Composable
fun VoiceNotesTab(
    onOpenJob: (String) -> Unit,
    vm: VoiceNotesViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by vm.list.collectAsState()
    val message by vm.message.collectAsState()

    LaunchedEffect(Unit) { vm.pollList() }
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.consumeMessage()
        }
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        vm.uploadPicked(uris)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            HowToCard(onPick = { picker.launch(arrayOf("audio/*", "application/ogg")) })
        }
        item {
            ModelStatusRow(status = state.status, offline = state.offline, onUnload = vm::unloadModels)
        }
        if (state.loaded && state.jobs.isEmpty() && !state.offline) {
            item { EmptyHint() }
        }
        items(state.jobs, key = { it.id }) { job ->
            VoiceJobCard(job = job, onClick = { onOpenJob(job.id) })
        }
    }
}

@Composable
private fun HowToCard(onPick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(VoiceVioletSoft)
            .border(0.5.dp, VoiceViolet.copy(alpha = 0.35f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.GraphicEq, contentDescription = null, tint = VoiceViolet, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Sprachnachrichten zusammenfassen", color = HikariText, fontWeight = FontWeight.Bold, fontSize = 15.sp)
        }
        Step(1, "In WhatsApp eine Sprachnachricht lange drücken, weitere antippen")
        Step(2, "Teilen und \"Hikari: zusammenfassen\" wählen")
        Step(3, "Die Zusammenfassung erscheint als Benachrichtigung, du bleibst im Chat")
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(VoiceViolet.copy(alpha = 0.18f))
                .clickable(onClick = onPick)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.AudioFile, contentDescription = null, tint = VoiceViolet, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Audiodatei auswählen", color = VoiceViolet, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Step(n: Int, text: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(VoiceViolet.copy(alpha = 0.22f)),
            contentAlignment = Alignment.Center,
        ) {
            Text("$n", color = VoiceViolet, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(10.dp))
        Text(text, color = HikariTextMuted, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun ModelStatusRow(status: VoiceStatusDto?, offline: Boolean, onUnload: () -> Unit) {
    if (offline) {
        Text(
            "Hikari-Server nicht erreichbar",
            color = HikariDanger,
            fontSize = 12.sp,
            modifier = Modifier.padding(horizontal = 4.dp),
        )
        return
    }
    if (status == null) return
    val anyLoaded = status.whisper.loaded || status.llm.loaded == true
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Default.Memory, contentDescription = null, tint = HikariTextFaint, modifier = Modifier.size(15.dp))
        ModelPill("Whisper", status.whisper.loaded)
        ModelPill(
            "Qwen",
            status.llm.loaded == true,
            unknown = !status.llm.reachable,
        )
        Spacer(Modifier.weight(1f))
        if (anyLoaded && !status.busy) {
            Text(
                "Jetzt freigeben",
                color = VoiceViolet,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onUnload)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        } else if (!anyLoaded) {
            Text("Speicher frei", color = HikariTextFaint, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ModelPill(name: String, loaded: Boolean, unknown: Boolean = false) {
    val color = when {
        unknown -> HikariTextFaint
        loaded -> VoiceViolet
        else -> HikariTextMuted
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (loaded) VoiceVioletSoft else HikariSurface)
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(5.dp))
        Text(
            when {
                unknown -> "$name: LM Studio aus"
                loaded -> "$name geladen"
                else -> "$name entladen"
            },
            color = color,
            fontSize = 11.5.sp,
        )
    }
}

@Composable
private fun EmptyHint() {
    Text(
        "Noch keine Sprachnachrichten. Teile eine aus WhatsApp, dann erscheint sie hier.",
        color = HikariTextFaint,
        fontSize = 13.sp,
        modifier = Modifier.padding(vertical = 24.dp, horizontal = 8.dp),
    )
}

@Composable
private fun VoiceJobCard(job: VoiceJobListItemDto, onClick: () -> Unit) {
    val running = !VoiceFormat.isFinished(job.status)
    val failed = job.status == "failed"
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HikariCardBg)
            .border(0.5.dp, if (running) VoiceViolet.copy(alpha = 0.4f) else HikariBorder, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp)
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = job.title ?: if (failed) "Nicht zusammengefasst" else "Wird verarbeitet",
                color = if (failed) HikariDanger else HikariText,
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (job.todoCount > 0 && !running) {
                Badge("${job.todoCount} ${if (job.todoCount == 1) "Aufgabe" else "Aufgaben"}", HikariAmber)
                Spacer(Modifier.width(6.dp))
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = HikariTextFaint, modifier = Modifier.size(18.dp))
        }
        Text(
            buildString {
                append(voiceDate(job.createdAt))
                append(" · ")
                append(VoiceFormat.meta(job.total, job.duration))
                if (!job.label.isNullOrBlank()) append(" · ").append(job.label)
            },
            color = HikariTextFaint,
            fontSize = 12.sp,
        )
        when {
            running -> {
                Text(
                    VoiceFormat.statusLine(job.status, job.done, job.total),
                    color = VoiceViolet,
                    fontSize = 12.5.sp,
                )
                LinearProgressIndicator(
                    progress = { VoiceFormat.progress(job.status, job.done, job.total) },
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                    color = VoiceViolet,
                    trackColor = HikariSurfaceHigh,
                )
            }
            failed -> Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = HikariDanger, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(4.dp))
                Text(VoiceFormat.errorText(job.error), color = HikariTextMuted, fontSize = 12.5.sp, maxLines = 2)
            }
            !job.preview.isNullOrBlank() -> Text(
                job.preview,
                color = HikariTextMuted,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun Badge(text: String, color: Color, icon: ImageVector? = null) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.16f))
            .padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(3.dp))
        }
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}
