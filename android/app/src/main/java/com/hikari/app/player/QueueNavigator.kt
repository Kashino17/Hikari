package com.hikari.app.player

import com.hikari.app.domain.model.MusicSong
import kotlin.random.Random

/**
 * Reine Queue-Index-Logik des [MusicPlayerController] — ohne Player- und
 * Coroutine-Abhängigkeiten, damit sie als Unit-Test prüfbar ist. Der
 * Controller reicht seine Zustände (Queue, Index, Shuffle/Repeat, Planung)
 * herein und übernimmt das Ergebnis.
 */
internal object QueueNavigator {

    /** Rückgabe von [advanceIndex]: Queue-Ende erreicht, Autoplay-Nachschub nötig. */
    const val EXTEND = -1

    /**
     * Ziel-Index nach einem AUTO-Übergang des Players. Wahrheit ist die
     * mediaId, die der Player gerade spielt — ein mitten im Song umgeschaltetes
     * Shuffle/Repeat kann den Player in ein anderes Item geführt haben als
     * geplant. Nur wenn die mediaId nicht (oder nur als aktueller Song) in der
     * Queue liegt, gilt der bisherige Plan: [plannedNextIndex], sonst der
     * nächste Index, am Ende der Queue-Anfang (Repeat-All).
     */
    fun autoAdvanceIndex(
        queue: List<MusicSong>,
        currentIndex: Int,
        playingMediaId: String?,
        plannedNextIndex: Int?,
    ): Int {
        if (queue.isEmpty() || currentIndex !in queue.indices) return currentIndex
        playingMediaId?.let { id ->
            val byId = queue.indexOfFirst { it.videoId == id }
            if (byId >= 0 && byId != currentIndex) return byId
        }
        plannedNextIndex?.takeIf { it in queue.indices && it != currentIndex }?.let { return it }
        return if (currentIndex + 1 < queue.size) currentIndex + 1 else 0
    }

    /**
     * Ziel-Index für vor/zurück. Liefert [EXTEND], wenn es vorwärts über das
     * Queue-Ende hinausgeht und kein Repeat-All greift. Bei Shuffle wird ein
     * geplanter Index konsumiert statt neu zu würfeln, damit Prefetch und
     * Übergang denselben Song treffen; [roll] ist für Tests injizierbar.
     */
    fun advanceIndex(
        queueSize: Int,
        currentIndex: Int,
        forward: Boolean,
        shuffle: Boolean,
        repeatAll: Boolean,
        plannedNextIndex: Int?,
        roll: (IntRange) -> Int = { it.random(Random.Default) },
    ): Int {
        if (queueSize <= 0) return currentIndex
        if (shuffle && queueSize > 1) {
            plannedNextIndex
                ?.takeIf { it in 0 until queueSize && it != currentIndex }
                ?.let { return it }
            var i: Int
            do { i = roll(0 until queueSize) } while (i == currentIndex)
            return i
        }
        if (forward) {
            val n = currentIndex + 1
            return when {
                n < queueSize -> n
                repeatAll -> 0
                else -> EXTEND
            }
        }
        return if (currentIndex - 1 >= 0) currentIndex - 1 else queueSize - 1
    }
}
