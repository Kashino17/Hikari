package com.hikari.app.ui.whatsapp

import android.content.Context
import android.content.Intent
import android.media.MediaPlayer
import android.media.PlaybackParams
import android.webkit.MimeTypeMap
import android.widget.MediaController
import android.widget.VideoView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FolderOpen
import androidx.compose.material.icons.outlined.Group
import androidx.compose.material.icons.outlined.HideImage
import androidx.compose.material.icons.automirrored.outlined.InsertDriveFile
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import coil.compose.AsyncImage
import com.hikari.app.data.db.WhatsAppMessageEntity
import com.hikari.app.domain.whatsapp.WhatsAppEditVersion
import com.hikari.app.domain.whatsapp.WhatsAppNotificationParser
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariBorderStrong
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariSurface
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted
import me.saket.telephoto.zoomable.coil.ZoomableAsyncImage
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private enum class GuardFilter { ALL, DELETED, EDITED, MEDIA }

/** Der Wächter-Bereich: alles Gerettete als ein Verlauf, nach Tagen gegliedert. */
@Composable
internal fun WhatsAppGuardTab(
    messages: List<WhatsAppMessageEntity>?,
    hasNotificationAccess: Boolean,
    hasMediaAccess: Boolean,
    mediaFolderWrong: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    decodeHistory: (String?) -> List<WhatsAppEditVersion>,
    onGrantNotificationAccess: () -> Unit,
    onGrantMediaAccess: () -> Unit,
    onDeleteEntry: (WhatsAppMessageEntity) -> Unit,
    onRunDemo: () -> Unit,
) {
    val context = LocalContext.current
    var filter by rememberSaveable { mutableStateOf(GuardFilter.ALL) }
    var viewer by remember { mutableStateOf<File?>(null) }

    val all = messages.orEmpty()
    val query = searchQuery.trim().lowercase()
    val visible = remember(all, filter, query) {
        all.filter { msg ->
            val matchesFilter = when (filter) {
                GuardFilter.ALL -> true
                GuardFilter.DELETED -> msg.isDeleted
                GuardFilter.EDITED -> msg.isEdited
                GuardFilter.MEDIA -> msg.mediaType != null
            }
            matchesFilter && (
                query.isEmpty() ||
                    msg.text.lowercase().contains(query) ||
                    msg.originalText?.lowercase()?.contains(query) == true ||
                    msg.chatTitle.lowercase().contains(query) ||
                    msg.senderName.lowercase().contains(query)
                )
        }
    }
    val days = remember(visible) { visible.groupBy { dayStart(it.eventTime()) }.toList() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 40.dp),
    ) {
        if (!hasNotificationAccess || !hasMediaAccess) {
            item(key = "setup") {
                SetupCard(
                    hasNotificationAccess = hasNotificationAccess,
                    hasMediaAccess = hasMediaAccess,
                    mediaFolderWrong = mediaFolderWrong,
                    onGrantNotificationAccess = onGrantNotificationAccess,
                    onGrantMediaAccess = onGrantMediaAccess,
                )
                Spacer(Modifier.height(18.dp))
            }
        }

        if (messages != null && all.isEmpty()) {
            item(key = "empty") {
                EmptyVault(guardActive = hasNotificationAccess, onRunDemo = onRunDemo)
                // Auch im leeren Tresor sichtbar machen, dass Fotos & Co. mitgesichert werden.
                if (hasNotificationAccess && hasMediaAccess) {
                    Spacer(Modifier.height(18.dp))
                    MediaStatusLine(active = true, wrongFolder = false, onGrant = onGrantMediaAccess)
                }
            }
        }

        if (all.isNotEmpty()) {
            item(key = "controls") {
                SearchAndFilter(
                    query = searchQuery,
                    onQueryChange = onSearchQueryChange,
                    filter = filter,
                    onFilterChange = { filter = it },
                    total = all.size,
                    deleted = all.count { it.isDeleted },
                    edited = all.count { it.isEdited },
                    media = all.count { it.mediaType != null },
                )
            }
            if (filter == GuardFilter.MEDIA) {
                item(key = "media-status") {
                    MediaStatusLine(
                        active = hasMediaAccess,
                        wrongFolder = mediaFolderWrong,
                        onGrant = onGrantMediaAccess,
                    )
                }
            }
            if (visible.isEmpty() && filter == GuardFilter.MEDIA && query.isEmpty()) {
                item(key = "no-media") { EmptyMedia() }
            } else if (visible.isEmpty()) {
                item(key = "no-match") {
                    Text(
                        text = "Nichts gefunden",
                        color = HikariTextMuted,
                        fontSize = 14.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 48.dp),
                    )
                }
            }
            if (filter == GuardFilter.MEDIA) {
                // Bilder und Videos als Galerie, alles andere (Sprachnachrichten, nicht Gesichertes) darunter.
                val (pictures, others) = visible.partition { msg ->
                    val file = msg.mediaPreviewPath?.let(::File)?.takeIf { it.exists() && it.length() > 0L }
                    file != null && mediaKind(file).let { it == MediaKind.IMAGE || it == MediaKind.VIDEO }
                }
                pictures.chunked(3).forEachIndexed { index, row ->
                    item(key = "gallery-$index-${row.first().id}") {
                        Row(
                            modifier = Modifier.padding(top = if (index == 0) 16.dp else 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            row.forEach { msg ->
                                GalleryTile(
                                    msg = msg,
                                    modifier = Modifier.weight(1f),
                                    onOpen = { msg.mediaPreviewPath?.let { viewer = File(it) } },
                                )
                            }
                            repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                        }
                    }
                }
                if (others.isNotEmpty()) {
                    item(key = "media-others-gap") { Spacer(Modifier.height(18.dp)) }
                }
                items(others, key = { it.id }) { msg ->
                    GuardEntryCard(
                        msg = msg,
                        versions = decodeHistory(msg.editHistoryJson),
                        hasMediaAccess = hasMediaAccess,
                        onOpenMedia = { viewer = it },
                        onCopy = { copyToClipboard(context, "Nachricht", msg.text) },
                        onDelete = { onDeleteEntry(msg) },
                    )
                    Spacer(Modifier.height(12.dp))
                }
            } else {
                days.forEach { (day, entries) ->
                    item(key = "day-$day") {
                        Text(
                            text = dayLabel(day),
                            color = HikariTextMuted,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(start = 4.dp, top = 26.dp, bottom = 10.dp),
                        )
                    }
                    items(entries, key = { it.id }) { msg ->
                        GuardEntryCard(
                            msg = msg,
                            versions = decodeHistory(msg.editHistoryJson),
                            hasMediaAccess = hasMediaAccess,
                            onOpenMedia = { viewer = it },
                            onCopy = {
                                val text = if (msg.isEdited && msg.originalText != null) {
                                    "Vorher: ${msg.originalText}\nJetzt: ${msg.text}"
                                } else {
                                    msg.text
                                }
                                copyToClipboard(context, "Nachricht", text)
                            },
                            onDelete = { onDeleteEntry(msg) },
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                }
            }
            if (visible.isNotEmpty()) {
                item(key = "footer") {
                    Text(
                        text = "Einträge verschwinden nach 7 Tagen von selbst.",
                        color = HikariTextFaint,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 18.dp),
                    )
                }
            }
        }
    }

    viewer?.let { file ->
        MediaViewerDialog(file = file, onDismiss = { viewer = null })
    }
}

// ── Einrichtung ──────────────────────────────────────────────────────────────────

@Composable
private fun SetupCard(
    hasNotificationAccess: Boolean,
    hasMediaAccess: Boolean,
    mediaFolderWrong: Boolean,
    onGrantNotificationAccess: () -> Unit,
    onGrantMediaAccess: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(WaGreenSoft)
            .padding(18.dp),
    ) {
        Text(
            text = if (!hasNotificationAccess) "Wächter einrichten" else "Gelöschte Fotos retten",
            color = HikariText,
            fontSize = 17.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(14.dp))
        // Nur zeigen, was noch fehlt.
        if (!hasNotificationAccess) {
            SetupStep(
                icon = Icons.Outlined.Shield,
                title = "Benachrichtigungen mitlesen",
                text = "Damit erkennt Hikari gelöschte und bearbeitete Nachrichten.",
                action = "Erlauben",
                onClick = onGrantNotificationAccess,
            )
        }
        if (!hasNotificationAccess && !hasMediaAccess) Spacer(Modifier.height(18.dp))
        if (!hasMediaAccess) {
            SetupStep(
                icon = Icons.Outlined.FolderOpen,
                title = if (mediaFolderWrong) "Medienordner neu freigeben" else "WhatsApp-Medienordner freigeben",
                text = if (mediaFolderWrong) {
                    "Die bisherige Freigabe zeigt nicht auf WhatsApps Ordner „Media“ — so wird nichts gesichert. Im nächsten Fenster unten auf „Diesen Ordner verwenden“ tippen."
                } else {
                    "Nur so lassen sich gelöschte Fotos, Videos und Sprachnachrichten retten. Im nächsten Fenster unten auf „Diesen Ordner verwenden“ tippen."
                },
                action = "Freigeben",
                onClick = onGrantMediaAccess,
            )
        }
    }
}

@Composable
private fun SetupStep(
    icon: ImageVector,
    title: String,
    text: String,
    action: String,
    onClick: () -> Unit,
) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(WaGreen),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = null, tint = Color.Black, modifier = Modifier.size(19.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = HikariText, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(text, color = HikariTextMuted, fontSize = 12.5.sp, lineHeight = 17.sp)
            Spacer(Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White)
                    .clickable(onClick = onClick)
                    .padding(horizontal = 18.dp, vertical = 8.dp),
            ) {
                Text(action, color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

// ── Suche + Filter als eine Einheit ──────────────────────────────────────────────

@Composable
private fun SearchAndFilter(
    query: String,
    onQueryChange: (String) -> Unit,
    filter: GuardFilter,
    onFilterChange: (GuardFilter) -> Unit,
    total: Int,
    deleted: Int,
    edited: Int,
    media: Int,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(HikariSurface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Search, contentDescription = null, tint = HikariTextFaint, modifier = Modifier.size(19.dp))
            Spacer(Modifier.width(12.dp))
            Box(Modifier.weight(1f)) {
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = HikariText, fontSize = 14.5.sp),
                    cursorBrush = SolidColor(WaGreen),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (query.isEmpty()) {
                    Text("Name oder Text suchen", color = HikariTextFaint, fontSize = 14.5.sp)
                }
            }
            if (query.isNotEmpty()) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "Suche leeren",
                    tint = HikariTextMuted,
                    modifier = Modifier
                        .size(18.dp)
                        .clickable { onQueryChange("") },
                )
            }
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(HikariBorderStrong),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            FilterSegment("Alle", total, filter == GuardFilter.ALL, HikariText, Modifier.weight(0.8f)) {
                onFilterChange(GuardFilter.ALL)
            }
            FilterSegment("Gelöscht", deleted, filter == GuardFilter.DELETED, WaDeleted, Modifier.weight(1.15f)) {
                onFilterChange(GuardFilter.DELETED)
            }
            FilterSegment("Bearbeitet", edited, filter == GuardFilter.EDITED, WaEdited, Modifier.weight(1.3f)) {
                onFilterChange(GuardFilter.EDITED)
            }
            FilterSegment("Medien", media, filter == GuardFilter.MEDIA, WaMedia, Modifier.weight(1.05f)) {
                onFilterChange(GuardFilter.MEDIA)
            }
        }
    }
}

@Composable
private fun FilterSegment(
    label: String,
    count: Int,
    selected: Boolean,
    accent: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val background by animateColorAsState(
        targetValue = if (selected) accent.copy(alpha = 0.14f) else Color.Transparent,
        animationSpec = tween(160),
        label = "wa-filter-bg",
    )
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(background)
            .clickable(onClick = onClick)
            .padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            color = if (selected) accent else HikariTextMuted,
            fontSize = 12.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            maxLines = 1,
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = count.toString(),
            color = if (selected) accent.copy(alpha = 0.75f) else HikariTextFaint,
            fontSize = 12.sp,
            maxLines = 1,
        )
    }
}

// ── Medien-Ansicht ──────────────────────────────────────────────────────────────

private val WaMedia = Color(0xFFA78BFA)

/** Sagt in einer Zeile, ob Medien gerade gesichert werden — und bietet sonst den Weg dorthin. */
@Composable
private fun MediaStatusLine(active: Boolean, wrongFolder: Boolean, onGrant: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) WaGreenSoft else HikariSurface)
            .then(if (active) Modifier else Modifier.clickable(onClick = onGrant))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(if (active) WaGreen else HikariAmber),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = when {
                    active -> "Mediensicherung läuft"
                    wrongFolder -> "Falscher Ordner freigegeben"
                    else -> "Mediensicherung ist aus"
                },
                color = HikariText,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = if (active) {
                    "Neue Fotos, Videos und Sprachnachrichten werden beim Eintreffen gesichert."
                } else {
                    "Tippen, um WhatsApps Ordner „Media“ freizugeben."
                },
                color = HikariTextMuted,
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
            )
        }
    }
}

@Composable
private fun EmptyMedia() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 44.dp, start = 20.dp, end = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Noch keine geretteten Medien", color = HikariText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Löscht jemand ein Foto, ein Video oder eine Sprachnachricht für alle, liegt die Datei hier. " +
                "Gesichert wird nur, was WhatsApp automatisch herunterlädt.",
            color = HikariTextMuted,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun GalleryTile(msg: WhatsAppMessageEntity, modifier: Modifier, onOpen: () -> Unit) {
    val file = remember(msg.mediaPreviewPath) { msg.mediaPreviewPath?.let(::File) }
    val isVideo = file != null && mediaKind(file) == MediaKind.VIDEO
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isVideo) Color.Black else HikariSurface)
            .clickable(onClick = onOpen),
    ) {
        if (isVideo) {
            Icon(
                Icons.Outlined.PlayArrow,
                contentDescription = "Video",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(34.dp),
            )
        } else {
            AsyncImage(
                model = file,
                contentDescription = "Gerettetes Foto",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Text(
            text = msg.chatTitle,
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                .padding(start = 8.dp, end = 8.dp, top = 14.dp, bottom = 6.dp),
        )
    }
}

// ── Ein geretteter Eintrag ───────────────────────────────────────────────────────

@Composable
private fun GuardEntryCard(
    msg: WhatsAppMessageEntity,
    versions: List<WhatsAppEditVersion>,
    hasMediaAccess: Boolean,
    onOpenMedia: (File) -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val mediaFile = remember(msg.mediaPreviewPath) {
        msg.mediaPreviewPath?.let(::File)?.takeIf { it.exists() && it.length() > 0L }
    }
    val bareMedia = msg.mediaType != null && WhatsAppNotificationParser.isBareMediaLabel(msg.text)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(HikariCardBg)
            .animateContentSize()
            .padding(start = 16.dp, top = 16.dp, end = 10.dp, bottom = 6.dp),
    ) {
        // Kopf: wer, wo, was ist passiert
        Row(modifier = Modifier.padding(end = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            ChatAvatar(avatarPath = msg.avatarPath, title = msg.chatTitle, isGroup = msg.isGroup)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = msg.chatTitle,
                        color = HikariText,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (!msg.isReadInHikari) {
                        Spacer(Modifier.width(8.dp))
                        Box(
                            Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(WaGreen),
                        )
                    }
                }
                val who = if (msg.isGroup && !msg.senderName.equals(msg.chatTitle, ignoreCase = true)) {
                    "${msg.senderName} · ${formatTime(msg.timestamp)}"
                } else {
                    "Gesendet ${formatTime(msg.timestamp)}"
                }
                Text(
                    text = who,
                    color = HikariTextMuted,
                    fontSize = 12.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(10.dp))
            StatusTag(msg)
        }

        Spacer(Modifier.height(14.dp))

        // Inhalt
        Column(modifier = Modifier.padding(end = 6.dp)) {
            if (msg.isEdited && msg.originalText != null) {
                EditTrail(original = msg.originalText, current = msg.text, versions = versions)
            } else if (!bareMedia) {
                Text(
                    text = msg.text,
                    color = HikariText,
                    fontSize = 15.5.sp,
                    lineHeight = 22.sp,
                )
            }

            if (mediaFile != null) {
                Spacer(Modifier.height(if (bareMedia) 0.dp else 12.dp))
                RescuedMedia(file = mediaFile, onOpen = { onOpenMedia(mediaFile) })
            } else if (msg.mediaType != null) {
                Spacer(Modifier.height(if (bareMedia) 0.dp else 12.dp))
                MissingMediaNote(mediaType = msg.mediaType, hasMediaAccess = hasMediaAccess)
            }
        }

        Spacer(Modifier.height(6.dp))

        // Fuß: wann + Aktionen in einer Zeile
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = when {
                    msg.isDeleted && msg.deletedAt != null -> "Gelöscht ${formatTime(msg.deletedAt)}"
                    msg.isEdited && msg.editedAt != null -> "Geändert ${formatTime(msg.editedAt)}"
                    else -> ""
                },
                color = HikariTextFaint,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f),
            )
            if (mediaFile != null) {
                FooterAction(Icons.Outlined.IosShare, "Teilen") { shareFile(context, mediaFile) }
            }
            if (!bareMedia) {
                FooterAction(Icons.Outlined.ContentCopy, "Text kopieren", onCopy)
            }
            FooterAction(Icons.Outlined.DeleteOutline, "Eintrag entfernen", onDelete)
        }
    }
}

@Composable
private fun StatusTag(msg: WhatsAppMessageEntity) {
    val deleted = msg.isDeleted
    val accent = if (deleted) WaDeleted else WaEdited
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (deleted) WaDeletedSoft else WaEditedSoft)
            .padding(start = 8.dp, end = 10.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            if (deleted) Icons.Outlined.DeleteOutline else Icons.Outlined.Edit,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(13.dp),
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = if (deleted) "Gelöscht" else "Bearbeitet",
            color = accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Vorher → Jetzt; frühere Zwischenfassungen klappen auf Wunsch auf. */
@Composable
private fun EditTrail(original: String, current: String, versions: List<WhatsAppEditVersion>) {
    val between = versions.drop(1).map { it.text }.filter { it != original && it != current }
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HikariSurface)
            .padding(14.dp),
    ) {
        TrailLabel("Vorher", HikariTextMuted)
        Text(
            text = original,
            color = HikariTextMuted,
            fontSize = 14.5.sp,
            lineHeight = 20.sp,
            textDecoration = TextDecoration.LineThrough,
        )
        if (between.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            if (expanded) {
                between.forEach { text ->
                    Text(
                        text = text,
                        color = HikariTextMuted,
                        fontSize = 14.5.sp,
                        lineHeight = 20.sp,
                        textDecoration = TextDecoration.LineThrough,
                        modifier = Modifier.padding(bottom = 6.dp),
                    )
                }
            } else {
                Text(
                    text = if (between.size == 1) "1 weitere Fassung zeigen" else "${between.size} weitere Fassungen zeigen",
                    color = WaEdited,
                    fontSize = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { expanded = true },
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(HikariBorderStrong),
        )
        Spacer(Modifier.height(12.dp))
        TrailLabel("Jetzt", WaEdited)
        Text(text = current, color = HikariText, fontSize = 15.5.sp, lineHeight = 22.sp)
    }
}

@Composable
private fun TrailLabel(text: String, color: Color) {
    Text(
        text = text,
        color = color,
        fontSize = 11.5.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun FooterAction(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(36.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = HikariTextMuted, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun ChatAvatar(avatarPath: String?, title: String, isGroup: Boolean) {
    val avatarFile = remember(avatarPath) { avatarPath?.let(::File)?.takeIf { it.exists() } }
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(WaGreenSoft),
        contentAlignment = Alignment.Center,
    ) {
        when {
            avatarFile != null -> AsyncImage(
                model = avatarFile,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            isGroup -> Icon(Icons.Outlined.Group, contentDescription = null, tint = WaGreen, modifier = Modifier.size(21.dp))
            else -> Text(
                text = (title.firstOrNull { it.isLetterOrDigit() }?.uppercaseChar() ?: '?').toString(),
                color = WaGreen,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}

// ── Medien ───────────────────────────────────────────────────────────────────────

private enum class MediaKind { IMAGE, VIDEO, AUDIO, OTHER }

private fun mediaKind(file: File): MediaKind = when (file.extension.lowercase()) {
    "jpg", "jpeg", "png", "webp", "gif" -> MediaKind.IMAGE
    "mp4", "3gp", "mkv", "webm", "mov" -> MediaKind.VIDEO
    "opus", "ogg", "mp3", "m4a", "aac", "wav", "amr" -> MediaKind.AUDIO
    else -> MediaKind.OTHER
}

@Composable
private fun RescuedMedia(file: File, onOpen: () -> Unit) {
    val context = LocalContext.current
    when (mediaKind(file)) {
        MediaKind.IMAGE -> AsyncImage(
            model = file,
            contentDescription = "Gerettetes Foto",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp, max = 280.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(HikariSurface)
                .clickable(onClick = onOpen),
        )

        MediaKind.VIDEO -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(150.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.Black)
                .clickable(onClick = onOpen),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color.White),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.PlayArrow, contentDescription = null, tint = Color.Black, modifier = Modifier.size(28.dp))
                }
                Spacer(Modifier.height(10.dp))
                Text("Gerettetes Video ansehen", color = Color.White.copy(alpha = 0.8f), fontSize = 12.5.sp)
            }
        }

        MediaKind.AUDIO -> VoicePlayer(audioFile = file)

        MediaKind.OTHER -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(HikariSurface)
                .clickable { shareFile(context, file) }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.AutoMirrored.Outlined.InsertDriveFile, contentDescription = null, tint = HikariTextMuted, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Text("Gerettete Datei öffnen", color = HikariText, fontSize = 14.sp)
        }
    }
}

@Composable
private fun MissingMediaNote(mediaType: String, hasMediaAccess: Boolean) {
    val label = when (mediaType) {
        "photo" -> "Foto"
        "video" -> "Video"
        "voice" -> "Sprachnachricht"
        "audio" -> "Audio"
        "gif" -> "GIF"
        "sticker" -> "Sticker"
        else -> "Datei"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HikariSurface)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.HideImage, contentDescription = null, tint = HikariTextFaint, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, color = HikariText, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
            Text(
                text = when {
                    mediaType == "sticker" -> "Sticker werden nicht gesichert."
                    hasMediaAccess -> "Nicht gesichert — WhatsApp hatte die Datei noch nicht heruntergeladen."
                    else -> "Nicht gesichert — dafür fehlt die Freigabe des Medienordners."
                },
                color = HikariTextMuted,
                fontSize = 12.5.sp,
                lineHeight = 17.sp,
            )
        }
    }
}

@Composable
private fun VoicePlayer(audioFile: File) {
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
            .clip(RoundedCornerShape(14.dp))
            .background(HikariSurface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(Color.White)
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
                if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                contentDescription = if (isPlaying) "Pause" else "Abspielen",
                tint = Color.Black,
                modifier = Modifier.size(22.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = "Gerettete Sprachnachricht",
            color = HikariText,
            fontSize = 14.sp,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(HikariSurfaceHigh)
                .clickable {
                    speed = when (speed) {
                        1.0f -> 1.5f
                        1.5f -> 2.0f
                        else -> 1.0f
                    }
                    playerRef.value?.let { mp ->
                        if (isPlaying) runCatching { mp.playbackParams = PlaybackParams().setSpeed(speed) }
                    }
                }
                .padding(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                text = if (speed == 1.0f) "1x" else "${speed}x",
                color = HikariText,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun MediaViewerDialog(file: File, onDismiss: () -> Unit) {
    val context = LocalContext.current
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
        ) {
            if (mediaKind(file) == MediaKind.VIDEO) {
                AndroidView(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.Center),
                    factory = { ctx ->
                        VideoView(ctx).apply {
                            setVideoPath(file.absolutePath)
                            setMediaController(MediaController(ctx).also { it.setAnchorView(this) })
                            setOnPreparedListener { start() }
                        }
                    },
                )
            } else {
                ZoomableAsyncImage(
                    model = file,
                    contentDescription = "Gerettetes Foto",
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ViewerButton(Icons.Outlined.Close, "Schließen", onDismiss)
                ViewerButton(Icons.Outlined.IosShare, "Teilen") { shareFile(context, file) }
            }
        }
    }
}

@Composable
private fun ViewerButton(icon: ImageVector, description: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size(21.dp))
    }
}

// ── Leerer Tresor ────────────────────────────────────────────────────────────────

@Composable
private fun EmptyVault(guardActive: Boolean, onRunDemo: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 56.dp, start = 20.dp, end = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(WaGreenSoft),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Shield, contentDescription = null, tint = WaGreen, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = if (guardActive) "Noch nichts gerettet" else "Der Wächter schläft noch",
            color = HikariText,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = if (guardActive) {
                "Löscht jemand eine Nachricht, ein Foto oder eine Sprachnachricht für alle — oder ändert einen Text nachträglich —, erscheint das hier."
            } else {
                "Sobald er Benachrichtigungen mitlesen darf, hält er gelöschte und bearbeitete Nachrichten fest."
            },
            color = HikariTextMuted,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(22.dp))
        Text(
            text = "Beispiel ansehen",
            color = HikariAmber,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onRunDemo)
                .padding(horizontal = 16.dp, vertical = 10.dp),
        )
    }
}

// ── Helfer ───────────────────────────────────────────────────────────────────────

private fun WhatsAppMessageEntity.eventTime(): Long = deletedAt ?: editedAt ?: timestamp

private fun dayStart(epochMs: Long): Long = Calendar.getInstance().apply {
    timeInMillis = epochMs
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

private fun dayLabel(dayStartMs: Long): String {
    val today = dayStart(System.currentTimeMillis())
    val oneDay = 24L * 60L * 60L * 1000L
    return when (dayStartMs) {
        today -> "Heute"
        today - oneDay -> "Gestern"
        else -> SimpleDateFormat("EEEE, d. MMMM", Locale.GERMAN).format(Date(dayStartMs))
    }
}

private fun formatTime(epochMs: Long): String =
    SimpleDateFormat("HH:mm", Locale.GERMAN).format(Date(epochMs))

private fun shareFile(context: Context, file: File) {
    runCatching {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(file.extension.lowercase())
            ?: when (mediaKind(file)) {
                MediaKind.AUDIO -> "audio/*"
                MediaKind.VIDEO -> "video/*"
                MediaKind.IMAGE -> "image/*"
                MediaKind.OTHER -> "*/*"
            }
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(
            Intent.createChooser(send, "Teilen über").apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) },
        )
    }
}
