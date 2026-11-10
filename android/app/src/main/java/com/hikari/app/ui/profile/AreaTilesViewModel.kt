package com.hikari.app.ui.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.prefs.SettingsStore
import com.hikari.app.domain.profile.AreaTiles
import com.hikari.app.domain.profile.MangaReading
import com.hikari.app.domain.profile.MangaShelfEntry
import com.hikari.app.domain.profile.MangaTile
import com.hikari.app.domain.profile.RussianTile
import com.hikari.app.domain.repo.MangaRepository
import com.hikari.app.domain.repo.MusicRepository
import com.hikari.app.domain.repo.NewsRepository
import com.hikari.app.domain.russian.RuSrs
import com.hikari.app.domain.russian.RussianCourseRepository
import com.hikari.app.domain.russian.RussianProgressStore
import com.hikari.app.domain.russian.RussianStudyTime
import com.hikari.app.domain.whatsapp.WhatsAppRepository
import com.hikari.app.player.MusicPlayerController
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Musik-Kachel: das laufende Lied, sonst das zuletzt gehörte. */
data class MusicTile(
    val title: String,
    val artist: String,
    val coverUrl: String?,
    val playing: Boolean,
)

/** Tagesbericht-Kachel: die erste Schlagzeile, die Zahl der Meldungen und von wann der Bericht ist. */
data class NewsTile(
    val headline: String,
    val count: Int,
    val day: String?,
)

/**
 * Daten für die Bereichs-Kacheln im Profil. Jede Kachel zeigt, was in ihrem Bereich
 * gerade los ist; fehlt etwas (kein Netz, noch nichts gehört), bleibt sie bei ihrem Namen.
 */
@HiltViewModel
class AreaTilesViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val settings: SettingsStore,
    private val newsRepo: NewsRepository,
    private val mangaRepo: MangaRepository,
    private val musicRepo: MusicRepository,
    private val studyTime: RussianStudyTime,
    russianRepo: RussianCourseRepository,
    russianStore: RussianProgressStore,
    player: MusicPlayerController,
    whatsApp: WhatsAppRepository,
) : ViewModel() {

    private val gamePrefs = context.getSharedPreferences("hikari_games", Context.MODE_PRIVATE)

    /** Zählt bei jedem Zurückkehren hoch, damit »heute« und die Serie neu berechnet werden. */
    private val resumed = MutableStateFlow(0)

    val russian: StateFlow<RussianTile?> = combine(russianStore.state, resumed) { p, _ ->
        AreaTiles.russian(
            p = p,
            today = russianStore.today(),
            courseDays = russianRepo.index.days.size,
            podcastDays = russianRepo.podcastIndex.days.size,
            lessonTitle = { russianRepo.index.day(it)?.title },
        )
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val streak: StateFlow<Int> = combine(russianStore.state, resumed) { p, _ ->
        RuSrs.visibleStreak(p, russianStore.today())
    }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _studySeconds = MutableStateFlow(0)
    val studySeconds: StateFlow<Int> = _studySeconds.asStateFlow()

    private val lastHeard = MutableStateFlow<MusicTile?>(null)

    val music: StateFlow<MusicTile?> = combine(player.currentSong, player.isPlaying, lastHeard) { song, playing, last ->
        song?.let { MusicTile(it.title, it.uploader, it.thumbnailUrl.ifEmpty { null }, playing) } ?: last
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _manga = MutableStateFlow<MangaTile?>(null)
    val manga: StateFlow<MangaTile?> = _manga.asStateFlow()

    private val _news = MutableStateFlow<NewsTile?>(null)
    val news: StateFlow<NewsTile?> = _news.asStateFlow()

    val whatsAppDeleted: StateFlow<Int> =
        whatsApp.deletedCount.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _lastGame = MutableStateFlow<String?>(null)
    val lastGame: StateFlow<String?> = _lastGame.asStateFlow()

    init {
        refresh()
    }

    /** Beim Öffnen und bei jedem Zurückkehren auf die Seite. */
    fun refresh() {
        resumed.value += 1
        _lastGame.value = AreaTiles.gameTitle(gamePrefs.getString("last_played_game", null))

        viewModelScope.launch {
            _studySeconds.value = withContext(Dispatchers.IO) { studyTime.todaySeconds() }
        }
        viewModelScope.launch {
            runCatching { musicRepo.getHistory().firstOrNull() }.getOrNull()?.let {
                lastHeard.value = MusicTile(it.title, it.uploader, it.thumbnailUrl.ifEmpty { null }, playing = false)
            }
        }
        viewModelScope.launch {
            runCatching { newsRepo.getBriefing(force = false) }.onSuccess { items ->
                _news.value = items.firstOrNull()?.let {
                    NewsTile(it.title, items.size, AreaTiles.newsDay(it.publishedAt, LocalDate.now()))
                }
            }
        }
        viewModelScope.launch {
            val base = settings.backendUrl.first()
            val cover = { path: String? -> path?.takeIf { it.isNotBlank() }?.let { mangaRepo.coverImageUrl(base, it) } }
            val reading = runCatching { mangaRepo.getContinue() }.getOrNull()
            val shelf = runCatching { mangaRepo.listSeries() }.getOrNull()
            // Beides nicht erreichbar (kein Netz): stehen lassen, was die Kachel schon zeigt.
            if (reading == null && shelf == null) return@launch
            _manga.value = AreaTiles.manga(
                reading = reading.orEmpty().map { MangaReading(it.title, cover(it.coverPath), it.chapterId, it.pageNumber) },
                shelf = shelf.orEmpty().map { MangaShelfEntry(it.title, cover(it.coverPath), it.totalChapters) },
            )
        }
    }
}
