package com.example.atv.testing.di

import android.content.Context
import com.example.atv.di.PlayerModule
import com.example.atv.player.AtvPlayer
import com.example.atv.testing.fakes.FakeAtvPlayer
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
    replaces = [PlayerModule::class]
)
object TestPlayerModule {
    @Provides
    @Singleton
    fun provideAtvPlayer(@ApplicationContext context: Context): AtvPlayer {
        return FakeAtvPlayer(context)
    }
}
