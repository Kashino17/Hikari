package com.hikari.app.data.db

import androidx.sqlite.db.SupportSQLiteDatabase
import io.mockk.every
import io.mockk.mockk
import java.lang.reflect.Modifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class Migration20To21Test {

    private fun runMigration(): List<String> {
        val statements = mutableListOf<String>()
        val db = mockk<SupportSQLiteDatabase>()
        every { db.execSQL(capture(statements)) } returns Unit
        MIGRATION_20_21.migrate(db)
        return statements
    }

    private fun columnsOf(sqlList: String): List<String> =
        Regex("`([A-Za-z]+)`").findAll(sqlList).map { it.groupValues[1] }.toList()

    @Test fun rebuildsFeedItemsWithExactlyTheEntityColumns() {
        val create = runMigration().first()
        assertTrue(create.startsWith("CREATE TABLE IF NOT EXISTS `feed_items_new`"))
        val created = create.lines()
            .map { it.trim() }
            .filter { it.startsWith("`") }
            .map { it.substringAfter("`").substringBefore("`") }
        val entityFields = FeedItemEntity::class.java.declaredFields
            .filterNot { Modifier.isStatic(it.modifiers) }
            .map { it.name }
        assertEquals(entityFields.toSet(), created.toSet())
        listOf("kind", "parentVideoId", "source").forEach { assertFalse(it in created) }
    }

    @Test fun copiesEveryColumnButDropsClipItems() {
        val (create, insert) = runMigration()
        val created = create.lines().map { it.trim() }.filter { it.startsWith("`") }
            .map { it.substringAfter("`").substringBefore("`") }
        val target = columnsOf(insert.substringAfter("(").substringBefore(")"))
        val selected = columnsOf(insert.substringAfter("SELECT").substringBefore("FROM"))
        assertEquals(created, target)
        assertEquals(created, selected)
        assertTrue(insert.trimEnd().endsWith("WHERE `kind` != 'clip'"))
    }

    @Test fun swapsTablesLast() {
        val statements = runMigration()
        assertEquals(4, statements.size)
        assertEquals("DROP TABLE `feed_items`", statements[2])
        assertEquals("ALTER TABLE `feed_items_new` RENAME TO `feed_items`", statements[3])
    }
}
