package com.hikari.app.ui.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.domain.update.UpdateCoordinator
import com.hikari.app.domain.update.UpdateState
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariBorderStrong
import com.hikari.app.ui.theme.HikariDanger
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextMuted
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface UpdateEntryPoint {
    fun updateCoordinator(): UpdateCoordinator
}

/**
 * Hinweis von oben, sobald ein neueres Release bereitsteht. „Aktualisieren“
 * lädt das Update und startet danach direkt die Installation; das X blendet
 * den Hinweis bis zum nächsten App-Start aus.
 */
@Composable
fun UpdateBanner(visible: Boolean, modifier: Modifier = Modifier) {
    val ctx = LocalContext.current
    val coordinator = remember {
        EntryPointAccessors.fromApplication(ctx, UpdateEntryPoint::class.java).updateCoordinator()
    }
    val state by coordinator.state.collectAsState()
    val dismissed by coordinator.bannerDismissed.collectAsState()

    // Nur Zustände, die ein konkretes Update betreffen — „aktuell“ oder ein
    // gescheiterter stiller Check zeigen nichts.
    val relevant = when (val s = state) {
        is UpdateState.Available, is UpdateState.Downloading, is UpdateState.Downloaded -> true
        is UpdateState.Error -> s.retry != null
        else -> false
    }

    AnimatedVisibility(
        visible = visible && relevant && !dismissed,
        enter = slideInVertically(tween(260)) { -it } + fadeIn(tween(200)),
        exit = slideOutVertically(tween(200)) { -it } + fadeOut(tween(160)),
        modifier = modifier,
    ) {
        BannerCard(
            state = state,
            onAction = coordinator::startUpdate,
            onDismiss = coordinator::dismissBanner,
        )
    }
}

@Composable
private fun BannerCard(state: UpdateState, onAction: () -> Unit, onDismiss: () -> Unit) {
    val version = when (state) {
        is UpdateState.Available -> state.version
        is UpdateState.Downloading -> state.version
        is UpdateState.Downloaded -> state.version
        is UpdateState.Error -> state.retry?.version.orEmpty()
        else -> ""
    }
    val shape = RoundedCornerShape(18.dp)
    Column(
        modifier = Modifier
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .shadow(16.dp, shape)
            .clip(shape)
            .background(HikariSurfaceHigh)
            .border(1.dp, HikariBorderStrong, shape),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(38.dp).clip(RoundedCornerShape(11.dp)).background(HikariAmber),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.SystemUpdate, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = when (state) {
                        is UpdateState.Downloading -> "Update wird geladen"
                        is UpdateState.Downloaded -> "Update bereit"
                        is UpdateState.Error -> "Download fehlgeschlagen"
                        else -> "Update verfügbar"
                    },
                    color = if (state is UpdateState.Error) HikariDanger else HikariText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = when (state) {
                        is UpdateState.Downloading ->
                            if (state.percent >= 0) "Version $version · ${state.percent} %" else "Version $version"
                        is UpdateState.Downloaded -> "Version $version · Installation bestätigen"
                        else -> "Version $version"
                    },
                    color = HikariTextMuted,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (state !is UpdateState.Downloading) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = when (state) {
                        is UpdateState.Downloaded -> "Installieren"
                        is UpdateState.Error -> "Erneut"
                        else -> "Aktualisieren"
                    },
                    color = Color.Black,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color.White)
                        .clickable(onClick = onAction)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
            Box(
                modifier = Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onDismiss),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Close, contentDescription = "Ausblenden", tint = HikariTextMuted, modifier = Modifier.size(18.dp))
            }
        }
        if (state is UpdateState.Downloading) {
            // Eingerückt und abgerundet — bündig am Kartenrand würde die Rundung den Balken anschneiden.
            Box(
                Modifier
                    .padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.08f)),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth((state.percent.coerceIn(0, 100)) / 100f)
                        .height(4.dp)
                        .background(HikariAmber),
                )
            }
        }
    }
}
