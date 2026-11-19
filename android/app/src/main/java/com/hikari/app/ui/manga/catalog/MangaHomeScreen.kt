package com.hikari.app.ui.manga.catalog

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.ui.graphics.graphicsLayer
import android.content.Context
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.hikari.app.data.api.dto.MangaCatalogCardDto
import com.hikari.app.data.api.dto.MangaCatalogHomeDto
import com.hikari.app.data.api.dto.MangaContinueDto
import com.hikari.app.data.prefs.SettingsStore
import com.hikari.app.domain.repo.MangaCatalogFilter
import com.hikari.app.domain.repo.MangaRepository
import com.hikari.app.ui.manga.MangaAccent
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariSurface
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MangaHomeState(
    val lang: String = "all",
    val home: MangaCatalogHomeDto? = null,
    val loading: Boolean = true,
    val offline: Boolean = false,
    val reading: List<MangaContinueDto> = emptyList(),
    /** Gerade wird eine Reihe geöffnet (der Mac holt die Kapitel). */
    val opening: String? = null,
    val openError: String? = null,
)

@HiltViewModel
class MangaHomeViewModel @Inject constructor(
    private val repo: MangaRepository,
    settings: SettingsStore,
    @ApplicationContext ctx: Context,
) : ViewModel() {
    private val prefs = ctx.getSharedPreferences("hikari_manga_catalog", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(MangaHomeState(lang = prefs.getString("lang", "all") ?: "all"))
    val state: StateFlow<MangaHomeState> = _state.asStateFlow()
    val backendUrl: StateFlow<String> = settings.backendUrl.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            val home = runCatching { repo.catalogHome(_state.value.lang) }
            val reading = runCatching { repo.getContinue() }.getOrNull()
            _state.update {
                it.copy(loading = false, offline = home.isFailure, home = home.getOrNull() ?: it.home, reading = reading ?: it.reading)
            }
        }
    }

    fun setLang(l: String) {
        if (l == _state.value.lang) return
        prefs.edit().putString("lang", l).apply()
        _state.update { it.copy(lang = l) }
        refresh()
    }

    fun open(card: MangaCatalogCardDto, then: (seriesId: String) -> Unit) {
        if (_state.value.opening != null) return
        _state.update { it.copy(opening = card.displayTitle, openError = null) }
        viewModelScope.launch {
            runCatching { repo.openCatalog(card.id) }
                .onSuccess { id -> _state.update { it.copy(opening = null) }; then(id) }
                .onFailure { _state.update { it.copy(opening = null, openError = "„${card.displayTitle}“ lässt sich gerade nicht öffnen.") } }
        }
    }

    fun cover(base: String, path: String?) = repo.catalogCoverUrl(base, path)
    fun seriesCover(base: String, path: String?) = path?.takeIf { it.isNotBlank() }?.let { repo.coverImageUrl(base, it) }
}

/** Wohin „Alle“ bei einem Katalog-Regal führt. */
internal fun shelfFilter(key: String, lang: String, title: String): MangaCatalogFilter = when (key) {
    "de" -> MangaCatalogFilter(lang = "de", title = title)
    "completed" -> MangaCatalogFilter(lang = lang, status = "completed", title = title)
    "romcom" -> MangaCatalogFilter(lang = lang, genre = "Romantik,Komödie", title = title)
    "action" -> MangaCatalogFilter(lang = lang, genre = "Action", title = title)
    "isekai" -> MangaCatalogFilter(lang = lang, genre = "Isekai", title = title)
    "webtoon" -> MangaCatalogFilter(lang = lang, kind = "Manhwa,Manhua,Webcomic", title = title)
    else -> MangaCatalogFilter(lang = lang, title = title)
}

/**
 * Manga-Bereich: oben Suche und Sprache (Alle / Deutsch / Englisch), darunter Entdecken, Meine Mangas, Genres.
 * Jede Reihe trägt deutlich DE oder EN.
 */
@Composable
fun MangaHomeScreen(
    onSeries: (seriesId: String) -> Unit,
    onContinue: (seriesId: String, chapterId: String, page: Int) -> Unit,
    onList: (MangaCatalogFilter, focusSearch: Boolean) -> Unit,
    onOfficial: () -> Unit,
    mine: @Composable () -> Unit,
    vm: MangaHomeViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsState()
    val base by vm.backendUrl.collectAsState()
    var tab by rememberSaveable { mutableStateOf("entdecken") }
    val openCard: (MangaCatalogCardDto) -> Unit = { c -> vm.open(c, onSeries) }
    Box(Modifier.fillMaxSize().background(HikariBg)) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp)).background(HikariSurface).clickable { onList(MangaCatalogFilter(lang = s.lang), true) }
                    .padding(horizontal = 14.dp, vertical = 13.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Search, null, tint = HikariTextMuted, modifier = Modifier.size(20.dp))
                Text("Titel, Zeichner oder Genre", color = HikariTextFaint, fontSize = 15.sp, modifier = Modifier.padding(start = 10.dp))
            }
            LangSwitch(s.lang, vm::setLang)
            Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                for ((k, l) in listOf("entdecken" to "Entdecken", "meine" to "Meine Mangas", "genres" to "Genres")) {
                    val on = k == tab
                    Column(Modifier.clickable { tab = k }.padding(top = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(l, color = if (on) HikariText else HikariTextMuted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Box(Modifier.padding(top = 9.dp).height(3.dp).width(if (on) 36.dp else 0.dp).clip(RoundedCornerShape(3.dp)).background(MangaAccent))
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x14FFFFFF)))
            when (tab) {
                "meine" -> Box(Modifier.weight(1f)) { mine() }
                "genres" -> GenresTab(s, { vm.cover(base, it) }, onList)
                else -> DiscoverTab(s, base, vm, onContinue, onList, onOfficial, openCard)
            }
        }
        if (s.opening != null) {
            Box(Modifier.fillMaxSize().background(Color(0xB3000000)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MangaAccent)
                    Text("„${s.opening}“ wird geöffnet …", color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(top = 14.dp))
                    Text("Der Mac holt die Kapitelliste.", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun LangSwitch(lang: String, onLang: (String) -> Unit) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Sprache", color = HikariTextMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(end = 10.dp))
        Row(
            Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).background(HikariSurface).border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(12.dp)).padding(3.dp),
            horizontalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            for ((k, l) in listOf("all" to "Alle", "de" to "Deutsch", "en" to "Englisch")) {
                val on = k == lang
                Row(
                    Modifier.weight(1f).clip(RoundedCornerShape(9.dp)).background(if (on) Color(0xFF2C2A33) else Color.Transparent)
                        .clickable { onLang(k) }.padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (k != "all") LangBadge(k, modifier = Modifier.padding(end = 6.dp))
                    Text(l, color = if (on) HikariText else HikariTextMuted, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun DiscoverTab(
    s: MangaHomeState,
    base: String,
    vm: MangaHomeViewModel,
    onContinue: (String, String, Int) -> Unit,
    onList: (MangaCatalogFilter, Boolean) -> Unit,
    onOfficial: () -> Unit,
    openCard: (MangaCatalogCardDto) -> Unit,
) {
    val home = s.home
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
        s.openError?.let { e -> item { Text(e, color = Color(0xFFF87171), fontSize = 13.sp, modifier = Modifier.padding(16.dp)) } }
        if (s.offline) item {
            Text("Katalog gerade nicht erreichbar — deine Reihen findest du unter „Meine Mangas“.", color = HikariTextMuted, fontSize = 13.sp,
                modifier = Modifier.padding(16.dp).clickable { vm.refresh() })
        }
        val reading = s.reading.filter { s.lang == "all" || langOfChapter(it.chapterId) == s.lang }
        reading.firstOrNull()?.let { c -> item { ContinueCard(c, vm.seriesCover(base, c.coverPath)) { onContinue(c.seriesId, c.chapterId, c.pageNumber) } } }
        if (reading.size > 1) item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                items(reading.drop(1), key = { it.seriesId }) { c ->
                    Row(Modifier.clip(RoundedCornerShape(12.dp)).background(HikariSurface).clickable { onContinue(c.seriesId, c.chapterId, c.pageNumber) }.padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        AsyncImage(vm.seriesCover(base, c.coverPath), null, contentScale = ContentScale.Crop, modifier = Modifier.size(width = 30.dp, height = 44.dp).clip(RoundedCornerShape(4.dp)))
                        Column(Modifier.padding(start = 8.dp).width(120.dp)) {
                            Text(c.title, color = HikariText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                LangBadge(langOfChapter(c.chapterId))
                                Text(" Kap. ${c.chapterId.substringAfterLast(':')}", color = HikariTextMuted, fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
        item { OfficialBanner(onOfficial) }
        if (home != null && home.genres.isNotEmpty()) item {
            Header("Wonach ist dir?", null, null)
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(home.genres.take(10), key = { it.name }) { g ->
                    Row(Modifier.clip(RoundedCornerShape(20.dp)).background(HikariSurface).clickable { onList(MangaCatalogFilter(lang = s.lang, genre = g.name, title = g.name), false) }
                        .padding(horizontal = 14.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(genreColor(g.name)))
                        Text(g.name, color = HikariText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        if (home == null && s.loading) item {
            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = MangaAccent) }
        }
        if (home != null && home.shelves.isEmpty()) item {
            Text(if (home.syncing) "Der Mac sammelt gerade den Katalog — gleich sind die ersten Reihen da." else "Noch keine Reihen im Katalog.",
                color = HikariTextMuted, fontSize = 14.sp, modifier = Modifier.padding(24.dp))
        }
        home?.shelves?.forEach { shelf ->
            item(key = "shelf-${shelf.key}") {
                Header(shelf.title, shelf.subtitle) { onList(shelfFilter(shelf.key, s.lang, shelf.title), false) }
                CatalogRow(shelf.items, { vm.cover(base, it.coverUrl) }, openCard)
            }
        }
        if (home != null && home.shelves.isNotEmpty()) item {
            Text(
                "Reihen aus dem Katalog sind Fan-Übersetzungen über MangaDex; das steht auch auf jeder Reihe. Offizielle Kapitel gibt es oben bei Shueisha.",
                color = HikariTextFaint, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
            )
        }
    }
}

@Composable
private fun Header(title: String, subtitle: String?, onAll: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(title, color = HikariText, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            if (!subtitle.isNullOrBlank()) Text(subtitle, color = HikariTextMuted, fontSize = 13.sp)
        }
        if (onAll != null) Text("Alle", color = MangaAccent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAll).padding(8.dp))
    }
}

@Composable
private fun ContinueCard(c: MangaContinueDto, cover: String?, onClick: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF2B2540), HikariSurface)))
            .border(1.dp, MangaAccent.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AsyncImage(cover, null, contentScale = ContentScale.Crop, modifier = Modifier.size(width = 70.dp, height = 104.dp).clip(RoundedCornerShape(6.dp)).background(HikariSurface))
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text("WEITERLESEN", color = MangaAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
            Text(c.title, color = HikariText, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Text("Kapitel ${c.chapterId.substringAfterLast(':')} · Seite ${c.pageNumber}  ", color = HikariTextMuted, fontSize = 13.sp)
                LangBadge(langOfChapter(c.chapterId))
            }
        }
        Box(Modifier.size(46.dp).clip(CircleShape).background(MangaAccent), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.PlayArrow, "Weiterlesen", tint = Color(0xFF160F2A))
        }
    }
}

@Composable
private fun OfficialBanner(onClick: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(14.dp))
            .background(Color(0x1A4ADE80)).border(1.dp, Color(0x334ADE80), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Verified, null, tint = Color(0xFF86EFAC), modifier = Modifier.size(26.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text("Offiziell reinlesen bei Shueisha", color = HikariText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 3.dp)) {
                LangBadge("de"); Spacer(Modifier.width(4.dp)); LangBadge("en")
                Text("  MANGA MILLION · meist die ersten Kapitel", color = HikariTextMuted, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun GenresTab(s: MangaHomeState, cover: (String) -> String?, onList: (MangaCatalogFilter, Boolean) -> Unit) {
    val genres = s.home?.genres.orEmpty()
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        items(genres, key = { it.name }) { g ->
            val c = genreColor(g.name)
            Box(
                Modifier.height(112.dp).clip(RoundedCornerShape(14.dp))
                    .background(Brush.linearGradient(listOf(c, Color(c.red * 0.45f, c.green * 0.45f, c.blue * 0.45f))))
                    .clickable { onList(MangaCatalogFilter(lang = s.lang, genre = g.name, title = g.name), false) },
            ) {
                // Collage: drei echte Cover aus dem Genre, leicht gefächert (wechseln alle sechs Stunden).
                val covers = g.covers.take(3)
                covers.forEachIndexed { i, path ->
                    val rot = listOf(-9f, 4f, 15f)[i + (3 - covers.size)]
                    val end = listOf(58.dp, 30.dp, 4.dp)[i + (3 - covers.size)]
                    AsyncImage(
                        model = cover(path), contentDescription = null, contentScale = ContentScale.Crop,
                        modifier = Modifier.align(Alignment.BottomEnd).padding(end = end)
                            .graphicsLayer { rotationZ = rot; translationY = 26f * density; shadowElevation = 8f * density; shape = RoundedCornerShape(5.dp); clip = true }
                            .size(width = 48.dp, height = 72.dp).background(HikariSurface),
                    )
                }
                Column(Modifier.padding(12.dp).fillMaxWidth(0.62f).fillMaxHeight()) {
                    Text(g.name, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text("${g.count} Reihen", color = Color.White.copy(alpha = 0.78f), fontSize = 12.sp)
                }
            }
        }
        for ((k, label, sub) in listOf(Triple("Manga", "Manga", "Japan"), Triple("Manhwa", "Manhwa", "Korea"), Triple("Webcomic", "Webcomics", "aus dem Netz"))) {
            item(key = "kind-$k") {
                Column(
                    Modifier.height(96.dp).clip(RoundedCornerShape(14.dp)).background(HikariSurface).border(1.dp, MangaAccent.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                        .clickable { onList(MangaCatalogFilter(lang = s.lang, kind = k, title = label), false) }.padding(12.dp),
                ) {
                    Text(label, color = HikariText, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text(sub, color = HikariTextMuted, fontSize = 12.sp)
                }
            }
        }
    }
}
