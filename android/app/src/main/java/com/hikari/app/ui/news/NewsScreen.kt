package com.hikari.app.ui.news

import android.content.Intent
import android.net.Uri
import androidx.annotation.OptIn as AnnotOptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Newspaper
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
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
 * Täglicher KI-Tagesbericht: vertikaler Swipe-Feed über die News-Beiträge.
 * Fortschritt oben, eine Slide pro Beitrag: Hero (oder gestaltete Text-Karte),
 * Meta-Zeile, Titel, Zusammenfassung, klarer CTA.
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
                vm.loading -> NewsLoadingSkeleton()
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

    Column(Modifier.fillMaxSize()) {
        // Lesefortschritt: animierte Linie + Zähler — wo bin ich im Bericht.
        val progress by animateFloatAsState(
            targetValue = (settledPage + 1).toFloat() / items.size,
            animationSpec = tween(350, easing = FastOutSlowInEasing),
            label = "news-progress",
        )
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.weight(1f).height(2.dp).clip(RoundedCornerShape(1.dp)),
                color = HikariPrimary,
                trackColor = HikariSurfaceHigh,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "${settledPage + 1} / ${items.size}",
                color = HikariTextFaint,
                fontSize = 11.sp,
            )
        }
        Spacer(Modifier.height(10.dp))

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
}

@Composable
private fun NewsSlide(item: NewsItem, isCurrent: Boolean, showSwipeHint: Boolean) {
    val ctx = LocalContext.current
    Column(
        Modifier.fillMaxSize().padding(horizontal = 16.dp),
    ) {
        NewsHero(item = item, isCurrent = isCurrent, modifier = Modifier.fillMaxWidth())

        Spacer(Modifier.height(16.dp))

        // Text-Block fährt sanft ein, sobald die Slide zur aktiven wird.
        AnimatedVisibility(
            visible = isCurrent,
            enter = fadeIn(tween(350)) + slideInVertically(tween(350, easing = FastOutSlowInEasing)) { it / 10 },
        ) {
            Column {
                // Eine Meta-Zeile statt freischwebender Angaben: Thema, Quelle, Zeit.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = HikariCardBg, shape = RoundedCornerShape(50)) {
                        Text(
                            item.topic.replaceFirstChar { it.uppercase() },
                            color = HikariPrimary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                    val relTime = remember(item.publishedAt) { formatRelativeTime(item.publishedAt) }
                    Text(
                        text = "  ·  ${item.source}" + if (relTime.isNotEmpty()) "  ·  $relTime" else "",
                        color = HikariTextFaint,
                        fontSize = 12.sp,
                    )
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    item.title,
                    color = HikariText,
                    fontSize = 21.sp,
                    fontWeight = FontWeight.Bold,
                    lineHeight = 27.sp,
                    maxLines = 4,
                )

                if (item.summary.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        item.summary,
                        color = HikariTextMuted,
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        maxLines = 8,
                    )
                }

                if (item.url.isNotBlank()) {
                    Spacer(Modifier.height(18.dp))
                    SourceCta(onClick = {
                        runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url))) }
                    })
                }
            }
        }

        Spacer(Modifier.weight(1f))

        if (showSwipeHint) {
            SwipeHint()
        } else {
            Spacer(Modifier.height(16.dp))
        }
    }
}

/** Deutlicher CTA: gefüllte Pill mit Icon und Press-Feedback statt Textlink. */
@Composable
private fun SourceCta(onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = tween(120),
        label = "cta-press",
    )
    Row(
        Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(50))
            .background(HikariPrimary)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Quelle lesen",
            color = Color.Black,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.width(7.dp))
        Icon(
            Icons.AutoMirrored.Filled.OpenInNew,
            contentDescription = null,
            tint = Color.Black,
            modifier = Modifier.size(15.dp),
        )
    }
}

/** Sanft wippender Pfeil als Wisch-Hinweis auf der ersten Slide. */
@Composable
private fun SwipeHint() {
    val transition = rememberInfiniteTransition(label = "swipe-hint")
    val offsetY by transition.animateFloat(
        initialValue = 0f,
        targetValue = -7f,
        animationSpec = infiniteRepeatable(tween(700, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "swipe-hint-y",
    )
    Row(
        Modifier.fillMaxWidth().padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Default.KeyboardArrowUp,
            contentDescription = null,
            tint = HikariTextMuted,
            modifier = Modifier.size(18.dp).offset(y = offsetY.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            "Nach oben wischen für die nächste Nachricht",
            color = HikariTextFaint,
            fontSize = 12.sp,
        )
    }
}

/**
 * Hero-Bereich einer Slide: Bild (16:9) mit Zweitbild-Kachel. Ohne Bild keine
 * leere graue Fläche, sondern eine gestaltete Quellen-Karte. Video startet
 * erst per Tap auf den Play-Button — kein Autoplay.
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
                SourceTextCard(source = item.source)
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
 * Wenn ein Artikel kein Bild liefert (Google-News-Meldungen), bekommt die
 * Quelle die Bühne: Initial im Kreis plus Name — sieht gewollt aus statt leer.
 */
@Composable
private fun SourceTextCard(source: String) {
    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    listOf(HikariSurfaceHigh, HikariCardBg, HikariBg),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .size(72.dp)
                    .background(HikariPrimary.copy(alpha = 0.15f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    source.firstOrNull()?.uppercase() ?: "?",
                    color = HikariPrimary,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(
                source,
                color = HikariTextMuted,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            )
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

/** Pulsierendes Skeleton im Slide-Layout statt nacktem Spinner. */
@Composable
private fun NewsLoadingSkeleton() {
    val transition = rememberInfiniteTransition(label = "skeleton")
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(800, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "skeleton-alpha",
    )
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).alpha(alpha)) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(16.dp))
                .background(HikariSurfaceHigh),
        )
        Spacer(Modifier.height(16.dp))
        Box(Modifier.width(140.dp).height(18.dp).clip(RoundedCornerShape(50)).background(HikariSurfaceHigh))
        Spacer(Modifier.height(14.dp))
        Box(Modifier.fillMaxWidth().height(24.dp).clip(RoundedCornerShape(5.dp)).background(HikariSurfaceHigh))
        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth(0.7f).height(24.dp).clip(RoundedCornerShape(5.dp)).background(HikariSurfaceHigh))
        Spacer(Modifier.height(14.dp))
        repeat(3) {
            Box(Modifier.fillMaxWidth().height(14.dp).clip(RoundedCornerShape(4.dp)).background(HikariSurfaceHigh))
            Spacer(Modifier.height(7.dp))
        }
    }
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
