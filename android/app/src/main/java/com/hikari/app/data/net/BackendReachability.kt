package com.hikari.app.data.net

import com.hikari.app.data.prefs.SettingsStore
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Weiß, ob der Hikari-Server gerade antwortet.
 *
 * [ConnectivityObserver] sagt nur, ob das Handy im Netz ist. Für Hikari reicht
 * das nicht: unterwegs ohne Verbindung nach Hause (oder bei ausgeschaltetem PC)
 * ist das Handy „online", aber nichts lässt sich streamen. Wer wissen will, ob
 * Inhalte vom Server spielbar sind, fragt deshalb hier nach.
 *
 * Der Stand gilt für die ganze App: Die Bibliothek meldet, ob ihr Abruf
 * geklappt hat ([report]); die Musikseite fragt beim Öffnen selbst nach
 * ([check]). Bis zur ersten Auskunft gilt der Server als erreichbar — das
 * vermeidet, dass beim Start kurz der Offline-Bereich aufblitzt.
 */
@Singleton
class BackendReachability @Inject constructor(
    client: OkHttpClient,
    private val settings: SettingsStore,
    private val connectivity: ConnectivityObserver,
) {
    // Eigene, kurze Frist: die Auskunft soll in Sekunden da sein, nicht nach
    // dem großzügigen Timeout der Streaming-Aufrufe.
    private val probeClient = client.newBuilder()
        .connectTimeout(PROBE_TIMEOUT_S, TimeUnit.SECONDS)
        .callTimeout(PROBE_TIMEOUT_S, TimeUnit.SECONDS)
        .retryOnConnectionFailure(false)
        .build()

    private val _reachable = MutableStateFlow(true)
    val reachable: StateFlow<Boolean> = _reachable.asStateFlow()

    /** Handy im Netz UND Server antwortet — nur dann ist Streamen möglich. */
    fun canStream(): Boolean = connectivity.currentlyOnline() && _reachable.value

    /** Eine andere Stelle hat gerade erfahren, ob der Server antwortet. */
    fun report(reachable: Boolean) {
        _reachable.value = reachable
    }

    /** Fragt den Server kurz an und merkt sich das Ergebnis. */
    suspend fun check(): Boolean = withContext(Dispatchers.IO) {
        val ok = connectivity.currentlyOnline() && runCatching {
            val base = settings.backendUrl.first().trimEnd('/')
            // Jede Antwort zählt, auch eine Absage: dann ist der Server da.
            probeClient.newCall(Request.Builder().url("$base/health").build()).execute().close()
            true
        }.getOrDefault(false)
        _reachable.value = ok
        ok
    }

    private companion object {
        const val PROBE_TIMEOUT_S = 5L
    }
}
