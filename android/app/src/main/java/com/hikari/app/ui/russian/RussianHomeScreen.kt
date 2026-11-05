package com.hikari.app.ui.russian

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.ChevronRight
import androidx.compose.material.icons.outlined.Construction
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Hearing
import androidx.compose.material.icons.outlined.JoinInner
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Podcasts
import androidx.compose.material.icons.outlined.RecordVoiceOver
import androidx.compose.material.icons.outlined.Replay
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material.icons.outlined.TravelExplore
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.hikari.app.domain.russian.RuDay
import com.hikari.app.domain.russian.RuGender
import com.hikari.app.domain.russian.RuProgress
import com.hikari.app.domain.russian.RuSrs
import com.hikari.app.domain.russian.RuToday
import com.hikari.app.domain.russian.RuTodayItem
import com.hikari.app.domain.russian.RuTodayKind
import com.hikari.app.domain.russian.RussianCourseRepository
import com.hikari.app.domain.russian.RussianProgressStore
import com.hikari.app.player.RussianPodcastPlayer
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

/**
 * Farbsystem des Russisch-Bereichs: jede Farbe steht für einen Bereich und
 * taucht überall gleich auf (Kurskarte, Icon, Fortschritt, "Heute dran").
 * Amber bleibt für Erfolge (Serie, Rekorde, Sterne).
 */
internal object RuColors {
    val Speak = Color(0xFFF472B6)
    val Listen = Color(0xFFA78BFA)
    val Review = Color(0xFF38BDF8)
    val Words = Color(0xFF34D399)
    val Xp = Color(0xFFFDE047)
    /** Dunkle Schrift/Icons auf voll gefärbten Flächen. */
    val Ink = Color(0xFF17171A)
}

@HiltViewModel
class RussianHomeViewModel @Inject constructor(
    repo: RussianCourseRepository,
    private val store: RussianProgressStore,
    private val audio: RussianAudioPlayer,
    private val podcastPlayer: RussianPodcastPlayer,
) : ViewModel() {
    val index = repo.index
    val podcastIndex = repo.podcastIndex
    val progress = store.state
    val podcastSpots = podcastPlayer.spots
    val totalItems = index.itemById.size + repo.podcastIndex.itemById.size
    val patternsTotal: Int by lazy { repo.patterns.size }

    fun today(): Long = store.today()

    /** Angefangene, noch nicht gehörte Podcast-Folge — sie geht in "Heute dran" vor. */
    fun podcastResumeDay(p: RuProgress): Int? {
        val unlocked = p.unlockedPodcastDay(podcastIndex.days.size)
        return podcastPlayer.lastDay?.takeIf { d ->
            d <= unlocked && d !in p.podcastListened && (podcastSpots.value[d]?.positionMs ?: 0) > 5_000
        }
    }

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
    val spots by vm.podcastSpots.collectAsState()
    val today = vm.today()
    val days = vm.index.days
    var settingsOpen by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { vm.warmUpAudio() }
    val ready = p.gender != null
    val open: (String) -> Unit = { if (ready) onOpen(it) }

    val plan = RuToday.plan(p, today, days.size, vm.podcastIndex.days.size, vm.podcastResumeDay(p))
    val podDay = plan[2].day
    val podSpot = spots[podDay]
    val podLeftMin = podSpot?.takeIf { it.durationMs > 0 }?.let { ((it.durationMs - it.positionMs) / 60_000).coerceAtLeast(1) }

    LazyColumn(
        Modifier.fillMaxSize().background(HikariBg),
        contentPadding = PaddingValues(bottom = 40.dp),
    ) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundIcon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", onBack)
                Text(
                    "Russisch", color = HikariText, fontSize = 22.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f).padding(start = 4.dp),
                )
                RoundIcon(Icons.Outlined.Tune, "Einstellungen") { settingsOpen = true }
            }
        }
        if (!ready) item { GenderIntro(onPick = vm::setGender) }
        item {
            Row(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(48.dp)
                    .clip(RoundedCornerShape(24.dp)).background(HikariSurface)
                    .border(1.dp, HikariBorder, RoundedCornerShape(24.dp))
                    .clickable { open("russian/phrases") }.padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Search, null, tint = HikariTextMuted, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Text("Satz oder Wort suchen", color = HikariTextMuted, fontSize = 14.5.sp)
            }
        }
        item {
            Row(
                Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp).fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                PulseTile("${RuSrs.visibleStreak(p, today)}", "Tage Serie", Icons.Filled.LocalFireDepartment, HikariAmber, Modifier.weight(1f))
                PulseTile("${p.xpOn(today)}", "XP heute", Icons.Filled.Bolt, RuColors.Xp, Modifier.weight(1f))
                PulseTile("${RuSrs.knownCount(p.cards)}", "Sätze sitzen", Icons.Outlined.TaskAlt, RuColors.Words, Modifier.weight(1f))
            }
        }

        item { SectionTitle("Heute dran", "Dein Tagesprogramm aus allen Bereichen, in der besten Reihenfolge.") }
        item {
            val next = plan.indexOfFirst { !it.done }
            Column(
                Modifier.padding(horizontal = 16.dp).fillMaxWidth().clip(RoundedCornerShape(22.dp))
                    .background(HikariSurface).border(1.dp, HikariBorder, RoundedCornerShape(22.dp)),
            ) {
                plan.forEachIndexed { i, item ->
                    if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(HikariBorder))
                    TodayRow(item, todayText(item, days, podLeftMin), next = i == next) { open(item.route) }
                }
                if (next >= 0) {
                    val first = plan[0].day == 1 && !plan[0].done && p.dayStars.isEmpty()
                    RuPrimaryButton(
                        if (first) "Loslegen" else "Weiterlernen",
                        Modifier.padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 16.dp),
                        enabled = ready,
                    ) { open(plan[next].route) }
                }
            }
        }

        item { SectionTitle("Deine Kurse", "Zwei Kurse, unabhängig voneinander, jeder mit eigenem Fortschritt.") }
        item {
            val unlocked = p.unlockedDay(days.size)
            val current = days.firstOrNull { it.day == unlocked }
            SpeakCourseCard(p, days, current) { open("russian-plan") }
        }
        item {
            val podIndex = vm.podcastIndex
            val lesson = podIndex.days.firstOrNull { it.day == podDay }
            PodcastCourseCard(
                lessonNo = podDay,
                coverUrl = lesson?.media?.videoId?.let { "https://i.ytimg.com/vi/$it/hqdefault.jpg" },
                done = p.podcastDayStars.size,
                total = podIndex.days.size,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 10.dp),
            ) { open("russian/podcast") }
        }

        item { SectionTitle("Frei üben", "Jederzeit, unabhängig vom Kurs, mit allem, was du schon gelernt hast.") }
        items(practiceGroups(p, vm.patternsTotal), key = { it.title }) { group ->
            ToolGroup(group, onOpen = open)
        }
    }

    if (settingsOpen) {
        SettingsDialog(p, onGender = vm::setGender, onVoice = vm::setVoice, onClose = { settingsOpen = false })
    }
}

/** Texte eines "Heute dran"-Punkts: Herkunft, Titel, Beschreibung, Zeit. */
private data class TodayText(val source: String, val title: String, val detail: String, val meta: String)

private fun todayText(item: RuTodayItem, days: List<RuDay>, podcastLeftMin: Long?): TodayText = when (item.kind) {
    RuTodayKind.COURSE -> {
        val d = days.firstOrNull { it.day == item.day }
        when {
            item.allDone -> TodayText("Sprechkurs", "Alle 20 Tage geschafft", "Wiederhole Tage im Kursplan", "Erledigt")
            item.done -> TodayText("Sprechkurs, Tag ${item.day}", "Tag ${item.day} geschafft", d?.title.orEmpty(), "Erledigt")
            else -> TodayText(
                "Sprechkurs, Tag ${item.day}",
                "Lektion: ${d?.title.orEmpty()}",
                "${d?.items?.size ?: 0} neue Sätze hören und nachsprechen",
                "ca. ${((d?.items?.size ?: 12) * 45 / 60).coerceAtLeast(5)} Min.",
            )
        }
    }
    RuTodayKind.REVIEW ->
        if (item.done) {
            TodayText("Wiederholen", "Alles wiederholt", "Gerade ist kein Satz fällig", "Erledigt")
        } else {
            TodayText(
                "Wiederholen",
                if (item.count == 1) "1 Satz ist fällig" else "${item.count} Sätze sind fällig",
                "Damit Gelerntes sitzen bleibt",
                "ca. ${(item.count * 15 / 60).coerceAtLeast(1)} Min.",
            )
        }
    RuTodayKind.PODCAST -> when {
        item.allDone -> TodayText("Podcast-Kurs", "Alle Lektionen geschafft", "Folgen bleiben zum Nachhören da", "Erledigt")
        item.done -> TodayText("Podcast-Kurs, Lektion ${item.day}", "Heutige Lektion geschafft", "Morgen geht es weiter", "Erledigt")
        item.practice -> TodayText("Podcast-Kurs, Lektion ${item.day}", "Übungen zur Folge", "Folge gehört, jetzt anwenden", "")
        podcastLeftMin != null -> TodayText("Podcast-Kurs, Lektion ${item.day}", "Folge weiterhören", "Hören, dann die Übungen dazu", "$podcastLeftMin Min.")
        else -> TodayText("Podcast-Kurs, Lektion ${item.day}", "Folge hören", "Hören, dann die Übungen dazu", "")
    }
}

private fun RuTodayKind.color() = when (this) {
    RuTodayKind.COURSE -> RuColors.Speak
    RuTodayKind.REVIEW -> RuColors.Review
    RuTodayKind.PODCAST -> RuColors.Listen
}

private fun RuTodayKind.icon() = when (this) {
    RuTodayKind.COURSE -> Icons.Outlined.School
    RuTodayKind.REVIEW -> Icons.Outlined.Replay
    RuTodayKind.PODCAST -> Icons.Outlined.Podcasts
}

@Composable
private fun TodayRow(item: RuTodayItem, text: TodayText, next: Boolean, onClick: () -> Unit) {
    val c = item.kind.color()
    Box(
        Modifier.fillMaxWidth()
            .background(
                if (next) Brush.horizontalGradient(listOf(c.copy(alpha = 0.16f), c.copy(alpha = 0.02f))) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)),
            )
            .clickable(onClick = onClick),
    ) {
        if (next) {
            Box(
                Modifier.align(Alignment.CenterStart).padding(vertical = 12.dp).width(3.dp).height(44.dp)
                    .clip(RoundedCornerShape(topEnd = 3.dp, bottomEnd = 3.dp)).background(c),
            )
        }
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(42.dp).clip(RoundedCornerShape(13.dp))
                    .background(if (next) c else c.copy(alpha = if (item.done) 0.10f else 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (item.done) Icons.Outlined.Check else item.kind.icon(), null,
                    tint = if (next) RuColors.Ink else c, modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(text.source, color = c.copy(alpha = if (item.done) 0.7f else 1f), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Text(
                    text.title,
                    color = if (item.done) HikariTextMuted else HikariText,
                    fontSize = 15.sp, fontWeight = FontWeight.Medium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(text.detail, color = HikariTextMuted, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            if (text.meta.isNotEmpty()) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text.meta,
                    color = if (next) c else HikariTextMuted,
                    fontSize = 12.5.sp, fontWeight = if (next) FontWeight.Medium else FontWeight.Normal,
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String, hint: String) {
    Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 12.dp)) {
        Text(title, color = HikariText, fontSize = 19.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(3.dp))
        Text(hint, color = HikariTextMuted, fontSize = 13.sp, lineHeight = 18.sp)
    }
}

@Composable
private fun PulseTile(value: String, label: String, icon: ImageVector, tint: Color, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(14.dp)).background(HikariSurface).padding(horizontal = 12.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = tint, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(4.dp))
            Text(value, color = HikariText, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        }
        Text(label, color = HikariTextMuted, fontSize = 12.sp)
    }
}

/** Kurskarte: leicht getönte Fläche, voll gefärbtes Icon, Fortschritt in der Bereichsfarbe. */
@Composable
private fun CourseCard(
    color: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier.fillMaxWidth().clip(shape).background(HikariCardBg).background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.36f), shape).clickable(onClick = onClick).padding(18.dp),
    ) { content() }
}

@Composable
private fun CourseHead(color: Color, kind: String, title: String, sub: String, leading: @Composable () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        leading()
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(kind, color = color, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            Text(title, color = HikariText, fontSize = 16.5.sp, fontWeight = FontWeight.SemiBold)
            Text(sub, color = HikariTextMuted, fontSize = 12.5.sp, lineHeight = 17.sp)
        }
        Icon(Icons.Outlined.ChevronRight, null, tint = color)
    }
}

@Composable
private fun CourseFoot(now: String, count: String) {
    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Text("Jetzt: ", color = HikariTextMuted, fontSize = 12.5.sp)
        Text(now, color = HikariText, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
        Text(count, color = HikariTextMuted, fontSize = 12.5.sp)
    }
}

@Composable
private fun SpeakCourseCard(p: RuProgress, days: List<RuDay>, current: RuDay?, onClick: () -> Unit) {
    val c = RuColors.Speak
    val unlocked = p.unlockedDay(days.size)
    CourseCard(c, Modifier.padding(horizontal = 16.dp), onClick) {
        CourseHead(c, "Kurs, Sprechen", "Sprechkurs", "${days.size} Tage, vom Smalltalk bis zu tiefen Gesprächen") {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(14.dp)).background(c), contentAlignment = Alignment.Center) {
                Icon(Icons.Outlined.School, null, tint = RuColors.Ink)
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            days.forEach { d ->
                if (d.day == DEEP_TALK_FROM) Spacer(Modifier.width(3.dp))
                Box(
                    Modifier.weight(1f).height(6.dp).clip(RoundedCornerShape(3.dp)).background(
                        when {
                            d.day in p.dayStars -> c
                            d.day == unlocked -> Color.White
                            else -> HikariBorderStrong
                        },
                    ),
                )
            }
        }
        CourseFoot(
            if (p.dayStars.size >= days.size) "alle Tage geschafft" else "Tag ${current?.day}, ${current?.title.orEmpty()}",
            "${p.dayStars.size} von ${days.size}",
        )
    }
}

@Composable
private fun PodcastCourseCard(lessonNo: Int, coverUrl: String?, done: Int, total: Int, modifier: Modifier, onClick: () -> Unit) {
    val c = RuColors.Listen
    CourseCard(c, modifier, onClick) {
        CourseHead(c, "Kurs, Hören", "Podcast-Kurs", "$total Lektionen von RusslandJournal zum Hören") {
            Box(Modifier.width(86.dp).height(48.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black)) {
                if (coverUrl != null && !LocalInspectionMode.current) {
                    AsyncImage(model = coverUrl, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
            }
        }
        Box(Modifier.padding(top = 16.dp).fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)).background(HikariBorderStrong)) {
            Box(Modifier.fillMaxWidth((done.toFloat() / total).coerceIn(0.015f, 1f)).fillMaxHeight().clip(RoundedCornerShape(3.dp)).background(c))
        }
        CourseFoot("Lektion $lessonNo", "$done von $total")
    }
}

/** Eine Übung im Raster "Frei üben". */
internal data class PracticeTool(
    val title: String,
    val purpose: String,
    val icon: ImageVector,
    val badge: String? = null,
    /** Amber = Rekord/Erfolg, sonst neutral (Zähler wie "236"). */
    val record: Boolean = false,
    val route: () -> String,
)

internal data class PracticeGroup(val title: String, val hint: String, val color: Color, val tools: List<PracticeTool>)

private fun rec(v: Int, unit: String = ""): String? = if (v > 0) "Rekord $v$unit" else "Neu"

internal fun practiceGroups(p: RuProgress, patternsTotal: Int): List<PracticeGroup> = listOf(
    PracticeGroup(
        "Sprechen", "antworten und frei reden", RuColors.Speak,
        listOf(
            PracticeTool("Sprich frei", "Fragen hören und frei antworten", Icons.Outlined.RecordVoiceOver) { "russian/talk" },
            PracticeTool("Sprech-Reflex", "Deutsch hören, schnell Russisch sagen", Icons.Outlined.Bolt, rec(p.bestReflex), p.bestReflex > 0) { "russian/reflex" },
            PracticeTool("Gesprächsreise", "Offenes Gespräch auf deinem Niveau", Icons.Outlined.TravelExplore) { "russian/conversation" },
            PracticeTool("Blind-Coach", "Nur Audio, gut für unterwegs", Icons.Outlined.Headphones) { "russian/blind" },
            PracticeTool("Rollenspiele", "Gespräche aus den Kurstagen üben", Icons.Outlined.Forum) {
                "russian/dialog/${p.dayStars.keys.randomOrNull() ?: 1}"
            },
        ),
    ),
    PracticeGroup(
        "Hören und Aussprache", "verstehen und richtig klingen", RuColors.Listen,
        listOf(
            PracticeTool("Hör-Blitz", "Satz hören, Bedeutung schnell tippen", Icons.Outlined.Hearing, rec(p.bestBlitz), p.bestBlitz > 0) { "russian/blitz" },
            PracticeTool(
                "Lautlabor", "Ähnliche Laute unterscheiden und sprechen", Icons.Outlined.GraphicEq,
                p.labBest.values.maxOrNull()?.let { "$it %" } ?: "Neu", p.labBest.isNotEmpty(),
            ) { "russian/sounds" },
        ),
    ),
    PracticeGroup(
        "Wörter und Grammatik", "festigen und Sätze bauen", RuColors.Words,
        listOf(
            PracticeTool("Satzbuch", "Alle Sätze, Favoriten, Vokabeltest", Icons.AutoMirrored.Outlined.MenuBook, "${p.cards.size + p.mySentences.size}") { "russian/phrases" },
            PracticeTool("Satzbau", "Muster lernen, eigene Sätze bauen", Icons.Outlined.Construction, "${p.patternsDone.size}/$patternsTotal") { "russian-patterns" },
            PracticeTool("Kombi-Meister", "Bausteine zu neuen Sätzen kombinieren", Icons.Outlined.Extension, rec(p.bestKombi), p.bestKombi > 0) { "russian/kombi" },
            PracticeTool(
                "Paare finden", "Russisch und Deutsch zuordnen", Icons.Outlined.JoinInner,
                if (p.bestPairsSeconds > 0) "${p.bestPairsSeconds} s" else "Neu", p.bestPairsSeconds > 0,
            ) { "russian/pairs" },
        ),
    ),
)

@Composable
internal fun ToolGroup(group: PracticeGroup, onOpen: (String) -> Unit) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 18.dp)) {
        Row(Modifier.padding(start = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(group.color))
            Spacer(Modifier.width(8.dp))
            Text(group.title, color = HikariText, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(8.dp))
            Text(group.hint, color = HikariTextMuted, fontSize = 13.sp)
        }
        group.tools.chunked(2).forEachIndexed { i, row ->
            if (i > 0) Spacer(Modifier.height(8.dp))
            if (row.size == 1) {
                ToolTileWide(row[0], group.color) { onOpen(row[0].route()) }
            } else {
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { t -> ToolTile(t, group.color, Modifier.weight(1f).fillMaxHeight()) { onOpen(t.route()) } }
                }
            }
        }
    }
}

private fun Modifier.toolSurface(color: Color, onClick: () -> Unit): Modifier {
    val shape = RoundedCornerShape(18.dp)
    return clip(shape).background(HikariCardBg).background(color.copy(alpha = 0.045f))
        .border(1.dp, color.copy(alpha = 0.22f), shape).clickable(onClick = onClick)
}

@Composable
private fun ToolChip(icon: ImageVector, color: Color) {
    Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(color), contentAlignment = Alignment.Center) {
        Icon(icon, null, tint = RuColors.Ink, modifier = Modifier.size(22.dp))
    }
}

@Composable
private fun ToolBadge(tool: PracticeTool) {
    val b = tool.badge ?: return
    Text(
        b,
        color = if (tool.record) HikariAmber else HikariTextMuted,
        fontSize = 11.5.sp, fontWeight = if (tool.record) FontWeight.SemiBold else FontWeight.Medium,
    )
}

@Composable
private fun ToolTile(tool: PracticeTool, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(modifier.heightIn(min = 118.dp).toolSurface(color, onClick).padding(14.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            ToolChip(tool.icon, color)
            Spacer(Modifier.weight(1f))
            ToolBadge(tool)
        }
        Spacer(Modifier.height(12.dp))
        Text(tool.title, color = HikariText, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(3.dp))
        Text(tool.purpose, color = HikariTextMuted, fontSize = 12.sp, lineHeight = 16.sp)
    }
}

@Composable
private fun ToolTileWide(tool: PracticeTool, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().toolSurface(color, onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ToolChip(tool.icon, color)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(tool.title, color = HikariText, fontSize = 14.5.sp, fontWeight = FontWeight.SemiBold)
            Text(tool.purpose, color = HikariTextMuted, fontSize = 12.sp, lineHeight = 16.sp)
        }
        ToolBadge(tool)
    }
}

/** Ab diesem Tag beginnt der Abschnitt "Tiefe Gespräche" (Lücke in der Fortschrittsleiste). */
private const val DEEP_TALK_FROM = 15

/** Kursplan des Sprechkurses: die 20 Tage nach Wochen, eine Ebene unter der Übersicht. */
@Composable
fun RussianPlanScreen(
    onBack: () -> Unit,
    onOpen: (route: String) -> Unit,
    vm: RussianHomeViewModel = hiltViewModel(),
) {
    val p by vm.progress.collectAsState()
    val days = vm.index.days
    val unlocked = p.unlockedDay(days.size)
    val allDone = p.dayStars.size >= days.size
    val weeks = listOf(
        Triple("Woche 1", "Small Talk, die Basis", 1..7),
        Triple("Woche 2", "Small Talk im Alltag", 8..14),
        Triple("Woche 3", "Tiefe Gespräche", DEEP_TALK_FROM..days.size),
    )
    LazyColumn(Modifier.fillMaxSize().background(HikariBg), contentPadding = PaddingValues(bottom = 40.dp)) {
        item {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundIcon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", onBack)
                Column(Modifier.weight(1f).padding(start = 4.dp)) {
                    Text("Sprechkurs", color = HikariText, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Text("${days.size} Tage, vom Smalltalk bis zu tiefen Gesprächen", color = HikariTextMuted, fontSize = 12.5.sp)
                }
            }
        }
        item {
            Text(
                "Jeder Tag hat eine Lektion mit neuen Sätzen und ein Rollenspiel. Der nächste Tag wird frei, sobald du den aktuellen abschließt.",
                color = HikariTextMuted, fontSize = 14.sp, lineHeight = 20.sp,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }
        weeks.forEach { (title, hint, range) ->
            item(key = title) {
                Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 24.dp)) {
                    Row(Modifier.padding(start = 4.dp, bottom = 8.dp), verticalAlignment = Alignment.Bottom) {
                        Text(title, color = HikariText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.width(6.dp))
                        Text(hint, color = HikariTextMuted, fontSize = 13.sp)
                    }
                    Column(
                        Modifier.fillMaxWidth().clip(RoundedCornerShape(20.dp)).background(HikariCardBg)
                            .border(1.dp, HikariBorder, RoundedCornerShape(20.dp)),
                    ) {
                        days.filter { it.day in range }.forEachIndexed { i, d ->
                            if (i > 0) Box(Modifier.fillMaxWidth().height(1.dp).background(HikariBorder))
                            PlanDayRow(
                                day = d,
                                stars = p.dayStars[d.day] ?: 0,
                                current = d.day == unlocked && !allDone,
                                locked = d.day > unlocked,
                                onLesson = { onOpen("russian/lesson/${d.day}") },
                                onDialog = { onOpen("russian/dialog/${d.day}") },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PlanDayRow(day: RuDay, stars: Int, current: Boolean, locked: Boolean, onLesson: () -> Unit, onDialog: () -> Unit) {
    val c = RuColors.Speak
    val done = stars > 0
    Row(
        Modifier.fillMaxWidth()
            .background(if (current) Brush.horizontalGradient(listOf(c.copy(alpha = 0.14f), Color.Transparent)) else Brush.linearGradient(listOf(Color.Transparent, Color.Transparent)))
            .clickable(enabled = !locked, onClick = onLesson)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(34.dp).clip(CircleShape)
                .background(if (done) c else Color.Transparent)
                .border(if (current) 2.dp else 1.5.dp, if (done || current) c else HikariBorderStrong, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            when {
                done -> Icon(Icons.Outlined.Check, null, tint = RuColors.Ink, modifier = Modifier.size(18.dp))
                locked -> Icon(Icons.Outlined.Lock, null, tint = HikariTextFaint, modifier = Modifier.size(15.dp))
                else -> Text("${day.day}", color = if (current) c else HikariText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "Tag ${day.day}: ${day.title}",
                color = if (locked) HikariTextFaint else HikariText,
                fontSize = 14.5.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text(
                day.goal, color = if (locked) HikariTextFaint else HikariTextMuted, fontSize = 12.5.sp,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (done) {
                Row(Modifier.padding(top = 3.dp)) {
                    repeat(3) { i ->
                        Icon(Icons.Filled.Star, null, tint = if (i < stars) HikariAmber else HikariBorderStrong, modifier = Modifier.size(13.dp))
                    }
                }
            }
        }
        when {
            current -> Text(
                "Heute", color = RuColors.Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(12.dp)).background(c).padding(horizontal = 10.dp, vertical = 4.dp),
            )
            done -> Box(
                Modifier.size(40.dp).clip(CircleShape).clickable(onClick = onDialog),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Outlined.Forum, "Rollenspiel", tint = HikariTextMuted, modifier = Modifier.size(20.dp)) }
        }
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
        Text("Bevor es losgeht", color = HikariAmber, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
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
