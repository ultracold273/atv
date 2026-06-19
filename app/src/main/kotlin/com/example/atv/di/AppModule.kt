package com.example.atv.di

import android.content.Context
import com.example.atv.data.local.datastore.UserPreferencesDataStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.serialization.json.Json
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * Qualifies the application-lifecycle [CoroutineScope] so it doesn't collide with
 * other scopes that may be provided later.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

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
     * Coroutine scope tied to the Application lifecycle. Used for work that must
     * outlive any ViewModel — currently the IPTV auto re-login bootstrap (Phase 5).
     *
     * SupervisorJob so a failed child coroutine does not cancel the scope or its
     * siblings; Dispatchers.Default because the bootstrap is CPU + IO mixed and
     * shouldn't run on the main thread.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)

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
