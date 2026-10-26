package com.hikari.app.ui.russian

import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.hikari.app.domain.russian.*
import io.mockk.every
import io.mockk.mockk
import org.junit.Rule
import org.junit.Test
import java.time.LocalDate

class RussianJourneySecondaryRenderTest {
    @get:Rule val paparazzi = Paparazzi(deviceConfig = DeviceConfig.PIXEL_5, theme = "Theme.Hikari", showSystemUi = false)
    @Test fun artworkGallery() = paparazzi.snapshot {
        HikariScreenTest { Column {
            listOf("hello", "tea", "coffee", "yes", "no", "thanks", "choice").chunked(2).forEach { row ->
                Row { row.forEach { kind -> Column(Modifier.weight(1f).padding(8.dp)) {
                    JourneySmall(kind)
                    JourneyArtwork(kind, Modifier.fillMaxWidth().height(150.dp))
                } } }
            }
        } }
    }
    private fun model(): RussianConversationViewModel = mockk<RussianConversationViewModel>(relaxed = true).also {
        every { it.settings } returns RuChatSettings()
        every { it.session } returns null
        every { it.available } returns true
        every { it.busy } returns false
        every { it.error } returns null
        every { it.today() } returns LocalDate.of(2026, 10, 26).toEpochDay()
    }
    @Test fun personalConversationSetup() {
        val vm = model()
        paparazzi.snapshot { HikariScreenTest { JourneyScaffold("Gesprächsreise", "Deine Wörter. Deine Geschichten.", {}, {}) {
            JourneyFreeSetup(vm, RuChatProgress(foundation = RuFoundationProgress(completed = setOf("hello", "choice", "likes"))), {})
        } } }
    }
    @Test fun personalConversationSavedTopic() {
        val vm = model()
        every { vm.settings } returns RuChatSettings(interest = "Meine Katze und mein Lieblingsfilm")
        paparazzi.snapshot { HikariScreenTest { JourneyScaffold("Gesprächsreise", "Deine Wörter. Deine Geschichten.", {}, {}) {
            JourneyFreeSetup(vm, RuChatProgress(foundation = RuFoundationProgress(completed = setOf("hello", "choice", "likes"))), {})
        } } }
    }
    @Test fun learningPlan() {
        val vm = model()
        paparazzi.snapshot { HikariScreenTest { JourneyScaffold("Dein Lernplan", "Russisch · Schritt für Schritt", {}, {}) {
            ConversationPlan(vm, RuChatProgress())
        } } }
    }
    @Test fun learningPlanWithSavedInput() {
        val vm = model()
        every { vm.settings } returns RuChatSettings(targetDate = "2027-04-26", minutesPerDay = 45, daysPerWeek = 7, blockMinutes = 30)
        paparazzi.snapshot { HikariScreenTest { JourneyScaffold("Dein Lernplan", "Russisch · Schritt für Schritt", {}, {}) {
            ConversationPlan(vm, RuChatProgress())
        } } }
    }
    @Test fun learningPlanInvalidDate() {
        val vm = model()
        every { vm.settings } returns RuChatSettings(targetDate = "2027-99-99")
        paparazzi.snapshot { HikariScreenTest { JourneyScaffold("Dein Lernplan", "Russisch · Schritt für Schritt", {}, {}) {
            ConversationPlan(vm, RuChatProgress())
        } } }
    }
    @Test fun fullyKnownQuestion() {
        val vm = model()
        val reply = RuChatReply(partnerRu = "Кофе или чай?", partnerDe = "Kaffee oder Tee?", cue = "Wähle ein Getränk.",
            words = emptyList(), starter = "", examples = listOf(RuChatPhrase("Кофе.", "Kaffee."), RuChatPhrase("Чай.", "Tee.")),
            feedback = null, chunk = RuChatPhrase("Кофе или чай?", "Kaffee oder Tee?"), mission = "Ein Getränk wählen")
        every { vm.session } returns RuChatSession("test", RuChatSettings(), 0, turns = listOf(RuChatTurn("turn", reply)))
        paparazzi.snapshot { HikariScreenTest { JourneyScaffold("Mit Anja sprechen", "Dein Tempo. Deine Geschichten.", {}, {}) {
            JourneyFreeChat(vm, RuChatProgress(), false, "", {}, {}, {}, {}, {}, {}, {})
        } } }
    }
    @Test fun newVocabularyBeforeFreeAnswer() {
        val vm = model()
        val reply = RuChatReply(partnerRu = "Ты любишь кофе?", partnerDe = "Magst du Kaffee?", cue = "Antworte mit Ja oder Nein.",
            words = listOf(RuChatPhrase("любишь", "du magst")), starter = "Я …", examples = listOf(RuChatPhrase("Да.", "Ja."), RuChatPhrase("Нет.", "Nein.")),
            feedback = null, chunk = RuChatPhrase("Ты любишь кофе?", "Magst du Kaffee?"), mission = "Eine Vorliebe nennen")
        every { vm.session } returns RuChatSession("test", RuChatSettings(), 0, turns = listOf(RuChatTurn("turn", reply)))
        paparazzi.snapshot { HikariScreenTest { JourneyScaffold("Mit Anja sprechen", "Dein Tempo. Deine Geschichten.", {}, {}) {
            JourneyFreeChat(vm, RuChatProgress(), false, "", {}, {}, {}, {}, {}, {}, {})
        } } }
    }
}
