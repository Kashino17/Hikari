package com.hikari.app.ui.manga.catalog

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.api.dto.MangaCatalogCardDto
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
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MangaListState(
    val filter: MangaCatalogFilter = MangaCatalogFilter(),
    val items: List<MangaCatalogCardDto> = emptyList(),
    val total: Int = 0,
    val loading: Boolean = false,
    val error: Boolean = false,
    val opening: String? = null,
    val openError: String? = null,
)

@HiltViewModel
class MangaCatalogListViewModel @Inject constructor(
    private val repo: MangaRepository,
    settings: SettingsStore,
    private val handle: SavedStateHandle,
) : ViewModel() {
    private fun arg(k: String) = handle.get<String>(k)?.takeIf { it.isNotBlank() }
    private val _state = MutableStateFlow(
        MangaListState(
            filter = MangaCatalogFilter(
                lang = arg("lang") ?: "all", genre = arg("genre"), kind = arg("kind"), status = arg("status"),
                sort = arg("sort") ?: "pop", q = arg("q"), title = arg("title"),
            ),
        ),
    )
    val state: StateFlow<MangaListState> = _state.asStateFlow()
    val backendUrl: StateFlow<String> = settings.backendUrl.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    private var job: Job? = null

    init { load(true, 0) }

    fun setFilter(f: MangaCatalogFilter, debounce: Long = 0) {
        _state.update { it.copy(filter = f) }
        load(true, debounce)
    }

    fun more() {
        val s = _state.value
        if (!s.loading && s.items.size < s.total) load(false, 0)
    }

    private fun load(reset: Boolean, debounce: Long) {
        job?.cancel()
        val f = _state.value.filter
        val offset = if (reset) 0 else _state.value.items.size
        _state.update { it.copy(loading = true, error = false) }
        job = viewModelScope.launch {
            if (debounce > 0) delay(debounce)
            try {
                val r = repo.catalogList(f, offset)
                _state.update { it.copy(items = if (reset) r.items else it.items + r.items, total = r.total, loading = false, error = false) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e // überholt von einer neueren Suche — nichts anzeigen
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = true) }
            }
        }
    }

    fun open(card: MangaCatalogCardDto, then: (String) -> Unit) {
        if (_state.value.opening != null) return
        _state.update { it.copy(opening = card.displayTitle) }
        viewModelScope.launch {
            val r = runCatching { repo.openCatalog(card.id) }
            _state.update { it.copy(opening = null, openError = if (r.isFailure) "„${card.displayTitle}“ lässt sich gerade nicht öffnen." else null) }
            r.onSuccess(then)
        }
    }

    fun cover(base: String, path: String?) = repo.catalogCoverUrl(base, path)
}

/** Liste aus dem Katalog — Suche, Sprache, Sortierung und Status; lädt beim Blättern nach. */
@Composable
fun MangaCatalogListScreen(
    focusSearch: Boolean,
    onBack: () -> Unit,
    onSeries: (String) -> Unit,
    vm: MangaCatalogListViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsState()
    val base by vm.backendUrl.collectAsState()
    val f = s.filter
    var query by remember { mutableStateOf(f.q ?: "") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { if (focusSearch) runCatching { focus.requestFocus() } }
    val grid = rememberLazyGridState()
    val nearEnd by remember { derivedStateOf { (grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) > s.items.size - 8 } }
    LaunchedEffect(nearEnd, s.items.size) { if (nearEnd) vm.more() }
    Box(Modifier.fillMaxSize().background(HikariBg).statusBarsPadding()) {
        Column(Modifier.fillMaxSize()) {
            Row(Modifier.padding(end = 12.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Zurück", tint = HikariText) }
                TextField(
                    value = query,
                    onValueChange = { query = it; vm.setFilter(f.copy(q = it.trim().ifBlank { null }), 250) },
                    placeholder = { Text("Titel, Zeichner oder Genre", color = HikariTextFaint) },
                    leadingIcon = { Icon(Icons.Outlined.Search, null, tint = HikariTextMuted) },
                    trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = ""; vm.setFilter(f.copy(q = null)) }) { Icon(Icons.Outlined.Close, "Leeren", tint = HikariTextMuted) } },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    shape = RoundedCornerShape(14.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = HikariSurface, unfocusedContainerColor = HikariSurface,
                        focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                        focusedTextColor = HikariText, unfocusedTextColor = HikariText, cursorColor = MangaAccent,
                    ),
                    modifier = Modifier.weight(1f).focusRequester(focus),
                )
            }
            s.openError?.let { Text(it, color = Color(0xFFF87171), fontSize = 13.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) }
            if (f.title != null) Text(f.title, color = HikariText, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, top = 6.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("all" to "Alle", "de" to "Deutsch", "en" to "Englisch"), key = { "l" + it.first }) { (k, l) ->
                    Pick(on = f.lang == k, onClick = { vm.setFilter(f.copy(lang = k)) }) {
                        if (k != "all") LangBadge(k, modifier = Modifier.padding(end = 6.dp))
                        Text(l, color = if (f.lang == k) Color(0xFF160F2A) else HikariText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                items(listOf("pop" to "Beliebt", "az" to "A–Z", "new" to "Neu"), key = { "s" + it.first }) { (k, l) ->
                    Pick(on = f.sort == k, onClick = { vm.setFilter(f.copy(sort = k)) }) {
                        Text(l, color = if (f.sort == k) Color(0xFF160F2A) else HikariText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                items(listOf("completed" to "Abgeschlossen", "ongoing" to "Laufend"), key = { "st" + it.first }) { (k, l) ->
                    Pick(on = f.status == k, onClick = { vm.setFilter(f.copy(status = if (f.status == k) null else k)) }) {
                        Text(l, color = if (f.status == k) Color(0xFF160F2A) else HikariText, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Text(
                when {
                    s.loading && s.items.isEmpty() -> "Sucht …"
                    s.error && s.items.isEmpty() -> "Katalog gerade nicht erreichbar"
                    else -> "${s.total} Reihen"
                },
                color = HikariTextMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp),
            )
            LazyVerticalGrid(
                state = grid,
                columns = GridCells.Adaptive(108.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 10.dp, bottom = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(s.items, key = { it.id }) { c -> CatalogCard(c, vm.cover(base, c.coverUrl), null) { vm.open(c, onSeries) } }
                if (!s.loading && s.items.isEmpty() && !s.error) item(span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Nichts gefunden", color = HikariText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text(if (f.lang != "all") "Vielleicht gibt es die Reihe nur in der anderen Sprache." else "Versuch den japanischen oder englischen Titel.",
                            color = HikariTextMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
        if (s.opening != null) {
            Box(Modifier.fillMaxSize().background(Color(0xB3000000)), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MangaAccent)
                    Text("„${s.opening}“ wird geöffnet …", color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(top = 14.dp))
                }
            }
        }
    }
}

@Composable
private fun Pick(on: Boolean, onClick: () -> Unit, content: @Composable () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(20.dp)).background(if (on) MangaAccent else HikariSurface).clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/** Shueishas offizieller Leser (MANGA MILLION) — sichtbar in Hikari, so wie im Browser. */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun MangaOfficialScreen(onBack: () -> Unit) {
    var web by remember { mutableStateOf<WebView?>(null) }
    BackHandler { if (web?.canGoBack() == true) web?.goBack() else onBack() }
    Column(Modifier.fillMaxSize().background(Color.Black).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().background(HikariBg).padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Zurück", tint = HikariText) }
            Icon(Icons.Outlined.Verified, null, tint = Color(0xFF86EFAC), modifier = Modifier.padding(end = 8.dp))
            Column(Modifier.weight(1f)) {
                Text("Offiziell bei Shueisha", color = HikariText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Text("MANGA MILLION · kostenlos, solange die Aktion läuft", color = HikariTextMuted, fontSize = 12.sp)
            }
        }
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { c ->
                WebView(c).apply {
                    layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = WebViewClient()
                    loadUrl("https://mangamillion.shueisha.co.jp/de")
                    web = this
                }
            },
            onRelease = { it.destroy() },
        )
    }
}
