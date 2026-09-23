package com.hikari.app.ui.russian

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.launch
import com.hikari.app.domain.russian.RuDay
import com.hikari.app.domain.russian.RuGender
import com.hikari.app.domain.russian.RuProgress
import com.hikari.app.domain.russian.RuSrs
import com.hikari.app.domain.russian.RussianCourseRepository
import com.hikari.app.domain.russian.RussianProgressStore
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariBorder
import com.hikari.app.ui.theme.HikariBorderStrong
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariSurface
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class RussianHomeViewModel @Inject constructor(
    repo: RussianCourseRepository,
    private val store: RussianProgressStore,
    private val audio: RussianAudioPlayer,
) : ViewModel() {
    val index = repo.index
    val progress = store.state
    val totalItems = index.itemById.size

    fun today(): Long = store.today()

    /** Vom Russisch-Bereich aufgerufen (nicht vom Profil-Hub): Audio vorwärmen. */
    fun warmUpAudio() {
        viewModelScope.launch { audio.warmUp() }
    }

    fun setGender(g: RuGender) = store.update { it.copy(gender = g, voice = it.voice ?: g) }

    fun setVoice(v: RuGender) = store.update { it.copy(voice = v) }
}

@Composable
fun RussianHomeScreen(
    onBack: () -> Unit,
    onOpen: (route: String) -> Unit,
    vm: RussianHomeViewModel = hiltViewModel(),
) {
    val p by vm.progress.collectAsState()
    val today = vm.today()
    val days = vm.index.days
    val unlocked = p.unlockedDay(days.size)
    val allDone = p.dayStars.size >= days.size
    val due = RuSrs.dueIds(p.cards, today).size
    var settingsOpen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.warmUpAudio() }

    LazyColumn(
        Modifier.fillMaxSize().background(HikariBg),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundIcon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", onBack)
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text("Russisch", color = HikariText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    Text("In 14 Tagen zum ersten Small Talk", color = HikariTextMuted, fontSize = 12.5.sp)
                }
                RoundIcon(Icons.Outlined.Tune, "Einstellungen") { settingsOpen = true }
            }
        }

        if (p.gender == null) {
            item { GenderIntro(onPick = vm::setGender) }
        }

        item {
            val current = days.firstOrNull { it.day == unlocked }
            HeroCard(
                day = current,
                done = p.dayStars.size,
                total = days.size,
                allDone = allDone,
                enabled = p.gender != null,
                onStart = { current?.let { onOpen("russian/lesson/${it.day}") } },
            )
        }

        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                StatTile(
                    "${RuSrs.visibleStreak(p, today)}",
                    "Tage Serie",
                    Icons.Outlined.LocalFireDepartment,
                    Modifier.weight(1f),
                )
                StatTile("${p.xpOn(today)}", "XP heute", null, Modifier.weight(1f))
                StatTile("${RuSrs.knownCount(p.cards)}/${vm.totalItems}", "sitzen", null, Modifier.weight(1f))
            }
        }

        item {
            ReviewCard(due = due, learned = p.cards.size, enabled = p.gender != null) {
                onOpen("russian/review")
            }
        }

        item {
            RuOverline("Üben & spielen", Modifier.padding(start = 16.dp, top = 26.dp, bottom = 12.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    PracticeCard("Hör-Blitz", "60 Sekunden hören und verstehen", Icons.Outlined.Timer,
                        if (p.bestBlitz > 0) "Rekord ${p.bestBlitz}" else null) { onOpen("russian/blitz") }
                }
                item {
                    PracticeCard("Paare finden", "Russisch und Deutsch verbinden", Icons.Outlined.Extension,
                        if (p.bestPairsSeconds > 0) "Bestzeit ${p.bestPairsSeconds} s" else null) { onOpen("russian/pairs") }
                }
                item {
                    PracticeCard("Gespräche", "Ein zufälliges Rollenspiel aus deinen Tagen", Icons.Outlined.Forum, null) {
                        onOpen("russian/dialog/${p.dayStars.keys.randomOrNull() ?: 1}")
                    }
                }
                item {
                    PracticeCard("Satzbuch", "Alles Gelernte zum Anhören", Icons.AutoMirrored.Outlined.MenuBook, null) {
                        onOpen("russian/phrases")
                    }
                }
            }
        }

        item { RuOverline("Dein Plan", Modifier.padding(start = 16.dp, top = 26.dp, bottom = 8.dp)) }
        items(days, key = { it.day }) { d ->
            DayRow(
                day = d,
                stars = p.dayStars[d.day] ?: 0,
                current = d.day == unlocked && !allDone,
                locked = d.day > unlocked,
                enabled = p.gender != null,
                onLesson = { onOpen("russian/lesson/${d.day}") },
                onDialog = { onOpen("russian/dialog/${d.day}") },
            )
        }
    }

    if (settingsOpen) {
        SettingsDialog(p, onGender = vm::setGender, onVoice = vm::setVoice, onClose = { settingsOpen = false })
    }
}

@Composable
private fun RoundIcon(icon: ImageVector, desc: String, onClick: () -> Unit) {
    Box(
        Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = desc, tint = HikariText) }
}

@Composable
private fun GenderIntro(onPick: (RuGender) -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(HikariCardBg)
            .border(1.dp, HikariAmber.copy(alpha = 0.35f), RoundedCornerShape(18.dp))
            .padding(18.dp),
    ) {
        RuOverline("Bevor es losgeht", color = HikariAmber)
        Spacer(Modifier.height(6.dp))
        Text(
            "Im Russischen ändern manche Ich-Sätze ihre Form: я уста́л (Mann) · я уста́ла (Frau). Welche Form sollst du lernen?",
            color = HikariText,
            fontSize = 14.5.sp,
            lineHeight = 21.sp,
        )
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            RuPrimaryButton("я уста́л", Modifier.weight(1f)) { onPick(RuGender.MALE) }
            RuPrimaryButton("я уста́ла", Modifier.weight(1f)) { onPick(RuGender.FEMALE) }
        }
    }
}

@Composable
private fun HeroCard(day: RuDay?, done: Int, total: Int, allDone: Boolean, enabled: Boolean, onStart: () -> Unit) {
    Column(
        Modifier
            .padding(16.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(
                Brush.linearGradient(listOf(HikariSurfaceHigh, HikariSurface, HikariCardBg)),
            )
            .border(1.dp, HikariBorder, RoundedCornerShape(22.dp))
            .padding(20.dp),
    ) {
        if (allDone || day == null) {
            RuOverline("Kurs abgeschlossen", color = HikariAmber)
            Spacer(Modifier.height(8.dp))
            Text("Молоде́ц! Alle 14 Tage geschafft.", color = HikariText, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Halte es frisch: täglich wiederholen, Rollenspiele laut mitsprechen, Hör-Blitz spielen.",
                color = HikariTextMuted,
                fontSize = 14.sp,
            )
            return@Column
        }
        RuOverline("Heute · Tag ${day.day} von $total", color = HikariAmber)
        Spacer(Modifier.height(8.dp))
        Text(day.title, color = HikariText, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(day.goal, color = HikariTextMuted, fontSize = 14.sp, lineHeight = 20.sp)
        Spacer(Modifier.height(10.dp))
        Text(
            "${day.items.size} neue Sätze · Aussprache: ${day.sound.title}",
            color = HikariTextFaint,
            fontSize = 12.5.sp,
        )
        Spacer(Modifier.height(16.dp))
        // Fortschritt: ein Segment pro Tag.
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(total) { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(if (i < done) HikariAmber else HikariBorderStrong),
                )
            }
        }
        Spacer(Modifier.height(18.dp))
        RuPrimaryButton("Tag ${day.day} starten", enabled = enabled, onClick = onStart)
    }
}

@Composable
private fun StatTile(value: String, label: String, icon: ImageVector?, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(14.dp))
            .background(HikariCardBg)
            .padding(vertical = 12.dp, horizontal = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (icon != null) {
                Icon(icon, null, tint = HikariAmber, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(4.dp))
            }
            Text(value, color = HikariText, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(label, color = HikariTextMuted, fontSize = 11.5.sp)
    }
}

@Composable
private fun ReviewCard(due: Int, learned: Int, enabled: Boolean, onStart: () -> Unit) {
    Row(
        Modifier
            .padding(start = 16.dp, end = 16.dp, top = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(HikariCardBg)
            .border(1.dp, if (due > 0) HikariAmber.copy(alpha = 0.35f) else HikariBorder, RoundedCornerShape(16.dp))
            .clickable(enabled = enabled && due > 0, onClick = onStart)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Replay, null, tint = if (due > 0) HikariAmber else HikariTextMuted, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Wiederholen", color = HikariText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
            Text(
                when {
                    learned == 0 -> "Nach deiner ersten Lektion geht's hier weiter."
                    due == 0 -> "Alles frisch — neue Karten werden morgen fällig."
                    due == 1 -> "1 Karte ist fällig"
                    else -> "$due Karten sind fällig"
                },
                color = HikariTextMuted,
                fontSize = 13.sp,
            )
        }
        if (due > 0) {
            Box(
                Modifier.clip(RoundedCornerShape(10.dp)).background(Color.White).padding(horizontal = 12.dp, vertical = 7.dp),
            ) { Text("Los", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
private fun PracticeCard(title: String, subtitle: String, icon: ImageVector, badge: String?, onClick: () -> Unit) {
    Column(
        Modifier
            .width(168.dp)
            .height(132.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(HikariCardBg)
            .border(1.dp, HikariBorder, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
    ) {
        Icon(icon, null, tint = HikariAmber, modifier = Modifier.size(22.dp))
        Spacer(Modifier.weight(1f))
        Text(title, color = HikariText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = HikariTextMuted, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
        if (badge != null) Text(badge, color = HikariAmber.copy(alpha = 0.85f), fontSize = 11.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun DayRow(
    day: RuDay,
    stars: Int,
    current: Boolean,
    locked: Boolean,
    enabled: Boolean,
    onLesson: () -> Unit,
    onDialog: () -> Unit,
) {
    val done = stars > 0
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled && !locked, onClick = onLesson)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(if (done) HikariAmber.copy(alpha = 0.15f) else Color.Transparent)
                .border(
                    1.5.dp,
                    when {
                        done -> HikariAmber
                        current -> Color.White
                        else -> HikariBorderStrong
                    },
                    CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            when {
                done -> Icon(Icons.Outlined.Check, null, tint = HikariAmber, modifier = Modifier.size(20.dp))
                locked -> Icon(Icons.Outlined.Lock, null, tint = HikariTextFaint, modifier = Modifier.size(16.dp))
                else -> Text("${day.day}", color = HikariText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Tag ${day.day} · ${day.title}",
                color = if (locked) HikariTextFaint else HikariText,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
            )
            Text(
                day.goal,
                color = if (locked) HikariTextFaint.copy(alpha = 0.6f) else HikariTextMuted,
                fontSize = 12.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (done) {
                Row(Modifier.padding(top = 3.dp)) {
                    repeat(3) { i ->
                        Icon(
                            Icons.Filled.Star,
                            null,
                            tint = if (i < stars) HikariAmber else HikariBorderStrong,
                            modifier = Modifier.size(13.dp),
                        )
                    }
                }
            }
        }
        if (done) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, HikariBorderStrong, RoundedCornerShape(10.dp))
                    .clickable(onClick = onDialog)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Forum, null, tint = HikariTextMuted, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text("Gespräch", color = HikariTextMuted, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun SettingsDialog(
    p: RuProgress,
    onGender: (RuGender) -> Unit,
    onVoice: (RuGender) -> Unit,
    onClose: () -> Unit,
) {
    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(HikariSurface)
                .padding(20.dp),
        ) {
            Text("Einstellungen", color = HikariText, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(18.dp))
            RuOverline("Deine Ich-Form")
            Spacer(Modifier.height(8.dp))
            Segmented(
                left = "Mann · я уста́л",
                right = "Frau · я уста́ла",
                rightSelected = p.gender == RuGender.FEMALE,
                onLeft = { onGender(RuGender.MALE) },
                onRight = { onGender(RuGender.FEMALE) },
            )
            Spacer(Modifier.height(18.dp))
            RuOverline("Stimme für Vokabeln")
            Spacer(Modifier.height(8.dp))
            Segmented(
                left = "Männlich",
                right = "Weiblich",
                rightSelected = p.settings.voice == RuGender.FEMALE,
                onLeft = { onVoice(RuGender.MALE) },
                onRight = { onVoice(RuGender.FEMALE) },
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Sätze mit Geschlechtsform klingen immer in der passenden Stimme.",
                color = HikariTextFaint,
                fontSize = 12.sp,
            )
            Spacer(Modifier.height(20.dp))
            RuPrimaryButton("Fertig", onClick = onClose)
        }
    }
}

@Composable
private fun Segmented(left: String, right: String, rightSelected: Boolean, onLeft: () -> Unit, onRight: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(HikariCardBg).padding(4.dp),
    ) {
        listOf(false to left, true to right).forEach { (isRight, label) ->
            val selected = isRight == rightSelected
            Box(
                Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(9.dp))
                    .background(if (selected) Color.White else Color.Transparent)
                    .clickable { if (isRight) onRight() else onLeft() }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    color = if (selected) Color.Black else HikariTextMuted,
                    fontSize = 13.sp,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                )
            }
        }
    }
}
