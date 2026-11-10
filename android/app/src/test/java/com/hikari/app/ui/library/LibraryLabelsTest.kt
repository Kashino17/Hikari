package com.hikari.app.ui.library

import com.hikari.app.data.api.dto.SeriesDto
import org.junit.Test
import kotlin.test.assertEquals

class LibraryLabelsTest {
    private val day = 86_400_000L
    private val now = 1_800_000_000_000L

    @Test fun episode_tag_names_season_and_episode() {
        assertEquals("S3 · F9", episodeTag(3, 9))
        assertEquals("Folge 9", episodeTag(null, 9))
        assertEquals("", episodeTag(null, null))
    }

    @Test fun series_scope_mentions_seasons_only_when_there_are_several() {
        fun series(seasons: Int, episodes: Int) =
            SeriesDto(id = "s", title = "Serie", added_at = 0L, episode_count = episodes, season_count = seasons)
        assertEquals("3 Staffeln · 38 Folgen", seriesScope(series(3, 38)))
        assertEquals("14 Folgen", seriesScope(series(1, 14)))
        assertEquals("1 Folge", seriesScope(series(1, 1)))
    }

    @Test fun durations_read_naturally() {
        assertEquals("8 min", durationLabel(8 * 60 + 20))
        assertEquals("2 h 49 min", durationLabel(2 * 3600 + 49 * 60))
        assertEquals("1 min", durationLabel(20))
        assertEquals("26:57", clockLabel(26 * 60 + 57))
        assertEquals("1:02:30", clockLabel(3750))
    }

    @Test fun relative_age_steps_from_days_to_years() {
        assertEquals("heute", relativeAge(now - day / 2, now))
        assertEquals("gestern", relativeAge(now - day, now))
        assertEquals("vor 3 Tagen", relativeAge(now - 3 * day, now))
        assertEquals("vor 1 Woche", relativeAge(now - 9 * day, now))
        assertEquals("vor 3 Wochen", relativeAge(now - 22 * day, now))
        assertEquals("vor 4 Monaten", relativeAge(now - 125 * day, now))
        assertEquals("vor 2 Jahren", relativeAge(now - 800 * day, now))
        assertEquals("", relativeAge(0L, now))
    }
}
