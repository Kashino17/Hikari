package com.hikari.app.domain.download

import android.util.Log
import com.hikari.app.data.api.dto.LibraryResponse
import com.hikari.app.data.db.LocalDownloadDao
import com.hikari.app.data.db.LocalDownloadEntity
import com.hikari.app.data.db.LocalMusicDownloadDao
import com.hikari.app.data.net.ConnectivityObserver
import com.hikari.app.domain.repo.FeedRepository
import com.hikari.app.domain.repo.MusicRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Hält die Bilder der Downloads auf dem Gerät aktuell.
 *
 * Zwei Schritte, immer in dieser Reihenfolge:
 *  1. **Nachtragen** — aus der Server-Bibliothek die echten Plakate holen und
 *     an den Downloads vermerken. Die meisten Download-Knöpfe kennen sie
 *     nicht, und ältere Downloads entstanden, bevor es Plakate gab.
 *  2. **Sichern** — jedes Bild, das ein Download braucht, als Datei ablegen
 *     ([OfflineArtworkStore]); was niemand mehr braucht, fliegt raus.
 *
 * Läuft von selbst: beim App-Start, nach jedem neuen Download und immer, wenn
 * die Bibliothek frisch vom Server kommt.
 */
@Singleton
class OfflineArtworkSync @Inject constructor(
    private val downloadDao: LocalDownloadDao,
    private val musicDownloadDao: LocalMusicDownloadDao,
    private val musicRepo: MusicRepository,
    private val feedRepo: FeedRepository,
    private val store: OfflineArtworkStore,
    private val connectivity: ConnectivityObserver,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Mutex()

    /** Bis zu welchem Download die Plakate schon beim Server nachgefragt wurden. */
    @Volatile private var enrichedUpTo = 0L

    /** Beobachtet die Downloads: kommt einer dazu oder geht einer, wird abgeglichen. */
    @OptIn(FlowPreview::class)
    fun start() {
        scope.launch {
            combine(downloadDao.observeIds(), musicDownloadDao.observeIds()) { videos, songs ->
                videos.toSet() to songs.toSet()
            }
                .distinctUntilChanged()
                // Eine ganze Staffel lädt Folge um Folge — nicht nach jeder einzeln abgleichen.
                .debounce(SETTLE_MS)
                .collect { syncSafely() }
        }
    }

    /** Die Bibliothek kam gerade frisch vom Server — gleich mitnutzen statt noch einmal zu fragen. */
    fun onLibraryLoaded(library: LibraryResponse) {
        scope.launch { syncSafely(library) }
    }

    /** Ein gescheiterter Abgleich ist kein Beinbruch — der nächste Anlass holt ihn nach. */
    private suspend fun syncSafely(library: LibraryResponse? = null) {
        runCatching { sync(library) }.onFailure { Log.w(TAG, "Bilder-Abgleich gescheitert", it) }
    }

    suspend fun sync(library: LibraryResponse? = null) = lock.withLock {
        var downloads = downloadDao.all()
        val newest = downloads.maxOfOrNull { it.downloadedAt } ?: 0L

        val source = library
            ?: if (newest > enrichedUpTo && downloads.any { it.missesArtwork() } && connectivity.currentlyOnline()) {
                runCatching { feedRepo.getLibrary() }.getOrNull()
            } else {
                null
            }
        if (source != null) {
            if (enrich(downloads, source)) downloads = downloadDao.all()
            enrichedUpTo = newest
        }

        val wanted = buildList {
            downloads.forEach { addAll(it.artworkUrls()) }
            musicDownloadDao.getAll().forEach { add(it.thumbnailUrl) }
            // Auch Songs, die nur in Favoriten oder Playlists stehen: ihre Cover
            // bilden das Titelbild der Playlist.
            runCatching { musicRepo.getFavorites() }.getOrDefault(emptyList()).forEach { add(it.thumbnailUrl) }
            runCatching { musicRepo.getPlaylists() }.getOrDefault(emptyList())
                .forEach { playlist -> playlist.songs.forEach { add(it.thumbnailUrl) } }
        }
        val stored = store.pin(wanted)
        store.prune(wanted)
        if (stored > 0) Log.i(TAG, "$stored Bilder aufs Gerät gesichert")
    }

    /**
     * Überträgt Plakate und Hintergründe aus der Server-Bibliothek auf die
     * Downloads. Geschrieben wird nur, was sich wirklich ändert — jede
     * Änderung weckt die Beobachter der Download-Tabelle.
     */
    private suspend fun enrich(downloads: List<LocalDownloadEntity>, library: LibraryResponse): Boolean {
        var changed = false
        val seriesById = library.series.associateBy { it.id }
        downloads.filter { it.seriesId != null }.groupBy { it.seriesId!! }.forEach { (seriesId, episodes) ->
            val series = seriesById[seriesId] ?: return@forEach
            val stale = episodes.any { episode ->
                (series.poster_url != null && episode.seriesPosterUrl != series.poster_url) ||
                    (series.backdrop_url != null && episode.seriesBackdropUrl != series.backdrop_url) ||
                    (series.thumbnail_url != null && episode.seriesThumbnailUrl != series.thumbnail_url) ||
                    episode.seriesTitle != series.title
            }
            if (stale) {
                downloadDao.updateSeriesArtwork(
                    seriesId = seriesId,
                    title = series.title,
                    thumbnailUrl = series.thumbnail_url,
                    posterUrl = series.poster_url,
                    backdropUrl = series.backdrop_url,
                )
                changed = true
            }
        }

        val videoById = (library.recentlyAdded + library.continueWatching + library.channelUploads)
            .associateBy { it.id }
        downloads.forEach { download ->
            val video = videoById[download.videoId] ?: return@forEach
            val stale = (video.poster_url != null && download.posterUrl != video.poster_url) ||
                (video.thumbnail_url != null && download.thumbnailUrl != video.thumbnail_url)
            if (stale) {
                downloadDao.updateVideoArtwork(download.videoId, video.poster_url, video.thumbnail_url)
                changed = true
            }
        }
        return changed
    }

    /** Eine Serienfolge ohne Serienplakat oder ein Einzelvideo ohne Plakat — da lohnt die Nachfrage. */
    private fun LocalDownloadEntity.missesArtwork(): Boolean =
        if (seriesId != null) seriesPosterUrl == null else posterUrl == null

    private fun LocalDownloadEntity.artworkUrls(): List<String?> = listOf(
        thumbnailUrl,
        posterUrl,
        seriesThumbnailUrl,
        seriesPosterUrl,
        seriesBackdropUrl,
        channelThumbnailUrl,
    )

    private companion object {
        const val TAG = "HikariArtwork"
        const val SETTLE_MS = 2_500L
    }
}
