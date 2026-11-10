package com.hikari.app.domain.library

import com.hikari.app.data.api.dto.LibraryVideoDto
import org.junit.Test
import kotlin.test.assertEquals

class SeriesResumeTest {

    private fun ep(
        season: Int,
        episode: Int,
        progress: Float? = null,
        at: Long = 0L,
        downloaded: Int = 1,
    ) = LibraryVideoDto(
        id = "s${season}e$episode",
        channel_id = "manual",
        series_id = "serie",
        title = "Folge $episode",
        published_at = 0L,
        duration_seconds = 1000,
        discovered_at = 0L,
        season = season,
        episode = episode,
        progress_seconds = progress,
        progress_updated_at = at,
        downloaded = downloaded,
    )

    @Test fun without_progress_the_first_episode_is_next() {
        val videos = listOf(ep(2, 1), ep(1, 2), ep(1, 1))
        assertEquals("s1e1", SeriesResume.target(videos)?.id)
    }

    @Test fun an_unfinished_episode_is_resumed_even_in_a_later_season() {
        val videos = listOf(ep(1, 1, progress = 400f, at = 10), ep(3, 9, progress = 300f, at = 99), ep(3, 10))
        assertEquals("s3e9", SeriesResume.target(videos)?.id)
    }

    @Test fun after_a_finished_episode_the_following_one_is_next() {
        val videos = listOf(ep(1, 1, progress = 990f, at = 5), ep(1, 2), ep(1, 3))
        assertEquals("s1e2", SeriesResume.target(videos)?.id)
    }

    @Test fun finishing_a_season_leads_into_the_next_season() {
        val videos = listOf(ep(1, 1, progress = 1000f, at = 1), ep(1, 2, progress = 1000f, at = 2), ep(2, 1))
        assertEquals("s2e1", SeriesResume.target(videos)?.id)
    }

    @Test fun a_finished_last_episode_stays_the_target() {
        val videos = listOf(ep(1, 1, progress = 1000f, at = 1), ep(1, 2, progress = 1000f, at = 2))
        assertEquals("s1e2", SeriesResume.target(videos)?.id)
    }

    @Test fun upcoming_starts_at_the_resume_episode_and_skips_missing_files() {
        val videos = listOf(
            ep(1, 1, progress = 1000f, at = 1),
            ep(1, 2, progress = 200f, at = 2),
            ep(1, 3, downloaded = 0),
            ep(1, 4),
            ep(1, 5),
        )
        assertEquals(listOf("s1e2", "s1e4"), SeriesResume.upcoming(videos, count = 2).map { it.id })
    }

    @Test fun nothing_is_kept_ready_for_a_series_never_started() {
        assertEquals(emptyList(), SeriesResume.upcoming(listOf(ep(1, 1), ep(1, 2)), count = 2))
    }
}
