package com.hikari.app.ui.russian

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import androidx.compose.runtime.Composable
import com.hikari.app.domain.russian.*
import org.junit.Rule
import org.junit.Test

class RussianJourneyRenderTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "android:Theme.Material.Light.NoActionBar", showSystemUi = false)
    @Test fun welcome() = paparazzi.snapshot {
        JourneyTheme { JourneyScaffold("Gesprächsreise", "Russisch · Schritt für Schritt", {}, {}) {
            JourneyHome(RuChatProgress(), 0, {}, {}, {}, {}, {})
        } }
    }
    @Test fun firstWord() = paparazzi.snapshot { RenderLesson(RuFoundationRun("test", "hello")) }
    @Test fun guided() = paparazzi.snapshot { RenderLesson(RuFoundationRun("test", "hello", phase = "guided")) }
    @Test fun recall() = paparazzi.snapshot { RenderLesson(RuFoundationRun("test", "hello", phase = "recall")) }
    @Test fun success() = paparazzi.snapshot { RenderLesson(RuFoundationRun("test", "hello", phase = "recall", draft = "Да", result = "correct")) }
}

@Composable internal fun RenderLesson(run: RuFoundationRun) {
    JourneyTheme { JourneyScaffold("Dein erstes Hallo", "Ein Platz im Café", {}, {}, footer = {
        JourneyLessonFooter(run, RuFoundationProgress(run = run), false, {}, {}, {}, {}, {}, {})
    }) {
        when (run.phase) {
            "learn" -> JourneyWordView(run, {})
            "complete" -> JourneyCelebration(run, {})
            else -> JourneyPracticeView(run, false, "", run.draft.isNotBlank(), {}, {}, {}, {})
        }
    } }
}
