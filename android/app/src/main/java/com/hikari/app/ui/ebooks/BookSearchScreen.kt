package com.hikari.app.ui.ebooks

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.api.dto.EbookAuthorDto
import com.hikari.app.data.api.dto.EbookCardDto
import com.hikari.app.data.api.dto.EbookCategoryDto
import com.hikari.app.domain.ebooks.EbookFilter
import com.hikari.app.domain.ebooks.EbookRepository
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariSurface
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.text.Normalizer
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Für Suche und Hervorhebung: klein, ohne Akzente, ß → ss („Marchen“ findet „Märchen“). */
internal fun foldForSearch(s: String): String =
    Normalizer.normalize(s.lowercase(), Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").replace("ß", "ss")

data class BookSearchState(
    val query: String = "",
    val format: String? = null,
    val length: String? = null,
    val sort: String = "beliebt",
    val books: List<EbookCardDto> = emptyList(),
    val total: Int = 0,
    val loading: Boolean = false,
    val authors: List<EbookAuthorDto> = emptyList(),
    val categories: List<EbookCategoryDto> = emptyList(),
    val recent: List<String> = emptyList(),
)

@HiltViewModel
class BookSearchViewModel @Inject constructor(
    private val repo: EbookRepository,
    @ApplicationContext ctx: Context,
) : ViewModel() {
    private val prefs = ctx.getSharedPreferences("hikari_book_search", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(BookSearchState(recent = prefs.getString("recent", null)?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()))
    val state: StateFlow<BookSearchState> = _state.asStateFlow()
    private var job: Job? = null

    init {
        viewModelScope.launch {
            repo.home().onSuccess { h -> _state.update { it.copy(authors = h.authors, categories = h.categories) } }
        }
    }

    fun setQuery(q: String) { _state.update { it.copy(query = q) }; search() }
    fun setFormat(f: String?) { _state.update { it.copy(format = f) }; search() }
    fun setLength(l: String?) { _state.update { it.copy(length = l) }; search() }
    fun setSort(s: String) { _state.update { it.copy(sort = s) }; search() }

    /** Während des Tippens: kurz warten, dann suchen — alte Anfragen werden verworfen. */
    private fun search() {
        job?.cancel()
        val s = _state.value
        if (s.query.isBlank()) { _state.update { it.copy(books = emptyList(), total = 0, loading = false) }; return }
        _state.update { it.copy(loading = true) }
        job = viewModelScope.launch {
            delay(220)
            repo.list(EbookFilter(q = s.query.trim(), format = s.format, length = s.length, sort = s.sort), 0, 40)
                .onSuccess { r -> _state.update { it.copy(books = r.books, total = r.total, loading = false) } }
                .onFailure { _state.update { it.copy(loading = false) } }
        }
    }

    fun remember(q: String) {
        val v = q.trim()
        if (v.length < 2) return
        val list = (listOf(v) + _state.value.recent.filter { foldForSearch(it) != foldForSearch(v) }).take(8)
        prefs.edit().putString("recent", list.joinToString("\n")).apply()
        _state.update { it.copy(recent = list) }
    }

    fun clearRecent() { prefs.edit().remove("recent").apply(); _state.update { it.copy(recent = emptyList()) } }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun BookSearchScreen(
    onBack: () -> Unit,
    onBook: (String) -> Unit,
    onList: (EbookFilter) -> Unit,
    vm: BookSearchViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsState()
    val focus = remember { FocusRequester() }
    var filters by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    val q = foldForSearch(s.query.trim())
    Column(Modifier.fillMaxSize().background(HikariBg).statusBarsPadding()) {
        Row(Modifier.padding(horizontal = 4.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Zurück", tint = HikariText) }
            TextField(
                value = s.query,
                onValueChange = vm::setQuery,
                placeholder = { Text("Titel, Autor oder Thema", color = HikariTextFaint) },
                leadingIcon = { Icon(Icons.Outlined.Search, null, tint = HikariTextMuted) },
                trailingIcon = { if (s.query.isNotEmpty()) IconButton(onClick = { vm.setQuery("") }) { Icon(Icons.Outlined.Close, "Leeren", tint = HikariTextMuted) } },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.remember(s.query) }),
                shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = HikariSurface, unfocusedContainerColor = HikariSurface,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = HikariText, unfocusedTextColor = HikariText, cursorColor = BooksAccent,
                ),
                modifier = Modifier.weight(1f).focusRequester(focus),
            )
            IconButton(onClick = { filters = true }) {
                Icon(Icons.Outlined.Tune, "Filter", tint = if (s.format != null || s.length != null) BooksAccent else HikariText)
            }
        }
        if (s.format != null || s.length != null) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                s.format?.let { ActiveChip(it.uppercase()) { vm.setFormat(null) } }
                s.length?.let { ActiveChip("Kurz") { vm.setLength(null) } }
            }
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 80.dp)) {
            if (q.isEmpty()) {
                if (s.recent.isNotEmpty()) item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Label("Zuletzt gesucht", Modifier.weight(1f))
                        Text("Löschen", color = HikariTextMuted, fontSize = 13.sp, modifier = Modifier.clickable(onClick = vm::clearRecent).padding(16.dp))
                    }
                    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (r in s.recent) Chip(r, Icons.Outlined.History) { vm.setQuery(r) }
                    }
                }
                item {
                    Label("Oft gesucht")
                    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (r in listOf("Kafka", "Goethe", "Märchen", "Nietzsche", "Krimi", "Reise", "Hesse")) Chip(r, null) { vm.setQuery(r) }
                    }
                }
                if (s.authors.isNotEmpty()) item {
                    Label("Autoren")
                    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (a in s.authors.take(12)) Chip(authorShort(a.name), null) { onList(EbookFilter(author = a.name)) }
                    }
                }
            } else {
                val tokens = q.split(Regex("\\s+")).filter { it.isNotBlank() }
                val authors = s.authors.filter { a -> tokens.all { foldForSearch(a.name).contains(it) } }.take(3)
                val cats = s.categories.filter { c -> tokens.all { foldForSearch(c.label).contains(it) || foldForSearch(c.id).contains(it) } }.take(3)
                if (authors.isNotEmpty()) {
                    item { Label("Autoren") }
                    items(authors, key = { "a-" + it.name }) { a ->
                        Row(Modifier.fillMaxWidth().clickable { vm.remember(s.query); onList(EbookFilter(author = a.name)) }.padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            AuthorAvatar(a.name, a.photoUrl, 44.dp, 18.sp)
                            Column(Modifier.padding(start = 12.dp)) {
                                Text(highlight(a.name, tokens.first()), color = HikariText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                                Text("${a.count} Bücher", color = HikariTextMuted, fontSize = 13.sp)
                            }
                        }
                    }
                }
                if (cats.isNotEmpty()) item {
                    Label("Kategorien")
                    FlowRow(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        for (c in cats) Chip(c.label, null, dot = categoryColor(c.id)) { onList(EbookFilter(category = c.id)) }
                    }
                }
                item { Label(if (s.loading && s.books.isEmpty()) "Bücher …" else "Bücher · ${s.total}") }
                items(s.books, key = { "b-" + it.id }) { b ->
                    Row(Modifier.fillMaxWidth().clickable { vm.remember(s.query); onBook(b.id) }.padding(horizontal = 16.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        BookCover(b.title, b.authors.joinToString(", "), b.categories.firstOrNull(), b.isPdf, b.coverUrl, Modifier.size(width = 48.dp, height = 72.dp))
                        Column(Modifier.weight(1f).padding(start = 14.dp)) {
                            Text(highlight(shortTitle(b.title), tokens.first()), color = HikariText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(highlight(b.authors.joinToString(", "), tokens.first()), color = HikariTextMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(listOfNotNull(if (b.isPdf) "PDF" else "EPUB", b.minutes?.let(::readingTime)).joinToString(" · "), color = HikariTextFaint, fontSize = 12.sp)
                        }
                    }
                }
                if (!s.loading && s.books.isEmpty() && authors.isEmpty() && cats.isEmpty()) item {
                    Column(Modifier.fillMaxWidth().padding(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Nichts gefunden für „${s.query.trim()}“", color = HikariText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                        Text("Versuch einen Teil des Titels oder nur den Nachnamen.", color = HikariTextMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
        }
    }
    if (filters) {
        ModalBottomSheet(onDismissRequest = { filters = false }, containerColor = Color(0xFF1C1C1F)) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("Filter", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Seg("Format", listOf(null to "Alle", "epub" to "EPUB", "pdf" to "PDF"), s.format, vm::setFormat)
                Seg("Länge", listOf(null to "Egal", "kurz" to "Unter 2 Std."), s.length, vm::setLength)
                Seg("Sortieren", listOf("beliebt" to "Beliebt", "titel" to "A–Z", "neu" to "Neu"), s.sort) { vm.setSort(it ?: "beliebt") }
            }
        }
    }
}

@Composable
private fun Label(text: String, modifier: Modifier = Modifier) {
    Text(text.uppercase(), color = HikariTextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
        modifier = modifier.padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 8.dp))
}

@Composable
private fun Chip(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, dot: Color? = null, onClick: () -> Unit) {
    Row(
        Modifier.background(HikariSurface, RoundedCornerShape(20.dp)).clickable(onClick = onClick).padding(horizontal = 13.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, tint = HikariTextMuted, modifier = Modifier.size(16.dp).padding(end = 2.dp))
        if (dot != null) Box(Modifier.size(8.dp).background(dot, CircleShape))
        Text(text, color = HikariText, fontSize = 14.sp, modifier = Modifier.padding(start = if (icon != null || dot != null) 6.dp else 0.dp))
    }
}

@Composable
private fun ActiveChip(text: String, onClear: () -> Unit) {
    Row(
        Modifier.background(BooksAccent, RoundedCornerShape(20.dp)).clickable(onClick = onClear).padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = Color(0xFF1C140B), fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Icon(Icons.Outlined.Close, "Filter entfernen", tint = Color(0xFF1C140B), modifier = Modifier.padding(start = 4.dp).size(14.dp))
    }
}

@Composable
private fun Seg(label: String, options: List<Pair<String?, String>>, current: String?, onPick: (String?) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, color = Color(0xB3FFFFFF), fontSize = 13.sp)
        Row(Modifier.fillMaxWidth().background(Color(0xFF111114), RoundedCornerShape(12.dp)).padding(3.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            for ((v, l) in options) {
                val on = v == current
                Box(Modifier.weight(1f).background(if (on) BooksAccent else Color.Transparent, RoundedCornerShape(9.dp)).clickable { onPick(v) }.padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center) {
                    Text(l, color = if (on) Color(0xFF1C140B) else Color.White, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

/** Den Suchbegriff im Text hervorheben — auch wenn er ohne Umlaute getippt wurde. */
@Composable
private fun highlight(text: String, token: String) = buildAnnotatedString {
    val map = ArrayList<Int>()
    val folded = StringBuilder()
    text.forEachIndexed { i, ch -> for (c in foldForSearch(ch.toString())) { folded.append(c); map.add(i) } }
    val k = if (token.isBlank()) -1 else folded.indexOf(token)
    if (k < 0) { append(text); return@buildAnnotatedString }
    val a = map[k]
    val z = map[(k + token.length - 1).coerceAtMost(map.size - 1)] + 1
    append(text.substring(0, a))
    withStyle(SpanStyle(color = BooksAccent, fontWeight = FontWeight.Bold)) { append(text.substring(a, z)) }
    append(text.substring(z))
}
