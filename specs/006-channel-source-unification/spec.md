# Feature Specification: Channel Source Unification

**Feature Branch**: `006-channel-source-unification`  
**Created**: 2026-06-24  
**Status**: Draft  
**Input**: Replace separate playlist/IPTV/proxy configuration surfaces with one channel-source setup flow that supports M3U8 playlist, Direct CTC, and Home proxy modes.

## Overview

The app currently has separate concepts for playlist import, direct CTC IPTV setup, and `udpxy` playback rewriting. The upcoming home proxy feature makes that split more confusing: the user is really choosing where channels come from, then configuring the source-specific fields needed to import or refresh them.

This feature introduces a unified Channel Source setup screen with three modes: M3U8 playlist, Direct CTC, and Home proxy. Each mode owns its own import controls and configuration. The previous standalone playlist import/settings surfaces are retired so users do not have two places to manage channel sources. `udpxy` moves out of the top-level Settings screen and becomes a playback option for modes that may return multicast stream URLs.

The playback pipeline also becomes cleaner: channel import stores canonical source URLs where appropriate, a dedicated stream URL resolver converts source URLs to playable URLs, and `AtvPlayer` receives only the final playback URL.

## User Scenarios & Testing

### User Story 1 - Import an M3U8 Playlist From Channel Source Setup (Priority: P1)

As a user with an M3U8 playlist, I want to configure and import it from the same Channel Source screen as other source types, so channel setup has one obvious home.

**Independent Test**: Select M3U8 playlist mode, provide a playlist URL or existing file import input, import channels, and verify playback still works for HTTP and multicast playlist entries.

**Acceptance Scenarios**:

1. **Given** I open Channel Source setup, **When** M3U8 playlist mode is selected, **Then** I see playlist import controls and an optional `udpxy` playback proxy field.
2. **Given** I provide a valid M3U8 playlist, **When** I import it, **Then** the parsed channels replace playlist-managed channels through the existing repository path.
3. **Given** the playlist contains `igmp://` or `rtp://` stream URLs, **When** `udpxy` is configured, **Then** playback resolves those URLs through `udpxy` before handing them to the player.
4. **Given** the playlist import fails, **When** the error is shown, **Then** existing channels remain unchanged.

### User Story 2 - Retire Legacy Playlist Import Surfaces (Priority: P1)

As a user, I want playlist import to live only in Channel Source setup, so I do not have to understand multiple competing ways to replace channels.

**Independent Test**: Open top-level Settings, playback settings overlay, and first-run setup/no-channel flow; verify playlist import actions route to Channel Source M3U8 mode and no legacy standalone playlist import page remains reachable.

**Acceptance Scenarios**:

1. **Given** I open top-level Settings, **When** I view the menu rows, **Then** there is no standalone Load New Playlist row; there is a Channel Source row instead.
2. **Given** I open the playback settings overlay, **When** I choose a load/source action, **Then** it opens Channel Source setup rather than a separate playlist picker flow.
3. **Given** the app has no channels configured, **When** the setup/no-channel screen is shown, **Then** its primary action opens Channel Source setup with M3U8 mode selected by default.
4. **Given** old navigation routes or callbacks still exist internally during migration, **When** they are invoked, **Then** they redirect to Channel Source setup rather than duplicating playlist import UI.

### User Story 3 - Keep Direct CTC Import in the Same Source Screen (Priority: P1)

As a direct CTC user, I want my existing provider credential flow to keep working inside the unified source screen.

**Independent Test**: Select Direct CTC mode, enter provider credentials and `udpxy`, run Test & import against MockWebServer, and verify the existing login/channel fetch behavior still passes.

**Acceptance Scenarios**:

1. **Given** Direct CTC mode is selected, **When** I open Channel Source setup, **Then** I see User ID, Password, STB ID, Local IP, Local MAC, Auth server URL, and `udpxy` proxy fields.
2. **Given** valid direct credentials are saved, **When** I tap Test & import, **Then** the existing direct CTC login and channel fetch path runs.
3. **Given** a direct-mode channel contains a multicast URL, **When** playback starts, **Then** a stream URL resolver rewrites it through the configured `udpxy` value before calling `AtvPlayer`.
4. **Given** a user upgrades from the current app, **When** existing IPTV credentials and `udpxy` preferences are present, **Then** they are migrated into Direct CTC source settings.

### User Story 4 - Import Channels Through Home Proxy (Priority: P1)

As a user with a home IPTV proxy, I want to enter only the proxy URL and local token, so this Android TV can import channels without provider credentials.

**Independent Test**: Select Home proxy mode, enter a MockWebServer proxy URL and token, tap Test & import, and verify channels are saved without invoking `CtcAuthClient.login()`.

**Acceptance Scenarios**:

1. **Given** Home proxy mode is selected, **When** I open Channel Source setup, **Then** I see Proxy URL and Access token fields.
2. **Given** proxy fields are valid, **When** I tap Test & import, **Then** the app requests channels from `/api/v1/channels` using `Authorization: Bearer <token>`.
3. **Given** proxy import succeeds, **When** I return to playback, **Then** the returned channel `streamUrl` values are treated as already playable.
4. **Given** proxy mode is active, **When** playback starts, **Then** local `udpxy` rewriting is not applied to proxy-returned HTTP stream URLs.

### User Story 5 - Resolve Playable URLs Outside the Player (Priority: P2)

As a maintainer, I want stream URL rewriting outside `AtvPlayer`, so playback code stays focused on media lifecycle and ExoPlayer state.

**Independent Test**: Unit test `StreamUrlResolver` for HTTP passthrough, `igmp://` rewrite, `rtp://` rewrite, blank `udpxy`, and proxy-mode passthrough; verify `AtvPlayer` no longer depends on `UdpxyUrlRewriter`.

**Acceptance Scenarios**:

1. **Given** a channel has an HTTP stream URL, **When** playback starts, **Then** the resolver returns it unchanged.
2. **Given** a channel has `igmp://239.0.0.1:1234` and `udpxy` is configured, **When** playback starts, **Then** the resolver returns `http://<udpxy>/udp/239.0.0.1:1234`.
3. **Given** `udpxy` is blank, **When** a multicast URL is resolved, **Then** the source URL is returned unchanged and player error handling remains responsible for any playback failure.
4. **Given** Home proxy mode is active, **When** a proxy-returned URL is resolved, **Then** the URL is treated as final unless it is unexpectedly multicast and an explicit fallback policy is later added.

### User Story 6 - Source-Aware Startup Refresh (Priority: P2)

As a user, I want app launch refresh to use the active source, so channel updates propagate consistently without extra setup steps.

**Independent Test**: Store each source mode in turn and verify bootstrap calls only that mode's refresh/import path.

**Acceptance Scenarios**:

1. **Given** M3U8 mode is active with a refreshable URL, **When** the app launches, **Then** the bootstrap may refresh that playlist according to the source policy.
2. **Given** Direct CTC mode is active with complete credentials, **When** the app launches, **Then** direct CTC refresh runs as it does today.
3. **Given** Home proxy mode is active with complete settings, **When** the app launches, **Then** proxy refresh runs and direct CTC authentication is not called.
4. **Given** refresh fails for any source, **When** Settings is opened, **Then** the last sync failure is visible and existing channels remain usable.

## Functional Requirements

### Source Mode & UI

- **FR-001**: The app MUST add a unified Channel Source setup screen reachable from Settings.
- **FR-002**: The screen MUST provide three source modes: `M3U8 playlist`, `Direct CTC`, and `Home proxy`.
- **FR-003**: The source mode control SHOULD be rendered as tabs or a segmented selector suitable for TV remote navigation.
- **FR-004**: The old standalone Settings row for loading a new playlist MUST be removed and replaced by the Channel Source entry.
- **FR-005**: Any playback overlay action that previously loaded a playlist directly MUST open Channel Source setup with M3U8 mode selected.
- **FR-006**: The first-run/no-channel setup screen MUST use Channel Source setup as the canonical path for playlist import rather than maintaining a separate playlist setup page.
- **FR-007**: Legacy playlist import routes/callbacks MUST be deleted when safe or converted into redirects to Channel Source setup.
- **FR-008**: The old top-level `udpxy` setting MUST be removed from top-level Settings after migration.
- **FR-009**: All new source-mode strings MUST be localized in English and Chinese resources.

### M3U8 Playlist Mode

- **FR-010**: M3U8 mode MUST expose the existing playlist import capability from the unified source screen.
- **FR-011**: M3U8 mode MUST preserve existing playlist parsing behavior and failure handling.
- **FR-012**: M3U8 mode MUST expose an optional `udpxy` proxy field for multicast playlist entries.
- **FR-013**: If a playlist source is refreshable by URL, the app SHOULD store enough source metadata to refresh it later.
- **FR-014**: M3U8 mode MUST be the only user-facing place to import or replace an M3U8 playlist after this feature lands.

### Direct CTC Mode

- **FR-015**: Direct CTC mode MUST expose User ID, Password, STB ID, Local IP, Local MAC, Auth server URL, and `udpxy` proxy fields.
- **FR-016**: Direct CTC validation MUST preserve existing rules for provider credentials.
- **FR-017**: Direct CTC import MUST continue using `CtcAuthClient` and `CtcChannelFetcher`.
- **FR-018**: Direct CTC import SHOULD store provider/raw stream URLs, not `udpxy`-rewritten URLs, so changing `udpxy` does not require re-import.

### Home Proxy Mode

- **FR-019**: Home proxy mode MUST expose Proxy URL and Access token fields.
- **FR-020**: Proxy URL MUST validate as an HTTP or HTTPS URL.
- **FR-021**: Access token MUST be non-blank and visually masked.
- **FR-022**: Proxy settings MUST be stored in encrypted storage or an equivalent secure store used for IPTV secrets.
- **FR-023**: The app MUST implement a proxy client for `GET /api/v1/channels`.
- **FR-024**: The proxy client MUST send `Authorization: Bearer <accessToken>`.
- **FR-025**: The proxy client MUST parse normalized channel JSON into the existing `Channel` domain model, preserving channel number, name, stream URL, and channel code.
- **FR-026**: In Home proxy mode, returned `streamUrl` values MUST be treated as already playable by default.
- **FR-027**: The app MUST NOT require local `udpxy` settings for Home proxy mode.

### Storage & Migration

- **FR-028**: The app MUST introduce a persisted active channel source mode.
- **FR-029**: Existing users with stored CTC credentials MUST migrate to Direct CTC mode.
- **FR-030**: Existing `udpxy` preference MUST migrate into Direct CTC settings and M3U8 settings where practical, or into a shared resolver config used by both modes.
- **FR-031**: Existing channels MUST remain usable after migration.
- **FR-032**: Clearing source settings MUST clearly define whether it clears only the active source or all saved source configurations.
- **FR-033**: The app MUST NOT log provider credentials or proxy access tokens.

### Import Flow

- **FR-034**: A unified import use case MUST branch by source mode: M3U8 parses playlist data, Direct CTC uses backend login/fetch, and Home proxy uses the proxy API client.
- **FR-035**: Successful imports from all modes MUST persist channels through `ChannelRepository.savePlaylistChannels(channels)` or the existing equivalent transactional path.
- **FR-036**: Failed imports from any mode MUST NOT modify existing channels.
- **FR-037**: Concurrent import activations MUST remain coalesced across all source modes.
- **FR-038**: Source-specific failures MUST map to user-visible status messages without exposing secrets.

### Stream URL Resolution

- **FR-039**: The app MUST introduce a `StreamUrlResolver` or equivalent use case outside `AtvPlayer`.
- **FR-040**: `AtvPlayer` MUST receive final playable URLs and MUST NOT perform `udpxy` rewriting itself.
- **FR-041**: The resolver MUST rewrite `igmp://` and `rtp://` URLs through configured `udpxy` for M3U8 and Direct CTC modes.
- **FR-042**: The resolver MUST pass HTTP(S), file, RTSP, and other non-multicast URLs through unchanged.
- **FR-043**: The resolver MUST tolerate `udpxy` values with or without `http://`, `https://`, and trailing slash, preserving current behavior.

### Startup Refresh

- **FR-044**: App-start bootstrap MUST read the active channel source and only run that source's refresh path when settings are complete.
- **FR-045**: Home proxy bootstrap MUST NOT call direct CTC authentication.
- **FR-046**: Bootstrap failure MUST surface through the same last-sync failure mechanism for all refreshable source modes.

### Testing

- **FR-047**: Unit tests MUST cover source mode validation and settings state transitions.
- **FR-048**: Unit tests MUST cover `StreamUrlResolver` behavior for HTTP passthrough, multicast rewrite, blank `udpxy`, and proxy-mode URLs.
- **FR-049**: Import use case tests MUST verify each source mode chooses the correct import path.
- **FR-050**: Proxy client tests MUST cover success, `401`, `503`, malformed JSON, unreachable host, and stale cache response parsing.
- **FR-051**: Bootstrapper tests MUST verify source-aware refresh behavior.
- **FR-052**: Navigation/UI tests SHOULD verify legacy playlist import entry points open Channel Source M3U8 mode rather than a separate playlist page.

## Proxy API Contract

Initial expected channel response from the home proxy:

```json
{
  "data": [
    {
      "number": 1,
      "name": "CCTV-1",
      "streamUrl": "http://openwrt:4022/udp/239.0.0.1:1234",
      "channelCode": "cctv1"
    }
  ],
  "cache": {
    "stale": false,
    "cachedAt": "2026-06-24T20:00:00+08:00",
    "ttlSeconds": 3600
  }
}
```

Initial expected error response:

```json
{
  "error": {
    "code": "backend_unavailable",
    "message": "Backend refresh failed and no channel cache is available"
  }
}
```

## Key Entities

- **ChannelSourceMode**: Enum-like setting: `M3U8`, `DIRECT_CTC`, or `HOME_PROXY`.
- **ChannelSourceConfig**: Source-specific settings for M3U8, Direct CTC, and Home proxy.
- **M3u8SourceSettings**: Playlist URL/import metadata and optional `udpxy` proxy.
- **DirectCtcSourceSettings**: Provider credentials plus optional `udpxy` proxy.
- **HomeProxySourceSettings**: Proxy URL and local access token.
- **StreamUrlResolver**: Converts stored source URLs into final playable URLs before playback.
- **ProxyChannelClient**: HTTP client that fetches normalized channel data from the home proxy.
- **UnifiedImportChannelsUseCase**: Source-aware import flow that saves channels through the existing repository.

## Success Criteria

- **SC-001**: A user can import an M3U8 playlist, direct CTC channels, or home-proxy channels from one Channel Source setup screen.
- **SC-002**: Existing direct CTC users and existing `udpxy` settings migrate without losing playback ability.
- **SC-003**: `AtvPlayer` no longer performs `udpxy` rewriting; tests prove URL resolution happens before playback.
- **SC-004**: A proxy-mode user can import channels without entering provider credentials on the Android TV client.
- **SC-005**: Direct CTC mode continues passing existing CTC auth/channel fetch tests.
- **SC-006**: Automated tests cover source-mode branching, proxy failure flows, and stream URL resolution.

## Assumptions

- The current app has existing playlist import entry points in setup/settings/playback overlay surfaces; this feature consolidates those into Channel Source M3U8 mode.
- The proxy server is reachable from the Android TV device over the home LAN.
- The home proxy returns `streamUrl` values that are already playable by ATV.
- HTTP is acceptable on a trusted LAN if the proxy is LAN-firewalled and tokens are high entropy.

## Out of Scope

- Building the proxy server in this Android repo.
- QR-code pairing or automatic token enrollment.
- Transparent emulation of provider CTC login endpoints.
- Multi-source merged channel lists.
- Provider credential migration from Android direct mode into the router proxy.
- Keeping the old standalone playlist import page as a parallel user-facing flow.

## Technical Decisions

- **Unified source model**: M3U8, Direct CTC, and Home proxy are all channel sources, so they share one setup surface and import contract.
- **Canonical storage plus resolver**: Direct and M3U8 modes keep source URLs in storage; `StreamUrlResolver` creates playable URLs at playback time.
- **Proxy returns playable URLs**: Home proxy mode treats `streamUrl` as final because the router owns multicast and `udpxy` topology.
- **Player stays media-focused**: `AtvPlayer` should not know about `igmp://`, `rtp://`, or `udpxy`.
