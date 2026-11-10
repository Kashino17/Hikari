package com.hikari.app.ui.profile

import android.widget.Toast
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import coil.compose.AsyncImage
import com.hikari.app.domain.profile.AreaTiles
import com.hikari.app.ui.channels.ImportSheet
import com.hikari.app.ui.common.TextTab
import com.hikari.app.ui.common.TextTabs
import com.hikari.app.ui.navigation.SharedImport
import com.hikari.app.ui.profile.components.AreaTileGrid
import com.hikari.app.ui.profile.tabs.ChannelsTab
import com.hikari.app.ui.profile.tabs.DownloadCategory
import com.hikari.app.ui.profile.tabs.DownloadsTab
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariBorder
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariDanger
import com.hikari.app.ui.theme.HikariSurface
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted
import java.io.File

private enum class ProfileTab { BEREICHE, CHANNELS, DOWNLOADS }

@Composable
fun ProfileScreen(
    onOpenSettings: () -> Unit,
    onOpenChannel: (String) -> Unit,
    onPlayVideo: (videoId: String, title: String, channel: String) -> Unit,
    onOpenDownloadCategory: (DownloadCategory) -> Unit,
    onOpenSection: (route: String) -> Unit,
    /** Per Teilen-Menü eingegangener Link: öffnet das Import-Sheet mit ihm vorbefüllt. */
    pendingImport: SharedImport? = null,
    vm: ProfileViewModel = hiltViewModel(),
    tilesVm: AreaTilesViewModel = hiltViewModel(),
) {
    val name by vm.name.collectAsState()
    val nickname by vm.nickname.collectAsState()
    val bio by vm.bio.collectAsState()
    val avatarPath by vm.avatarPath.collectAsState()
    val channelsCount by vm.channelsCount.collectAsState()
    val downloadsCount by vm.downloadsCount.collectAsState()
    val studySeconds by tilesVm.studySeconds.collectAsState()
    val streak by tilesVm.streak.collectAsState()

    var showEditSheet by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(ProfileTab.BEREICHE) }
    var importOpen by remember { mutableStateOf(false) }
    var importSeed by remember { mutableStateOf<SharedImport?>(null) }

    LaunchedEffect(pendingImport?.nonce) {
        if (pendingImport != null) {
            importSeed = pendingImport
            importOpen = true
        }
    }

    // Refresh stats when returning to the screen
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val obs = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                vm.refreshAll()
                tilesVm.refresh()
            }
        }
        lifecycleOwner.lifecycle.addObserver(obs)
        onDispose { lifecycleOwner.lifecycle.removeObserver(obs) }
    }

    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) vm.pickAvatar(uri) }

    // Surface avatar-save failures
    val toastCtx = LocalContext.current
    LaunchedEffect(Unit) {
        vm.events.collect { msg ->
            Toast.makeText(toastCtx, msg, Toast.LENGTH_SHORT).show()
        }
    }

    Column(Modifier.fillMaxSize().background(HikariBg)) {
        // ── Kopf: wer du bist und was heute lief, rechts Importieren und Einstellungen ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Den Abstand zur Statusleiste bringt schon der Rahmen der Navigation mit.
                .padding(start = 12.dp, end = 16.dp, top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable(onClickLabel = "Profil bearbeiten") { showEditSheet = true }
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box {
                    ProfileAvatar(
                        avatarPath = avatarPath,
                        fallbackChar = name.firstOrNull()?.uppercaseChar() ?: 'H',
                        size = 44.dp,
                        onClick = { showEditSheet = true },
                    )
                    // Der einzige sichtbare Hinweis, dass sich Bild und Name hier ändern lassen.
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 3.dp, y = 3.dp)
                            .size(19.dp)
                            .clip(CircleShape)
                            .background(HikariAmber)
                            .border(2.dp, HikariBg, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Edit,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(10.dp),
                        )
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = name.ifBlank { "Hikari Nutzer" },
                        color = HikariText,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    ProfileSubline(
                        studyMinutes = AreaTiles.studyMinutes(studySeconds),
                        streak = streak,
                        bio = bio,
                        nickname = nickname,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            HeaderButton(Icons.Outlined.Add, "Link importieren", HikariAmber) { importOpen = true }
            Spacer(Modifier.width(8.dp))
            HeaderButton(Icons.Outlined.Settings, "Einstellungen", HikariTextMuted, onOpenSettings)
        }

        ProfileTabsRow(
            active = tab,
            channelsCount = channelsCount,
            downloadsCount = downloadsCount,
            onTabSelected = { tab = it },
        )

        Box(modifier = Modifier.fillMaxSize()) {
            when (tab) {
                ProfileTab.BEREICHE -> AreaTileGrid(vm = tilesVm, onOpenSection = onOpenSection)
                ProfileTab.CHANNELS -> ChannelsTab(
                    onOpenChannel = onOpenChannel,
                    onOpenImport = { importOpen = true },
                )
                ProfileTab.DOWNLOADS -> DownloadsTab(
                    onOpenCategory = onOpenDownloadCategory,
                    onOpenTransfers = { onOpenSection("channel/manual") },
                )
            }
        }
    }

    if (importOpen) {
        ImportSheet(
            onDismiss = {
                importOpen = false
                importSeed = null
            },
            onOpenBrowser = {
                importOpen = false
                importSeed = null
                onOpenSection("browser")
            },
            onSubmitted = {
                importOpen = false
                importSeed = null
                onOpenSection("channel/manual")
            },
            seed = importSeed,
        )
    }

    if (showEditSheet) {
        UnifiedProfileEditSheet(
            initialName = name,
            initialNickname = nickname,
            initialBio = bio,
            avatarPath = avatarPath,
            bioMax = vm.bioMax,
            onDismiss = { showEditSheet = false },
            onPickPhoto = {
                pickPhoto.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            },
            onSave = { newName, newNick, newBio ->
                vm.setName(newName)
                val nickErr = if (newNick.isNotBlank() && newNick != nickname) {
                    vm.trySetNickname(newNick)
                } else null
                vm.setBio(newBio)
                if (nickErr == null) {
                    showEditSheet = false
                }
                nickErr?.msg
            },
        )
    }
}

/** Zeile unter dem Namen: was heute lief — sonst die Bio, sonst der Nickname. */
@Composable
private fun ProfileSubline(studyMinutes: String?, streak: Int, bio: String, nickname: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        when {
            studyMinutes != null || streak > 0 -> {
                if (studyMinutes != null) {
                    Text("Heute $studyMinutes gelernt", color = HikariTextMuted, fontSize = 12.5.sp, maxLines = 1)
                    Spacer(Modifier.width(8.dp))
                }
                if (streak > 0) {
                    Icon(
                        Icons.Outlined.LocalFireDepartment,
                        contentDescription = "Serie",
                        tint = HikariAmber,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        if (streak == 1) "1 Tag" else "$streak Tage",
                        color = HikariAmber,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                    )
                }
            }
            else -> Text(
                text = bio.ifBlank { "@" + nickname.ifBlank { "hikari" } },
                color = HikariTextMuted,
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun HeaderButton(icon: ImageVector, label: String, tint: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(38.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.06f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ProfileTabsRow(
    active: ProfileTab,
    channelsCount: Int,
    downloadsCount: Int,
    onTabSelected: (ProfileTab) -> Unit,
) {
    TextTabs(
        tabs = listOf(TextTab("Bereiche"), TextTab("Kanäle", channelsCount), TextTab("Downloads", downloadsCount)),
        selected = active.ordinal,
        onSelect = { onTabSelected(ProfileTab.entries[it]) },
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
    )
}

@Composable
private fun ProfileAvatar(
    avatarPath: String?,
    fallbackChar: Char,
    size: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(HikariSurfaceHigh)
            .border(1.5.dp, HikariAmber.copy(alpha = 0.7f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        val avatarFile = remember(avatarPath) {
            avatarPath?.substringBefore("?")?.let(::File)?.takeIf { it.exists() }
        }
        if (avatarFile != null) {
            AsyncImage(
                model = avatarFile,
                contentDescription = "Profilbild",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        } else {
            Text(
                fallbackChar.toString(),
                color = HikariAmber,
                style = TextStyle(fontSize = 26.sp, fontWeight = FontWeight.Black),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnifiedProfileEditSheet(
    initialName: String,
    initialNickname: String,
    initialBio: String,
    avatarPath: String?,
    bioMax: Int,
    onDismiss: () -> Unit,
    onPickPhoto: () -> Unit,
    onSave: (name: String, nickname: String, bio: String) -> String?,
) {
    var draftName by remember { mutableStateOf(initialName) }
    var draftNick by remember { mutableStateOf(initialNickname) }
    var draftBio by remember { mutableStateOf(initialBio) }
    var errorText by remember { mutableStateOf<String?>(null) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = HikariSurface,
        dragHandle = null,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Abbrechen",
                    color = HikariTextMuted,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp),
                    modifier = Modifier.clickable { onDismiss() },
                )
                Text(
                    "Profil bearbeiten",
                    color = HikariText,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                )
                Text(
                    "Fertig",
                    color = HikariAmber,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = 13.sp, fontWeight = FontWeight.Bold),
                    modifier = Modifier.clickable {
                        val err = onSave(draftName.trim(), draftNick.trim().lowercase(), draftBio.trim())
                        errorText = err
                    },
                )
            }

            Spacer(Modifier.height(18.dp))

            // Avatar Change Section
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ProfileAvatar(
                    avatarPath = avatarPath,
                    fallbackChar = draftName.firstOrNull()?.uppercaseChar() ?: 'H',
                    size = 72.dp,
                    onClick = onPickPhoto,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(HikariSurfaceHigh)
                        .clickable { onPickPhoto() }
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        Icons.Default.PhotoCamera,
                        contentDescription = null,
                        tint = HikariAmber,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        "Foto ändern",
                        color = HikariAmber,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Field: Name
            Text(
                "NAME",
                color = HikariTextFaint,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.2.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(HikariCardBg)
                    .border(0.5.dp, HikariBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                BasicTextField(
                    value = draftName,
                    onValueChange = { if (it.length <= 40) draftName = it },
                    singleLine = true,
                    textStyle = TextStyle(color = HikariText, fontSize = 14.sp),
                    cursorBrush = SolidColor(HikariAmber),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (draftName.isEmpty()) {
                    Text("Dein Anzeigename", color = HikariTextFaint, fontSize = 14.sp)
                }
            }

            Spacer(Modifier.height(14.dp))

            // Field: Nickname (@handle)
            Text(
                "NICKNAME (@HANDLE)",
                color = HikariTextFaint,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 1.2.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(HikariCardBg)
                    .border(0.5.dp, if (errorText != null) HikariDanger else HikariBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "@",
                        color = HikariAmber,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.width(4.dp))
                    BasicTextField(
                        value = draftNick,
                        onValueChange = {
                            draftNick = it.lowercase().take(20)
                            errorText = null
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
            }
            if (errorText != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    errorText!!,
                    color = HikariDanger,
                    fontSize = 11.sp,
                )
            }

            Spacer(Modifier.height(14.dp))

            // Field: Bio
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "BIO / MOTTO",
                    color = HikariTextFaint,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.2.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Text(
                    "${draftBio.length}/$bioMax",
                    color = HikariTextFaint,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 72.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(HikariCardBg)
                    .border(0.5.dp, HikariBorder, RoundedCornerShape(10.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                BasicTextField(
                    value = draftBio,
                    onValueChange = { if (it.length <= bioMax) draftBio = it },
                    singleLine = false,
                    textStyle = TextStyle(color = HikariText, fontSize = 13.5.sp, lineHeight = 18.sp),
                    cursorBrush = SolidColor(HikariAmber),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (draftBio.isEmpty()) {
                    Text("Ein kurzer Satz über dich...", color = HikariTextFaint, fontSize = 13.5.sp)
                }
            }

            Spacer(Modifier.height(28.dp))
        }
    }
}
