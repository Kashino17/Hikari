package com.hikari.app.ui.manga

import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.foundation.border
import com.hikari.app.domain.sync.SyncStatus
import androidx.compose.runtime.DisposableEffect
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import coil.compose.AsyncImage
import com.hikari.app.ui.manga.components.ArcAccordion
import com.hikari.app.ui.manga.components.MangaCoverFallback
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariPrimary
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint

@Composable
fun MangaDetailScreen(
    seriesId: String,
    onBack: () -> Unit,
    onChapterClick: (chapterId: String, page: Int?) -> Unit,
    vm: MangaDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(seriesId) { vm.load(seriesId) }
    val state by vm.uiState.collectAsState()
    val baseUrl by vm.backendUrl.collectAsState()
    val downloadedArcs by vm.downloadedArcs.collectAsState()
    val arcProgress by vm.arcProgress.collectAsState()
    val syncStatus by vm.syncStatus.collectAsState()
    val syncMessage by vm.syncMessage.collectAsState()
    val hasSync = seriesId.startsWith("onepiecetube:")
    if (hasSync) {
        DisposableEffect(Unit) {
            vm.startSyncPolling()
            onDispose { vm.stopSyncPolling() }
        }
    }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(vm) {
        vm.errors.collect { msg -> snackbarHostState.showSnackbar(msg) }
    }

    Box(modifier = Modifier.fillMaxSize().background(HikariBg)) {
        when (val s = state) {
            is MangaDetailUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Lade…", color = HikariTextFaint, fontSize = 12.sp)
                }
            }
            is MangaDetailUiState.Error -> {
                Column(
                    modifier = Modifier.fillMaxSize().padding(32.dp),
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
                        "Manga konnte nicht geladen werden",
                        color = HikariText,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(s.message, color = HikariTextFaint, fontSize = 13.sp, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { vm.load(seriesId) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HikariPrimary,
                            contentColor = Color.Black,
                        ),
                        shape = RoundedCornerShape(50),
                    ) {
                        Text("Erneut versuchen", fontWeight = FontWeight.Bold)
                    }
                }
            }
            is MangaDetailUiState.Success -> {
                val ctx = androidx.compose.ui.platform.LocalContext.current
                val langs = s.detail.langs.ifEmpty { listOf("de") }
                var lang by remember(seriesId, langs) {
                    val pref = ctx.getSharedPreferences("hikari_manga_catalog", android.content.Context.MODE_PRIVATE).getString("lang", "all")
                    val fromContinue = s.continueItem?.chapterId?.let { com.hikari.app.ui.manga.catalog.langOfChapter(it) }
                    mutableStateOf(fromContinue?.takeIf { it in langs } ?: pref?.takeIf { it in langs } ?: langs.first())
                }
                val chapters = s.detail.chapters.filter { (it.lang ?: "de") == lang }
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        DetailHero(
                            title = s.detail.title,
                            author = s.detail.author,
                            coverUrl = s.detail.coverPath?.let { vm.coverUrl(baseUrl, it) },
                            ctaLabel = if (s.continueItem != null) "Weiterlesen" else "Lesen",
                            onCta = {
                                // Neu anfangen = erstes Kapitel in der gewählten Sprache, nicht das neueste.
                                val ctaChapter = s.continueItem?.chapterId
                                    ?: chapters.filter { it.isAvailable == 1 }.minByOrNull { it.number }?.id
                                if (ctaChapter != null) {
                                    onChapterClick(ctaChapter, s.continueItem?.pageNumber)
                                }
                            },
                            onBack = onBack,
                        )
                    }
                    item {
                        LangAndSource(
                            seriesId = seriesId,
                            langs = langs,
                            counts = langs.associateWith { l -> s.detail.chapters.count { (it.lang ?: "de") == l } },
                            selected = lang,
                            groups = chapters.mapNotNull { it.scanlationGroup }.distinct().take(3),
                            onLang = { lang = it },
                        )
                    }
                    if (hasSync) item {
                        SyncCard(
                            status = syncStatus,
                            message = syncMessage,
                            lastChapter = s.detail.chapters.maxOfOrNull { it.number }?.toInt(),
                            onSync = { vm.startSync(seriesId) },
                        )
                    }
                    item {
                        ArcAccordion(
                            arcs = s.detail.arcs,
                            chapters = chapters,
                            initialExpandedArcId = s.continueItem?.let { c ->
                                s.detail.chapters.firstOrNull { it.id == c.chapterId }?.arcId
                            },
                            onChapterClick = { chapterId -> onChapterClick(chapterId, null) },
                            downloadedArcIds = downloadedArcs,
                            arcProgress = arcProgress,
                            onDownloadArc = { arcId -> vm.downloadArc(arcId) },
                        )
                    }
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        ) { data ->
            // Material-Default ist hell — auf die dunklen Hikari-Flächen abstimmen.
            Snackbar(
                snackbarData = data,
                containerColor = HikariSurfaceHigh,
                contentColor = HikariText,
                shape = RoundedCornerShape(12.dp),
            )
        }
    }
}

@Composable
private fun DetailHero(
    title: String,
    author: String?,
    coverUrl: String?,
    ctaLabel: String,
    onCta: () -> Unit,
    onBack: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 12f)) {
        if (coverUrl != null) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            MangaCoverFallback(title = title)
        }
        // Scrim: Text und CTA müssen auch auf hellen Covern stehen.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.3f to HikariBg.copy(alpha = 0.30f),
                        0.7f to HikariBg.copy(alpha = 0.85f),
                        1f to HikariBg.copy(alpha = 0.97f),
                    )
                ),
        )
        IconButton(
            onClick = onBack,
            modifier = Modifier.padding(8.dp).align(Alignment.TopStart),
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Zurück",
                tint = HikariPrimary,
            )
        }
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(tween(350)) + slideInVertically(
                tween(350, easing = FastOutSlowInEasing),
            ) { it / 10 },
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text("MANGA", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp, letterSpacing = 2.sp)
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(top = 8.dp),
                )
                author?.let {
                    Text(it.uppercase(), color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp, letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 4.dp))
                }
                DetailCta(
                    label = ctaLabel,
                    onClick = onCta,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}

/** Gefüllte Pill mit Press-Scale-Mikroanimation (Muster: SourceCta im News-Tab). */
@Composable
private fun DetailCta(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "detail-cta-press",
    )
    Row(
        modifier = modifier
            .scale(pressScale)
            .clip(RoundedCornerShape(50))
            .background(HikariPrimary)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = null,
            tint = Color.Black,
            modifier = Modifier.size(15.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = label,
            color = Color.Black,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}

/** Nur One Piece: neue Kapitel direkt von der Quelle holen — mit Fortschritt, solange es läuft. */
@Composable
private fun SyncCard(status: SyncStatus, message: String?, lastChapter: Int?, onSync: () -> Unit) {
    val job = (status as? SyncStatus.Active)?.job
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(HikariSurfaceHigh)
            .padding(start = 16.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("Neue Kapitel holen", color = HikariText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            val sub = when {
                job != null && job.totalChapters > 0 -> "Lädt Kapitel ${job.doneChapters} von ${job.totalChapters} …"
                job != null -> "Sucht nach neuen Kapiteln …"
                message != null -> message
                lastChapter != null -> "Bis Kapitel $lastChapter da · holt neue von onepiece.tube"
                else -> "Gleicht mit onepiece.tube ab"
            }
            Text(sub, color = HikariTextFaint, fontSize = 12.sp)
            if (job != null && job.totalChapters > 0) {
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.LinearProgressIndicator(
                    progress = { job.doneChapters.toFloat() / job.totalChapters },
                    modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(3.dp)),
                    color = HikariPrimary,
                    trackColor = Color.White.copy(alpha = 0.1f),
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        if (job == null) {
            Text(
                "Abgleichen",
                color = Color.Black,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(HikariPrimary)
                    .clickable(onClick = onSync)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
    }
}

/** Sprache wählen (mit Kapitelzahl) und ehrlich sagen, woher die Kapitel kommen. */
@Composable
private fun LangAndSource(
    seriesId: String,
    langs: List<String>,
    counts: Map<String, Int>,
    selected: String,
    groups: List<String>,
    onLang: (String) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (l in listOf("de", "en")) {
                val has = l in langs
                val on = l == selected
                Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                        .background(if (on) com.hikari.app.ui.manga.MangaAccent.copy(alpha = 0.16f) else HikariSurfaceHigh)
                        .border(1.dp, if (on) com.hikari.app.ui.manga.MangaAccent else Color.Transparent, RoundedCornerShape(12.dp))
                        .clickable(enabled = has) { onLang(l) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    com.hikari.app.ui.manga.catalog.LangBadge(l, big = true)
                    Text(
                        if (has) "${counts[l] ?: 0} Kapitel" else "nicht da",
                        color = if (has) HikariText else HikariTextFaint, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = 10.dp),
                    )
                }
            }
        }
        val source = when {
            seriesId.startsWith("mangadex:") -> "Fan-Übersetzung über MangaDex" + if (groups.isNotEmpty()) " · ${groups.joinToString(", ")}" else ""
            seriesId.startsWith("onepiecetube:") -> "Fan-Übersetzung von onepiece.tube"
            else -> null
        }
        if (source != null) Text(source, color = HikariTextFaint, fontSize = 12.sp, maxLines = 2, modifier = Modifier.padding(top = 8.dp))
    }
}
