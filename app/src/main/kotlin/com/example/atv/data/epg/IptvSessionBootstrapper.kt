package com.example.atv.data.epg

import com.example.atv.di.ApplicationScope
import com.example.atv.domain.usecase.ImportResult
import com.example.atv.domain.usecase.UnifiedImportChannelsUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import javax.inject.Inject
import javax.inject.Singleton

/**
 * App-startup IPTV bootstrap. When the app launches AND credentials are stored
 * AND those credentials are complete, runs [ImportCtcChannelsUseCase] once in
 * the [ApplicationScope] (not any ViewModel scope — must outlive any screen).
 *
 * The outcome is published to [lastResult] so the Settings screen can render
 * a "Last sync failed: …" subtitle when the auto-relogin fails (FR-021).
 *
 * Calling [start] more than once is a no-op after the first invocation
 * (idempotent — there is no scheduled or periodic re-login in 005).
 */
@Singleton
class IptvSessionBootstrapper @Inject constructor(
    private val importChannels: UnifiedImportChannelsUseCase,
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val started = AtomicBoolean(false)

    private val _lastResult = MutableStateFlow<ImportResult?>(null)
    val lastResult: StateFlow<ImportResult?> = _lastResult.asStateFlow()

    fun start() {
        if (!started.compareAndSet(false, true)) return
        scope.launch {
            if (!importChannels.canBootstrap()) {
                Timber.d("IPTV bootstrap: no complete refreshable source, skipping")
                return@launch
            }
            val result = importChannels()
            _lastResult.value = result
            when (result) {
                is ImportResult.Success ->
                    Timber.d("IPTV bootstrap: imported %d channels", result.importedCount)
                is ImportResult.LoginFailure ->
                    Timber.w("IPTV bootstrap: login failed: %s", result.reason)
                is ImportResult.FetchFailure ->
                    Timber.w("IPTV bootstrap: fetch failed: %s", result.reason)
                ImportResult.NoChannelsReturned ->
                    Timber.w("IPTV bootstrap: no channels returned")
            }
        }
    }
}
