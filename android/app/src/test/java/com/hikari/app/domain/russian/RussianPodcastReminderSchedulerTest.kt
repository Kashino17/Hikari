package com.hikari.app.domain.russian

import io.mockk.mockk
import java.util.Calendar
import kotlin.test.assertEquals
import org.junit.Test

class RussianPodcastReminderSchedulerTest {
    private val scheduler = RussianPodcastReminderScheduler(mockk())

    @Test
    fun naechsteErinnerungLiegtHeuteOderMorgen() {
        val now = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 17)
            set(Calendar.MINUTE, 30)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        assertEquals(30 * 60 * 1000L, scheduler.delayUntilNextRun(18 * 60, now))
        assertEquals((24 * 60 - 30) * 60 * 1000L, scheduler.delayUntilNextRun(17 * 60, now))
    }

    @Test
    fun erledigteLektionUndFertigerKursWerdenNichtErinnert() {
        kotlin.test.assertTrue(shouldSendPodcastReminder(RuProgress(), 100, 100))
        kotlin.test.assertFalse(shouldSendPodcastReminder(RuProgress(podcastLastCompletedDay = 100), 100, 100))
        kotlin.test.assertFalse(shouldSendPodcastReminder(RuProgress(podcastDayStars = (1..100).associateWith { 3 }), 100, 100))
    }
}
