package com.example.atv.di

import android.content.Context
import androidx.room.Room
import com.example.atv.data.local.db.AtvDatabase
import com.example.atv.data.local.db.ChannelDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for database dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): AtvDatabase {
        return Room.databaseBuilder(
            context,
            AtvDatabase::class.java,
            AtvDatabase.DATABASE_NAME
        ).build()
    }
    
    @Provides
    fun provideChannelDao(database: AtvDatabase): ChannelDao {
        return database.channelDao()
    }
}
