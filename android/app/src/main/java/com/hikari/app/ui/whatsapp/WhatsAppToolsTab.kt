package com.hikari.app.ui.whatsapp

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.FormatQuote
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Terminal
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hikari.app.domain.whatsapp.DirectChatHistoryItem
import com.hikari.app.domain.whatsapp.WhatsAppChatAnalysis
import com.hikari.app.domain.whatsapp.WhatsAppStatusItem
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
    hasMediaFolderAccess: Boolean,
    statusItems: List<WhatsAppStatusItem>,
    initialMediaTreeUri: Uri,
    onMediaFolderGranted: (Uri) -> Unit,
    onRefreshStatuses: () -> Unit,
    onSaveStatus: (WhatsAppStatusItem) -> Unit,
    chatAnalysis: WhatsAppChatAnalysis?,
    onAnalyzeChatUri: (Uri) -> Unit,
    onRunSampleAnalysis: () -> Unit,
    onClearAnalysis: () -> Unit,
    directChatHistory: List<DirectChatHistoryItem>,
    onRecordDirectChat: (phoneNumber: String, displayFormatted: String, message: String) -> Unit,
    onClearDirectHistory: () -> Unit,
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val openFolderLauncher = rememberLauncherForActivityResult(
        contract = object : ActivityResultContracts.OpenDocumentTree() {
            override fun createIntent(context: Context, input: Uri?): Intent {
                val intent = super.createIntent(context, input)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && input != null) {
                    intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, input)
                }
                return intent
            }
        },
    ) { uri ->
        if (uri != null) {
            onMediaFolderGranted(uri)
            Toast.makeText(context, "WhatsApp-Medienordner verknüpft!", Toast.LENGTH_SHORT).show()
        }
    }

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
        // ── TOOL 1: Status-Saver & Sprachnachrichten-/Medien-Tresor ──────────────
        StatusAndMediaVaultCard(
            hasAccess = hasMediaFolderAccess,
            statuses = statusItems,
            onRequestAccess = { openFolderLauncher.launch(initialMediaTreeUri) },
            onRefresh = onRefreshStatuses,
            onSaveStatus = onSaveStatus,
        )

        // ── TOOL 2: Direkt-Chat ohne Nummer einspeichern ────────────────────────
        DirectChatToolCard(
            context = context,
            history = directChatHistory,
            onRecordDirectChat = onRecordDirectChat,
            onClearDirectHistory = onClearDirectHistory,
        )

        // ── TOOL 3: Text-Studio, „Mehr lesen“-Spoiler & Unicode-Schriften ────────
        TextFormatterAndSpoilerCard(context = context)

        // ── TOOL 4: Chat-Analyse („WhatsApp Wrapped“) ────────────────────────────
        ChatAnalyzerCard(
            analysis = chatAnalysis,
            onPickFile = { pickChatExportLauncher.launch("*/*") },
            onRunDemo = onRunSampleAnalysis,
            onClear = onClearAnalysis,
        )

        // ── TOOL 5: Text-Repeater (Nachrichten-Vervielfacher) ───────────────────
        TextRepeaterCard(context = context)

        // ── TOOL 6: Unsichtbare / Leere Nachricht ───────────────────────────────
        InvisibleMessageCard(context = context)

        Spacer(Modifier.height(40.dp))
    }
}

@Composable
private fun StatusAndMediaVaultCard(
    hasAccess: Boolean,
    statuses: List<WhatsAppStatusItem>,
    onRequestAccess: () -> Unit,
    onRefresh: () -> Unit,
    onSaveStatus: (WhatsAppStatusItem) -> Unit,
) {
    ToolSectionCard(
        badge = "STATUS-SAVER & MEDIEN-WÄCHTER",
        title = "Sprachnachrichten-Backup & Status-Retter",
        subtitle = "Verknüpfe einmalig den WhatsApp-Medienordner: Hikari sichert dann eingehende Sprachnachrichten & Fotos sofort bei Empfang (bevor sie für alle gelöscht werden können) und zeigt dir alle aktuellen WhatsApp-Status-Storys zum Speichern an.",
        accentColor = WhatsAppGreen,
    ) {
        if (!hasAccess) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(WhatsAppGreen)
                    .clickable(onClick = onRequestAccess)
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(17.dp),
                    )
                    Text(
                        text = "WhatsApp-Medienordner freigeben",
                        color = Color.Black,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(
                text = "Tipp: Der System-Dialog öffnet direkt „Android/media/com.whatsapp/WhatsApp/Media“. Tippe unten einfach auf „Diesen Ordner verwenden“.",
                color = HikariTextFaint,
                fontSize = 11.sp,
                lineHeight = 15.sp,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(WhatsAppGreen),
                    )
                    Text(
                        text = "Sprachnachrichten-Backup aktiv · ${statuses.size} Status gefunden",
                        color = WhatsAppGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Status neu laden",
                        tint = HikariTextMuted,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable(onClick = onRefresh),
                    )
                    Icon(
                        Icons.Default.FolderOpen,
                        contentDescription = "Ordner wechseln",
                        tint = HikariTextFaint,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable(onClick = onRequestAccess),
                    )
                }
            }

            if (statuses.isEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Noch keine Status-Bilder/-Videos im Zwischenspeicher. Schau dir in WhatsApp kurz einen Status an und tippe hier auf Aktualisieren.",
                    color = HikariTextMuted,
                    fontSize = 11.5.sp,
                    lineHeight = 16.sp,
                )
            } else {
                Spacer(Modifier.height(10.dp))
                LazyRow(
                    contentPadding = PaddingValues(end = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(statuses, key = { it.uri }) { status ->
                        Box(
                            modifier = Modifier
                                .width(110.dp)
                                .aspectRatio(9f / 16f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(HikariSurface)
                                .border(0.5.dp, HikariBorderStrong, RoundedCornerShape(12.dp))
                                .clickable { onSaveStatus(status) },
                        ) {
                            AsyncImage(
                                model = Uri.parse(status.uri),
                                contentDescription = status.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                            if (status.isVideo) {
                                Icon(
                                    Icons.Default.PlayCircleOutline,
                                    contentDescription = "Video",
                                    tint = Color.White,
                                    modifier = Modifier
                                        .align(Alignment.Center)
                                        .size(32.dp),
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(6.dp)
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(WhatsAppGreen),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.Default.Download,
                                    contentDescription = "In Galerie speichern",
                                    tint = Color.Black,
                                    modifier = Modifier.size(15.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
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
private fun DirectChatToolCard(
    context: Context,
    history: List<DirectChatHistoryItem>,
    onRecordDirectChat: (phoneNumber: String, displayFormatted: String, message: String) -> Unit,
    onClearDirectHistory: () -> Unit,
) {
    var selectedCountry by remember { mutableStateOf(WhatsAppToolsHelper.COUNTRY_CODES.first()) }
    var rawPhone by remember { mutableStateOf("") }
    var initialMessage by remember { mutableStateOf("") }

    ToolSectionCard(
        badge = "DIREKT-CHAT",
        title = "Nachricht ohne Kontaktbuch senden",
        subtitle = "Starte sofort einen WhatsApp-Chat mit jeder Telefonnummer — ohne sie vorher in deinen Kontakten speichern zu müssen.",
        accentColor = WhatsAppGreen,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            WhatsAppToolsHelper.COUNTRY_CODES.forEach { country ->
                val isSelected = country.dialCode == selectedCountry.dialCode
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) WhatsAppGreenSoft else HikariSurface)
                        .border(
                            width = if (isSelected) 1.dp else 0.5.dp,
                            color = if (isSelected) WhatsAppGreen else HikariBorder,
                            shape = RoundedCornerShape(10.dp),
                        )
                        .clickable { selectedCountry = country }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = "${country.flag} ${country.dialCode}",
                        color = if (isSelected) WhatsAppGreen else HikariTextMuted,
                        fontSize = 12.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(HikariSurface)
                .border(0.5.dp, HikariBorderStrong, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Phone,
                    contentDescription = null,
                    tint = WhatsAppGreen,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = selectedCountry.dialCode,
                    color = WhatsAppGreen,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.width(8.dp))
                Box(Modifier.weight(1f)) {
                    BasicTextField(
                        value = rawPhone,
                        onValueChange = { rawPhone = it },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        textStyle = TextStyle(
                            color = HikariText,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                        ),
                        cursorBrush = SolidColor(WhatsAppGreen),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    if (rawPhone.isEmpty()) {
                        Text(
                            "176 12345678",
                            color = HikariTextFaint,
                            fontSize = 14.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 46.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(HikariSurface)
                .border(0.5.dp, HikariBorder, RoundedCornerShape(12.dp))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            BasicTextField(
                value = initialMessage,
                onValueChange = { initialMessage = it },
                textStyle = TextStyle(color = HikariText, fontSize = 13.5.sp),
                cursorBrush = SolidColor(WhatsAppGreen),
                modifier = Modifier.fillMaxWidth(),
            )
            if (initialMessage.isEmpty()) {
                Text(
                    "Optionale Nachricht vorbefüllen…",
                    color = HikariTextFaint,
                    fontSize = 13.5.sp,
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(WhatsAppGreen)
                    .clickable {
                        val normalized = WhatsAppToolsHelper.normalizePhoneNumber(
                            selectedCountry.dialCode,
                            rawPhone,
                        )
                        if (normalized == null) {
                            Toast.makeText(context, "Bitte eine gültige Telefonnummer eingeben", Toast.LENGTH_SHORT).show()
                        } else {
                            val display = WhatsAppToolsHelper.formatDisplayPhone(normalized)
                            onRecordDirectChat(normalized, display, initialMessage)
                            openWaMeLink(context, normalized, initialMessage)
                        }
                    }
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        "In WhatsApp öffnen",
                        color = Color.Black,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(HikariSurfaceHigh)
                    .border(0.5.dp, HikariBorderStrong, RoundedCornerShape(12.dp))
                    .clickable {
                        val normalized = WhatsAppToolsHelper.normalizePhoneNumber(
                            selectedCountry.dialCode,
                            rawPhone,
                        )
                        if (normalized == null) {
                            Toast.makeText(context, "Bitte zuerst eine Telefonnummer eingeben", Toast.LENGTH_SHORT).show()
                        } else {
                            val url = WhatsAppToolsHelper.buildWaMeUrl(normalized, initialMessage)
                            copyToClipboard(context, "wa.me Link", url)
                        }
                    }
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "wa.me-Link kopieren",
                    tint = HikariText,
                    modifier = Modifier.size(16.dp),
                )
            }
        }

        if (history.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Icon(
                        Icons.Default.History,
                        contentDescription = null,
                        tint = HikariTextFaint,
                        modifier = Modifier.size(13.dp),
                    )
                    Text(
                        "ZULETZT KONTAKTIERT",
                        color = HikariTextFaint,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                    )
                }
                Text(
                    "Leeren",
                    color = HikariTextMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.clickable { onClearDirectHistory() },
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                history.forEach { item ->
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(HikariSurface)
                            .border(0.5.dp, HikariBorder, RoundedCornerShape(10.dp))
                            .clickable {
                                openWaMeLink(context, item.phoneNumber, item.lastMessagePreview)
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = item.displayFormatted,
                            color = WhatsAppGreen,
                            fontSize = 11.5.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TextFormatterAndSpoilerCard(context: Context) {
    var mode by remember { mutableIntStateOf(0) } // 0 = Formatierer & Fonts, 1 = Spoiler ("Mehr lesen")
    var inputText by remember { mutableStateOf("") }
    var spoilerTeaser by remember { mutableStateOf("🔒 Geheimnis (tippe auf „Mehr lesen“)") }
    var spoilerSecret by remember { mutableStateOf("") }

    ToolSectionCard(
        badge = "TEXT-STUDIO & SCHRIFTEN",
        title = "Formatierer, Unicode-Fonts & Spoiler",
        subtitle = "Formatiere Text, wandle ihn in Spezial-Schriften für dein WhatsApp-Profil um oder verstecke Pointen hinter „… Mehr lesen“.",
        accentColor = HikariAmber,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(HikariSurface)
                .padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            listOf("Formatierer & Fonts" to 0, "Spoiler („Mehr lesen“)" to 1).forEach { (label, idx) ->
                val active = mode == idx
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) HikariSurfaceHigh else Color.Transparent)
                        .clickable { mode = idx }
                        .padding(vertical = 7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        color = if (active) HikariAmber else HikariTextMuted,
                        fontSize = 11.5.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        if (mode == 0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                FormatActionChip("Fett", Icons.Default.FormatBold) {
                    if (inputText.isNotBlank()) inputText = WhatsAppToolsHelper.formatBold(inputText)
                }
                FormatActionChip("Kursiv", Icons.Default.FormatItalic) {
                    if (inputText.isNotBlank()) inputText = WhatsAppToolsHelper.formatItalic(inputText)
                }
                FormatActionChip("Durchgestrichen", Icons.Default.FormatStrikethrough) {
                    if (inputText.isNotBlank()) inputText = WhatsAppToolsHelper.formatStrikethrough(inputText)
                }
                FormatActionChip("Code", Icons.Default.Terminal) {
                    if (inputText.isNotBlank()) inputText = WhatsAppToolsHelper.formatMonospace(inputText)
                }
                FormatActionChip("Zitat", Icons.Default.FormatQuote) {
                    if (inputText.isNotBlank()) inputText = WhatsAppToolsHelper.formatQuote(inputText)
                }
            }

            Spacer(Modifier.height(6.dp))

            // Unicode-Schriftarten (funktionieren auch in WhatsApp-Info & Gruppennamen)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                WhatsAppToolsHelper.UnicodeFontStyle.entries.forEach { fontStyle ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(HikariSurface)
                            .border(0.5.dp, HikariBorder, RoundedCornerShape(8.dp))
                            .clickable {
                                if (inputText.isNotBlank()) {
                                    inputText = WhatsAppToolsHelper.applyUnicodeFont(inputText, fontStyle)
                                }
                            }
                            .padding(horizontal = 9.dp, vertical = 5.dp),
                    ) {
                        Text(
                            text = "${fontStyle.preview} (${fontStyle.label})",
                            color = HikariTextMuted,
                            fontSize = 11.sp,
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 68.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(HikariSurface)
                    .border(0.5.dp, HikariBorder, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                BasicTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    textStyle = TextStyle(color = HikariText, fontSize = 13.5.sp),
                    cursorBrush = SolidColor(HikariAmber),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (inputText.isEmpty()) {
                    Text(
                        "Text eingeben und oben auf einen Stil oder eine Schriftart tippen…",
                        color = HikariTextFaint,
                        fontSize = 13.sp,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            CopyAndShareRow(
                onCopy = {
                    if (inputText.isBlank()) {
                        Toast.makeText(context, "Bitte zuerst einen Text eingeben", Toast.LENGTH_SHORT).show()
                    } else {
                        copyToClipboard(context, "Formatierter Text", inputText)
                    }
                },
                onShare = {
                    if (inputText.isBlank()) {
                        Toast.makeText(context, "Bitte zuerst einen Text eingeben", Toast.LENGTH_SHORT).show()
                    } else {
                        shareToWhatsApp(context, inputText)
                    }
                },
            )
        } else {
            Text(
                "SICHTBARER ANFANG (VOR „MEHR LESEN“)",
                color = HikariTextFaint,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(HikariSurface)
                    .border(0.5.dp, HikariBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            ) {
                BasicTextField(
                    value = spoilerTeaser,
                    onValueChange = { spoilerTeaser = it },
                    singleLine = true,
                    textStyle = TextStyle(color = HikariText, fontSize = 13.sp),
                    cursorBrush = SolidColor(HikariAmber),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(8.dp))

            Text(
                "VERSTECKTER TEXT (ERST BEIM AUFKLAPPEN SICHTBAR)",
                color = HikariTextFaint,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.sp,
            )
            Spacer(Modifier.height(4.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 56.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(HikariSurface)
                    .border(0.5.dp, HikariBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 12.dp, vertical = 9.dp),
            ) {
                BasicTextField(
                    value = spoilerSecret,
                    onValueChange = { spoilerSecret = it },
                    textStyle = TextStyle(color = HikariText, fontSize = 13.sp),
                    cursorBrush = SolidColor(HikariAmber),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (spoilerSecret.isEmpty()) {
                    Text(
                        "Dein geheimer Text oder Spoiler…",
                        color = HikariTextFaint,
                        fontSize = 13.sp,
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            CopyAndShareRow(
                onCopy = {
                    if (spoilerSecret.isBlank()) {
                        Toast.makeText(context, "Bitte den versteckten Text eingeben", Toast.LENGTH_SHORT).show()
                    } else {
                        val built = WhatsAppToolsHelper.buildSpoilerMessage(spoilerTeaser, spoilerSecret)
                        copyToClipboard(context, "Spoiler-Nachricht", built)
                    }
                },
                onShare = {
                    if (spoilerSecret.isBlank()) {
                        Toast.makeText(context, "Bitte den versteckten Text eingeben", Toast.LENGTH_SHORT).show()
                    } else {
                        val built = WhatsAppToolsHelper.buildSpoilerMessage(spoilerTeaser, spoilerSecret)
                        shareToWhatsApp(context, built)
                    }
                },
            )
        }
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
private fun InvisibleMessageCard(context: Context) {
    var lines by remember { mutableIntStateOf(1) }

    ToolSectionCard(
        badge = "GHOST BUBBLE",
        title = "Leere / Unsichtbare Nachricht",
        subtitle = "Versende eine komplett leere Sprechblase in WhatsApp (nutzt ein spezielles Unicode-Zeichen, das WhatsApp nicht blockiert).",
        accentColor = HikariAmber,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf(1 to "1 Zeile (Kompakt)", 5 to "5 Zeilen (Mittel)", 15 to "15 Zeilen (Lang)").forEach { (cnt, label) ->
                val selected = lines == cnt
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (selected) HikariAmberSoft else HikariSurface)
                        .border(0.5.dp, if (selected) HikariAmber else HikariBorder, RoundedCornerShape(10.dp))
                        .clickable { lines = cnt }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        color = if (selected) HikariAmber else HikariTextMuted,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
        }

        Spacer(Modifier.height(10.dp))

        CopyAndShareRow(
            onCopy = {
                val msg = WhatsAppToolsHelper.buildInvisibleMessage(lines)
                copyToClipboard(context, "Unsichtbare Nachricht", msg)
            },
            onShare = {
                val msg = WhatsAppToolsHelper.buildInvisibleMessage(lines)
                shareToWhatsApp(context, msg)
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
private fun FormatActionChip(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(HikariSurface)
            .border(0.5.dp, HikariBorderStrong, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(icon, contentDescription = null, tint = HikariAmber, modifier = Modifier.size(14.dp))
        Text(label, color = HikariText, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
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

internal fun openWaMeLink(context: Context, normalizedDigits: String, message: String) {
    val url = WhatsAppToolsHelper.buildWaMeUrl(normalizedDigits, message)
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching {
        context.startActivity(intent)
    }.onFailure {
        Toast.makeText(context, "WhatsApp konnte nicht geöffnet werden", Toast.LENGTH_SHORT).show()
    }
}
