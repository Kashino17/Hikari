package com.hikari.app.domain.russian

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * Lädt nachts (WLAN, beim Laden — nach Einstellung) aufs Handy, was Russisch unterwegs braucht:
 *  1. die Stückliste von »Lieder & Szenen«,
 *  2. Stücke in dieser Reihenfolge, bis die Speichergrenze erreicht ist: gemerkte, angefangene,
 *     heute neue, dann die neuesten der Sammlung,
 *  3. die Kursstimme für eigene Sätze ohne fertige Aufnahme (auch Anjas und mitgenommene).
 * Gesehenes (fertig und eingeschätzt, älter als 7 Tage) und vom Server Gelöschtes verschwindet vom
 * Handy — gemerkte Stücke bleiben. Auf dem Mac bleibt die Sammlung immer vollständig.
 */
@HiltWorker
class RussianOfflineWorker @AssistedInject constructor(
    @Assisted ctx: Context,
    @Assisted params: WorkerParameters,
    private val okHttp: OkHttpClient,
) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val ctx = applicationContext
        val s = RussianOffline.settings(ctx)
        val feed = runCatching { get("/russian/erleben/feed") }.getOrNull()
            ?: return@withContext Result.retry() // Server nicht erreichbar — später noch einmal
        RussianOffline.writeAtomic(RussianOffline.feedFile(ctx), feed)
        val f = JSONObject(String(feed))
        val web = ctx.getSharedPreferences("hikari_russian", Context.MODE_PRIVATE)
        val erleben = runCatching { JSONObject(web.getString("erleben_v1", "{}") ?: "{}") }.getOrDefault(JSONObject())
        val states = erleben.optJSONObject("pieces") ?: JSONObject()
        val saved = f.optJSONArray("saved").strings().toSet()
        val today = f.optJSONArray("todayIds").strings()
        val pieces = f.optJSONArray("pieces") ?: JSONArray()
        // Auch die von Hand aufbereiteten Stücke (klein, immer mit dabei).
        val hand = RussianOffline.handPieces(ctx)
        val byId = ((0 until pieces.length()).mapNotNull { pieces.optJSONObject(it) } + hand).associateBy { it.optString("id") }
        val collection = f.optJSONArray("collection").let { a -> (0 until (a?.length() ?: 0)).mapNotNull { a?.optJSONObject(it)?.optString("id") } }

        // Gesehen: fertig, eingeschätzt und seit mindestens 7 Tagen nicht mehr angefasst.
        val epochDay = LocalDate.now().toEpochDay()
        fun seenLongAgo(id: String): Boolean {
            val st = states.optJSONObject(id) ?: return false
            return st.optBoolean("done") && st.has("ratedAt") && epochDay - st.optLong("ratedAt") >= SEEN_DAYS
        }
        fun started(id: String) = states.optJSONObject(id)?.let { it.optBoolean("started") && !it.optBoolean("done") } == true

        val order = LinkedHashSet<String>().apply {
            addAll(hand.map { it.optString("id") })
            addAll(saved.filter { it in byId })
            addAll(byId.keys.filter { started(it) })
            addAll(today.filter { it in byId })
            addAll(collection.filter { it in byId })
        }.filter { it in saved || !(s.deleteSeen && seenLongAgo(it)) }

        // Aufräumen: was der Server nicht mehr hat, und Gesehenes (außer Gemerktem).
        val keepNames = order.flatMap { listOfNotNull(byId[it]?.optString("media"), byId[it]?.optString("poster")) }.toSet()
        for (name in RussianOffline.mediaNames(ctx)) {
            if (name.endsWith(".part") || name !in keepNames) RussianOffline.mediaFile(ctx, name)?.delete()
        }

        // Laden, bis die Grenze erreicht ist.
        val limit = s.limitMb.toLong() * 1024 * 1024
        var fresh = 0
        for (id in order) {
            if (isStopped) break
            val p = byId[id] ?: continue
            if (RussianOffline.usedBytes(ctx) >= limit) break
            for (name in listOf(p.optString("poster"), p.optString("media")).filter { it.isNotBlank() }) {
                val file = RussianOffline.mediaFile(ctx, name) ?: continue
                if (file.exists()) continue
                val bytes = runCatching { get("/russian/erleben/media/$name") }.getOrNull() ?: continue
                RussianOffline.writeAtomic(file, bytes)
                if (name.endsWith(".mp4")) fresh++
            }
        }

        // Kursstimme für Sätze ohne fertige Aufnahme.
        val voices = loadVoices(ctx, web)
        RussianOffline.noteRun(ctx, order.size, fresh, voices)
        Log.i(TAG, "Russisch offline: $fresh neue Stücke, $voices Sätze in Kursstimme, ${RussianOffline.usedBytes(ctx) / 1_048_576} MB")
        Result.success()
    }

    /**
     * Eigene Sätze — dazu gehören Anjas Verbesserungen und die aus »Lieder & Szenen« mitgenommenen —
     * in der Kursstimme. (Zeilen der Stücke selbst spielen ihren Originalton aus dem Video.)
     */
    private fun loadVoices(ctx: Context, web: android.content.SharedPreferences): Int {
        val progress = runCatching { JSONObject(web.getString("progress_v1", "{}") ?: "{}") }.getOrDefault(JSONObject())
        val voice = if ((progress.optString("voice").ifBlank { progress.optString("gender") }) == "FEMALE") "f" else "m"
        // [stimme, tempo, text]: eigene Sätze normal und langsam, dazu die Vormerkliste der Oberfläche
        // (jedes Wort der Stücke aus »Lieder & Szenen«, langsam — so, wie »Deutlich« es abspielt).
        val wanted = LinkedHashSet<Triple<String, String, String>>()
        progress.optJSONArray("mySentences")?.let { a ->
            for (i in 0 until a.length()) a.optJSONObject(i)?.optString("ru")?.takeIf { it.isNotBlank() }?.let {
                wanted += Triple(voice, "1", it); wanted += Triple(voice, "0.7", it)
            }
        }
        runCatching { JSONArray(web.getString("voice_wish_v1", "[]") ?: "[]") }.getOrNull()?.let { a ->
            for (i in 0 until a.length()) {
                val parts = a.optString(i).split('|', limit = 3)
                if (parts.size == 3 && parts[2].isNotBlank()) wanted += Triple(parts[0], parts[1], parts[2])
            }
        }
        var n = 0
        for ((v, rate, text) in wanted.take(MAX_VOICES)) {
            if (isStopped) break
            val file = RussianOffline.voiceFile(ctx, v, rate, text)
            if (file.exists()) { n++; continue }
            val url = "http://hikari.invalid/russian/voice".toHttpUrl().newBuilder()
                .addQueryParameter("voice", v).addQueryParameter("rate", rate).addQueryParameter("text", text).build()
            val bytes = runCatching { okHttp.newCall(Request.Builder().url(url).build()).execute().use { r -> if (r.isSuccessful) r.body?.bytes() else null } }.getOrNull() ?: continue
            RussianOffline.writeAtomic(file, bytes)
            n++
        }
        return n
    }

    /** GET am Hikari-Server — der OkHttp-Client der App setzt die eingestellte Adresse ein. */
    private fun get(path: String): ByteArray? =
        okHttp.newCall(Request.Builder().url("http://hikari.invalid$path").build()).execute().use { r ->
            if (r.isSuccessful) r.body?.bytes() else null
        }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).takeIf(String::isNotBlank) }

    companion object {
        private const val TAG = "HikariRuOffline"
        private const val SEEN_DAYS = 7
        private const val MAX_VOICES = 8000
    }
}
