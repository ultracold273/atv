# Feature Specification: Android Test Safety Roadmap

**Feature Branch**: `009-android-test-safety-roadmap`  
**Created**: 2026-07-01  
**Status**: Draft  
**Input**: Define the remaining Android instrumented test work after the initial safety baseline, following the repository's spec and plan documentation pattern.

## Overview

The project now has a runnable Android instrumented test baseline: Hilt tests launch with `HiltTestApplication`, Room schema files are available to migration tests, useful integration tests pass locally, and stale UI E2E suites are quarantined until they have seeded state and stable selectors.

This feature documents the remaining work required to turn `androidTest` into a practical safety net for the upcoming UI and architecture refactors. The goal is to replace brittle UI sketches with deterministic Android TV smoke tests that verify app launch, playback navigation, overlays, settings navigation, and source/import behavior without depending on real streams or live upstream services.

## Current Baseline

Already in place:

- `HiltTestRunner` launches instrumented tests with `HiltTestApplication`.
- Room schema JSON files are packaged into androidTest assets for migration validation.
- Room migration, encrypted credential storage, and CTC import integration tests run as the useful baseline.
- Stale UI E2E suites are quarantined with `@Ignore` until seeded state and stable selectors exist.
- `kotlinx.serialization` is aligned with Room migration tooling.

Current baseline command:

```bash
./studio-gradlew app:connectedDebugAndroidTest
```

Expected current result:

- Real baseline tests pass.
- Quarantined UI E2E suites are skipped.
- No live upstream IPTV service is contacted.

## User Scenarios & Testing

### User Story 1 - Seeded Playback Smoke Test (Priority: P1)

As a maintainer doing UI refactors, I want a deterministic playback smoke test with seeded channels so that I can detect broken app startup, ViewModel wiring, Compose rendering, and TV remote handling before merging larger UI changes.

**Independent Test**: Seed two known channels into the test database, launch the app, verify playback is shown, press D-pad Down, verify the current channel changes, press D-pad Left, and verify the channel list opens.

**Acceptance Scenarios**:

1. **Given** two channels are seeded before launch, **When** `MainActivity` starts, **Then** the app opens the playback screen rather than setup.
2. **Given** playback is visible, **When** D-pad Down is pressed, **Then** the current channel changes to the next seeded channel.
3. **Given** playback is visible, **When** D-pad Left is pressed, **Then** the channel list overlay is shown.
4. **Given** the smoke test runs, **When** playback starts, **Then** no real video stream is required.

### User Story 2 - Stable UI Selectors for Android TV Surfaces (Priority: P1)

As a test author, I want stable Compose selectors on screen and overlay roots so that UI tests survive visual refactors.

**Independent Test**: Query the main screen and overlay roots by test tag in a Compose instrumented test.

**Acceptance Scenarios**:

1. **Given** the setup screen is visible, **When** a test queries `setup_screen`, **Then** a stable node is found.
2. **Given** playback is visible, **When** a test queries `playback_screen`, **Then** a stable focusable node is found.
3. **Given** overlays are visible, **When** tests query their root tags, **Then** stable nodes are found for channel info, channel list, number pad, settings menu, and error overlay.
4. **Given** decorative implementation details change, **When** root tags remain intact, **Then** tests continue to target behavior rather than layout internals.

### User Story 3 - Reusable Test Fixtures and Robots (Priority: P2)

As a maintainer, I want small reusable test helpers so that new E2E tests are readable and do not duplicate launch, seeding, or D-pad boilerplate.

**Independent Test**: Write a second UI smoke test using the shared seeder and robot APIs without duplicating low-level Compose operations.

**Acceptance Scenarios**:

1. **Given** a test needs playback state, **When** it calls the seeding helper, **Then** Room contains deterministic channels before launch.
2. **Given** a test drives playback UI, **When** it uses `PlaybackRobot`, **Then** D-pad input and assertions are expressed as user-level actions.
3. **Given** helpers evolve, **When** a new test is added, **Then** it composes small fixtures rather than inheriting from a large abstract base class.

### User Story 4 - Replace Quarantined UI Suites (Priority: P2)

As a maintainer, I want the old quarantined UI E2E scenarios replaced incrementally so that their scenario intent becomes trustworthy executable coverage.

**Independent Test**: Remove or rewrite one quarantined scenario only after an equivalent harness-backed test passes locally.

**Acceptance Scenarios**:

1. **Given** a quarantined channel navigation scenario exists, **When** a seeded replacement test covers it, **Then** the old scenario can be deleted or rewritten.
2. **Given** a settings menu scenario exists, **When** a robot-backed test covers open, close, and navigation behavior, **Then** the stale settings test is no longer needed.
3. **Given** setup/source selection scenarios exist, **When** the harness can launch fresh and seeded app states, **Then** first-run setup coverage can replace stale playlist-loading checks.

### User Story 5 - Deterministic Network-Facing Scenarios (Priority: P3)

As a maintainer, I want network-facing Android tests to use local fakes or `MockWebServer` so that import and proxy flows are covered without contacting live services.

**Independent Test**: Run CTC/proxy/import tests with all HTTP traffic directed at `MockWebServer` or local emulator endpoints.

**Acceptance Scenarios**:

1. **Given** a Direct CTC import test runs, **When** it performs HTTP calls, **Then** calls target `MockWebServer` rather than the real operator URL.
2. **Given** a Home proxy flow is tested, **When** channels are fetched, **Then** responses come from deterministic fixtures.
3. **Given** a pure playback UI smoke test runs, **When** it needs channels, **Then** it uses seeded local data rather than `MockWebServer`.

## Functional Requirements

- **FR-001**: The test framework MUST keep `./studio-gradlew app:connectedDebugAndroidTest` green on a local Android TV emulator.
- **FR-002**: The first new UI smoke test MUST launch from seeded local state rather than using the Android system file picker.
- **FR-003**: UI tests MUST NOT require real video streams or live IPTV services.
- **FR-004**: Screen and overlay roots SHOULD expose stable Compose test tags.
- **FR-005**: Test tags SHOULD be placed on stable roots, not decorative inner nodes.
- **FR-006**: The playback smoke test MUST verify app launch into playback from seeded channels.
- **FR-007**: The playback smoke test MUST verify at least one D-pad channel navigation action.
- **FR-008**: The playback smoke test MUST verify at least one overlay-open action.
- **FR-009**: Test fixtures SHOULD isolate or clear state between tests.
- **FR-010**: Test helpers SHOULD favor small composable seeders and robots over a large abstract base class.
- **FR-011**: Quarantined UI E2E suites MUST remain skipped until their scenarios are covered by harness-backed replacements.
- **FR-012**: `MockWebServer` SHOULD be used for HTTP-bound import/proxy scenarios.
- **FR-013**: `MockWebServer` SHOULD NOT be introduced for pure playback UI smoke tests.
- **FR-014**: No Android test MAY contact the real CTC operator URL or any live upstream IPTV service.
- **FR-015**: CI emulator coverage SHOULD be deferred until the local instrumented suite is stable and fast.

## Recommended Test Tags

```text
setup_screen
playback_screen
channel_info_overlay
channel_list_overlay
number_pad_overlay
settings_menu
error_overlay
```

## Success Criteria

- **SC-001**: A seeded playback smoke test passes locally on the Android TV emulator.
- **SC-002**: Existing baseline integration tests continue to pass.
- **SC-003**: Quarantined UI suites remain skipped until replacement coverage exists.
- **SC-004**: The first smoke test runs without real streams, real file pickers, or live HTTP services.
- **SC-005**: Future UI refactor PRs have at least one instrumented smoke test that can catch broken launch or D-pad behavior.

## Assumptions

- Android instrumented tests remain local-only initially.
- JVM unit tests remain the primary fast feedback layer for business logic.
- Android TV D-pad/focus behavior is more important than phone-style touch behavior for this app.
- The existing CTC import integration test remains the model for deterministic HTTP-bound Android tests.

## Clarifications

### Session 2026-07-01

- Q: Do we need a mock server for the first playback smoke test? -> A: No. Seed local data and use a fake or harmless player path. Keep `MockWebServer` for network-facing import/proxy scenarios.
- Q: Should the old UI E2E files be deleted immediately? -> A: No. Keep them quarantined as scenario backlog until harness-backed replacements exist.

