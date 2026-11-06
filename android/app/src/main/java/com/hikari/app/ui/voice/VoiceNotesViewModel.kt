package com.hikari.app.ui.voice

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hikari.app.data.api.HikariApi
import com.hikari.app.data.api.dto.VoiceJobDto
import com.hikari.app.data.api.dto.VoiceJobListItemDto
import com.hikari.app.data.api.dto.VoiceStatusDto
import com.hikari.app.domain.voice.VoiceFormat
import com.hikari.app.voice.VoiceIntake
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

data class VoiceListState(
    val jobs: List<VoiceJobListItemDto> = emptyList(),
    val loaded: Boolean = false,
    val offline: Boolean = false,
    val status: VoiceStatusDto? = null,
)

data class VoiceDetailState(
    val job: VoiceJobDto? = null,
    val missing: Boolean = false,
    val offline: Boolean = false,
)

@HiltViewModel
class VoiceNotesViewModel @Inject constructor(
    private val api: HikariApi,
    @ApplicationContext private val ctx: Context,
) : ViewModel() {

    private val _list = MutableStateFlow(VoiceListState())
    val list: StateFlow<VoiceListState> = _list.asStateFlow()

    private val _detail = MutableStateFlow(VoiceDetailState())
    val detail: StateFlow<VoiceDetailState> = _detail.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun consumeMessage() = _message.update { null }

    suspend fun refreshList() {
        val jobs = runCatching { api.listVoiceJobs() }
        val status = runCatching { api.getVoiceStatus() }.getOrNull()
        _list.update {
            it.copy(
                jobs = jobs.getOrDefault(it.jobs),
                loaded = true,
                offline = jobs.isFailure,
                status = status ?: it.status,
            )
        }
    }

    /** Läuft, solange die Liste sichtbar ist: schnell bei laufender Arbeit, sonst gemächlich. */
    suspend fun pollList() {
        while (coroutineContext.isActive) {
            refreshList()
            val busy = _list.value.jobs.any { !VoiceFormat.isFinished(it.status) }
            delay(if (busy) 2_000 else 15_000)
        }
    }

    suspend fun pollDetail(id: String) {
        while (coroutineContext.isActive) {
            val r = runCatching { api.getVoiceJob(id) }
            val missing = (r.exceptionOrNull() as? retrofit2.HttpException)?.code() == 404
            _detail.update {
                VoiceDetailState(job = r.getOrNull() ?: it.job, missing = missing, offline = r.isFailure && !missing)
            }
            if (missing) return
            val finished = r.getOrNull()?.let { VoiceFormat.isFinished(it.status) } == true
            delay(if (finished) 30_000 else 2_000)
        }
    }

    /** Audios aus dem Dateiauswahl-Dialog — gleicher Weg wie Teilen aus WhatsApp. */
    fun uploadPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            val batch = withContext(Dispatchers.IO) { VoiceIntake.copyToBatch(ctx, uris) }
            if (batch == null) {
                _message.value = "Die Dateien ließen sich nicht lesen"
                return@launch
            }
            VoiceIntake.enqueue(ctx, batch)
            _message.value = if (uris.size == 1) "Sprachnachricht wird hochgeladen" else "${uris.size} Audios werden hochgeladen"
            delay(1_500)
            refreshList()
        }
    }

    fun retry(id: String) = viewModelScope.launch {
        runCatching { api.retryVoiceJob(id) }
            .onSuccess { job -> _detail.update { it.copy(job = job) } }
            .onFailure { _message.value = "Erneuter Versuch ging nicht" }
        refreshList()
    }

    fun delete(id: String, onDone: () -> Unit = {}) = viewModelScope.launch {
        val r = runCatching { api.deleteVoiceJob(id) }
        if (r.isSuccess || (r.exceptionOrNull() as? retrofit2.HttpException)?.code() == 404) {
            _list.update { s -> s.copy(jobs = s.jobs.filterNot { it.id == id }) }
            onDone()
        } else {
            val busy = (r.exceptionOrNull() as? retrofit2.HttpException)?.code() == 409
            _message.value = if (busy) "Läuft gerade, gleich nochmal versuchen" else "Löschen ging nicht"
        }
    }

    fun unloadModels() = viewModelScope.launch {
        val r = runCatching { api.unloadVoiceModels() }
        _message.value = when {
            r.isSuccess -> "Modelle entladen, Speicher ist frei"
            (r.exceptionOrNull() as? retrofit2.HttpException)?.code() == 409 -> "Gerade in Arbeit, danach geht es"
            else -> "Server nicht erreichbar"
        }
        refreshList()
    }
}
