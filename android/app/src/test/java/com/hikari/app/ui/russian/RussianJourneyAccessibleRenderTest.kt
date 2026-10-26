package com.hikari.app.ui.russian

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.hikari.app.domain.russian.*
import io.mockk.every
import io.mockk.mockk
import java.time.LocalDate
import org.junit.Rule
import org.junit.Test

class RussianJourneyAccessibleRenderTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig(screenWidth = 640, screenHeight = 1280, density = Density.XHIGH, fontScale = 1.3f), theme = "Theme.Hikari", showSystemUi = false)
    @Test fun firstWordNarrowLargeText() = paparazzi.snapshot { RenderLesson(RuFoundationRun("test", "hello")) }
    @Test fun guidedNarrowLargeText() = paparazzi.snapshot { RenderLesson(RuFoundationRun("test", "hello", phase = "guided")) }
    @Test fun retryNarrowLargeText() = paparazzi.snapshot { RenderLesson(RuFoundationRun("test", "hello", phase = "recall", draft = "нет", result = "retry", helped = true)) }
    @Test fun learningPlanNarrowLargeText() {
        val vm = mockk<RussianConversationViewModel>(relaxed = true)
        every { vm.settings } returns RuChatSettings(targetDate = "2027-04-26", minutesPerDay = 45)
        every { vm.today() } returns LocalDate.of(2026, 10, 26).toEpochDay()
        paparazzi.snapshot { HikariScreenTest { JourneyScaffold("Dein Lernplan", "Russisch · Schritt für Schritt", {}, {}) {
            ConversationPlan(vm, RuChatProgress())
        } } }
    }
}

/** Extended viewport exposes every planner control and the calculated budget for inspection. */
class RussianJourneyFullPlanRenderTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5.copy(screenHeight = 5000), theme = "Theme.Hikari", showSystemUi = false)
    @Test fun allPlannerInputsAndBudget() {
        val vm = mockk<RussianConversationViewModel>(relaxed = true)
        every { vm.settings } returns RuChatSettings(targetDate = "2027-04-26", minutesPerDay = 45, daysPerWeek = 7, blockMinutes = 30)
        every { vm.today() } returns LocalDate.of(2026, 10, 26).toEpochDay()
        paparazzi.snapshot { HikariScreenTest { JourneyScaffold("Dein Lernplan", "Russisch · Schritt für Schritt", {}, {}) {
            ConversationPlan(vm, RuChatProgress())
        } } }
    }
}
class RussianJourneyWideRenderTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig(screenWidth = 1600, screenHeight = 2000, density = Density.XHIGH), theme = "Theme.Hikari", showSystemUi = false)
    @Test fun tabletWelcome() = paparazzi.snapshot {
        HikariScreenTest { JourneyScaffold("Gesprächsreise", "Russisch · Schritt für Schritt", {}, {}) {
            JourneyHome(RuChatProgress(), 0, {}, {}, {}, {}, {})
        } }
    }
    @Test fun completedLesson() = paparazzi.snapshot { RenderLesson(RuFoundationRun("test", "hello", phase = "complete", recalled = 3)) }
}
