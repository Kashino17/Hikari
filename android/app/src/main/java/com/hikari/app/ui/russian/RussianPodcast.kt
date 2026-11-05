package com.hikari.app.ui.russian

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
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
import com.hikari.app.domain.russian.RuPodcastPlayback
import com.hikari.app.domain.russian.RuSrs
import com.hikari.app.domain.russian.RussianCourseRepository
import com.hikari.app.domain.russian.RussianProgressStore
import com.hikari.app.player.PodcastPlayback
import com.hikari.app.player.RussianPodcastPlayer
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
import kotlinx.coroutines.launch

@HiltViewModel
class RussianPodcastViewModel @Inject constructor(
    savedState: SavedStateHandle,
    private val repo: RussianCourseRepository,
    private val store: RussianProgressStore,
    val audio: RussianAudioPlayer,
    val player: RussianPodcastPlayer,
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

    fun markListened() = player.markListened(day)

    /** Lektion in den Player legen (läuft sie schon, bleibt sie unangetastet). */
    fun loadInPlayer(target: RuDay, autoplay: Boolean) {
        val media = target.media ?: return
        player.load(target.day, media.videoId, target.title, autoplay)
        // Diese und die nächste Folge schon auf den Server holen.
        val next = index.day(target.day + 1)?.media?.videoId
        player.prefetch(listOfNotNull(media.videoId, next))
    }

    /** Lernphrase vorlesen — der Podcast macht dafür Pause. */
    fun playPhrase(audioName: String) {
        player.pause()
        audio.play(audioName)
    }

    override fun onCleared() {
        audio.stop()
        player.saveNow()
    }
}

private fun RussianPodcastPlayer.actions() = PodcastActions(
    toggle = ::toggle,
    seekTo = ::seekTo,
    skipBy = ::skipBy,
    cycleSpeed = ::cycleSpeed,
    restart = ::restart,
    dismissResume = ::dismissResumeHint,
)

@Composable
fun RussianPodcastHomeScreen(
    onBack: () -> Unit,
    onOpenLesson: (Int) -> Unit,
    onOpenReminder: () -> Unit,
    vm: RussianPodcastViewModel = hiltViewModel(),
) {
    val p by vm.progress.collectAsState()
    val playback by vm.player.state.collectAsState()
    val spots by vm.player.spots.collectAsState()
    val days = vm.index.days
    val unlocked = p.unlockedPodcastDay(days.size)
    val done = p.podcastDayStars.size
    // "Weiterhören" schlägt "nächste Lektion": angefangen, nicht fertig gehört, freigeschaltet.
    val resumeDay = vm.player.lastDay?.takeIf { d ->
        d <= unlocked && d !in p.podcastListened && (spots[d]?.positionMs ?: 0) > 5_000
    }
    val heroDay = resumeDay ?: unlocked
    val hero = days.firstOrNull { it.day == heroDay }
    Box(Modifier.fillMaxSize().background(HikariBg)) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = if (playback.day != null) 96.dp else 32.dp),
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
                    val allDone = done == days.size && resumeDay == null
                    RuOverline(
                        when {
                            allDone -> "Kurs abgeschlossen"
                            resumeDay != null -> "Weiterhören"
                            else -> "Deine nächste Lektion"
                        },
                        color = HikariAmber,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (allDone) "Alle 100 Lektionen geschafft" else "Lektion ${hero?.day} · ${hero?.title.orEmpty()}",
                        color = HikariText, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("Hören · verstehen · direkt anwenden", color = HikariTextMuted, fontSize = 14.sp)
                    val spot = resumeDay?.let { spots[it] }
                    if (spot != null && spot.durationMs > 0) {
                        Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                            val pos = if (playback.day == resumeDay) playback.positionMs else spot.positionMs
                            Text(RuPodcastPlayback.formatTime(pos), color = HikariTextMuted, fontSize = 12.5.sp)
                            Box(
                                Modifier.padding(horizontal = 10.dp).weight(1f).height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)).background(HikariBorderStrong),
                            ) {
                                Box(
                                    Modifier.fillMaxWidth((pos.toFloat() / spot.durationMs).coerceIn(0f, 1f))
                                        .fillMaxHeight().background(Color.White),
                                )
                            }
                            Text(RuPodcastPlayback.formatTime(spot.durationMs), color = HikariTextMuted, fontSize = 12.5.sp)
                        }
                    }
                    if (hero != null && !allDone) {
                        Spacer(Modifier.height(18.dp))
                        val running = playback.day == hero.day && playback.playing
                        val label = when {
                            running -> "Zur laufenden Folge"
                            spot != null -> "Weiterhören ab " +
                                RuPodcastPlayback.formatTime(RuPodcastPlayback.resumeStartMs(spot.positionMs, spot.durationMs))
                            else -> "Lektion ${hero.day} starten"
                        }
                        RuPrimaryButton(label) {
                            if (!running) vm.loadInPlayer(hero, autoplay = true)
                            onOpenLesson(hero.day)
                        }
                    }
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
                val spot = spots[lesson.day]
                val live = playback.day == lesson.day
                val pos = if (live) playback.positionMs else spot?.positionMs ?: 0
                val dur = if (live && playback.durationMs > 0) playback.durationMs else spot?.durationMs ?: 0
                val listened = lesson.day in p.podcastListened
                val stars = p.podcastDayStars[lesson.day] ?: 0
                PodcastLessonRow(
                    lesson = lesson,
                    stars = stars,
                    locked = lesson.day > unlocked,
                    current = lesson.day == heroDay && done < days.size,
                    progress = if (dur > 0 && !listened) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f,
                    subtitle = when {
                        listened && stars == 0 -> "Gehört"
                        dur > 0 && pos > 5_000 && !listened ->
                            "${RuPodcastPlayback.formatTime(pos)} von ${RuPodcastPlayback.formatTime(dur)}"
                        else -> null
                    },
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
        AnimatedVisibility(
            visible = playback.day != null,
            modifier = Modifier.align(Alignment.BottomCenter),
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            PodcastPlayerBar(
                state = playback,
                onExpand = { playback.day?.let(onOpenLesson) },
                onToggle = vm.player::toggle,
                onClose = vm.player::close,
                modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 12.dp),
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
    val playback by vm.player.state.collectAsState()
    val listened = lesson.day in p.podcastListened
    val playing by vm.audio.playing.collectAsState()
    val uriHandler = LocalUriHandler.current
    val list = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val compactAfter = with(LocalDensity.current) { 90.dp.toPx() }
    val compact by remember {
        derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > compactAfter }
    }
    LaunchedEffect(lesson.day) { vm.loadInPlayer(lesson, autoplay = false) }
    // Solange die Lektion noch in den Player wandert, zeigt die UI schon ihr Cover.
    val shown = if (playback.day == lesson.day) {
        playback
    } else {
        PodcastPlayback(day = lesson.day, title = lesson.title, videoId = media.videoId, loading = true, speed = playback.speed)
    }
    val actions = remember(vm.player) { vm.player.actions() }
    Column(Modifier.fillMaxSize().background(HikariBg)) {
        PodcastTopBar("Lektion ${lesson.day}", "Podcast-Kurs", onBack)
        BoxWithConstraints(Modifier.fillMaxWidth().weight(1f)) {
            val fullHeight = (maxWidth - 32.dp) * 9f / 16f + PodcastControlsHeight
            LazyColumn(
                state = list,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = fullHeight + 10.dp, bottom = 32.dp),
            ) {
                item {
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        Text(lesson.title, color = HikariText, fontSize = 23.sp, fontWeight = FontWeight.SemiBold, lineHeight = 29.sp)
                        Spacer(Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val dur = shown.durationMs
                            Text(
                                (if (dur > 0) RuPodcastPlayback.formatTime(dur) + " · " else "") + "RusslandJournal",
                                color = HikariTextMuted, fontSize = 12.5.sp,
                            )
                            if (listened) {
                                Spacer(Modifier.width(8.dp))
                                Row(
                                    Modifier.height(22.dp).clip(RoundedCornerShape(11.dp))
                                        .border(1.dp, HikariAmber.copy(alpha = 0.4f), RoundedCornerShape(11.dp))
                                        .padding(horizontal = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(Icons.Outlined.Check, null, tint = HikariAmber, modifier = Modifier.size(14.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("Gehört", color = HikariAmber, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                        Spacer(Modifier.height(12.dp))
                        Text(media.summary, color = HikariTextMuted, fontSize = 14.sp, lineHeight = 20.sp)
                        Spacer(Modifier.height(18.dp))
                        if (listened) {
                            RuPrimaryButton(
                                if (lesson.day in p.podcastDayStars) "Übungen wiederholen" else "Direkt zu den Übungen",
                                trailingIcon = Icons.AutoMirrored.Outlined.ArrowForward,
                            ) { onPractice(lesson.day) }
                        } else {
                            RuPrimaryButton("Übungen nach der Folge", enabled = false, container = HikariSurfaceHigh) {}
                            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.Center) {
                                Text("Schon gehört? ", color = HikariTextMuted, fontSize = 12.5.sp)
                                Text(
                                    "Als gehört markieren", color = HikariAmber, fontSize = 12.5.sp,
                                    modifier = Modifier.clickable(onClick = vm::markListened),
                                )
                            }
                        }
                    }
                }
                item {
                    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 26.dp)) {
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
                            .background(HikariCardBg).clickable { vm.playPhrase(phrase.audio) }.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            RuCompactPhrase(phrase.ru, size = 16.5.sp)
                            Spacer(Modifier.height(3.dp))
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
            AnimatedContent(
                targetState = compact,
                transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(150)) using SizeTransform(clip = false) },
                label = "podcast-player",
            ) { isCompact ->
                if (isCompact) {
                    PodcastPlayerBar(
                        state = shown,
                        onExpand = { scope.launch { list.animateScrollToItem(0) } },
                        onToggle = actions.toggle,
                        modifier = Modifier.padding(horizontal = 8.dp),
                    )
                } else {
                    PodcastPlayerFull(
                        state = shown,
                        actions = actions,
                        onPhrases = { scope.launch { list.animateScrollToItem(PHRASES_ITEM) } },
                        modifier = Modifier.background(HikariBg).padding(horizontal = 16.dp),
                    )
                }
            }
        }
    }
}

/** Index der Überschrift "Lernphrasen" in der Lektionsliste. */
private const val PHRASES_ITEM = 2

@Composable
private fun PodcastTopBar(title: String, subtitle: String, onBack: () -> Unit, trailing: @Composable () -> Unit = {}) {
    Row(
        Modifier.fillMaxWidth().background(HikariBg).padding(horizontal = 8.dp, vertical = 8.dp),
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
internal fun PodcastLessonRow(
    lesson: RuDay,
    stars: Int,
    locked: Boolean,
    current: Boolean,
    progress: Float = 0f,
    subtitle: String? = null,
    onClick: () -> Unit,
) {
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
                    current && progress == 0f -> Color.White
                    else -> HikariBorderStrong
                }, CircleShape)
                .drawBehind {
                    // Angefangene Folge: weißer Fortschrittsring um die Nummer.
                    if (progress > 0f && stars == 0) {
                        val w = 2.dp.toPx()
                        drawArc(
                            Color.White, startAngle = -90f, sweepAngle = 360f * progress, useCenter = false,
                            topLeft = Offset(w / 2, w / 2),
                            size = Size(size.width - w, size.height - w),
                            style = Stroke(width = w, cap = StrokeCap.Round),
                        )
                    }
                },
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
            if (subtitle != null) {
                Text(subtitle, color = HikariTextMuted, fontSize = 12.sp, modifier = Modifier.padding(top = 3.dp))
            }
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
