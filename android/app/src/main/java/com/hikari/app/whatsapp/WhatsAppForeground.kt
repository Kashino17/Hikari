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

    /** Zuletzt gesehene Vorder-/Hintergrundwechsel je Paket — einmal lang abfragen, dann nur Neues. */
    private class State(var queriedUntil: Long, var lastResume: Long, var lastPause: Long)
    private val states = HashMap<String, State>()

    /**
     * true = WhatsApp war rund um [at] im Vordergrund (oder gerade erst verlassen),
     * false = sicher nicht, null = unbekannt (kein Zugriff / Abfrage fehlgeschlagen).
     *
     * Gemessen 20.11.: Eine 6-Stunden-Abfrage dauerte am Handy teils 11 s — so lange, dass
     * inzwischen die nächste Nachricht kam. Deshalb wird nur der erste Aufruf lang abgefragt,
     * danach nur der Abschnitt seit dem letzten Mal.
     */
    @Synchronized
    fun wasOpenAround(context: Context, packageName: String, at: Long): Boolean? {
        if (!hasAccess(context)) return null
        return runCatching {
            val usm = context.getSystemService(UsageStatsManager::class.java) ?: return null
            val until = maxOf(at + AFTER_MS, System.currentTimeMillis())
            val state = states.getOrPut(packageName) { State(at - LOOKBACK_MS, Long.MIN_VALUE, Long.MIN_VALUE) }
            // Kleine Überlappung, falls Ereignisse leicht verspätet eingetragen werden.
            val events = usm.queryEvents(state.queriedUntil - OVERLAP_MS, until)
            val e = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(e)
                if (e.packageName != packageName) continue
                when (e.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> state.lastResume = maxOf(state.lastResume, e.timeStamp)
                    UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED ->
                        state.lastPause = maxOf(state.lastPause, e.timeStamp)
                }
            }
            state.queriedUntil = until
            // Noch offen, gerade geöffnet oder erst vor Kurzem verlassen — im Zweifel „offen“,
            // denn eine verpasste Löschung ist besser als eine erfundene.
            state.lastResume > state.lastPause || state.lastPause >= at - RECENTLY_LEFT_MS
        }.getOrNull()
    }

    /** Beim Start des Wächters einmal vorwärmen, damit die erste echte Prüfung schnell ist. */
    fun warmUp(context: Context, packageNames: List<String>) {
        val now = System.currentTimeMillis()
        packageNames.forEach { wasOpenAround(context, it, now) }
    }

    private const val LOOKBACK_MS = 6L * 60L * 60L * 1000L
    private const val AFTER_MS = 1_500L
    private const val RECENTLY_LEFT_MS = 3_000L
    private const val OVERLAP_MS = 10_000L
}
