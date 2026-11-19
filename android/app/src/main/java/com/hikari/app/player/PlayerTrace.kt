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
 * Fehler auf dem Handy nachvollziehen lassen, auch ohne Kabel. Klein und still: Fehler beim
 * Senden werden ignoriert, höchstens 300 Zeilen warten.
 */
object PlayerTrace {
    private const val TAG = "HikariPlayerTrace"
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pending = ArrayDeque<String>()
    private var baseUrl: String? = null
    private var flushJob: Job? = null
    private val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.GERMANY)

    fun setBackend(url: String) {
        baseUrl = url.trimEnd('/')
    }

    fun log(msg: String) {
        Log.d(TAG, msg)
        synchronized(pending) {
            pending.addLast("${time.format(Date())} $msg")
            while (pending.size > 300) pending.removeFirst()
        }
        if (flushJob?.isActive != true) {
            flushJob = scope.launch {
                delay(2500)
                flush()
            }
        }
    }

    private fun flush() {
        val base = baseUrl ?: return
        val lines = synchronized(pending) { pending.toList().also { pending.clear() } }
        if (lines.isEmpty()) return
        runCatching {
            val conn = URL("$base/debug/trace").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.connectTimeout = 5000
            conn.readTimeout = 5000
            conn.doOutput = true
            conn.setRequestProperty("content-type", "application/json")
            val body = JSONObject().put("lines", JSONArray(lines)).toString()
            conn.outputStream.use { it.write(body.toByteArray()) }
            conn.responseCode
            conn.disconnect()
        }
    }
}
