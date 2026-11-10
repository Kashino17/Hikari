package com.hikari.app.ui.whatsapp

import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.NotificationsActive
import androidx.compose.material.icons.outlined.PlayCircleOutline
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.hikari.app.domain.whatsapp.MediaFolderState
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariBorderStrong
import com.hikari.app.ui.theme.HikariSurface
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted
import com.hikari.app.ui.voice.VoiceNotesTab
import com.hikari.app.ui.voice.VoiceViolet
import com.hikari.app.whatsapp.WhatsAppNotificationListenerService

// Farbsystem der WhatsApp-Ecke: Grün = Wächter/Bereich, Rosé = gelöscht, Himmelblau = bearbeitet.
internal val WaGreen = Color(0xFF25D366)
internal val WaGreenSoft = Color(0x1F25D366)
internal val WaDeleted = Color(0xFFFB7185)
internal val WaDeletedSoft = Color(0x1FFB7185)
internal val WaEdited = Color(0xFF38BDF8)
internal val WaEditedSoft = Color(0x1F38BDF8)
private val ToolsTeal = Color(0xFF2DD4BF)

enum class WhatsAppTab {
    GUARD,
    VOICE,
    TOOLS,
}

@Composable
fun WhatsAppScreen(
    onBack: () -> Unit,
    onOpenVoiceJob: (String) -> Unit = {},
    initialTab: WhatsAppTab = WhatsAppTab.GUARD,
    vm: WhatsAppViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val messages by vm.messages.collectAsState()
    val alertsEnabled by vm.alertsEnabled.collectAsState()
    val searchQuery by vm.searchQuery.collectAsState()
    val chatAnalysis by vm.chatAnalysis.collectAsState()
    val mediaFolderState by vm.mediaFolderState.collectAsState()
    val hasMediaAccess = mediaFolderState == MediaFolderState.READY
    val mediaFolderWrong = mediaFolderState == MediaFolderState.WRONG_FOLDER

    // Saveable: nach dem Zurück aus einer Zusammenfassung bleibt der Tab offen.
    var activeTab by rememberSaveable { mutableStateOf(initialTab) }
    var showSettings by remember { mutableStateOf(false) }
    var hasNotificationAccess by remember {
        mutableStateOf(WhatsAppNotificationListenerService.isNotificationAccessGranted(context))
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    hasNotificationAccess =
                        WhatsAppNotificationListenerService.isNotificationAccessGranted(context)
                    WhatsAppNotificationListenerService.ensureServiceBound(context)
                    vm.refreshMediaFolderAccess()
                    vm.pruneNow()
                }
                Lifecycle.Event.ON_STOP -> vm.markAllSeen()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            vm.markAllSeen()
        }
    }

    val openNotificationAccess = {
        val intent = Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
        Unit
    }
    val mediaFolderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri != null) {
            vm.onMediaFolderGranted(uri)
            Toast.makeText(context, "Medienordner freigegeben", Toast.LENGTH_SHORT).show()
        }
    }
    val openMediaFolderPicker = {
        runCatching { mediaFolderPicker.launch(vm.buildInitialMediaTreeUri()) }
        Unit
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(HikariBg),
    ) {
        WhatsAppHeader(
            guardActive = hasNotificationAccess,
            onBack = onBack,
            onOpenSettings = { showSettings = true },
        )

        WhatsAppSegments(
            activeTab = activeTab,
            unseenCount = messages.orEmpty().count { !it.isReadInHikari },
            onSelect = { activeTab = it },
        )

        Box(modifier = Modifier.fillMaxSize()) {
            when (activeTab) {
                WhatsAppTab.GUARD -> WhatsAppGuardTab(
                    messages = messages,
                    hasNotificationAccess = hasNotificationAccess,
                    hasMediaAccess = hasMediaAccess,
                    mediaFolderWrong = mediaFolderWrong,
                    searchQuery = searchQuery,
                    onSearchQueryChange = vm::setSearchQuery,
                    decodeHistory = vm::decodeEditHistory,
                    onGrantNotificationAccess = openNotificationAccess,
                    onGrantMediaAccess = openMediaFolderPicker,
                    onDeleteEntry = { vm.deleteMessage(it.id) },
                    onRunDemo = { vm.runDemoScenario(context) },
                )

                WhatsAppTab.VOICE -> VoiceNotesTab(onOpenJob = onOpenVoiceJob)

                WhatsAppTab.TOOLS -> WhatsAppToolsTab(
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
                )
            }
        }
    }

    if (showSettings) {
        GuardSettingsSheet(
            hasNotificationAccess = hasNotificationAccess,
            hasMediaAccess = hasMediaAccess,
            mediaFolderWrong = mediaFolderWrong,
            alertsEnabled = alertsEnabled,
            hasEntries = messages.orEmpty().isNotEmpty(),
            onToggleAlerts = vm::setAlertsEnabled,
            onOpenNotificationAccess = openNotificationAccess,
            onOpenMediaFolderPicker = openMediaFolderPicker,
            onRunDemo = {
                vm.runDemoScenario(context)
                showSettings = false
                activeTab = WhatsAppTab.GUARD
            },
            onClearAll = {
                vm.clearAllMessages()
                showSettings = false
                Toast.makeText(context, "Tresor geleert", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { showSettings = false },
        )
    }
}

@Composable
private fun WhatsAppHeader(
    guardActive: Boolean,
    onBack: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RoundIconButton(
            icon = Icons.AutoMirrored.Filled.ArrowBack,
            description = "Zurück",
            onClick = onBack,
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "WhatsApp",
                color = HikariText,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
            Spacer(Modifier.height(2.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (guardActive) WaGreen else HikariAmber),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = if (guardActive) "Wächter aktiv" else "Wächter braucht Zugriff",
                    color = HikariTextMuted,
                    fontSize = 12.5.sp,
                )
            }
        }
        RoundIconButton(
            icon = Icons.Outlined.Tune,
            description = "Wächter einstellen",
            onClick = onOpenSettings,
        )
    }
}

@Composable
private fun RoundIconButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(HikariSurface)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = HikariText, modifier = Modifier.size(20.dp))
    }
}

/** Die drei Bereiche als eine zusammenhängende Leiste. */
@Composable
private fun WhatsAppSegments(
    activeTab: WhatsAppTab,
    unseenCount: Int,
    onSelect: (WhatsAppTab) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(HikariSurface)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Segment(
            label = "Wächter",
            badge = unseenCount.takeIf { it > 0 && activeTab != WhatsAppTab.GUARD },
            selected = activeTab == WhatsAppTab.GUARD,
            accent = WaGreen,
            modifier = Modifier.weight(1f),
            onClick = { onSelect(WhatsAppTab.GUARD) },
        )
        Segment(
            label = "Sprachnachrichten",
            badge = null,
            selected = activeTab == WhatsAppTab.VOICE,
            accent = VoiceViolet,
            modifier = Modifier.weight(1.5f),
            onClick = { onSelect(WhatsAppTab.VOICE) },
        )
        Segment(
            label = "Tools",
            badge = null,
            selected = activeTab == WhatsAppTab.TOOLS,
            accent = ToolsTeal,
            modifier = Modifier.weight(0.8f),
            onClick = { onSelect(WhatsAppTab.TOOLS) },
        )
    }
}

@Composable
private fun Segment(
    label: String,
    badge: Int?,
    selected: Boolean,
    accent: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val background by animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = 0.16f) else Color.Transparent,
        animationSpec = tween(180),
        label = "wa-segment-bg",
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) accent else HikariTextMuted,
        animationSpec = tween(180),
        label = "wa-segment-text",
    )
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp, horizontal = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = textColor,
            fontSize = 13.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (badge != null) {
            Spacer(Modifier.width(6.dp))
            Box(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(WaGreen)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            ) {
                Text(
                    text = if (badge > 99) "99+" else badge.toString(),
                    color = Color.Black,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GuardSettingsSheet(
    hasNotificationAccess: Boolean,
    hasMediaAccess: Boolean,
    mediaFolderWrong: Boolean,
    alertsEnabled: Boolean,
    hasEntries: Boolean,
    onToggleAlerts: (Boolean) -> Unit,
    onOpenNotificationAccess: () -> Unit,
    onOpenMediaFolderPicker: () -> Unit,
    onRunDemo: () -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmClear by remember { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = HikariSurface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, bottom = 28.dp),
        ) {
            Text("Wächter", color = HikariText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "Hält fest, was in WhatsApp für alle gelöscht oder nachträglich geändert wird.",
                color = HikariTextMuted,
                fontSize = 13.sp,
                lineHeight = 18.sp,
            )

            Spacer(Modifier.height(20.dp))
            SheetGroup {
                SheetRow(
                    icon = Icons.Outlined.NotificationsActive,
                    accent = WaGreen,
                    title = "Hinweis bei Fund",
                    subtitle = "Benachrichtigung, sobald etwas gerettet wurde",
                    trailing = {
                        Switch(
                            checked = alertsEnabled,
                            onCheckedChange = onToggleAlerts,
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = HikariBg,
                                checkedTrackColor = WaGreen,
                                checkedBorderColor = WaGreen,
                                uncheckedThumbColor = HikariTextMuted,
                                uncheckedTrackColor = HikariSurface,
                                uncheckedBorderColor = HikariBorderStrong,
                            ),
                        )
                    },
                )
                SheetDivider()
                SheetRow(
                    icon = Icons.Outlined.Shield,
                    accent = if (hasNotificationAccess) WaGreen else HikariAmber,
                    title = "Benachrichtigungszugriff",
                    subtitle = if (hasNotificationAccess) "Erteilt" else "Fehlt — ohne ihn sieht der Wächter nichts",
                    onClick = onOpenNotificationAccess,
                )
                SheetDivider()
                SheetRow(
                    icon = Icons.Outlined.FolderOpen,
                    accent = if (hasMediaAccess) WaGreen else HikariAmber,
                    title = "Medienordner",
                    subtitle = when {
                        hasMediaAccess -> "Freigegeben — Fotos, Videos und Sprachnachrichten werden gesichert"
                        mediaFolderWrong -> "Falscher Ordner freigegeben — bitte WhatsApps Ordner „Media“ wählen"
                        else -> "Nicht freigegeben — gelöschte Fotos lassen sich so nicht retten"
                    },
                    onClick = onOpenMediaFolderPicker,
                )
            }

            Spacer(Modifier.height(24.dp))
            Text("So arbeitet der Wächter", color = HikariText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(10.dp))
            ExplainLine(
                "Er liest die WhatsApp-Benachrichtigungen mit. Jede Nachricht hat dort Absender und Uhrzeit — daran erkennt er sie wieder.",
            )
            ExplainLine(
                "Gelöscht gilt nur, was sich belegen lässt: WhatsApp zeigt an der Stelle „Diese Nachricht wurde gelöscht“, oder die Nachricht fehlt, obwohl ältere ungelesene noch da sind.",
            )
            ExplainLine(
                "Bearbeitet gilt nur, wenn der Text einer noch ungelesenen Nachricht an Ort und Stelle ersetzt wird. Ein zweites Foto ist keine Bearbeitung.",
            )
            ExplainLine(
                "Grenzen: Ist der Chat gerade offen oder stummgeschaltet, kommt keine Benachrichtigung — dann gibt es nichts zu retten. Verschwindet die einzige ungelesene Nachricht samt Benachrichtigung, lässt sich das nicht von „gelesen“ unterscheiden und wird nicht gezählt.",
            )
            ExplainLine(
                "Normale Nachrichten bleiben unsichtbar und werden nach 24 Stunden verworfen, Gerettetes nach 7 Tagen.",
            )

            Spacer(Modifier.height(20.dp))
            SheetGroup {
                SheetRow(
                    icon = Icons.Outlined.PlayCircleOutline,
                    accent = HikariTextMuted,
                    title = "Beispiel einspielen",
                    subtitle = "Zeigt eine gelöschte Nachricht, eine bearbeitete und ein gelöschtes Foto",
                    onClick = onRunDemo,
                )
                if (hasEntries) {
                    SheetDivider()
                    SheetRow(
                        icon = Icons.Outlined.DeleteSweep,
                        accent = WaDeleted,
                        title = if (confirmClear) "Wirklich alles löschen?" else "Tresor leeren",
                        subtitle = if (confirmClear) {
                            "Noch einmal tippen — das lässt sich nicht rückgängig machen"
                        } else {
                            "Entfernt alle geretteten Nachrichten und Medien"
                        },
                        titleColor = WaDeleted,
                        onClick = { if (confirmClear) onClearAll() else confirmClear = true },
                    )
                }
            }
        }
    }
}

@Composable
private fun SheetGroup(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(HikariSurfaceHigh.copy(alpha = 0.55f)),
    ) {
        content()
    }
}

@Composable
private fun SheetDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 62.dp)
            .height(0.5.dp)
            .background(HikariBorderStrong),
    )
}

@Composable
private fun SheetRow(
    icon: ImageVector,
    accent: Color,
    title: String,
    subtitle: String,
    titleColor: Color = HikariText,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = titleColor, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, color = HikariTextMuted, fontSize = 12.5.sp, lineHeight = 17.sp)
        }
        Spacer(Modifier.width(10.dp))
        when {
            trailing != null -> trailing()
            onClick != null -> Icon(
                Icons.AutoMirrored.Outlined.KeyboardArrowRight,
                contentDescription = null,
                tint = HikariTextFaint,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

@Composable
private fun ExplainLine(text: String) {
    Row(modifier = Modifier.padding(bottom = 10.dp)) {
        Box(
            modifier = Modifier
                .padding(top = 7.dp)
                .size(5.dp)
                .clip(CircleShape)
                .background(WaGreen),
        )
        Spacer(Modifier.width(12.dp))
        Text(text, color = HikariTextMuted, fontSize = 13.sp, lineHeight = 19.sp)
    }
}
