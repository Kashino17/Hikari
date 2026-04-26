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
import androidx.compose.material.icons.filled.Person
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariBorder
import com.hikari.app.ui.theme.HikariDanger
import com.hikari.app.ui.theme.HikariSurface
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted
import kotlinx.coroutines.launch

private enum class EditField { NAME, NICKNAME, BIO }

@Composable
fun ProfileScreen(
    onOpenSettings: () -> Unit,
    vm: ProfileViewModel = hiltViewModel(),
) {
    val name by vm.name.collectAsState()
    val nickname by vm.nickname.collectAsState()
    val bio by vm.bio.collectAsState()
    val avatarPath by vm.avatarPath.collectAsState()

    var editing by remember { mutableStateOf<EditField?>(null) }

    val pickPhoto = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri -> if (uri != null) vm.pickAvatar(uri) }

    Box(Modifier.fillMaxSize().background(HikariBg)) {
        Column(Modifier.fillMaxSize()) {
            // ── Top bar: only the gear top-right, nothing else (minimalist) ─
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .windowInsetsPadding(WindowInsets.statusBars)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .clickable { onOpenSettings() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Einstellungen",
                        tint = HikariTextMuted,
                        modifier = Modifier.size(22.dp),
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // ── Avatar ──────────────────────────────────────────────────────
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
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
            }

            Spacer(Modifier.height(20.dp))

            // ── Name ────────────────────────────────────────────────────────
            ProfileLine(
                value = name,
                placeholder = "Dein Name",
                style = MaterialTheme.typography.headlineMedium.copy(fontSize = 20.sp),
                color = HikariText,
                onClick = { editing = EditField.NAME },
            )

            Spacer(Modifier.height(4.dp))

            // ── Nickname ────────────────────────────────────────────────────
            ProfileLine(
                value = if (nickname.isEmpty()) "" else "@$nickname",
                placeholder = "@nickname",
                style = MaterialTheme.typography.bodyMedium.copy(fontSize = 13.sp),
                color = HikariTextMuted,
                onClick = { editing = EditField.NICKNAME },
            )

            Spacer(Modifier.height(20.dp))

            // ── Bio ─────────────────────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { editing = EditField.BIO }
                    .padding(horizontal = 32.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = bio.ifEmpty { "Bio hinzufügen" },
                    color = if (bio.isEmpty()) HikariTextFaint else HikariText.copy(alpha = 0.85f),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = 13.sp,
                        lineHeight = 19.sp,
                    ),
                    textAlign = TextAlign.Center,
                )
            }
        }
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
private fun Avatar(
    avatarPath: String?,
    fallbackChar: Char?,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(108.dp)
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
                style = TextStyle(fontSize = 40.sp),
            )
        } else {
            Icon(
                Icons.Default.Person,
                contentDescription = null,
                tint = HikariTextFaint,
                modifier = Modifier.size(44.dp),
            )
        }
    }
}

@Composable
private fun ProfileLine(
    value: String,
    placeholder: String,
    style: TextStyle,
    color: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 32.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = value.ifEmpty { placeholder },
            color = if (value.isEmpty()) HikariTextFaint else color,
            style = style,
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
