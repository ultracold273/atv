package com.example.atv.di

import android.content.Context
import com.example.atv.player.AtvPlayer
import com.example.atv.player.AtvPlayerController
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
    fun provideAtvPlayerController(
        @ApplicationContext context: Context
    ): AtvPlayerController {
        return AtvPlayer(context)
    }
}
