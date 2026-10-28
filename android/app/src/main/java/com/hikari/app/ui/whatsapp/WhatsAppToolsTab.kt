package com.hikari.app.ui.whatsapp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.domain.whatsapp.WhatsAppChatAnalysis
import com.hikari.app.domain.whatsapp.WhatsAppToolsHelper
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariAmberSoft
import com.hikari.app.ui.theme.HikariBorder
import com.hikari.app.ui.theme.HikariBorderStrong
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariSurface
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted

private val WhatsAppGreen = Color(0xFF25D366)
private val WhatsAppGreenSoft = Color(0x2625D366)

@Composable
fun WhatsAppToolsTab(
    chatAnalysis: WhatsAppChatAnalysis?,
    onAnalyzeChatUri: (Uri) -> Unit,
    onRunSampleAnalysis: () -> Unit,
    onClearAnalysis: () -> Unit,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val pickChatExportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            onAnalyzeChatUri(uri)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // ── TOOL 1: Chat-Analyse („WhatsApp Wrapped“) ────────────────────────────
        ChatAnalyzerCard(
            analysis = chatAnalysis,
            onPickFile = { pickChatExportLauncher.launch("*/*") },
            onRunDemo = onRunSampleAnalysis,
            onClear = onClearAnalysis,
        )

        // ── TOOL 2: Text-Repeater (Nachrichten-Vervielfacher) ───────────────────
        TextRepeaterCard(context = context)

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun ChatAnalyzerCard(
    analysis: WhatsAppChatAnalysis?,
    onPickFile: () -> Unit,
    onRunDemo: () -> Unit,
    onClear: () -> Unit,
) {
    ToolSectionCard(
        badge = "WHATSAPP WRAPPED",
        title = "Chat-Statistik & Rekorde",
        subtitle = "Wähle in einem WhatsApp-Chat „Mehr → Chat exportieren (ohne Medien)“ und öffne die `.txt`- oder `.zip`-Datei hier für eine komplette Auswertung.",
        accentColor = HikariAmber,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(11.dp))
                    .background(HikariAmber)
                    .clickable(onClick = onPickFile)
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Default.UploadFile,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = "Chat-Export (.txt/.zip) öffnen",
                        color = Color.Black,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(11.dp))
                    .background(HikariSurfaceHigh)
                    .border(0.5.dp, HikariBorderStrong, RoundedCornerShape(11.dp))
                    .clickable(onClick = if (analysis == null) onRunDemo else onClear)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(
                        if (analysis == null) Icons.Default.AutoAwesome else Icons.Default.Analytics,
                        contentDescription = null,
                        tint = HikariAmber,
                        modifier = Modifier.size(15.dp),
                    )
                    Text(
                        text = if (analysis == null) "Demo" else "Schließen",
                        color = HikariText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }

        if (analysis != null) {
            Spacer(Modifier.height(14.dp))

            // Kennzahlen-Grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                StatMetricBox("Nachrichten", analysis.totalMessages.toString(), Modifier.weight(1f))
                StatMetricBox("Wörter", analysis.totalWords.toString(), Modifier.weight(1f))
                StatMetricBox("Medien", analysis.totalMedia.toString(), Modifier.weight(1f))
                StatMetricBox("Peak-Zeit", "${analysis.peakHour}:00", Modifier.weight(1f))
            }

            Spacer(Modifier.height(12.dp))

            // Wer schreibt am meisten?
            Text(
                text = "WER SCHREIBT AM MEISTEN?",
                color = HikariTextFaint,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(6.dp))
            analysis.participants.take(6).forEach { p ->
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = p.name,
                            color = HikariText,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            text = "${p.messageCount} Nachr. (${String.format("%.1f", p.sharePercent)}%) · Ø ${String.format("%.1f", p.avgWordsPerMessage)} Wörter",
                            color = HikariTextMuted,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(7.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(HikariSurface),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth((p.sharePercent / 100f).coerceIn(0.03f, 1f))
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(4.dp))
                                .background(WhatsAppGreen),
                        )
                    }
                }
            }

            // Top Emojis & Top Wörter
            if (analysis.topEmojis.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "MEISTGENUTZTE EMOJIS",
                    color = HikariTextFaint,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    analysis.topEmojis.forEach { (emoji, cnt) ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(HikariSurface)
                                .border(0.5.dp, HikariBorder, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(emoji, fontSize = 14.sp)
                            Text(
                                "${cnt}×",
                                color = HikariAmber,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }

            if (analysis.topWords.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "LIEBLINGSWÖRTER",
                    color = HikariTextFaint,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(6.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    analysis.topWords.forEach { (word, cnt) ->
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(HikariSurface)
                                .border(0.5.dp, HikariBorder, RoundedCornerShape(8.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(word, color = HikariText, fontSize = 11.5.sp)
                            Text(
                                "${cnt}×",
                                color = WhatsAppGreen,
                                fontSize = 10.5.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatMetricBox(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(HikariSurface)
            .border(0.5.dp, HikariBorder, RoundedCornerShape(10.dp))
            .padding(vertical = 8.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = value,
            color = HikariAmber,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = label,
            color = HikariTextMuted,
            fontSize = 10.sp,
            maxLines = 1,
        )
    }
}

@Composable
private fun TextRepeaterCard(context: Context) {
    var phrase by remember { mutableStateOf("") }
    var count by remember { mutableIntStateOf(10) }
    var newLine by remember { mutableStateOf(true) }
    var numbered by remember { mutableStateOf(false) }

    ToolSectionCard(
        badge = "TEXT-REPEATER",
        title = "Nachrichten-Vervielfacher",
        subtitle = "Wiederhole Text oder Emojis bis zu 100-mal mit einem einzigen Klick.",
        accentColor = WhatsAppGreen,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(HikariSurface)
                .border(0.5.dp, HikariBorder, RoundedCornerShape(10.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        ) {
            BasicTextField(
                value = phrase,
                onValueChange = { phrase = it },
                singleLine = true,
                textStyle = TextStyle(color = HikariText, fontSize = 13.5.sp),
                cursorBrush = SolidColor(WhatsAppGreen),
                modifier = Modifier.fillMaxWidth(),
            )
            if (phrase.isEmpty()) {
                Text("Text oder Emoji zum Wiederholen…", color = HikariTextFaint, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            listOf(5, 10, 25, 50, 100).forEach { preset ->
                val active = count == preset
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) WhatsAppGreenSoft else HikariSurface)
                        .border(0.5.dp, if (active) WhatsAppGreen else HikariBorder, RoundedCornerShape(8.dp))
                        .clickable { count = preset }
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                ) {
                    Text(
                        "${preset}x",
                        color = if (active) WhatsAppGreen else HikariTextMuted,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (newLine) HikariAmberSoft else HikariSurface)
                    .border(0.5.dp, if (newLine) HikariAmber else HikariBorder, RoundedCornerShape(8.dp))
                    .clickable { newLine = !newLine }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(
                    if (newLine) "Zeilenumbruch" else "Nebeneinander",
                    color = if (newLine) HikariAmber else HikariTextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (numbered) HikariAmberSoft else HikariSurface)
                    .border(0.5.dp, if (numbered) HikariAmber else HikariBorder, RoundedCornerShape(8.dp))
                    .clickable { numbered = !numbered }
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text(
                    "1. 2. 3.",
                    color = if (numbered) HikariAmber else HikariTextMuted,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        CopyAndShareRow(
            onCopy = {
                if (phrase.isBlank()) {
                    Toast.makeText(context, "Bitte zuerst Text oder Emoji eingeben", Toast.LENGTH_SHORT).show()
                } else {
                    val result = WhatsAppToolsHelper.repeatText(phrase, count, newLine, numbered)
                    copyToClipboard(context, "Wiederholter Text (${count}x)", result)
                }
            },
            onShare = {
                if (phrase.isBlank()) {
                    Toast.makeText(context, "Bitte zuerst Text oder Emoji eingeben", Toast.LENGTH_SHORT).show()
                } else {
                    val result = WhatsAppToolsHelper.repeatText(phrase, count, newLine, numbered)
                    shareToWhatsApp(context, result)
                }
            },
        )
    }
}

@Composable
private fun ToolSectionCard(
    badge: String,
    title: String,
    subtitle: String,
    accentColor: Color,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(HikariCardBg)
            .border(0.5.dp, HikariBorderStrong, RoundedCornerShape(18.dp))
            .padding(16.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .clip(CircleShape)
                    .background(accentColor),
            )
            Text(
                text = badge,
                color = accentColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.2.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = title,
            color = HikariText,
            fontSize = 15.5.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = subtitle,
            color = HikariTextMuted,
            fontSize = 12.sp,
            lineHeight = 16.sp,
        )
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun CopyAndShareRow(
    onCopy: () -> Unit,
    onShare: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(11.dp))
                .background(HikariSurfaceHigh)
                .border(0.5.dp, HikariBorderStrong, RoundedCornerShape(11.dp))
                .clickable(onClick = onCopy)
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = null,
                    tint = HikariText,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    "Kopieren",
                    color = HikariText,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(11.dp))
                .background(WhatsAppGreen)
                .clickable(onClick = onShare)
                .padding(vertical = 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    Icons.Default.Share,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    "An WhatsApp senden",
                    color = Color.Black,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

internal fun copyToClipboard(context: Context, label: String, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
    cm?.setPrimaryClip(ClipData.newPlainText(label, text))
    Toast.makeText(context, "$label kopiert", Toast.LENGTH_SHORT).show()
}

internal fun shareToWhatsApp(context: Context, text: String) {
    val waIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
        setPackage("com.whatsapp")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
        context.startActivity(waIntent)
    }.onFailure {
        val chooser = Intent.createChooser(
            Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_TEXT, text)
            },
            "Teilen über",
        ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
        runCatching { context.startActivity(chooser) }
    }
}
