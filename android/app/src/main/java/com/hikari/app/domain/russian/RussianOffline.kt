package com.hikari.app.domain.russian

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.time.Duration
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit

/**
 * Russisch für unterwegs (Bauplan 18.11.2026): Was der Russisch-Teil ohne Netz oder ohne Mac
 * braucht, liegt hier auf dem Gerät —
 *  - Stücke aus »Lieder & Szenen« (Video und Standbild) und die zuletzt gesehene Stückliste,
 *  - die Kursstimme für Sätze ohne fertige Aufnahme (eigene Sätze, Anjas Sätze, Lieder-Zeilen).
 *
 * Die Brücke der WebView (RussianWebClient) liefert diese Dateien aus, wenn sie da sind, und fällt
 * auf sie zurück, wenn der Server nicht erreichbar ist. Geladen wird nachts im WLAN beim Laden
 * (RussianOfflineWorker); Gesehenes verschwindet nach ein paar Tagen vom Handy (nicht vom Mac).
 */
object RussianOffline {
    private const val PREFS = "hikari_russian_offline"
    const val DEFAULT_LIMIT_MB = 2048

    fun dir(ctx: Context): File = File(ctx.filesDir, "russian_offline").apply { mkdirs() }
    private fun mediaDir(ctx: Context) = File(dir(ctx), "media").apply { mkdirs() }
    private fun voiceDir(ctx: Context) = File(dir(ctx), "voice").apply { mkdirs() }

    /** Dateiname eines Stücks (Video/Standbild) — nur einfache Namen, nie Pfade. */
    fun mediaFile(ctx: Context, name: String): File? =
        name.takeIf { it.matches(Regex("[A-Za-z0-9._-]{1,120}")) && ".." !in it }?.let { File(mediaDir(ctx), it) }

    fun feedFile(ctx: Context) = File(dir(ctx), "erleben_feed.json")

    /** Kursstimme für einen Satz; Schlüssel aus Stimme, Tempo und Text (unabhängig von der URL-Kodierung). */
    fun voiceFile(ctx: Context, voice: String, rate: String, text: String): File =
        File(voiceDir(ctx), sha1("$voice|${rate.toDoubleOrNull() ?: 1.0}|${text.trim()}") + ".ogg")

    fun mediaNames(ctx: Context): Set<String> = mediaDir(ctx).list()?.toSet() ?: emptySet()

    /** Die von Hand aufbereiteten Stücke (assets/russian/erleben.json) — ihre Videos liegen auf dem Mac. */
    fun handPieces(ctx: Context): List<org.json.JSONObject> = runCatching {
        val a = org.json.JSONObject(ctx.assets.open("russian/erleben.json").bufferedReader().use { it.readText() }).optJSONArray("pieces")
        (0 until (a?.length() ?: 0)).mapNotNull { a?.optJSONObject(it) }
    }.getOrDefault(emptyList())

    fun usedBytes(ctx: Context): Long =
        (mediaDir(ctx).listFiles().orEmpty().toList() + voiceDir(ctx).listFiles().orEmpty().toList()).sumOf { f -> f.length() }

    fun voiceCount(ctx: Context) = voiceDir(ctx).list()?.size ?: 0

    /** Atomar schreiben: erst in eine Nebendatei, dann umbenennen — nie eine halbe Datei ausliefern. */
    fun writeAtomic(target: File, bytes: ByteArray) {
        val tmp = File(target.parentFile, "${target.name}.part")
        tmp.writeBytes(bytes)
        if (!tmp.renameTo(target)) { target.delete(); tmp.renameTo(target) }
    }

    // ── Einstellungen (»Für unterwegs«) ─────────────────────────────────────

    data class Settings(val limitMb: Int, val wifiOnly: Boolean, val chargingOnly: Boolean, val deleteSeen: Boolean)

    fun settings(ctx: Context): Settings {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Settings(
            limitMb = p.getInt("limitMb", DEFAULT_LIMIT_MB),
            wifiOnly = p.getBoolean("wifiOnly", true),
            chargingOnly = p.getBoolean("chargingOnly", true),
            deleteSeen = p.getBoolean("deleteSeen", true),
        )
    }

    fun saveSettings(ctx: Context, json: String) {
        val o = runCatching { JSONObject(json) }.getOrNull() ?: return
        val old = settings(ctx)
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("limitMb", o.optInt("limitMb", old.limitMb).coerceIn(200, 64_000))
            .putBoolean("wifiOnly", o.optBoolean("wifiOnly", old.wifiOnly))
            .putBoolean("chargingOnly", o.optBoolean("chargingOnly", old.chargingOnly))
            .putBoolean("deleteSeen", o.optBoolean("deleteSeen", old.deleteSeen))
            .apply()
        schedule(ctx, replace = true)
    }

    fun noteRun(ctx: Context, pieces: Int, newPieces: Int, voices: Int) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong("lastRun", System.currentTimeMillis())
            .putInt("lastPieces", pieces)
            .putInt("lastNew", newPieces)
            .putInt("lastVoices", voices)
            .apply()
    }

    /** Für die Oberfläche: Einstellungen und Stand des letzten Ladens. */
    fun stateJson(ctx: Context): String {
        val s = settings(ctx)
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return JSONObject()
            .put("limitMb", s.limitMb).put("wifiOnly", s.wifiOnly).put("chargingOnly", s.chargingOnly).put("deleteSeen", s.deleteSeen)
            .put("usedBytes", usedBytes(ctx)).put("pieces", mediaNames(ctx).count { it.endsWith(".mp4") })
            .put("voices", voiceCount(ctx))
            .put("lastRun", p.getLong("lastRun", 0)).put("lastNew", p.getInt("lastNew", 0))
            .toString()
    }

    // ── Planung ──────────────────────────────────────────────────────────────

    private const val UNIQUE = "russian-offline-nightly"
    private const val NOW = "russian-offline-now"

    /** Einmal am Tag, nachts um drei — WLAN und Laden nach Einstellung. */
    fun schedule(ctx: Context, replace: Boolean = false) {
        val s = settings(ctx)
        val now = LocalDateTime.now()
        var at = now.toLocalDate().atTime(3, 0)
        if (!at.isAfter(now)) at = at.plusDays(1)
        val req = PeriodicWorkRequestBuilder<RussianOfflineWorker>(24, TimeUnit.HOURS, 3, TimeUnit.HOURS)
            .setInitialDelay(Duration.between(now, at).toMinutes(), TimeUnit.MINUTES)
            .setConstraints(constraints(s))
            .build()
        WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(
            UNIQUE,
            if (replace) ExistingPeriodicWorkPolicy.UPDATE else ExistingPeriodicWorkPolicy.KEEP,
            req,
        )
    }

    /** »Jetzt laden« aus den Einstellungen — mit Netz-Bedingung, aber ohne aufs Laden zu warten. */
    fun runNow(ctx: Context) {
        val s = settings(ctx)
        val req = OneTimeWorkRequestBuilder<RussianOfflineWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(if (s.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED).build())
            .build()
        WorkManager.getInstance(ctx).enqueueUniqueWork(NOW, ExistingWorkPolicy.REPLACE, req)
    }

    private fun constraints(s: Settings) = Constraints.Builder()
        .setRequiredNetworkType(if (s.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
        .setRequiresCharging(s.chargingOnly)
        .setRequiresStorageNotLow(true)
        .build()

    private fun sha1(s: String): String =
        MessageDigest.getInstance("SHA-1").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
