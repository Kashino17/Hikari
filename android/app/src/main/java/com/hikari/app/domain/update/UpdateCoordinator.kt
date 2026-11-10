package com.hikari.app.domain.update

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Zustand des In-App-Updaters — gemeinsam für Hinweis-Banner und Einstellungen. */
sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val version: String, val downloadUrl: String) : UpdateState
    data class Downloading(val version: String, val percent: Int) : UpdateState
    data class Downloaded(val version: String, val apk: File) : UpdateState

    /** [retry] ist gesetzt, wenn ein Download scheiterte und wiederholt werden kann. */
    data class Error(val message: String, val retry: Available? = null) : UpdateState
}

/**
 * Hält den Update-Zustand app-weit. Beim App-Start wird still nach einer neuen
 * Version gesucht; gibt es eine, zeigt die Navigation oben einen Hinweis, über
 * den das Update direkt geladen und installiert wird.
 *
 * „Wegklicken“ gilt nur bis zum nächsten App-Start — der Merker lebt bewusst
 * nur im Speicher.
 */
@Singleton
class UpdateCoordinator @Inject constructor(
    private val manager: UpdateManager,
    @ApplicationContext private val ctx: Context,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private val _bannerDismissed = MutableStateFlow(false)
    val bannerDismissed: StateFlow<Boolean> = _bannerDismissed.asStateFlow()

    /** Frischer App-Start: der Hinweis darf wieder erscheinen, und es wird neu gesucht. */
    fun onAppStart() {
        _bannerDismissed.value = false
        check(silent = true)
    }

    fun dismissBanner() {
        _bannerDismissed.value = true
    }

    /**
     * Sucht nach einem neueren Release. [silent] = ohne sichtbares „Suche …“ und
     * ohne Fehlermeldung — für den automatischen Check beim Start.
     */
    fun check(silent: Boolean = false) {
        when (_state.value) {
            is UpdateState.Checking, is UpdateState.Downloading -> return
            // Ein schon gefundenes oder geladenes Update still nicht verwerfen.
            is UpdateState.Available, is UpdateState.Downloaded -> if (silent) return
            else -> Unit
        }
        if (!silent) _state.value = UpdateState.Checking
        scope.launch {
            when (val result = manager.checkForUpdate()) {
                is UpdateCheckResult.Available ->
                    _state.value = UpdateState.Available(result.version, result.downloadUrl)
                UpdateCheckResult.UpToDate -> if (!silent) _state.value = UpdateState.UpToDate
                is UpdateCheckResult.Error -> if (!silent) _state.value = UpdateState.Error(result.message)
            }
        }
    }

    /**
     * Der eine Knopf: lädt das Update und startet danach sofort die
     * Installation; ist es schon geladen, startet nur die Installation.
     */
    fun startUpdate() {
        when (val current = _state.value) {
            is UpdateState.Available -> download(current)
            is UpdateState.Downloaded -> ApkInstaller.install(ctx, current.apk)
            is UpdateState.Error -> current.retry?.let(::download)
            else -> Unit
        }
    }

    private fun download(update: UpdateState.Available) {
        _state.value = UpdateState.Downloading(update.version, percent = 0)
        scope.launch {
            val target = File(ctx.cacheDir, "update.apk")
            manager.downloadApk(update.downloadUrl, target) { percent ->
                _state.value = UpdateState.Downloading(update.version, percent)
            }
                .onSuccess { apk ->
                    _state.value = UpdateState.Downloaded(update.version, apk)
                    ApkInstaller.install(ctx, apk)
                }
                .onFailure {
                    _state.value = UpdateState.Error("Download fehlgeschlagen", retry = update)
                }
        }
    }
}
