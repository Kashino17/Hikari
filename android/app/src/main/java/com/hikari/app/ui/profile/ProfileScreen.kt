package com.hikari.app.ui.profile

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
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
import androidx.compose.ui.graphics.Brush
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
import com.hikari.app.ui.channels.ImportSheet
import com.hikari.app.ui.navigation.SharedImport
import com.hikari.app.ui.profile.components.AreaHubShowcase
import com.hikari.app.ui.profile.tabs.ChannelsTab
import com.hikari.app.ui.profile.tabs.DownloadCategory
import com.hikari.app.ui.profile.tabs.DownloadsTab
import com.hikari.app.ui.profile.tabs.SavedTab
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
import java.io.File

private enum class ProfileTab { SAVED, CHANNELS, DOWNLOADS, BEREICHE }

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
) {
    val name by vm.name.collectAsState()
    val nickname by vm.nickname.collectAsState()
    val bio by vm.bio.collectAsState()
    val avatarPath by vm.avatarPath.collectAsState()
    val savedCount by vm.savedCount.collectAsState()
    val channelsCount by vm.channelsCount.collectAsState()
    val downloadsCount by vm.downloadsCount.collectAsState()
    val hubNews by vm.hubNews.collectAsState()
    val hubNewsCount by vm.hubNewsCount.collectAsState()
    val hubMangaCovers by vm.hubMangaCovers.collectAsState()
    val hubMangaLabel by vm.hubMangaLabel.collectAsState()

    var showEditSheet by remember { mutableStateOf(false) }
    var tab by remember { mutableStateOf(ProfileTab.SAVED) }
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
            if (event == Lifecycle.Event.ON_RESUME) vm.refreshAll()
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

    Box(Modifier.fillMaxSize().background(HikariBg)) {
        Column(Modifier.fillMaxSize()) {
            // ── Top Bar: Profil Label links, Quick Actions rechts ────────────────
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(HikariAmber),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (nickname.isNotBlank()) "@$nickname" else "PROFIL",
                        color = HikariTextMuted,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }

                // Quick Import Action
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(HikariSurfaceHigh.copy(alpha = 0.6f))
                        .clickable { importOpen = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = "Link importieren",
                        tint = HikariAmber,
                        modifier = Modifier.size(19.dp),
                    )
                }

                Spacer(Modifier.width(8.dp))

                // Settings Action
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(HikariSurfaceHigh.copy(alpha = 0.6f))
                        .clickable { onOpenSettings() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Einstellungen",
                        tint = HikariTextMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // ── Hero Profile Identity Card (Modern Streaming Profile) ───────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(
                            listOf(HikariCardBg, HikariSurface),
                        ),
                    )
                    .border(0.5.dp, HikariBorderStrong, RoundedCornerShape(20.dp))
                    .clickable { showEditSheet = true }
                    .padding(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Avatar mit Glowing Accent Ring & Kamera-Badge
                    Box(
                        modifier = Modifier.size(62.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        ProfileAvatar(
                            avatarPath = avatarPath,
                            fallbackChar = name.firstOrNull()?.uppercaseChar() ?: 'H',
                            size = 60.dp,
                            onClick = { showEditSheet = true },
                        )
                        // Mini Edit Badge
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .align(Alignment.BottomEnd)
                                .clip(CircleShape)
                                .background(HikariAmber)
                                .border(1.5.dp, HikariCardBg, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(11.dp),
                            )
                        }
                    }

                    Spacer(Modifier.width(14.dp))

                    // Name, Handle & Bio / Motto
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = name.ifBlank { "Hikari Nutzer" },
                            color = HikariText,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )

                        Spacer(Modifier.height(2.dp))

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = if (nickname.isNotBlank()) "@$nickname" else "@hikari",
                                color = HikariAmber,
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                fontFamily = FontFamily.Monospace,
                            )
                        }

                        Spacer(Modifier.height(3.dp))

                        Text(
                            text = bio.ifBlank { "Tippe hier, um dein Profil anzupassen" },
                            color = if (bio.isBlank()) HikariTextFaint else HikariTextMuted,
                            fontSize = 11.5.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 15.sp,
                        )
                    }

                    Spacer(Modifier.width(8.dp))

                    // Bearbeiten Button
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(HikariSurfaceHigh)
                            .border(0.5.dp, HikariBorderStrong, RoundedCornerShape(20.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                "Bearbeiten",
                                color = HikariText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                            )
                            Icon(
                                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = HikariTextMuted,
                                modifier = Modifier.size(12.dp),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            // ── Unified Modern Tab Switcher mit Zählern ─────────────────────────
            ProfileTabsRow(
                active = tab,
                savedCount = savedCount,
                channelsCount = channelsCount,
                downloadsCount = downloadsCount,
                onTabSelected = { tab = it },
            )

            Spacer(Modifier.height(4.dp))

            // ── Tab Content: Volle Höhe für maximale UX ─────────────────────────
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
                        onOpenTransfers = { onOpenSection("channel/manual") },
                    )
                    ProfileTab.BEREICHE -> AreaHubShowcase(
                        news = hubNews,
                        newsCount = hubNewsCount,
                        mangaCovers = hubMangaCovers,
                        mangaLabel = hubMangaLabel,
                        onOpenSection = onOpenSection,
                    )
                }
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

@Composable
private fun ProfileTabsRow(
    active: ProfileTab,
    savedCount: Int,
    channelsCount: Int,
    downloadsCount: Int,
    onTabSelected: (ProfileTab) -> Unit,
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TabChip(
            icon = Icons.Default.Bookmark,
            label = "Gespeichert",
            count = savedCount,
            selected = active == ProfileTab.SAVED,
            onClick = { onTabSelected(ProfileTab.SAVED) },
        )
        TabChip(
            icon = Icons.Default.Public,
            label = "Kanäle",
            count = channelsCount,
            selected = active == ProfileTab.CHANNELS,
            onClick = { onTabSelected(ProfileTab.CHANNELS) },
        )
        TabChip(
            icon = Icons.Default.Download,
            label = "Downloads",
            count = downloadsCount,
            selected = active == ProfileTab.DOWNLOADS,
            onClick = { onTabSelected(ProfileTab.DOWNLOADS) },
        )
        TabChip(
            icon = Icons.Default.Apps,
            label = "Bereiche",
            count = 6,
            selected = active == ProfileTab.BEREICHE,
            onClick = { onTabSelected(ProfileTab.BEREICHE) },
        )
    }
}

@Composable
private fun TabChip(
    icon: ImageVector,
    label: String,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bgColor by animateColorAsState(
        targetValue = if (selected) HikariSurfaceHigh else HikariCardBg,
        animationSpec = tween(180),
        label = "tab-bg",
    )
    val borderColor by animateColorAsState(
        targetValue = if (selected) HikariAmber else HikariBorder,
        animationSpec = tween(180),
        label = "tab-border",
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
            tint = if (selected) HikariAmber else HikariTextFaint,
            modifier = Modifier.size(15.dp),
        )
        Text(
            text = label,
            color = if (selected) HikariText else HikariTextFaint,
            fontSize = 12.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
        )
        // Count Pill
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (selected) HikariAmberSoft else HikariSurface)
                .padding(horizontal = 5.dp, vertical = 1.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = count.toString(),
                color = if (selected) HikariAmber else HikariTextMuted,
                fontSize = 10.5.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
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
