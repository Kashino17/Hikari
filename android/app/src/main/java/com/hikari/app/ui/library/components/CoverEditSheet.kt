package com.hikari.app.ui.library.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.ui.library.CoverEditState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoverEditSheet(
    seriesTitle: String,
    state: CoverEditState,
    onDismiss: () -> Unit,
    onSaveUrl: (String) -> Unit,
    onPickGallery: (bytes: ByteArray, mime: String) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var url by remember { mutableStateOf("") }
    val ctx = LocalContext.current

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            val mime = ctx.contentResolver.getType(uri) ?: "image/jpeg"
            val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            if (bytes != null) onPickGallery(bytes, mime)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF111111),
        contentColor = Color.White,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "Cover für: $seriesTitle",
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                "Cover-URL",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 10.sp,
                letterSpacing = 1.5.sp,
            )
            OutlinedTextField(
                value = url,
                onValueChange = { url = it },
                placeholder = { Text("https://…") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                enabled = state !is CoverEditState.Saving,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onSaveUrl(url) },
                    enabled = url.isNotBlank() && state !is CoverEditState.Saving,
                    modifier = Modifier.weight(1f),
                ) { Text("Speichern") }
                OutlinedButton(
                    onClick = { onSaveUrl("") },
                    enabled = state !is CoverEditState.Saving,
                ) { Text("Zurücksetzen") }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                color = Color.White.copy(alpha = 0.1f),
            )

            Text(
                "ODER",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 10.sp,
                letterSpacing = 1.5.sp,
            )
            Button(
                onClick = { galleryLauncher.launch("image/*") },
                enabled = state !is CoverEditState.Saving,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black,
                ),
            ) { Text("Aus Galerie wählen", fontWeight = FontWeight.Bold) }

            if (state is CoverEditState.Saving) {
                Spacer(Modifier.height(4.dp))
                Text("Speichere…", color = Color(0xFFFBBF24), fontSize = 12.sp)
            }
            if (state is CoverEditState.Error) {
                Spacer(Modifier.height(4.dp))
                Text(state.message, color = Color(0xFFEF4444), fontSize = 12.sp)
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
