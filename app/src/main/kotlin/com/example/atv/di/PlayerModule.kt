package com.example.atv.di

import android.content.Context
import com.example.atv.player.AtvPlayer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for player dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {
    
    @Provides
    @Singleton
    fun provideAtvPlayer(
        @ApplicationContext context: Context
    ): AtvPlayer {
        return AtvPlayer(context)
    }
}
