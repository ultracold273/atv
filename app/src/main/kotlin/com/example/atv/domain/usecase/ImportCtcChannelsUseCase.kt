package com.example.atv.domain.usecase

import com.example.atv.data.epg.CtcAuthClient
import com.example.atv.data.epg.CtcChannelEntry
import com.example.atv.data.epg.CtcChannelFetcher
import com.example.atv.data.epg.CtcEpgProvider
import com.example.atv.data.epg.LoginResult
import com.example.atv.domain.model.Channel
import com.example.atv.domain.repository.ChannelRepository
import com.example.atv.domain.repository.IptvCredentialsStore
import timber.log.Timber
import javax.inject.Inject

/**
 * Orchestrates a one-shot CTC channel import:
 *
 *   read credentials → login → fetch channels → save → flip isConfigured
 *
 * Single source of truth for the import flow. Called by both the manual
 * "Test & import" button (Phase 4 [IptvSettingsViewModel]) and the auto-bootstrapper
 * at app launch (Phase 5 [IptvSessionBootstrapper]). Returns an [ImportResult]
 * sealed class so callers can render granular status without inspecting exceptions.
 *
 * On a failure at any step, NO existing channels are modified and `isConfigured`
 * is NOT flipped — the user's previous state remains usable.
 */
class ImportCtcChannelsUseCase @Inject constructor(
    private val authClient: CtcAuthClient,
    private val channelFetcher: CtcChannelFetcher,
    private val channelRepository: ChannelRepository,
    private val credentialsStore: IptvCredentialsStore,
    private val epgProvider: CtcEpgProvider,
) {
    // Each pipeline step (read → validate → login → fetch → non-empty) fails fast with its
    // own ImportResult, so early returns read more clearly than a nested when-cascade.
    @Suppress("ReturnCount")
    suspend operator fun invoke(): ImportResult {
        val creds = credentialsStore.read()
            ?: return ImportResult.LoginFailure("no credentials stored")
        if (!creds.isComplete) {
            return ImportResult.LoginFailure("incomplete credentials")
        }

        val login = authClient.login(creds)
        if (login is LoginResult.Failure) {
            Timber.d("CTC import login failed: %s", login.reason)
            return ImportResult.LoginFailure(login.reason)
        }
        login as LoginResult.Success

        val fetchResult = channelFetcher.fetch(login)
        val entries = fetchResult.getOrElse { t ->
            Timber.d(t, "CTC import fetch failed")
            return ImportResult.FetchFailure(t.message ?: t::class.simpleName.orEmpty())
        }
        if (entries.isEmpty()) {
            return ImportResult.NoChannelsReturned
        }

        val channels = entries.map { it.toDomainChannel() }
        channelRepository.savePlaylistChannels(channels)
        epgProvider.markConfigured(true)
        return ImportResult.Success(channels.size)
    }

    private fun CtcChannelEntry.toDomainChannel(): Channel = Channel(
        number = displayNumber,
        name = channelName,
        streamUrl = channelUrl,
        groupTitle = null,
        logoUrl = null,
        channelCode = channelId,
    )
}
