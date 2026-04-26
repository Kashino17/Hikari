package com.hikari.app.ui.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.api.dto.BulkImportItem
import com.hikari.app.data.api.dto.ImportItemMetadata
import com.hikari.app.data.api.dto.SeriesItemDto
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

sealed interface ImportCardState {
    val url: String

    data class Loading(override val url: String) : ImportCardState

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
    val submitting: Boolean = false,
    val submitError: String? = null,
)

@HiltViewModel
class ImportSheetViewModel @Inject constructor(
    private val repo: ChannelsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportSheetUiState())
    val uiState: StateFlow<ImportSheetUiState> = _uiState.asStateFlow()

    private var inputDebounceJob: Job? = null

    init {
        viewModelScope.launch {
            runCatching { repo.listSeries() }
                .onSuccess { fetched -> _uiState.update { it.copy(allSeries = fetched) } }
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

    private fun parseUrls(text: String): List<String> =
        text.split('\n', ',')
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
                        val result = runCatching { repo.analyzeVideo(url) }
                        replaceCard(url) { _ ->
                            result.fold(
                                onSuccess = { r ->
                                    ImportCardState.Ready(
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
                                },
                                onFailure = { e ->
                                    ImportCardState.Failed(url, e.message ?: "Analyze fehlgeschlagen")
                                },
                            )
                        }
                    }
                }
            }.awaitAll()
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
            val result = runCatching { repo.analyzeVideo(url) }
            replaceCard(url) {
                result.fold(
                    onSuccess = { r ->
                        ImportCardState.Ready(
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
                    },
                    onFailure = { e ->
                        ImportCardState.Failed(url, e.message ?: "Analyze fehlgeschlagen")
                    },
                )
            }
        }
    }

    fun updateDefaults(transform: SharedDefaults.() -> SharedDefaults) {
        _uiState.update { it.copy(defaults = it.defaults.transform()) }
    }

    suspend fun submit(): Int? {
        val state = _uiState.value
        val items = state.cards.mapNotNull { card ->
            if (card !is ImportCardState.Ready) return@mapNotNull null
            BulkImportItem(
                url = card.url,
                metadata = ImportItemMetadata(
                    title = card.title.takeIf { it.isNotBlank() },
                    seriesId = card.seriesId ?: state.defaults.seriesId,
                    seriesTitle = card.seriesTitle ?: state.defaults.seriesTitle,
                    season = card.season ?: state.defaults.season,
                    episode = card.episode,
                    dubLanguage = card.dubLanguage ?: state.defaults.dubLanguage,
                    subLanguage = card.subLanguage ?: state.defaults.subLanguage,
                    isMovie = card.isMovie.takeIf { it },
                ),
            )
        }
        if (items.isEmpty()) return null
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
                ImportSheetUiState(allSeries = s.allSeries) // reset everything except series cache
            }
        }
        return n
    }
}
