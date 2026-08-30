package com.hikari.app.ui.channels

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.api.dto.BulkJobStatusDto
import com.hikari.app.data.api.dto.ChannelVideoDto
import com.hikari.app.data.api.dto.PendingImportDto
import com.hikari.app.data.api.dto.PendingImportPatch
import com.hikari.app.domain.model.Channel
import com.hikari.app.domain.repo.ChannelsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ChannelDetailViewModel @Inject constructor(
    private val repo: ChannelsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val channelId: String = checkNotNull(savedStateHandle["channelId"])

    private val _channel = MutableStateFlow<Channel?>(null)
    val channel: StateFlow<Channel?> = _channel.asStateFlow()

    private val _videos = MutableStateFlow<List<ChannelVideoDto>>(emptyList())
    val videos: StateFlow<List<ChannelVideoDto>> = _videos.asStateFlow()

    private val _loading = MutableStateFlow(true)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _syncing = MutableStateFlow(false)
    val syncing: StateFlow<Boolean> = _syncing.asStateFlow()

    private val _syncMessage = MutableStateFlow<String?>(null)
    val syncMessage: StateFlow<String?> = _syncMessage.asStateFlow()

    /** Flips to true once the channel has been unsubscribed, so the screen can pop back. */
    private val _removed = MutableStateFlow(false)
    val removed: StateFlow<Boolean> = _removed.asStateFlow()

    // ---- Laufende Importe (nur im Archiv "Manuell hinzugefügt") ----------
    //
    // Ein Import dauert Minuten. Er gehört genau dorthin, wo das fertige Video
    // später auch landet — deshalb stehen laufende Übertragungen oben in
    // dieser Liste statt auf einem eigenen Bildschirm.

    private val _pending = MutableStateFlow<List<PendingImportDto>>(emptyList())
    val pending: StateFlow<List<PendingImportDto>> = _pending.asStateFlow()

    private val _editingImport = MutableStateFlow<String?>(null)
    val editingImport: StateFlow<String?> = _editingImport.asStateFlow()

    private val _savingImport = MutableStateFlow<String?>(null)
    val savingImport: StateFlow<String?> = _savingImport.asStateFlow()

    private var pendingPoller: Job? = null

    /** Nur das manuelle Archiv kennt laufende Importe. */
    val showsImports: Boolean get() = channelId == MANUAL_CHANNEL_ID

    // ---- Bulk-Job-Zusammenfassung ------------------------------------------
    //
    // Das Import-Sheet und der Browser schicken oft mehrere URLs auf einmal.
    // Der Server fasst sie zu einem Job zusammen; diese Karte zeigt dessen
    // Gesamtergebnis, während die einzelnen Zeilen unten den Fortschritt
    // pro Import tragen.

    private val _bulkJob = MutableStateFlow<BulkJobStatusDto?>(null)
    val bulkJob: StateFlow<BulkJobStatusDto?> = _bulkJob.asStateFlow()

    /** Weggewischt heißt: bis zum nächsten Job ausgeblendet. */
    private val _bulkJobDismissed = MutableStateFlow(false)
    val bulkJobDismissed: StateFlow<Boolean> = _bulkJobDismissed.asStateFlow()

    fun dismissBulkJob() { _bulkJobDismissed.value = true }

    fun toggleImportEdit(id: String) {
        _editingImport.value = if (_editingImport.value == id) null else id
    }

    private fun startPendingPolling() {
        if (!showsImports) return
        pendingPoller?.cancel()
        pendingPoller = viewModelScope.launch {
            while (true) {
                loadPending()
                loadBulkJob()
                // Zwei Sekunden: Der Balken läuft sichtbar weiter, ohne den
                // Server mit Anfragen zu überziehen.
                delay(PENDING_POLL_MS)
            }
        }
    }

    /**
     * Stand des letzten Bulk-Jobs nachziehen. Nur solange nötig: Hat der Job
     * sein Ende gemeldet und läuft kein Import mehr, liegt das Ergebnis fest
     * und das Polling kann ruhen.
     */
    private suspend fun loadBulkJob() {
        val current = _bulkJob.value
        val busy = _pending.value.isNotEmpty() || (current != null && current.finishedAt == null)
        if (!busy) return
        runCatching { repo.bulkImportStatus() }
            .onSuccess { job ->
                // Ein neuer Job macht die Karte wieder sichtbar.
                if (job.id != current?.id) _bulkJobDismissed.value = false
                _bulkJob.value = job
            }
        // Fehler (z. B. 404, weil es noch nie einen Job gab) heißen einfach:
        // keine Karte.
    }

    private suspend fun loadPending() {
        val before = _pending.value.map { it.id }.toSet()
        runCatching { repo.listImports() }
            .onSuccess { items ->
                _pending.value = items
                // Ein gerade fertig gewordener Import taucht als Video auf —
                // die Liste muss nachziehen, sonst verschwindet er einfach.
                if (before.isNotEmpty() && items.none { it.id in before }) load()
            }
            // Ohne diesen Zweig verschwand jeder Fehler hier spurlos: Beim
            // Umbruch der Antwort scheiterte das Parsen, die Downloadliste
            // blieb leer, und nichts deutete auf den Grund hin.
            .onFailure { _error.value = "Downloads nicht abrufbar: ${it.message}" }
    }

    fun saveImport(id: String, patch: PendingImportPatch) {
        if (_savingImport.value != null) return
        _savingImport.value = id
        viewModelScope.launch {
            runCatching { repo.updateImport(id, patch) }
                .onSuccess { updated ->
                    _pending.value = _pending.value.map { if (it.id == id) updated else it }
                    _editingImport.value = null
                }
                .onFailure { _error.value = "Speichern fehlgeschlagen: ${it.message}" }
            _savingImport.value = null
        }
    }

    fun dismissImport(id: String) = viewModelScope.launch {
        runCatching { repo.deleteImport(id) }
            .onSuccess { _pending.value = _pending.value.filterNot { it.id == id } }
            .onFailure { _error.value = it.message }
    }

    fun dismissSyncMessage() { _syncMessage.value = null }

    /** Unsubscribe from this channel (soft-delete on the backend → no more new videos). */
    fun unsubscribe() = viewModelScope.launch {
        runCatching { repo.remove(channelId) }
            .onSuccess { _removed.value = true }
            .onFailure { _error.value = it.message ?: "Deabonnieren fehlgeschlagen" }
    }

    init {
        load()
        startPendingPolling()
    }

    fun load() = viewModelScope.launch {
        _loading.value = true
        runCatching {
            // Fetch channel header (from list — no per-id endpoint) + videos in parallel.
            val all = repo.list()
            val ch = all.firstOrNull { it.id == channelId }
            _channel.value = ch
            if (ch == null) _error.value = "Kanal nicht gefunden"
            _videos.value = repo.listVideos(channelId)
        }.onFailure { _error.value = it.message ?: "Konnte Videos nicht laden" }
        _loading.value = false
    }

    fun deleteVideo(videoId: String) = viewModelScope.launch {
        runCatching { repo.deleteVideo(videoId) }
            .onSuccess {
                _videos.value = _videos.value.filterNot { it.videoId == videoId }
            }
            .onFailure { _error.value = it.message ?: "Löschen fehlgeschlagen" }
    }

    fun toggleAutoApprove() = viewModelScope.launch {
        val current = _channel.value ?: return@launch
        val target = !current.autoApprove
        // Optimistic update — flip locally, revert on failure.
        _channel.value = current.copy(autoApprove = target)
        runCatching { repo.setAutoApprove(channelId, target) }
            .onFailure {
                _channel.value = current
                _error.value = it.message ?: "Konnte Vertrauenskanal nicht umschalten"
            }
    }

    fun syncAndClip() = viewModelScope.launch {
        if (_syncing.value) return@launch
        _syncing.value = true
        _error.value = null
        runCatching {
            val pollResult = repo.pollChannel(channelId)
            repo.forceClipperWindow()
            pollResult
        }.onSuccess { result ->
            _syncMessage.value = if (result.queued > 0)
                "Sync: ${result.queued} neu, ${result.skipped} bekannt · Clipper läuft jetzt"
            else
                "Keine neuen Videos · Clipper läuft (${result.skipped} bekannt)"
            // Refresh the videos list — new ones should appear shortly via fire-and-forget pipeline
            load()
        }.onFailure {
            _error.value = it.message ?: "Sync fehlgeschlagen"
        }
        _syncing.value = false
    }

    private companion object {
        const val MANUAL_CHANNEL_ID = "manual"
        const val PENDING_POLL_MS = 2_000L
    }
}
