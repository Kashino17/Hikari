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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
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
import com.hikari.app.domain.russian.RuGlossData
import com.hikari.app.domain.russian.RuPhrase
import com.hikari.app.domain.russian.RussianGlossary
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

/** Umschrift eines einzelnen Wortes (Spans aus [RussianPhonetics.words]). */
internal fun latinAnnotated(spans: List<RussianPhonetics.Span>): AnnotatedString = buildAnnotatedString {
    for (span in spans) {
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

/** Glossen-Daten einmal pro Prozess aus den Assets laden. */
@Composable
internal fun rememberGlossData(): RuGlossData {
    val context = LocalContext.current
    return remember { RussianGlossary.load(context.assets) }
}

/**
 * Wort-für-Wort-Zeile: Umschrift groß (was du sprichst), darunter klein die
 * deutsche Bedeutung genau dieses Wortes. Feste Wendungen (потому что =
 * »weil«) stehen als ein Block. Kyrillisch nur dezent als eine Zeile
 * darunter — zum Gewöhnen, nicht zum Lernen.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun RuInterlinear(
    ru: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 24.sp,
    center: Boolean = true,
    showGloss: Boolean = true,
    showCyrillic: Boolean = true,
    latinColor: Color = HikariText,
    latinWeight: FontWeight = FontWeight.Medium,
) {
    val data = rememberGlossData()
    val chunks = remember(ru, data) { RussianGlossary.chunks(ru, data) }
    val glossSize = (size.value * 0.5f).coerceIn(11f, 15f).sp
    Column(
        modifier,
        horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(
                (size.value * 0.38f).dp,
                if (center) Alignment.CenterHorizontally else Alignment.Start,
            ),
            verticalArrangement = Arrangement.spacedBy(if (showGloss) 10.dp else 2.dp),
        ) {
            for (chunk in chunks) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val latin = buildAnnotatedString {
                        chunk.words.forEachIndexed { i, w ->
                            if (i > 0) append(' ')
                            append(w.before)
                            append(latinAnnotated(w.latin))
                            append(w.after)
                        }
                    }
                    Text(
                        latin,
                        color = latinColor,
                        fontSize = size,
                        fontWeight = latinWeight,
                        lineHeight = size * 1.15f,
                        maxLines = 1,
                    )
                    if (showGloss) {
                        Text(
                            chunk.gloss ?: " ",
                            color = HikariTextMuted,
                            fontSize = glossSize,
                            lineHeight = glossSize * 1.2f,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            modifier = Modifier.widthIn(max = 160.dp),
                        )
                    }
                }
            }
        }
        if (showCyrillic) {
            Spacer(Modifier.height(if (showGloss) 10.dp else 6.dp))
            Text(
                RussianPhonetics.display(ru),
                color = HikariTextFaint,
                fontSize = (size.value * 0.55f).coerceIn(12f, 16f).sp,
                textAlign = if (center) TextAlign.Center else TextAlign.Start,
            )
        }
    }
}

/** Starthilfe: nur das erste Wort (bzw. die erste Wendung) in Umschrift. */
@Composable
internal fun RuFirstWordHint(ru: String, modifier: Modifier = Modifier) {
    val data = rememberGlossData()
    val first = remember(ru, data) { RussianGlossary.chunks(ru, data).firstOrNull() } ?: return
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text("Starthilfe", color = HikariTextFaint, fontSize = 12.sp)
        Text(
            buildAnnotatedString {
                first.words.forEachIndexed { i, w ->
                    if (i > 0) append(' ')
                    append(latinAnnotated(w.latin))
                }
                append(" …")
            },
            color = HikariText,
            fontSize = 22.sp,
            fontWeight = FontWeight.SemiBold,
        )
        first.gloss?.let { Text(it, color = HikariTextMuted, fontSize = 13.sp) }
        Text(
            first.words.joinToString(" ") { it.cyrillic },
            color = HikariTextFaint,
            fontSize = 12.sp,
        )
    }
}

/**
 * Kompakte Listen-Zeile (Satzbuch, Podcast, Antwort-Karten): Umschrift
 * linksbündig, Bedeutungen darunter, Kyrillisch dezent.
 */
@Composable
internal fun RuCompactPhrase(
    ru: String,
    modifier: Modifier = Modifier,
    size: TextUnit = 16.sp,
    showGloss: Boolean = true,
    center: Boolean = false,
) {
    RuInterlinear(ru, modifier, size = size, center = center, showGloss = showGloss)
}

/**
 * Lern-Satz: Wort-für-Wort-Umschrift mit Bedeutungen, Kyrillisch dezent,
 * optional die ganze deutsche Übersetzung. [cyrillicSize] heißt aus
 * historischen Gründen so — es ist die Grundgröße des Blocks.
 */
@Composable
internal fun RuPhraseBlock(
    phrase: RuPhrase,
    modifier: Modifier = Modifier,
    cyrillicSize: TextUnit = 30.sp,
    showGerman: Boolean = true,
    center: Boolean = true,
    showGloss: Boolean = showGerman,
) {
    val align = if (center) TextAlign.Center else TextAlign.Start
    Column(
        modifier,
        horizontalAlignment = if (center) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        RuInterlinear(
            phrase.ru,
            size = (cyrillicSize.value * 0.8f).sp,
            center = center,
            showGloss = showGloss,
        )
        if (showGerman) {
            Spacer(Modifier.height(12.dp))
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
    trailingIcon: ImageVector? = null,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, spring(dampingRatio = 0.6f), label = "btn")
    Box(
        modifier
            .fillMaxWidth()
            .heightIn(min = 54.dp)
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .background(if (enabled) container else HikariSurfaceHigh)
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(Modifier.padding(horizontal = 18.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text,
                modifier = if (trailingIcon != null) Modifier.weight(1f) else Modifier,
                color = if (enabled) Color.Black else HikariTextFaint,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
            trailingIcon?.let { Icon(it, null, tint = if (enabled) Color.Black else HikariTextFaint, modifier = Modifier.size(20.dp)) }
        }
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
