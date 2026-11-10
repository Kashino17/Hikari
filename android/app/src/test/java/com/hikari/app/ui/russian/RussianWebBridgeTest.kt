package com.hikari.app.ui.russian

import com.hikari.app.domain.russian.RuGender
import com.hikari.app.domain.russian.RuProgress
import com.hikari.app.domain.russian.ruJson
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.Test

/** Die Web-Oberfläche und die App teilen sich Routen und Lernstand — beides muss zusammenpassen. */
class RussianWebBridgeTest {

    @Test
    fun appRoutenFuehrenAufDieRichtigeSeite() {
        assertEquals("heute", russianWebRoute("russian"))
        assertEquals("plan", russianWebRoute("russian-plan"))
        assertEquals("lesson/3", russianWebRoute("russian/lesson/3"))
        assertEquals("dialog/7", russianWebRoute("russian/dialog/7"))
        assertEquals("review", russianWebRoute("russian/review"))
        assertEquals("test", russianWebRoute("russian/test/0"))
        assertEquals("podcast", russianWebRoute("russian/podcast"))
        assertEquals("podcast/12", russianWebRoute("russian/podcast/12"))
        assertEquals("podcastPractice/12", russianWebRoute("russian/podcastPractice/12"))
        assertEquals("podcast", russianWebRoute("russian/podcast/reminder"))
        assertEquals("satzbuch", russianWebRoute("russian/phrases"))
        assertEquals("anja", russianWebRoute("russian/conversation"))
        assertEquals("deep/d03_kak_dela", russianWebRoute("russian-deep/d03_kak_dela"))
        assertEquals("patterns", russianWebRoute("russian-patterns"))
        assertEquals("pattern/p01_hochu", russianWebRoute("russian-pattern/p01_hochu"))
        assertEquals("sounds", russianWebRoute("russian/sounds"))
    }

    /** So schreibt die Web-Oberfläche den Lernstand (JSON aus progress.js) — die App muss ihn vollständig lesen. */
    @Test
    fun lernstandDerWebOberflaecheWirdGelesen() {
        val json = """
            {"gender":"MALE","voice":null,"xp":180,"streak":2,"bestStreak":5,"lastActiveDay":20767,
             "dayStars":{"1":3,"2":2},"podcastDayStars":{"1":3},"podcastListened":[1,2],
             "podcastLastCompletedDay":20766,"courseLastCompletedDay":20767,
             "cards":{"d01_privet":{"box":3,"due":20771,"seen":4,"wrong":1},"rj001_01":{"box":1,"due":20767,"seen":1,"wrong":0}},
             "xpByDay":{"20766":60,"20767":120},"bestBlitz":180,"bestPairsSeconds":41,"bestReflex":96,"bestKombi":0,
             "spokenOk":38,"labBest":{"yi":86},"favorites":["d01_spasibo"],"patternsDone":["p01_hochu"],
             "mySentences":[{"ru":"Я хоч+у домой.","de":"Ich will nach Hause.","patternId":"p01_hochu","note":"meiner Mama"}]}
        """.trimIndent()
        val p = ruJson.decodeFromString(RuProgress.serializer(), json)
        assertEquals(RuGender.MALE, p.gender)
        assertEquals(mapOf(1 to 3, 2 to 2), p.dayStars)
        assertEquals(setOf(1, 2), p.podcastListened)
        assertEquals(3, p.cards.getValue("d01_privet").box)
        assertEquals(120, p.xpOn(20767))
        assertEquals(86, p.labBest["yi"])
        assertEquals("meiner Mama", p.mySentences.single().note)
        // Hin und zurück bleibt der Inhalt gleich — die App kann den Stand weiterschreiben (z. B. »Folge gehört«).
        val again = ruJson.decodeFromString(RuProgress.serializer(), ruJson.encodeToString(RuProgress.serializer(), p))
        assertEquals(p, again)
        assertTrue(again.favorites.contains("d01_spasibo"))
    }
}
