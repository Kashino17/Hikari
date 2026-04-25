package com.hikari.app.ui.manga.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hikari.app.data.api.dto.MangaArcDto
import com.hikari.app.data.api.dto.MangaChapterDto

private val SetSaver: Saver<Set<String>, List<String>> = Saver(
    save = { it.toList() },
    restore = { it.toSet() },
)

@Composable
fun ArcAccordion(
    arcs: List<MangaArcDto>,
    chapters: List<MangaChapterDto>,
    initialExpandedArcId: String?,
    onChapterClick: (chapterId: String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by rememberSaveable(stateSaver = SetSaver) {
        mutableStateOf(
            when {
                initialExpandedArcId != null -> setOf(initialExpandedArcId)
                arcs.isNotEmpty() -> setOf(arcs[0].id)
                else -> emptySet()
            }
        )
    }
    val byArc = remember(arcs, chapters) {
        val map = HashMap<String, MutableList<MangaChapterDto>>()
        for (a in arcs) map[a.id] = mutableListOf()
        for (ch in chapters) {
            ch.arcId?.let { id -> map.getOrPut(id) { mutableListOf() }.add(ch) }
        }
        map
    }
    val orphans = remember(arcs, chapters) {
        val arcIds = arcs.map { it.id }.toSet()
        chapters.filter { it.arcId == null || it.arcId !in arcIds }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        if (arcs.isEmpty()) {
            chapters.forEach { ch ->
                ChapterRow(ch, onClick = { onChapterClick(ch.id) })
                HorizontalDivider(thickness = 0.5.dp, color = Color.White.copy(alpha = 0.05f))
            }
        } else {
            arcs.forEach { arc ->
                val isOpen = arc.id in expanded
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            expanded = if (isOpen) expanded - arc.id else expanded + arc.id
                        }
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (isOpen) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.4f),
                    )
                    Text(
                        text = arc.title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                    )
                    Text(
                        text = "CH ${arc.chapterStart ?: ""}–${arc.chapterEnd ?: ""}",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                if (isOpen) {
                    byArc[arc.id]?.forEach { ch ->
                        ChapterRow(ch, onClick = { onChapterClick(ch.id) })
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, color = Color.White.copy(alpha = 0.05f))
            }
            if (orphans.isNotEmpty()) {
                Text(
                    text = "SONSTIGE",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 10.sp,
                    letterSpacing = 1.5.sp,
                    modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
                )
                orphans.forEach { ch ->
                    ChapterRow(ch, onClick = { onChapterClick(ch.id) })
                }
            }
        }
    }
}
