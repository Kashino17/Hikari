package com.hikari.app.ui.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.prefs.SettingsStore
import com.hikari.app.domain.profile.AreaTiles
import com.hikari.app.domain.profile.MangaReading
import com.hikari.app.domain.profile.MangaShelfEntry
import com.hikari.app.domain.profile.MangaTile
import com.hikari.app.domain.profile.BookReading
import com.hikari.app.domain.profile.RussianTile
import com.hikari.app.domain.repo.MangaRepository
import com.hikari.app.domain.repo.MusicRepository
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
import kotlinx.coroutines.flow.map
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

/** Fitness-Kachel: heutiges Training (oder Ruhetag), Serie, übrige Kalorien. */
data class FitnessTile(
    val title: String,
    val detail: String,
    val done: Boolean,
    val streak: Int,
    /** Bild der ersten Hauptübung des Tages (weißer Hintergrund). */
    val imageUrl: String? = null,
)

/** Achtsamkeit-Kachel: Minuten heute, Stand des Yoga-Pfads, Serie. */
data class MindTile(
    val title: String,
    val detail: String,
    val streak: Int,
    val done: Boolean,
)

/** Eine Meldung im Tagesbericht-Wechselbild. */
data class NewsSlide(
    val headline: String,
    val imageUrl: String?,
    val source: String,
)

/** Tagesbericht-Kachel: die erste Schlagzeile, die Zahl der Meldungen und von wann der Bericht ist. */
data class NewsTile(
    val headline: String,
    val count: Int,
    val day: String?,
    /** Die ersten Meldungen mit Foto — die Kachel blendet sie nacheinander ein. */
    val slides: List<NewsSlide> = emptyList(),
)

/** Spiele-Kachel: das zuletzt gespielte Spiel mit seinem Stand. */
data class GamesTile(
    val id: String,
    val title: String,
    /** Erspielter Stand (»Rekord 26.348«, »Level 12«) oder worum es geht. */
    val stat: String,
    val earned: Boolean,
    /** Zwei weitere Spiele für den Symbol-Fächer hinter dem Hauptsymbol. */
    val others: List<String>,
    /** Schon einmal gespielt — sonst zeigt die Leiste »Spiele« statt eines Spielnamens. */
    val played: Boolean = true,
)

/** Browser-Kachel: das zuletzt über den Browser gefundene Video. */
data class BrowserTile(
    val title: String,
    val thumbnailUrl: String?,
    val count: Int,
)

/**
 * Daten für die Bereichs-Kacheln im Profil. Jede Kachel zeigt, was in ihrem Bereich
 * gerade los ist; fehlt etwas (kein Netz, noch nichts gehört), bleibt sie bei ihrem Namen.
 */
@HiltViewModel
class AreaTilesViewModel @Inject constructor(
    @ApplicationContext context: Context,
    private val settings: SettingsStore,
    private val mangaRepo: MangaRepository,
    private val ebookRepo: com.hikari.app.domain.ebooks.EbookRepository,
    private val musicRepo: MusicRepository,
    private val studyTime: RussianStudyTime,
    russianRepo: RussianCourseRepository,
    russianStore: RussianProgressStore,
    player: MusicPlayerController,
    whatsApp: WhatsAppRepository,
    private val okHttp: okhttp3.OkHttpClient,
    private val channelsRepo: com.hikari.app.domain.repo.ChannelsRepository,
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

    /** Vom Wächter gerettete Nachrichten (gelöscht oder bearbeitet). */
    val whatsAppRescued: StateFlow<Int> =
        whatsApp.allMessages.map { it.size }.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _games = MutableStateFlow<GamesTile?>(null)
    val games: StateFlow<GamesTile?> = _games.asStateFlow()

    private val _browser = MutableStateFlow<BrowserTile?>(null)
    val browser: StateFlow<BrowserTile?> = _browser.asStateFlow()

    private val _fitness = MutableStateFlow<FitnessTile?>(null)
    val fitness: StateFlow<FitnessTile?> = _fitness.asStateFlow()
    private val _mind = MutableStateFlow<MindTile?>(null)
    val mind: StateFlow<MindTile?> = _mind.asStateFlow()

    init {
        refresh()
    }

    /** Beim Öffnen und bei jedem Zurückkehren auf die Seite. */
    fun refresh() {
        resumed.value += 1
        _games.value = run {
            val catalog = com.hikari.app.ui.games.GameCatalog
            val last = gamePrefs.getString("last_played_game", null)?.let { id -> catalog.firstOrNull { it.id == id } }
            val main = last ?: catalog.first()
            val stat = main.stat { key -> gamePrefs.getInt(key, 0) }
            GamesTile(
                id = main.id,
                title = main.title,
                stat = stat.spelledOut,
                earned = last != null && stat.earned,
                others = catalog.filter { it.id != main.id }.take(2).map { it.id },
                played = last != null,
            )
        }
        viewModelScope.launch {
            runCatching { channelsRepo.listVideos("manual") }.onSuccess { vids ->
                val base = settings.backendUrl.first().trimEnd('/')
                val latest = vids.maxByOrNull { it.discoveredAt ?: it.addedToFeedAt ?: 0L }
                _browser.value = latest?.let { v ->
                    val thumb = v.thumbnailUrl?.takeIf { it.isNotBlank() }?.let { if (it.startsWith("http")) it else "$base$it" }
                    BrowserTile(AreaTiles.browserTitle(v.title), thumb, vids.size)
                }
            }
        }

        viewModelScope.launch {
            _studySeconds.value = withContext(Dispatchers.IO) { studyTime.todaySeconds() }
        }
        viewModelScope.launch {
            _fitness.value = withContext(Dispatchers.IO) {
                runCatching {
                    okHttp.newCall(okhttp3.Request.Builder().url("http://hikari.invalid/fitness/tile").build()).execute().use { res ->
                        if (!res.isSuccessful) return@use null
                        val o = org.json.JSONObject(res.body?.string().orEmpty())
                        val base = settings.backendUrl.first().trimEnd('/')
                        val img = o.optString("image").takeIf { it.isNotBlank() && it != "null" }?.let { "$base$it" }
                        FitnessTile(o.optString("title"), o.optString("detail"), o.optBoolean("done"), o.optInt("streak"), img)
                    }
                }.getOrNull()
            } ?: _fitness.value
        }
        viewModelScope.launch {
            _mind.value = withContext(Dispatchers.IO) {
                runCatching {
                    okHttp.newCall(okhttp3.Request.Builder().url("http://hikari.invalid/fitness/mind/tile").build()).execute().use { res ->
                        if (!res.isSuccessful) return@use null
                        val o = org.json.JSONObject(res.body?.string().orEmpty())
                        MindTile(o.optString("title"), o.optString("detail"), o.optInt("streak"), o.optBoolean("done"))
                    }
                }.getOrNull()
            } ?: _mind.value
        }
        viewModelScope.launch {
            runCatching { musicRepo.getHistory().firstOrNull() }.getOrNull()?.let {
                lastHeard.value = MusicTile(it.title, it.uploader, it.thumbnailUrl.ifEmpty { null }, playing = false)
            }
        }
        viewModelScope.launch {
            // Gleiche Meldungen wie im Tagesbericht (Discover): nur frei lesbare Volltexte zu Kadirs Stichwörtern.
            val tile = withContext(Dispatchers.IO) {
                runCatching {
                    okHttp.newCall(okhttp3.Request.Builder().url("http://hikari.invalid/news/discover").build()).execute().use { res ->
                        if (!res.isSuccessful) return@use null
                        val items = org.json.JSONObject(res.body?.string().orEmpty()).optJSONArray("items") ?: return@use null
                        if (items.length() == 0) return@use null
                        val base = settings.backendUrl.first().trimEnd('/')
                        val slides = (0 until minOf(6, items.length())).map { i ->
                            val o = items.getJSONObject(i)
                            val image = o.optString("image").takeIf { it.isNotBlank() && it != "null" }
                                ?.let { "$base/news/img?u=${android.net.Uri.encode(it)}" }
                            NewsSlide(o.optString("title"), image, o.optString("site"))
                        }
                        val newest = (0 until items.length()).maxOf { items.getJSONObject(it).optLong("at") }
                        NewsTile(
                            headline = slides.first().headline,
                            count = items.length(),
                            day = AreaTiles.newsDay(java.time.Instant.ofEpochMilli(newest).toString(), LocalDate.now()),
                            slides = slides,
                        )
                    }
                }.getOrNull()
            }
            if (tile != null) _news.value = tile
        }
        viewModelScope.launch {
            val base = settings.backendUrl.first()
            val cover = { path: String? -> path?.takeIf { it.isNotBlank() }?.let { mangaRepo.coverImageUrl(base, it) } }
            val reading = runCatching { mangaRepo.getContinue() }.getOrNull()
            val shelf = runCatching { mangaRepo.listSeries() }.getOrNull()
            // Angefangene Bücher liegen auf dem Handy — die gehen auch ohne Netz.
            val books = runCatching {
                val states = ebookRepo.allStates().first()
                ebookRepo.local().first().mapNotNull { b ->
                    val st = states[b.id] ?: return@mapNotNull null
                    val pct = st.position?.value?.percent ?: return@mapNotNull null
                    val at = st.lastOpenedAt ?: return@mapNotNull null
                    // Titelbild auf dem Handy nur, wenn die Datei wirklich da ist — sonst vom Mac.
                    val cover = b.coverPath?.takeIf { java.io.File(it).length() > 100 }
                        ?: "/api/ebooks/${android.net.Uri.encode(b.id)}/cover?v=3"
                    if (st.finished?.value == true) null else BookReading(com.hikari.app.ui.ebooks.shortTitle(b.title), cover, pct, at)
                }
            }.getOrDefault(emptyList())
            // Nichts erreichbar und kein Buch: stehen lassen, was die Kachel schon zeigt.
            if (reading == null && shelf == null && books.isEmpty()) return@launch
            _manga.value = AreaTiles.manga(
                reading = reading.orEmpty().map { MangaReading(it.title, cover(it.coverPath), it.chapterId, it.pageNumber, it.updatedAt) },
                shelf = shelf.orEmpty().map { MangaShelfEntry(it.title, cover(it.coverPath), it.totalChapters) },
                books = books,
            )
        }
    }
}
