package com.hikari.app.ui.voice

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import com.hikari.app.data.api.dto.VoiceClipDto
import com.hikari.app.data.api.dto.VoiceJobDto
import com.hikari.app.domain.voice.VoiceFormat
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariBorder
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariDanger
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted

@Composable
fun VoiceNoteDetailScreen(
    jobId: String,
    onBack: () -> Unit,
    vm: VoiceNotesViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by vm.detail.collectAsState()
    val message by vm.message.collectAsState()
    var confirmDelete by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(jobId) { vm.pollDetail(jobId) }
    LaunchedEffect(message) {
        message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            vm.consumeMessage()
        }
    }

    val job = state.job
    val summaryText = job?.summary?.let { VoiceFormat.asText(it, job.label) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HikariBg)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconAction(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", HikariText, onBack)
            Text(
                "Sprachnachrichten",
                color = HikariTextMuted,
                fontSize = 14.sp,
                modifier = Modifier.weight(1f).padding(start = 4.dp),
            )
            if (summaryText != null) {
                IconAction(Icons.Default.ContentCopy, "Kopieren", HikariTextMuted) {
                    copy(context, summaryText)
                }
                IconAction(Icons.Default.Share, "Teilen", HikariTextMuted) {
                    share(context, summaryText)
                }
            }
            if (job != null && VoiceFormat.isFinished(job.status)) {
                IconAction(Icons.Default.DeleteOutline, "Löschen", HikariTextMuted) { confirmDelete = true }
            }
        }

        when {
            state.missing -> CenterNote("Diese Zusammenfassung gibt es nicht mehr.")
            job == null && state.offline -> CenterNote("Hikari-Server nicht erreichbar")
            job == null -> CenterNote("Lädt")
            else -> JobContent(job, onRetry = { vm.retry(job.id) })
        }
    }

    if (confirmDelete && job != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = HikariCardBg,
            title = { Text("Löschen?", color = HikariText) },
            text = { Text("Zusammenfassung, Transkript und Audios werden vom Server entfernt.", color = HikariTextMuted) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    vm.delete(job.id, onDone = onBack)
                }) { Text("Löschen", color = HikariDanger) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Abbrechen", color = HikariTextMuted) }
            },
        )
    }
}

@Composable
private fun JobContent(job: VoiceJobDto, onRetry: () -> Unit) {
    val summary = job.summary
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    summary?.title ?: if (job.status == "failed") "Nicht zusammengefasst" else "Wird verarbeitet",
                    color = HikariText,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 28.sp,
                )
                Text(
                    buildString {
                        append(voiceDate(job.createdAt)).append(" · ").append(VoiceFormat.meta(job.total, job.duration))
                        if (!job.label.isNullOrBlank()) append(" · ").append(job.label)
                    },
                    color = HikariTextFaint,
                    fontSize = 12.5.sp,
                )
            }
        }

        if (!VoiceFormat.isFinished(job.status)) {
            item { ProgressCard(job) }
        }
        if (job.status == "failed") {
            item { FailedCard(job.error, onRetry) }
        }

        if (summary != null) {
            item {
                SelectionContainer {
                    Text(summary.summary, color = HikariText, fontSize = 15.sp, lineHeight = 22.sp)
                }
            }
            if (summary.todos.isNotEmpty()) item { Section("Aufgaben für dich", summary.todos, HikariAmber) }
            if (summary.dates.isNotEmpty()) item { Section("Termine und Zahlen", summary.dates, VoiceSky) }
            if (summary.questions.isNotEmpty()) item { Section("Offene Fragen", summary.questions, VoiceViolet) }
            if (summary.points.isNotEmpty()) item { Section("Kernpunkte", summary.points, HikariTextMuted) }
        }

        val heard = job.clips.filter { it.text != null }
        if (heard.isNotEmpty()) {
            item {
                Text(
                    "Transkript",
                    color = HikariTextFaint,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            items(heard, key = { it.index }) { clip -> ClipCard(clip, startExpanded = job.clips.size == 1) }
        }
        if (job.transcribeMs != null || job.summarizeMs != null) {
            item {
                Text(
                    buildString {
                        append("Whisper ").append(seconds(job.transcribeMs))
                        append(" · Qwen ").append(seconds(job.summarizeMs))
                    },
                    color = HikariTextFaint,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

private fun seconds(ms: Long?): String = if (ms == null) "–" else "%.0f s".format(ms / 1000.0)

@Composable
private fun ProgressCard(job: VoiceJobDto) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(VoiceVioletSoft)
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            VoiceFormat.statusLine(job.status, job.done, job.total, job.queuePosition),
            color = VoiceViolet,
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
        )
        LinearProgressIndicator(
            progress = { VoiceFormat.progress(job.status, job.done, job.total) },
            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp)),
            color = VoiceViolet,
            trackColor = HikariSurfaceHigh,
        )
        Text(
            "Du kannst die App schließen. Die Zusammenfassung kommt als Benachrichtigung.",
            color = HikariTextMuted,
            fontSize = 12.5.sp,
        )
    }
}

@Composable
private fun FailedCard(error: String?, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HikariDanger.copy(alpha = 0.12f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(VoiceFormat.errorText(error), color = HikariText, fontSize = 14.sp)
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(10.dp))
                .background(HikariDanger.copy(alpha = 0.2f))
                .clickable(onClick = onRetry)
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Refresh, contentDescription = null, tint = HikariDanger, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Nochmal versuchen", color = HikariDanger, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun Section(title: String, items: List<String>, color: Color) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HikariCardBg)
            .border(0.5.dp, HikariBorder, RoundedCornerShape(14.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 3.dp, height = 14.dp).clip(RoundedCornerShape(2.dp)).background(color))
            Spacer(Modifier.width(8.dp))
            Text(title, color = color, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        SelectionContainer {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                for (item in items) {
                    Row(verticalAlignment = Alignment.Top) {
                        Box(
                            Modifier
                                .padding(top = 7.dp)
                                .size(5.dp)
                                .clip(CircleShape)
                                .background(color.copy(alpha = 0.7f)),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(item, color = HikariText, fontSize = 14.sp, lineHeight = 20.sp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ClipCard(clip: VoiceClipDto, startExpanded: Boolean) {
    var expanded by rememberSaveable(clip.index) { mutableStateOf(startExpanded) }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(HikariCardBg)
            .clickable { expanded = !expanded }
            .padding(12.dp)
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                buildString {
                    append("Nachricht ").append(clip.index)
                    VoiceFormat.duration(clip.duration).takeIf { it.isNotEmpty() }?.let { append(" · ").append(it) }
                    clip.language?.takeIf { it != "de" }?.let { append(" · ").append(it.uppercase()) }
                },
                color = HikariTextMuted,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Zuklappen" else "Aufklappen",
                tint = HikariTextFaint,
                modifier = Modifier.size(18.dp),
            )
        }
        val text = clip.text.orEmpty().ifBlank { "[nichts verständlich]" }
        if (expanded) {
            SelectionContainer {
                Text(text, color = HikariText, fontSize = 13.5.sp, lineHeight = 20.sp)
            }
        } else {
            Text(text, color = HikariTextFaint, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun IconAction(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun CenterNote(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = HikariTextFaint, fontSize = 14.sp)
    }
}

private fun copy(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText("Zusammenfassung", text))
    Toast.makeText(context, "Zusammenfassung kopiert", Toast.LENGTH_SHORT).show()
}

private fun share(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Zusammenfassung teilen"))
}
