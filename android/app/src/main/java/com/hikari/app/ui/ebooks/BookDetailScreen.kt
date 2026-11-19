package com.hikari.app.ui.ebooks

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.CheckCircleOutline
import androidx.compose.material.icons.outlined.CloudDownload
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.hikari.app.data.db.LocalEbookEntity
import com.hikari.app.ui.theme.HikariBg
import com.hikari.app.ui.theme.HikariSurface
import com.hikari.app.ui.theme.HikariText
import com.hikari.app.ui.theme.HikariTextFaint
import com.hikari.app.ui.theme.HikariTextMuted

private val CATEGORY_LABEL = mapOf(
    "roman" to "Roman", "krimi" to "Krimi & Mystery", "abenteuer" to "Abenteuer", "fantastik" to "Fantastik & Grusel",
    "historisch" to "Historischer Roman", "liebe" to "Liebe", "humor" to "Humor", "erzaehlungen" to "Erzählungen",
    "maerchen" to "Märchen & Sagen", "drama" to "Drama", "lyrik" to "Lyrik", "kinder" to "Kinder & Jugend",
    "geschichte" to "Geschichte", "philosophie" to "Philosophie", "wissenschaft" to "Wissenschaft & Natur",
    "politik" to "Politik & Gesellschaft", "reisen" to "Reisen", "biografie" to "Biografien & Briefe",
    "religion" to "Religion", "kunst" to "Kunst & Musik", "psychologie" to "Psychologie", "weitere" to "Weitere",
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BookDetailScreen(
    onBack: () -> Unit,
    onRead: (LocalEbookEntity) -> Unit,
    vm: BookDetailViewModel = hiltViewModel(),
) {
    val s by vm.state.collectAsState()
    val d = s.detail
    val local = s.local
    val title = d?.title ?: local?.title ?: ""
    val authors = d?.authors?.joinToString(", ") ?: local?.authors ?: ""
    val isPdf = d?.isPdf ?: local?.isPdf ?: false
    val category = d?.categories?.firstOrNull()

    Column(Modifier.fillMaxSize().background(HikariBg).statusBarsPadding()) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Zurück", tint = HikariText) }
        if (d == null && local == null) {
            Text(
                if (s.error != null) "Dieses Buch ist gerade nicht erreichbar." else "Lädt …",
                color = HikariTextMuted, modifier = Modifier.padding(24.dp),
            )
            return@Column
        }
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val wide = maxWidth >= 600.dp
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                val cover = @Composable { m: Modifier ->
                    BookCover(title, authors, category, isPdf, d?.coverUrl, m, localPath = local?.coverPath)
                }
                if (wide) {
                    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                        cover(Modifier.width(200.dp))
                        Column(Modifier.weight(1f)) { Header(title, authors, d?.year, d?.minutes, isPdf) }
                    }
                } else {
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { cover(Modifier.width(170.dp)) }
                    Spacer(Modifier.height(18.dp))
                    Header(title, authors, d?.year, d?.minutes, isPdf)
                }
                if (isPdf) {
                    Row(
                        Modifier.padding(top = 14.dp).fillMaxWidth().clip(RoundedCornerShape(12.dp))
                            .background(Color(0x14FFFFFF)).border(1.dp, Color(0x33FFFFFF), RoundedCornerShape(12.dp)).padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Outlined.PictureAsPdf, null, tint = HikariText, modifier = Modifier.size(20.dp))
                        Text("PDF — feste Seiten, kein Umbruch wie im Kindle-Leser", color = HikariText, fontSize = 13.sp,
                            modifier = Modifier.padding(start = 10.dp))
                    }
                }
                // Hauptknopf: Laden → Fortschritt → Lesen
                Spacer(Modifier.height(18.dp))
                val progress = s.progress
                when {
                    local != null -> MainButton("Lesen", null) { onRead(local) }
                    progress != null -> Column(Modifier.fillMaxWidth()) {
                        Text("Wird geladen … ${(progress * 100).toInt()} %", color = HikariTextMuted, fontSize = 13.sp)
                        LinearProgressIndicator(
                            progress = { progress }, color = BooksAccent, trackColor = Color(0x22FFFFFF),
                            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(6.dp).clip(RoundedCornerShape(3.dp)),
                        )
                    }
                    else -> MainButton("Aufs Handy laden", Icons.Outlined.CloudDownload) { vm.download() }
                }
                s.downloadError?.let { Text(it, color = Color(0xFFF87171), fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp)) }
                Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    val saved = s.bookState.saved?.value == true
                    val finished = s.bookState.finished?.value == true
                    SmallAction(if (saved) "Gemerkt" else "Merken", if (saved) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, saved) { vm.toggleSaved() }
                    SmallAction("Gelesen", if (finished) Icons.Filled.CheckCircle else Icons.Outlined.CheckCircleOutline, finished) { vm.toggleFinished() }
                    if (local != null) SmallAction("Vom Handy", Icons.Outlined.DeleteOutline, false) { vm.removeDownload() }
                }
                if (d != null) {
                    if (d.categories.isNotEmpty()) {
                        FlowRow(Modifier.padding(top = 18.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            for (c in d.categories) {
                                Text(CATEGORY_LABEL[c] ?: c, color = HikariText, fontSize = 12.sp,
                                    modifier = Modifier.clip(RoundedCornerShape(20.dp)).background(categoryColor(c).copy(alpha = 0.45f))
                                        .padding(horizontal = 12.dp, vertical = 6.dp))
                            }
                        }
                    }
                    d.description?.let {
                        Text(it, color = HikariTextMuted, fontSize = 14.sp, lineHeight = 21.sp, modifier = Modifier.padding(top = 18.dp))
                    }
                    Text("Quelle: ${d.sourceName}", color = HikariTextFaint, fontSize = 12.sp, modifier = Modifier.padding(top = 18.dp, bottom = 120.dp))
                }
            }
        }
    }
}

@Composable
private fun Header(title: String, authors: String, year: Int?, minutes: Int?, isPdf: Boolean) {
    Text(title, color = HikariText, fontSize = 24.sp, lineHeight = 29.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif)
    if (authors.isNotBlank()) Text(authors, color = HikariTextMuted, fontSize = 15.sp, modifier = Modifier.padding(top = 6.dp))
    val meta = listOfNotNull(
        if (isPdf) "PDF" else "EPUB",
        year?.toString(),
        minutes?.let { "${readingTime(it)} Lesezeit" },
    )
    Text(meta.joinToString("  ·  "), color = HikariTextFaint, fontSize = 13.sp, modifier = Modifier.padding(top = 8.dp))
}

@Composable
private fun MainButton(label: String, icon: ImageVector?, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp)).background(BooksAccent).clickable(onClick = onClick).padding(vertical = 14.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Icon(icon, null, tint = Color(0xFF2A1D0E), modifier = Modifier.size(20.dp).padding(end = 2.dp))
        Text(label, color = Color(0xFF2A1D0E), fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 6.dp))
    }
}

@Composable
private fun SmallAction(label: String, icon: ImageVector, active: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.clip(RoundedCornerShape(12.dp)).background(if (active) Color(0x22D4A373) else HikariSurface)
            .clickable(onClick = onClick).padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = if (active) BooksAccent else HikariTextMuted, modifier = Modifier.size(18.dp))
        Text(label, color = HikariText, fontSize = 13.sp, modifier = Modifier.padding(start = 6.dp))
    }
}
