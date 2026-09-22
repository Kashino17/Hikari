package com.hikari.app.ui.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.api.dto.AnalyzeResponse
import com.hikari.app.data.api.dto.BulkImportItem
import com.hikari.app.data.api.dto.ImportItemMetadata
import com.hikari.app.data.api.dto.SeriesItemDto
import com.hikari.app.data.api.dto.SniffedImportItem
import com.hikari.app.domain.browser.EpisodeDiscovery
import com.hikari.app.domain.browser.EpisodeLinkFilter
import com.hikari.app.domain.browser.EpisodeRef
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

/**
 * Erkennt, ob eine URL eine Staffel-/Übersichtsseite ist (mehrere Folgen) statt
 * einer einzelnen Folge. Bewusst konservativ: Im Zweifel Einzelvideo, denn eine
 * fälschlich aufgeteilte Einzelseite wäre ärgerlicher als eine nicht erkannte
 * Staffel (die man dann Folge für Folge schickt).
 */
internal object SeasonPage {
    fun looksLikeMultiEpisode(url: String): Boolean {
        val lower = url.lowercase()
        // Zeigt die URL auf eine konkrete Folge, ist es keine Übersicht.
        val hasEpisode = lower.contains("episode") || lower.contains("/folge") ||
            Regex("""[/\-_]ep[-_]?\d""").containsMatchIn(lower) ||
            Regex("""s\d{1,2}e\d{1,3}""").containsMatchIn(lower)
        if (hasEpisode) return false
        return lower.contains("staffel") || lower.contains("season") ||
            Regex("""/serie[ns]?/[^/]+/?(\?.*)?$""").containsMatchIn(lower) ||
            Regex("""/anime/stream/[^/]+/?(\?.*)?$""").containsMatchIn(lower)
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

    /** Staffel-Seiten, die bereits in Folgen aufgeteilt wurden — nicht erneut. */
    private val expandedSeasons = mutableSetOf<String>()

    /**
     * Karten-URLs, die aus einer Staffel-Seite entstanden sind (nicht selbst im
     * Eingabetext). Ohne sie würde die Abgleich-Logik sie beim nächsten
     * Tastendruck wieder wegräumen, weil sie nicht im Textfeld stehen.
     */
    private val derivedUrls = mutableSetOf<String>()

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
        // Karten behalten, die noch im Text stehen ODER aus einer Staffel
        // abgeleitet wurden.
        val keep = current.filter { it.url in newUrls || it.url in derivedUrls }
        val keepUrls = keep.map { it.url }.toSet()
        val fresh = newUrls.filterNot { it in keepUrls || it in expandedSeasons }
        val withLoaders = keep + fresh.map { ImportCardState.Loading(it) }
        _uiState.update { it.copy(cards = withLoaders) }

        // Jede URL bekommt einen EIGENEN Job im viewModelScope — nicht als Kind
        // des Debounce-Jobs. Der wird beim nächsten Tastendruck abgebrochen und
        // riss vorher sämtliche laufenden Analysen mit (eine erkannte Staffel
        // blieb dann ewig bei "Folge 1 …" und nichts war absendbar).
        for (url in fresh) {
            analysisJobs[url]?.cancel()
            analysisJobs[url] = viewModelScope.launch {
                try {
                    analysisSemaphore.withPermit { processFreshUrl(url) }
                    fillMissingEpisodes()
                } finally {
                    analysisJobs.remove(url)
                }
            }
        }
    }

    /** Laufende Analysen je Eingabe-URL (Staffel-URLs decken ihre Folgen mit ab). */
    private val analysisJobs = mutableMapOf<String, Job>()
    private val analysisSemaphore = Semaphore(4)

    /**
     * Eine neu eingegangene URL: Ist es eine Staffel-/Übersichtsseite, wird sie
     * in ihre Folgen aufgeteilt; sonst als einzelnes Video analysiert.
     */
    private suspend fun processFreshUrl(url: String) {
        if (looksLikeMultiEpisode(url)) {
            expandSeason(url)
        } else {
            val card = analyzeCard(url)
            replaceCard(url) { card }
        }
    }

    /**
     * Liest die Folgen einer Staffel-Seite aus und legt pro Folge eine Karte an
     * — jede bekommt Serie, Staffel und Folgennummer, und wird danach einzeln
     * gesnifft. Genau das, was der Nutzer wollte: eine Staffel schicken, alle
     * Folgen landen fertig beschriftet in Hikari.
     */
    private suspend fun expandSeason(url: String) {
        replaceCard(url) {
            ImportCardState.Loading(url, hint = "Staffel wird gelesen — Folgen werden gesucht…")
        }
        val disc = runCatching { sniffer.discoverEpisodes(url) }.getOrNull()
        val episodes = disc?.episodes.orEmpty()
        if (episodes.isEmpty()) {
            // Doch keine Folgen erkannt — wie Einzelseite behandeln.
            val card = analyzeCard(url)
            replaceCard(url) { card }
            return
        }
        expandedSeasons.add(url)
        // Abgeleitete URLs SOFORT registrieren — nicht erst, wenn ihre Analyse
        // an der Reihe ist. Sonst räumte ein Tastendruck im Eingabefeld die
        // noch wartenden Folgenkarten wieder weg (sie stehen nicht im Text),
        // und die Staffel kam "erkannt, aber nichts importiert" an.
        episodes.forEach { derivedUrls.add(it.url) }
        // Staffel-Karte durch je eine Karte pro Folge ersetzen.
        _uiState.update { st ->
            val withoutSeason = st.cards.filterNot { it.url == url }
            val epCards = episodes.map { ImportCardState.Loading(it.url, hint = "Folge ${it.episode ?: "?"} …") }
            st.copy(cards = withoutSeason + epCards)
        }
        // Eingabetext um die Staffel-URL bereinigen, damit sie nicht als
        // gescheiterte Einzelkarte zurückkommt.
        _uiState.update { st ->
            st.copy(rawInput = st.rawInput.lines().filter { it.trim() != url }.joinToString("\n"))
        }

        coroutineScope {
            val sem = Semaphore(2)
            episodes.map { ep ->
                async {
                    sem.withPermit {
                        // Karte inzwischen entfernt (Nutzer hat sie weggewischt)? Dann nicht mehr anfassen.
                        if (ep.url !in derivedUrls) return@withPermit
                        val card = analyzeCard(ep.url, seed = ep, discovery = disc)
                        replaceCard(ep.url) { card }
                    }
                }
            }.awaitAll()
        }

        // Ist KEINE Folge brauchbar geworden, darf die Staffel nicht spurlos
        // verschwinden: eine Karte für die Staffel-URL mit Fehler + Neuversuch.
        val anyReady = _uiState.value.cards.any { it.url in episodes.map { e -> e.url }.toSet() && it is ImportCardState.Ready }
        if (!anyReady) {
            val firstError = _uiState.value.cards
                .filterIsInstance<ImportCardState.Failed>()
                .firstOrNull { f -> episodes.any { it.url == f.url } }?.error
            episodes.forEach { derivedUrls.remove(it.url) }
            expandedSeasons.remove(url)
            _uiState.update { st ->
                st.copy(
                    cards = st.cards.filterNot { c -> episodes.any { it.url == c.url } } +
                        ImportCardState.Failed(
                            url,
                            "${episodes.size} Folgen erkannt, aber keine ließ sich einlesen" +
                                (firstError?.let { " — $it" } ?: ""),
                        ),
                )
            }
        }
    }

    /** Grobe Erkennung einer Staffel-/Übersichtsseite (keine einzelne Folge). */
    private fun looksLikeMultiEpisode(url: String): Boolean = SeasonPage.looksLikeMultiEpisode(url)

    /**
     * Zwei Wege gleichzeitig: yt-dlp fragt das Backend, und für unbekannte
     * Hosts besucht parallel die unsichtbare WebView die Seite. Gewinnt
     * yt-dlp, wird der Seitenbesuch abgebrochen; scheitert es, steht der
     * mitgelesene Stream meist schon bereit. So dauert ein Filehoster-Link
     * nicht Analyse PLUS Seitenbesuch, sondern nur das Längere von beidem.
     *
     * [seed]/[discovery] tragen die Metadaten einer aus einer Staffel-Seite
     * abgeleiteten Folge (Serie/Staffel/Folge), damit die Karte sie behält,
     * auch wenn der Sniff sie nicht aus der URL ablesen kann.
     */
    private suspend fun analyzeCard(
        url: String,
        seed: EpisodeRef? = null,
        discovery: EpisodeDiscovery? = null,
    ): ImportCardState = coroutineScope {
        val sniffJob = if (DirectHosts.isWellSupported(url)) null
        else async { runCatching { sniffer.sniffDetailed(url) }.getOrNull() }

        val analyzed = runCatching { repo.analyzeVideo(url) }
        analyzed.fold(
            onSuccess = { r ->
                sniffJob?.cancel()
                readyFromAnalyze(url, r).mergeSeed(seed, discovery)
            },
            onFailure = { e ->
                if (sniffJob == null) {
                    return@fold ImportCardState.Failed(url, e.message ?: "Analyze fehlgeschlagen")
                }
                replaceCard(url) {
                    ImportCardState.Loading(
                        url,
                        hint = seed?.let { "Folge ${it.episode ?: "?"} — Player wird gesucht…" }
                            ?: "Kein direkter Link — Seite wird nach dem Player durchsucht…",
                    )
                }
                val outcome = sniffJob.await()
                val found = outcome?.result
                if (found != null) {
                    readyFromSniff(url, found).mergeSeed(seed, discovery)
                } else {
                    // Diagnose sichtbar machen, sonst ist von außen nicht zu
                    // sehen, woran der Seitenbesuch scheiterte.
                    val diag = outcome?.diagnostics?.takeIf { it.isNotBlank() }
                    ImportCardState.Failed(
                        url,
                        diag?.let { "Kein Stream gefunden — $it" }
                            ?: "Kein Video auf der Seite gefunden (${e.message ?: "Analyze fehlgeschlagen"})",
                    )
                }
            },
        )
    }

    /** Übernimmt Serie/Staffel/Folge aus der Staffel-Erkennung, wo die Karte selbst keine hat. */
    private fun ImportCardState.mergeSeed(seed: EpisodeRef?, discovery: EpisodeDiscovery?): ImportCardState {
        if (this !is ImportCardState.Ready || seed == null) return this
        return copy(
            seriesTitle = seriesTitle ?: discovery?.seriesTitle,
            season = season ?: discovery?.season,
            episode = episode ?: seed.episode,
            title = title.ifBlank { seed.label },
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
        // Seiten-DOM (JSON-LD, h1, Folgentitel) über die URL-Ableitung gelegt —
        // "American Horror Story" statt "American Horror Story Die Dunkle Seite In Dir".
        val meta = found.meta
        return ImportCardState.Ready(
            url = url,
            title = meta.episodeTitle ?: found.title.orEmpty(),
            seriesTitle = meta.seriesTitle,
            season = meta.season,
            episode = meta.episode ?: EpisodeLinkFilter.episodeNumber(url),
            isMovie = meta.isMovie,
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
        derivedUrls.remove(url)
        analysisJobs.remove(url)?.cancel()
        _uiState.update { state ->
            state.copy(
                cards = state.cards.filterNot { it.url == url },
                rawInput = state.rawInput.lines().filter { it.trim() != url }.joinToString("\n"),
            )
        }
    }

    fun retryCard(url: String) {
        replaceCard(url) { ImportCardState.Loading(url) }
        analysisJobs[url]?.cancel()
        analysisJobs[url] = viewModelScope.launch {
            try {
                // Über processFreshUrl, damit eine Staffel-Seite erneut aufgeteilt wird.
                analysisSemaphore.withPermit { processFreshUrl(url) }
                fillMissingEpisodes()
            } finally {
                analysisJobs.remove(url)
            }
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

        // Direktlinks und mitgelesene Streams in EINEM Request: Der Server
        // führt sie als einen Job, und die Kanalansicht sieht Fortschritt und
        // Fehler aller Karten — vorher war nur der zweite von zwei Jobs sichtbar.
        val items = ready.map { card ->
            val s = card.sniffed
            if (s == null) {
                BulkImportItem.direct(card.url, metadataOf(card))
            } else {
                BulkImportItem.sniffed(
                    SniffedImportItem(
                        pageUrl = card.url,
                        mediaUrl = s.mediaUrl,
                        referer = s.referer,
                        cookie = s.cookie,
                        userAgent = s.userAgent,
                        title = card.title.takeIf { it.isNotBlank() },
                        description = s.description,
                        metadata = metadataOf(card),
                    ),
                )
            }
        }

        _uiState.update { it.copy(submitting = true, submitError = null) }
        val n = runCatching { repo.importVideosBulk(items) }
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
