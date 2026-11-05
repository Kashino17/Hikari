package com.hikari.app.domain.russian

/**
 * Reine Regeln des Podcast-Players: wo eine Folge weiterläuft, ab wann sie
 * als gehört zählt, welche Tempi es gibt. Ohne Android, damit testbar.
 */
object RuPodcastPlayback {
    val SPEEDS = listOf(0.75f, 0.9f, 1f, 1.25f, 1.5f)

    /** Kürzer gehört als das zählt nicht als angefangene Folge. */
    private const val MIN_RESUME_MS = 5_000L
    /** Beim Fortsetzen ein Stück zurück, damit man wieder in den Satz findet. */
    private const val RESUME_REWIND_MS = 3_000L
    const val LISTENED_FRACTION = 0.9

    /**
     * Startposition beim Öffnen einer Folge. 0, wenn kaum angefangen oder
     * (fast) zu Ende gehört — dann beginnt sie von vorn.
     */
    fun resumeStartMs(savedMs: Long, durationMs: Long): Long {
        if (savedMs < MIN_RESUME_MS) return 0
        if (durationMs > 0 && savedMs >= durationMs - MIN_RESUME_MS) return 0
        return (savedMs - RESUME_REWIND_MS).coerceAtLeast(0)
    }

    fun isListened(positionMs: Long, durationMs: Long): Boolean =
        durationMs > 0 && positionMs >= durationMs * LISTENED_FRACTION

    fun nextSpeed(current: Float): Float {
        val i = SPEEDS.indexOfFirst { kotlin.math.abs(it - current) < 0.001f }
        return if (i < 0) 1f else SPEEDS[(i + 1) % SPEEDS.size]
    }

    /** 1,0× / 0,75× — deutsches Komma, mindestens eine Nachkommastelle. */
    fun speedLabel(speed: Float): String {
        val s = if (kotlin.math.abs(speed * 10 - Math.round(speed * 10)) < 0.001f) {
            String.format(java.util.Locale.ROOT, "%.1f", speed)
        } else {
            String.format(java.util.Locale.ROOT, "%.2f", speed)
        }
        return s.replace('.', ',') + "×"
    }

    /** m:ss bzw. h:mm:ss. */
    fun formatTime(ms: Long): String {
        val total = (ms.coerceAtLeast(0) / 1000)
        val h = total / 3600
        val m = (total % 3600) / 60
        val s = total % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
    }
}
