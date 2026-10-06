package com.hikari.app.ui.library.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.ui.graphics.vector.ImageVector

data class TopTenItem(
    val rank: Int, // 1 .. 10
    val id: String,
    val title: String,
    val subtitle: String = "",
    val thumbnailUrl: String? = null,
    val isSeries: Boolean = false,
    val seriesId: String? = null,
    val channelTitle: String = "",
    val durationSeconds: Int = 0,
    val matchScore: Int? = null,
    val isMovie: Boolean = false,
    val progressSeconds: Float? = null,
)

data class TopTenCharts(
    val topSeries: List<TopTenItem> = emptyList(),
    val topMovies: List<TopTenItem> = emptyList(),
)

enum class LibraryTab(val label: String, val icon: ImageVector) {
    FOR_YOU("Für dich", Icons.Outlined.AutoAwesome),
    SERIES("Serien", Icons.Outlined.Tv),
    MOVIES("Filme", Icons.Outlined.Movie),
    DISCOVER("Stöbern", Icons.Outlined.Explore),
}

