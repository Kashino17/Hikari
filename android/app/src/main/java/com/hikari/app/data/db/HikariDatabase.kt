package com.hikari.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [FeedItemEntity::class], version = 1, exportSchema = false)
abstract class HikariDatabase : RoomDatabase() {
    abstract fun feedDao(): FeedDao
}
