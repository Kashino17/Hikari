package com.hikari.app.ui.manga.components

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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hikari.app.data.api.dto.MangaContinueDto
import com.hikari.app.data.api.dto.MangaSeriesDto
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariPrimary
import com.hikari.app.ui.theme.HikariSurfaceHigh

@Composable
fun MangaHero(
    series: MangaSeriesDto,
    cont: MangaContinueDto?,
    onCta: () -> Unit,
    coverUrl: String? = null,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier.fillMaxWidth().aspectRatio(16f / 12f)) {
        if (coverUrl != null) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                HikariPrimary.copy(alpha = 0.12f),
                                HikariSurfaceHigh,
                                HikariCardBg,
                                HikariBg,
                            )
                        )
                    ),
            )
        }
        // Scrim: Titel und CTA müssen auch auf hellen Covern stehen (Muster: MixCard).
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
        AnimatedVisibility(
            visible = true,
            enter = fadeIn(tween(350)) + slideInVertically(
                tween(350, easing = FastOutSlowInEasing),
            ) { it / 10 },
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                Text(
                    text = "MANGA",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 10.sp,
                    letterSpacing = 2.sp,
                )
                Text(
                    text = series.title,
                    color = Color.White,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(top = 8.dp),
                )
                series.author?.let {
                    Text(
                        text = it.uppercase(),
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 11.sp,
                        letterSpacing = 1.sp,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                cont?.let {
                    Text(
                        text = "Kapitel weiterlesen · Seite ${it.pageNumber}",
                        color = HikariPrimary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                }
                HeroCta(
                    label = if (cont != null) "Weiterlesen" else "Lesen",
                    onClick = onCta,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
        }
    }
}

/** Gefüllte Pill mit Press-Scale-Mikroanimation (Muster: SourceCta im News-Tab). */
@Composable
private fun HeroCta(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "hero-cta-press",
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
