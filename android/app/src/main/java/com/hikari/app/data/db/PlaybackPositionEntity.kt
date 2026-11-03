package com.hikari.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playback_positions")
data class PlaybackPositionEntity(
    @PrimaryKey val videoId: String,
    val positionMs: Long,
    val updatedAt: Long,
    /**
     * Position, die zuletzt erfolgreich ans Backend übertragen wurde.
     * -1 = noch nie übertragen. Weicht der Wert von [positionMs] ab, liegt
     * eine offline entstandene Änderung vor, die auf den nächsten Sync wartet.
     */
    val syncedPositionMs: Long = -1L,
    val syncedAt: Long = 0L,
)
