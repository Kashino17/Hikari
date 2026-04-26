package com.hikari.app.ui.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.hikari.app.ui.channels.ImportSheet
import com.hikari.app.ui.profile.tabs.ChannelsTab
import com.hikari.app.ui.profile.tabs.DownloadsTab
import com.hikari.app.ui.profile.tabs.SavedTab
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariBorder
import com.hikari.app.ui.theme.HikariDanger
import com.hikari.app.ui.theme.HikariSurface
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted

private enum class EditField { NAME, NICKNAME, BIO }
private enum class ProfileTab { SAVED, CHANNELS, DOWNLOADS }

@Composable
fun ProfileScreen(
    onOpenSettings: () -> Unit,
    onOpenChannel: (String) -> Unit,
    onPlayVideo: (videoId: String, title: String, channel: String) -> Unit,
    onOpenDownloadCategory: (com.hikari.app.ui.profile.tabs.DownloadCategory) -> Unit,
    vm: ProfileViewModel = hiltViewModel(),
) {
    val name by vm.name.collectAsState()
    val nickname by vm.nickname.collectAsState()
    val bio by vm.bio.collectAsState()
    val avatarPath by vm.avatarPath.collectAsState()
    val savedCount by vm.savedCount.collectAsState()
    val channelsCount by vm.channelsCount.collectAsState()

    var editing by remember { mutableStateOf<EditField?>(null) }
    var tab by remember { mutableStateOf(ProfileTab.SAVED) }
    var importOpen by remember { mutableStateOf(false) }

    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) vm.pickAvatar(uri) }

    Box(Modifier.fillMaxSize().background(HikariBg)) {
        Column(Modifier.fillMaxSize()) {
            // ── Top bar: title left, gear right ──────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "PROFIL",
                    color = HikariTextFaint,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.5.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .clickable { onOpenSettings() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Einstellungen",
                        tint = HikariTextMuted,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            // ── Header: avatar left + 3-stat row ────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Avatar(
                    avatarPath = avatarPath,
                    fallbackChar = name.firstOrNull()?.uppercaseChar(),
                    onClick = {
                        pickPhoto.launch(
                            PickVisualMediaRequest(
                                ActivityResultContracts.PickVisualMedia.ImageOnly,
                            ),
                        )
                    },
                )
                Spacer(Modifier.size(20.dp))
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.SpaceAround,
                ) {
                    Stat(value = savedCount, label = "VIDEOS")
                    Stat(value = channelsCount, label = "KANÄLE")
                    Stat(value = 0, label = "DOWNLOADS", muted = true)
                }
            }

            // ── Name + Nick + Bio (click → edit) ─────────────────────────────
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                ProfileLine(
                    value = name,
                    placeholder = "Dein Name",
                    style = MaterialTheme.typography.headlineMedium.copy(fontSize = 16.sp, fontWeight = FontWeight.Bold),
                    color = HikariText,
                    onClick = { editing = EditField.NAME },
                    align = TextAlign.Start,
                )
                ProfileLine(
                    value = if (nickname.isEmpty()) "" else "@$nickname",
                    placeholder = "@nickname",
                    style = MaterialTheme.typography.bodyMedium.copy(fontSize = 12.sp),
                    color = HikariAmber,
                    onClick = { editing = EditField.NICKNAME },
                    align = TextAlign.Start,
                )
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { editing = EditField.BIO },
                ) {
                    Text(
                        text = bio.ifEmpty { "Bio hinzufügen" },
                        color = if (bio.isEmpty()) HikariTextFaint else HikariText.copy(alpha = 0.85f),
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontSize = 12.sp,
                            lineHeight = 17.sp,
                        ),
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── Tab bar ──────────────────────────────────────────────────────
            TabBar(active = tab, onChange = { tab = it })

            // ── Tab content ──────────────────────────────────────────────────
            Box(modifier = Modifier.fillMaxSize()) {
                when (tab) {
                    ProfileTab.SAVED -> SavedTab(
                        onPlay = { onPlayVideo(it.videoId, it.title, it.channelTitle) },
                    )
                    ProfileTab.CHANNELS -> ChannelsTab(
                        onOpenChannel = onOpenChannel,
                        onOpenImport = { importOpen = true },
                    )
                    ProfileTab.DOWNLOADS -> DownloadsTab(
                        onOpenCategory = onOpenDownloadCategory,
                    )
                }
            }
        }
    }

    if (importOpen) {
        ImportSheet(onDismiss = { importOpen = false })
    }

    when (editing) {
        EditField.NAME -> EditSheet(
            title = "Name",
            initial = name,
            singleLine = true,
            maxLength = 40,
            onDismiss = { editing = null },
            onSave = {
                vm.setName(it)
                editing = null
            },
        )
        EditField.NICKNAME -> NicknameEditSheet(
            initial = nickname,
            onDismiss = { editing = null },
            onSave = { value ->
                val err = vm.trySetNickname(value)
                if (err == null) editing = null
                err
            },
        )
        EditField.BIO -> EditSheet(
            title = "Bio",
            initial = bio,
            singleLine = false,
            maxLength = vm.bioMax,
            onDismiss = { editing = null },
            onSave = {
                vm.setBio(it)
                editing = null
            },
        )
        null -> Unit
    }
}

@Composable
private fun Stat(value: Int, label: String, muted: Boolean = false) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            value.toString(),
            color = if (muted) HikariTextMuted else HikariText,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(2.dp))
        Text(
            label,
            color = HikariTextFaint,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 1.sp,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun TabBar(active: ProfileTab, onChange: (ProfileTab) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(HikariBg)
            .padding(top = 4.dp),
    ) {
        TabIcon(Icons.Default.Bookmark, "Gespeichert", active == ProfileTab.SAVED) {
            onChange(ProfileTab.SAVED)
        }
        TabIcon(Icons.Default.Public, "Kanäle", active == ProfileTab.CHANNELS) {
            onChange(ProfileTab.CHANNELS)
        }
        TabIcon(Icons.Default.Download, "Downloads", active == ProfileTab.DOWNLOADS) {
            onChange(ProfileTab.DOWNLOADS)
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.TabIcon(
    icon: ImageVector,
    contentDescription: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .weight(1f)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            icon,
            contentDescription = contentDescription,
            tint = if (active) HikariText else HikariTextFaint,
            modifier = Modifier
                .size(48.dp)
                .padding(12.dp),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.5.dp)
                .background(if (active) HikariAmber else Color.Transparent),
        )
    }
}

@Composable
private fun Avatar(
    avatarPath: String?,
    fallbackChar: Char?,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(84.dp)
            .clip(CircleShape)
            .background(HikariSurfaceHigh)
            .border(0.5.dp, HikariBorder, CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        if (avatarPath != null) {
            AsyncImage(
                model = avatarPath,
                contentDescription = "Profilbild",
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        } else if (fallbackChar != null) {
            Text(
                fallbackChar.toString(),
                color = HikariAmber,
                style = TextStyle(fontSize = 36.sp, fontWeight = FontWeight.Black),
            )
        } else {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                tint = HikariTextFaint,
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

@Composable
private fun ProfileLine(
    value: String,
    placeholder: String,
    style: TextStyle,
    color: Color,
    onClick: () -> Unit,
    align: TextAlign = TextAlign.Center,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 2.dp),
        contentAlignment = if (align == TextAlign.Center) Alignment.Center else Alignment.CenterStart,
    ) {
        Text(
            text = value.ifEmpty { placeholder },
            color = if (value.isEmpty()) HikariTextFaint else color,
            style = style,
            textAlign = align,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditSheet(
    title: String,
    initial: String,
    singleLine: Boolean,
    maxLength: Int,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var draft by remember { mutableStateOf(initial) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HikariSurface,
        dragHandle = null,
    ) {
        SheetHeader(title = title, onCancel = onDismiss, onSave = { onSave(draft) })

        BasicTextField(
            value = draft,
            onValueChange = { if (it.length <= maxLength) draft = it },
            singleLine = singleLine,
            textStyle = TextStyle(color = HikariText, fontSize = 14.sp, lineHeight = 20.sp),
            cursorBrush = SolidColor(HikariAmber),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .heightIn(min = if (singleLine) 44.dp else 96.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Text(
                "${draft.length}/$maxLength",
                color = HikariTextFaint,
                style = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp),
            )
        }
        Spacer(Modifier.height(8.dp))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun NicknameEditSheet(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> NicknameError?,
) {
    var draft by remember { mutableStateOf(initial) }
    var error by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HikariSurface,
        dragHandle = null,
    ) {
        SheetHeader(
            title = "Nickname",
            onCancel = onDismiss,
            onSave = {
                val sanitized = draft.lowercase()
                val err = onSave(sanitized)
                error = err?.msg
            },
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "@",
                color = HikariTextMuted,
                style = TextStyle(fontSize = 14.sp, fontFamily = FontFamily.Monospace),
            )
            Spacer(Modifier.size(2.dp))
            BasicTextField(
                value = draft,
                onValueChange = {
                    draft = it.lowercase().take(20)
                    error = null
                },
                singleLine = true,
                textStyle = TextStyle(
                    color = HikariText,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                ),
                cursorBrush = SolidColor(HikariAmber),
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = error ?: "a–z, 0–9, _ und . — 3 bis 20 Zeichen.",
            color = if (error != null) HikariDanger else HikariTextFaint,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 11.sp),
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun SheetHeader(title: String, onCancel: () -> Unit, onSave: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
    ) {
        Text(
            "Abbrechen",
            color = HikariTextMuted,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .clickable { onCancel() },
        )
        Text(
            title,
            color = HikariText,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.align(Alignment.Center),
        )
        Text(
            "Speichern",
            color = HikariAmber,
            style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .clickable { onSave() },
        )
    }
}
