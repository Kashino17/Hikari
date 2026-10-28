package com.hikari.app.domain.whatsapp

import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

data class ParticipantStat(
    val name: String,
    val messageCount: Int,
    val wordCount: Int,
    val mediaCount: Int,
    val sharePercent: Float,
    val avgWordsPerMessage: Float,
)

data class WhatsAppChatAnalysis(
    val totalMessages: Int,
    val totalWords: Int,
    val totalMedia: Int,
    val activeDaysCount: Int,
    val participants: List<ParticipantStat>,
    /** 24 Einträge (Stunde 0..23) mit der Nachrichtenanzahl pro Tagesstunde. */
    val hourlyDistribution: List<Int>,
    val peakHour: Int,
    val topWords: List<Pair<String, Int>>,
    val topEmojis: List<Pair<String, Int>>,
)

/**
 * Analysiert exportierte WhatsApp-Chatverläufe (`.txt` oder `.zip` aus WhatsApps
 * „Chat exportieren“-Funktion) komplett lokal auf dem Gerät („WhatsApp Wrapped“).
 */
object WhatsAppChatAnalyzer {

    // Unterstützt sowohl "28.10.26, 19:42 - Name: Text" als auch "[28.10.26, 19:42:10] Name: Text"
    private val LINE_REGEX = Regex(
        """^\[?(\d{1,2}[./]\d{1,2}[./]\d{2,4}),?\s+(\d{1,2}):(\d{2})(?::\d{2})?(?:\s*(AM|PM|am|pm))?\]?\s*(?:-\s*)?([^:]+):\s*(.*)$""",
    )

    private val STOP_WORDS = setOf(
        "und", "ich", "die", "der", "das", "in", "zu", "den", "mit", "nicht", "von", "auf",
        "ist", "es", "ein", "eine", "für", "auch", "als", "an", "nach", "wie", "im", "für",
        "du", "wir", "mir", "mich", "dir", "dich", "aber", "oder", "wenn", "nur", "noch",
        "schon", "mal", "dann", "ja", "nein", "hab", "habe", "hat", "sind", "war", "wird",
        "kann", "was", "so", "da", "hier", "heute", "morgen", "gut", "alles", "über", "bei",
        "the", "and", "to", "of", "in", "is", "it", "for", "that", "you", "with", "on",
        "medien", "ausgeschlossen", "media", "omitted", "bild", "weggelassen", "audio",
    )

    /**
     * Liest entweder eine `.txt`- oder eine `.zip`-Datei (extrahiert die erste `.txt` darin)
     * und berechnet die Statistik.
     */
    fun analyzeStream(inputStream: InputStream, fileName: String? = null): WhatsAppChatAnalysis? {
        val rawBytes = inputStream.readBytes()
        if (rawBytes.isEmpty()) return null

        val isZip = fileName?.lowercase()?.endsWith(".zip") == true ||
            (rawBytes.size >= 4 && rawBytes[0] == 0x50.toByte() && rawBytes[1] == 0x4B.toByte())

        val textContent = if (isZip) {
            extractTxtFromZip(rawBytes) ?: return null
        } else {
            rawBytes.toString(Charsets.UTF_8)
        }

        return analyzeText(textContent)
    }

    fun analyzeText(rawText: String): WhatsAppChatAnalysis? {
        if (rawText.isBlank()) return null

        val msgCounts = mutableMapOf<String, Int>()
        val wordCounts = mutableMapOf<String, Int>()
        val mediaCounts = mutableMapOf<String, Int>()
        val activeDates = mutableSetOf<String>()
        val hourly = IntArray(24)
        val wordFreq = mutableMapOf<String, Int>()
        val emojiFreq = mutableMapOf<String, Int>()

        var totalMessages = 0
        var totalWords = 0
        var totalMedia = 0

        for (rawLine in rawText.lineSequence()) {
            val line = rawLine.trim().removePrefix("\uFEFF")
            if (line.isEmpty()) continue

            val match = LINE_REGEX.matchEntire(line) ?: continue
            val dateStr = match.groupValues[1]
            val hourRaw = match.groupValues[2].toIntOrNull() ?: 0
            val amPm = match.groupValues[4].uppercase()
            val sender = match.groupValues[5].trim()
            val body = match.groupValues[6].trim()

            // Systemzeilen ohne echten Absender ignorieren
            if (sender.length > 45 || body.contains("Ende-zu-Ende-verschlüsselt", ignoreCase = true)) {
                continue
            }

            val hour24 = when {
                amPm == "PM" && hourRaw in 1..11 -> hourRaw + 12
                amPm == "AM" && hourRaw == 12 -> 0
                else -> hourRaw.coerceIn(0, 23)
            }

            totalMessages++
            activeDates.add(dateStr)
            hourly[hour24]++
            msgCounts[sender] = (msgCounts[sender] ?: 0) + 1

            val isMediaOmitted = body.contains("<Medien ausgeschlossen>", ignoreCase = true) ||
                body.contains("<Media omitted>", ignoreCase = true) ||
                body.contains("Bild weggelassen", ignoreCase = true) ||
                body.contains("Audio weggelassen", ignoreCase = true)

            if (isMediaOmitted) {
                totalMedia++
                mediaCounts[sender] = (mediaCounts[sender] ?: 0) + 1
                continue
            }

            // Emojis zählen
            extractEmojis(body).forEach { emoji ->
                emojiFreq[emoji] = (emojiFreq[emoji] ?: 0) + 1
            }

            // Wörter zählen
            val words = body
                .lowercase()
                .split(Regex("""[^\p{L}\p{N}]+"""))
                .filter { it.length >= 2 }

            totalWords += words.size
            wordCounts[sender] = (wordCounts[sender] ?: 0) + words.size

            for (w in words) {
                if (w.length >= 3 && w !in STOP_WORDS && !w.all { it.isDigit() }) {
                    wordFreq[w] = (wordFreq[w] ?: 0) + 1
                }
            }
        }

        if (totalMessages == 0) return null

        val participants = msgCounts.entries
            .sortedByDescending { it.value }
            .map { (name, count) ->
                val wCount = wordCounts[name] ?: 0
                val mCount = mediaCounts[name] ?: 0
                ParticipantStat(
                    name = name,
                    messageCount = count,
                    wordCount = wCount,
                    mediaCount = mCount,
                    sharePercent = (count.toFloat() / totalMessages.toFloat()) * 100f,
                    avgWordsPerMessage = if (count > 0) wCount.toFloat() / count.toFloat() else 0f,
                )
            }

        val peakHour = hourly.indices.maxByOrNull { hourly[it] } ?: 12
        val topWords = wordFreq.entries
            .sortedByDescending { it.value }
            .take(8)
            .map { it.key to it.value }
        val topEmojis = emojiFreq.entries
            .sortedByDescending { it.value }
            .take(8)
            .map { it.key to it.value }

        return WhatsAppChatAnalysis(
            totalMessages = totalMessages,
            totalWords = totalWords,
            totalMedia = totalMedia,
            activeDaysCount = activeDates.size,
            participants = participants,
            hourlyDistribution = hourly.toList(),
            peakHour = peakHour,
            topWords = topWords,
            topEmojis = topEmojis,
        )
    }

    private fun extractTxtFromZip(zipBytes: ByteArray): String? {
        return runCatching {
            ZipInputStream(ByteArrayInputStream(zipBytes)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory && entry.name.lowercase().endsWith(".txt")) {
                        return zis.readBytes().toString(Charsets.UTF_8)
                    }
                    entry = zis.nextEntry
                }
                null
            }
        }.getOrNull()
    }

    private fun extractEmojis(text: String): List<String> {
        val result = mutableListOf<String>()
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val charCount = Character.charCount(cp)
            val isEmoji = (cp in 0x1F300..0x1FAFF) || (cp in 0x2600..0x27BF)
            if (isEmoji) {
                result.add(String(Character.toChars(cp)))
            }
            i += charCount
        }
        return result
    }

    fun buildSampleExportText(): String =
        """
        24.10.26, 18:15 - Leon: Hey! Hast du heute Abend schon was vor? 🔥
        24.10.26, 18:17 - Sarah: Noch nichts Geplantes, lass uns gerne ins Kino oder Pizza essen gehen 🍕😂
        24.10.26, 18:19 - Leon: Pizza klingt mega! Lass um 20 Uhr bei Luigi treffen 🍕🔥
        24.10.26, 19:45 - Sarah: <Medien ausgeschlossen>
        24.10.26, 19:46 - Sarah: Bin schon fast unterwegs, bringe noch Max mit 🙌
        24.10.26, 20:02 - Max: Bin da! Wo seid ihr? 😂
        25.10.26, 11:30 - Leon: Der Abend gestern war echt legendär 🔥🙌
        25.10.26, 11:35 - Sarah: Auf jeden Fall, müssen wir nächste Woche wiederholen ❤️🍕
        25.10.26, 21:10 - Leon: <Medien ausgeschlossen>
        25.10.26, 21:14 - Max: Schickt mal die Fotos von gestern in die Gruppe 📸🔥
        """.trimIndent()
}
