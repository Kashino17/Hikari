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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.domain.russian.RuDeepdiveLogic
import com.hikari.app.domain.russian.RuPhrase
import com.hikari.app.domain.russian.RussianCourseRepository
import com.hikari.app.domain.russian.RussianPhonetics
import com.hikari.app.domain.russian.RussianProgressStore
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariBorder
import com.hikari.app.ui.theme.HikariCardBg
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.launch

@HiltViewModel
class RussianDeepdiveViewModel @Inject constructor(
    savedState: SavedStateHandle,
    repo: RussianCourseRepository,
    store: RussianProgressStore,
    val audio: RussianAudioPlayer,
) : ViewModel() {
    private val settings = store.state.value.settings
    val id: String = savedState.get<String>("id").orEmpty()
    val phrase: RuPhrase? = repo.index.phrase(id, settings)
    val entry = repo.deepdive[id]
    val related: List<RuPhrase> = phrase?.let {
        RuDeepdiveLogic.related(it, repo.index.phrasesUpTo(store.state.value.unlockedDay(repo.index.days.size), settings))
    }.orEmpty()

    init {
        viewModelScope.launch { audio.warmUp() }
    }

    override fun onCleared() {
        audio.stop()
    }
}

/** Text mit "+"-Betonungsmarken (aus den Inhaltsdateien) für die Anzeige aufbereiten. */
internal fun mk(text: String) = RussianPhonetics.display(text)

/** Deepdive zu einem Satz: Varianten (formell/locker), Beispiele mit Erklärung, männlich/weiblich, Grammatik. */
@Composable
fun RussianDeepdiveScreen(onBack: () -> Unit, vm: RussianDeepdiveViewModel = hiltViewModel()) {
    val playing by vm.audio.playing.collectAsState()
    val p = vm.phrase
    val e = vm.entry
    LazyColumn(
        Modifier.fillMaxSize().background(HikariBg),
        contentPadding = PaddingValues(bottom = 40.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack), contentAlignment = Alignment.Center) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Zurück", tint = HikariText)
                }
                Text("Vertiefen", color = HikariText, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 4.dp))
            }
        }
        if (p == null) {
            item { Text("Dieser Satz wurde nicht gefunden.", color = HikariTextMuted, modifier = Modifier.padding(24.dp)) }
            return@LazyColumn
        }

        item {
            Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                PhraseCard(p, vm.audio, playing, highlight = true)
                if (e != null && e.register.isNotBlank()) {
                    Spacer(Modifier.height(10.dp))
                    Tag(mk(e.register))
                }
                if (e != null && e.summary.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Text(mk(e.summary), color = HikariText, fontSize = 15.sp, lineHeight = 22.sp)
                }
                if (e == null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        "Für diesen Satz gibt es noch keine ausführliche Vertiefung. Unten siehst du, wo seine Wörter noch vorkommen.",
                        color = HikariTextMuted,
                        fontSize = 13.sp,
                    )
                    p.lit?.let { Spacer(Modifier.height(8.dp)); Text("Wörtlich: »$it«", color = HikariText, fontSize = 14.sp) }
                    p.note?.let { Spacer(Modifier.height(4.dp)); Text(mk(it), color = HikariText, fontSize = 14.sp) }
                }
            }
        }

        if (e != null && e.variants.isNotEmpty()) {
            item { Section("Synonyme & Varianten", "Wann sagt man was? Locker, höflich, förmlich.") }
            items(e.variants.size) { i ->
                val v = e.variants[i]
                Box(Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                    PhraseCard(RuDeepdiveLogic.asPhrase(v.ru, v.de), vm.audio, playing, explain = v.use.takeIf { it.isNotBlank() }?.let { "Wann: ${mk(it)}" })
                }
            }
        }

        if (e != null && e.examples.isNotEmpty()) {
            item { Section("Beispielsätze mit Erklärung", "So sitzt das Wort im Satz — und warum es grammatisch so steht.") }
            items(e.examples.size) { i ->
                val x = e.examples[i]
                Box(Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                    PhraseCard(RuDeepdiveLogic.asPhrase(x.ru, x.de), vm.audio, playing, explain = x.why.takeIf { it.isNotBlank() }?.let { "Warum so: ${mk(it)}" })
                }
            }
        }

        if (e?.gender != null) {
            val g = e.gender
            item { Section("Männlich / weiblich", g.title) }
            item {
                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Text(mk(g.text), color = HikariText, fontSize = 15.sp, lineHeight = 22.sp)
                }
            }
            items(g.forms.size) { i ->
                val f = g.forms[i]
                Box(Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                    PhraseCard(RuDeepdiveLogic.asPhrase(f.ru, f.de), vm.audio, playing, explain = f.use.takeIf { it.isNotBlank() }?.let { mk(it) })
                }
            }
        }

        if (e?.grammar != null) {
            item { Section("Grammatik dahinter", "Die Regel, mit der du eigene Sätze baust.") }
            item {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(HikariCardBg)
                        .border(1.dp, HikariBorder, RoundedCornerShape(14.dp))
                        .padding(14.dp),
                ) {
                    Text(mk(e.grammar), color = HikariText, fontSize = 15.sp, lineHeight = 22.sp)
                }
            }
        }

        if (vm.related.isNotEmpty()) {
            item { Section("Wo es noch vorkommt", "Dieselben Wörter in anderen Sätzen deines Kurses.") }
            items(vm.related.size) { i ->
                val r = vm.related[i]
                Box(Modifier.padding(horizontal = 16.dp, vertical = 5.dp)) {
                    PhraseCard(r, vm.audio, playing, small = true)
                }
            }
        }
    }
}

@Composable
internal fun Section(title: String, hint: String) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 22.dp, bottom = 6.dp)) {
        RuOverline(title)
        Text(hint, color = HikariTextFaint, fontSize = 12.5.sp)
    }
}

@Composable
internal fun Tag(text: String) {
    Box(
        Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(HikariAmber.copy(alpha = 0.16f))
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(text, color = HikariAmber, fontSize = 12.5.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Ein Satz mit Anhören (normal/langsam), Nachsprechen mit KI-Prüfung und optionaler Erklärung. */
@Composable
internal fun PhraseCard(
    p: RuPhrase,
    audio: RussianAudioPlayer,
    playing: String?,
    highlight: Boolean = false,
    small: Boolean = false,
    explain: String? = null,
) {
    var speak by remember(p.id) { mutableStateOf(false) }
    val hasAudio = audio.has(p.audio)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(HikariCardBg)
            .border(1.dp, if (highlight) HikariAmber.copy(alpha = 0.5f) else HikariBorder, RoundedCornerShape(14.dp))
            .padding(14.dp),
    ) {
        Text(
            p.display,
            color = HikariText,
            fontSize = if (highlight) 22.sp else if (small) 16.sp else 18.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Text(translitAnnotated(p.ru), color = HikariTextMuted, fontSize = 13.sp)
        Text(p.de, color = HikariTextFaint, fontSize = 13.sp)
        explain?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = HikariText, fontSize = 14.sp, lineHeight = 20.sp)
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (hasAudio) {
                DeepChip(if (playing == p.audio) "Spielt …" else "Anhören", playing == p.audio) { audio.play(p.audio) }
                DeepChip("Langsam", false) { audio.play(p.audio, slow = true) }
            }
            DeepChip(if (speak) "Prüfung zu" else "Nachsprechen", speak) { speak = !speak }
        }
        if (speak) {
            Spacer(Modifier.height(10.dp))
            PhraseSpeakCheck(p)
        }
    }
}

@Composable
internal fun DeepChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(18.dp))
            .background(if (selected) HikariAmber else Color.Transparent)
            .border(1.dp, if (selected) HikariAmber else com.hikari.app.ui.theme.HikariBorderStrong, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(text, color = if (selected) Color.Black else HikariText, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}
