package com.hikari.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.hikari.app.data.sponsor.SegmentBehavior
import com.hikari.app.data.sponsor.SegmentCategories
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sponsorBlockDataStore by preferencesDataStore(name = "hikari_sponsorblock")

private val TOTAL_SKIPPED_COUNT = longPreferencesKey("skip_total_count")
private val TOTAL_SKIPPED_MS = longPreferencesKey("skip_total_ms")
private fun behaviorKey(apiKey: String) =
    stringPreferencesKey("skip_behavior_$apiKey")

@Singleton
class SponsorBlockPrefs @Inject constructor(
    @ApplicationContext private val ctx: Context,
) {
    /** Reactive map of category apiKey → current user behavior. */
    val behaviors: Flow<Map<String, SegmentBehavior>> = ctx.sponsorBlockDataStore.data.map { prefs ->
        SegmentCategories.all.associate { cat ->
            cat.apiKey to (prefs[behaviorKey(cat.apiKey)]
                ?.let { runCatching { SegmentBehavior.valueOf(it) }.getOrNull() }
                ?: cat.defaultBehavior)
        }
    }

    val totalSkippedCount: Flow<Long> = ctx.sponsorBlockDataStore.data.map {
        it[TOTAL_SKIPPED_COUNT] ?: 0L
    }
    val totalSkippedMs: Flow<Long> = ctx.sponsorBlockDataStore.data.map {
        it[TOTAL_SKIPPED_MS] ?: 0L
    }

    suspend fun setBehavior(apiKey: String, behavior: SegmentBehavior) {
        ctx.sponsorBlockDataStore.edit { it[behaviorKey(apiKey)] = behavior.name }
    }

    suspend fun recordSkip(segmentDurationMs: Long) {
        ctx.sponsorBlockDataStore.edit {
            it[TOTAL_SKIPPED_COUNT] = (it[TOTAL_SKIPPED_COUNT] ?: 0L) + 1
            it[TOTAL_SKIPPED_MS] = (it[TOTAL_SKIPPED_MS] ?: 0L) + segmentDurationMs.coerceAtLeast(0L)
        }
    }

    suspend fun resetStats() {
        ctx.sponsorBlockDataStore.edit {
            it[TOTAL_SKIPPED_COUNT] = 0L
            it[TOTAL_SKIPPED_MS] = 0L
        }
    }
}
