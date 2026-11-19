package com.hikari.app.ui.ebooks

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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BooksHomeScreen(
    onBook: (String) -> Unit,
    onList: (EbookFilter) -> Unit,
    onRead: (LocalEbookEntity) -> Unit,
    vm: BooksViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    LazyColumn(
        Modifier.fillMaxSize().background(HikariBg),
        contentPadding = PaddingValues(bottom = 120.dp),
    ) {
        item {
            TextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Titel, Autor, Thema …", color = HikariTextFaint) },
                leadingIcon = { Icon(Icons.Outlined.Search, null, tint = HikariTextMuted) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { if (query.isNotBlank()) onList(EbookFilter(q = query.trim())) }),
                shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = HikariSurface, unfocusedContainerColor = HikariSurface,
                    focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent,
                    focusedTextColor = HikariText, unfocusedTextColor = HikariText, cursorColor = BooksAccent,
                ),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
        if (s.offline) {
            item {
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp)).background(Color(0x1AD4A373))
                        .border(1.dp, Color(0x33D4A373), RoundedCornerShape(12.dp)).padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.CloudOff, null, tint = BooksAccent, modifier = Modifier.size(20.dp))
                    Text(
                        "Katalog gerade nicht erreichbar — deine geladenen Bücher kannst du lesen",
                        color = HikariText, fontSize = 13.sp, modifier = Modifier.padding(start = 10.dp).weight(1f),
                    )
                    Text("Erneut", color = BooksAccent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable { vm.refresh() }.padding(6.dp))
                }
            }
        }
        if (s.continueReading.isNotEmpty()) {
            item { SectionTitle("Weiterlesen") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(s.continueReading, key = { it.book.id }) { c ->
                        Column(Modifier.width(112.dp).clickable { onRead(c.book) }) {
                            BookCover(c.book.title, c.book.authors, null, c.book.isPdf, null, Modifier.fillMaxWidth(), localPath = c.book.coverPath)
                            LinearProgressIndicator(
                                progress = { c.percent.toFloat() }, color = BooksAccent, trackColor = Color(0x22FFFFFF),
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp).height(3.dp).clip(CircleShape),
                            )
                            CardText(c.book.title, "${(c.percent * 100).toInt()} %")
                        }
                    }
                }
            }
        }
        if (s.local.isNotEmpty()) {
            item { SectionTitle("Auf dem Handy", "${s.local.size}") }
            item {
                LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(s.local, key = { it.id }) { b ->
                        Column(Modifier.width(100.dp).clickable { onBook(b.id) }) {
                            BookCover(b.title, b.authors, null, b.isPdf, null, Modifier.fillMaxWidth(), localPath = b.coverPath)
                            CardText(b.title, b.authors)
                        }
                    }
                }
            }
        }
        val home = s.home
        if (home == null && s.loading) {
            item {
                Box(Modifier.fillMaxWidth().padding(40.dp), contentAlignment = Alignment.Center) {
                    Text("Katalog wird geladen …", color = HikariTextMuted, fontSize = 14.sp)
                }
            }
        }
        if (home != null) {
            if (home.newest.isEmpty() && home.rows.isEmpty()) {
                item {
                    Text(
                        if (home.syncing) "Der Mac sammelt gerade die Kataloge — gleich sind die ersten Bücher da."
                        else "Noch keine Bücher im Katalog.",
                        color = HikariTextMuted, fontSize = 14.sp, modifier = Modifier.padding(24.dp),
                    )
                }
            }
            if (home.newest.isNotEmpty()) {
                item { SectionTitle("Neu dazugekommen", action = "Alle") { onList(EbookFilter(sort = "neu")) } }
                item { CardRow(home.newest, onBook) }
            }
            item {
                Row(Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    QuickChip("Merkliste") { onList(EbookFilter(state = "gemerkt")) }
                    QuickChip("Gelesen") { onList(EbookFilter(state = "gelesen")) }
                    QuickChip("Nur PDF") { onList(EbookFilter(format = "pdf")) }
                }
            }
            for (row in home.rows) {
                item(key = "row-${row.category}") {
                    SectionTitle(row.label, action = "Alle", dot = categoryColor(row.category)) {
                        onList(EbookFilter(category = row.category))
                    }
                }
                item(key = "books-${row.category}") { CardRow(row.books, onBook) }
            }
            if (home.categories.isNotEmpty()) {
                item { SectionTitle("Alle Kategorien") }
                item {
                    FlowRow(
                        Modifier.padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        for (c in home.categories) {
                            Row(
                                Modifier.clip(RoundedCornerShape(20.dp)).background(HikariSurface)
                                    .clickable { onList(EbookFilter(category = c.id)) }
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(8.dp).clip(CircleShape).background(categoryColor(c.id).copy(alpha = 1f)))
                                Text(c.label, color = HikariText, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp))
                                Text("${c.count}", color = HikariTextFaint, fontSize = 12.sp, modifier = Modifier.padding(start = 6.dp))
                            }
                        }
                    }
                }
            }
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
internal fun CardRow(books: List<EbookCardDto>, onBook: (String) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        items(books, key = { it.id }) { b -> BookCard(b, Modifier.width(112.dp)) { onBook(b.id) } }
    }
}

@Composable
internal fun BookCard(b: EbookCardDto, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Column(modifier.clickable(onClick = onClick)) {
        BookCover(b.title, b.authors.joinToString(", "), b.categories.firstOrNull(), b.isPdf, b.coverUrl, Modifier.fillMaxWidth())
        CardText(b.title, listOfNotNull(b.authors.firstOrNull(), b.minutes?.let(::readingTime)).joinToString(" · "))
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
