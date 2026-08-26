package com.hikari.app.ui.imports

import com.hikari.app.data.api.dto.PendingImportDto
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

class ImportsFormattingTest {

    private fun item(
        title: String? = null,
        seriesTitle: String? = null,
        season: Int? = null,
        episode: Int? = null,
        status: String = "downloading",
        downloadedBytes: Double = 0.0,
        totalBytes: Double? = null,
        speedBps: Double? = null,
        etaSeconds: Double? = null,
        fragmentIndex: Int? = null,
        fragmentCount: Int? = null,
        progress: Float? = null,
    ) = PendingImportDto(
        id = "sniff_a",
        pageUrl = "https://serien.test/folge-1",
        title = title,
        seriesTitle = seriesTitle,
        season = season,
        episode = episode,
        status = status,
        downloadedBytes = downloadedBytes,
        totalBytes = totalBytes,
        speedBps = speedBps,
        etaSeconds = etaSeconds,
        fragmentIndex = fragmentIndex,
        fragmentCount = fragmentCount,
        progress = progress,
    )

    @Test
    fun titelGewinntVorSerieUndFolge() {
        assertEquals("Mein Titel", item(title = "Mein Titel", seriesTitle = "Serie").displayTitle())
    }

    @Test
    fun ohneTitelSerieUndFolgeKombinieren() {
        assertEquals("Solo Leveling — Folge 3", item(seriesTitle = "Solo Leveling", episode = 3).displayTitle())
    }

    // Ohne jede Angabe bleibt nur die Herkunftsseite — besser als eine leere
    // Zeile, an der man den Eintrag nicht wiedererkennt.
    @Test
    fun faelltAufDieSeitenadresseZurueck() {
        assertEquals("https://serien.test/folge-1", item().displayTitle())
    }

    @Test
    fun wartendeZeigenKeinenFortschritt() {
        assertEquals("Wartet auf Start", item(status = "queued").progressLine())
    }

    @Test
    fun fortschrittszeileFasstAllesBekannteZusammen() {
        val line = item(
            downloadedBytes = 104_857_600.0,
            totalBytes = 209_715_200.0,
            speedBps = 5_242_880.0,
            etaSeconds = 20.0,
            progress = 0.5f,
        ).progressLine()

        assertTrue(line.contains("50 %"), line)
        assertTrue(line.contains("100 MB von 200 MB"), line)
        assertTrue(line.contains("5 MB/s"), line)
        assertTrue(line.contains("noch 20 s"), line)
    }

    // Bei HLS ist die Gesamtgröße lange unbekannt; dann trägt die
    // Fragmentzählung die Information.
    @Test
    fun zeigtFragmenteWennDieGroesseFehlt() {
        val line = item(downloadedBytes = 1024.0, fragmentIndex = 12, fragmentCount = 300).progressLine()
        assertTrue(line.contains("Teil 12/300"), line)
        assertTrue(!line.contains("von"), line)
    }

    @Test
    fun byteGroessenLesbar() {
        assertEquals("512 B", formatBytes(512))
        assertEquals("2 KB", formatBytes(2048))
        assertEquals("100 MB", formatBytes(104_857_600))
        assertEquals("1,1 GB", formatBytes(1_181_116_006).replace('.', ','))
    }

    @Test
    fun restzeitLesbar() {
        assertEquals("45 s", formatDuration(45))
        assertEquals("3 min", formatDuration(200))
        assertEquals("1 h 5 min", formatDuration(3900))
    }

    @Test
    fun untertitelListetWasBekanntIst() {
        val s = item(seriesTitle = "Solo Leveling", season = 1, episode = 3)
            .copy(dubLanguage = "de")
            .subtitle()
        assertTrue(s.contains("Solo Leveling"), s)
        assertTrue(s.contains("Staffel 1"), s)
        assertTrue(s.contains("DE"), s)
    }

    // Der Server rundet inzwischen, aber die Anzeige muss auch mit einem
    // Komma zurechtkommen — genau daran scheiterte zuvor die gesamte
    // Downloadliste, weil das Parsen der Antwort abbrach.
    @Test
    fun vertraegtFliesskommawerteVomServer() {
        val line = item(
            downloadedBytes = 122_978_568.0,
            totalBytes = 239_690_509.7142857,
            speedBps = 2_011_712.0049568545,
            etaSeconds = 59.504575810593934,
            progress = 0.509f,
        ).progressLine()

        assertTrue(line.contains("50 %"), line)
        assertTrue(line.contains("117 MB"), line)
        assertTrue(line.contains("noch 59 s"), line)
    }
}
