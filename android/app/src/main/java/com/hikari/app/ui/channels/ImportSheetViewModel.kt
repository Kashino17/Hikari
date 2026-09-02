package com.hikari.app.ui.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.api.dto.AnalyzeResponse
import com.hikari.app.data.api.dto.BulkImportItem
import com.hikari.app.data.api.dto.ImportItemMetadata
import com.hikari.app.data.api.dto.SeriesItemDto
import com.hikari.app.data.api.dto.SniffedImportItem
import com.hikari.app.domain.browser.EpisodeLinkFilter
import com.hikari.app.domain.browser.HeadlessResult
import com.hikari.app.domain.browser.HeadlessSniffer
import com.hikari.app.domain.browser.PageMetaParser
import com.hikari.app.domain.repo.ChannelsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Ein im Hintergrund mitgelesener Stream einer Seite mit eingebettetem Player.
 * Solche Karten gehen beim Absenden an `/videos/import/sniffed`, nicht an den
 * yt-dlp-Import — der Hoster wurde ja gerade nicht verstanden.
 */
data class SniffedSource(
    val mediaUrl: String,
    val referer: String?,
    val cookie: String?,
    val userAgent: String?,
    val description: String?,
)

sealed interface ImportCardState {
    val url: String

    data class Loading(
        override val url: String,
        /** Was gerade passiert, wenn es mehr als "Analysiere…" ist. */
        val hint: String? = null,
    ) : ImportCardState

    data class Ready(
        override val url: String,
        val title: String,
        val thumbnailUrl: String? = null,
        val seriesId: String? = null,
        val seriesTitle: String? = null,
        val season: Int? = null,
        val episode: Int? = null,
        val dubLanguage: String? = null,
        val subLanguage: String? = null,
        val isMovie: Boolean = false,
        val expanded: Boolean = false,
        val sniffed: SniffedSource? = null,
    ) : ImportCardState

    data class Failed(
        override val url: String,
        val error: String,
    ) : ImportCardState
}

data class SharedDefaults(
    val seriesId: String? = null,
    val seriesTitle: String? = null,
    val season: Int? = null,
    val dubLanguage: String? = null,
    val subLanguage: String? = null,
)

data class ImportSheetUiState(
    val rawInput: String = "",
    val cards: List<ImportCardState> = emptyList(),
    val defaults: SharedDefaults = SharedDefaults(),
    val allSeries: List<SeriesItemDto> = emptyList(),
    val allDubLanguages: List<String> = emptyList(),
    val allSubLanguages: List<String> = emptyList(),
    val submitting: Boolean = false,
    val submitError: String? = null,
)

/**
 * Hosts, die yt-dlp zuverlässig versteht. Für sie lohnt der unsichtbare
 * Seitenbesuch nicht — er würde nur eine WebView auf youtube.com losschicken.
 */
internal object DirectHosts {
    private val HOSTS = listOf(
        "youtube.com", "youtu.be", "vimeo.com", "twitter.com", "x.com",
        "instagram.com", "tiktok.com", "reddit.com", "twitch.tv",
        "dailymotion.com", "soundcloud.com", "facebook.com", "streamable.com",
        "bilibili.com", "nicovideo.jp",
    )

    fun isWellSupported(url: String): Boolean {
        val host = runCatching { java.net.URI(url).host?.lowercase() }.getOrNull() ?: return false
        return HOSTS.any { host == it || host.endsWith(".$it") }
    }
}

@HiltViewModel
class ImportSheetViewModel @Inject constructor(
    private val repo: ChannelsRepository,
    private val sniffer: HeadlessSniffer,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportSheetUiState())
    val uiState: StateFlow<ImportSheetUiState> = _uiState.asStateFlow()

    private var inputDebounceJob: Job? = null

    init {
        viewModelScope.launch {
            runCatching { repo.listSeries() }
                .onSuccess { fetched -> _uiState.update { it.copy(allSeries = fetched) } }
        }
        viewModelScope.launch {
            runCatching { repo.listLanguages() }
                .onSuccess { langs ->
                    _uiState.update {
                        it.copy(allDubLanguages = langs.dub, allSubLanguages = langs.sub)
                    }
                }
        }
    }

    fun onInputChanged(text: String) {
        _uiState.update { it.copy(rawInput = text) }
        inputDebounceJob?.cancel()
        inputDebounceJob = viewModelScope.launch {
            delay(500)
            reconcileUrls(parseUrls(text))
        }
    }

    /**
     * Ein geteilter Link (Android-Teilen-Menü): sofort anhängen und ohne
     * Tipp-Verzögerung analysieren. Schon vorhandene URLs bleiben unberührt.
     */
    fun addUrl(url: String) {
        val clean = url.trim()
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) return
        val current = _uiState.value.rawInput
        if (parseUrls(current).contains(clean)) return
        val next = if (current.isBlank()) clean else current.trimEnd() + "\n" + clean
        _uiState.update { it.copy(rawInput = next) }
        inputDebounceJob?.cancel()
        inputDebounceJob = viewModelScope.launch { reconcileUrls(parseUrls(next)) }
    }

    private fun parseUrls(text: String): List<String> =
        text.split('\n', ',', ' ')
            .map { it.trim() }
            .filter { it.startsWith("http://") || it.startsWith("https://") }
            .distinct()

    private suspend fun reconcileUrls(newUrls: List<String>) {
        val current = _uiState.value.cards
        val keep = current.filter { it.url in newUrls }
        val keepUrls = keep.map { it.url }.toSet()
        val fresh = newUrls.filterNot { it in keepUrls }
        val withLoaders = keep + fresh.map { ImportCardState.Loading(it) }
        _uiState.update { it.copy(cards = withLoaders) }

        coroutineScope {
            val sem = Semaphore(4)
            fresh.map { url ->
                async {
                    sem.withPermit {
                        val card = analyzeCard(url)
                        replaceCard(url) { card }
                    }
                }
            }.awaitAll()
        }
        fillMissingEpisodes()
    }

    /**
     * Zwei Wege gleichzeitig: yt-dlp fragt das Backend, und für unbekannte
     * Hosts besucht parallel die unsichtbare WebView die Seite. Gewinnt
     * yt-dlp, wird der Seitenbesuch abgebrochen; scheitert es, steht der
     * mitgelesene Stream meist schon bereit. So dauert ein Filehoster-Link
     * nicht Analyse PLUS Seitenbesuch, sondern nur das Längere von beidem.
     */
    private suspend fun analyzeCard(url: String): ImportCardState = coroutineScope {
        val sniffJob = if (DirectHosts.isWellSupported(url)) null
        else async { runCatching { sniffer.sniff(url) }.getOrNull() }

        val analyzed = runCatching { repo.analyzeVideo(url) }
        analyzed.fold(
            onSuccess = { r ->
                sniffJob?.cancel()
                readyFromAnalyze(url, r)
            },
            onFailure = { e ->
                if (sniffJob == null) {
                    return@fold ImportCardState.Failed(url, e.message ?: "Analyze fehlgeschlagen")
                }
                replaceCard(url) {
                    ImportCardState.Loading(url, hint = "Kein direkter Link — Seite wird nach dem Player durchsucht…")
                }
                val found = sniffJob.await()
                if (found != null) readyFromSniff(url, found)
                else ImportCardState.Failed(url, "Kein Video auf der Seite gefunden (${e.message ?: "Analyze fehlgeschlagen"})")
            },
        )
    }

    private fun readyFromAnalyze(url: String, r: AnalyzeResponse) = ImportCardState.Ready(
        url = url,
        title = r.title.orEmpty(),
        thumbnailUrl = r.thumbnailUrl,
        seriesTitle = r.aiMeta?.seriesTitle,
        season = r.aiMeta?.season,
        episode = r.aiMeta?.episode,
        dubLanguage = r.aiMeta?.dubLanguage,
        subLanguage = r.aiMeta?.subLanguage,
        isMovie = r.aiMeta?.isMovie ?: false,
    )

    /**
     * Ohne yt-dlp-Metadaten bleibt die URL selbst die beste Quelle für Serie,
     * Staffel und Folge — dieselben Regeln wie im In-App-Browser.
     */
    private fun readyFromSniff(url: String, found: HeadlessResult): ImportCardState.Ready {
        val meta = PageMetaParser.parse(url)
        return ImportCardState.Ready(
            url = url,
            title = found.title.orEmpty(),
            seriesTitle = meta.seriesTitle,
            season = meta.season,
            episode = EpisodeLinkFilter.episodeNumber(url),
            sniffed = SniffedSource(
                mediaUrl = found.finding.url,
                // Ohne Referer aus dem Interceptor ist die Seite selbst die
                // beste Annahme — der Hoster erwartet ohnehin genau sie.
                referer = found.finding.referer ?: url,
                cookie = found.finding.cookie,
                userAgent = found.finding.userAgent,
                description = found.description,
            ),
        )
    }

    /**
     * Füllt fehlende Folgennummern aus dem Vorgänger auf: Gehören zwei
     * aufeinanderfolgende Ready-Karten zur selben Serie und nur die zweite
     * hat keine Folge, bekommt sie die nächste Nummer (Ketten-Auffüllung,
     * damit auch drei Lücken hintereinander greifen).
     */
    private fun fillMissingEpisodes() {
        _uiState.update { state ->
            val cards = state.cards.toMutableList()
            var prevSeries: String? = null
            var prevEpisode: Int? = null
            for (i in cards.indices) {
                val card = cards[i] as? ImportCardState.Ready
                if (card == null) {
                    prevSeries = null
                    prevEpisode = null
                    continue
                }
                val series = card.seriesTitle ?: state.defaults.seriesTitle
                if (card.episode == null &&
                    prevEpisode != null &&
                    series != null && prevSeries != null &&
                    series.equals(prevSeries, ignoreCase = true)
                ) {
                    val filled = prevEpisode + 1
                    cards[i] = card.copy(episode = filled)
                    prevEpisode = filled
                } else {
                    prevEpisode = card.episode
                }
                prevSeries = series
            }
            state.copy(cards = cards)
        }
    }

    private fun replaceCard(url: String, transform: (ImportCardState) -> ImportCardState) {
        _uiState.update { state ->
            state.copy(cards = state.cards.map { if (it.url == url) transform(it) else it })
        }
    }

    fun updateCard(url: String, patch: ImportCardState.Ready.() -> ImportCardState.Ready) {
        replaceCard(url) {
            if (it is ImportCardState.Ready) it.patch() else it
        }
    }

    fun toggleExpanded(url: String) =
        updateCard(url) { copy(expanded = !expanded) }

    fun removeCard(url: String) {
        _uiState.update { state ->
            state.copy(
                cards = state.cards.filterNot { it.url == url },
                rawInput = state.rawInput.lines().filter { it.trim() != url }.joinToString("\n"),
            )
        }
    }

    fun retryCard(url: String) {
        replaceCard(url) { ImportCardState.Loading(url) }
        viewModelScope.launch {
            val card = analyzeCard(url)
            replaceCard(url) { card }
            fillMissingEpisodes()
        }
    }

    fun updateDefaults(transform: SharedDefaults.() -> SharedDefaults) {
        _uiState.update { it.copy(defaults = it.defaults.transform()) }
    }

    suspend fun submit(): Int? {
        val state = _uiState.value
        val ready = state.cards.filterIsInstance<ImportCardState.Ready>()
        if (ready.isEmpty()) return null

        fun metadataOf(card: ImportCardState.Ready) = ImportItemMetadata(
            title = card.title.takeIf { it.isNotBlank() },
            seriesId = card.seriesId ?: state.defaults.seriesId,
            seriesTitle = card.seriesTitle ?: state.defaults.seriesTitle,
            season = card.season ?: state.defaults.season,
            episode = card.episode,
            dubLanguage = card.dubLanguage ?: state.defaults.dubLanguage,
            subLanguage = card.subLanguage ?: state.defaults.subLanguage,
            isMovie = card.isMovie.takeIf { it },
        )

        val direct = ready.filter { it.sniffed == null }.map { card ->
            BulkImportItem(url = card.url, metadata = metadataOf(card))
        }
        val sniffed = ready.mapNotNull { card ->
            val s = card.sniffed ?: return@mapNotNull null
            SniffedImportItem(
                pageUrl = card.url,
                mediaUrl = s.mediaUrl,
                referer = s.referer,
                cookie = s.cookie,
                userAgent = s.userAgent,
                title = card.title.takeIf { it.isNotBlank() },
                description = s.description,
                metadata = metadataOf(card),
            )
        }

        _uiState.update { it.copy(submitting = true, submitError = null) }
        val n = runCatching {
            var total = 0
            if (direct.isNotEmpty()) total += repo.importVideosBulk(direct)
            if (sniffed.isNotEmpty()) total += repo.importSniffed(sniffed)
            total
        }
            .onFailure { e ->
                _uiState.update {
                    it.copy(submitting = false, submitError = e.message ?: "Import fehlgeschlagen")
                }
            }
            .getOrNull()
        if (n != null) {
            _uiState.update { s ->
                ImportSheetUiState(
                    allSeries = s.allSeries,
                    allDubLanguages = s.allDubLanguages,
                    allSubLanguages = s.allSubLanguages,
                )
            }
        }
        return n
    }
}
