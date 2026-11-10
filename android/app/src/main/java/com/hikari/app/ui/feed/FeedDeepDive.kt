package com.hikari.app.ui.feed

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.domain.feed.FeedDeepSection
import com.hikari.app.domain.russian.RuDeepdiveLogic
import com.hikari.app.domain.russian.RussianPhonetics
import com.hikari.app.ui.russian.RussianAudioPlayer
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariBorderStrong
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextMuted

private val CYRILLIC_RUN = Regex("[+\\p{IsCyrillic}]+")

/**
 * Russische Einsprengsel in deutschem Fließtext tragen die Betonung als "+" vor dem Vokal
 * (Здр+авствуйте). Für die Anzeige wird daraus der Akzent — andere Pluszeichen bleiben stehen.
 */
internal fun ruProse(text: String): String = CYRILLIC_RUN.replace(text) { m ->
    if (m.value.any { it.isLetter() }) RussianPhonetics.display(m.value) else m.value
}

/** Ein Abschnitt der Vertiefung: Überschrift, Text und (bei Russisch) Sätze zum Anhören. */
@Composable
internal fun DeepDiveSection(section: FeedDeepSection, accent: Color, audio: RussianAudioPlayer?) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(HikariCardBg)
            .border(0.5.dp, HikariBorderStrong, RoundedCornerShape(18.dp))
            .padding(16.dp),
    ) {
        if (section.title.isNotBlank()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(width = 3.dp, height = 16.dp).clip(RoundedCornerShape(2.dp)).background(accent))
                Spacer(Modifier.width(9.dp))
                Text(ruProse(section.title), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 22.sp)
            }
        }
        if (section.text.isNotBlank()) {
            Spacer(Modifier.height(9.dp))
            Text(ruProse(section.text), color = HikariText, fontSize = 14.sp, lineHeight = 22.sp)
        }
        val playing = audio?.playing?.collectAsState()?.value
        section.phrases.forEach { phrase ->
            val clip = RuDeepdiveLogic.clipName(phrase.ru)
            val hasClip = audio?.has(clip) == true
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.White.copy(alpha = 0.04f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(RussianPhonetics.display(phrase.ru), color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, lineHeight = 22.sp)
                    Text(RussianPhonetics.transliterateFlat(phrase.ru), color = HikariTextMuted, fontSize = 12.sp)
                    Text(phrase.de, color = HikariAmber, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    if (phrase.note.isNotBlank()) {
                        Spacer(Modifier.height(3.dp))
                        Text(ruProse(phrase.note), color = HikariText, fontSize = 12.5.sp, lineHeight = 18.sp)
                    }
                }
                if (hasClip) {
                    Spacer(Modifier.width(10.dp))
                    val active = playing == clip
                    Surface(
                        onClick = { audio?.play(clip) },
                        shape = CircleShape,
                        color = if (active) HikariAmber.copy(alpha = 0.28f) else Color.White.copy(alpha = 0.09f),
                        border = BorderStroke(0.8.dp, if (active) HikariAmber else Color.White.copy(alpha = 0.14f)),
                        modifier = Modifier.size(36.dp),
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.AutoMirrored.Filled.VolumeUp,
                                contentDescription = "Anhören",
                                tint = if (active) HikariAmber else Color.White,
                                modifier = Modifier.size(17.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}
