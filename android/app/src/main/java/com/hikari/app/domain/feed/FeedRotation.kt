package com.hikari.app.domain.feed

import kotlin.random.Random

/**
 * Sorgt dafür, dass der Feed nichts wiederholt, solange es noch Ungesehenes gibt:
 * Erst kommt alles, was noch nie dran war (gemischt), danach das, was am längsten
 * zurückliegt. So wandert man durch den ganzen Vorrat, bevor etwas zum zweiten Mal kommt.
 */
object FeedRotation {
    fun pick(
        pool: List<String>,
        lastSeen: Map<String, Long>,
        count: Int,
        rng: Random,
        exclude: Set<String> = emptySet(),
    ): List<String> {
        if (count <= 0) return emptyList()
        val candidates = pool.filter { it !in exclude }.distinct()
        val (seen, fresh) = candidates.partition { it in lastSeen }
        val picked = fresh.shuffled(rng).take(count).toMutableList()
        if (picked.size < count) {
            // Gleich alte Einträge mischen, damit die zweite Runde nicht in derselben Reihenfolge läuft.
            picked += seen.shuffled(rng).sortedBy { lastSeen.getValue(it) }.take(count - picked.size)
        }
        return picked
    }

    /** Verlauf klein halten: nur die jüngsten [keep] Einträge überleben. */
    fun trim(lastSeen: Map<String, Long>, keep: Int = 1500): Map<String, Long> =
        if (lastSeen.size <= keep) lastSeen
        else lastSeen.entries.sortedByDescending { it.value }.take(keep).associate { it.key to it.value }
}
