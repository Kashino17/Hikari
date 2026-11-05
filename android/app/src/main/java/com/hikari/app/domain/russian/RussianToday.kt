package com.hikari.app.domain.russian

enum class RuTodayKind { COURSE, REVIEW, PODCAST }

/**
 * Ein Punkt in "Heute dran". [day] ist der Kurstag bzw. die Podcast-Lektion,
 * [count] die Zahl fälliger Sätze. [practice] = Podcast-Folge ist gehört,
 * jetzt kommen die Übungen dazu.
 */
data class RuTodayItem(
    val kind: RuTodayKind,
    val done: Boolean,
    val route: String,
    val day: Int = 0,
    val count: Int = 0,
    val practice: Boolean = false,
    val allDone: Boolean = false,
)

/** Tagesprogramm über alle Bereiche: Sprechkurs, Wiederholen, Podcast-Kurs. */
object RuToday {
    fun plan(
        p: RuProgress,
        today: Long,
        courseDays: Int,
        podcastDays: Int,
        podcastResumeDay: Int? = null,
    ): List<RuTodayItem> {
        val courseAll = p.dayStars.size >= courseDays
        val courseToday = p.courseLastCompletedDay == today
        val courseDay = when {
            courseToday -> p.dayStars.keys.maxOrNull() ?: 1
            else -> p.unlockedDay(courseDays)
        }
        val course = RuTodayItem(
            kind = RuTodayKind.COURSE,
            done = courseToday || courseAll,
            route = "russian/lesson/$courseDay",
            day = courseDay,
            allDone = courseAll,
        )

        val due = RuSrs.dueIds(p.cards, today).size
        val review = RuTodayItem(RuTodayKind.REVIEW, done = due == 0, route = "russian/review", count = due)

        val podcastAll = p.podcastDayStars.size >= podcastDays
        val podDay = podcastResumeDay ?: p.unlockedPodcastDay(podcastDays)
        val practice = podDay in p.podcastListened
        val podcast = RuTodayItem(
            kind = RuTodayKind.PODCAST,
            done = p.podcastLastCompletedDay == today || podcastAll,
            route = if (practice) "russian/podcastPractice/$podDay" else "russian/podcast/$podDay",
            day = podDay,
            practice = practice,
            allDone = podcastAll,
        )
        return listOf(course, review, podcast)
    }
}
