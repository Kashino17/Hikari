package com.hikari.app.ui.profile.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.hikari.app.ui.profile.components.LocalState
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariTextMuted

/**
 * Tri-state download status icon. Tap behaves as:
 * - NotLocal → trigger download
 * - Downloading → cancel (no-op for now; click consumed silently)
 * - Local → trigger remove-from-device (caller may want to confirm)
 */
@Composable
fun LocalDownloadIcon(
    state: LocalState,
    progress: Float?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(
                when (state) {
                    LocalState.Local -> HikariAmber.copy(alpha = 0.18f)
                    LocalState.Downloading -> HikariAmber.copy(alpha = 0.12f)
                    LocalState.NotLocal -> Color.Transparent
                },
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when (state) {
            LocalState.NotLocal -> Icon(
                Icons.Default.CloudDownload,
                contentDescription = "Auf Gerät herunterladen",
                tint = HikariTextMuted,
                modifier = Modifier.size(16.dp),
            )
            LocalState.Downloading -> {
                if (progress != null && progress > 0f) {
                    CircularProgressIndicator(
                        progress = { progress },
                        color = HikariAmber,
                        strokeWidth = 2.dp,
                        modifier = Modifier.fillMaxSize().size(20.dp),
                    )
                } else {
                    CircularProgressIndicator(
                        color = HikariAmber,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
            LocalState.Local -> Icon(
                Icons.Default.PhoneAndroid,
                contentDescription = "Lokal verfügbar",
                tint = HikariAmber,
                modifier = Modifier.size(15.dp),
            )
        }
    }
}
