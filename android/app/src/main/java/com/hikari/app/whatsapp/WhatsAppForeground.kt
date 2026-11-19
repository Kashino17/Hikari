package com.hikari.app.whatsapp

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process

/**
 * War WhatsApp offen, als eine Benachrichtigung verschwand?
 *
 * Gemessen 20.11.: Steht nur eine Nachricht in der Benachrichtigung und wird sie „für alle“
 * gelöscht, entfernt WhatsApp die Benachrichtigung genauso wie beim Lesen — kein Platzhalter,
 * kein anderes Muster (die Sammel-Benachrichtigung kam 2:2 zufällig). Der einzige Unterschied:
 * Lesen am Handy heißt, WhatsApp ist im Vordergrund. Kadir nutzt kein WhatsApp Web, Lesen
 * woanders fällt also weg. Braucht den Nutzungsdatenzugriff (einmalig in den Einstellungen).
 */
object WhatsAppForeground {

    fun hasAccess(context: Context): Boolean = runCatching {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        ops.unsafeCheckOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName,
        ) == AppOpsManager.MODE_ALLOWED
    }.getOrDefault(false)

    /**
     * true = WhatsApp war rund um [at] im Vordergrund (oder gerade erst verlassen),
     * false = sicher nicht, null = unbekannt (kein Zugriff / Abfrage fehlgeschlagen).
     */
    fun wasOpenAround(context: Context, packageName: String, at: Long): Boolean? {
        if (!hasAccess(context)) return null
        return runCatching {
            val usm = context.getSystemService(UsageStatsManager::class.java) ?: return null
            val events = usm.queryEvents(at - LOOKBACK_MS, at + AFTER_MS)
            val e = UsageEvents.Event()
            var lastResume = Long.MIN_VALUE
            var lastPause = Long.MIN_VALUE
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                if (e.packageName != packageName) continue
                when (e.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> lastResume = maxOf(lastResume, e.timeStamp)
                    UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED ->
                        lastPause = maxOf(lastPause, e.timeStamp)
                }
            }
            // Noch offen, gerade geöffnet oder erst vor Kurzem verlassen — im Zweifel „offen“,
            // denn eine verpasste Löschung ist besser als eine erfundene.
            lastResume > lastPause || lastPause >= at - RECENTLY_LEFT_MS
        }.getOrNull()
    }

    private const val LOOKBACK_MS = 6L * 60L * 60L * 1000L
    private const val AFTER_MS = 1_500L
    private const val RECENTLY_LEFT_MS = 3_000L
}
