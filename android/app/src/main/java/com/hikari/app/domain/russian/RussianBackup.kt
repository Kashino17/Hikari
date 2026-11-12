package com.hikari.app.domain.russian

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.hikari.app.BuildConfig
import com.hikari.app.data.api.HikariApi
import com.hikari.app.data.prefs.SettingsStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Sicherung des Russisch-Lernstands — damit er nie wieder verloren geht.
 *
 * Bis v0.89.102 lag der komplette Lernstand (Kurstage, Podcast-Kurs, XP, Karten, Lernzeit,
 * Gesprächsreise) in genau einer Datei (SharedPreferences "hikari_russian") ohne jede Kopie.
 * Jetzt gibt es drei Netze:
 *  1. lokal: filesDir/russian-backup/ — bester Stand + Tagesstände, unabhängig von den Einstellungen;
 *  2. auf dem Mac: PUT /russian/backup nach jeder Änderung (gebündelt), der Server
 *     lässt einen ärmeren Stand nie über einen reicheren;
 *  3. Android-Sicherung (Google) für genau diese Dateien (res/xml/backup_rules.xml).
 *
 * Beim App-Start wird verglichen: Ist irgendeine Sicherung reicher als der Stand auf dem
 * Gerät (mehr XP/Karten/Kurstage), wird sie automatisch zurückgespielt.
 */
@Singleton
class RussianBackup @Inject constructor(
    @ApplicationContext private val context: Context,
    private val api: HikariApi,
    private val progress: RussianProgressStore,
    private val settings: SettingsStore,
) {
    private val dir = File(context.filesDir, "russian-backup").apply { mkdirs() }
    private val bestFile = File(dir, "best.json")
    private val daysDir = File(dir, "days").apply { mkdirs() }
    private val mutex = Mutex()
    private var pending: Job? = null

    // SharedPreferences hält Listener nur schwach — hier fest verankert.
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> scheduleSave() }
    private lateinit var scope: CoroutineScope

    /** Einmal beim App-Start: erst prüfen/zurückholen, dann jede Änderung sichern. */
    fun start(appScope: CoroutineScope) {
        scope = appScope
        appScope.launch {
            // Die Server-Adresse kommt aus dem DataStore und landet asynchron im Netzwerk-
            // Interceptor. Ohne zu warten ginge die erste Anfrage an die Standard-Adresse
            // statt an die eingestellte — und damit ins Leere (v0.89.103).
            runCatching { withTimeoutOrNull(5_000) { settings.backendUrl.first() } }
            delay(START_DELAY_MS)
            for (name in FILES) prefs(name).registerOnSharedPreferenceChangeListener(listener)
            // Bis der Mac einmal erreicht wurde: immer wieder versuchen (30 s, 1, 2, 4 … max. 15 min).
            var wait = 30_000L
            while (true) {
                runCatching { restoreIfRicher() }.onFailure { Log.w(TAG, "Prüfung fehlgeschlagen", it) }
                if (runCatching { save() }.getOrDefault(false)) break
                delay(wait)
                wait = (wait * 2).coerceAtMost(15 * 60_000L)
            }
        }
    }

    private fun prefs(name: String) = context.getSharedPreferences(name, Context.MODE_PRIVATE)

    private fun scheduleSave() {
        if (!::scope.isInitialized) return
        // Gebündelt: läuft schon eine Sicherung an, nimmt sie diese Änderung mit.
        if (pending?.isActive == true) return
        pending = scope.launch {
            delay(SAVE_DELAY_MS)
            runCatching { save() }.onFailure { Log.w(TAG, "Sichern fehlgeschlagen", it) }
        }
    }

    // ── Momentaufnahme ───────────────────────────────────────────────────────

    /** Alle Russisch-Einstellungen mit Typ: { datei: { schlüssel: {t, v} } }. */
    fun snapshot(): JSONObject {
        val files = JSONObject()
        for (name in FILES) {
            val entries = JSONObject()
            for ((k, v) in prefs(name).all) {
                val (t, value) = when (v) {
                    is String -> "s" to v
                    is Long -> "l" to v
                    is Int -> "i" to v
                    is Float -> "f" to v.toDouble()
                    is Boolean -> "b" to v
                    else -> continue // Set<String> nutzt hier niemand
                }
                entries.put(k, JSONObject().put("t", t).put("v", value))
            }
            files.put(name, entries)
        }
        return files
    }

    /** Spielt eine Momentaufnahme zurück — ersetzt die Russisch-Dateien vollständig. */
    private fun apply(files: JSONObject) {
        for (name in FILES) {
            val entries = files.optJSONObject(name) ?: continue
            val edit = prefs(name).edit().clear()
            for (k in entries.keys()) {
                val e = entries.optJSONObject(k) ?: continue
                when (e.optString("t")) {
                    "s" -> edit.putString(k, e.optString("v"))
                    "l" -> edit.putLong(k, e.optLong("v"))
                    "i" -> edit.putInt(k, e.optInt("v"))
                    "f" -> edit.putFloat(k, e.optDouble("v").toFloat())
                    "b" -> edit.putBoolean(k, e.optBoolean("v"))
                }
            }
            edit.commit()
        }
        progress.reload()
    }

    private fun scoreOf(files: JSONObject?): Int {
        val raw = files?.optJSONObject(MAIN)?.optJSONObject(PROGRESS_KEY)?.optString("v")
        val p = raw?.takeIf { it.isNotEmpty() }?.let(progress::decode) ?: return 0
        return RuProgressGuard.score(p)
    }

    // ── Sichern ──────────────────────────────────────────────────────────────

    /** true = der Mac hat geantwortet (angenommen oder abgelehnt). */
    private suspend fun save(): Boolean = mutex.withLock {
        val files = snapshot()
        val score = scoreOf(files)
        val body = JSONObject().put("savedAt", System.currentTimeMillis()).put("score", score).put("files", files).toString()

        // Lokal: Tagesstand immer, besten Stand nur, wenn er nicht ärmer ist.
        writeAtomic(File(daysDir, "${LocalDate.now()}.json"), body)
        if (score >= readScore(bestFile)) writeAtomic(bestFile, body)
        daysDir.listFiles()?.sortedBy { it.name }?.dropLast(KEEP_LOCAL_DAYS)?.forEach { it.delete() }

        // Mac: der Server nimmt nur gleich gute oder bessere Stände an.
        val payload = JSONObject().put("files", files).put("appVersion", BuildConfig.VERSION_NAME).toString()
        val res = withTimeoutOrNull(NET_TIMEOUT_MS) {
            runCatching { api.putRussianBackup(SLOT, payload.toRequestBody(JSON)) }.getOrNull()
        }
        when (res?.code()) {
            null -> Log.i(TAG, "Mac nicht erreichbar — lokal gesichert (Score $score)")
            in 200..299 -> Log.i(TAG, "Gesichert auf dem Mac (Score $score)")
            409 -> {
                // Der Mac hat mehr als das Gerät — das sollte nie passieren. Zurückholen.
                Log.w(TAG, "Mac hat einen reicheren Stand als das Gerät (Score $score) — hole ihn zurück")
                restoreIfRicherLocked()
            }
            else -> Log.w(TAG, "Sichern auf dem Mac: HTTP ${res.code()}")
        }
        res?.body()?.close()
        res != null
    }

    // ── Zurückholen ──────────────────────────────────────────────────────────

    private suspend fun restoreIfRicher() = mutex.withLock { restoreIfRicherLocked() }

    private suspend fun restoreIfRicherLocked() {
        val localScore = scoreOf(snapshot())
        val candidates = buildList {
            readJson(bestFile)?.let { add("Gerät (best.json)" to it) }
            fetchServer()?.let { add("Mac" to it) }
        }
        val best = candidates.maxByOrNull { scoreOf(it.second.optJSONObject("files")) } ?: return
        val bestScore = scoreOf(best.second.optJSONObject("files"))
        if (bestScore <= localScore) return
        Log.w(TAG, "Lernstand auf dem Gerät (Score $localScore) ärmer als Sicherung ${best.first} (Score $bestScore) — wird zurückgespielt")
        // Den jetzigen Stand nicht wegwerfen, sondern daneben legen.
        writeAtomic(File(dir, "before-restore-${System.currentTimeMillis()}.json"), JSONObject().put("files", snapshot()).toString())
        dir.listFiles { f -> f.name.startsWith("before-restore-") }?.sortedBy { it.name }?.dropLast(10)?.forEach { it.delete() }
        apply(mergeForRestore(best.second.getJSONObject("files"), snapshot()))
    }

    /**
     * Sicherung + aktueller Gerätestand. Die Sicherung gewinnt (der Gerätestand gilt ja als
     * verloren), aber der Lernstand selbst wird zusammengeführt — was seit dem Verlust schon
     * wieder gelernt wurde, bleibt erhalten. Schlüssel, die nur das Gerät hat, bleiben auch.
     */
    private fun mergeForRestore(backup: JSONObject, local: JSONObject): JSONObject {
        val out = JSONObject()
        for (name in FILES) {
            val merged = JSONObject()
            local.optJSONObject(name)?.let { l -> for (k in l.keys()) merged.put(k, l.get(k)) }
            backup.optJSONObject(name)?.let { b -> for (k in b.keys()) merged.put(k, b.get(k)) }
            if (name == MAIN) {
                val fromBackup = backup.optJSONObject(name)?.optJSONObject(PROGRESS_KEY)?.optString("v")?.let(progress::decode)
                val fromLocal = local.optJSONObject(name)?.optJSONObject(PROGRESS_KEY)?.optString("v")?.let(progress::decode)
                if (fromBackup != null && fromLocal != null) {
                    val p = RuProgressGuard.merge(fromBackup, fromLocal)
                    merged.put(PROGRESS_KEY, JSONObject().put("t", "s").put("v", progress.encode(p)))
                }
            }
            out.put(name, merged)
        }
        return out
    }

    private suspend fun fetchServer(): JSONObject? = withTimeoutOrNull(NET_TIMEOUT_MS) {
        runCatching {
            val res = api.russianBackup(SLOT)
            res.body().use { body -> if (res.isSuccessful) body?.string()?.let(::JSONObject) else null }
        }.getOrNull()
    }

    private fun readJson(f: File): JSONObject? = runCatching { if (f.exists()) JSONObject(f.readText()) else null }.getOrNull()
    private fun readScore(f: File): Int = readJson(f)?.optInt("score", 0) ?: 0

    private fun writeAtomic(f: File, text: String) {
        val tmp = File(f.parentFile, "${f.name}.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(f)) { f.delete(); tmp.renameTo(f) }
    }

    companion object {
        private const val TAG = "RussianBackup"
        private const val MAIN = "hikari_russian"
        private const val PROGRESS_KEY = "progress_v1"
        val FILES = listOf(MAIN, "hikari_russian_conversation")
        private const val SAVE_DELAY_MS = 4_000L
        private const val START_DELAY_MS = 2_000L
        private const val NET_TIMEOUT_MS = 8_000L
        private const val KEEP_LOCAL_DAYS = 30
        private val JSON = "application/json".toMediaType()

        /** Test-Builds (Emulatoren am echten Mac) haben ein eigenes Fach — nie in der echten Sicherung. */
        private val SLOT = if (BuildConfig.DEBUG) "debug" else "main"
    }
}
