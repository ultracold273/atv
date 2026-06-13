package com.example.atv.di

import android.content.Context
import com.example.atv.data.local.datastore.UserPreferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import java.time.Clock
import javax.inject.Singleton

/**
 * Hilt module for app-level dependencies.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideUserPreferencesDataStore(
        @ApplicationContext context: Context
    ): UserPreferencesDataStore {
        return UserPreferencesDataStore(context)
    }

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemDefaultZone()

    /**
     * Project-wide [Json] configuration. Inject this rather than constructing your own
     * `Json {}` block — keeping a single configuration prevents subtle parser-vs-parser
     * drift across features.
     *
     *   - `ignoreUnknownKeys = true`  — forward-compat: server adds a field, we don't crash.
     *   - `isLenient = false`         — strict input shape; broken JSON is broken JSON.
     *   - `coerceInputValues = false` — don't paper over null-vs-missing-vs-wrong-type bugs.
     *
     * Mirrors the top-level `AppJson` constant in `CtcResponseParsers.kt`. The top-level
     * exists so the parser is unit-testable without DI; this provider is what gets
     * injected into runtime collaborators (and future spec 005 JSON consumers).
     */
    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = false
        coerceInputValues = false
    }
}
