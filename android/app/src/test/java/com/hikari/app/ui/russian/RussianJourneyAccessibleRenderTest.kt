package com.hikari.app.ui.russian

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.hikari.app.domain.russian.*
import org.junit.Rule
import org.junit.Test

class RussianJourneyAccessibleRenderTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig(screenWidth = 640, screenHeight = 1280, density = Density.XHIGH, fontScale = 1.3f), theme = "android:Theme.Material.Light.NoActionBar", showSystemUi = false)
    @Test fun firstWordNarrowLargeText() = paparazzi.snapshot { RenderLesson(RuFoundationRun("test", "hello")) }
    @Test fun guidedNarrowLargeText() = paparazzi.snapshot { RenderLesson(RuFoundationRun("test", "hello", phase = "guided")) }
    @Test fun retryNarrowLargeText() = paparazzi.snapshot { RenderLesson(RuFoundationRun("test", "hello", phase = "recall", draft = "нет", result = "retry", helped = true)) }
}
class RussianJourneyWideRenderTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig(screenWidth = 1600, screenHeight = 2000, density = Density.XHIGH), theme = "android:Theme.Material.Light.NoActionBar", showSystemUi = false)
    @Test fun tabletWelcome() = paparazzi.snapshot {
        JourneyTheme { JourneyScaffold("Gesprächsreise", "Russisch · Schritt für Schritt", {}, {}) {
            JourneyHome(RuChatProgress(), 0, {}, {}, {}, {}, {})
        } }
    }
    @Test fun completedLesson() = paparazzi.snapshot { RenderLesson(RuFoundationRun("test", "hello", phase = "complete", recalled = 3)) }
}
