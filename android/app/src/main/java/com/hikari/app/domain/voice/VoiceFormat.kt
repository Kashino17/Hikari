package com.hikari.app.domain.voice

import com.hikari.app.data.api.dto.VoiceSummaryDto
import kotlin.math.roundToInt

/** Reine Text-Regeln für Sprachnachrichten — ohne Android, damit testbar. */
object VoiceFormat {

    fun isFinished(status: String): Boolean = status == "done" || status == "failed"

    /** Kurze Statuszeile für Liste und Fortschritts-Benachrichtigung. */
    fun statusLine(status: String, done: Int, total: Int, queuePosition: Int = 0): String = when (status) {
        "queued" -> if (queuePosition > 0) "Wartet ($queuePosition vor dir)" else "Wartet auf Whisper"
        "transcribing" ->
            if (total > 1) "Transkribiere ${minOf(done + 1, total)} von $total" else "Transkribiere"
        "summarizing" -> "Qwen fasst zusammen"
        "done" -> "Fertig"
        "failed" -> "Fehlgeschlagen"
        else -> status
    }

    /** 0..1 für Fortschrittsbalken; Zusammenfassen zählt als letztes Fünftel. */
    fun progress(status: String, done: Int, total: Int): Float = when (status) {
        "queued" -> 0f
        "transcribing" -> if (total <= 0) 0f else 0.8f * done / total
        "summarizing" -> 0.85f
        "done", "failed" -> 1f
        else -> 0f
    }

    /** 75.4 → "1:15", 3725 → "1:02:05". */
    fun duration(seconds: Double?): String {
        if (seconds == null || seconds < 0) return ""
        val s = seconds.roundToInt()
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
    }

    /** "3 Sprachnachrichten · 4:12" */
    fun meta(total: Int, seconds: Double?): String {
        val count = if (total == 1) "1 Sprachnachricht" else "$total Sprachnachrichten"
        val d = duration(seconds)
        return if (d.isEmpty()) count else "$count · $d"
    }

    /** Fehlertexte des Servers in etwas Verständliches übersetzen. */
    fun errorText(raw: String?): String {
        val e = raw.orEmpty()
        return when {
            e.isBlank() -> "Unbekannter Fehler"
            e.startsWith("lmstudio_") || e.contains("ECONNREFUSED") || e.contains("fetch failed") ->
                "LM Studio war nicht erreichbar. Läuft es auf dem Mac?"
            e.startsWith("whisper_") || e.contains("Error:") && e.contains("ffmpeg", ignoreCase = true) ->
                "Whisper konnte die Aufnahme nicht lesen."
            else -> e
        }
    }

    /** Die Zusammenfassung als Klartext zum Kopieren oder Weiterleiten. */
    fun asText(summary: VoiceSummaryDto, label: String? = null): String = buildString {
        append(summary.title)
        if (!label.isNullOrBlank()) append(" (").append(label).append(")")
        append("\n\n").append(summary.summary.trim())
        section("Aufgaben", summary.todos)
        section("Termine und Zahlen", summary.dates)
        section("Offene Fragen", summary.questions)
        section("Kernpunkte", summary.points)
    }

    /** Text für die aufgeklappte Benachrichtigung: kurz halten, Aufgaben zuerst. */
    fun notificationText(summary: VoiceSummaryDto): String = buildString {
        append(summary.summary.trim())
        section("Aufgaben", summary.todos)
        section("Offene Fragen", summary.questions)
    }

    private fun StringBuilder.section(title: String, items: List<String>) {
        if (items.isEmpty()) return
        append("\n\n").append(title).append(":")
        for (item in items) append("\n• ").append(item.trim())
    }

    /**
     * Anzeigename einer geteilten Datei säubern. WhatsApp liefert z. B.
     * "PTT-20261106-WA0012.opus"; Pfadteile und Steuerzeichen fliegen raus.
     */
    fun safeFileName(raw: String?, index: Int): String {
        val base = raw?.substringAfterLast('/')?.substringAfterLast('\\')
            ?.replace(Regex("[\\u0000-\\u001f\"<>|:*?]"), "")
            ?.trim()
            ?.take(120)
        return if (base.isNullOrBlank()) "Sprachnachricht-${index + 1}.audio" else base
    }
}
