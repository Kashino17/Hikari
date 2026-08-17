package com.hikari.app.data.db

import androidx.room.Dao
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Insert
import androidx.room.PrimaryKey
import androidx.room.Query

/**
 * Einzelnes Abspiel-Ereignis — Grundlage für "Meistgehört der letzten
 * 7 Tage" (die music_songs-Tabelle kennt nur die letzte Wiedergabe,
 * keine Häufigkeit). Alte Ereignisse werden beim Schreiben weggeräumt.
 */
@Entity(tableName = "music_play_events", indices = [Index("playedAt")])
data class MusicPlayEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val videoId: String,
    val playedAt: Long,
)

@Dao
interface MusicPlayEventDao {
    @Insert
    suspend fun insert(event: MusicPlayEventEntity)

    @Query("DELETE FROM music_play_events WHERE playedAt < :olderThan")
    suspend fun prune(olderThan: Long)

    /** Meistgespielte Songs seit [since], absteigend nach Abspielzahl. */
    @Query(
        """
        SELECT s.* FROM music_songs s
        JOIN (
            SELECT videoId, COUNT(*) AS plays FROM music_play_events
            WHERE playedAt >= :since GROUP BY videoId
        ) p ON s.videoId = p.videoId
        ORDER BY p.plays DESC, s.addedAt DESC
        LIMIT :limit
        """,
    )
    suspend fun topPlayedSince(since: Long, limit: Int): List<MusicSongEntity>
}
