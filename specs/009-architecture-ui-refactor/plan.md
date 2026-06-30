# Implementation Plan: Architecture and UI Refactor

**Branch**: `009-architecture-ui-refactor` | **Date**: 2026-06-29 | **Spec**: [spec.md](spec.md)

## Summary

Refactor ATV's Android TV UI and architecture incrementally so the app is easier to read, test, and extend without changing the existing user experience. The work starts with shared TV UI primitives and screen decomposition, then moves platform/workflow logic out of oversized ViewModels, then hardens playback overlay and D-pad focus behavior.

The plan intentionally avoids a rewrite. Each phase should produce small, reviewable changes with focused verification. Behavior-preserving movement should be separated from intentional UI or navigation behavior changes wherever practical.

## Technical Context

**Language/Version**: Kotlin 2.1.0  
**UI**: Jetpack Compose for TV, TV Material 3, AndroidX Compose  
**Architecture**: MVVM with Hilt, domain/data/ui package split  
**Async**: Coroutines, Flow, StateFlow  
**Testing**: JUnit 5, MockK, Turbine, coroutine tests, Compose UI tests where practical  
**Target Platform**: Android TV API 29+  
**Constraints**: Preserve existing user-facing behavior; keep D-pad focus reliable; avoid unrelated feature work; do not change IPTV/proxy/EPG/player protocols as part of this refactor

## Architecture Direction

```text
Route composable
      ↓ collects state / wires callbacks
Screen content composable
      ↓ composes focused feature components
Feature components
      ↓ reuse app-specific TV primitives
ATV UI primitives
      ↓ wrap TV Material surfaces + theme tokens
Compose for TV
```

For ViewModels and workflows:

```text
Screen ViewModel
      ↓ delegates workflows
Feature coordinator / controller
      ↓ calls domain use cases, repositories, or clients
Domain/data layer
      ↓ emits result/state
ViewModel maps result into UI state/effects
```

The target shape is clearer boundaries, not more layers for their own sake. Extract only where it removes real complexity, enables testing, or prevents duplication that already exists.

## Project Structure

Proposed package additions and file movement targets:

```text
app/src/main/kotlin/com/example/atv/ui/
├── components/
│   ├── AtvButton.kt                      # NEW shared focused button primitives
│   ├── AtvDialog.kt                      # NEW shared dialog container/actions
│   ├── AtvListItem.kt                    # NEW shared list/settings row primitive
│   ├── AtvTextField.kt                   # NEW shared text-field styling wrapper
│   ├── AtvStatusText.kt                  # NEW shared inline status text
│   └── AtvOverlayPanel.kt                # NEW shared overlay container shape/surface
├── focus/
│   ├── FocusRequesters.kt                # NEW optional helper for safe focus requests
│   └── PlaybackFocusTargets.kt           # NEW optional playback-focused target model
├── screens/
│   ├── iptv/
│   │   ├── IptvSettingsRoute.kt          # NEW, or route extracted from existing file
│   │   ├── IptvSettingsScreen.kt         # stateless content after split
│   │   ├── components/                   # mode selector, forms, pairing/status views
│   │   └── workflow/                     # optional UI-facing coordinators if kept near screen
│   ├── settings/
│   │   ├── SettingsRoute.kt              # NEW, or route extracted from existing file
│   │   ├── SettingsScreen.kt             # stateless content after split
│   │   └── components/                   # stats, rows, dialogs
│   └── channelmanagement/
│       ├── ChannelManagementRoute.kt     # NEW, or route extracted from existing file
│       ├── ChannelManagementScreen.kt    # stateless content after split
│       └── components/                   # list, row, form dialog, delete dialog
└── util/
    └── KeyEventExtensions.kt             # existing D-pad helpers, possibly extended

app/src/main/kotlin/com/example/atv/domain/usecase/
├── NumberPadInputController.kt           # NEW if kept pure/domain-ish
└── PlaybackOverlayController.kt          # NEW if pure enough for unit tests

app/src/main/kotlin/com/example/atv/ui/screens/playback/
├── PlaybackViewModel.kt                  # slimmer coordinator of state
├── PlaybackOverlayState.kt               # NEW nested overlay state model if useful
├── PlaybackEpgCoordinator.kt             # NEW banner/panel EPG loading coordinator
└── NumberPadInputController.kt           # alternative location if UI-specific

app/src/main/kotlin/com/example/atv/ui/screens/iptv/
├── DemoPlaylistImporter.kt               # NEW, or domain/data package if preferred
├── ProxyPairingCoordinator.kt            # NEW pairing state machine wrapper
└── DeviceInfoProvider.kt                 # NEW platform metadata wrapper
```

Exact package names can be adjusted during implementation to fit existing ownership boundaries. Prefer the smallest move that makes the next change easier.

## Phase 0: Baseline and Guardrails

### Goals

Capture current behavior and reduce the chance that mechanical refactors accidentally change UX.

### Tasks

- [ ] Record current large-file baseline: line counts for UI screen/component files and `PlaybackViewModel`/`IptvSettingsViewModel`.
- [ ] Identify the smallest useful test set for each area: IPTV settings, settings, channel management, playback, EPG.
- [ ] Create a manual D-pad verification checklist for playback overlays before focus-heavy changes.
- [ ] Decide the first implementation slice: recommended first slice is shared UI primitives plus `IptvSettingsScreen` component extraction.
- [ ] Confirm whether new shared primitives live under `ui/components` or a new `ui/design` package.
- [ ] Keep all behavior-preserving extraction commits separate from intentional UI behavior changes.

### Verification

- [ ] Run current relevant unit tests before starting broad refactor work if the local environment permits.
- [ ] Save the command list and any known failures in the implementation notes or PR description.

## Phase 1: Shared ATV UI Primitives

### Goals

Reduce duplicated TV Material surface styling before splitting screens further.

### Tasks

- [ ] Add `AtvButton` for primary, secondary, neutral, and destructive focused actions.
- [ ] Support enabled/disabled, selected, destructive, and full-width variants in `AtvButton` only where currently needed.
- [ ] Add `AtvDialog` with shared container shape, width handling, padding, and action-row layout.
- [ ] Add `AtvSettingsRow` or `AtvListItem` for title/subtitle rows with consistent focused border and container colors.
- [ ] Add `AtvToggleRow` if settings toggles continue to repeat row/switch styling.
- [ ] Add `AtvTextField` wrapper for current Material3 `OutlinedTextField` colors used in IPTV setup.
- [ ] Add `AtvStatusText` for inline success/error/neutral status messages.
- [ ] Add `AtvOverlayPanel` for repeated overlay panel background/clip/padding patterns if useful.
- [ ] Keep primitive APIs narrow and app-specific; avoid introducing unused parameters.
- [ ] Migrate only one or two low-risk usages first to validate API shape.

### Candidate Files

- `ui/components/AtvButton.kt`
- `ui/components/AtvDialog.kt`
- `ui/components/AtvListItem.kt`
- `ui/components/AtvTextField.kt`
- `ui/components/AtvStatusText.kt`
- `ui/components/AtvOverlayPanel.kt`

### Verification

- [ ] Build or compile after introducing primitives.
- [ ] Run tests for any migrated screen if available.
- [ ] Manually inspect focus border, disabled state, destructive state, and text sizing on migrated components.

## Phase 2: Split `IptvSettingsScreen`

### Goals

Make channel source setup readable and create a pattern for future screen refactors.

### Tasks

- [ ] Extract a route-level composable from `IptvSettingsScreen` if useful: collects `uiState`, applies `initialMode`, wires `onBack` behavior.
- [ ] Convert main content into a stateless `IptvSettingsContent` that receives state and callbacks.
- [ ] Extract `SourceModeSelector` and `SourceModeButton` into focused components.
- [ ] Replace `SourceModeButton` duplicated surface styling with `AtvButton` or a source-mode-specific primitive.
- [ ] Extract `M3u8SourceForm` from current `M3u8Fields`.
- [ ] Extract `DirectCtcSourceForm` from current `DirectCtcFields`.
- [ ] Extract `HomeProxySourceForm` from current `HomeProxyFields`.
- [ ] Extract `ProxyPairingStatusView` from current `ProxyPairingStatusBlock`.
- [ ] Extract `ImportStatusView` from current `StatusBlock`.
- [ ] Replace local `ActionButton` with shared button primitive.
- [ ] Replace clear credentials dialog with shared `AtvDialog`.
- [ ] Keep current focus behavior for source mode selection unless explicitly changed.
- [ ] Keep all strings and validation behavior unchanged.

### Suggested Structure

```text
ui/screens/iptv/
├── IptvSettingsRoute.kt
├── IptvSettingsScreen.kt
└── components/
    ├── SourceModeSelector.kt
    ├── M3u8SourceForm.kt
    ├── DirectCtcSourceForm.kt
    ├── HomeProxySourceForm.kt
    ├── ProxyPairingStatusView.kt
    ├── ImportStatusView.kt
    └── ClearCredentialsDialog.kt
```

### Verification

- [ ] Run `IptvSettingsViewModelTest`.
- [ ] Run any proxy pairing tests affected by UI state mapping.
- [ ] Manually verify source mode switching, form enabled state, pairing status rendering, clear credentials dialog, and back-after-success behavior.

## Phase 3: Split `SettingsScreen` and `ChannelManagementScreen`

### Goals

Apply the route/content/component pattern to the other large non-playback screens.

### Settings Tasks

- [ ] Extract `SettingsRoute` if useful to collect state and wire ViewModel callbacks.
- [ ] Convert body into stateless `SettingsContent`.
- [ ] Extract `SettingsStatsPanel`.
- [ ] Replace `SettingsItem` with shared `AtvSettingsRow`.
- [ ] Replace `ToggleSettingsItem` with shared `AtvToggleRow` or a settings-specific wrapper.
- [ ] Replace `ConfirmationDialog`, `AboutDialog`, and `DialogButton` with shared dialog/button primitives.
- [ ] Keep message snackbar behavior unchanged for now unless a UI-effect migration is part of the same slice.
- [ ] Fix the About row version string if a safe one-line cleanup is included, using `BuildConfig.VERSION_NAME` consistently.

### Channel Management Tasks

- [ ] Extract `ChannelManagementRoute` if useful.
- [ ] Convert main content into stateless `ChannelManagementContent`.
- [ ] Extract `ChannelList` and `ChannelRow`.
- [ ] Replace channel row focused surface styling with shared list item primitive where possible.
- [ ] Extract `ChannelFormDialog`.
- [ ] Extract `DeleteChannelDialog`.
- [ ] Replace local `ActionChip` with shared button/chip primitive.
- [ ] Decide whether dialog form state remains local or moves fully into `ChannelManagementViewModel`.
- [ ] Remove unused `editName` and `editUrl` fields from `ChannelManagementUiState` if they remain unused after extraction.
- [ ] Localize hardcoded validation/error strings if touched.

### Verification

- [ ] Run `SettingsViewModelTest`.
- [ ] Run channel management tests if present; otherwise compile and manually verify add/edit/delete dialog flows.
- [ ] Manually verify first focus target, settings toggle behavior, clear-all dialog, about dialog, channel add/edit/delete, and BACK behavior.

## Phase 4: IPTV Setup Workflow Extraction

### Goals

Move platform-heavy and workflow-heavy code out of `IptvSettingsViewModel` while preserving UI state and behavior.

### Tasks

- [ ] Add `DeviceInfoProvider` to wrap device name, package name, and app version lookup.
- [ ] Add `DemoPlaylistImporter` to copy `demo_playlist.m3u8` from assets into internal storage and call the existing playlist loader.
- [ ] Add unit tests for `DemoPlaylistImporter` or keep integration through ViewModel tests if Android asset access makes pure tests impractical.
- [ ] Add `ProxyPairingCoordinator` to own pairing session creation, polling, cancellation, stale response guards, nonce generation, and approved-token validation.
- [ ] Keep final token persistence through existing `ChannelSourceSettingsStore`.
- [ ] Move pairing interval constants and active pairing ID tracking out of the ViewModel if coordinator-owned.
- [ ] Keep manual token entry behavior unchanged and ensure manual edits cancel or invalidate active pairing.
- [ ] Keep `testAndImport()` source-mode branching stable, or extract a `ChannelSourceImportCoordinator` if it reduces ViewModel complexity.
- [ ] Ensure `IptvSettingsViewModel` remains the owner of UI state transitions and maps workflow results to `ImportStatus`/`ProxyPairingStatus`.

### Candidate Files

- `ui/screens/iptv/DeviceInfoProvider.kt`
- `ui/screens/iptv/DemoPlaylistImporter.kt`
- `ui/screens/iptv/ProxyPairingCoordinator.kt`
- `ui/screens/iptv/ChannelSourceImportCoordinator.kt` if needed

### Verification

- [ ] Run `IptvSettingsViewModelTest`.
- [ ] Run proxy pairing tests.
- [ ] Add tests for stale pairing approval after cancellation or manual token edit if coverage is missing.
- [ ] Manually verify M3U8 import, demo playlist load, Direct CTC import, Home Proxy pairing, Home Proxy manual token import, clear credentials.

## Phase 5: Playback ViewModel Decomposition

### Goals

Reduce `PlaybackViewModel` responsibility while keeping playback behavior stable.

### Tasks

- [ ] Extract number pad input rules into `NumberPadInputController` or a pure helper.
- [ ] Add unit tests for number pad behavior: leading zero ignored, max digits, backspace, clear, invalid range, valid confirm.
- [ ] Extract overlay visibility and auto-hide timing into `PlaybackOverlayController` if it can remain testable and lifecycle-safe.
- [ ] Model overlay state explicitly, possibly as a nested `PlaybackOverlayState` inside `PlaybackUiState` or a separate internal state class.
- [ ] Preserve overlay dismissal priority: number pad, channel list, settings, error, channel info.
- [ ] Extract banner EPG loading into `PlaybackEpgCoordinator`.
- [ ] Extract panel EPG focus/debounce/loading into the same coordinator or a dedicated `EpgPanelCoordinator`.
- [ ] Preserve EPG debounce, cancellation, show/hide guards, and date-offset behavior.
- [ ] Keep `AtvPlayer` initialization/release behavior unchanged unless a dedicated lifecycle refactor is planned.
- [ ] Replace direct global snackbar calls only if a UI-effect/message mechanism is ready; otherwise leave behavior unchanged.

### Suggested Order

1. Extract `NumberPadInputController` first because it is narrow and easy to test.
2. Extract overlay state and auto-hide logic second.
3. Extract EPG coordination third because it has more coroutine timing risk.
4. Revisit snackbar/UI effects after the state split is stable.

### Verification

- [ ] Run `PlaybackViewModelTest`.
- [ ] Run `PlaybackViewModelEpgTest` and `PlaybackViewModelEpgIntegrationTest`.
- [ ] Run `EpgPanelStateTest`.
- [ ] Manually verify playback screen: channel switching, channel info auto-hide, channel list, EPG panel, number pad, settings menu, error overlay, retry, next channel.

## Phase 6: Focus and D-pad Hardening

### Goals

Make Android TV focus transitions easier to reason about and safer to modify.

### Tasks

- [ ] Document playback overlay focus transitions in a table or code comments near the focus coordinator.
- [ ] Add a small helper for safe `FocusRequester.requestFocus()` calls that currently use `runCatching` ad hoc.
- [ ] Wrap delayed initial focus logic so overlays do not each copy raw `delay(100)` patterns.
- [ ] Consider a feature-local `PlaybackFocusTargets` model for channel list, EPG today tab, previous channel row, number pad center, and settings first item.
- [ ] Keep `ChannelListOverlay` and `EpgPanel` focus behavior unchanged while extracting helpers.
- [ ] Add Compose UI tests for at least one high-risk focus flow before further focus changes.
- [ ] Candidate first automated focus test: channel list opens, current channel receives focus, RIGHT moves to EPG when enabled, LEFT returns to channel row.
- [ ] Candidate second automated focus test: number pad opens and default focus lands on digit 5 after delay.
- [ ] If Compose UI tests are too costly initially, add a manual verification checklist to the spec folder and reference it in PRs.

### Verification

- [ ] Run new focus tests if added.
- [ ] Run existing instrumented tests if environment permits.
- [ ] Manually verify D-pad navigation on TV/emulator layout after any focus helper refactor.

## Phase 7: Transient Messages, Navigation, and Startup Cleanup

### Goals

Address lower-priority architecture cleanup after UI and ViewModel boundaries are stable.

### Transient Message Tasks

- [ ] Decide whether new work uses ViewModel `UiEffect` channels or an injected/root message bus.
- [ ] Introduce a minimal `UiEffect` pattern on one screen if it clearly improves coupling.
- [ ] Migrate hardcoded ViewModel messages to string resources when touched.
- [ ] Keep `SnackBarManager` as a compatibility bridge until all existing calls are migrated.

### Navigation Tasks

- [ ] Introduce typed route objects or a sealed route model if route duplication continues after screen splitting.
- [ ] Add helper functions for repeated pop-back-stack behavior if useful.
- [ ] Preserve `IPTV_SETTINGS` and `CHANNEL_SOURCE_M3U8` behavior unless intentionally consolidated.
- [ ] Keep first-run/no-channel setup route behavior unchanged.

### Startup Tasks

- [ ] Replace `MainActivity` `runBlocking` start destination check with an async root-loading route or root ViewModel.
- [ ] Preserve startup routing: users with channels land on playback, users without channels land on setup/source flow.
- [ ] Ensure startup loading state has deterministic background and no jarring flicker.
- [ ] Add tests for startup state if logic moves into a ViewModel or pure coordinator.

### Verification

- [ ] Run navigation-related tests if present.
- [ ] Manually verify first launch/no channels, launch with existing channels, back behavior, settings navigation, channel source navigation.

## Phase 8: Final Cleanup and Consistency Pass

### Goals

Remove leftover duplication and document the new patterns so future work follows them naturally.

### Tasks

- [ ] Search for duplicated focused `Surface` button/list/dialog patterns that should now use shared primitives.
- [ ] Search for remaining hardcoded user-visible strings in ViewModels or UI code touched by the refactor.
- [ ] Search for stale TODOs related to UI cleanup; either resolve, move to a tracked spec/task, or leave only if still valid.
- [ ] Update `docs/AI_AGENT_GUIDE.md` or another developer doc if the repo uses it to document preferred UI structure.
- [ ] Add short comments only where focus behavior is non-obvious.
- [ ] Confirm no unrelated metadata, generated files, or formatting churn was introduced.

### Verification

- [ ] Run `./studio-gradlew testDebugUnitTest`.
- [ ] Run `./studio-gradlew detekt lint` if broad UI or architecture changes landed.
- [ ] Run affected instrumented tests if an emulator is available.
- [ ] Perform manual D-pad checklist for playback and setup flows.

## Suggested Work Slices

### Slice A: UI Primitive Foundation

- Add shared primitives.
- Migrate a tiny, low-risk component.
- Compile and verify visual/focus basics.

### Slice B: IPTV Settings Screen Split

- Extract route/content/forms/status/dialogs.
- Use shared primitives.
- Preserve ViewModel untouched except imports/callback names if needed.

### Slice C: Settings and Channel Management Split

- Apply the same pattern.
- Remove unused channel management edit fields if safe.

### Slice D: IPTV Workflow Coordinators

- Extract demo playlist import, device info, proxy pairing coordinator.
- Keep UI mostly unchanged.

### Slice E: Number Pad and Overlay State

- Extract number pad controller.
- Extract overlay state/auto-hide if tests make it safe.

### Slice F: Playback EPG Coordination

- Extract banner/panel EPG loading.
- Preserve debounce/cancellation behavior with tests.

### Slice G: Focus Helpers and Tests

- Add safe focus helper and delayed focus wrapper.
- Add or document focus verification.

### Slice H: Navigation/Startup/Message Cleanup

- Remove startup blocking.
- Start route/message cleanup after major UI splits are stable.

## Manual D-pad Verification Checklist

Use this checklist after any focus or playback UI refactor:

- [ ] Launch with existing channels: playback opens and video/player loading state appears.
- [ ] D-pad UP/DOWN switches channels when no overlay is open.
- [ ] D-pad LEFT opens channel list and focuses current channel.
- [ ] BACK dismisses channel list.
- [ ] If EPG is enabled/configured, RIGHT from channel row moves into EPG panel.
- [ ] LEFT from EPG panel returns to the previously focused channel row.
- [ ] LEFT from channel row dismisses the channel list.
- [ ] OK opens number pad and default focus is correct.
- [ ] Number pad digits, backspace, clear, invalid number, valid confirm all behave correctly.
- [ ] BACK dismisses number pad.
- [ ] MENU opens settings overlay.
- [ ] Settings overlay actions still navigate to source setup, channel management, and settings.
- [ ] Error overlay retry, next channel, dismiss, and MENU escape still work.
- [ ] Settings screen initial focus is visible and BACK returns.
- [ ] IPTV setup mode selector focus and mode switching still work.
- [ ] Channel management add/edit/delete dialogs are reachable and dismiss correctly.

## Open Questions

- Should shared UI primitives live directly under `ui/components` or a new `ui/design` package?
- Should `IptvSettingsRoute`/`SettingsRoute` be new files or should the existing screen files keep route functions at the top?
- Should playback overlay state stay flat inside `PlaybackUiState` or become a nested `PlaybackOverlayState`?
- Should `ProxyPairingCoordinator` live near `ui/screens/iptv` because it emits UI-facing state, or under domain/usecase because it is workflow logic?
- Should the first focus test be automated with Compose UI immediately, or should the first refactor slice rely on a manual checklist until screen boundaries are cleaner?
- Should quick-menu emoji icons be replaced in this refactor or tracked as follow-up polish after shared primitives land?

## Complexity Tracking

This is a broad refactor with medium-to-high regression risk because Android TV focus and playback overlays are sensitive. The safest path is to start with non-playback UI extraction, preserve behavior mechanically, and delay focus-heavy changes until primitives and screen boundaries are stable. The highest-risk phases are playback ViewModel decomposition and focus hardening; both should have targeted tests or explicit manual verification before completion.

