package com.hikari.app.ui.manga

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.hikari.app.data.api.dto.MangaPageDto
import com.hikari.app.ui.manga.components.ChapterEndPage
import com.hikari.app.ui.manga.components.MANGA_MAX_ZOOM
import kotlinx.coroutines.flow.distinctUntilChanged
import me.saket.telephoto.zoomable.DoubleClickToZoomListener
import me.saket.telephoto.zoomable.ZoomSpec
import me.saket.telephoto.zoomable.rememberZoomableState
import me.saket.telephoto.zoomable.zoomable

/** Akzent des Manga-Bereichs (wie die Kachel „Lesen“). */
internal val MangaAccent = Color(0xFFA78BFA)

/** Webtoon-Modus: alle Seiten als ein langer Streifen, als Ganzes frei zoombar. */
@Composable
internal fun VerticalStrip(
    pages: List<MangaPageDto>,
    startIndex: Int,
    imageUrl: (MangaPageDto) -> String,
    onPage: (Int) -> Unit,
    onTap: () -> Unit,
    nextChapterId: String?,
    onNextChapter: () -> Unit,
    onBack: () -> Unit,
) {
    val list = rememberLazyListState(initialFirstVisibleItemIndex = startIndex)
    val zoom = rememberZoomableState(zoomSpec = ZoomSpec(maxZoomFactor = MANGA_MAX_ZOOM, preventOverOrUnderZoom = false))
    LaunchedEffect(list) {
        snapshotFlow { list.firstVisibleItemIndex }.distinctUntilChanged().collect { if (it < pages.size) onPage(it) }
    }
    Box(
        Modifier.fillMaxSize().zoomable(
            state = zoom,
            onClick = { onTap() },
            onDoubleClick = DoubleClickToZoomListener.cycle(maxZoomFactor = 2.5f),
        ),
    ) {
        LazyColumn(state = list, modifier = Modifier.fillMaxSize()) {
            itemsIndexed(pages, key = { _, p -> p.id }) { _, p ->
                AsyncImage(
                    model = imageUrl(p),
                    contentDescription = "Seite ${p.pageNumber}",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                Box(Modifier.fillMaxWidth().height(520.dp)) {
                    ChapterEndPage(nextChapterId = nextChapterId, onNextChapter = onNextChapter, onBackToOverview = onBack)
                }
            }
        }
    }
}

/** Einstellungen des Manga-Lesers. */
@Composable
internal fun MangaReaderSettingsSheet(s: MangaReaderSettings, onChange: (MangaReaderSettings) -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 24.dp).navigationBarsPadding(),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("Manga-Leser", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Option("Leserichtung", "Japanische Mangas liest man von rechts nach links. Manhwa und Webtoons als langer Streifen.") {
            Segments(
                listOf(ReadDirection.RTL to "Japanisch", ReadDirection.LTR to "Westlich", ReadDirection.VERTICAL to "Streifen"),
                s.direction,
            ) { onChange(s.copy(direction = it)) }
        }
        if (s.direction != ReadDirection.VERTICAL) {
            Option("Umblättern", animHint(s.anim)) {
                Segments(
                    listOf(PageAnim.BOOK to "Blättern", PageAnim.SLIDE to "Gleiten", PageAnim.FADE to "Ruhig", PageAnim.NONE to "Sofort"),
                    s.anim,
                ) { onChange(s.copy(anim = it)) }
            }
            Toggle(
                "Tippen zum Blättern",
                "Nur die äußeren Ränder blättern, die Mitte öffnet das Menü. Ist das Menü offen, schließt ein Tippen nur das Menü. Aus: nur wischen.",
                s.tapTurn,
            ) { onChange(s.copy(tapTurn = it)) }
            Toggle("Lautstärketasten blättern", "Leiser = weiter, lauter = zurück.", s.volumeKeys) { onChange(s.copy(volumeKeys = it)) }
        }
        Option("Hintergrund", null) {
            Segments(listOf("schwarz" to "Schwarz", "grau" to "Grau", "weiss" to "Papier"), s.background) { onChange(s.copy(background = it)) }
        }
        Option("Blaulichtfilter", com.hikari.app.ui.reader.BlueFilter.hint(s.blueFilter)) {
            Segments(com.hikari.app.ui.reader.BlueFilter.OPTIONS, s.blueFilter) { onChange(s.copy(blueFilter = it)) }
        }
        Text(
            "Zoomen: mit zwei Fingern bis zu ${MANGA_MAX_ZOOM.toInt()}-fach, Doppeltippen zoomt an die Stelle. Gezoomt blättert nichts, bis du wieder ganz herausgezoomt hast.",
            color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp,
        )
    }
}

private fun animHint(a: PageAnim) = when (a) {
    PageAnim.BOOK -> "Die Seite hebt sich wie im Buch und folgt deinem Finger."
    PageAnim.SLIDE -> "Die Seite gleitet zur Seite, die nächste liegt schon darunter."
    PageAnim.FADE -> "Die Seiten blenden ruhig ineinander."
    PageAnim.NONE -> "Ohne Bewegung, sofort die nächste Seite."
}

@Composable
private fun Option(title: String, hint: String?, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(title, color = Color.White.copy(alpha = 0.6f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        content()
        if (hint != null) Text(hint, color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
    }
}

@Composable
private fun <T> Segments(items: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(Color(0xFF111014)).padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        for ((value, label) in items) {
            val on = value == selected
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(9.dp))
                    .background(if (on) MangaAccent else Color.Transparent)
                    .clickable { onSelect(value) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (on) Color(0xFF160F2A) else Color.White.copy(alpha = 0.7f), fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun Toggle(title: String, hint: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { onChange(!on) }) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(2.dp))
            Text(hint, color = Color.White.copy(alpha = 0.45f), fontSize = 12.sp)
        }
        Switch(
            checked = on, onCheckedChange = onChange,
            colors = SwitchDefaults.colors(checkedTrackColor = MangaAccent, checkedThumbColor = Color.White),
        )
    }
}
