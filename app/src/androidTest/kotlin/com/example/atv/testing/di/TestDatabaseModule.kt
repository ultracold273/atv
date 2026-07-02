package com.example.atv.testing.di

import android.content.Context
import androidx.room.Room
import com.example.atv.data.local.db.AtvDatabase
import com.example.atv.data.local.db.ChannelDao
import com.example.atv.di.DatabaseModule
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [DatabaseModule::class]
)
object TestDatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AtvDatabase {
        return Room.inMemoryDatabaseBuilder(context, AtvDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @Provides
    fun provideChannelDao(database: AtvDatabase): ChannelDao = database.channelDao()
}
