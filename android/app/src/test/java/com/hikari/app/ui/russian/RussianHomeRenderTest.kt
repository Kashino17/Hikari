package com.hikari.app.ui.russian

import androidx.compose.foundation.layout.Column
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.hikari.app.domain.russian.RuDay
import com.hikari.app.domain.russian.RuDialog
import com.hikari.app.domain.russian.RuProgress
import com.hikari.app.domain.russian.RuSoundTip
import org.junit.Rule
import org.junit.Test

class RussianHomeRenderTest {
    @get:Rule
    val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "Theme.Hikari", showSystemUi = false)

    private val progress = RuProgress(bestReflex = 18, bestBlitz = 44, labBest = mapOf("o" to 86), bestPairsSeconds = 41, patternsDone = setOf("p1"))

    @Test
    fun uebungen() = paparazzi.snapshot {
        HikariScreenTest {
            Column { practiceGroups(progress, patternsTotal = 25).forEach { ToolGroup(it, onOpen = {}) } }
        }
    }

    @Test
    fun kursplan() = paparazzi.snapshot {
        HikariScreenTest {
            Column {
                PlanDayRow(day(1), stars = 3, current = false, locked = false, onLesson = {}, onDialog = {})
                PlanDayRow(day(2), stars = 0, current = true, locked = false, onLesson = {}, onDialog = {})
                PlanDayRow(day(3), stars = 0, current = false, locked = true, onLesson = {}, onDialog = {})
            }
        }
    }

    private fun day(n: Int) = RuDay(
        day = n,
        title = "Sich vorstellen",
        goal = "Du kannst sagen, wie du heißt, woher du kommst und wo du wohnst.",
        sound = RuSoundTip("", "", emptyList()),
        items = emptyList(),
        dialog = RuDialog("dialog", "", "", emptyList()),
    )
}
