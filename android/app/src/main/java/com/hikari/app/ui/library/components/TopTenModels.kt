package com.hikari.app.ui.library.components

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

enum class LibraryTab(val label: String, val icon: String) {
    FOR_YOU("Für dich", "🌟"),
    SERIES("Serien", "📺"),
    MOVIES("Filme", "🎬"),
    DISCOVER("Stöbern", "🧭"),
}
