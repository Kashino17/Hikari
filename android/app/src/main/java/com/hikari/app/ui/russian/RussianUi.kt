package com.hikari.app.ui.russian

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.SlowMotionVideo
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.domain.russian.RuPhrase
import com.hikari.app.domain.russian.RussianPhonetics
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariBorder
import com.hikari.app.ui.theme.HikariBorderStrong
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariDanger
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted

internal val RuGood = Color(0xFF34D399)
internal val RuBad = HikariDanger

/** Umschrift mit hervorgehobener Betonung (Amber, fett, unterstrichen). */
internal fun translitAnnotated(ru: String): AnnotatedString = buildAnnotatedString {
    for (span in RussianPhonetics.transliterate(ru)) {
        if (span.stressed) {
            withStyle(
                SpanStyle(
                    color = HikariAmber,
                    fontWeight = FontWeight.Bold,
                    textDecoration = TextDecoration.Underline,
                ),
            ) { append(span.text) }
        } else {
            append(span.text)
        }
    }
}

/** Kyrillisch groß, Umschrift darunter, optional Bedeutung. */
@Composable
internal fun RuPhraseBlock(
    phrase: RuPhrase,
    modifier: Modifier = Modifier,
    cyrillicSize: TextUnit = 30.sp,
    showGerman: Boolean = true,
    center: Boolean = true,
) {
    val align = if (center) TextAlign.Center else TextAlign.Start
    Column(
        modifier,
        horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Text(
            phrase.display,
            color = HikariText,
            fontSize = cyrillicSize,
            fontWeight = FontWeight.SemiBold,
            textAlign = align,
            lineHeight = cyrillicSize * 1.2f,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            translitAnnotated(phrase.ru),
            color = HikariTextMuted,
            fontSize = (cyrillicSize.value * 0.62f).sp,
            textAlign = align,
            lineHeight = (cyrillicSize.value * 0.8f).sp,
        )
        if (showGerman) {
            Spacer(Modifier.height(10.dp))
            Text(
                phrase.de,
                color = HikariText.copy(alpha = 0.8f),
                fontSize = (cyrillicSize.value * 0.52f).sp,
                textAlign = align,
            )
        }
    }
}

/** Runder weißer Abspiel-Knopf + "Langsam"-Knopf daneben. */
@Composable
internal fun RuPlayRow(
    playingNow: Boolean,
    onPlay: () -> Unit,
    onSlow: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
        val pulse by animateFloatAsState(if (playingNow) 1.08f else 1f, tween(220), label = "play-pulse")
        Box(
            Modifier
                .size(size)
                .scale(pulse)
                .clip(CircleShape)
                .background(Color.White)
                .clickable(onClick = onPlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.AutoMirrored.Outlined.VolumeUp,
                contentDescription = "Anhören",
                tint = Color.Black,
                modifier = Modifier.size(size * 0.45f),
            )
        }
        Spacer(Modifier.width(14.dp))
        Box(
            Modifier
                .size(size * 0.72f)
                .clip(CircleShape)
                .border(1.dp, HikariBorderStrong, CircleShape)
                .clickable(onClick = onSlow),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.SlowMotionVideo,
                contentDescription = "Langsam anhören",
                tint = HikariText,
                modifier = Modifier.size(size * 0.36f),
            )
        }
    }
}

/** Primärknopf im Hikari-Stil: weiß mit schwarzer Schrift. */
@Composable
internal fun RuPrimaryButton(
    text: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    container: Color = Color.White,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(dampingRatio = 0.6f), label = "btn")
    Box(
        modifier
            .fillMaxWidth()
            .height(54.dp)
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) container else HikariSurfaceHigh)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text,
            color = if (enabled) Color.Black else HikariTextFaint,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
internal fun RuGhostButton(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .border(1.dp, HikariBorderStrong, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = HikariText, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

enum class RuOptionState { Idle, Selected, Correct, Wrong, Dimmed }

/** Antwortkarte für Auswahlübungen. */
@Composable
internal fun RuOptionCard(
    state: RuOptionState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val border = when (state) {
        RuOptionState.Correct -> RuGood
        RuOptionState.Wrong -> RuBad
        RuOptionState.Selected -> HikariAmber
        else -> HikariBorder
    }
    val bg = when (state) {
        RuOptionState.Correct -> RuGood.copy(alpha = 0.10f)
        RuOptionState.Wrong -> RuBad.copy(alpha = 0.10f)
        else -> HikariCardBg
    }
    val alpha = if (state == RuOptionState.Dimmed) 0.45f else 1f
    Box(
        modifier
            .fillMaxWidth()
            .alpha(alpha)
            .clip(RoundedCornerShape(14.dp))
            .background(bg)
            .border(if (state == RuOptionState.Idle || state == RuOptionState.Dimmed) 1.dp else 1.5.dp, border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        content()
    }
}

/** Kopfzeile einer Sitzung: Schließen + dünner Fortschrittsbalken. */
@Composable
internal fun RuSessionTopBar(progress: Float, onClose: () -> Unit, trailing: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 8.dp, end = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClose),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Close, contentDescription = "Schließen", tint = HikariTextMuted)
        }
        Spacer(Modifier.width(6.dp))
        val animated by animateFloatAsState(progress.coerceIn(0f, 1f), tween(350), label = "progress")
        Box(
            Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(HikariSurfaceHigh),
        ) {
            Box(
                Modifier.fillMaxWidth(animated).height(6.dp).clip(RoundedCornerShape(3.dp)).background(HikariAmber),
            )
        }
        if (trailing != null) {
            Spacer(Modifier.width(12.dp))
            Text(trailing, color = HikariTextMuted, fontSize = 12.sp, fontWeight = FontWeight.Medium)
        }
    }
}

/**
 * Buchstaben-Lupe: jedes kyrillische Zeichen über seinem Grundlaut — zum
 * Gewöhnen an die Schrift, nicht zum Pauken.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RuLetterLens(ru: String, modifier: Modifier = Modifier) {
    val pairs = remember(ru) { RussianPhonetics.letterPairs(ru) }
    FlowRow(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        pairs.forEach { pair ->
            if (pair == null) {
                Spacer(Modifier.width(10.dp))
            } else {
                Column(
                    Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(HikariSurfaceHigh)
                        .padding(horizontal = 7.dp, vertical = 5.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(pair.first.toString(), color = HikariText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                    Text(pair.second, color = HikariTextMuted, fontSize = 10.5.sp)
                }
            }
        }
    }
}

/** Kleines Label in Versalien über Abschnitten. */
@Composable
internal fun RuOverline(text: String, modifier: Modifier = Modifier, color: Color = HikariTextFaint) {
    Text(
        text.uppercase(),
        modifier = modifier,
        color = color,
        fontSize = 10.5.sp,
        fontWeight = FontWeight.SemiBold,
        letterSpacing = 1.6.sp,
    )
}
