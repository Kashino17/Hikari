package com.hikari.app.ui.whatsapp

import android.content.Intent
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RestoreFromTrash
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.hikari.app.data.db.WhatsAppMessageEntity
import com.hikari.app.domain.whatsapp.WhatsAppEditVersion
import com.hikari.app.domain.whatsapp.WhatsAppRepository
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariAmberSoft
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariBorder
import com.hikari.app.ui.theme.HikariBorderStrong
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariDanger
import com.hikari.app.ui.theme.HikariSurface
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted
import com.hikari.app.whatsapp.WhatsAppNotificationListenerService
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val WhatsAppGreen = Color(0xFF25D366)
private val WhatsAppGreenDark = Color(0xFF075E54)
private val WhatsAppGreenSoft = Color(0x2625D366)
private val DeletedRedSoft = Color(0x26F87171)

private enum class WhatsAppTab {
    DELETED,
    EDITED,
    TOOLS,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WhatsAppScreen(
    onBack: () -> Unit,
    vm: WhatsAppViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val deletedMessages by vm.deletedMessages.collectAsState()
    val editedMessages by vm.editedMessages.collectAsState()
    val stats by vm.stats.collectAsState()
    val alertsEnabled by vm.alertsEnabled.collectAsState()
    val directChatHistory by vm.directChatHistory.collectAsState()
    val searchQuery by vm.searchQuery.collectAsState()
    val hasMediaFolderAccess by vm.hasMediaFolderAccess.collectAsState()
    val statusItems by vm.statusItems.collectAsState()
    val chatAnalysis by vm.chatAnalysis.collectAsState()

    var activeTab by remember { mutableStateOf(WhatsAppTab.DELETED) }
    var showInfoSheet by remember { mutableStateOf(false) }
    var hasNotificationAccess by remember {
        mutableStateOf(WhatsAppNotificationListenerService.isNotificationAccessGranted(context))
    }

    // Aktualisiere den Berechtigungs-Status & führe 7-Tage-Auto-Bereinigung bei Rückkehr aus
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasNotificationAccess =
                    WhatsAppNotificationListenerService.isNotificationAccessGranted(context)
                vm.pruneNow()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HikariBg),
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Top Bar ──────────────────────────────────────────────────────────
            WhatsAppTopBar(
                hasAccess = hasNotificationAccess,
                alertsEnabled = alertsEnabled,
                onBack = onBack,
                onToggleAlerts = {
                    val next = !alertsEnabled
                    vm.setAlertsEnabled(next)
                    Toast.makeText(
                        context,
                        if (next) "Wächter-Alarme aktiviert" else "Wächter-Alarme stummgeschaltet",
                        Toast.LENGTH_SHORT,
                    ).show()
                },
                onRunDemo = {
                    vm.runDemoScenario()
                    Toast.makeText(
                        context,
                        "Demo-Nachrichten (Gelöscht & Bearbeitet) wurden simuliert!",
                        Toast.LENGTH_SHORT,
                    ).show()
                },
                onShowInfo = { showInfoSheet = true },
            )

            // ── Berechtigungs-Banner (falls Benachrichtigungszugriff noch fehlt) ──
            if (!hasNotificationAccess) {
                PermissionSetupBanner(
                    onOpenSettings = {
                        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        runCatching { context.startActivity(intent) }
                    },
                )
            }

            // ── Kompakte Statistik-Leiste (inkl. 7-Tage-Auto-Cleanup-Anzeige) ────
            StatsOverviewStrip(
                deletedCount = stats.deletedCount,
                editedCount = stats.editedCount,
                onClearAll = {
                    vm.clearAllMessages()
                    Toast.makeText(context, "Nachrichten-Tresor geleert", Toast.LENGTH_SHORT).show()
                },
            )

            // ── Tab-Auswahl: Gelöscht · Bearbeitet · Tools ───────────────────────
            WhatsAppTabsRow(
                activeTab = activeTab,
                deletedCount = stats.deletedCount,
                editedCount = stats.editedCount,
                onSelect = { activeTab = it },
            )

            // ── Suchleiste für Gelöscht / Bearbeitet ────────────────────────────
            if (activeTab != WhatsAppTab.TOOLS) {
                WhatsAppSearchBar(
                    query = searchQuery,
                    onQueryChange = vm::setSearchQuery,
                )
            }

            // ── Hauptinhalt pro Tab ─────────────────────────────────────────────
            Box(modifier = Modifier.fillMaxSize()) {
                val q = searchQuery.trim().lowercase()
                when (activeTab) {
                    WhatsAppTab.DELETED -> {
                        val filtered = remember(deletedMessages, q) {
                            if (q.isEmpty()) {
                                deletedMessages
                            } else {
                                deletedMessages.filter {
                                    it.text.lowercase().contains(q) ||
                                        it.chatTitle.lowercase().contains(q) ||
                                        it.senderName.lowercase().contains(q)
                                }
                            }
                        }
                        DeletedMessagesList(
                            messages = filtered,
                            onCopy = { copyToClipboard(context, "Nachricht", it.text) },
                            onDeleteEntry = { vm.deleteMessage(it.id) },
                            onRunDemo = { vm.runDemoScenario() },
                        )
                    }

                    WhatsAppTab.EDITED -> {
                        val filtered = remember(editedMessages, q) {
                            if (q.isEmpty()) {
                                editedMessages
                            } else {
                                editedMessages.filter {
                                    it.text.lowercase().contains(q) ||
                                        (it.originalText?.lowercase()?.contains(q) == true) ||
                                        it.chatTitle.lowercase().contains(q) ||
                                        it.senderName.lowercase().contains(q)
                                }
                            }
                        }
                        EditedMessagesList(
                            messages = filtered,
                            decodeHistory = vm::decodeEditHistory,
                            onCopy = { copyToClipboard(context, "Nachricht", it) },
                            onDeleteEntry = { vm.deleteMessage(it.id) },
                            onRunDemo = { vm.runDemoScenario() },
                        )
                    }

                    WhatsAppTab.TOOLS -> {
                        WhatsAppToolsTab(
                            hasMediaFolderAccess = hasMediaFolderAccess,
                            statusItems = statusItems,
                            initialMediaTreeUri = remember { vm.buildInitialMediaTreeUri() },
                            onMediaFolderGranted = vm::onMediaFolderGranted,
                            onRefreshStatuses = vm::refreshStatuses,
                            onSaveStatus = { item ->
                                vm.saveStatusToGallery(item) { ok ->
                                    Toast.makeText(
                                        context,
                                        if (ok) "Status in Galerie gespeichert!" else "Speichern fehlgeschlagen",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                            chatAnalysis = chatAnalysis,
                            onAnalyzeChatUri = { uri ->
                                vm.analyzeExportedChatUri(context, uri) {
                                    Toast.makeText(
                                        context,
                                        "Datei konnte nicht als WhatsApp-Chat gelesen werden",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            },
                            onRunSampleAnalysis = vm::runSampleChatAnalysis,
                            onClearAnalysis = vm::clearChatAnalysis,
                            directChatHistory = directChatHistory,
                            onRecordDirectChat = vm::recordDirectChat,
                            onClearDirectHistory = vm::clearDirectChatHistory,
                        )
                    }
                }
            }
        }
    }

    // ── Funktionsweise / Info-Sheet ──────────────────────────────────────────────
    if (showInfoSheet) {
        HowItWorksBottomSheet(
            hasAccess = hasNotificationAccess,
            onOpenPermissionSettings = {
                showInfoSheet = false
                val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                runCatching { context.startActivity(intent) }
            },
            onDismiss = { showInfoSheet = false },
        )
    }
}

@Composable
private fun WhatsAppTopBar(
    hasAccess: Boolean,
    alertsEnabled: Boolean,
    onBack: () -> Unit,
    onToggleAlerts: () -> Unit,
    onRunDemo: () -> Unit,
    onShowInfo: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(HikariSurfaceHigh.copy(alpha = 0.65f))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Zurück",
                tint = HikariText,
                modifier = Modifier.size(18.dp),
            )
        }

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = "WhatsApp-Ecke",
                    color = HikariText,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (hasAccess) WhatsAppGreenSoft else HikariAmberSoft)
                        .border(
                            0.5.dp,
                            if (hasAccess) WhatsAppGreen else HikariAmber,
                            RoundedCornerShape(8.dp),
                        )
                        .padding(horizontal = 7.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(if (hasAccess) WhatsAppGreen else HikariAmber),
                    )
                    Text(
                        text = if (hasAccess) "Wächter aktiv" else "Zugriff fehlt",
                        color = if (hasAccess) WhatsAppGreen else HikariAmber,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
            Text(
                text = "Speichert nur Gelöscht & Bearbeitet · Auto-Reset nach 7 Tagen",
                color = HikariTextMuted,
                fontSize = 11.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(HikariSurfaceHigh.copy(alpha = 0.65f))
                .clickable(onClick = onRunDemo),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = "Demo-Szenario testen",
                tint = HikariAmber,
                modifier = Modifier.size(17.dp),
            )
        }

        Spacer(Modifier.width(6.dp))

        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(HikariSurfaceHigh.copy(alpha = 0.65f))
                .clickable(onClick = onToggleAlerts),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (alertsEnabled) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff,
                contentDescription = "Wächter-Benachrichtigung",
                tint = if (alertsEnabled) WhatsAppGreen else HikariTextFaint,
                modifier = Modifier.size(17.dp),
            )
        }

        Spacer(Modifier.width(6.dp))

        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(HikariSurfaceHigh.copy(alpha = 0.65f))
                .clickable(onClick = onShowInfo),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Info,
                contentDescription = "Wie funktioniert das?",
                tint = HikariTextMuted,
                modifier = Modifier.size(17.dp),
            )
        }
    }
}

@Composable
private fun PermissionSetupBanner(onOpenSettings: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(
                Brush.linearGradient(
                    listOf(WhatsAppGreenDark.copy(alpha = 0.55f), HikariCardBg),
                ),
            )
            .border(1.dp, WhatsAppGreen.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Column {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    tint = WhatsAppGreen,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Benachrichtigungszugriff erforderlich",
                    color = HikariText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Damit Hikari gelöschte und bearbeitete WhatsApp-Nachrichten abfangen kann, aktiviere bitte einmalig „Hikari“ im Android-Benachrichtigungszugriff.",
                color = HikariTextMuted,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
            Spacer(Modifier.height(10.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(WhatsAppGreen)
                    .clickable(onClick = onOpenSettings)
                    .padding(vertical = 9.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Jetzt in Android-Einstellungen aktivieren",
                    color = Color.Black,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun StatsOverviewStrip(
    deletedCount: Int,
    editedCount: Int,
    onClearAll: () -> Unit,
) {
    val totalSaved = deletedCount + editedCount
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MiniStatPill(
            label = "Gelöscht",
            value = deletedCount.toString(),
            accentColor = HikariDanger,
            modifier = Modifier.weight(1f),
        )
        MiniStatPill(
            label = "Bearbeitet",
            value = editedCount.toString(),
            accentColor = HikariAmber,
            modifier = Modifier.weight(1f),
        )
        MiniStatPill(
            label = "Auto-Löschung",
            value = "7 Tg.",
            accentColor = WhatsAppGreen,
            modifier = Modifier.weight(1.15f),
        )
        if (totalSaved > 0) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(HikariCardBg)
                    .border(0.5.dp, HikariBorder, RoundedCornerShape(10.dp))
                    .clickable(onClick = onClearAll),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.DeleteSweep,
                    contentDescription = "Tresor leeren",
                    tint = HikariTextMuted,
                    modifier = Modifier.size(17.dp),
                )
            }
        }
    }
}

@Composable
private fun MiniStatPill(
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(11.dp))
            .background(HikariCardBg)
            .border(0.5.dp, HikariBorder, RoundedCornerShape(11.dp))
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            color = HikariTextMuted,
            fontSize = 11.sp,
            maxLines = 1,
        )
        Text(
            text = value,
            color = accentColor,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun WhatsAppTabsRow(
    activeTab: WhatsAppTab,
    deletedCount: Int,
    editedCount: Int,
    onSelect: (WhatsAppTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        WhatsAppTabChip(
            icon = Icons.Default.RestoreFromTrash,
            label = "Gelöscht",
            count = deletedCount,
            selected = activeTab == WhatsAppTab.DELETED,
            accentColor = HikariDanger,
            onClick = { onSelect(WhatsAppTab.DELETED) },
        )
        WhatsAppTabChip(
            icon = Icons.Default.EditNote,
            label = "Bearbeitet",
            count = editedCount,
            selected = activeTab == WhatsAppTab.EDITED,
            accentColor = HikariAmber,
            onClick = { onSelect(WhatsAppTab.EDITED) },
        )
        WhatsAppTabChip(
            icon = Icons.Default.Build,
            label = "Tools",
            count = 6,
            selected = activeTab == WhatsAppTab.TOOLS,
            accentColor = WhatsAppGreen,
            onClick = { onSelect(WhatsAppTab.TOOLS) },
        )
    }
}

@Composable
private fun WhatsAppTabChip(
    icon: ImageVector,
    label: String,
    count: Int,
    selected: Boolean,
    accentColor: Color,
    onClick: () -> Unit,
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) HikariSurfaceHigh else HikariCardBg,
        animationSpec = tween(160),
        label = "wa-tab-bg",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) accentColor else HikariBorder,
        animationSpec = tween(160),
        label = "wa-tab-border",
    )

    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(
                width = if (selected) 1.2.dp else 0.5.dp,
                color = borderColor,
                shape = RoundedCornerShape(12.dp),
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Icon(
            icon,
            contentDescription = label,
            tint = if (selected) accentColor else HikariTextFaint,
            modifier = Modifier.size(15.dp),
        )
        Text(
            text = label,
            color = if (selected) HikariText else HikariTextMuted,
            fontSize = 12.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (selected) accentColor.copy(alpha = 0.2f) else HikariSurface)
                .padding(horizontal = 5.dp, vertical = 1.dp),
        ) {
            Text(
                text = count.toString(),
                color = if (selected) accentColor else HikariTextFaint,
                fontSize = 10.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun WhatsAppSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(HikariCardBg)
            .border(0.5.dp, HikariBorder, RoundedCornerShape(12.dp))
            .padding(horizontal = 12.dp, vertical = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = HikariTextFaint,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = HikariText, fontSize = 13.sp),
                    cursorBrush = SolidColor(WhatsAppGreen),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (query.isEmpty()) {
                    Text(
                        "Gelöschte & bearbeitete Nachrichten durchsuchen…",
                        color = HikariTextFaint,
                        fontSize = 13.sp,
                    )
                }
            }
            if (query.isNotEmpty()) {
                Icon(
                    Icons.Default.Clear,
                    contentDescription = "Suche löschen",
                    tint = HikariTextMuted,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { onQueryChange("") },
                )
            }
        }
    }
}

@Composable
private fun DeletedMessagesList(
    messages: List<WhatsAppMessageEntity>,
    onCopy: (WhatsAppMessageEntity) -> Unit,
    onDeleteEntry: (WhatsAppMessageEntity) -> Unit,
    onRunDemo: () -> Unit,
) {
    if (messages.isEmpty()) {
        EmptyVaultPlaceholder(
            icon = Icons.Default.RestoreFromTrash,
            title = "Noch keine gelöschten Nachrichten",
            subtitle = "Normale Nachrichten werden NICHT dauerhaft gespeichert — nur wenn jemand eine Nachricht „Für alle löscht“, landet sie hier und wird nach 7 Tagen automatisch wieder gelöscht.",
            onRunDemo = onRunDemo,
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(messages, key = { it.id }) { msg ->
            DeletedMessageCard(
                msg = msg,
                onCopy = { onCopy(msg) },
                onDelete = { onDeleteEntry(msg) },
            )
        }
    }
}

@Composable
private fun DeletedMessageCard(
    msg: WhatsAppMessageEntity,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    val remainingDays = remainingRetentionDays(msg.deletedAt ?: msg.timestamp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HikariCardBg)
            .border(0.8.dp, HikariDanger.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChatAvatarBubble(
                avatarPath = msg.avatarPath,
                title = msg.chatTitle,
                isGroup = msg.isGroup,
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = msg.chatTitle,
                    color = HikariText,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (msg.isGroup && msg.senderName != msg.chatTitle) {
                    Text(
                        text = "Absender: ${msg.senderName}",
                        color = WhatsAppGreen,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(DeletedRedSoft)
                    .border(0.5.dp, HikariDanger, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    text = "GELÖSCHT",
                    color = HikariDanger,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp,
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(HikariSurface)
                .border(0.5.dp, HikariBorderStrong, RoundedCornerShape(12.dp))
                .padding(12.dp),
        ) {
            Column {
                Text(
                    text = "GERETTETER ORIGINALTEXT",
                    color = HikariAmber,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = msg.text,
                    color = HikariText,
                    fontSize = 14.sp,
                    lineHeight = 19.sp,
                )

                val previewFile = remember(msg.mediaPreviewPath) {
                    msg.mediaPreviewPath?.let(::File)?.takeIf { it.exists() }
                }
                if (previewFile != null) {
                    Spacer(Modifier.height(8.dp))
                    if (isAudioFile(previewFile)) {
                        VoiceNotePlayerBar(audioFile = previewFile)
                    } else {
                        AsyncImage(
                            model = previewFile,
                            contentDescription = "Gesichertes Vorschaubild",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(10.dp)),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = buildString {
                    append(formatTimestamp(msg.timestamp))
                    msg.deletedAt?.let { append(" · Gelöscht ${formatTimeOnly(it)}") }
                    append(" · Auto-Del in ${remainingDays}d")
                },
                color = HikariTextFaint,
                fontSize = 10.5.sp,
                fontFamily = FontFamily.Monospace,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Text kopieren",
                    tint = HikariTextMuted,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable(onClick = onCopy),
                )
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "Eintrag entfernen",
                    tint = HikariTextFaint,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable(onClick = onDelete),
                )
            }
        }
    }
}

@Composable
private fun EditedMessagesList(
    messages: List<WhatsAppMessageEntity>,
    decodeHistory: (String?) -> List<WhatsAppEditVersion>,
    onCopy: (String) -> Unit,
    onDeleteEntry: (WhatsAppMessageEntity) -> Unit,
    onRunDemo: () -> Unit,
) {
    if (messages.isEmpty()) {
        EmptyVaultPlaceholder(
            icon = Icons.Default.EditNote,
            title = "Noch keine bearbeiteten Nachrichten",
            subtitle = "Wenn ein Kontakt eine gesendete WhatsApp-Nachricht nachträglich ändert, speichert Hikari hier „Vorher“ und „Nachher“ (automatische Löschung nach 7 Tagen).",
            onRunDemo = onRunDemo,
        )
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(messages, key = { it.id }) { msg ->
            EditedMessageCard(
                msg = msg,
                versions = decodeHistory(msg.editHistoryJson),
                onCopy = onCopy,
                onDelete = { onDeleteEntry(msg) },
            )
        }
    }
}

@Composable
private fun EditedMessageCard(
    msg: WhatsAppMessageEntity,
    versions: List<WhatsAppEditVersion>,
    onCopy: (String) -> Unit,
    onDelete: () -> Unit,
) {
    val beforeText = msg.originalText ?: versions.firstOrNull()?.text ?: msg.text
    val remainingDays = remainingRetentionDays(msg.editedAt ?: msg.timestamp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HikariCardBg)
            .border(0.8.dp, HikariAmber.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ChatAvatarBubble(
                avatarPath = msg.avatarPath,
                title = msg.chatTitle,
                isGroup = msg.isGroup,
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = msg.chatTitle,
                    color = HikariText,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (msg.isGroup && msg.senderName != msg.chatTitle) {
                    Text(
                        text = "Absender: ${msg.senderName}",
                        color = WhatsAppGreen,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(HikariAmberSoft)
                    .border(0.5.dp, HikariAmber, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(
                    text = "BEARBEITET",
                    color = HikariAmber,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.8.sp,
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(11.dp))
                .background(DeletedRedSoft.copy(alpha = 0.12f))
                .border(0.5.dp, HikariDanger.copy(alpha = 0.35f), RoundedCornerShape(11.dp))
                .padding(10.dp),
        ) {
            Column {
                Text(
                    text = "VORHER (ORIGINAL)",
                    color = HikariDanger,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = beforeText,
                    color = HikariTextMuted,
                    fontSize = 13.5.sp,
                    textDecoration = TextDecoration.LineThrough,
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(11.dp))
                .background(WhatsAppGreenSoft.copy(alpha = 0.14f))
                .border(0.5.dp, WhatsAppGreen.copy(alpha = 0.45f), RoundedCornerShape(11.dp))
                .padding(10.dp),
        ) {
            Column {
                Text(
                    text = "NACHHER (BEARBEITET)",
                    color = WhatsAppGreen,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = msg.text,
                    color = HikariText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = buildString {
                    append(formatTimestamp(msg.timestamp))
                    msg.editedAt?.let { append(" · Bearb. ${formatTimeOnly(it)}") }
                    append(" · Auto-Del in ${remainingDays}d")
                },
                color = HikariTextFaint,
                fontSize = 10.5.sp,
                fontFamily = FontFamily.Monospace,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = "Original kopieren",
                    tint = HikariTextMuted,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable { onCopy("$beforeText -> ${msg.text}") },
                )
                Icon(
                    Icons.Default.DeleteOutline,
                    contentDescription = "Eintrag entfernen",
                    tint = HikariTextFaint,
                    modifier = Modifier
                        .size(16.dp)
                        .clickable(onClick = onDelete),
                )
            }
        }
    }
}

@Composable
private fun ChatAvatarBubble(
    avatarPath: String?,
    title: String,
    isGroup: Boolean,
    size: Int = 40,
) {
    val avatarFile = remember(avatarPath) {
        avatarPath?.let(::File)?.takeIf { it.exists() }
    }
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(WhatsAppGreenDark)
            .border(1.dp, WhatsAppGreen.copy(alpha = 0.5f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (avatarFile != null) {
            AsyncImage(
                model = avatarFile,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
            )
        } else if (isGroup) {
            Icon(
                Icons.Default.Group,
                contentDescription = null,
                tint = WhatsAppGreen,
                modifier = Modifier.size((size * 0.5f).dp),
            )
        } else {
            Text(
                text = (title.firstOrNull()?.uppercaseChar() ?: 'W').toString(),
                color = WhatsAppGreen,
                fontSize = (size * 0.42f).sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@Composable
private fun EmptyVaultPlaceholder(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onRunDemo: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(HikariCardBg)
                .border(0.5.dp, HikariBorderStrong, RoundedCornerShape(20.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = WhatsAppGreen,
                modifier = Modifier.size(30.dp),
            )
        }
        Spacer(Modifier.height(14.dp))
        Text(
            text = title,
            color = HikariText,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = subtitle,
            color = HikariTextMuted,
            fontSize = 12.5.sp,
            lineHeight = 18.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(HikariSurfaceHigh)
                .border(0.5.dp, HikariAmber.copy(alpha = 0.6f), RoundedCornerShape(12.dp))
                .clickable(onClick = onRunDemo)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint = HikariAmber,
                modifier = Modifier.size(15.dp),
            )
            Text(
                text = "Demo-Szenario jetzt vorführen",
                color = HikariAmber,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HowItWorksBottomSheet(
    hasAccess: Boolean,
    onOpenPermissionSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HikariSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "So funktioniert der WhatsApp-Wächter",
                color = HikariText,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )

            InfoStepRow(
                step = "1",
                title = "Nur gelöschte & bearbeitete Nachrichten werden gespeichert",
                desc = "Normale Nachrichten landen NICHT in der Datenbank. Sie werden nur kurzzeitig im flüchtigen Arbeitsspeicher (RAM) vorgehalten, falls der Absender sie kurz darauf für alle löscht oder bearbeitet.",
            )
            InfoStepRow(
                step = "2",
                title = "Erkennung gelöschter & bearbeiteter Nachrichten",
                desc = "Meldet WhatsApp „🚫 Diese Nachricht wurde gelöscht“ oder ändert sich der Nachrichtentext im Benachrichtigungs-Stack, sichert Hikari genau diese Nachricht im Tresor.",
            )
            InfoStepRow(
                step = "3",
                title = "Automatische Löschung nach 7 Tagen",
                desc = "Alle geretteten Nachrichten und zugehörigen Medien-Vorschauen werden nach 7 Tagen vollautomatisch aus der Datenbank und vom Speicher gelöscht, damit dein Speicher immer frei bleibt.",
            )

            Spacer(Modifier.height(4.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (hasAccess) HikariSurfaceHigh else WhatsAppGreen)
                    .clickable(onClick = onOpenPermissionSettings)
                    .padding(vertical = 11.dp),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = if (hasAccess) WhatsAppGreen else Color.Black,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = if (hasAccess) {
                            "Benachrichtigungszugriff ist aktiv (Einstellungen öffnen)"
                        } else {
                            "Benachrichtigungszugriff jetzt aktivieren"
                        },
                        color = if (hasAccess) HikariText else Color.Black,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun InfoStepRow(step: String, title: String, desc: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(CircleShape)
                .background(WhatsAppGreenSoft)
                .border(0.5.dp, WhatsAppGreen, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = step,
                color = WhatsAppGreen,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                color = HikariText,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = desc,
                color = HikariTextMuted,
                fontSize = 12.sp,
                lineHeight = 16.sp,
            )
        }
    }
}

private fun remainingRetentionDays(eventEpochMs: Long): Int {
    val ageMs = (System.currentTimeMillis() - eventEpochMs).coerceAtLeast(0L)
    val leftMs = (WhatsAppRepository.RETENTION_MS - ageMs).coerceAtLeast(0L)
    val days = (leftMs / (24L * 60L * 60L * 1000L)).toInt()
    return (days + 1).coerceIn(1, WhatsAppRepository.RETENTION_DAYS)
}

private fun formatTimestamp(epochMs: Long): String {
    val sdf = SimpleDateFormat("dd.MM. HH:mm", Locale.GERMANY)
    return sdf.format(Date(epochMs))
}

private fun formatTimeOnly(epochMs: Long): String {
    val sdf = SimpleDateFormat("HH:mm", Locale.GERMANY)
    return sdf.format(Date(epochMs))
}

private fun isAudioFile(file: File): Boolean {
    val lower = file.name.lowercase()
    return lower.endsWith(".opus") ||
        lower.endsWith(".ogg") ||
        lower.endsWith(".mp3") ||
        lower.endsWith(".m4a") ||
        lower.endsWith(".aac") ||
        lower.endsWith(".wav")
}

@Composable
private fun VoiceNotePlayerBar(audioFile: File) {
    var isPlaying by remember { mutableStateOf(false) }
    var speed by remember { mutableStateOf(1.0f) }
    val playerRef = remember { mutableStateOf<MediaPlayer?>(null) }

    DisposableEffect(audioFile.absolutePath) {
        onDispose {
            playerRef.value?.runCatching {
                stop()
                release()
            }
            playerRef.value = null
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(WhatsAppGreenSoft)
            .border(0.5.dp, WhatsAppGreen.copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(WhatsAppGreen)
                .clickable {
                    val current = playerRef.value
                    if (isPlaying && current != null) {
                        runCatching { current.pause() }
                        isPlaying = false
                    } else {
                        runCatching {
                            val mp = current ?: MediaPlayer().apply {
                                setDataSource(audioFile.absolutePath)
                                prepare()
                                setOnCompletionListener { isPlaying = false }
                                playerRef.value = this
                            }
                            mp.playbackParams = PlaybackParams().setSpeed(speed)
                            mp.start()
                            isPlaying = true
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = "Abspielen",
                tint = Color.Black,
                modifier = Modifier.size(18.dp),
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Gesicherte Sprachnachricht",
                color = HikariText,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = audioFile.name,
                color = HikariTextMuted,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(7.dp))
                .background(HikariCardBg)
                .border(0.5.dp, WhatsAppGreen, RoundedCornerShape(7.dp))
                .clickable {
                    speed = when (speed) {
                        1.0f -> 1.5f
                        1.5f -> 2.0f
                        else -> 1.0f
                    }
                    playerRef.value?.let { mp ->
                        if (isPlaying) {
                            runCatching { mp.playbackParams = PlaybackParams().setSpeed(speed) }
                        }
                    }
                }
                .padding(horizontal = 7.dp, vertical = 3.dp),
        ) {
            Text(
                text = "${speed}x",
                color = WhatsAppGreen,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}
