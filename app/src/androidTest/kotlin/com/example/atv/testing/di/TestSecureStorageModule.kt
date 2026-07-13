package com.example.atv.testing.di

import com.example.atv.di.SecureStorageModule
import com.example.atv.domain.repository.ChannelSourceSettingsStore
import com.example.atv.domain.repository.IptvCredentialsStore
import com.example.atv.testing.fakes.FakeChannelSourceSettingsStore
import com.example.atv.testing.fakes.FakeIptvCredentialsStore
import dagger.Module
import dagger.Provides
import dagger.hilt.components.SingletonComponent
import dagger.hilt.testing.TestInstallIn
import javax.inject.Singleton

@Module
@TestInstallIn(
    components = [SingletonComponent::class],
    replaces = [SecureStorageModule::class]
)
object TestSecureStorageModule {
    @Provides
    @Singleton
    fun provideIptvCredentialsStore(): IptvCredentialsStore = FakeIptvCredentialsStore()

    @Provides
    @Singleton
    fun provideChannelSourceSettingsStore(): ChannelSourceSettingsStore = FakeChannelSourceSettingsStore()
}
