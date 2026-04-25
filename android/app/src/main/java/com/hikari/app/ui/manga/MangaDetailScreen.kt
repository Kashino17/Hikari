package com.hikari.app.ui.manga

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hikari.app.ui.manga.components.ArcAccordion
import com.hikari.app.ui.theme.HikariBg

private val Accent = Color(0xFFFBBF24)

@Composable
fun MangaDetailScreen(
    seriesId: String,
    onBack: () -> Unit,
    onChapterClick: (chapterId: String, page: Int?) -> Unit,
    vm: MangaDetailViewModel = hiltViewModel(),
) {
    LaunchedEffect(seriesId) { vm.load(seriesId) }
    val state by vm.uiState.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(HikariBg)) {
        when (val s = state) {
            is MangaDetailUiState.Loading -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Lade…", color = Color.White.copy(alpha = 0.4f), fontSize = 12.sp)
                }
            }
            is MangaDetailUiState.Error -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(s.message, color = Accent, fontSize = 14.sp)
                }
            }
            is MangaDetailUiState.Success -> {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        DetailHero(
                            title = s.detail.title,
                            author = s.detail.author,
                            ctaLabel = if (s.continueItem != null) "Weiterlesen" else "Lesen",
                            onCta = {
                                val ctaChapter = s.continueItem?.chapterId ?: s.detail.chapters.firstOrNull()?.id
                                if (ctaChapter != null) {
                                    onChapterClick(ctaChapter, s.continueItem?.pageNumber)
                                }
                            },
                            onBack = onBack,
                        )
                    }
                    item {
                        ArcAccordion(
                            arcs = s.detail.arcs,
                            chapters = s.detail.chapters,
                            initialExpandedArcId = s.continueItem?.let { c ->
                                s.detail.chapters.firstOrNull { it.id == c.chapterId }?.arcId
                            },
                            onChapterClick = { chapterId -> onChapterClick(chapterId, null) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailHero(
    title: String,
    author: String?,
    ctaLabel: String,
    onCta: () -> Unit,
    onBack: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxWidth().aspectRatio(16f / 12f)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 12f)
                .background(
                    Brush.linearGradient(
                        colors = listOf(Color(0x4DB45309), Color(0xFF18181B), Color(0xFF000000)),
                    )
                ),
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 12f)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0x66000000), Color.Black),
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
                tint = Accent,
            )
        }
        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(20.dp),
        ) {
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
            Button(
                onClick = onCta,
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.Black),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(14.dp))
                Text(ctaLabel, fontSize = 14.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(start = 8.dp))
            }
        }
    }
}
