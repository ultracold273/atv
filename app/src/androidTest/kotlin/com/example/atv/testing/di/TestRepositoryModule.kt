package com.example.atv.testing.di

import com.example.atv.data.local.db.ChannelDao
import com.example.atv.data.repository.ChannelRepositoryImpl
import com.example.atv.di.RepositoryModule
import com.example.atv.domain.repository.ChannelRepository
import com.example.atv.domain.repository.PreferencesRepository
import com.example.atv.testing.fakes.FakePreferencesRepository
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [RepositoryModule::class]
)
object TestRepositoryModule {
    @Provides
    @Singleton
    fun provideChannelRepository(channelDao: ChannelDao): ChannelRepository {
        return ChannelRepositoryImpl(channelDao)
    }

    @Provides
    @Singleton
    fun providePreferencesRepository(): PreferencesRepository {
        return FakePreferencesRepository()
    }
}
