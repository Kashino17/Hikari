package com.hikari.app.ui.news

import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn as AnnotOptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.hikari.app.domain.model.NewsItem
import com.hikari.app.player.HikariPlayerFactory
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariPrimary
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

@EntryPoint
@InstallIn(SingletonComponent::class)
interface NewsEntryPoint {
    fun playerFactory(): HikariPlayerFactory
}

/**
 * Täglicher KI-Tagesbericht: vertikaler Swipe-Feed über die News-Beiträge,
 * pro Beitrag eine Slide mit Hero-Bild, Text und optionalem Inline-Video.
 */
@Composable
fun NewsScreen(vm: NewsViewModel = hiltViewModel()) {
    Column(Modifier.fillMaxSize().background(HikariBg)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Tagesbericht",
                color = HikariText,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = { vm.openSettings() }) {
                Icon(Icons.Default.Settings, contentDescription = "Einstellungen", tint = HikariTextMuted)
            }
        }

        Box(Modifier.fillMaxWidth().weight(1f)) {
            when {
                vm.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = HikariPrimary)
                }
                vm.failed -> NewsErrorState(onRetry = { vm.reload(force = false) })
                vm.items.isEmpty() -> NewsEmptyState(onOpenSettings = { vm.openSettings() })
                else -> NewsPager(vm.items)
            }
        }
    }

    if (vm.settingsOpen) {
        NewsSettingsSheet(vm = vm, onDismiss = { vm.closeSettings() })
    }
}

@Composable
private fun NewsPager(items: List<NewsItem>) {
    val pagerState = rememberPagerState(pageCount = { items.size })
    var settledPage by remember { mutableIntStateOf(0) }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { settledPage = it }
    }

    VerticalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
        key = { items[it].id },
    ) { page ->
        NewsSlide(
            item = items[page],
            isCurrent = page == settledPage,
            showSwipeHint = page == 0,
        )
    }
}

@Composable
private fun NewsSlide(item: NewsItem, isCurrent: Boolean, showSwipeHint: Boolean) {
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        NewsHero(item = item, isCurrent = isCurrent, modifier = Modifier.fillMaxWidth())

        // Topic-Chip
        Surface(
            color = HikariCardBg,
            shape = RoundedCornerShape(50),
        ) {
            Text(
                item.topic.replaceFirstChar { it.uppercase() },
                color = HikariPrimary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            )
        }

        Text(item.title, color = HikariText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(item.summary, color = HikariTextMuted, fontSize = 14.sp, lineHeight = 20.sp)

        val relTime = remember(item.publishedAt) { formatRelativeTime(item.publishedAt) }
        Text(
            text = if (relTime.isEmpty()) item.source else "${item.source} · $relTime",
            color = HikariTextFaint,
            fontSize = 12.sp,
        )

        if (item.url.isNotBlank()) {
            TextButton(onClick = {
                runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url))) }
            }) {
                Text("Quelle lesen", color = HikariPrimary, fontSize = 13.sp)
            }
        }

        Spacer(Modifier.weight(1f))

        if (showSwipeHint) {
            Row(
                Modifier.fillMaxWidth().padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.KeyboardArrowUp,
                    contentDescription = null,
                    tint = HikariTextFaint,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    "Nach oben wischen für die nächste Nachricht",
                    color = HikariTextFaint,
                    fontSize = 12.sp,
                )
            }
        } else {
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * Hero-Bereich einer Slide: Bild (16:9) mit Zweitbild-Kachel, ohne Bild ein
 * Verlaufs-Platzhalter. Gibt es ein Video, startet ein Tap auf den
 * Play-Button den Inline-Player — kein Autoplay.
 */
@Composable
private fun NewsHero(item: NewsItem, isCurrent: Boolean, modifier: Modifier = Modifier) {
    var playing by remember(item.id) { mutableStateOf(false) }

    Box(
        modifier
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(16.dp)),
    ) {
        if (playing && item.videoUrl != null) {
            InlineVideoPlayer(videoUrl = item.videoUrl, isCurrent = isCurrent)
        } else {
            if (item.imageUrls.isNotEmpty()) {
                AsyncImage(
                    model = item.imageUrls[0],
                    contentDescription = item.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(Brush.verticalGradient(listOf(HikariSurfaceHigh, HikariBg))),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Newspaper,
                        contentDescription = null,
                        tint = HikariTextFaint,
                        modifier = Modifier.size(56.dp),
                    )
                }
            }

            if (item.imageUrls.size > 1) {
                AsyncImage(
                    model = item.imageUrls[1],
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(8.dp)
                        .size(72.dp)
                        .clip(RoundedCornerShape(8.dp)),
                )
            }

            if (item.videoUrl != null) {
                Box(
                    Modifier
                        .align(Alignment.Center)
                        .size(64.dp)
                        .background(HikariPrimary, CircleShape)
                        .clickable { playing = true },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Video abspielen",
                        tint = Color.Black,
                        modifier = Modifier.size(36.dp),
                    )
                }
            }
        }
    }
}

/**
 * Inline-ExoPlayer für eine Slide. Der Player lebt nur, solange das Video
 * sichtbar gespielt wird; Release im DisposableEffect, Pause bei Seitenwechsel.
 */
@AnnotOptIn(UnstableApi::class)
@Composable
private fun InlineVideoPlayer(videoUrl: String, isCurrent: Boolean) {
    val ctx = LocalContext.current
    val factory = remember {
        EntryPointAccessors.fromApplication(ctx, NewsEntryPoint::class.java).playerFactory()
    }
    // Erst nach Tap auf Play erzeugt — playWhenReady ist damit Nutzer-intent, kein Autoplay.
    val player = remember(videoUrl) {
        factory.create().apply {
            setMediaItem(MediaItem.fromUri(videoUrl))
            prepare()
        }
    }
    DisposableEffect(player) { onDispose { player.release() } }
    LaunchedEffect(isCurrent) {
        if (!isCurrent) player.pause()
    }

    AndroidView(
        factory = { viewCtx ->
            PlayerView(viewCtx).apply {
                this.player = player
                useController = true
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShutterBackgroundColor(android.graphics.Color.BLACK)
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

@Composable
private fun NewsErrorState(onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.CloudOff,
            contentDescription = null,
            tint = HikariTextFaint,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Nachrichten-Server nicht erreichbar",
            color = HikariText,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onRetry,
            colors = ButtonDefaults.buttonColors(containerColor = HikariPrimary, contentColor = Color.Black),
        ) {
            Text("Erneut versuchen")
        }
    }
}

@Composable
private fun NewsEmptyState(onOpenSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Default.Newspaper,
            contentDescription = null,
            tint = HikariTextFaint,
            modifier = Modifier.size(56.dp),
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Keine Nachrichten gefunden — Themen in den Einstellungen prüfen",
            color = HikariText,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = onOpenSettings,
            colors = ButtonDefaults.buttonColors(containerColor = HikariPrimary, contentColor = Color.Black),
        ) {
            Text("Einstellungen öffnen")
        }
    }
}
