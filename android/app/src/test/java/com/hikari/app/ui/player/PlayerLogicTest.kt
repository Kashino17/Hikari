package com.hikari.app.ui.player

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PlayerLogicTest {

    // ── Tap-Zonen ────────────────────────────────────────────────────────────

    @Test fun tap_zone_center_is_neutral_band() {
        val w = 1000f
        assertEquals(TapZone.Left, tapZoneFor(x = 100f, widthPx = w))
        assertEquals(TapZone.Center, tapZoneFor(x = 500f, widthPx = w))
        assertEquals(TapZone.Right, tapZoneFor(x = 900f, widthPx = w))
        // Grenzen: 32 % links, 32 % rechts → 320 / 680
        assertEquals(TapZone.Left, tapZoneFor(x = 319f, widthPx = w))
        assertEquals(TapZone.Center, tapZoneFor(x = 321f, widthPx = w))
        assertEquals(TapZone.Center, tapZoneFor(x = 679f, widthPx = w))
        assertEquals(TapZone.Right, tapZoneFor(x = 681f, widthPx = w))
    }

    @Test fun tap_zone_with_zero_width_is_center() {
        assertEquals(TapZone.Center, tapZoneFor(x = 10f, widthPx = 0f))
    }

    // ── Seek-Stapelung (Netflix: +10, +20, +30 …) ────────────────────────────

    @Test fun seek_accumulator_stacks_same_direction_within_window() {
        val acc = SeekAccumulator(stepMs = 10_000L, windowMs = 800L)
        assertEquals(10_000L, acc.tap(forward = true, nowMs = 0L))
        assertEquals(20_000L, acc.tap(forward = true, nowMs = 300L))
        assertEquals(30_000L, acc.tap(forward = true, nowMs = 900L))
    }

    @Test fun seek_accumulator_resets_after_window() {
        val acc = SeekAccumulator(stepMs = 10_000L, windowMs = 800L)
        acc.tap(forward = true, nowMs = 0L)
        assertEquals(10_000L, acc.tap(forward = true, nowMs = 2_000L))
    }

    @Test fun seek_accumulator_resets_on_direction_change() {
        val acc = SeekAccumulator(stepMs = 10_000L, windowMs = 800L)
        acc.tap(forward = true, nowMs = 0L)
        acc.tap(forward = true, nowMs = 100L)
        assertEquals(-10_000L, acc.tap(forward = false, nowMs = 200L))
    }

    // ── Helligkeit / Lautstärke per vertikalem Wischen ───────────────────────

    @Test fun level_drag_up_increases_and_clamps() {
        // Volle Spanne über 60 % der Höhe: 600 px hoch = +1.0
        assertEquals(0.5f, adjustLevel(current = 0.0f, dragDeltaY = -300f, heightPx = 1000f), 0.001f)
        assertEquals(1.0f, adjustLevel(current = 0.8f, dragDeltaY = -900f, heightPx = 1000f), 0.001f)
        assertEquals(0.0f, adjustLevel(current = 0.2f, dragDeltaY = 900f, heightPx = 1000f), 0.001f)
    }

    @Test fun level_drag_with_zero_height_is_noop() {
        assertEquals(0.4f, adjustLevel(current = 0.4f, dragDeltaY = -100f, heightPx = 0f), 0.001f)
    }

    // ── Orientierung ─────────────────────────────────────────────────────────

    @Test fun auto_orientation_follows_video_shape() {
        assertEquals(PlayerOrientation.Landscape, resolveOrientation(OrientationMode.Auto, videoW = 1920, videoH = 1080))
        assertEquals(PlayerOrientation.Portrait, resolveOrientation(OrientationMode.Auto, videoW = 1080, videoH = 1920))
        // quadratisch / unbekannt → Landscape (Serien-Default)
        assertEquals(PlayerOrientation.Landscape, resolveOrientation(OrientationMode.Auto, videoW = 0, videoH = 0))
        assertEquals(PlayerOrientation.Landscape, resolveOrientation(OrientationMode.Auto, videoW = 1000, videoH = 1000))
    }

    @Test fun manual_orientation_overrides_video_shape() {
        assertEquals(PlayerOrientation.Portrait, resolveOrientation(OrientationMode.Portrait, videoW = 1920, videoH = 1080))
        assertEquals(PlayerOrientation.Landscape, resolveOrientation(OrientationMode.Landscape, videoW = 1080, videoH = 1920))
    }

    @Test fun portrait_panel_only_for_landscape_video_in_portrait() {
        assertTrue(showsPortraitPanel(PlayerOrientation.Portrait, videoW = 1920, videoH = 1080))
        assertFalse(showsPortraitPanel(PlayerOrientation.Portrait, videoW = 1080, videoH = 1920))
        assertFalse(showsPortraitPanel(PlayerOrientation.Landscape, videoW = 1920, videoH = 1080))
        // unbekannte Größe: Panel zeigen (16:9 ist der Normalfall)
        assertTrue(showsPortraitPanel(PlayerOrientation.Portrait, videoW = 0, videoH = 0))
    }

    // ── Scrubber-Mapping ─────────────────────────────────────────────────────

    @Test fun scrub_maps_full_width_to_full_duration_in_normal_mode() {
        val target = scrubTarget(
            startMs = 0L, durationMs = 60_000L,
            dragDeltaX = 500f, trackWidthPx = 1000f, fine = false,
        )
        assertEquals(30_000L, target)
    }

    @Test fun scrub_fine_mode_quarters_the_gain() {
        val target = scrubTarget(
            startMs = 40_000L, durationMs = 60_000L,
            dragDeltaX = -400f, trackWidthPx = 1000f, fine = true,
        )
        // -400/1000 * 60s = -24s → ×0.25 = -6s
        assertEquals(34_000L, target)
    }

    @Test fun scrub_clamps_to_bounds() {
        assertEquals(0L, scrubTarget(5_000L, 60_000L, dragDeltaX = -5000f, trackWidthPx = 1000f, fine = false))
        assertEquals(60_000L, scrubTarget(5_000L, 60_000L, dragDeltaX = 5000f, trackWidthPx = 1000f, fine = false))
    }

    // ── Zeitformat ───────────────────────────────────────────────────────────

    @Test fun formats_clock_with_and_without_hours() {
        assertEquals("0:05", formatClock(5_000L))
        assertEquals("12:34", formatClock(754_000L))
        assertEquals("1:02:03", formatClock(3_723_000L))
        assertEquals("0:00", formatClock(-500L))
    }

    @Test fun remaining_is_prefixed_with_minus() {
        assertEquals("-41:00", formatRemaining(positionMs = 60_000L, durationMs = 2_520_000L))
        assertEquals("-0:00", formatRemaining(positionMs = 99_000L, durationMs = 60_000L))
    }

    // ── Pinch → Zoom-Entscheidung ────────────────────────────────────────────

    @Test fun pinch_zoom_needs_clear_intent() {
        assertEquals(null, pinchDecision(scale = 1.05f))
        assertEquals(true, pinchDecision(scale = 1.3f))
        assertEquals(false, pinchDecision(scale = 0.7f))
    }

    // ── Episoden-Label ───────────────────────────────────────────────────────

    @Test fun episode_label_uses_season_and_episode() {
        assertEquals("S1 F3", episodeLabel(season = 1, episode = 3))
        assertEquals("F3", episodeLabel(season = null, episode = 3))
        assertEquals("", episodeLabel(season = null, episode = null))
    }
}
