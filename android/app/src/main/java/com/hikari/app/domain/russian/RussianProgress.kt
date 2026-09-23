package com.hikari.app.domain.russian

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable

/** Lernstand einer Karte im Leitner-System. */
@Serializable
data class RuCard(
    /** 0 = neu … 6 = sitzt (30 Tage Abstand). */
    val box: Int = 1,
    /** Fällig ab diesem Tag (epochDay). */
    val due: Long,
    val seen: Int = 0,
    val wrong: Int = 0,
)

@Serializable
data class RuProgress(
    /** null = noch nicht gefragt (erster Start zeigt die Auswahl). */
    val gender: RuGender? = null,
    val voice: RuGender? = null,
    val xp: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val lastActiveDay: Long = -1,
    /** Abgeschlossene Tage → Sterne (1–3). */
    val dayStars: Map<Int, Int> = emptyMap(),
    val cards: Map<String, RuCard> = emptyMap(),
    /** XP pro Kalendertag (epochDay) — für "heute" und den Wochenverlauf. */
    val xpByDay: Map<Long, Int> = emptyMap(),
    val bestBlitz: Int = 0,
    val bestPairsSeconds: Int = 0,
    /** Gesprochene Sätze, die die Spracherkennung akzeptiert hat. */
    val spokenOk: Int = 0,
) {
    val settings: RuSettings
        get() = RuSettings(gender ?: RuGender.MALE, voice ?: gender ?: RuGender.MALE)

    /** Höchster freigeschalteter Tag: nächster nach dem letzten abgeschlossenen. */
    fun unlockedDay(totalDays: Int): Int =
        ((dayStars.keys.maxOrNull() ?: 0) + 1).coerceAtMost(totalDays)

    fun xpOn(epochDay: Long): Int = xpByDay[epochDay] ?: 0
}

/** Reine Lern-Logik: Leitner-Abstände, Serie, Sterne. */
object RuSrs {
    /** Abstand in Tagen je Box. Box 0 wird nie vergeben (neue Karten starten in 1). */
    val INTERVALS = intArrayOf(0, 1, 2, 4, 7, 14, 30)
    const val MAX_BOX = 6

    fun introduce(existing: RuCard?, today: Long): RuCard =
        existing ?: RuCard(box = 1, due = today + INTERVALS[1])

    /**
     * Richtig → eine Box höher, entsprechend später wieder. Falsch → zurück in
     * Box 1 und noch heute erneut fällig: Fehler werden sofort nachgeübt.
     */
    fun grade(card: RuCard, correct: Boolean, today: Long): RuCard =
        if (correct) {
            val box = (card.box + 1).coerceAtMost(MAX_BOX)
            card.copy(box = box, due = today + INTERVALS[box], seen = card.seen + 1)
        } else {
            card.copy(box = 1, due = today, seen = card.seen + 1, wrong = card.wrong + 1)
        }

    fun dueIds(cards: Map<String, RuCard>, today: Long): List<String> =
        cards.entries.filter { it.value.due <= today }
            .sortedWith(compareBy({ it.value.due }, { it.value.box }))
            .map { it.key }

    fun streakAfterActivity(p: RuProgress, today: Long): Pair<Int, Long> = when (p.lastActiveDay) {
        today -> p.streak to today
        today - 1 -> (p.streak + 1) to today
        else -> 1 to today
    }

    /** Die Serie gilt als gerissen, wenn gestern und heute nichts gelernt wurde. */
    fun visibleStreak(p: RuProgress, today: Long): Int =
        if (p.lastActiveDay >= today - 1) p.streak else 0

    fun stars(correct: Int, total: Int): Int {
        if (total == 0) return 3
        val rate = correct.toDouble() / total
        return when {
            rate >= 0.9 -> 3
            rate >= 0.7 -> 2
            else -> 1
        }
    }

    /** Karten, deren Box ≥ 3 ist, gelten als "sicher" (mind. 4 Tage Abstand geschafft). */
    fun knownCount(cards: Map<String, RuCard>): Int = cards.values.count { it.box >= 3 }
}

/** Ergebnis einer Sitzung, das in den Fortschritt einfließt. */
data class RuSessionResult(
    val xp: Int,
    /** itemId → alle Antworten in dieser Sitzung richtig? */
    val graded: Map<String, Boolean>,
    /** Neu eingeführte Items (Lektion). */
    val introduced: Set<String> = emptySet(),
    val completedDay: Int? = null,
    val stars: Int = 0,
    val spokenOk: Int = 0,
)

fun RuProgress.apply(result: RuSessionResult, today: Long): RuProgress {
    val cards = cards.toMutableMap()
    for (id in result.introduced) cards[id] = RuSrs.introduce(cards[id], today)
    for ((id, ok) in result.graded) {
        val c = cards[id] ?: continue
        // Frisch eingeführte Karten: richtig beantwortet bleiben sie bei "morgen";
        // falsch → heute noch einmal.
        cards[id] = if (id in result.introduced) {
            if (ok) c.copy(seen = c.seen + 1) else RuSrs.grade(c, false, today)
        } else {
            RuSrs.grade(c, ok, today)
        }
    }
    val (streak, last) = RuSrs.streakAfterActivity(this, today)
    val stars = if (result.completedDay != null) {
        dayStars + (result.completedDay to maxOf(result.stars, dayStars[result.completedDay] ?: 0))
    } else {
        dayStars
    }
    // Nur die letzten 30 Tage XP-Verlauf behalten.
    val xpDays = (xpByDay + (today to xpOn(today) + result.xp)).filterKeys { it > today - 30 }
    return copy(
        xp = xp + result.xp,
        streak = streak,
        bestStreak = maxOf(bestStreak, streak),
        lastActiveDay = last,
        dayStars = stars,
        cards = cards,
        xpByDay = xpDays,
        spokenOk = spokenOk + result.spokenOk,
    )
}

/**
 * Persistenz in SharedPreferences als JSON — bewusst NICHT in Room: kein
 * Migrationsrisiko für die übrige App, und der Zustand ist klein.
 */
@Singleton
class RussianProgressStore @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val prefs = context.getSharedPreferences("hikari_russian", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<RuProgress> = _state.asStateFlow()

    private fun load(): RuProgress = prefs.getString(KEY, null)?.let {
        runCatching { ruJson.decodeFromString(RuProgress.serializer(), it) }.getOrNull()
    } ?: RuProgress()

    @Synchronized
    fun update(transform: (RuProgress) -> RuProgress) {
        val next = transform(_state.value)
        _state.value = next
        prefs.edit().putString(KEY, ruJson.encodeToString(RuProgress.serializer(), next)).apply()
    }

    fun today(): Long = LocalDate.now().toEpochDay()

    private companion object {
        const val KEY = "progress_v1"
    }
}
