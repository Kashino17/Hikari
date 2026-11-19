package com.hikari.app.ui.ebooks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hikari.app.domain.ebooks.EbookFilter
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariSurface
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted

/** Auswahl für einen Filter-Chip: Wert (null = alle) und Bezeichnung. */
private data class Choice(val value: String?, val label: String)

private val CATEGORY_CHOICES = listOf(
    Choice(null, "Alle Kategorien"),
    Choice("roman", "Roman"), Choice("krimi", "Krimi & Mystery"), Choice("abenteuer", "Abenteuer"),
    Choice("fantastik", "Fantastik & Grusel"), Choice("historisch", "Historischer Roman"), Choice("liebe", "Liebe"),
    Choice("humor", "Humor"), Choice("erzaehlungen", "Erzählungen"), Choice("maerchen", "Märchen & Sagen"),
    Choice("drama", "Drama"), Choice("lyrik", "Lyrik"), Choice("kinder", "Kinder & Jugend"),
    Choice("geschichte", "Geschichte"), Choice("philosophie", "Philosophie"), Choice("wissenschaft", "Wissenschaft & Natur"),
    Choice("politik", "Politik & Gesellschaft"), Choice("reisen", "Reisen"), Choice("biografie", "Biografien & Briefe"),
    Choice("religion", "Religion"), Choice("kunst", "Kunst & Musik"), Choice("psychologie", "Psychologie"),
    Choice("weitere", "Weitere"),
)
private val FORMAT_CHOICES = listOf(Choice(null, "Alle Formate"), Choice("epub", "EPUB"), Choice("pdf", "PDF"))
private val LENGTH_CHOICES = listOf(
    Choice(null, "Jede Länge"), Choice("kurz", "Kurz (< 2 Std.)"), Choice("mittel", "Mittel (2–7 Std.)"), Choice("lang", "Lang (> 7 Std.)"),
)
private val SOURCE_CHOICES = listOf(
    Choice(null, "Alle Quellen"), Choice("gutenberg", "Gutenberg"), Choice("wikisource", "Wikisource"),
    Choice("bpb", "bpb"), Choice("openaccess", "Open Access"),
)
private val SORT_CHOICES = listOf(
    Choice("beliebt", "Beliebt"), Choice("neu", "Neu dazu"), Choice("titel", "Titel A–Z"), Choice("autor", "Autor A–Z"), Choice("jahr", "Jahr"),
)
private val STATE_CHOICES = listOf(Choice(null, "Alle Bücher"), Choice("gemerkt", "Merkliste"), Choice("gelesen", "Gelesen"))

@Composable
fun BookListScreen(
    onBack: () -> Unit,
    onBook: (String) -> Unit,
    vm: BookListScreenViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsState()
    val f = s.filter
    var query by rememberSaveable { mutableStateOf(f.q ?: "") }
    Column(Modifier.fillMaxSize().background(HikariBg).statusBarsPadding()) {
        Row(Modifier.fillMaxWidth().padding(end = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Zurück", tint = HikariText) }
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Suchen …", color = HikariTextFaint) },
                leadingIcon = { Icon(Icons.Outlined.Search, null, tint = HikariTextMuted) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { vm.setFilter(f.copy(q = query.trim().ifBlank { null })) }),
                shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = HikariSurface, unfocusedContainerColor = HikariSurface,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = HikariText, unfocusedTextColor = HikariText, cursorColor = BooksAccent,
                ),
                modifier = Modifier.weight(1f),
            )
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { FilterChip(CATEGORY_CHOICES, f.category) { vm.setFilter(f.copy(category = it)) } }
            item { FilterChip(FORMAT_CHOICES, f.format) { vm.setFilter(f.copy(format = it)) } }
            item { FilterChip(LENGTH_CHOICES, f.length) { vm.setFilter(f.copy(length = it)) } }
            item { FilterChip(STATE_CHOICES, f.state) { vm.setFilter(f.copy(state = it)) } }
            item { FilterChip(SOURCE_CHOICES, f.source) { vm.setFilter(f.copy(source = it)) } }
            item { FilterChip(SORT_CHOICES, f.sort, alwaysActive = true) { vm.setFilter(f.copy(sort = it ?: "beliebt")) } }
        }
        Text(
            when {
                s.loading && s.books.isEmpty() -> "Suche …"
                s.error != null && s.books.isEmpty() -> "Katalog nicht erreichbar"
                else -> "${s.total} ${if (s.total == 1) "Buch" else "Bücher"}"
            },
            color = HikariTextMuted, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp),
        )
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val columns = if (maxWidth >= 600.dp) 4 else 2
            val grid = rememberLazyGridState()
            val nearEnd by remember {
                derivedStateOf {
                    val last = grid.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    last >= grid.layoutInfo.totalItemsCount - 6
                }
            }
            LaunchedEffect(nearEnd, s.books.size) { if (nearEnd) vm.loadMore() }
            LazyVerticalGrid(
                columns = GridCells.Fixed(columns),
                state = grid,
                contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 120.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                items(s.books, key = { it.id }) { b -> BookCard(b) { onBook(b.id) } }
                if (s.loading && s.books.isNotEmpty()) {
                    item(span = { GridItemSpan(columns) }) {
                        Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            Text("Mehr wird geladen …", color = HikariTextFaint, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterChip(choices: List<Choice>, current: String?, alwaysActive: Boolean = false, onPick: (String?) -> Unit) {
    var open by remember { mutableStateOf(false) }
    val active = alwaysActive || current != null
    val label = choices.firstOrNull { it.value == current }?.label ?: choices.first().label
    Box {
        Text(
            label,
            color = if (active) Color(0xFF2A1D0E) else HikariText,
            fontSize = 13.sp,
            fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.clip(RoundedCornerShape(20.dp))
                .background(if (active) BooksAccent else HikariSurface)
                .border(1.dp, if (active) BooksAccent else Color(0x14FFFFFF), RoundedCornerShape(20.dp))
                .clickable { open = true }
                .padding(horizontal = 14.dp, vertical = 8.dp),
        )
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            for (c in choices) {
                DropdownMenuItem(
                    text = { Text(c.label, fontWeight = if (c.value == current) FontWeight.Bold else FontWeight.Normal) },
                    onClick = { open = false; onPick(c.value) },
                )
            }
        }
    }
}
