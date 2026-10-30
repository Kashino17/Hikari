package com.hikari.app.ui.russian

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.LocalFireDepartment
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.domain.russian.RuDay
import com.hikari.app.domain.russian.RuSrs
import com.hikari.app.domain.russian.RussianCourseRepository
import com.hikari.app.domain.russian.RussianProgressStore
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariBorder
import com.hikari.app.ui.theme.HikariBorderStrong
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariSurface
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class RussianPodcastViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: RussianCourseRepository,
    private val store: RussianProgressStore,
    val audio: RussianAudioPlayer,
) : ViewModel() {
    val index = repo.podcastIndex
    val progress = store.state
    val day: Int = savedState.get<String>("day")?.toIntOrNull() ?: 1
    val lesson: RuDay? = index.day(day)
    val phrases = lesson?.let { index.dayPhrases(it, store.state.value.settings) }.orEmpty()

    init {
        viewModelScope.launch { audio.warmUp() }
    }

    fun today(): Long = store.today()

    fun markListened() {
        store.update { it.copy(podcastListened = it.podcastListened + day) }
    }

    override fun onCleared() {
        audio.stop()
    }
}

@Composable
fun RussianPodcastHomeScreen(
    onBack: () -> Unit,
    onOpenLesson: (Int) -> Unit,
    onOpenReminder: () -> Unit,
    vm: RussianPodcastViewModel = hiltViewModel(),
) {
    val p by vm.progress.collectAsState()
    val days = vm.index.days
    val unlocked = p.unlockedPodcastDay(days.size)
    val done = p.podcastDayStars.size
    val current = days.firstOrNull { it.day == unlocked }
    LazyColumn(
        Modifier.fillMaxSize().background(HikariBg),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item {
            PodcastTopBar("Podcast-Kurs", "100 Lektionen · RusslandJournal", onBack) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onOpenReminder),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Notifications, "Erinnerung", tint = HikariText)
                }
            }
        }
        item {
            Column(
                Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp))
                    .background(HikariSurface).border(1.dp, HikariBorder, RoundedCornerShape(22.dp)).padding(20.dp),
            ) {
                RuOverline(if (done == days.size) "Kurs abgeschlossen" else "Deine nächste Lektion", color = HikariAmber)
                Spacer(Modifier.height(8.dp))
                Text(
                    if (done == days.size) "Alle 100 Lektionen geschafft" else "Lektion ${current?.day} · ${current?.title.orEmpty()}",
                    color = HikariText, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Hören · verstehen · direkt anwenden",
                    color = HikariTextMuted, fontSize = 14.sp,
                )
                Spacer(Modifier.height(16.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.LocalFireDepartment, null, tint = HikariAmber, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("${RuSrs.visibleStreak(p, vm.today())} Tage Serie", color = HikariText, fontSize = 14.sp)
                    Spacer(Modifier.weight(1f))
                    Text("$done/100", color = HikariAmber, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.height(12.dp))
                Box(Modifier.fillMaxWidth().height(5.dp).clip(RoundedCornerShape(3.dp)).background(HikariBorderStrong)) {
                    Box(Modifier.fillMaxWidth(done / days.size.toFloat()).height(5.dp).background(HikariAmber))
                }
                if (current != null && done < days.size) {
                    Spacer(Modifier.height(18.dp))
                    RuPrimaryButton("Lektion ${current.day} starten") { onOpenLesson(current.day) }
                }
            }
        }
        item {
            Row(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
                    .background(HikariCardBg).clickable(onClick = onOpenReminder).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Notifications, null, tint = HikariAmber)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Tägliche Erinnerung", color = HikariText, fontWeight = FontWeight.SemiBold)
                    Text("Uhrzeit festlegen und dranbleiben", color = HikariTextMuted, fontSize = 12.5.sp)
                }
            }
        }
        item { RuOverline("Dein 100-Tage-Plan", Modifier.padding(start = 16.dp, top = 26.dp, bottom = 8.dp)) }
        items(days, key = { it.day }) { lesson ->
            PodcastLessonRow(
                lesson = lesson,
                stars = p.podcastDayStars[lesson.day] ?: 0,
                locked = lesson.day > unlocked,
                current = lesson.day == unlocked && done < days.size,
                onClick = { onOpenLesson(lesson.day) },
            )
        }
        item {
            Text(
                "Audio und Begleittexte: RusslandJournal.de. Die Lernphrasen und Übungen sind offline in Hikari gespeichert.",
                color = HikariTextFaint, fontSize = 11.5.sp, lineHeight = 17.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            )
        }
    }
}

@Composable
fun RussianPodcastLessonScreen(
    onBack: () -> Unit,
    onPractice: (Int) -> Unit,
    vm: RussianPodcastViewModel = hiltViewModel(),
) {
    val lesson = vm.lesson ?: return
    val media = lesson.media ?: return
    val p by vm.progress.collectAsState()
    val listened = lesson.day in p.podcastListened
    val playing by vm.audio.playing.collectAsState()
    val uriHandler = LocalUriHandler.current
    LazyColumn(
        Modifier.fillMaxSize().background(HikariBg),
        contentPadding = PaddingValues(bottom = 32.dp),
    ) {
        item { PodcastTopBar("Lektion ${lesson.day}", "Podcast-Kurs", onBack) }
        item {
            RussianYouTubePlayer(
                videoId = media.videoId,
                onEnded = vm::markListened,
                modifier = Modifier.padding(horizontal = 16.dp).clip(RoundedCornerShape(16.dp)),
            )
        }
        item {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 16.dp)) {
                Text(lesson.title, color = HikariText, fontSize = 23.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                Text(media.summary, color = HikariTextMuted, fontSize = 14.sp, lineHeight = 20.sp)
                Spacer(Modifier.height(14.dp))
                if (!listened) {
                    RuPrimaryButton("Folge als gehört markieren", onClick = vm::markListened)
                } else {
                    RuPrimaryButton(if (lesson.day in p.podcastDayStars) "Übungen wiederholen" else "Direkt zu den Übungen") {
                        onPractice(lesson.day)
                    }
                }
            }
        }
        item {
            Column(Modifier.padding(horizontal = 16.dp)) {
                RuOverline("Wissen für diese Lektion", color = HikariAmber)
                Spacer(Modifier.height(8.dp))
                media.notes.forEach { note ->
                    Text(
                        note,
                        color = HikariText, fontSize = 14.sp, lineHeight = 21.sp,
                        modifier = Modifier.padding(bottom = 10.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
                            .background(HikariCardBg).padding(14.dp),
                    )
                }
                Text(
                    "Originaltext auf RusslandJournal.de öffnen",
                    color = HikariAmber, fontSize = 12.5.sp,
                    modifier = Modifier.clickable { uriHandler.openUri(media.sourceUrl) }.padding(vertical = 8.dp),
                )
            }
        }
        item { RuOverline("Lernphrasen · ${vm.phrases.size}", Modifier.padding(start = 16.dp, top = 18.dp, bottom = 8.dp)) }
        items(vm.phrases, key = { it.id }) { phrase ->
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
                    .background(HikariCardBg).clickable { vm.audio.play(phrase.audio) }.padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(phrase.display, color = HikariText, fontSize = 16.5.sp, fontWeight = FontWeight.SemiBold)
                    Text(translitAnnotated(phrase.ru), color = HikariTextMuted, fontSize = 13.sp)
                    Text(phrase.de, color = HikariTextFaint, fontSize = 12.5.sp)
                }
                Icon(
                    Icons.AutoMirrored.Outlined.VolumeUp, "Anhören",
                    tint = if (playing == phrase.audio) HikariAmber else HikariTextMuted,
                )
            }
        }
        if (listened) {
            item {
                RuPrimaryButton(
                    "Übungen zu Lektion ${lesson.day}",
                    Modifier.padding(16.dp),
                ) { onPractice(lesson.day) }
            }
        }
    }
}

@Composable
private fun PodcastTopBar(title: String, subtitle: String, onBack: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = HikariText)
        }
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(title, color = HikariText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = HikariTextMuted, fontSize = 12.5.sp)
        }
        trailing()
    }
}

@Composable
internal fun PodcastLessonRow(lesson: RuDay, stars: Int, locked: Boolean, current: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = !locked, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).clip(CircleShape)
                .background(if (stars > 0) HikariAmber.copy(alpha = 0.15f) else Color.Transparent)
                .border(1.5.dp, when {
                    stars > 0 -> HikariAmber
                    current -> Color.White
                    else -> HikariBorderStrong
                }, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            when {
                stars > 0 -> Icon(Icons.Outlined.Check, null, tint = HikariAmber, modifier = Modifier.size(20.dp))
                locked -> Icon(Icons.Outlined.Lock, null, tint = HikariTextFaint, modifier = Modifier.size(16.dp))
                else -> Text("${lesson.day}", color = HikariText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Lektion ${lesson.day} · ${lesson.title}",
                color = if (locked) HikariTextFaint else HikariText,
                fontSize = 14.5.sp, fontWeight = FontWeight.Medium,
                maxLines = 2, overflow = TextOverflow.Ellipsis,
            )
            if (stars > 0) {
                Row(Modifier.padding(top = 3.dp)) {
                    repeat(3) { i ->
                        Icon(Icons.Filled.Star, null, tint = if (i < stars) HikariAmber else HikariBorderStrong, modifier = Modifier.size(13.dp))
                    }
                }
            }
        }
        if (current) Icon(Icons.Outlined.Headphones, null, tint = HikariAmber, modifier = Modifier.size(20.dp))
    }
}
