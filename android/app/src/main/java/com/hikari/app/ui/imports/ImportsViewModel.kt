package com.hikari.app.ui.imports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.api.dto.PendingImportDto
import com.hikari.app.data.api.dto.PendingImportPatch
import com.hikari.app.data.api.dto.SeriesDto
import com.hikari.app.domain.repo.ChannelsRepository
import com.hikari.app.domain.repo.FeedRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ImportsUiState(
    val pending: List<PendingImportDto> = emptyList(),
    val series: List<SeriesDto> = emptyList(),
    val loading: Boolean = true,
    /** Die Karte, die gerade zum Bearbeiten aufgeklappt ist. */
    val editingId: String? = null,
    val savingId: String? = null,
    val error: String? = null,
    val message: String? = null,
) {
    val active: List<PendingImportDto> get() = pending.filter { it.status != "failed" }
    val failed: List<PendingImportDto> get() = pending.filter { it.status == "failed" }
}

/**
 * Übersicht der manuell hinzugefügten Inhalte.
 *
 * Der Kern ist die Liste der laufenden Downloads: Ein Serienimport dauert
 * Minuten, und ohne diese Ansicht hatte der Nutzer keinerlei Anhaltspunkt, ob
 * überhaupt etwas passiert. Solange geladen wird, lassen sich Titel, Serie und
 * Sprache bereits eintragen — die Werte übernimmt das Backend beim Abschluss.
 */
@HiltViewModel
class ImportsViewModel @Inject constructor(
    private val repo: ChannelsRepository,
    private val libraryRepo: FeedRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(ImportsUiState())
    val ui: StateFlow<ImportsUiState> = _ui.asStateFlow()

    private var poller: Job? = null

    init {
        refresh()
        startPolling()
    }

    /**
     * Der Fortschritt kommt per Abfrage, nicht per Push.
     *
     * Zwei Sekunden sind der Kompromiss: Der Balken läuft sichtbar weiter, ohne
     * dass die Ansicht den Server mit Anfragen überzieht. Die Schleife hält an,
     * sobald nichts mehr lädt — sonst pollte die App endlos im Leerlauf.
     */
    private fun startPolling() {
        poller?.cancel()
        poller = viewModelScope.launch {
            while (true) {
                delay(POLL_INTERVAL_MS)
                if (_ui.value.active.isEmpty()) continue
                loadPending()
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _ui.update { it.copy(loading = true) }
            loadPending()
            runCatching { libraryRepo.getLibrary() }
                .onSuccess { lib -> _ui.update { it.copy(series = lib.series) } }
            _ui.update { it.copy(loading = false) }
        }
    }

    private suspend fun loadPending() {
        runCatching { repo.listImports() }
            .onSuccess { items -> _ui.update { it.copy(pending = items, error = null) } }
            .onFailure { e -> _ui.update { it.copy(error = e.message) } }
    }

    fun toggleEdit(id: String) {
        _ui.update { it.copy(editingId = if (it.editingId == id) null else id) }
    }

    /**
     * Speichert die Angaben eines laufenden Imports.
     *
     * Die Antwort des Servers ersetzt den Eintrag in der Liste, statt lokal
     * zu raten — so bleibt der Fortschritt aus derselben Quelle wie alles
     * andere, auch wenn parallel eine Aktualisierung hereinkommt.
     */
    fun save(id: String, patch: PendingImportPatch) {
        if (_ui.value.savingId != null) return
        _ui.update { it.copy(savingId = id) }
        viewModelScope.launch {
            runCatching { repo.updateImport(id, patch) }
                .onSuccess { updated ->
                    _ui.update { st ->
                        st.copy(
                            pending = st.pending.map { if (it.id == id) updated else it },
                            savingId = null,
                            editingId = null,
                            message = "Gespeichert",
                        )
                    }
                }
                .onFailure { e ->
                    _ui.update { it.copy(savingId = null, error = "Speichern fehlgeschlagen: ${e.message}") }
                }
        }
    }

    fun dismissFailed(id: String) {
        viewModelScope.launch {
            runCatching { repo.deleteImport(id) }
                .onSuccess { _ui.update { st -> st.copy(pending = st.pending.filterNot { it.id == id }) } }
                .onFailure { e -> _ui.update { it.copy(error = e.message) } }
        }
    }

    fun dismissMessage() = _ui.update { it.copy(message = null, error = null) }

    private companion object {
        const val POLL_INTERVAL_MS = 2_000L
    }
}
