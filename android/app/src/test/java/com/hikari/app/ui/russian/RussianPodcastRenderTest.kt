package com.hikari.app.ui.russian

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.hikari.app.domain.russian.RuDay
import com.hikari.app.domain.russian.RuDialog
import com.hikari.app.domain.russian.RuSoundTip
import com.hikari.app.player.PodcastPlayback
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
                PodcastLessonRow(lesson(4), stars = 0, locked = false, current = false, progress = 0.4f, subtitle = "5:08 von 12:49") {}
            }
        }
    }

    @Test
    fun player() = paparazzi.snapshot {
        val state = PodcastPlayback(
            day = 1, title = "Begrüßen und sich vorstellen", videoId = "vdAB-uJ6F-g",
            playing = true, positionMs = 252_000, durationMs = 769_000, bufferedMs = 400_000, speed = 1.25f,
        )
        HikariScreenTest {
            Column(Modifier.padding(16.dp)) {
                PodcastPlayerFull(state, PodcastActions(), onPhrases = {})
                Spacer(Modifier.height(24.dp))
                PodcastPlayerBar(state.copy(playing = false), onExpand = {}, onToggle = {}, onClose = {})
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
