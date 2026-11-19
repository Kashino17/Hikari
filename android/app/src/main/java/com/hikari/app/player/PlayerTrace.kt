package com.hikari.app.player

import android.util.Log
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Spur des Video-Players (Öffnen, Minimieren, Zurück, Neuaufbau …) — landet im Logcat
 * (Tag HikariPlayerTrace) und gesammelt auf dem Server (~/.hikari/client-trace.log), damit sich
 * Fehler auf dem Handy nachvollziehen lassen, auch ohne Kabel. Klein und still: Ist der Server
 * nicht erreichbar, warten höchstens 2000 Zeilen auf den nächsten Versuch.
 */
object PlayerTrace {
    private const val TAG = "HikariPlayerTrace"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pending = ArrayDeque<String>()
    private var baseUrl: String? = null
    private var flushJob: Job? = null
    private val retryPending = java.util.concurrent.atomic.AtomicBoolean(false)
    private val time = SimpleDateFormat("dd.MM. HH:mm:ss.SSS", Locale.GERMANY)

    fun setBackend(url: String) {
        baseUrl = url.trimEnd('/')
    }

    fun log(msg: String) {
        Log.d(TAG, msg)
        synchronized(pending) {
            pending.addLast("${time.format(Date())} $msg")
            while (pending.size > MAX_PENDING) pending.removeFirst()
        }
        if (flushJob?.isActive != true) {
            flushJob = scope.launch {
                delay(2500)
                flush()
            }
        }
    }

    private fun flush() {
        // Adresse noch nicht geladen (Dienst startet vor der App) oder Server nicht erreichbar:
        // in 30 s von selbst erneut versuchen.
        val base = baseUrl ?: return scheduleRetry()
        while (true) {
            // Der Server nimmt höchstens 500 Zeilen je Aufruf an.
            val lines = synchronized(pending) {
                List(minOf(400, pending.size)) { pending.removeFirst() }
            }
            if (lines.isEmpty()) return
            if (!send(base, lines)) return scheduleRetry()
        }
    }

    /** Höchstens ein Wiederholungsversuch wartet gleichzeitig. */
    private fun scheduleRetry() {
        if (!retryPending.compareAndSet(false, true)) return
        scope.launch {
            delay(30_000)
            retryPending.set(false)
            flush()
        }
    }

    private fun send(base: String, lines: List<String>): Boolean {
        val sent = runCatching {
            val conn = URL("$base/debug/trace").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.doOutput = true
            conn.setRequestProperty("content-type", "application/json")
            val body = JSONObject().put("lines", JSONArray(lines)).toString()
            conn.outputStream.use { it.write(body.toByteArray()) }
            val code = conn.responseCode
            conn.disconnect()
            check(code in 200..299)
        }.isSuccess
        // Server nicht erreichbar (unterwegs): Zeilen behalten und beim nächsten Eintrag erneut senden.
        if (!sent) {
            synchronized(pending) {
                lines.asReversed().forEach { pending.addFirst(it) }
                while (pending.size > MAX_PENDING) pending.removeFirst()
            }
        }
        return sent
    }

    private const val MAX_PENDING = 2000
}
