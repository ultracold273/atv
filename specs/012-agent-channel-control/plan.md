# Implementation Plan: Agent Channel Control

**Branch**: `codex/012-agent-channel-control` | **Date**: 2026-10-31 | **Spec**: [spec.md](spec.md)

## Summary

Add a foreground-only LAN control listener with exactly two operations: retrieve the complete channel catalog and request switching by channel number. The external agent owns language understanding and channel choice. A `202` response acknowledges dispatch; v1 has no playback-status interface or background command queue.

Keep the API contract in the companion spec, following the existing spec/plan layout used by features 005-008. This change checks in design documents only. Implementation phases below remain unchecked.

Feature number `012` follows the repository's feature-numbering rule of considering both spec folders and existing numbered branches: the existing `011-startup-source-backstack-1.2.1` branch already occupies 011. Use the `codex/` prefix for the working branch.

## Technical Context

- **Language/Version**: Kotlin 2.1.0; existing coroutines and Flow
- **Existing Dependencies**: Hilt, Room 2.8.4, Media3 1.5.1, kotlinx.serialization 1.8.1, encrypted settings storage
- **API Transport**: Embedded HTTPS listener, port 8765, JSON and a dedicated bearer credential
- **Target Platform**: Android TV API 29+, current compile/target SDK 35
- **Storage**: Existing channel database; new control settings and TLS identity; no channel-ID migration required
- **Testing**: JUnit 5, MockK, coroutine tests, Room tests, real loopback requests to the embedded listener, existing fake-player instrumented fixtures
- **Constraint**: All API operations and command dispatch follow foreground/playback-screen availability; no persistent service or extra API operations.

An embedded server dependency has not been selected or added by this spec commit. Select a maintained Android-compatible implementation with TLS support and bounded resource usage during the listener phase, recording the dependency/security and APK-size impact in its implementation PR. Existing OkHttp is a client and is not the production listener.

## Constitution Check

- **Architecture**: Network DTOs and listener code stay outside the domain layer; the HTTP handler depends on a small application-facing switch contract, not Compose or ExoPlayer.
- **Security**: Both operations require authorization. Use encrypted credential storage and HTTPS with verified/pinned server identity. Provision on the TV to keep the API at two operations. No plaintext bearer-token exception is requested.
- **Testing**: New implementation must meet 80% line coverage and full critical-path coverage as required by the constitution, with authorization, stale-catalog handling, lifecycle races, and dispatch semantics covered explicitly.
- **UX**: Reuse the shared settings primitives and existing playback behavior; localize new strings in English and Chinese.
- **Performance**: Keep work off the UI thread except the existing player/UI dispatch; measure the 200 ms p95 warm-request target under the spec's fixture conditions.
- **Scope**: No model integration, background playback redesign, or third endpoint. Horizontal service scaling is not applicable to a listener controlling one physical TV; document concrete deviations if implementation introduces any.

## Current Integration Points

| Existing file | Relevant behavior / planned change |
|---------------|-----------------------------------|
| [ChannelEntity.kt](../../app/src/main/kotlin/com/example/atv/data/local/db/ChannelEntity.kt) | Channel number is the primary key; preserve it as the v1 selector. |
| [ChannelDao.kt](../../app/src/main/kotlin/com/example/atv/data/local/db/ChannelDao.kt) | Ordered channel reads; add the consistent snapshot access needed by the catalog. |
| [ChannelRepositoryImpl.kt](../../app/src/main/kotlin/com/example/atv/data/repository/ChannelRepositoryImpl.kt) | Playlist replacement currently deletes then inserts; wrap replacement atomically and coordinate all catalog mutations with versioned dispatch. |
| [PlaybackViewModel.kt](../../app/src/main/kotlin/com/example/atv/ui/screens/playback/PlaybackViewModel.kt) | Existing numbered switching, URL resolution, UI/EPG updates, player initialization and release. Preserve these behaviors through the shared switch path. |
| [MainActivity.kt](../../app/src/main/kotlin/com/example/atv/MainActivity.kt) | Leaving the activity currently finishes it. Bind listener lifetime to foreground lifecycle; do not extend process lifetime. |
| [SettingsScreen.kt](../../app/src/main/kotlin/com/example/atv/ui/screens/settings/SettingsScreen.kt) | Host the opt-in control settings using existing components and D-pad patterns. |
| [ChannelSourceSettingsStoreImpl.kt](../../app/src/main/kotlin/com/example/atv/data/local/secure/ChannelSourceSettingsStoreImpl.kt) | Reference the existing encrypted-storage pattern, while keeping the new control credential separate from provider/proxy secrets. |

## Proposed Project Structure

Paths below are implementation targets, not files created by this documentation change.

```text
specs/012-agent-channel-control/
├── spec.md                              # requirements and two-operation API contract
└── plan.md                              # integration and verification plan

app/src/main/kotlin/com/example/atv/
├── data/remotecontrol/
│   ├── RemoteControlServer.kt           # HTTPS lifecycle, auth, routing, limits
│   └── RemoteControlDtos.kt             # narrow wire models and errors
├── data/local/secure/
│   └── RemoteControlSettingsStoreImpl.kt
├── domain/model/
│   ├── ChannelCatalogSnapshot.kt
│   └── ChannelSwitchResult.kt
├── domain/repository/
│   ├── ChannelRepository.kt             # snapshot/mutation coordination contract
│   └── RemoteControlSettingsStore.kt
├── player/
│   └── ChannelSwitchCoordinator.kt      # shared workflow and availability gate
├── di/
│   └── RemoteControlModule.kt
└── ui/screens/
    ├── settings/components/
    │   └── RemoteControlSettingsSection.kt
    └── playback/
        └── PlaybackViewModel.kt         # use shared dispatch and observe UI effects
```

Keep source-mode URL handling and playback orchestration in the existing application/player layer. Domain catalog/result types must not depend on Android, the server engine, or ExoPlayer. Exact class boundaries can follow the existing dependency conventions without requiring a broad ViewModel refactor.

## Phase 1: Atomic Catalog and Number Selection

- [ ] Add a catalog snapshot projection containing only number, name, and group title for the wire response.
- [ ] Derive an opaque version from a deterministic digest of the complete, ordered stored channel records, including underlying stream URLs. Keep serialization stable and distinguish null from empty values; never expose or log the canonical input. This avoids introducing a new channel-ID schema or a persistent revision table.
- [ ] Make playlist delete/insert replacement one Room transaction. Coordinate every add, update, delete, clear, and import operation through the same catalog mutation guard.
- [ ] Read a consistent snapshot and use the same guard for version validation, number lookup, and handoff to playback. A separate version check followed by an unguarded lookup is insufficient.
- [ ] Release the guard after dispatch, before media loading; no network/media I/O may hold it. Ensure local controls participate in dispatch ordering.
- [ ] Test empty catalogs, sorting, duplicate names, stable versions, metadata/URL changes, replacement atomicity, missing numbers, and import-vs-switch races.

## Phase 2: Shared Foreground Switch Workflow

- [ ] Introduce the smallest shared coordinator that reuses the current switching workflow and returns a typed dispatch result.
- [ ] Keep source-mode/udpxy URL resolution, last-watched persistence, current-channel UI state, channel information, and EPG refresh consistent for both local and external switches.
- [ ] Register readiness only while the playback screen and its player are active. Invalidate readiness on disposal/backgrounding; the listener must never retain a cleared ViewModel or player.
- [ ] Recheck readiness and current authorization before dispatching on the appropriate player/UI thread. Cancel undispatched work on disablement, credential rotation, or lifecycle loss.
- [ ] Return unavailable/busy failures without navigation or durable queuing. Serialize with physical-remote switches and keep resources bounded.
- [ ] Acknowledge only after the workflow has accepted the selected channel. Do not wait for buffering completion or first frame, and do not wire player state into an external status interface.
- [ ] Test ordinary switching, overlays, late player errors, teardown during dispatch, concurrent local/remote requests, and retention of existing EPG behavior.

## Phase 3: Two-Operation HTTPS Listener

- [ ] Select and document an Android-compatible embedded server implementation; verify TLS support, license, dependency advisories, resource limits, and APK impact before adoption.
- [ ] Register only the two routes from the spec; disable any framework-added management, health, discovery, or status routes. Unsupported methods/paths follow the specified error contract.
- [ ] Validate bearer authorization before data access or side effects. Keep token headers and request details containing secrets out of logs.
- [ ] Implement strict input validation, the 4 KiB switch-body limit, bounded connection/dispatch handling, sanitized errors, and no-store response headers.
- [ ] Map the domain/application result to the documented status code. Build the `202` body from the dispatched request, not observed media state.
- [ ] Integrate foreground start/stop and binding-error reporting; do not add foreground-service or battery-optimization workarounds.
- [ ] Run contract tests against the actual embedded listener with a local test client. MockWebServer can still support upstream-stream fixtures, but cannot substitute for testing the production server routes.

## Phase 4: TV Provisioning and Settings

- [ ] Add an opt-in switch, listener availability/error feedback, current endpoint display, credential reveal/rotation, and TLS identity details using shared settings components.
- [ ] Generate a dedicated random controller credential and store it encrypted. Keep one credential per installation in v1; rotation invalidates all earlier client copies.
- [ ] Provision an installation-specific TLS key/certificate and provide an out-of-band trust procedure using the certificate/public-key fingerprint shown on the TV. Keep private-key material protected using Android's secure storage facilities.
- [ ] Document a native/scripted client setup that verifies that identity before sending the token. Do not introduce a pairing endpoint, network credential export, or an insecure verification-bypass example.
- [ ] Preserve the enabled preference across launches, but listen only during foreground operation. Update connection details after LAN address changes and handle bind failures without interrupting playback.
- [ ] Add English/Chinese strings and verify D-pad reachability, disabled/error states, and BACK behavior.

## Phase 5: Verification and Client Example

- [ ] Add a small developer example that retrieves the complete catalog and submits a returned number/version. It must use only the two operations and label `202` as dispatch acknowledgement.
- [ ] Use local fixtures and test credentials; no provider credentials, home TV automation, cloud model, or live IPTV feed is required for automated tests. Follow the [Android test safety roadmap](../009-android-test-safety-roadmap/spec.md).
- [ ] Contract tests cover every specified success/error response and ensure absent playback-status/name-search functionality is not accidentally exposed.
- [ ] Add an instrumented flow using the fake player: enable control, retrieve the catalog through the listener, submit a number, verify the player/UI selection locally, and confirm listener shutdown on background/disablement.
- [ ] Verify a delayed/failed stream still produces dispatch acknowledgement and normal TV error handling; no test should infer actual playback from `202`.
- [ ] Exercise unauthorized callers, credential rotation, TLS identity mismatch, stale versions, import races, port conflicts, and non-playback routes.
- [ ] Run focused unit/contract tests plus existing playback, EPG, settings, and channel-repository regression tests. Run relevant lint/Detekt checks and affected instrumented tests for implementation changes.
- [ ] Record warm request p95 and UI responsiveness with a 1,000-channel fixture, identifying the device and excluding TLS establishment and media startup.

## Documentation Validation for This Commit

- Verify the spec and plan agree on exactly two operations, agent-owned name resolution, numbered selection, and acknowledgement-only responses.
- Parse every JSON example and resolve repository-relative links.
- Run `git diff --check` and inspect the staged file list. No application source changes, runtime dependencies, or generated artifacts belong in this documentation commit.

## Complexity Tracking

Keep two API operations and existing channel numbers. The main implementation risks are stale catalog selection, lifecycle loss during dispatch, and protecting controller credentials on the LAN. A shared dispatch path, atomic catalog handling, and TV-provisioned HTTPS address those requirements without introducing an agent runtime, status polling, a background service, or new server-side enrollment operations.
