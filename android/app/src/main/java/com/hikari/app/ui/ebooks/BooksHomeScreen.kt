package com.hikari.app.ui.ebooks

import androidx.compose.runtime.remember

import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hikari.app.data.api.dto.EbookCardDto
import com.hikari.app.data.db.LocalEbookEntity
import com.hikari.app.domain.ebooks.EbookFilter
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariSurface
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted

internal val BooksAccent = Color(0xFFD4A373)

/** Stimmungen auf „Entdecken“ — führen zu gefilterten Listen. */
private val MOODS = listOf(
    Triple("Etwas Kurzes", EbookFilter(length = "kurz"), Color(0xFFD4A373)),
    Triple("Spannung", EbookFilter(category = "krimi"), Color(0xFFB04A4A)),
    Triple("Zum Nachdenken", EbookFilter(category = "philosophie"), Color(0xFF6E6BC8)),
    Triple("Märchenhaft", EbookFilter(category = "maerchen"), Color(0xFF2E9E95)),
    Triple("Zum Lachen", EbookFilter(category = "humor"), Color(0xFFC9A227)),
    Triple("Für Kinder", EbookFilter(category = "kinder"), Color(0xFF22A091)),
    Triple("Ferne Länder", EbookFilter(category = "reisen"), Color(0xFF2B95AE)),
    Triple("Liebe", EbookFilter(category = "liebe"), Color(0xFFC0487A)),
)

/** Wohin „Alle“ bei einem Regal führt. */
private fun shelfFilter(key: String): EbookFilter? = when {
    key == "beliebt" -> EbookFilter(sort = "beliebt")
    key == "kurz" -> EbookFilter(length = "kurz")
    key == "pdf" -> EbookFilter(format = "pdf", sort = "neu")
    key == "neu" -> EbookFilter(sort = "neu")
    key.startsWith("kat:") -> EbookFilter(category = key.removePrefix("kat:"))
    else -> null
}

@Composable
fun BooksHomeScreen(
    onBook: (String) -> Unit,
    onList: (EbookFilter) -> Unit,
    onRead: (LocalEbookEntity) -> Unit,
    onSearch: () -> Unit = {},
    vm: BooksViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsState()
    var tab by rememberSaveable { mutableStateOf("entdecken") }
    Column(Modifier.fillMaxSize().background(HikariBg)) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()
                .clip(RoundedCornerShape(14.dp)).background(HikariSurface).clickable(onClick = onSearch)
                .padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Search, null, tint = HikariTextMuted, modifier = Modifier.size(20.dp))
            Text("Titel, Autor oder Thema", color = HikariTextFaint, fontSize = 15.sp, modifier = Modifier.padding(start = 10.dp))
        }
        TabsRow(tab) { tab = it }
        when (tab) {
            "meine" -> MineTab(s, vm, onBook, onRead)
            "kategorien" -> CategoriesTab(s, onList)
            else -> DiscoverTab(s, vm, onBook, onList, onRead)
        }
    }
}

@Composable
private fun TabsRow(tab: String, onTab: (String) -> Unit) {
    Column {
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            for ((k, l) in listOf("entdecken" to "Entdecken", "meine" to "Meine Bücher", "kategorien" to "Kategorien")) {
                val on = k == tab
                Column(Modifier.clickable { onTab(k) }.padding(top = 10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(l, color = if (on) HikariText else HikariTextMuted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                    Box(Modifier.padding(top = 9.dp).height(3.dp).width(if (on) 36.dp else 0.dp).clip(RoundedCornerShape(3.dp)).background(BooksAccent))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x14FFFFFF)))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DiscoverTab(
    s: BooksHomeState,
    vm: BooksViewModel,
    onBook: (String) -> Unit,
    onList: (EbookFilter) -> Unit,
    onRead: (LocalEbookEntity) -> Unit,
) {
    val home = s.home
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
        if (s.offline) item { OfflineBanner { vm.refresh() } }
        s.continueReading.firstOrNull()?.let { c -> item { ContinueCard(c) { onRead(c.book) } } }
        home?.featured?.let { f -> item { FeaturedCard(f, saved = f.id in s.saved, onOpen = { onBook(f.id) }, onSave = { vm.toggleSaved(f.id) }) } }
        item {
            SectionTitle("Wonach ist dir?")
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(MOODS, key = { it.first }) { (label, filter, color) ->
                    Row(
                        Modifier.clip(RoundedCornerShape(20.dp)).background(HikariSurface).clickable { onList(filter) }
                            .padding(horizontal = 14.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
                        Text(label, color = HikariText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(start = 8.dp))
                    }
                }
            }
        }
        if (home == null && s.loading) item {
            Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                Text("Katalog wird geladen …", color = HikariTextMuted, fontSize = 14.sp)
            }
        }
        if (home != null && home.shelves.isEmpty() && home.newest.isEmpty()) item {
            Text(
                if (home.syncing) "Der Mac sammelt gerade die Kataloge — gleich sind die ersten Bücher da." else "Noch keine Bücher im Katalog.",
                color = HikariTextMuted, fontSize = 14.sp, modifier = Modifier.padding(24.dp),
            )
        }
        home?.shelves?.forEachIndexed { i, shelf ->
            item(key = "shelf-${shelf.key}") {
                val filter = shelfFilter(shelf.key)
                ShelfHeader(shelf.title, shelf.subtitle, if (filter != null) ({ onList(filter) }) else null)
                CardRow(shelf.books, onBook, showTime = shelf.key == "kurz")
            }
            if (i == 2 && home.authors.isNotEmpty()) item(key = "authors") {
                ShelfHeader("Autoren entdecken", "Die großen Namen im Katalog", null)
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    items(home.authors.take(16), key = { it.name }) { a ->
                        AuthorBubble(a.name, a.count) { onList(EbookFilter(author = a.name)) }
                    }
                }
            }
        }
        if (home != null && home.shelves.isNotEmpty()) item {
            Text(
                "Jede Nacht um 4:30 holt der Mac neue Bücher. PDF-Bücher tragen überall das Schild „PDF“.",
                color = HikariTextFaint, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
            )
        }
    }
}

@Composable
private fun OfflineBanner(onRetry: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()
            .clip(RoundedCornerShape(12.dp)).background(Color(0x1AD4A373))
            .border(1.dp, Color(0x33D4A373), RoundedCornerShape(12.dp)).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.CloudOff, null, tint = BooksAccent, modifier = Modifier.size(20.dp))
        Text("Katalog gerade nicht erreichbar — deine geladenen Bücher kannst du lesen",
            color = HikariText, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp).weight(1f))
        Text("Erneut", color = BooksAccent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clickable(onClick = onRetry).padding(6.dp))
    }
}

@Composable
private fun ContinueCard(c: ContinueItem, onClick: () -> Unit) {
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF2C251C), HikariSurface)))
            .border(1.dp, Color(0x33D4A373), RoundedCornerShape(16.dp))
            .clickable(onClick = onClick).padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BookCover(c.book.title, c.book.authors, null, c.book.isPdf, null, Modifier.width(72.dp), localPath = c.book.coverPath)
        Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
            Text("WEITERLESEN", color = BooksAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
            Text(shortTitle(c.book.title), color = HikariText, fontSize = 19.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text("${c.book.authors} · ${(c.percent * 100).toInt()} %", color = HikariTextMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            LinearProgressIndicator(
                progress = { c.percent.toFloat() }, color = BooksAccent, trackColor = Color(0x22FFFFFF),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(4.dp).clip(CircleShape),
            )
        }
        Box(Modifier.size(46.dp).clip(CircleShape).background(BooksAccent), contentAlignment = Alignment.Center) {
            Icon(Icons.Filled.PlayArrow, "Weiterlesen", tint = Color(0xFF1C140B))
        }
    }
}

@Composable
private fun FeaturedCard(b: EbookCardDto, saved: Boolean, onOpen: () -> Unit, onSave: () -> Unit) {
    Box(
        Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth().clip(RoundedCornerShape(18.dp))
            .border(1.dp, Color(0x14FFFFFF), RoundedCornerShape(18.dp)),
    ) {
        if (b.coverUrl != null) {
            AsyncImage(
                model = b.coverUrl, contentDescription = null, contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize().blur(36.dp),
            )
            Box(Modifier.matchParentSize().background(Color(0xB3100D0A)))
        } else {
            Box(Modifier.matchParentSize().background(Brush.linearGradient(listOf(categoryColor(b.categories.firstOrNull()), Color(0xFF14110F)))))
        }
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.Bottom) {
            BookCover(b.title, b.authors.joinToString(", "), b.categories.firstOrNull(), b.isPdf, b.coverUrl, Modifier.width(104.dp).clickable(onClick = onOpen))
            Column(Modifier.weight(1f).padding(start = 16.dp)) {
                Text("BUCH DES TAGES", color = BooksAccent, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                Text(shortTitle(b.title), color = Color.White, fontSize = 20.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold,
                    lineHeight = 24.sp, maxLines = 3, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 4.dp),
                    style = androidx.compose.ui.text.TextStyle(hyphens = androidx.compose.ui.text.style.Hyphens.Auto, lineBreak = androidx.compose.ui.text.style.LineBreak.Paragraph))
                Text(listOfNotNull(b.authors.firstOrNull(), b.minutes?.let(::readingTime)).joinToString(" · "),
                    color = Color(0xFFDDD3C7), fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("Ansehen", filled = true, onClick = onOpen)
                    Pill(if (saved) "Gemerkt" else "Merken", filled = false, onClick = onSave)
                }
            }
        }
    }
}

@Composable
internal fun Pill(label: String, filled: Boolean, onClick: () -> Unit) {
    Text(
        label, color = if (filled) Color(0xFF1C140B) else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold,
        modifier = Modifier.clip(RoundedCornerShape(50)).background(if (filled) BooksAccent else Color(0x1FFFFFFF))
            .clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 9.dp),
    )
}

@Composable
internal fun ShelfHeader(title: String, subtitle: String, onAll: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 22.dp, bottom = 10.dp), verticalAlignment = Alignment.Bottom) {
        Column(Modifier.weight(1f)) {
            Text(title, color = HikariText, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            if (subtitle.isNotBlank()) Text(subtitle, color = HikariTextMuted, fontSize = 13.sp)
        }
        if (onAll != null) Text("Alle", color = BooksAccent, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
            modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAll).padding(8.dp))
    }
}

/** Runder Autoren-Knopf mit Anfangsbuchstaben des Nachnamens in einer festen Farbe. */
@Composable
internal fun AuthorBubble(name: String, count: Int, onClick: () -> Unit) {
    Column(Modifier.width(84.dp).clickable(onClick = onClick), horizontalAlignment = Alignment.CenterHorizontally) {
        val c = authorColor(name)
        Box(
            Modifier.size(72.dp).clip(CircleShape).background(Brush.radialGradient(listOf(c.copy(alpha = 0.95f), c.copy(alpha = 0.55f).compositeOverBlack()))),
            contentAlignment = Alignment.Center,
        ) { Text(authorInitial(name), color = Color.White, fontSize = 26.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold) }
        Text(authorShort(name), color = HikariText, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, lineHeight = 14.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp))
        Text("$count Bücher", color = HikariTextMuted, fontSize = 11.sp)
    }
}

private fun Color.compositeOverBlack() = Color(red * alpha, green * alpha, blue * alpha, 1f)
internal fun authorColor(name: String): Color = listOf(
    Color(0xFF8A3B2E), Color(0xFF2F4FA8), Color(0xFF2F6B47), Color(0xFF6D3FC0), Color(0xFF93541F), Color(0xFF1E7F78), Color(0xFFA3335F), Color(0xFF4D6283),
)[(name.fold(7) { h, ch -> h * 31 + ch.code } and 0x7fffffff) % 8]
internal fun authorInitial(name: String) = name.replace(Regex("\\s*\\(.*?\\)"), "").split(" ").lastOrNull { it.firstOrNull()?.isUpperCase() == true }?.take(1) ?: name.take(1)
/** „Johann Wolfgang von Goethe“ → „Goethe“; Initialen-Namen bleiben ganz. */
internal fun authorShort(name: String): String {
    val clean = name.replace(Regex("\\s*\\(.*?\\)"), "").trim()
    val parts = clean.split(" ")
    return if (parts.size <= 2 || parts.dropLast(1).all { it.endsWith(".") }) clean else parts.last()
}
/** Haupttitel ohne Untertitel. */
internal fun shortTitle(t: String): String = t.substringBefore(": ").trim()

@Composable
private fun MineTab(s: BooksHomeState, vm: BooksViewModel, onBook: (String) -> Unit, onRead: (LocalEbookEntity) -> Unit) {
    var filter by rememberSaveable { mutableStateOf("lese") }
    LaunchedEffect(filter) { if (filter == "gemerkt" || filter == "gelesen") vm.loadMine(filter) }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 120.dp)) {
        item {
            Row(
                Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(HikariSurface).padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Fact("${s.continueReading.size}", "angefangen")
                Fact("${s.saved.size}", "gemerkt")
                Fact("${s.local.size}", "auf dem Handy")
                Fact("${s.finishedCount}", "gelesen")
            }
        }
        item {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(listOf("lese" to "Lese ich", "gemerkt" to "Gemerkt", "handy" to "Auf dem Handy", "gelesen" to "Gelesen"), key = { it.first }) { (k, l) ->
                    val on = k == filter
                    Text(l, color = if (on) Color(0xFF1C140B) else HikariText, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(if (on) BooksAccent else HikariSurface)
                            .clickable { filter = k }.padding(horizontal = 14.dp, vertical = 8.dp))
                }
            }
        }
        when (filter) {
            "lese" -> {
                if (s.continueReading.isEmpty()) item { EmptyHint("Noch nichts angefangen", "Was du liest, steht hier mit deinem Fortschritt.") }
                items(s.continueReading, key = { "c-" + it.book.id }) { c ->
                    LocalRow(c.book, "${(c.percent * 100).toInt()} % gelesen", c.percent.toFloat()) { onRead(c.book) }
                }
            }
            "handy" -> {
                if (s.local.isEmpty()) item { EmptyHint("Noch nichts geladen", "Geladene Bücher kannst du auch ohne Netz lesen.") }
                items(s.local, key = { "l-" + it.id }) { b -> LocalRow(b, if (b.isPdf) "PDF" else "EPUB", null) { onBook(b.id) } }
            }
            else -> {
                val list = s.mine[filter]
                if (list == null) item { EmptyHint("Lädt …", "") }
                else if (list.isEmpty()) item {
                    EmptyHint(if (filter == "gemerkt") "Noch nichts gemerkt" else "Noch nichts fertig gelesen",
                        if (filter == "gemerkt") "Mit „Merken“ landet ein Buch hier." else "Fertige Bücher sammeln sich hier.")
                }
                else items(list, key = { "m-" + it.id }) { b -> CardListRow(b) { onBook(b.id) } }
            }
        }
    }
}

@Composable
private fun Fact(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = HikariText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Text(label, color = HikariTextMuted, fontSize = 12.sp)
    }
}

@Composable
private fun EmptyHint(title: String, sub: String) {
    Column(Modifier.fillMaxWidth().padding(36.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = HikariText, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        if (sub.isNotBlank()) Text(sub, color = HikariTextMuted, fontSize = 13.sp, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun LocalRow(b: LocalEbookEntity, meta: String, progress: Float?, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        BookCover(b.title, b.authors, null, b.isPdf, null, Modifier.width(52.dp), localPath = b.coverPath)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(shortTitle(b.title), color = HikariText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(b.authors, color = HikariTextMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(Modifier.padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                if (progress != null) LinearProgressIndicator(
                    progress = { progress }, color = BooksAccent, trackColor = Color(0x22FFFFFF),
                    modifier = Modifier.width(70.dp).height(3.dp).clip(CircleShape),
                )
                Text(meta, color = HikariTextFaint, fontSize = 12.sp, modifier = Modifier.padding(start = if (progress != null) 8.dp else 0.dp))
            }
        }
    }
}

@Composable
internal fun CardListRow(b: EbookCardDto, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        BookCover(b.title, b.authors.joinToString(", "), b.categories.firstOrNull(), b.isPdf, b.coverUrl, Modifier.width(52.dp))
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
            Text(shortTitle(b.title), color = HikariText, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(b.authors.joinToString(", "), color = HikariTextMuted, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(listOfNotNull(if (b.isPdf) "PDF" else "EPUB", b.minutes?.let(::readingTime)).joinToString(" · "), color = HikariTextFaint, fontSize = 12.sp)
        }
    }
}

/** Kategorien als farbige Kacheln, nach Gruppen. */
@Composable
private fun CategoriesTab(s: BooksHomeState, onList: (EbookFilter) -> Unit) {
    val cats = s.home?.categories.orEmpty()
    // Je Kategorie zwei Titelbilder — jedes Buch nur auf einer Kachel, damit nicht überall Faust liegt.
    val coverOf = remember(s.home) {
        val used = HashSet<String>()
        s.home?.shelves.orEmpty().filter { it.key.startsWith("kat:") }.associate { shelf ->
            shelf.key.removePrefix("kat:") to shelf.books.filter { b -> b.coverUrl != null && used.add(b.id) }.take(2)
        }
    }
    val groups = listOf("belletristik" to "Romane & Geschichten", "sachbuch" to "Sachbuch & Wissen", "weitere" to "Weitere")
    LazyVerticalGrid(
        columns = GridCells.Adaptive(160.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        for ((g, label) in groups) {
            val inGroup = cats.filter { it.group == g }.sortedByDescending { it.count }
            if (inGroup.isEmpty()) continue
            item(key = "g-$g", span = { GridItemSpan(maxLineSpan) }) {
                Text(label.uppercase(), color = HikariTextMuted, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp,
                    modifier = Modifier.padding(top = 14.dp, bottom = 2.dp))
            }
            items(inGroup, key = { "c-" + it.id }) { c -> CategoryTile(c.label, c.count, categoryColor(c.id), coverOf[c.id].orEmpty()) { onList(EbookFilter(category = c.id)) } }
        }
        item(key = "pdf", span = { GridItemSpan(maxLineSpan) }) {
            Text("PDF-Bücher tragen überall das Schild „PDF“.", color = HikariTextFaint, fontSize = 12.sp, modifier = Modifier.padding(top = 16.dp))
        }
    }
}

@Composable
private fun CategoryTile(label: String, count: Int, base: Color, covers: List<EbookCardDto>, onClick: () -> Unit) {
    val bright = Color(minOf(1f, base.red * 1.7f + 0.06f), minOf(1f, base.green * 1.7f + 0.06f), minOf(1f, base.blue * 1.7f + 0.06f))
    Box(
        Modifier.height(104.dp).clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(bright, base)))
            .clickable(onClick = onClick),
    ) {
        covers.reversed().forEachIndexed { i, b ->
            BookCover(b.title, "", null, b.isPdf, b.coverUrl,
                Modifier.align(Alignment.BottomEnd).padding(end = if (i == 0) 34.dp else 8.dp).width(if (i == 0) 44.dp else 50.dp)
                    .graphicsLayer { rotationZ = if (i == 0) -5f else 13f; translationY = 22f })
        }
        Column(Modifier.padding(12.dp).fillMaxWidth(0.66f)) {
            Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, lineHeight = 18.sp)
            Spacer(Modifier.weight(1f))
            Text("$count Bücher", color = Color.White.copy(alpha = 0.78f), fontSize = 12.sp)
        }
    }
}

@Composable
internal fun SectionTitle(
    title: String,
    sub: String? = null,
    action: String? = null,
    dot: Color? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 18.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) Box(Modifier.padding(end = 8.dp).size(10.dp).clip(CircleShape).background(dot))
        Text(title, color = HikariText, fontSize = 18.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
        if (sub != null) Text(sub, color = HikariTextFaint, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
        Spacer(Modifier.weight(1f))
        if (action != null && onAction != null) {
            Text(action, color = BooksAccent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onAction).padding(8.dp))
        }
    }
}

@Composable
internal fun CardRow(books: List<EbookCardDto>, onBook: (String) -> Unit, showTime: Boolean = false) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(books, key = { it.id }) { b -> BookCard(b, Modifier.width(112.dp)) { onBook(b.id) } }
    }
}

@Composable
internal fun BookCard(b: EbookCardDto, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick)) {
        BookCover(b.title, b.authors.joinToString(", "), b.categories.firstOrNull(), b.isPdf, b.coverUrl, Modifier.fillMaxWidth())
        CardText(shortTitle(b.title), listOfNotNull(b.authors.firstOrNull(), b.minutes?.let(::readingTime)).joinToString(" · "))
    }
}

@Composable
internal fun CardText(title: String, sub: String) {
    Text(title, color = HikariText, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 2,
        overflow = TextOverflow.Ellipsis, lineHeight = 16.sp, modifier = Modifier.padding(top = 6.dp))
    if (sub.isNotBlank()) Text(sub, color = HikariTextFaint, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
}

@Composable
private fun QuickChip(label: String, onClick: () -> Unit) {
    Text(
        label, color = HikariText, fontSize = 13.sp,
        modifier = Modifier.clip(RoundedCornerShape(20.dp)).border(1.dp, Color(0x33D4A373), RoundedCornerShape(20.dp))
            .clickable(onClick = onClick).padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/** „ca. 45 Min.“ / „ca. 6 Std.“ */
internal fun readingTime(minutes: Int): String = when {
    minutes < 60 -> "ca. $minutes Min."
    else -> "ca. ${(minutes + 30) / 60} Std."
}
