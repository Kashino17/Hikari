package com.hikari.app.ui.russian

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.VolumeUp
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import com.hikari.app.domain.russian.RuAnswerCheck
import com.hikari.app.domain.russian.RuDeepdiveLogic
import com.hikari.app.domain.russian.RuMySentence
import com.hikari.app.domain.russian.RuPhrase
import com.hikari.app.domain.russian.RuPhrasebookSearch
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariBorder
import com.hikari.app.ui.theme.HikariBorderStrong
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted

private sealed interface PbFilter {
    data object All : PbFilter
    data object Favorites : PbFilter
    data object Weak : PbFilter
    data object Mine : PbFilter
    data class Theme(val id: String) : PbFilter
}

/** Satzbuch: alle gelernten Sätze — durchsuchbar (Deutsch, Russisch, Umschrift), nach Tag oder Thema. */
@Composable
fun RussianPhrasebookScreen(
    onBack: () -> Unit,
    onTest: () -> Unit = {},
    onDeepdive: (String) -> Unit = {},
    vm: RussianGamesViewModel = hiltViewModel(),
) {
    val playing by vm.audio.playing.collectAsState()
    val progress by vm.progress.collectAsState()
    val days = remember { vm.unlockedDays() }
    val meta = vm.phrasebookMeta
    val all = remember(days) { days.flatMap { d -> vm.dayPhrases(d.day) } }
    val dayTitle = remember(days) { days.associate { it.day to it.title } }

    var query by remember { mutableStateOf("") }
    var byTheme by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf<PbFilter>(PbFilter.All) }
    var openId by remember { mutableStateOf<String?>(null) }

    val shown = remember(all, query, filter, progress.favorites, progress.cards) {
        all.filter { p ->
            RuPhrasebookSearch.matches(p, query) && when (val f = filter) {
                PbFilter.All -> true
                PbFilter.Favorites -> p.id in progress.favorites
                PbFilter.Weak -> (progress.cards[p.id]?.box ?: 0) in 0..2
                is PbFilter.Theme -> f.id in meta.items[p.id].orEmpty()
                PbFilter.Mine -> false
            }
        }
    }

    fun themeOf(p: RuPhrase): String =
        meta.items[p.id]?.firstOrNull { id -> meta.themes.any { it.id == id } }
            ?.let { id -> meta.themes.first { it.id == id }.title } ?: "Sonstiges"

    val groups: List<Pair<String, List<RuPhrase>>> = remember(shown, byTheme) {
        if (byTheme) {
            val order = meta.themes.map { it.title } + "Sonstiges"
            shown.groupBy { themeOf(it) }.toList().sortedBy { order.indexOf(it.first) }
        } else {
            shown.groupBy { it.day }.toList().sortedBy { it.first }
                .map { (d, list) -> "Tag $d · ${dayTitle[d].orEmpty()}" to list }
        }
    }

    Column(Modifier.fillMaxSize().background(HikariBg)) {
        Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = HikariText)
            }
            Column(Modifier.padding(start = 4.dp).weight(1f)) {
                Text("Satzbuch", color = HikariText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Text("${shown.size} von ${all.size} Sätzen", color = HikariTextMuted, fontSize = 12.5.sp)
            }
            PbChip(if (byTheme) "Nach Thema" else "Nach Tag", selected = false) { byTheme = !byTheme }
        }

        SearchField(query, onChange = { query = it })

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { PbChip("Alle", filter == PbFilter.All) { filter = PbFilter.All } }
            item {
                PbChip("★ Favoriten (${progress.favorites.count { id -> all.any { it.id == id } }})", filter == PbFilter.Favorites) {
                    filter = PbFilter.Favorites
                }
            }
            item { PbChip("Wackelig", filter == PbFilter.Weak) { filter = PbFilter.Weak } }
            item { PbChip("Meine Sätze (${progress.mySentences.size})", filter == PbFilter.Mine) { filter = PbFilter.Mine } }
            items(meta.themes) { t ->
                PbChip(t.title, (filter as? PbFilter.Theme)?.id == t.id) { filter = PbFilter.Theme(t.id) }
            }
        }

        if (filter != PbFilter.Mine && shown.isNotEmpty()) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(HikariAmber)
                    .clickable {
                        vm.prepareTest(shown.map { it.id })
                        onTest()
                    }
                    .padding(vertical = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Diese ${minOf(shown.size, 15)} Sätze testen",
                    color = Color.Black,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }

        if (filter == PbFilter.Mine) {
            MySentences(progress.mySentences.filter { RuPhrasebookSearch.matches(RuDeepdiveLogic.asPhrase(it.ru, it.de, it.note), query) }, vm.audio, onRemove = vm::removeSentence)
        } else if (shown.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                Text(
                    if (query.isBlank()) "Hier ist noch nichts." else "Nichts gefunden für »$query«.",
                    color = HikariTextMuted,
                    fontSize = 14.sp,
                )
            }
        }

        if (filter != PbFilter.Mine) LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 32.dp)) {
            groups.forEach { (title, list) ->
                item(key = "h-$title") {
                    RuOverline(title, Modifier.padding(start = 16.dp, top = 16.dp, bottom = 6.dp))
                }
                items(list, key = { it.id }) { p ->
                    PhraseRow(
                        p = p,
                        box = progress.cards[p.id]?.box ?: 0,
                        favorite = p.id in progress.favorites,
                        playing = playing == p.audio,
                        open = openId == p.id,
                        tags = meta.items[p.id].orEmpty().mapNotNull { id -> meta.themes.firstOrNull { it.id == id }?.title },
                        query = query,
                        onToggleOpen = {
                            openId = if (openId == p.id) null else p.id
                            if (openId == p.id) vm.audio.play(p.audio)
                        },
                        onFavorite = { vm.toggleFavorite(p.id) },
                        hasDeep = p.id in vm.deepIds,
                        onDeepdive = { onDeepdive(p.id) },
                        onPlay = { vm.audio.play(p.audio) },
                        onSlow = { vm.audio.play(p.audio, slow = true) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchField(value: String, onChange: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(HikariCardBg)
            .border(1.dp, HikariBorder, RoundedCornerShape(14.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Default.Search, null, tint = HikariTextMuted, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text("Suchen — Deutsch, Russisch oder »spasibo«", color = HikariTextFaint, fontSize = 15.sp)
            }
            BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = androidx.compose.ui.text.TextStyle(color = HikariText, fontSize = 15.sp),
                cursorBrush = SolidColor(HikariAmber),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Icon(
                Icons.Default.Close,
                "Löschen",
                tint = HikariTextMuted,
                modifier = Modifier.size(18.dp).clickable { onChange("") },
            )
        }
    }
}

@Composable
private fun PbChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) HikariAmber else HikariCardBg)
            .border(1.dp, if (selected) HikariAmber else HikariBorderStrong, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(text, color = if (selected) Color.Black else HikariText, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

@Composable
private fun PhraseRow(
    p: RuPhrase,
    box: Int,
    favorite: Boolean,
    playing: Boolean,
    open: Boolean,
    tags: List<String>,
    query: String,
    onToggleOpen: () -> Unit,
    onFavorite: () -> Unit,
    hasDeep: Boolean,
    onDeepdive: () -> Unit,
    onPlay: () -> Unit,
    onSlow: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().animateContentSize()) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onToggleOpen).padding(horizontal = 16.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(p.display, color = HikariText, fontSize = 16.5.sp, fontWeight = FontWeight.SemiBold)
                Text(translitAnnotated(p.ru), color = HikariTextMuted, fontSize = 13.sp)
                Text(p.de, color = HikariTextFaint, fontSize = 12.5.sp)
            }
            Spacer(Modifier.width(10.dp))
            Column(horizontalAlignment = Alignment.End) {
                Icon(
                    if (favorite) Icons.Default.Star else Icons.Outlined.StarBorder,
                    if (favorite) "Favorit entfernen" else "Als Favorit markieren",
                    tint = if (favorite) HikariAmber else HikariTextMuted,
                    modifier = Modifier.size(22.dp).clickable(onClick = onFavorite),
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(6) { i ->
                        Box(
                            Modifier
                                .size(width = 6.dp, height = 3.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(if (i < box) HikariAmber else HikariBorderStrong),
                        )
                    }
                }
            }
        }
        if (open) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(HikariCardBg)
                    .border(1.dp, HikariBorder, RoundedCornerShape(14.dp))
                    .padding(14.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PbChip(if (playing) "Spielt …" else "Anhören", playing, onPlay)
                    PbChip("Langsam", false, onSlow)
                    PbChip(if (hasDeep) "Vertiefen ›" else "Mehr ›", hasDeep, onDeepdive)
                }
                p.lit?.let {
                    Spacer(Modifier.height(10.dp))
                    RuOverline("Wörtlich")
                    Text("»$it«", color = HikariText, fontSize = 14.sp)
                }
                p.note?.let {
                    Spacer(Modifier.height(10.dp))
                    RuOverline("Gut zu wissen")
                    Text(it, color = HikariText, fontSize = 14.sp)
                }
                if (tags.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text(tags.joinToString(" · "), color = HikariTextFaint, fontSize = 12.sp)
                }
                Spacer(Modifier.height(12.dp))
                PhraseSpeakCheck(p)
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Nachsprechen mit KI-Auswertung: jedes Wort grün oder rot, dazu was gehört wurde. */
@Composable
internal fun PhraseSpeakCheck(p: RuPhrase) {
    val context = LocalContext.current
    val recognizer = remember(p.id) { RussianSpeechRecognizer(context) }
    DisposableEffect(recognizer) { onDispose { recognizer.destroy() } }
    val state by recognizer.state.collectAsState()
    var score by remember(p.id) { mutableStateOf<RuAnswerCheck.SpeechScore?>(null) }
    var evaluating by remember(p.id) { mutableStateOf(false) }
    val listening = state is RuListenState.Listening || state is RuListenState.Partial

    LaunchedEffect(state) {
        val s = state
        if (s is RuListenState.Done || s is RuListenState.Failed) evaluating = false
        if (s is RuListenState.Done) score = RuAnswerCheck.speech(p.plain, s.hypotheses)
    }

    fun start() {
        score = null
        recognizer.reset()
        recognizer.start()
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) start()
    }

    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Row(
            Modifier
                .clip(RoundedCornerShape(22.dp))
                .background(if (listening) HikariAmber.copy(alpha = 0.2f) else HikariBg)
                .border(1.dp, if (listening) HikariAmber else HikariBorderStrong, RoundedCornerShape(22.dp))
                .clickable(enabled = !evaluating) {
                    when {
                        listening -> { evaluating = true; recognizer.stopListening() }
                        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                            PackageManager.PERMISSION_GRANTED -> start()
                        else -> launcher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                }
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                if (listening) Icons.Outlined.GraphicEq else Icons.Outlined.Mic,
                null,
                tint = if (listening) HikariAmber else HikariText,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    evaluating -> "Wird ausgewertet …"
                    listening -> "Ich höre zu … tippen zum Beenden"
                    score != null -> "Nochmal sprechen"
                    else -> "Nachsprechen & prüfen"
                },
                color = if (listening) HikariAmber else HikariText,
                fontSize = 13.5.sp,
                fontWeight = FontWeight.Medium,
            )
        }
        (state as? RuListenState.Failed)?.let {
            Spacer(Modifier.height(8.dp))
            Text(it.message, color = Color(0xFFF87171), fontSize = 12.sp)
        }
        score?.let { sc ->
            Spacer(Modifier.height(10.dp))
            RuSpeechScoreCard(sc)
        }
    }
}

/** Selbst gebaute Sätze aus den Satzbau-Mustern, jeweils mit dem persönlichen Anker. */
@Composable
private fun MySentences(list: List<RuMySentence>, audio: RussianAudioPlayer, onRemove: (String) -> Unit) {
    if (list.isEmpty()) {
        Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
            Text("Noch keine eigenen Sätze. Im Bereich »Satzbau« baust und speicherst du welche.", color = HikariTextMuted, fontSize = 14.sp)
        }
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 32.dp, top = 8.dp)) {
        items(list, key = { it.ru }) { s ->
            Box(Modifier.padding(vertical = 5.dp)) {
                Column {
                    PhraseCard(RuDeepdiveLogic.asPhrase(s.ru, s.de), audio, null)
                    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (s.note.isNotBlank()) "Für: ${s.note}" else "",
                            color = HikariTextFaint,
                            fontSize = 12.5.sp,
                            modifier = Modifier.weight(1f),
                        )
                        Text("Entfernen", color = HikariTextMuted, fontSize = 12.5.sp, modifier = Modifier.clickable { onRemove(s.ru) })
                    }
                }
            }
        }
    }
}
