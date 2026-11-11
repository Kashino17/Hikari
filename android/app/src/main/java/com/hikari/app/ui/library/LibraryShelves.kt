package com.hikari.app.ui.library

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.domain.genre.Genre
import com.hikari.app.domain.library.Shelves
import com.hikari.app.ui.library.components.CinemaPosterCard
import com.hikari.app.ui.theme.HikariAmber
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariBorderStrong
import com.hikari.app.ui.theme.HikariSurface
import com.hikari.app.ui.theme.HikariSurfaceHigh
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted

/** Ein Titel als Plakat — Serie oder Film, in einer Reihe oder im Raster. */
internal data class PosterItem(
    val key: String,
    val title: String,
    val subtitle: String = "",
    val thumbnailUrl: String?,
    val posterUrl: String?,
    val tag: String? = null,
    val matchScore: Int? = null,
    val onClick: () -> Unit,
    val onLongClick: (() -> Unit)? = null,
)

/**
 * Eine Reihe der Bibliothek: Überschrift und höchstens zehn Plakate. »Alle« erscheint nur,
 * wenn es mehr Titel gibt, als die Reihe zeigt — und führt dann zur vollständigen Übersicht.
 */
@Composable
internal fun PosterShelf(
    title: String,
    items: List<PosterItem>,
    onShowAll: (List<PosterItem>) -> Unit,
) {
    val shown = remember(items) { Shelves.preview(items) }
    SectionHeader(
        title = title,
        count = items.size,
        onShowAll = if (Shelves.hasMore(items)) ({ onShowAll(items) }) else null,
    )
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(shown, key = { it.key }) { item -> PosterCard(item) }
    }
}

@Composable
private fun PosterCard(item: PosterItem, modifier: Modifier = Modifier, fill: Boolean = false) {
    if (!fill) {
        CinemaPosterCard(
            title = item.title,
            subtitle = item.subtitle,
            thumbnailUrl = item.thumbnailUrl,
            posterUrl = item.posterUrl,
            tag = item.tag,
            matchScore = item.matchScore,
            onClick = item.onClick,
            onLongClick = item.onLongClick,
            modifier = modifier,
        )
        return
    }
    // Im Raster bestimmt die Spalte die Breite; das Plakat bleibt 2:3.
    BoxWithConstraints(modifier.fillMaxWidth()) {
        CinemaPosterCard(
            title = item.title,
            subtitle = item.subtitle,
            thumbnailUrl = item.thumbnailUrl,
            posterUrl = item.posterUrl,
            tag = item.tag,
            matchScore = item.matchScore,
            onClick = item.onClick,
            onLongClick = item.onLongClick,
            width = maxWidth,
            height = maxWidth * 1.5f,
        )
    }
}

/** Plakat-Raster in drei Spalten — die Übersicht hinter »Alle« und hinter einer Kategorie. */
@Composable
internal fun PosterGrid(
    items: List<PosterItem>,
    modifier: Modifier = Modifier,
    emptyText: String = "Hier ist noch nichts.",
) {
    if (items.isEmpty()) {
        Box(modifier.fillMaxSize().padding(horizontal = 32.dp), contentAlignment = Alignment.Center) {
            Text(emptyText, color = HikariTextMuted, fontSize = 14.sp, textAlign = TextAlign.Center)
        }
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 104.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(items, key = { it.key }) { item -> PosterCard(item, fill = true) }
    }
}

/** Vollbild-Übersicht einer Reihe: Titel oben, darunter alle Plakate im Raster. */
@Composable
internal fun PosterGridPage(title: String, items: List<PosterItem>, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Column(Modifier.fillMaxSize().background(HikariBg)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 6.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).clickable(onClick = onBack),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Zurück", tint = HikariText, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(4.dp))
            Text(
                title,
                color = HikariText,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        PosterGrid(items)
    }
}

// ── Kategorien ───────────────────────────────────────────────────────────────

/** Leicht gerundete Ecken für die Knöpfe der Bibliothek — bewusst keine Pille. */
internal val SoftCorner = RoundedCornerShape(9.dp)

/**
 * Kategorie-Umschalter in der Kopfzeile, neben Zufall und Suche: »Kategorien« öffnet die
 * Auswahl. Ist eine gewählt, steht ihr Name dort, und das Kreuz davor hebt die Auswahl wieder auf.
 */
@Composable
internal fun GenreSwitcher(selected: Genre, onOpen: () -> Unit, onClear: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val active = selected != Genre.ALL
        if (active) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(SoftCorner)
                    .border(1.dp, HikariBorderStrong, SoftCorner)
                    .clickable(onClick = onClear),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Outlined.Close, "Kategorie aufheben", tint = HikariText, modifier = Modifier.size(17.dp))
            }
        }
        Row(
            modifier = Modifier
                .weight(1f, fill = false)
                .height(34.dp)
                .clip(SoftCorner)
                .background(if (active) HikariSurfaceHigh else Color.Transparent)
                .border(1.dp, if (active) HikariAmber.copy(alpha = 0.7f) else HikariBorderStrong, SoftCorner)
                .clickable(onClick = onOpen)
                .padding(start = 12.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (active) selected.title else "Kategorien",
                color = HikariText,
                fontSize = 13.sp,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Spacer(Modifier.width(2.dp))
            Icon(Icons.Outlined.KeyboardArrowDown, null, tint = HikariTextMuted, modifier = Modifier.size(20.dp))
        }
    }
}

/**
 * Auswahl der Kategorie über den ganzen Bildschirm: eine ruhige Liste in der Mitte, die
 * gewählte hervorgehoben, unten der Knopf zum Schließen. Es stehen nur Kategorien da,
 * in denen es etwas gibt.
 */
@Composable
internal fun GenrePickerOverlay(
    genres: List<Genre>,
    selected: Genre,
    onSelect: (Genre) -> Unit,
    onDismiss: () -> Unit,
) {
    BackHandler(onBack = onDismiss)
    val options = remember(genres) { listOf(Genre.ALL) + genres }
    val listState = rememberLazyListState()
    LaunchedEffect(Unit) {
        val index = options.indexOf(selected)
        if (index > 3) listState.scrollToItem(index - 3)
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(HikariBg.copy(alpha = 0.97f))
            // Tipps auf die Fläche dürfen nicht zur Bibliothek dahinter durchfallen.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onDismiss),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            // Eine kurze Liste steht in der Mitte, eine lange füllt die Höhe und rollt.
            verticalArrangement = Arrangement.Center,
            contentPadding = PaddingValues(top = 72.dp, bottom = 190.dp),
        ) {
            items(options, key = { it.id }) { genre ->
                val isSelected = genre == selected
                Text(
                    text = if (genre == Genre.ALL) "Alle Kategorien" else genre.title,
                    color = if (isSelected) HikariAmber else HikariTextMuted,
                    fontSize = if (isSelected) 22.sp else 18.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onSelect(genre)
                            onDismiss()
                        }
                        .padding(vertical = 15.dp, horizontal = 24.dp),
                )
            }
        }
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 104.dp)
                .size(58.dp)
                .clip(CircleShape)
                .background(Color.White)
                .clickable(onClick = onDismiss),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Outlined.Close, "Schließen", tint = Color.Black, modifier = Modifier.size(26.dp))
        }
    }
}

// ── Weiterschauen: Menü einer Karte ──────────────────────────────────────────

/** Was sich mit einem Titel in »Weiterschauen« tun lässt. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ContinueMenuSheet(
    title: String,
    onOpenSeries: (() -> Unit)?,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = HikariSurface,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 20.dp)) {
            Text(
                title,
                color = HikariText,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            )
            if (onOpenSeries != null) MenuRow(Icons.Outlined.Tv, "Zur Serie", "Alle Staffeln und Folgen", onOpenSeries)
            MenuRow(
                Icons.Outlined.RemoveCircleOutline,
                "Aus Weiterschauen entfernen",
                "Kommt wieder, sobald du weiterschaust",
                onRemove,
            )
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, hint: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = HikariText, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(label, color = HikariText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            Text(hint, color = HikariTextFaint, fontSize = 12.sp)
        }
    }
}
