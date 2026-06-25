# Feature 007 Plan: Proxy EPG Support

## Architecture

Introduce a routing `EpgProvider` that delegates to:

- `CtcEpgProvider` when `ChannelSourceMode.DIRECT_CTC`
- `ProxyEpgProvider` when `ChannelSourceMode.HOME_PROXY`
- an unconfigured result for `ChannelSourceMode.M3U8`

Playback UI continues to depend only on `EpgProvider`, so no playback-screen logic changes are needed.

## Implementation Tasks

- [x] Add proxy EPG DTOs and parsing in `data/proxy`.
- [x] Add `ProxyEpgProvider` that reads `ChannelSourceSettingsStore`, sends bearer-token requests, and maps DTOs to `Program`.
- [x] Add `ModeAwareEpgProvider` that exposes a combined configured state and delegates fetches by source mode.
- [x] Update Hilt bindings so playback receives `ModeAwareEpgProvider` while Direct CTC import can still mark `CtcEpgProvider` configured.
- [x] Add unit tests for proxy EPG request/parsing and mode routing.
- [x] Run unit tests and static gates.

## Test Plan

- `ProxyChannelClientTest` extended with EPG success, authorization, and structured error cases.
- New `ModeAwareEpgProviderTest` for source-mode routing and configured state.
- Existing `CtcEpgProviderTest` remains unchanged and must pass.

## Rollout

This is backward compatible for existing users:

- Direct CTC mode uses the current provider.
- Home Proxy mode starts showing EPG after users import channels from a proxy that supports `/api/v1/epg/day`.
- M3U8 mode remains without EPG.
