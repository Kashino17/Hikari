package com.hikari.app.domain.download

import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DownloadRetentionPolicyTest {
    private val day = 24L * 60 * 60 * 1000
    private val now = 1_800_000_000_000L

    @Test fun zero_days_means_keep_forever() {
        assertFalse(DownloadRetentionPolicy.isExpired(downloadedAt = 0L, retentionDays = 0, now = now))
    }

    @Test fun download_expires_once_the_period_has_fully_passed() {
        assertFalse(DownloadRetentionPolicy.isExpired(now - 7 * day + 1, retentionDays = 7, now = now))
        assertTrue(DownloadRetentionPolicy.isExpired(now - 7 * day, retentionDays = 7, now = now))
        assertTrue(DownloadRetentionPolicy.isExpired(now - 90 * day, retentionDays = 60, now = now))
    }

    @Test fun fresh_download_never_expires_immediately() {
        assertFalse(DownloadRetentionPolicy.isExpired(now, retentionDays = 7, now = now))
    }

    @Test fun labels_read_naturally() {
        assertEquals("Für immer", retentionLabel(0))
        assertEquals("14 Tage", retentionLabel(14))
        assertEquals("2 Monate", retentionLabel(60))
    }
}
