# Implementation Plan: Channel Source Unification

**Branch**: `006-channel-source-unification` | **Date**: 2026-06-24 | **Spec**: [spec.md](spec.md)

## Summary

Replace separate playlist import, IPTV credential setup, proxy setup, and top-level `udpxy` configuration with a unified Channel Source screen. The screen supports M3U8 playlist, Direct CTC, and Home proxy modes. Legacy playlist import entry points are removed or redirected to Channel Source M3U8 mode. A source-aware import use case saves channels through the existing repository, and a new stream URL resolver converts multicast source URLs into playable URLs before `AtvPlayer` is called.

## Technical Context

**Language/Version**: Kotlin 2.1.0  
**Primary Dependencies**: Existing OkHttp, Hilt, Room, DataStore, EncryptedSharedPreferences, kotlinx.serialization  
**Testing**: JUnit 5, MockK, MockWebServer, ViewModel tests  
**Target Platform**: Android TV API 29+  
**Storage**: Existing preferences plus encrypted IPTV credential storage need to evolve into source-aware settings  
**Constraints**: Preserve existing M3U8 and Direct CTC behavior; remove duplicate playlist import UI; do not log provider credentials or proxy token

## Architecture

```text
ChannelSourceSettingsStore
        ↓
UnifiedImportChannelsUseCase
        ↓                 ↓                   ↓
M3U8 source          Direct CTC source     Home proxy source
LoadPlaylistUseCase  CtcAuthClient         ProxyChannelClient
ParseM3U8UseCase     CtcChannelFetcher     /api/v1/channels
        ↓                 ↓                   ↓
        ChannelRepository.savePlaylistChannels(...)

PlaybackViewModel
        ↓
StreamUrlResolver
        ↓
AtvPlayer.play(playableUrl)
```

## Project Structure

```text
app/src/main/kotlin/com/example/atv/
├── domain/model/
│   ├── ChannelSourceMode.kt              # NEW
│   ├── ChannelSourceConfig.kt            # NEW
│   ├── ProxySettings.kt                  # NEW
│   └── IptvCredentials.kt                # existing direct mode
├── domain/repository/
│   └── ChannelSourceSettingsStore.kt     # NEW or evolved credentials store
├── domain/usecase/
│   ├── UnifiedImportChannelsUseCase.kt   # NEW or renamed import use case
│   └── ResolveStreamUrlUseCase.kt        # NEW
├── domain/util/
│   └── UdpxyUrlRewriter.kt               # keep as pure helper
├── data/local/secure/
│   └── ChannelSourceSettingsStoreImpl.kt # NEW or evolved encrypted store
├── data/proxy/
│   ├── ProxyChannelClient.kt             # NEW
│   └── ProxyDtos.kt                      # NEW
└── ui/screens/iptv/
    ├── IptvSettingsUiState.kt            # evolve to source setup state
    ├── IptvSettingsViewModel.kt          # source-mode aware
    └── IptvSettingsScreen.kt             # tab/segmented source UI
```

## Phase 1: Source Model & Migration

### Goals

Represent all channel sources explicitly while preserving existing user data.

### Tasks

- [ ] Add `ChannelSourceMode` with `M3U8`, `DIRECT_CTC`, and `HOME_PROXY`.
- [ ] Add source-specific settings models: M3U8, Direct CTC, Home proxy.
- [ ] Design `ChannelSourceSettingsStore` or evolve existing stores with minimal churn.
- [ ] Migrate existing CTC credentials into Direct CTC settings.
- [ ] Migrate existing `UserPreferences.udpxyProxy` into Direct CTC and/or M3U8 resolver settings.
- [ ] Decide default source for fresh installs and upgrades with no stored CTC credentials.
- [ ] Add tests for settings round-trips and migration behavior.

## Phase 2: Stream URL Resolver

### Goals

Move playback URL rewriting out of `AtvPlayer`.

### Tasks

- [ ] Add `ResolveStreamUrlUseCase` or `StreamUrlResolver` that wraps `UdpxyUrlRewriter`.
- [ ] Resolve multicast URLs through `udpxy` for M3U8 and Direct CTC modes.
- [ ] Treat Home proxy `streamUrl` values as already playable by default.
- [ ] Update `PlaybackViewModel` to resolve a playable URL before calling the player.
- [ ] Change `AtvPlayer` so it accepts a final playable URL and no longer imports `UdpxyUrlRewriter`.
- [ ] Add unit tests for resolver behavior and playback ViewModel wiring.

## Phase 3: Unified Import Use Case

### Goals

Route imports by source mode and preserve existing transactional save behavior.

### Tasks

- [ ] Add `UnifiedImportChannelsUseCase` or refactor `ImportCtcChannelsUseCase` into a source-aware use case.
- [ ] Wire M3U8 mode to the existing playlist load/parse flow.
- [ ] Wire Direct CTC mode to the existing `CtcAuthClient` and `CtcChannelFetcher` flow.
- [ ] Wire Home proxy mode to the new proxy client.
- [ ] Preserve failure behavior: existing channels remain untouched on failure.
- [ ] Preserve concurrent import coalescing from the settings ViewModel.
- [ ] Add tests proving each mode calls only its own import path.

## Phase 4: Proxy API Client

### Goals

Fetch normalized channels from the home proxy and map failures cleanly.

### Tasks

- [ ] Add DTOs for channel response, cache metadata, and structured errors.
- [ ] Implement `ProxyChannelClient.fetchChannels(settings)` using OkHttp.
- [ ] Send `Authorization: Bearer <token>`.
- [ ] Parse channel DTOs directly into `Channel` or an intermediate source entry.
- [ ] Treat proxy `streamUrl` as final/playable.
- [ ] Map `401` to authorization failure.
- [ ] Map `503` and structured proxy errors to fetch failures.
- [ ] Treat malformed JSON and empty channel arrays as safe failures.
- [ ] Add MockWebServer tests for success, stale success, unauthorized, backend unavailable, malformed JSON, and network failure.

## Phase 5: Channel Source UI

### Goals

Create one setup screen for all channel source modes.

### Tasks

- [ ] Rename or reposition IPTV setup as Channel Source setup in Settings.
- [ ] Add tab/segmented selector for M3U8 playlist, Direct CTC, and Home proxy.
- [ ] Move existing playlist import controls into M3U8 mode.
- [ ] Remove the top-level Settings `Load New Playlist` row and replace it with the Channel Source row.
- [ ] Update playback overlay `Load Playlist` action to open Channel Source with M3U8 selected.
- [ ] Update first-run/no-channel setup primary action to open Channel Source with M3U8 selected.
- [ ] Delete obsolete standalone playlist setup UI once all navigation paths are migrated, or leave only a redirect wrapper during the transition.
- [ ] Update navigation constants/routes so legacy playlist routes cannot be reached as parallel user-facing screens.
- [ ] Render Direct CTC credentials and `udpxy` field only in Direct CTC mode.
- [ ] Render Proxy URL and masked Access token only in Home proxy mode.
- [ ] Remove the standalone top-level `udpxy` settings row after migration.
- [ ] Update validation and button enabled state by source mode.
- [ ] Update clear settings behavior to define active-source vs all-source clearing.
- [ ] Localize all new strings in `values/strings.xml` and `values-zh/strings.xml`.
- [ ] Add ViewModel tests for mode switching, validation, status messages, and clear flow.
- [ ] Add navigation/UI tests or focused ViewModel tests proving legacy playlist entry points land in Channel Source M3U8 mode.

## Phase 6: Startup Bootstrap

### Goals

Make app-start refresh source-mode aware.

### Tasks

- [ ] Update `IptvSessionBootstrapper` or replace it with a source-aware bootstrapper.
- [ ] In M3U8 mode, refresh only when the source is URL-backed and refresh policy permits it.
- [ ] In Direct CTC mode, keep existing app-start re-login behavior.
- [ ] In Home proxy mode, call proxy import once when settings are complete and skip direct CTC authentication.
- [ ] Surface last sync failure consistently for all refreshable modes.
- [ ] Add bootstrap tests for every source mode and incomplete settings.

## Phase 7: EPG Follow-Up Decision

### Goals

Decide how EPG availability maps to source modes.

### Tasks

- [ ] Keep Direct CTC EPG behavior unchanged.
- [ ] If proxy server ships EPG endpoints in v1, add `ProxyEpgProvider` consuming `/api/v1/epg/current` and `/api/v1/epg/day`.
- [ ] If proxy server ships channel-only first, keep EPG disabled or direct-only in Home proxy mode and document that limitation in status text.
- [ ] Keep M3U8 mode EPG behavior unchanged unless XMLTV support is added later.
- [ ] Add tests for whichever policy is chosen.

## Phase 8: Verification

### Goals

Protect existing behavior while changing the setup foundation.

### Tasks

- [ ] Run resolver, settings, import use case, proxy client, and bootstrapper unit tests.
- [ ] Run existing playlist parser/import tests.
- [ ] Run existing CTC auth/channel fetch tests.
- [ ] Verify top-level Settings, playback overlay, and no-channel setup no longer expose a separate playlist import flow.
- [ ] Run `./studio-gradlew testDebugUnitTest`.
- [ ] Run lint/detekt if this feature touches UI or DI broadly.
- [ ] Manually verify M3U8, Direct CTC, and Home proxy flows once the proxy server is runnable.

## Open Questions

- Should clearing source settings clear only the active source or all saved source configurations?
- Should stale proxy channel data be treated as full success, success-with-warning, or a distinct `ImportResult`?
- Should M3U8 URL refresh run automatically at app startup, or only manual imports?
- Should proxy mode require HTTPS for non-RFC1918 URLs?
- Should M3U8 and Direct CTC share one `udpxy` value or keep separate source-specific values?

## Complexity Tracking

This is a foundation refactor. Keep implementation phased: first models/migration, then resolver, then source-aware import, then UI. The riskiest areas are preserving upgrade behavior and avoiding regressions in existing playlist/direct CTC imports.
