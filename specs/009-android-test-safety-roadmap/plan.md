# Implementation Plan: Android Test Safety Roadmap

**Branch**: `009-android-test-safety-roadmap` | **Date**: 2026-07-01 | **Spec**: [spec.md](spec.md)

## Summary

Build on the merged Android instrumented test baseline by adding a deterministic Android TV playback smoke test, then extracting small reusable fixtures and robots, then incrementally replacing the quarantined UI E2E suites. The first executable goal is a seeded playback smoke test that proves app launch, playback screen rendering, D-pad channel navigation, and channel-list overlay behavior without real streams, file pickers, or live services.

## Technical Context

**Language/Version**: Kotlin 2.1.0, Java 17  
**Primary Dependencies**: Hilt, Room, Compose UI testing, MockWebServer where HTTP is required  
**Testing**: Android instrumented tests on Android TV emulator, JVM tests for business logic  
**Target Platform**: Android TV API 29+  
**Storage**: Room database plus existing preferences/secure stores  
**Constraints**: Keep Android tests deterministic; do not require real streams, file picker system UI, real IPTV accounts, or live upstream services

## Current Baseline

The current baseline is expected to run with:

```bash
./studio-gradlew app:connectedDebugAndroidTest
```

Current expected result:

- Baseline migration/storage/import integration tests pass.
- Quarantined UI E2E suites are skipped.
- No real upstream service is contacted.

## Architecture

```text
androidTest
    |
    |-- fixtures seed deterministic Room/preference state
    |-- robots drive Compose UI as Android TV user actions
    |-- smoke tests assert visible behavior
    |
MainActivity -> Hilt test app -> test-controlled state -> Compose UI
```

Network-bound tests keep this separate path:

```text
MockWebServer -> real HTTP client/use case -> Room/repository assertions
```

Pure playback UI smoke tests should not use `MockWebServer`; they should use seeded local channels and a fake or harmless player path.

## Project Structure

Expected eventual structure:

```text
app/src/androidTest/kotlin/com/example/atv/
├── PlaybackSmokeTest.kt
├── testing/
│   ├── E2eFixtures.kt
│   ├── E2eDatabaseSeeder.kt
│   └── robots/
│       ├── PlaybackRobot.kt
│       ├── SetupRobot.kt
│       └── SettingsRobot.kt
├── data/local/db/MigrationTest.kt
├── data/local/secure/IptvCredentialsStoreImplTest.kt
└── ui/screens/playback/PlaybackViewModelCtcImportIntegrationTest.kt
```

Quarantined files remain temporarily:

```text
app/src/androidTest/kotlin/com/example/atv/
├── PlaylistLoadingTest.kt     # skipped until replaced
├── ChannelNavigationTest.kt   # skipped until replaced
└── SettingsFlowTest.kt        # skipped until replaced
```

## Phase 1: Stable UI Tags

### Goals

Expose durable selectors for screen and overlay roots so tests target behavior rather than visual internals.

### Tasks

- [ ] Add `Modifier.testTag("setup_screen")` to the setup screen root.
- [ ] Add `Modifier.testTag("playback_screen")` to the playback screen root.
- [ ] Add `Modifier.testTag("channel_info_overlay")` to the channel info overlay root.
- [ ] Add `Modifier.testTag("channel_list_overlay")` to the channel list overlay root.
- [ ] Add `Modifier.testTag("number_pad_overlay")` to the number pad overlay root.
- [ ] Add `Modifier.testTag("settings_menu")` to the settings menu root.
- [ ] Add `Modifier.testTag("error_overlay")` to the error overlay root.
- [ ] Keep tags on stable roots and avoid tagging decorative or layout-only nodes unless a test genuinely needs them.

## Phase 2: First Seeded Playback Smoke Test

### Goals

Create the first real UI smoke test that proves the test harness direction.

### Scenario

```text
Given two seeded channels
When the app launches
Then playback screen is shown
When D-pad Down is pressed
Then the current channel changes
When D-pad Left is pressed
Then the channel list opens
```

### Tasks

- [ ] Add a minimal channel fixture list with two deterministic channels.
- [ ] Add a test seeding helper that inserts channels before `MainActivity` launches.
- [ ] Ensure the test can launch into playback from seeded state.
- [ ] Add a fake or harmless player path so no real stream is required.
- [ ] Add `PlaybackSmokeTest` with the seeded launch, D-pad Down, and D-pad Left checks.
- [ ] Verify with `./studio-gradlew app:connectedDebugAndroidTest` on Android TV emulator.

## Phase 3: Extract Small Harness Helpers

### Goals

Reduce duplication after the first smoke test proves what helpers are actually needed.

### Tasks

- [ ] Extract `E2eFixtures` for shared channel fixtures.
- [ ] Extract `E2eDatabaseSeeder` or equivalent Room setup helper.
- [ ] Add `PlaybackRobot` for playback assertions and D-pad input.
- [ ] Add `SetupRobot` only when setup/source tests need it.
- [ ] Add `SettingsRobot` only when settings tests need it.
- [ ] Avoid a large abstract base class; compose small helpers per test.

## Phase 4: Replace Quarantined UI Suites

### Goals

Turn stale scenario sketches into trustworthy executable coverage.

### Replacement Order

1. Channel navigation and channel list scenarios.
2. Number pad direct channel selection.
3. Settings menu open/close and navigation.
4. Fresh setup/source selection flow.
5. Import/source-flow behavior with deterministic fakes.

### Tasks

- [ ] Replace `ChannelNavigationTest` scenarios with harness-backed tests.
- [ ] Replace `SettingsFlowTest` scenarios with harness-backed tests.
- [ ] Replace `PlaylistLoadingTest` setup/source scenarios with current UI behavior.
- [ ] Remove `@Ignore` from rewritten classes only after all tests are deterministic.
- [ ] Delete obsolete stale classes after equivalent replacement coverage exists.

## Phase 5: Network-Facing Android Tests

### Goals

Cover HTTP-bound flows without live services.

### Tasks

- [ ] Keep Direct CTC import coverage routed through `MockWebServer`.
- [ ] Add Home proxy channel-fetch tests with `MockWebServer` when proxy flow work needs Android runtime coverage.
- [ ] Add EPG provider Android tests only if they require Android runtime behavior; otherwise prefer JVM tests.
- [ ] Store representative protocol fixtures as literals or resource files.
- [ ] Ensure no Android test calls the real CTC operator URL.

## Phase 6: Optional CI Emulator Job

### Goals

Add CI only after local emulator tests are stable and fast.

### Candidate Workflow

```yaml
instrumented-tests:
  runs-on: macos-latest
  steps:
    - uses: actions/checkout@v7
    - uses: actions/setup-java@v5
      with:
        java-version: 17
        distribution: temurin
    - uses: gradle/actions/setup-gradle@v6
    - uses: reactivecircus/android-emulator-runner@v2
      with:
        api-level: 31
        target: android-tv
        arch: x86_64
        script: ./gradlew app:connectedDebugAndroidTest --no-daemon
```

### Entry Criteria

- [ ] Local suite is deterministic across clean emulator runs.
- [ ] Runtime is acceptable for PR feedback.
- [ ] Skipped tests are intentional and documented.
- [ ] Failures are actionable and not environment noise.

## Suggested PR Sequence

1. Add stable UI tags and one seeded playback smoke test.
2. Extract reusable seeders and robots from that first test.
3. Replace channel navigation scenarios.
4. Replace settings-menu scenarios.
5. Replace setup/source-selection scenarios.
6. Add targeted network-flow tests with `MockWebServer` for proxy and CTC paths.
7. Add optional CI emulator job once local reliability is proven.

## Verification

Each implementation PR should keep this command green locally:

```bash
./studio-gradlew app:connectedDebugAndroidTest
```

For docs-only changes to this spec package, Markdown review is sufficient.

