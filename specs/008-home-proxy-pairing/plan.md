# Implementation Plan: Home Proxy Pairing

**Branch**: `008-home-proxy-pairing` | **Date**: 2026-06-27 | **Spec**: [spec.md](spec.md)

## Summary

Add a TV-friendly pairing flow to Home Proxy mode. The Android client creates a short-lived pairing session with the proxy server, displays a short code, polls for admin approval, and saves the returned access token through the existing secure Home Proxy settings path. Manual token entry remains available, and the existing channel/EPG bearer-token APIs remain unchanged.

## Technical Context

**Language/Version**: Kotlin 2.1.0  
**Primary Dependencies**: Existing OkHttp, Hilt, kotlinx.serialization, coroutines, EncryptedSharedPreferences-backed source settings  
**Testing**: JUnit 5, MockK, MockWebServer, ViewModel coroutine tests  
**Target Platform**: Android TV API 29+  
**Storage**: Existing `ChannelSourceSettingsStore` stores `ProxySettings(proxyBaseUrl, accessToken)` securely  
**Constraints**: Do not log tokens or client nonces; keep manual token entry working; do not change channel or EPG API contracts

## Architecture

```text
Home Proxy tab
      ↓
IptvSettingsViewModel
      ↓
ProxyPairingClient
      ↓
POST /api/v1/pairing/sessions
GET  /api/v1/pairing/sessions/{sessionId}
      ↓ approved
ChannelSourceSettingsStore.saveProxySettings(...)
      ↓
Existing Home Proxy import and EPG clients
```

The pairing feature is an enrollment layer. After approval, the app has the same `ProxySettings` shape it already uses today.

## Project Structure

```text
app/src/main/kotlin/com/example/atv/
├── data/proxy/
│   ├── ProxyPairingClient.kt             # NEW
│   ├── ProxyPairingDtos.kt               # NEW, or merged into ProxyDtos.kt
│   └── ProxyChannelClient.kt             # existing channel/EPG client remains unchanged
├── domain/model/
│   └── ProxyPairingModels.kt             # NEW if domain-level session/status models are useful
└── ui/screens/iptv/
    ├── IptvSettingsUiState.kt            # add pairing state fields
    ├── IptvSettingsViewModel.kt          # create/poll/cancel pairing
    └── IptvSettingsScreen.kt             # Home Proxy pairing controls
```

## Phase 1: Pairing Models and API Contract

### Goals

Represent pairing state explicitly and define serialization boundaries.

### Tasks

- [ ] Add DTOs for create-session request, create-session response, poll response, and structured proxy errors.
- [ ] Add a sealed pairing result/status model for client code: pending, approved, rejected, expired, and failure.
- [ ] Add a UI-facing pairing state model or fields that represent idle, creating, pending, approved, rejected, expired, cancelling, and error.
- [ ] Decide whether pairing DTOs live in `ProxyDtos.kt` or a dedicated `ProxyPairingDtos.kt` file.
- [ ] Add unit tests for DTO serialization and deserialization, especially approved, rejected, expired, and structured errors.

## Phase 2: Proxy Pairing Client

### Goals

Create and poll pairing sessions against the Home Proxy base URL.

### Tasks

- [ ] Implement `ProxyPairingClient.createSession(proxyBaseUrl, request)` with OkHttp.
- [ ] Implement `ProxyPairingClient.pollSession(proxyBaseUrl, sessionId, clientNonce)` with OkHttp.
- [ ] Build URLs by trimming trailing slashes and appending `/api/v1/pairing/sessions`.
- [ ] Send `Content-Type: application/json` on create-session requests.
- [ ] Send `X-Client-Nonce` on poll requests.
- [ ] Parse structured proxy errors using the same error style as existing proxy clients.
- [ ] Map `401`/`403` to authorization failure, `404` to missing or expired session, and malformed responses to safe failures.
- [ ] Avoid logging tokens, nonce values, or approved payload bodies.
- [ ] Add MockWebServer tests for successful create, pending poll, approved poll, rejected poll, expired poll, unauthorized, malformed JSON, and network failure.

## Phase 3: ViewModel Pairing State Machine

### Goals

Wire pairing into the existing Home Proxy setup flow without disturbing manual token import.

### Tasks

- [ ] Inject `ProxyPairingClient` into `IptvSettingsViewModel`.
- [ ] Add `startProxyPairing()` that validates Proxy URL, creates a random client nonce, creates a pairing session, and enters pending state.
- [ ] Add polling loop scoped to `viewModelScope`.
- [ ] Respect server-provided `pollIntervalSeconds` with a client-side minimum interval.
- [ ] Stop polling on approval, rejection, expiry, error, cancellation, new pairing start, manual token edit, manual import, or ViewModel clear.
- [ ] On approved response, validate the locally configured `proxyBaseUrl`, returned `accessToken`, and token type before saving.
- [ ] Save approved settings with `sourceSettingsStore.saveProxySettings(ProxySettings(localProxyBaseUrl, accessToken))`.
- [ ] Update UI state with the returned token so the existing Test & import button becomes valid.
- [ ] Save `ChannelSourceMode.HOME_PROXY` on successful pairing if the feature chooses to activate mode immediately.
- [ ] Decide whether approval automatically calls existing Home Proxy import or only enables Test & import.
- [ ] Ensure failure, rejection, expiry, and cancellation never clear an existing stored token.
- [ ] Add ViewModel tests for the complete happy path and each failure/cancellation path.

## Phase 4: Home Proxy TV UI

### Goals

Make pairing discoverable and usable with a TV remote.

### Tasks

- [ ] Add a Generate pairing code button to Home Proxy mode.
- [ ] Keep the existing Proxy URL and masked Access token fields visible or place manual token entry behind an advanced section if the UI needs less clutter.
- [ ] Disable Generate pairing code while Proxy URL is invalid or a create request is in progress.
- [ ] Show the pairing code in large grouped digits while pending.
- [ ] Show expiration or time remaining when available.
- [ ] Show pending, approved, rejected, expired, cancelled, and error states with concise copy.
- [ ] Add a Cancel pairing or Generate new code action while pending.
- [ ] Prevent stale approved responses from overwriting manual edits after cancellation or new pairing.
- [ ] Keep Test & import enabled for complete manually entered or paired settings.
- [ ] Localize all new strings in `values/strings.xml` and `values-zh/strings.xml`.
- [ ] Verify focus order with D-pad navigation: Proxy URL, Generate code, token field/manual import, cancel/retry where applicable.

## Phase 5: Security and Token Handling

### Goals

Keep pairing secrets short-lived and keep final tokens in the existing secure path.

### Tasks

- [ ] Generate client nonce with `SecureRandom` and at least 128 bits of entropy; prefer 256 bits.
- [ ] Keep client nonce in memory only for the active pairing session.
- [ ] Redact access token, nonce, and approval payload in all logs.
- [ ] Validate the locally configured `proxyBaseUrl` as HTTP or HTTPS before storing.
- [ ] Validate approved `accessToken` is non-blank before storing.
- [ ] Treat unsupported `tokenType` values as failure unless a fallback is explicitly required.
- [ ] Confirm pairing failure does not call `clearProxySettings()`.
- [ ] Confirm clear-credentials flow still clears paired and manual proxy tokens intentionally.

## Phase 6: Integration With Existing Import Flow

### Goals

Use paired tokens through the already implemented Home Proxy paths.

### Tasks

- [ ] Confirm `IptvSettingsUiState.asProxySettings` works for paired token values.
- [ ] Confirm `importHomeProxy()` saves the paired settings and calls `UnifiedImportChannelsUseCase(ChannelSourceMode.HOME_PROXY)`.
- [ ] Keep `ProxyChannelClient.fetchChannels()` unchanged unless shared error parsing is extracted.
- [ ] Keep `ProxyEpgProvider` behavior unchanged.
- [ ] Add or update tests proving pairing success followed by Test & import uses the existing bearer token header.
- [ ] Preserve existing manual-token `IptvSettingsViewModelTest` coverage.

## Phase 7: Verification

### Goals

Protect existing source setup behavior and prove pairing is stable.

### Tasks

- [ ] Run pairing client tests.
- [ ] Run Home Proxy ViewModel tests.
- [ ] Run existing proxy channel and EPG client tests.
- [ ] Run existing settings ViewModel tests.
- [ ] Run `./studio-gradlew testDebugUnitTest`.
- [ ] Run lint/detekt if UI or DI changes are broad.
- [ ] Manually verify a happy path with a local proxy server or MockWebServer-backed debug endpoint.
- [ ] Manually verify expiration, rejection, cancellation, and manual-token fallback on TV-sized layout.

## Open Questions

- Should pairing approval automatically run Test & import, or should it stop after saving the token and let the user press Test & import?
- Should manual token entry remain always visible or move behind an Advanced/Manual setup action?
- Decision: the client keeps the locally typed `proxyBaseUrl`; the server returns only the approved access token.
- What minimum and maximum polling intervals should the client enforce?
- Should the proxy server expose pending sessions by pairing code only, or by both session ID and code?
- Should approved tokens include explicit expiration metadata for display or future refresh logic?

## Complexity Tracking

This is a medium-sized additive feature. The riskiest parts are stale async polling responses overwriting newer user actions, and accidentally clearing or logging existing tokens. Keep the pairing client isolated, test the ViewModel state machine heavily, and reuse the current Home Proxy import path after approval.
