package com.hikari.app.ui.channels

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.hikari.app.ui.channels.components.LanguageTypeahead
import com.hikari.app.ui.channels.components.SeriesTypeahead
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariBorder
import com.hikari.app.ui.theme.HikariSurface
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted

@Composable
fun VideoEditScreen(
    videoId: String,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    vm: VideoEditViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsState()

    LaunchedEffect(videoId) { vm.load(videoId) }
    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }

    Box(Modifier.fillMaxSize().background(HikariBg)) {
        Column(Modifier.fillMaxSize()) {
            Header(
                onBack = onBack,
                onSave = vm::save,
                saving = state.saving,
                enabled = !state.loading && state.form.title.isNotBlank(),
            )
            when {
                state.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = HikariAmber)
                }
                state.error != null && state.form.title.isBlank() ->
                    Text(
                        state.error ?: "Fehler",
                        color = Color(0xFFEF4444),
                        modifier = Modifier.padding(20.dp),
                    )
                else -> EditForm(
                    state = state,
                    onPatch = vm::updateForm,
                    onUploadBytes = { bytes, mime -> vm.uploadThumbnail(bytes, mime) },
                )
            }
        }
    }
}

@Composable
private fun Header(
    onBack: () -> Unit,
    onSave: () -> Unit,
    saving: Boolean,
    enabled: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(RoundedCornerShape(50))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Zurück",
                tint = HikariText,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.size(8.dp))
        Text(
            "Video bearbeiten",
            color = HikariText,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = onSave,
            enabled = enabled && !saving,
            colors = ButtonDefaults.buttonColors(
                containerColor = HikariAmber,
                contentColor = Color.Black,
            ),
        ) {
            Text(if (saving) "Speichere…" else "Speichern", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EditForm(
    state: VideoEditUiState,
    onPatch: (VideoEditFormState.() -> VideoEditFormState) -> Unit,
    onUploadBytes: (ByteArray, String) -> Unit,
) {
    val ctx = LocalContext.current
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            val mime = ctx.contentResolver.getType(uri) ?: "image/jpeg"
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) onUploadBytes(bytes, mime)
        }
    }

    val form = state.form
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            // Thumbnail-Preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(8.dp))
                    .background(HikariSurface)
                    .border(0.5.dp, HikariBorder, RoundedCornerShape(8.dp)),
            ) {
                if (form.thumbnailUrl.isNotBlank()) {
                    AsyncImage(
                        model = form.thumbnailUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop,
                    )
                } else {
                    Text(
                        "Kein Thumbnail",
                        color = HikariTextFaint,
                        fontSize = 12.sp,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
        }

        item {
            OutlinedTextField(
                value = form.thumbnailUrl,
                onValueChange = { v -> onPatch { copy(thumbnailUrl = v) } },
                label = { Text("Thumbnail-URL") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            OutlinedButton(
                onClick = { galleryLauncher.launch("image/*") },
                modifier = Modifier.fillMaxWidth(),
            ) { Text("Aus Galerie hochladen") }
        }

        item {
            SectionLabel("Titel & Beschreibung")
        }

        item {
            OutlinedTextField(
                value = form.title,
                onValueChange = { v -> onPatch { copy(title = v) } },
                label = { Text("Titel") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        item {
            OutlinedTextField(
                value = form.description,
                onValueChange = { v -> onPatch { copy(description = v) } },
                label = { Text("Beschreibung") },
                modifier = Modifier.fillMaxWidth().height(120.dp),
                maxLines = 6,
            )
        }

        item {
            SectionLabel("Serie & Zuordnung")
        }

        item {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    "Film",
                    color = HikariText,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = form.isMovie,
                    onCheckedChange = { v -> onPatch { copy(isMovie = v) } },
                )
            }
        }

        if (!form.isMovie) {
            item {
                SeriesTypeahead(
                    value = form.seriesTitle,
                    allSeries = state.allSeries,
                    onChange = { input, sid, stitle ->
                        onPatch { copy(seriesId = sid, seriesTitle = stitle ?: input) }
                    },
                )
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = form.season?.toString().orEmpty(),
                        onValueChange = { v ->
                            onPatch { copy(season = v.toIntOrNull()) }
                        },
                        label = { Text("Staffel") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = form.episode?.toString().orEmpty(),
                        onValueChange = { v ->
                            onPatch { copy(episode = v.toIntOrNull()) }
                        },
                        label = { Text("Folge") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }

        item {
            SectionLabel("Sprache")
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LanguageTypeahead(
                    value = form.dubLanguage,
                    options = state.allDubLanguages,
                    label = "Sprache",
                    onChange = { v -> onPatch { copy(dubLanguage = v) } },
                    modifier = Modifier.weight(1f),
                )
                LanguageTypeahead(
                    value = form.subLanguage,
                    options = state.allSubLanguages,
                    label = "Untertitel",
                    onChange = { v -> onPatch { copy(subLanguage = v) } },
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (state.error != null) {
            item {
                Text(
                    state.error,
                    color = Color(0xFFEF4444),
                    fontSize = 12.sp,
                )
            }
        }

        item { Spacer(Modifier.height(40.dp)) }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        color = HikariTextMuted,
        fontSize = 10.sp,
        letterSpacing = 1.5.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = 8.dp),
    )
}
