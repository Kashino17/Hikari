package com.hikari.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface LocalMangaDao {

    // -- Arcs -----------------------------------------------------------------

    @Query("SELECT * FROM local_manga_arcs WHERE arc_id = :id LIMIT 1")
    suspend fun getArc(id: String): LocalMangaArcEntity?

    @Query("SELECT * FROM local_manga_arcs ORDER BY downloaded_at DESC")
    fun observeArcs(): Flow<List<LocalMangaArcEntity>>

    @Query("SELECT arc_id FROM local_manga_arcs")
    fun observeArcIds(): Flow<List<String>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertArc(arc: LocalMangaArcEntity)

    @Query("DELETE FROM local_manga_arcs WHERE arc_id = :id")
    suspend fun deleteArc(id: String)

    // -- Pages ----------------------------------------------------------------

    @Query("SELECT * FROM local_manga_pages WHERE page_id = :id LIMIT 1")
    suspend fun getPage(id: String): LocalMangaPageEntity?

    @Query("SELECT * FROM local_manga_pages WHERE arc_id = :arcId ORDER BY chapter_number, page_number")
    suspend fun pagesForArc(arcId: String): List<LocalMangaPageEntity>

    @Query("SELECT COUNT(*) FROM local_manga_pages WHERE arc_id = :arcId")
    suspend fun pageCountForArc(arcId: String): Int

    @Query("SELECT COUNT(*) FROM local_manga_pages WHERE arc_id = :arcId")
    fun observePageCountForArc(arcId: String): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPage(page: LocalMangaPageEntity)

    @Query("DELETE FROM local_manga_pages WHERE arc_id = :arcId")
    suspend fun deletePagesForArc(arcId: String)

    // -- Composite ------------------------------------------------------------

    /**
     * Atomarer Delete: erst Pages, dann Arc. CASCADE würde das auch tun, aber
     * explizit ist defensiver gegen DB-Versions-Drift und macht die Intent
     * im Caller offensichtlich.
     */
    @Transaction
    suspend fun deleteArcWithPages(arcId: String) {
        deletePagesForArc(arcId)
        deleteArc(arcId)
    }
}
