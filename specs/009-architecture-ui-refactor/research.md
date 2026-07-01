# Research: Architecture and UI Refactor

**Feature**: 009-architecture-ui-refactor  
**Date**: 2026-06-28  
**Status**: Draft

## Executive Summary

The current ATV codebase has a solid foundation: Kotlin, Jetpack Compose for TV, Hilt, Room, DataStore, Media3, coroutine/Flow-based state, and a visible domain/data/ui package split. The implementation is not in a rewrite-needed state. The main issue is that several feature additions have accumulated inside large screen composables and large ViewModels, especially around playback overlays, IPTV setup, EPG, and TV remote focus behavior.

The recommended refactor direction is incremental. Start by extracting reusable UI primitives and route/content/component boundaries, then split the largest ViewModels into smaller coordinators or use-case-style services. The highest UI risk is D-pad focus behavior because it is currently spread across multiple composables through `FocusRequester`, delayed focus requests, and custom `onKeyEvent` handlers.

---

## Scope of Research

This research reviewed the implementation structure and maintainability of:

- App startup and navigation
- Playback screen and playback ViewModel
- IPTV/channel source setup screen and ViewModel
- Settings and channel management screens
- Shared UI components and theme primitives
- D-pad/focus handling for Android TV
- Testing posture and refactor risk

Primary files examined:

- `app/src/main/kotlin/com/example/atv/MainActivity.kt`
- `app/src/main/kotlin/com/example/atv/ui/navigation/AtvNavGraph.kt`
- `app/src/main/kotlin/com/example/atv/ui/screens/playback/PlaybackScreen.kt`
- `app/src/main/kotlin/com/example/atv/ui/screens/playback/PlaybackViewModel.kt`
- `app/src/main/kotlin/com/example/atv/ui/screens/iptv/IptvSettingsScreen.kt`
- `app/src/main/kotlin/com/example/atv/ui/screens/iptv/IptvSettingsViewModel.kt`
- `app/src/main/kotlin/com/example/atv/ui/screens/settings/SettingsScreen.kt`
- `app/src/main/kotlin/com/example/atv/ui/screens/settings/SettingsViewModel.kt`
- `app/src/main/kotlin/com/example/atv/ui/screens/channelmanagement/ChannelManagementScreen.kt`
- `app/src/main/kotlin/com/example/atv/ui/components/ChannelListOverlay.kt`
- `app/src/main/kotlin/com/example/atv/ui/components/EpgPanel.kt`
- `app/src/main/kotlin/com/example/atv/ui/components/NumberPadOverlay.kt`
- `app/src/main/kotlin/com/example/atv/ui/components/SettingsMenu.kt`
- `app/src/main/kotlin/com/example/atv/ui/theme/Theme.kt`
- `app/src/main/kotlin/com/example/atv/ui/theme/Typography.kt`

---

## Current Strengths

### Clear broad architecture

The repo already separates many concerns by package:

- `domain/model`, `domain/repository`, `domain/usecase`
- `data/local`, `data/parser`, `data/epg`, `data/proxy`, `data/repository`
- `ui/screens`, `ui/components`, `ui/theme`, `ui/navigation`, `ui/util`
- `player`
- `di`

This makes the codebase understandable at the directory level and gives the refactor a good path forward.

### Appropriate Android TV stack

The app uses Compose for TV, Media3, Hilt, Room, DataStore, Flow, and coroutine-based ViewModels. These are appropriate choices for this project.

### Existing test investment

The project has meaningful unit tests for domain logic, parsers, repositories, EPG behavior, proxy clients, and ViewModels. This lowers refactor risk, especially outside the UI rendering layer.

### Theme tokens exist

`AtvColors` and `AtvTypography` already provide a base design language. This is useful, but not yet enough to keep components consistent by itself.

---

## Key Findings

## 1. Playback ViewModel has too many responsibilities

`PlaybackViewModel` currently owns:

- Player initialization and release
- Channel list observation
- Current-channel switching
- Stream URL resolution
- Preference persistence for last channel
- Source mode and udpxy proxy observation
- Player state observation
- Banner EPG loading
- Panel EPG loading and debounce behavior
- Overlay visibility
- Overlay auto-hide timers
- Number pad input rules
- Error handling
- App-wide snackbar calls

This makes the class difficult to extend safely. New playback or overlay behavior has to touch a central object that already coordinates many unrelated concerns.

### Refactor direction

Extract smaller collaborators while preserving the public screen contract:

- `PlaybackController` or use-case coordinator for play/switch/retry
- `PlaybackOverlayController` for overlay visibility and auto-hide behavior
- `NumberPadInputController` for digit input and validation
- `PlaybackEpgCoordinator` for banner and panel EPG loading
- `PlaybackUiEffect` or message channel for transient snackbar events

The goal is not to create many abstractions immediately. The goal is to move the highest-churn logic out of the central ViewModel.

---

## 2. TV focus and D-pad navigation are spread across components

Focus behavior is currently managed through local `FocusRequester`s and custom key handlers in multiple places:

- `PlaybackScreen` stores cross-component requesters for the channel list and EPG panel.
- `ChannelListOverlay` publishes item focus requesters upward and consumes left/back keys.
- `EpgPanel` owns date-tab and program-list focus requesters and manually routes left/right/up/down behavior.
- `NumberPadOverlay` and `SettingsMenu` each own their own initial focus rules.

This is common in Compose TV apps, but the current implementation makes focus transitions implicit and hard to reason about. Adding one more panel or changing the EPG layout can break remote navigation in surprising ways.

### Refactor direction

Create explicit focus models per overlay or per playback surface:

- Named focus targets, such as `ChannelList`, `EpgTodayTab`, `EpgProgramRow`, `NumberPadCenter`, `SettingsFirstItem`
- Small helper APIs for initial focus and safe focus requests
- A documented transition table for D-pad behavior
- Compose UI tests for critical focus paths

The refactor should avoid moving all focus into one global manager too early. A per-overlay focus coordinator is likely enough.

---

## 3. Screen composables mix layout, workflow, and primitives

Large screen files contain route glue, layout, mode-specific form content, status formatting, dialogs, and private component primitives.

Examples:

- `IptvSettingsScreen.kt` includes the whole screen, source mode selector, M3U8 fields, Direct CTC fields, Home Proxy fields, pairing status rendering, action buttons, status formatting, and clear dialog.
- `SettingsScreen.kt` includes page layout, stats, rows, toggle rows, dialogs, local snackbar-like rendering, and dialog buttons.
- `ChannelManagementScreen.kt` includes list rendering, add/edit/delete dialogs, local form state, and repeated button styles.

This makes each file harder to scan and increases style drift.

### Refactor direction

Use a route/content/component split:

- `XRoute`: obtains ViewModel state and wires callbacks
- `XScreen` or `XContent`: stateless screen layout
- Feature components: `SourceModeSelector`, `M3u8SourceForm`, `DirectCtcSourceForm`, `HomeProxySourceForm`, `ImportStatusView`
- Shared primitives: buttons, dialogs, list rows, text fields, overlay panels

This should be done one screen at a time, starting with `IptvSettingsScreen` because it is large, visible, and less coupled to Media3 playback than `PlaybackScreen`.

---

## 4. ViewModels sometimes contain platform-heavy workflows

`IptvSettingsViewModel` currently handles UI state, credential persistence, source mode persistence, demo playlist asset copying, URL validation, CTC import, Home Proxy import, pairing nonce generation, device/app metadata, and pairing polling.

This mixes UI concerns with application-service concerns. It also makes targeted tests more complicated because the ViewModel owns several workflows that could be tested independently.

### Refactor direction

Extract workflow services:

- `DemoPlaylistImporter`
- `ChannelSourceImportCoordinator`
- `ProxyPairingCoordinator`
- `DeviceInfoProvider`
- `NonceGenerator` or a simple injectable random source if test determinism is needed

The ViewModel should remain responsible for presenting state and responding to UI events, but the detailed workflow should move to injectable collaborators.

---

## 5. Shared UI primitives are underdeveloped

The app has theme tokens, but repeated patterns are reimplemented across many files:

- Focusable TV buttons
- Destructive actions
- Dialog containers
- Settings rows
- List rows
- Text fields
- Overlay panels
- Focus borders
- Surface colors and rounded shapes

Duplicated styling makes the app harder to polish. It also makes UI refactors expensive because small visual changes require edits across many components.

### Refactor direction

Add a small UI kit under `ui/components` or `ui/design`:

- `AtvButton`
- `AtvIconButton` or `AtvMenuButton`
- `AtvListItem`
- `AtvSettingsRow`
- `AtvDialog`
- `AtvTextField`
- `AtvOverlayPanel`
- `AtvStatusText`

The UI kit should wrap TV Material surfaces and app theme tokens. It should not become a general-purpose design system before the app needs one.

---

## 6. Global snackbar singleton creates hidden coupling

`SnackBarManager` is a singleton object called from the Activity and ViewModels. It is simple, but it creates hidden global state and makes UI events less explicit.

### Refactor direction

Replace or wrap it with one of:

- Screen-level `UiEffect` channels from ViewModels
- An injected `MessageBus`
- A root app state object that owns transient messages

The best incremental step is to introduce `UiEffect` for new work and migrate existing snackbar calls opportunistically.

---

## 7. Startup uses blocking work on the Activity path

`MainActivity` uses `runBlocking` to decide whether to start on setup or playback. The current call is probably fast, but blocking the main thread during startup is not ideal Android practice.

### Refactor direction

Introduce one of:

- A lightweight splash/loading route that observes startup state asynchronously
- A root app ViewModel that emits the start destination
- Navigation that starts at a deterministic root route and redirects after channel count is loaded

This is a lower-priority refactor than UI/component cleanup.

---

## 8. Navigation route modeling is basic

Navigation uses string constants and repeated back-stack checks. This is fine at the current size, but the setup routes already show duplication: both `IPTV_SETTINGS` and `CHANNEL_SOURCE_M3U8` render `IptvSettingsScreen` with different initial/back behavior.

### Refactor direction

Introduce typed route definitions or sealed route objects. Also consider a small helper for back behavior. This should happen after screen contracts are cleaner.

---

## 9. Some UI behavior is unfinished or inconsistent

Observed examples:

- Quick menu `Clear playlist` is still a TODO.
- Quick menu uses emoji icons instead of a consistent icon approach.
- Channel management dialog stores local form state while the ViewModel also contains unused edit fields.
- Some ViewModel error messages are hardcoded English instead of localized string resources.
- Dialogs and cards use similar but duplicated shapes, padding, and focus borders.

### Refactor direction

Fold these into the UI primitive work and screen split. Avoid making a separate cleanup branch for every small inconsistency unless one blocks a user-facing workflow.

---

## 10. Tests are strongest below the composable layer

The project has useful tests for domain/data/ViewModel behavior. The highest-risk area is currently TV focus behavior and overlay navigation, which is not as directly protected.

### Refactor direction

Add focused tests for:

- Overlay open/close behavior
- Number pad input and confirm behavior
- Channel list focus and select behavior
- EPG panel day switching and focus recovery
- Settings and IPTV setup form state

Where Compose UI tests are costly, extract pure focus transition rules into small testable classes.

---

## Recommended Refactor Sequence

### Phase 1: UI foundation and screen splitting

Start with low-risk UI extraction:

- Add shared TV UI primitives.
- Split `IptvSettingsScreen` into route/content/forms/status/dialog components.
- Split `SettingsScreen` into route/content/rows/dialog components.
- Split `ChannelManagementScreen` dialog/form components and remove unused ViewModel form fields if safe.

Why first: this improves readability quickly and builds reusable pieces for later playback work.

### Phase 2: IPTV setup workflow extraction

Extract platform/workflow concerns from `IptvSettingsViewModel`:

- Demo playlist import
- Proxy pairing polling
- Device/app metadata
- Channel source import coordination

Why second: it reduces ViewModel size without touching playback focus behavior.

### Phase 3: Playback ViewModel decomposition

Split playback responsibilities:

- Channel switching/playback orchestration
- Overlay state and auto-hide timers
- Number pad input
- EPG loading
- UI effects/messages

Why third: playback is central and user-facing, so it benefits from the safer foundation created in phases 1 and 2.

### Phase 4: Focus/navigation hardening

Introduce explicit focus coordinators and tests for TV remote behavior:

- Channel list and EPG panel focus transitions
- Number pad initial focus and action grid behavior
- Settings menu navigation
- Back/left/right behavior across overlays

Why fourth: focus changes are high-risk. Refactor after component boundaries are clearer.

### Phase 5: App startup and navigation cleanup

Address lower-priority architecture polish:

- Remove startup `runBlocking`
- Introduce typed routes or route objects
- Reduce repeated navigation/back-stack checks

Why fifth: important, but not the current biggest source of UI complexity.

---

## Initial Design Decisions

### Decision: Refactor incrementally, not as a rewrite

**Rationale**: The repo has working architecture and tests. A rewrite would add risk without solving the core maintainability issues faster.

### Decision: Start with UI primitives and `IptvSettingsScreen`

**Rationale**: IPTV settings is large, visible, and already contains multiple modes/forms. It is a good place to prove the route/content/component pattern before touching playback.

### Decision: Keep TV focus behavior explicit

**Rationale**: Android TV focus is product-critical. The refactor should make focus rules easier to see and test, not hide them behind overly generic abstractions.

### Decision: Prefer feature-local coordinators before global architecture changes

**Rationale**: `PlaybackViewModel` and `IptvSettingsViewModel` have different complexity profiles. Local extraction is less risky than introducing a broad app architecture pattern in one step.

---

## Risks

### Focus regressions

Remote navigation can break even when visual UI looks unchanged. Any refactor touching `FocusRequester`, `onKeyEvent`, or overlay composition needs targeted manual and/or Compose UI verification.

### Over-abstraction

Creating too many generic UI primitives too early can make the app harder to read. Start with repeated patterns that already exist in at least two places.

### Behavior drift during screen splitting

Moving code from large screen files into smaller components can accidentally change focus order, enabled states, or back behavior. Keep component extraction mechanical first, then improve behavior in separate commits.

### ViewModel extraction changing coroutine timing

Playback and pairing both depend on cancellation and debounce behavior. Extracting coordinators should preserve coroutine scopes and cancellation semantics deliberately.

---

## Verification Strategy

For each refactor slice:

- Run relevant unit tests.
- Run affected ViewModel tests.
- Add tests before changing behavior-heavy logic where practical.
- Manually verify D-pad flows on TV/emulator-sized UI.
- For playback-related changes, verify channel switch, channel list, number pad, settings menu, error overlay, and EPG panel behavior.
- Run `./studio-gradlew testDebugUnitTest` before merging broad changes.
- Run Detekt/lint for larger UI or architecture changes.

---

## Open Questions

- Should shared UI primitives live under `ui/components` or a new `ui/design` package?
- Should playback overlay state remain inside `PlaybackUiState` or move into a nested `OverlayUiState`?
- Should transient messages use per-screen `UiEffect` channels or a root app message bus?
- Should proxy pairing become a standalone domain/application service before or after UI screen splitting?
- Which focus paths should receive automated Compose UI tests first?
- Should the refactor include UI polish such as replacing emoji icons, or should that be a separate visual cleanup after structural work?

