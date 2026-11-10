package com.hikari.app.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Dns
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hikari.app.BuildConfig
import com.hikari.app.data.db.LocalDownloadEntity
import com.hikari.app.data.sponsor.SegmentBehavior
import com.hikari.app.data.sponsor.SegmentCategories
import com.hikari.app.data.sponsor.SegmentCategory
import com.hikari.app.domain.download.DownloadRetentionPolicy
import com.hikari.app.domain.download.RETENTION_DAY_CHOICES
import com.hikari.app.domain.download.retentionLabel
import com.hikari.app.domain.update.UpdateState
import com.hikari.app.ui.profile.formatBytes
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariBorder
import com.hikari.app.ui.theme.HikariBorderStrong
import com.hikari.app.ui.theme.HikariDanger
import com.hikari.app.ui.theme.HikariSurface
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted

// Jeder Bereich hat seine eigene Farbe — auf der Übersicht, im Kopf der
// Unterseite und bei allem, was dort aktiv ist.
private val PlaybackColor = Color(0xFFA78BFA)  // Violett
private val DownloadsColor = Color(0xFF38BDF8) // Himmelblau
private val ServerColor = Color(0xFF34D399)    // Smaragd
private val AppColor = HikariAmber             // Amber
private val SmartColor = HikariAmber           // Smart-Downloads tragen den Blitz in Amber

private enum class Page(val title: String) {
    Hub("Einstellungen"),
    Playback("Wiedergabe"),
    Downloads("Downloads"),
    Server("Server"),
    App("App & Updates"),
}

/**
 * Einstellungen als Übersicht mit vier Bereichen statt einer langen Liste.
 * Die Unterseiten leben im selben Bildschirm — Zurück führt erst zur Übersicht,
 * dann aus den Einstellungen heraus.
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    var page by rememberSaveable { mutableStateOf(Page.Hub) }
    BackHandler(enabled = page != Page.Hub) { page = Page.Hub }

    Column(
        Modifier
            .fillMaxSize()
            .background(HikariBg)
            .windowInsetsPadding(WindowInsets.statusBars),
    ) {
        TopBar(
            title = page.title,
            onBack = { if (page == Page.Hub) onBack() else page = Page.Hub },
        )
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                // Unterseiten schieben sich von rechts herein, die Übersicht kommt von links zurück.
                val forward = targetState != Page.Hub
                (slideInHorizontally(tween(220)) { if (forward) it / 4 else -it / 4 } + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(tween(180)) { if (forward) -it / 4 else it / 4 } + fadeOut(tween(120)))
            },
            label = "settingsPage",
        ) { current ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .windowInsetsPadding(WindowInsets.navigationBars),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Spacer(Modifier.height(4.dp))
                when (current) {
                    Page.Hub -> HubPage(vm, onOpen = { page = it })
                    Page.Playback -> PlaybackPage(vm)
                    Page.Downloads -> DownloadsPage(vm)
                    Page.Server -> ServerPage(vm)
                    Page.App -> AppPage(vm)
                }
                Spacer(Modifier.height(32.dp))
            }
        }
    }
}

@Composable
private fun TopBar(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Zurück", tint = HikariText, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(4.dp))
        Text(title, color = HikariText, fontSize = 22.sp, fontWeight = FontWeight.Bold)
    }
}

// ── Übersicht ────────────────────────────────────────────────────────────────

@Composable
private fun HubPage(vm: SettingsViewModel, onOpen: (Page) -> Unit) {
    val behaviors by vm.sbBehaviors.collectAsState(initial = emptyMap())
    val smart by vm.smartDownloads.collectAsState(initial = true)
    val device by vm.deviceDownloads.collectAsState()
    val server by vm.server.collectAsState()
    val backendUrl by vm.backendUrl.collectAsState(initial = "")
    val update by vm.updateState.collectAsState()

    // Steht ein Update bereit, ist das die wichtigste Nachricht der Seite.
    if (update.concernsUpdate()) {
        UpdateHero(update, onAction = vm::startUpdate)
    }

    Card {
        HubRow(
            icon = Icons.Outlined.SkipNext,
            color = PlaybackColor,
            title = Page.Playback.title,
            summary = run {
                val auto = behaviors.values.count { it == SegmentBehavior.SKIP_AUTO }
                val manual = behaviors.values.count { it == SegmentBehavior.SKIP_MANUAL }
                "Überspringen: $auto automatisch · $manual per Knopf"
            },
            onClick = { onOpen(Page.Playback) },
        )
        RowDivider()
        HubRow(
            icon = Icons.Outlined.FileDownload,
            color = DownloadsColor,
            title = Page.Downloads.title,
            summary = buildString {
                append(if (smart) "Smart an" else "Smart aus")
                append(" · ")
                append(if (device.count == 0) "nichts auf dem Gerät" else "${device.count} Videos · ${formatBytes(device.totalBytes)}")
            },
            onClick = { onOpen(Page.Downloads) },
        )
        RowDivider()
        HubRow(
            icon = Icons.Outlined.Dns,
            color = ServerColor,
            title = Page.Server.title,
            summary = when (server) {
                is ServerStatus.Connected -> "Verbunden · ${hostOf(backendUrl)}"
                ServerStatus.Checking -> "Verbindung wird geprüft …"
                ServerStatus.Unreachable -> "Nicht erreichbar"
            },
            summaryColor = if (server is ServerStatus.Unreachable) HikariDanger else HikariTextMuted,
            onClick = { onOpen(Page.Server) },
        )
        RowDivider()
        HubRow(
            icon = Icons.Outlined.SystemUpdate,
            color = AppColor,
            title = Page.App.title,
            summary = "Version ${BuildConfig.VERSION_NAME}",
            onClick = { onOpen(Page.App) },
        )
    }
}

@Composable
private fun HubRow(
    icon: ImageVector,
    color: Color,
    title: String,
    summary: String,
    onClick: () -> Unit,
    summaryColor: Color = HikariTextMuted,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconTile(icon, color)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = HikariText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(summary, color = summaryColor, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null, tint = HikariTextFaint, modifier = Modifier.size(22.dp))
    }
}

// ── Wiedergabe ───────────────────────────────────────────────────────────────

private val segmentGroups = listOf(
    "Werbung" to listOf("sponsor", "selfpromo", "interaction"),
    "Anfang und Ende" to listOf("intro", "outro", "preview", "hook"),
    "Sonstiges" to listOf("filler", "music_offtopic"),
)

@Composable
private fun PlaybackPage(vm: SettingsViewModel) {
    val behaviors by vm.sbBehaviors.collectAsState(initial = emptyMap())
    val skippedCount by vm.skippedCount.collectAsState(initial = 0L)
    val skippedMs by vm.skippedMs.collectAsState(initial = 0L)

    Hero(PlaybackColor) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f)) {
                HeroLabel("Gesparte Zeit")
                Text(formatSaved(skippedMs), color = PlaybackColor, fontSize = 34.sp, fontWeight = FontWeight.Bold)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("$skippedCount", color = HikariText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Text(if (skippedCount == 1L) "Abschnitt übersprungen" else "Abschnitte übersprungen", color = HikariTextMuted, fontSize = 12.sp)
            }
        }
    }

    Text(
        "Abschnitte in YouTube-Videos, die die Community markiert hat. " +
            "Auto überspringt sofort, Knopf blendet »Überspringen« ein, Aus lässt alles laufen.",
        color = HikariTextMuted,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        modifier = Modifier.padding(horizontal = 4.dp),
    )

    segmentGroups.forEach { (groupTitle, keys) ->
        GroupLabel(groupTitle)
        Card {
            keys.mapNotNull(SegmentCategories::byKey).forEachIndexed { index, category ->
                if (index > 0) RowDivider(startInset = 14.dp)
                SegmentRow(
                    category = category,
                    current = behaviors[category.apiKey] ?: category.defaultBehavior,
                    onPick = { vm.setSbBehavior(category.apiKey, it) },
                )
            }
        }
    }

    Text(
        "Markierte Abschnitte erscheinen in ihrer Farbe auf der Fortschrittsleiste. " +
            "Nach einem automatischen Sprung lässt er sich im Player rückgängig machen.",
        color = HikariTextFaint,
        fontSize = 12.sp,
        lineHeight = 17.sp,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun SegmentRow(category: SegmentCategory, current: SegmentBehavior, onPick: (SegmentBehavior) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(category.color))
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(category.label, color = HikariText, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(category.description, color = HikariTextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(8.dp))
        Row(
            modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(HikariBg).padding(2.dp),
        ) {
            BehaviorCell("Auto", active = current == SegmentBehavior.SKIP_AUTO, fill = PlaybackColor, text = Color.Black) {
                onPick(SegmentBehavior.SKIP_AUTO)
            }
            BehaviorCell("Knopf", active = current == SegmentBehavior.SKIP_MANUAL, fill = PlaybackColor.copy(alpha = 0.22f), text = PlaybackColor) {
                onPick(SegmentBehavior.SKIP_MANUAL)
            }
            BehaviorCell("Aus", active = current == SegmentBehavior.IGNORE, fill = HikariSurfaceHigh, text = HikariText) {
                onPick(SegmentBehavior.IGNORE)
            }
        }
    }
}

/** Je kräftiger die Fläche, desto stärker greift die App ein: Auto voll, Knopf getönt, Aus grau. */
@Composable
private fun BehaviorCell(label: String, active: Boolean, fill: Color, text: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(48.dp)
            .height(32.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) fill else Color.Transparent)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (active) text else HikariTextFaint,
            fontSize = 12.sp,
            fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

// ── Downloads ────────────────────────────────────────────────────────────────

@Composable
private fun DownloadsPage(vm: SettingsViewModel) {
    val smart by vm.smartDownloads.collectAsState(initial = true)
    val manualDays by vm.manualRetentionDays.collectAsState(initial = 0)
    val smartDays by vm.smartRetentionDays.collectAsState(initial = 30)
    val device by vm.deviceDownloads.collectAsState()
    val server by vm.server.collectAsState()

    Hero(DownloadsColor) {
        HeroLabel("Videos auf diesem Gerät")
        Text(
            if (device.count == 0) "Noch keine" else formatBytes(device.totalBytes),
            color = DownloadsColor,
            fontSize = 34.sp,
            fontWeight = FontWeight.Bold,
        )
        if (device.count > 0) {
            Spacer(Modifier.height(10.dp))
            SplitBar(device.manualBytes, device.smartBytes)
            Spacer(Modifier.height(10.dp))
            Row {
                Legend(DownloadsColor, "Eigene", device.manual.size, device.manualBytes, Modifier.weight(1f))
                Legend(SmartColor, "Smart", device.smart.size, device.smartBytes, Modifier.weight(1f))
            }
        }
    }

    Card(padded = true) {
        CardHeader(Icons.Outlined.FileDownload, DownloadsColor, "Eigene Downloads", "Folgen, Filme und Videos, die du selbst herunterlädst.")
        RetentionPicker(
            selectedDays = manualDays,
            color = DownloadsColor,
            entities = device.manual,
            onPick = vm::setManualRetentionDays,
        )
    }

    Card(padded = true) {
        CardHeader(
            icon = Icons.Outlined.Bolt,
            color = SmartColor,
            title = "Smart-Downloads",
            description = "Hält die nächsten Folgen deiner laufenden Serien und deine Lieblingssongs bereit — nur im WLAN.",
            trailing = {
                Switch(
                    checked = smart,
                    onCheckedChange = vm::setSmartDownloads,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = HikariBg,
                        checkedTrackColor = SmartColor,
                        checkedBorderColor = SmartColor,
                        uncheckedThumbColor = HikariTextMuted,
                        uncheckedTrackColor = HikariSurface,
                        uncheckedBorderColor = HikariBorderStrong,
                    ),
                )
            },
        )
        RetentionPicker(
            selectedDays = smartDays,
            color = SmartColor,
            entities = device.smart,
            onPick = vm::setSmartRetentionDays,
        )
    }

    (server as? ServerStatus.Connected)?.disk?.let { disk ->
        Card(padded = true) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Archiv auf dem PC", color = HikariText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (disk.limit_bytes > 0) "${formatBytes(disk.total_bytes)} belegt · Limit ${formatBytes(disk.limit_bytes)}"
                        else "${formatBytes(disk.total_bytes)} belegt",
                        color = HikariTextMuted,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

/** Fünf Fristen in einer Leiste; darunter steht, was die Wahl für den Bestand bedeutet. */
@Composable
private fun RetentionPicker(
    selectedDays: Int,
    color: Color,
    entities: List<LocalDownloadEntity>,
    onPick: (Int) -> Unit,
) {
    Text("Behalten", color = HikariTextMuted, fontSize = 12.sp)
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).background(HikariBg).padding(2.dp),
    ) {
        RETENTION_DAY_CHOICES.forEach { days ->
            val active = days == selectedDays
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (active) color else Color.Transparent)
                    .clickable { onPick(days) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    if (days == 0) "Immer" else retentionLabel(days),
                    color = if (active) Color.Black else HikariTextMuted,
                    fontSize = 12.sp,
                    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                )
            }
        }
    }
    val now = remember(entities, selectedDays) { System.currentTimeMillis() }
    val expired = entities.count { DownloadRetentionPolicy.isExpired(it.downloadedAt, selectedDays, now) }
    Text(
        text = when {
            selectedDays == 0 -> "Bleiben auf dem Gerät, bis du sie löschst."
            expired > 0 -> "$expired ${if (expired == 1) "Download ist" else "Downloads sind"} schon älter und " +
                "${if (expired == 1) "wird" else "werden"} beim nächsten App-Start entfernt."
            else -> "Werden ${retentionLabel(selectedDays)} nach dem Herunterladen vom Gerät entfernt."
        },
        color = if (expired > 0 && selectedDays != 0) HikariAmber else HikariTextFaint,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )
}

@Composable
private fun SplitBar(manualBytes: Long, smartBytes: Long) {
    val total = (manualBytes + smartBytes).coerceAtLeast(1L).toFloat()
    Row(Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f))) {
        if (manualBytes > 0) Box(Modifier.weight(manualBytes / total).height(8.dp).background(DownloadsColor))
        if (smartBytes > 0) Box(Modifier.weight(smartBytes / total).height(8.dp).background(SmartColor))
    }
}

@Composable
private fun Legend(color: Color, label: String, count: Int, bytes: Long, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(8.dp))
        Text(label, color = HikariText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.width(6.dp))
        Text("$count · ${formatBytes(bytes)}", color = HikariTextMuted, fontSize = 13.sp)
    }
}

// ── Server ───────────────────────────────────────────────────────────────────

@Composable
private fun ServerPage(vm: SettingsViewModel) {
    val backendUrl by vm.backendUrl.collectAsState(initial = "")
    val authToken by vm.authToken.collectAsState(initial = "")
    val server by vm.server.collectAsState()
    val mangaSyncStatus by vm.mangaSyncStatus.collectAsState()
    var urlDraft by remember(backendUrl) { mutableStateOf(backendUrl) }
    var tokenDraft by remember(authToken) { mutableStateOf(authToken) }
    val keyboard = LocalSoftwareKeyboardController.current

    val statusColor = when (server) {
        is ServerStatus.Connected -> ServerColor
        ServerStatus.Checking -> HikariTextMuted
        ServerStatus.Unreachable -> HikariDanger
    }
    Hero(statusColor) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                HeroLabel("Verbindung")
                Text(
                    when (server) {
                        is ServerStatus.Connected -> "Verbunden"
                        ServerStatus.Checking -> "Wird geprüft …"
                        ServerStatus.Unreachable -> "Nicht erreichbar"
                    },
                    color = statusColor,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(hostOf(backendUrl), color = HikariTextMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.06f)).clickable { vm.refreshServer() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Refresh, contentDescription = "Erneut prüfen", tint = HikariText, modifier = Modifier.size(20.dp))
            }
        }
    }

    Card(padded = true) {
        FieldLabel("Server-Adresse")
        SettingField(
            value = urlDraft,
            onValueChange = { urlDraft = it },
            changed = urlDraft.trim().trimEnd('/') != backendUrl,
            onSave = { vm.setBackendUrl(urlDraft.trim()); keyboard?.hide() },
            keyboardType = KeyboardType.Uri,
        )
        FieldHint("Tailscale-Name oder IP mit Port, z. B. http://macbook-pro.taile64a95.ts.net:3939")

        Spacer(Modifier.height(6.dp))
        FieldLabel("Zugriffs-Token")
        SettingField(
            value = tokenDraft,
            onValueChange = { tokenDraft = it },
            changed = tokenDraft.trim() != authToken,
            onSave = { vm.setAuthToken(tokenDraft.trim()); keyboard?.hide() },
            keyboardType = KeyboardType.Password,
            password = true,
            placeholder = "Kein Token",
        )
        FieldHint("Nur nötig, wenn der Server ein Token verlangt.")
    }

    GroupLabel("Wartung")
    Card {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Manga-Abgleich", color = HikariText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(
                    mangaSyncStatus ?: "Holt neue Kapitel in die Manga-Bibliothek.",
                    color = if (mangaSyncStatus != null) ServerColor else HikariTextMuted,
                    fontSize = 12.sp,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                "Starten",
                color = ServerColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(ServerColor.copy(alpha = 0.14f))
                    .clickable(onClick = vm::triggerMangaSync)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

/** Eingabefeld mit eingebautem Speichern-Haken — er erscheint erst, wenn sich etwas geändert hat. */
@Composable
private fun SettingField(
    value: String,
    onValueChange: (String) -> Unit,
    changed: Boolean,
    onSave: () -> Unit,
    keyboardType: KeyboardType,
    password: Boolean = false,
    placeholder: String = "",
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(HikariBg)
            .border(1.dp, if (changed) ServerColor.copy(alpha = 0.6f) else HikariBorderStrong, RoundedCornerShape(12.dp))
            .padding(start = 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (value.isEmpty() && placeholder.isNotEmpty()) {
                Text(placeholder, color = HikariTextFaint, fontSize = 14.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = true,
                cursorBrush = SolidColor(ServerColor),
                textStyle = TextStyle(color = HikariText, fontSize = 14.sp, fontFamily = FontFamily.Monospace),
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (changed) onSave() }),
                visualTransformation = if (password) PasswordVisualTransformation() else VisualTransformation.None,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (changed) {
            Box(
                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(9.dp)).background(ServerColor).clickable(onClick = onSave),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Check, contentDescription = "Speichern", tint = Color.Black, modifier = Modifier.size(20.dp))
            }
        }
    }
}

// ── App & Updates ────────────────────────────────────────────────────────────

@Composable
private fun AppPage(vm: SettingsViewModel) {
    val update by vm.updateState.collectAsState()

    Hero(AppColor) {
        HeroLabel("Installierte Version")
        Text(BuildConfig.VERSION_NAME, color = AppColor, fontSize = 34.sp, fontWeight = FontWeight.Bold)
        Text(
            text = when (val s = update) {
                UpdateState.Idle -> "Updates kommen direkt aus dem GitHub-Release."
                UpdateState.Checking -> "Suche nach Updates …"
                UpdateState.UpToDate -> "Die App ist aktuell."
                is UpdateState.Available -> "Version ${s.version} ist verfügbar."
                is UpdateState.Downloading ->
                    if (s.percent >= 0) "Version ${s.version} wird geladen · ${s.percent} %" else "Version ${s.version} wird geladen …"
                is UpdateState.Downloaded -> "Version ${s.version} ist geladen — Installation bestätigen."
                is UpdateState.Error -> s.message
            },
            color = if (update is UpdateState.Error) HikariDanger else HikariTextMuted,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
        if (update is UpdateState.Downloading) {
            Spacer(Modifier.height(12.dp))
            ProgressLine(((update as UpdateState.Downloading).percent.coerceIn(0, 100)) / 100f, AppColor)
        } else if (update !is UpdateState.Checking) {
            Spacer(Modifier.height(14.dp))
            val wantsUpdate = update.concernsUpdate()
            PrimaryButton(
                label = when (update) {
                    is UpdateState.Available -> "Jetzt aktualisieren"
                    is UpdateState.Downloaded -> "Installieren"
                    is UpdateState.Error -> if (wantsUpdate) "Erneut versuchen" else "Nach Updates suchen"
                    else -> "Nach Updates suchen"
                },
                onClick = { if (wantsUpdate) vm.startUpdate() else vm.checkForUpdate() },
            )
        }
    }

    Text(
        "Neue Versionen melden sich von selbst oben in der App. Dort genügt ein Tipp auf »Aktualisieren« — " +
            "das Update wird geladen und die Installation startet direkt.",
        color = HikariTextMuted,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        modifier = Modifier.padding(horizontal = 4.dp),
    )
}

@Composable
private fun UpdateHero(update: UpdateState, onAction: () -> Unit) {
    Hero(AppColor) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    when (update) {
                        is UpdateState.Downloading -> "Update wird geladen"
                        is UpdateState.Downloaded -> "Update bereit"
                        is UpdateState.Error -> "Download fehlgeschlagen"
                        else -> "Update verfügbar"
                    },
                    color = HikariText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    when (update) {
                        is UpdateState.Available -> "Version ${update.version}"
                        is UpdateState.Downloading -> "Version ${update.version} · ${update.percent.coerceAtLeast(0)} %"
                        is UpdateState.Downloaded -> "Version ${update.version}"
                        is UpdateState.Error -> "Version ${update.retry?.version.orEmpty()}"
                        else -> ""
                    },
                    color = HikariTextMuted,
                    fontSize = 13.sp,
                )
            }
            if (update !is UpdateState.Downloading) {
                PrimaryButton(
                    label = when (update) {
                        is UpdateState.Downloaded -> "Installieren"
                        is UpdateState.Error -> "Erneut"
                        else -> "Aktualisieren"
                    },
                    onClick = onAction,
                )
            }
        }
        if (update is UpdateState.Downloading) {
            Spacer(Modifier.height(12.dp))
            ProgressLine(update.percent.coerceIn(0, 100) / 100f, AppColor)
        }
    }
}

/** Geht es um ein konkretes Update (gefunden, im Download, geladen oder beim Laden gescheitert)? */
private fun UpdateState.concernsUpdate(): Boolean = when (this) {
    is UpdateState.Available, is UpdateState.Downloading, is UpdateState.Downloaded -> true
    is UpdateState.Error -> retry != null
    else -> false
}

// ── Bausteine ────────────────────────────────────────────────────────────────

@Composable
private fun IconTile(icon: ImageVector, color: Color) {
    Box(
        modifier = Modifier.size(42.dp).clip(RoundedCornerShape(12.dp)).background(color),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = Color.Black, modifier = Modifier.size(22.dp))
    }
}

/** Blickfang oben auf jeder Unterseite — leicht in der Bereichsfarbe getönt. */
@Composable
private fun Hero(color: Color, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(HikariSurface)
            .background(color.copy(alpha = 0.07f))
            .border(1.dp, color.copy(alpha = 0.28f), shape)
            .padding(18.dp),
        content = content,
    )
}

@Composable
private fun HeroLabel(text: String) {
    Text(text, color = HikariTextMuted, fontSize = 13.sp)
}

@Composable
private fun Card(padded: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(HikariSurface)
            .border(1.dp, HikariBorder, shape)
            .then(if (padded) Modifier.padding(14.dp) else Modifier),
        verticalArrangement = if (padded) Arrangement.spacedBy(8.dp) else Arrangement.Top,
        content = content,
    )
}

@Composable
private fun CardHeader(
    icon: ImageVector,
    color: Color,
    title: String,
    description: String,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconTile(icon, color)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = HikariText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(description, color = HikariTextMuted, fontSize = 12.sp, lineHeight = 16.sp)
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        }
    }
    Spacer(Modifier.height(4.dp))
}

@Composable
private fun RowDivider(startInset: androidx.compose.ui.unit.Dp = 70.dp) {
    Box(Modifier.padding(start = startInset).fillMaxWidth().height(1.dp).background(HikariBorder))
}

@Composable
private fun GroupLabel(text: String) {
    Text(
        text,
        color = HikariTextMuted,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp),
    )
}

@Composable
private fun FieldLabel(text: String) {
    Text(text, color = HikariText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
}

@Composable
private fun FieldHint(text: String) {
    Text(text, color = HikariTextFaint, fontSize = 12.sp, lineHeight = 16.sp)
}

/** Primärknopf im Hikari-Stil: weiß mit schwarzer Schrift. */
@Composable
private fun PrimaryButton(label: String, onClick: () -> Unit) {
    Text(
        label,
        color = Color.Black,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 10.dp),
    )
}

@Composable
private fun ProgressLine(fraction: Float, color: Color) {
    Box(Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)).background(Color.White.copy(alpha = 0.08f))) {
        Box(Modifier.fillMaxWidth(fraction.coerceIn(0f, 1f)).height(6.dp).background(color))
    }
}

/** „http://macbook-pro.tail….ts.net:3939" → „macbook-pro.tail….ts.net" */
private fun hostOf(url: String): String =
    url.substringAfter("://").substringBefore('/').substringBefore(':').ifBlank { "—" }

/** Gesparte Zeit in lesbarer Form: „0 min", „14 min", „1 h 12 min". */
private fun formatSaved(ms: Long): String {
    val minutes = ms / 60_000
    return if (minutes >= 60) "${minutes / 60} h ${minutes % 60} min" else "$minutes min"
}
