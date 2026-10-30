package com.hikari.app.ui.russian

import androidx.compose.foundation.layout.Column
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.hikari.app.domain.russian.RuDay
import com.hikari.app.domain.russian.RuDialog
import com.hikari.app.domain.russian.RuSoundTip
import org.junit.Rule
import org.junit.Test

class RussianPodcastRenderTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "Theme.Hikari", showSystemUi = false)

    @Test
    fun lessonStates() = paparazzi.snapshot {
        HikariScreenTest {
            Column {
                PodcastLessonRow(lesson(1), stars = 3, locked = false, current = false) {}
                PodcastLessonRow(lesson(2), stars = 0, locked = false, current = true) {}
                PodcastLessonRow(lesson(3), stars = 0, locked = true, current = false) {}
            }
        }
    }

    private fun lesson(day: Int) = RuDay(
        day = day,
        title = "Begrüßen und sich vorstellen",
        goal = "Begrüßen",
        sound = RuSoundTip("", "", emptyList()),
        items = emptyList(),
        dialog = RuDialog("dialog", "", "", emptyList()),
    )
}
