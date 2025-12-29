package com.example.atv.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Room database for ATV app.
 */
@Database(
    entities = [ChannelEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AtvDatabase : RoomDatabase() {
    
    abstract fun channelDao(): ChannelDao
    
    companion object {
        const val DATABASE_NAME = "atv_database"
    }
}
