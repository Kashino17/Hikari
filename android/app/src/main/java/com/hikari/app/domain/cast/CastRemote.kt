package com.hikari.app.domain.cast

/** Was auf dem Fernseher läuft: ein Video (Serie, Film) oder ein Lied. */
enum class CastKind { VIDEO, MUSIC }

/** Das, was gerade an den Fernseher geschickt wurde — Grundlage für Leiste und Steuerung. */
data class CastMedia(
    val kind: CastKind,
    val id: String,
    val title: String,
    /** Serie (bei Folgen) bzw. Interpret (bei Liedern); sonst der Kanal oder nichts. */
    val owner: String? = null,
    /** »S3 · F10« bei Folgen. */
    val episode: String? = null,
    val thumbnailUrl: String? = null,
)

/** Reine Logik der Cast-Steuerung — ohne Android, damit sie sich prüfen lässt. */
object CastRemote {
    /** Schritte der vier Sprung-Knöpfe unter der Steuerung (nur Videos). */
    val JUMPS_MS = listOf(-300_000L, -60_000L, 60_000L, 300_000L)
    const val STEP_MS = 10_000L

    /**
     * Mindestabstand zwischen zwei Spulbefehlen an den Fernseher. Jeder Befehl lässt ihn neu
     * puffern; wer fünfmal schnell »+10 s« tippt, soll einen Sprung um 50 s auslösen und nicht
     * fünf Pufferpausen. Der erste Tipp geht sofort raus, weitere werden bis hierhin gesammelt.
     */
    const val SEEK_GAP_MS = 450L

    /** Wie lange der nächste Spulbefehl noch warten muss (0 = sofort senden). */
    fun seekDelay(nowMs: Long, lastSentMs: Long): Long =
        (lastSentMs + SEEK_GAP_MS - nowMs).coerceIn(0L, SEEK_GAP_MS)

    /** Ab hier springt »zurück« an den Liedanfang statt zum vorigen Lied (wie im Musikplayer). */
    const val RESTART_AFTER_MS = 3_000L

    fun jumpLabel(deltaMs: Long): String {
        val minutes = kotlin.math.abs(deltaMs) / 60_000L
        return (if (deltaMs < 0) "− " else "+ ") + "$minutes min"
    }

    fun episodeLabel(season: Int?, episode: Int?): String? = when {
        season != null && episode != null -> "S$season · F$episode"
        episode != null -> "Folge $episode"
        else -> null
    }

    /** Große Zeile der Leiste: bei Folgen die Serie, sonst der Titel selbst. */
    fun barTitle(media: CastMedia): String =
        if (media.kind == CastKind.VIDEO && media.episode != null && !media.owner.isNullOrBlank()) media.owner
        else media.title.ifBlank { "Wird geladen …" }

    /** Kleine Zeile der Leiste: Folge bzw. Interpret, dahinter das Gerät. */
    fun barLine(media: CastMedia, deviceName: String?): String {
        val first = when (media.kind) {
            CastKind.MUSIC -> media.owner
            CastKind.VIDEO -> media.episode
        }
        return listOfNotNull(first?.takeIf { it.isNotBlank() }, deviceName?.takeIf { it.isNotBlank() } ?: "Fernseher")
            .joinToString(" · ")
    }

    /** Kleine Zeile über dem Titel in der Steuerung: »Serie · S3 · F10« bzw. der Interpret. */
    fun remoteLine(media: CastMedia): String? =
        listOfNotNull(media.owner?.takeIf { it.isNotBlank() }, media.episode).joinToString(" · ").ifBlank { null }

    /** Ziel eines Sprungs — nie vor den Anfang, nie hinter das Ende (wenn die Länge bekannt ist). */
    fun seekTarget(positionMs: Long, deltaMs: Long, durationMs: Long): Long {
        val target = (positionMs + deltaMs).coerceAtLeast(0L)
        return if (durationMs > 0L) target.coerceAtMost(durationMs) else target
    }

    /** Anteil für den Fortschrittsbalken, 0 solange die Länge unbekannt ist. */
    fun progress(positionMs: Long, durationMs: Long): Float =
        if (durationMs <= 0L) 0f else (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)

    fun clock(ms: Long): String {
        val total = ms.coerceAtLeast(0L) / 1000L
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }

    /** Rechte Zeitangabe: »noch 34:31«, oder leer, solange die Länge fehlt. */
    fun remaining(positionMs: Long, durationMs: Long): String =
        if (durationMs <= 0L) "" else "noch " + clock(durationMs - positionMs)

    /** Stelle, an der ein Titel wieder einsetzt — ein (fast) zu Ende gesehener beginnt von vorn. */
    fun resumePoint(positionMs: Long, durationMs: Long): Long =
        if (durationMs > 0L && positionMs >= durationMs - 5_000L) 0L else positionMs.coerceAtLeast(0L)

    /** Private LAN-Adresse (oder .local)? Nur dann erreicht ein Fernseher den Server direkt. */
    fun isLanHost(host: String): Boolean {
        if (host.endsWith(".local")) return true
        val p = host.split(".").mapNotNull { it.toIntOrNull() }
        if (p.size != 4) return false
        return p[0] == 10 || (p[0] == 192 && p[1] == 168) || (p[0] == 172 && p[1] in 16..31)
    }

    /**
     * Liest aus der Adresse, die der Fernseher abspielt, was dort läuft. Daran erkennt die App
     * nach einem Neustart eine laufende Übertragung wieder.
     */
    fun parseStreamUrl(url: String): Pair<CastKind, String>? {
        val path = url.substringBefore('?').substringBefore('#')
        val kind = when {
            "/stream/video/" in path -> CastKind.VIDEO
            "/music/audio/" in path -> CastKind.MUSIC
            else -> return null
        }
        val id = path.substringAfterLast('/')
        return if (id.isBlank()) null else kind to id
    }
}
