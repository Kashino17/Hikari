package com.hikari.app.domain.russian

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import org.json.JSONObject

/**
 * Lernzeit für die Russisch-Statistik. Der Russisch-Bereich (Web-Oberfläche) führt sie unter
 * "study_time" als { Tag: { Modus: Sekunden } } — hier bucht die übrige App mit, damit auch
 * Übungen aus dem Feed in »Lernzeit« und im Tagesverlauf auftauchen.
 *
 * Die Web-Oberfläche liest den Stand beim Öffnen; solange sie offen ist, schreibt nur sie.
 * Der Feed ist in dieser Zeit nicht sichtbar, die beiden kommen sich also nicht in die Quere.
 */
@Singleton
class RussianStudyTime @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("hikari_russian", Context.MODE_PRIVATE)

    @Synchronized
    fun add(mode: String, seconds: Int) {
        if (seconds <= 0) return
        val all = runCatching { JSONObject(prefs.getString(KEY, null) ?: "{}") }.getOrDefault(JSONObject())
        val day = LocalDate.now().toEpochDay().toString()
        val today = all.optJSONObject(day) ?: JSONObject()
        today.put(mode, today.optInt(mode, 0) + seconds)
        all.put(day, today)
        prefs.edit().putString(KEY, all.toString()).apply()
    }

    /** Lernzeit von heute über alle Modi, in Sekunden — für die Kopfzeile im Profil. */
    fun todaySeconds(): Int = runCatching {
        val today = JSONObject(prefs.getString(KEY, null) ?: "{}")
            .optJSONObject(LocalDate.now().toEpochDay().toString()) ?: return 0
        today.keys().asSequence().sumOf { today.optInt(it, 0) }
    }.getOrDefault(0)

    /** Freie, klare Antworten im Gespräch mit Anja — zählt fürs Sprachniveau ab B1. */
    fun talkCount(): Int = runCatching {
        val observations = JSONObject(prefs.getString("conv_progress", null) ?: "{}").optJSONArray("observations")
            ?: return 0
        (0 until observations.length()).count { i ->
            val o = observations.getJSONObject(i)
            o.optBoolean("clear") && o.optBoolean("independent") && ':' !in o.optString("id")
        }
    }.getOrDefault(0)

    companion object {
        private const val KEY = "study_time"

        /** Modi des Feeds — Namen und Farbe stehen in assets/russian-web/js/timetrack.js. */
        const val FEED_PRACTICE = "feedPractice"
        const val FEED_ALPHABET = "feedAlphabet"
        const val FEED_PHRASES = "feedPhrases"
    }
}
