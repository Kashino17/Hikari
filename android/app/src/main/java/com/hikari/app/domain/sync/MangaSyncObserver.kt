package com.hikari.app.domain.sync

import com.hikari.app.data.api.dto.MangaSyncJobDto
import com.hikari.app.domain.repo.MangaRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface SyncStatus {
    object Idle : SyncStatus
    data class Active(val job: MangaSyncJobDto) : SyncStatus
}

@Singleton
class MangaSyncObserver @Inject constructor(
    private val repo: MangaRepository,
) {
    private val _status = MutableStateFlow<SyncStatus>(SyncStatus.Idle)
    val status: StateFlow<SyncStatus> = _status.asStateFlow()

    private var pollingJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun startPolling() {
        if (pollingJob?.isActive == true) return
        pollingJob = scope.launch {
            while (isActive) {
                runCatching { repo.listSyncJobs() }.onSuccess { jobs ->
                    val active = jobs.firstOrNull { it.status == "running" || it.status == "queued" }
                    _status.value = active?.let { SyncStatus.Active(it) } ?: SyncStatus.Idle
                }
                delay(2_000)
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }
}
