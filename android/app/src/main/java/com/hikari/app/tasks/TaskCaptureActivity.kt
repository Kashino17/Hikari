package com.hikari.app.tasks

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import java.io.File
import kotlinx.coroutines.delay

private val Ink = Color(0xFF0B0B0D)
private val Sheet = Color(0xFF18181B)
private val Surf = Color(0xFF2A2A2E)
private val Amber = Color(0xFFFBBF24)

/**
 * Aufnahme-Fenster für Aufgaben. Startet aus Schnellmenü-Kachel, Seitentaste (»Hikari Aufgabe«),
 * Widget und dem Mikrofon im Aufgaben-Reiter. Sprechen, Stopp, fertig: Hochladen und Sortieren
 * laufen im Hintergrund, das Ergebnis kommt als Benachrichtigung.
 */
class TaskCaptureActivity : ComponentActivity() {
    private var recorder: MediaRecorder? = null
    private var file: File? = null
    private var startedAt = 0L

    private enum class Mode { ASK, LISTEN, TYPE, SENT }
    private var mode by mutableStateOf(Mode.ASK)
    private val bars = mutableStateListOf<Float>().apply { repeat(BARS) { add(0.05f) } }
    private var seconds by mutableIntStateOf(0)
    private var sentText by mutableStateOf("Aufgenommen")

    private val askMic = registerForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        if (ok) startRecording() else mode = Mode.TYPE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Seitentaste auf dem Sperrbildschirm: sofort sprechen können.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) setShowWhenLocked(true)
        val typeFirst = intent?.getBooleanExtra(EXTRA_TYPE, false) == true
        setContent { Screen() }
        when {
            typeFirst -> mode = Mode.TYPE
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED -> startRecording()
            else -> askMic.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    private fun startRecording() {
        val f = File(TaskCapture.audioDir(this), "aufnahme-${System.currentTimeMillis()}.m4a")
        val r = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else @Suppress("DEPRECATION") MediaRecorder()
        try {
            r.setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
            r.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            r.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            r.setAudioChannels(1)
            r.setAudioSamplingRate(32_000)
            r.setAudioEncodingBitRate(64_000)
            r.setMaxDuration(MAX_SECONDS * 1000)
            r.setOnInfoListener { _, what, _ -> if (what == MediaRecorder.MEDIA_RECORDER_INFO_MAX_DURATION_REACHED) runOnUiThread { finishRecording() } }
            r.setOutputFile(f.absolutePath)
            r.prepare()
            r.start()
        } catch (e: Exception) {
            r.release(); f.delete()
            Toast.makeText(this, "Mikrofon nicht verfügbar, du kannst tippen", Toast.LENGTH_SHORT).show()
            mode = Mode.TYPE
            return
        }
        recorder = r; file = f; startedAt = System.currentTimeMillis(); mode = Mode.LISTEN
    }

    private fun stopRecorder(keep: Boolean): File? {
        val r = recorder ?: return null
        recorder = null
        val ok = runCatching { r.stop() }.isSuccess
        r.release()
        val f = file; file = null
        if (!keep || !ok || f == null || System.currentTimeMillis() - startedAt < 900) { f?.delete(); return null }
        return f
    }

    private fun finishRecording() {
        val f = stopRecorder(keep = true)
        if (f == null) {
            Toast.makeText(this, "Zu kurz, nichts aufgenommen", Toast.LENGTH_SHORT).show()
            finish(); return
        }
        TaskCapture.enqueueAudio(this, f)
        sentText = "Aufgenommen"
        mode = Mode.SENT
    }

    private fun sendText(text: String) {
        if (text.isBlank()) return
        TaskCapture.enqueueText(this, text.trim(), "text")
        sentText = "Notiert"
        mode = Mode.SENT
    }

    private fun toTyping() { stopRecorder(keep = false); mode = Mode.TYPE }

    private fun cancel() { stopRecorder(keep = false); finish() }

    override fun onStop() {
        super.onStop()
        // Weggewischt oder Bildschirm aus während der Aufnahme: das Gesagte nicht verlieren.
        if (recorder != null && !isChangingConfigurations) finishRecording()
        if (mode == Mode.SENT) finish()
    }

    override fun onDestroy() {
        stopRecorder(keep = false)
        super.onDestroy()
    }

    @Composable
    private fun Screen() {
        LaunchedEffect(mode) {
            if (mode == Mode.LISTEN) {
                while (recorder != null) {
                    val amp = runCatching { recorder?.maxAmplitude ?: 0 }.getOrDefault(0)
                    bars.removeAt(0)
                    bars.add((amp / 18_000f).coerceIn(0.05f, 1f))
                    seconds = ((System.currentTimeMillis() - startedAt) / 1000).toInt()
                    delay(70)
                }
            }
            if (mode == Mode.SENT) { delay(1300); finish() }
        }
        Box(
            Modifier.fillMaxSize().background(Color(0xCC000000))
                .clickable(remember { MutableInteractionSource() }, null) { if (mode != Mode.SENT) cancel() },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Column(
                Modifier
                    .navigationBarsPadding().imePadding()
                    .padding(10.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(26.dp)).background(Sheet)
                    .clickable(remember { MutableInteractionSource() }, null) {}
                    .padding(horizontal = 20.dp, vertical = 22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                when (mode) {
                    Mode.ASK -> Text("Mikrofon wird geöffnet …", color = Color(0xB3FFFFFF), fontSize = 14.sp)
                    Mode.LISTEN -> Listening()
                    Mode.TYPE -> Typing()
                    Mode.SENT -> Sent()
                }
            }
        }
    }

    @Composable
    private fun Listening() {
        Text("SAG EINFACH, WAS ANSTEHT", color = Color(0x99FFFFFF), fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp)
        Spacer(Modifier.height(18.dp))
        Row(Modifier.height(64.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
            bars.forEach { v ->
                val h by animateDpAsState((6 + v * 58).dp, label = "bar")
                Box(Modifier.width(5.dp).height(h).clip(RoundedCornerShape(3.dp)).background(Color.White))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("%d:%02d".format(seconds / 60, seconds % 60), color = Color(0x99FFFFFF), fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(10.dp))
        Text(
            "Mehrere Sachen in einem Satz gehen auch. Fristen einfach mitsagen: »bis Montag«.",
            color = Color(0x80FFFFFF), fontSize = 13.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(28.dp)) {
            Small("Tippen", Icons.Outlined.Keyboard) { toTyping() }
            Box(
                Modifier.size(76.dp).clip(CircleShape).background(Color.White).clickable { finishRecording() },
                contentAlignment = Alignment.Center,
            ) { Box(Modifier.size(24.dp).clip(RoundedCornerShape(5.dp)).background(Ink)) }
            Small("Abbrechen", Icons.Outlined.Close) { cancel() }
        }
    }

    @Composable
    private fun Typing() {
        var text by remember { mutableStateOf("") }
        val focus = remember { FocusRequester() }
        LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
        Text("EINFACH HINSCHREIBEN", color = Color(0x99FFFFFF), fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = 0.4.sp)
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().heightIn(min = 110.dp).clip(RoundedCornerShape(14.dp)).background(Surf).padding(14.dp)) {
            if (text.isEmpty()) Text("z. B. morgen Steuerberater anrufen und bis Freitag Flug buchen", color = Color(0x66FFFFFF), fontSize = 16.sp)
            BasicTextField(
                value = text, onValueChange = { text = it },
                textStyle = TextStyle(color = Color.White, fontSize = 16.5.sp),
                cursorBrush = SolidColor(Amber),
                modifier = Modifier.fillMaxWidth().focusRequester(focus),
            )
        }
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(if (text.isBlank()) Color(0x33FFFFFF) else Color.White)
                .clickable(enabled = text.isNotBlank()) { sendText(text) }.padding(vertical = 14.dp),
            contentAlignment = Alignment.Center,
        ) { Text("Fertig", color = Ink, fontWeight = FontWeight.Bold, fontSize = 15.5.sp) }
        Text(
            "Abbrechen", color = Color(0xB3FFFFFF), fontSize = 14.5.sp,
            modifier = Modifier.padding(top = 6.dp).clip(RoundedCornerShape(8.dp)).clickable { cancel() }.padding(12.dp),
        )
    }

    @Composable
    private fun Sent() {
        Box(Modifier.size(72.dp).clip(CircleShape).background(Amber), contentAlignment = Alignment.Center) {
            Icon(Icons.Outlined.Check, null, tint = Ink, modifier = Modifier.size(38.dp))
        }
        Spacer(Modifier.height(14.dp))
        Text(sentText, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(4.dp))
        Text(
            "Hikari sortiert im Hintergrund.\nDu bekommst gleich eine Benachrichtigung.",
            color = Color(0xB3FFFFFF), fontSize = 13.sp, textAlign = TextAlign.Center, lineHeight = 19.sp,
        )
    }

    @Composable
    private fun Small(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick)) {
            Box(Modifier.size(48.dp).clip(CircleShape).background(Surf), contentAlignment = Alignment.Center) {
                Icon(icon, label, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.height(5.dp))
            Text(label, color = Color(0xA0FFFFFF), fontSize = 12.sp)
        }
    }

    companion object {
        const val EXTRA_TYPE = "type"
        private const val BARS = 30
        private const val MAX_SECONDS = 180
    }
}

/** Teilen-Ziel »Hikari Aufgabe«: Text aus WhatsApp, Mail & Co. wird zur Aufgabe. */
class TaskShareActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val text = listOfNotNull(
            intent?.getStringExtra(Intent.EXTRA_SUBJECT),
            intent?.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString(),
        ).joinToString("\n").trim()
        if (text.isNotEmpty()) {
            TaskCapture.enqueueText(this, text, "share")
            Toast.makeText(this, "An Hikari geteilt, wird sortiert", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "Kein Text zum Teilen gefunden", Toast.LENGTH_SHORT).show()
        }
        finish()
    }
}

/** Kachel im Schnellmenü: »Aufgabe sprechen«. */
class TaskTileService : TileService() {
    override fun onStartListening() {
        qsTile?.apply { state = android.service.quicksettings.Tile.STATE_INACTIVE; updateTile() }
    }

    override fun onClick() {
        val intent = Intent(this, TaskCaptureActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val open = {
            if (Build.VERSION.SDK_INT >= 34) {
                startActivityAndCollapse(
                    android.app.PendingIntent.getActivity(this, 7950, intent, android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT),
                )
            } else {
                @Suppress("DEPRECATION")
                startActivityAndCollapse(intent)
            }
        }
        if (isLocked) unlockAndRun { open() } else open()
    }
}
