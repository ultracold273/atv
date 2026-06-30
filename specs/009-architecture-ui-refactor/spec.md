# Feature Specification: Architecture and UI Refactor

**Feature Branch**: `009-architecture-ui-refactor`  
**Created**: 2026-06-28  
**Status**: Draft  
**Input**: Refactor the Android TV app architecture and UI implementation so the codebase is easier to read, understand, test, and extend, with particular focus on Compose TV screens, shared UI primitives, playback state coordination, and D-pad focus behavior.

## Overview

ATV already has a solid technical foundation: Kotlin, Compose for TV, Hilt, Room, DataStore, Media3, coroutine/Flow state, and a recognizable domain/data/ui split. The current implementation is functional, but several features have accumulated inside large screen composables and large ViewModels. This creates friction when adding or modifying UI behavior, especially in playback, IPTV setup, EPG surfaces, and Android TV remote navigation.

This feature refactors the codebase incrementally. It introduces clearer UI boundaries, reusable TV UI primitives, smaller application workflow coordinators, and more explicit D-pad focus rules. The goal is to preserve existing user behavior while making future work safer and faster.

The refactor should not redesign the app as a new product. It should clean the implementation beneath the existing user experience, then allow modest UI polish where repeated components become more consistent.

## User Scenarios & Testing

### User Story 1 - Maintain Existing Playback Behavior During Refactor (Priority: P1)

As a TV viewer, I want playback, channel switching, overlays, and error handling to behave the same after the refactor, so code cleanup does not disrupt daily watching.

**Independent Test**: Launch the app with channels configured, switch channels with D-pad UP/DOWN, open and dismiss channel list, number pad, settings menu, and error overlay, then verify behavior matches the pre-refactor flow.

**Acceptance Scenarios**:

1. **Given** playback is open and no overlay is visible, **When** I press D-pad UP or DOWN, **Then** the app switches to the previous or next channel as before.
2. **Given** playback is open and no overlay is visible, **When** I press D-pad LEFT, **Then** the channel list overlay opens and focuses the current channel.
3. **Given** playback is open and no overlay is visible, **When** I press OK/CENTER, **Then** the number pad overlay opens and accepts channel-number input.
4. **Given** any playback overlay is open, **When** I press BACK, **Then** the active overlay is dismissed before the app exits.
5. **Given** playback encounters an error, **When** the error overlay is visible, **Then** Retry, Next channel, dismiss, and Menu-to-settings escape behavior remain available.

### User Story 2 - Make UI Screens Easier to Read and Extend (Priority: P1)

As a maintainer, I want screen files to separate route wiring, stateless layout, feature components, and reusable primitives so I can understand and modify UI behavior without reading one very large file.

**Independent Test**: Inspect refactored IPTV settings, settings, and channel management screens and verify each has a clear route/content/component split, with repeated button/dialog/row behavior using shared primitives.

**Acceptance Scenarios**:

1. **Given** I open IPTV settings source code, **When** I look for mode-specific form UI, **Then** M3U8, Direct CTC, and Home Proxy forms are separated into focused components.
2. **Given** I open settings source code, **When** I look for common settings rows and dialogs, **Then** they use reusable ATV UI primitives instead of duplicating surface styling.
3. **Given** I open channel management source code, **When** I inspect add/edit/delete dialogs, **Then** form and dialog code is separated from the top-level screen layout.
4. **Given** a new setup field or row is needed, **When** it is added, **Then** the change can be made by composing existing primitives rather than copying surface/focus styling.

### User Story 3 - Preserve TV Remote Focus Reliability (Priority: P1)

As a TV viewer, I want D-pad navigation to remain predictable across overlays and forms, so the app feels native on Android TV.

**Independent Test**: Use D-pad keys to navigate the channel list, EPG panel, number pad, settings menu, and IPTV setup forms after refactor; verify focus movement, dismiss behavior, and selected states match current behavior or documented improvements.

**Acceptance Scenarios**:

1. **Given** the channel list is open without EPG, **When** I press LEFT from a channel row, **Then** the overlay dismisses.
2. **Given** the channel list is open with EPG, **When** I press RIGHT from a channel row, **Then** focus moves into the EPG panel.
3. **Given** focus is inside the EPG panel, **When** I press LEFT, **Then** focus returns to the previously focused channel row.
4. **Given** the number pad opens, **When** the first focus request completes, **Then** focus lands on the expected default number pad control without consuming the original OK press as an accidental digit.
5. **Given** a setup or settings screen opens, **When** D-pad navigation starts, **Then** the initial focus target is deterministic and visible.

### User Story 4 - Reduce ViewModel Responsibility Without Changing UI State Contracts (Priority: P2)

As a maintainer, I want complex workflows to move out of oversized ViewModels so business behavior can be tested independently and screen state remains easier to reason about.

**Independent Test**: Run existing ViewModel tests plus new coordinator/controller tests after extracting playback overlay logic, number-pad input, EPG loading, demo playlist import, and proxy pairing coordination.

**Acceptance Scenarios**:

1. **Given** playback ViewModel is refactored, **When** channel switching and overlay behavior are tested, **Then** public UI state and user-visible behavior remain equivalent.
2. **Given** IPTV settings ViewModel is refactored, **When** demo playlist import, proxy pairing, and channel import are tested, **Then** each workflow can be tested without requiring one monolithic ViewModel test.
3. **Given** an extracted coordinator fails, **When** the ViewModel receives the failure result, **Then** the same user-visible status or error state is emitted as before.

### User Story 5 - Keep Refactors Safe and Incremental (Priority: P2)

As a developer, I want each refactor slice to be small enough to review and verify, so cleanup does not introduce hidden regressions.

**Independent Test**: Review each refactor PR or commit and verify it has a clear scope, relevant tests, and no unrelated feature changes.

**Acceptance Scenarios**:

1. **Given** a refactor changes component boundaries only, **When** tests run, **Then** behavior tests continue to pass without needing unrelated production changes.
2. **Given** a refactor intentionally changes focus or UI behavior, **When** it is reviewed, **Then** the behavior change is documented and covered by a test or manual verification checklist.
3. **Given** a broad file is split, **When** the diff is reviewed, **Then** mechanical movement and behavioral changes are kept separate where practical.

## Functional Requirements

### UI Structure and Componentization

- **FR-001**: The app MUST introduce or adopt a clear route/content/component pattern for large Compose screens.
- **FR-002**: Route-level composables SHOULD collect ViewModel state and bind callbacks, but SHOULD NOT contain detailed layout or reusable UI primitive definitions.
- **FR-003**: Content-level composables SHOULD be stateless wherever practical and receive state plus event callbacks as parameters.
- **FR-004**: `IptvSettingsScreen` MUST be split into focused components for mode selection, M3U8 fields, Direct CTC fields, Home Proxy fields, pairing status, import status, actions, and dialogs.
- **FR-005**: `SettingsScreen` MUST be split so settings rows, toggle rows, stats, dialogs, and transient messages are not all defined in the top-level screen body.
- **FR-006**: `ChannelManagementScreen` MUST separate list rendering, channel row rendering, channel form dialog, and delete confirmation dialog.
- **FR-007**: The refactor MUST avoid changing user-visible screen hierarchy unless a change is explicitly documented as UI polish.

### Shared ATV UI Primitives

- **FR-008**: The app MUST introduce reusable TV-friendly UI primitives for repeated focused-surface patterns.
- **FR-009**: Shared primitives SHOULD cover common buttons, settings rows, list items, dialogs, text fields, status text, and overlay panels.
- **FR-010**: Shared primitives MUST use existing app theme tokens from `AtvColors` and `AtvTypography` unless those tokens are intentionally evolved.
- **FR-011**: Shared primitives MUST expose focus, disabled, destructive, and selected states where applicable.
- **FR-012**: Existing duplicated button/dialog/list-row styling SHOULD be migrated to shared primitives incrementally.
- **FR-013**: Shared primitives MUST remain small and app-specific; the refactor MUST NOT introduce a broad generic design system beyond current app needs.
- **FR-014**: Quick-menu icon usage SHOULD move away from emoji strings toward a consistent app icon approach when a suitable dependency or local icon convention is chosen.

### D-pad and Focus Behavior

- **FR-015**: Critical TV focus transitions MUST be explicit and documented in code or tests.
- **FR-016**: Focus behavior for playback overlays MUST preserve existing D-pad semantics unless a change is intentionally specified.
- **FR-017**: Channel-list-to-EPG focus transfer MUST remain deterministic when EPG is visible.
- **FR-018**: EPG-to-channel-list focus return MUST restore the previously focused channel row when possible.
- **FR-019**: Initial focus for channel list, number pad, settings menu, settings screen, channel management, and IPTV setup MUST remain deterministic.
- **FR-020**: Focus-request timing workarounds, such as delayed focus after overlay open, MUST be centralized or wrapped where practical so future components do not copy ad hoc delay logic.
- **FR-021**: Focus failures caused by unattached `FocusRequester`s MUST be handled safely without crashing.
- **FR-022**: Compose UI tests SHOULD cover the highest-risk focus flows: channel list open/dismiss, channel list to EPG and back, number pad default focus, settings menu open/dismiss, and IPTV mode selector focus.

### Playback Architecture

- **FR-023**: Playback ViewModel responsibilities SHOULD be decomposed into smaller collaborators without changing the public playback screen contract.
- **FR-024**: Channel switching and playback orchestration SHOULD be separated from overlay visibility and number-pad input logic.
- **FR-025**: Number pad input rules SHOULD be testable independently from the playback ViewModel.
- **FR-026**: Overlay visibility and auto-hide behavior SHOULD be testable independently from channel playback and EPG loading.
- **FR-027**: Banner and panel EPG loading SHOULD be isolated behind a coordinator or equivalent abstraction while preserving debounce and cancellation behavior.
- **FR-028**: `AtvPlayer` lifecycle behavior MUST remain unchanged unless covered by explicit playback tests.
- **FR-029**: Existing playback tests MUST continue to pass after each playback refactor slice.

### IPTV Setup Architecture

- **FR-030**: `IptvSettingsViewModel` SHOULD delegate platform-heavy or workflow-heavy operations to injectable collaborators.
- **FR-031**: Demo playlist loading and internal file creation SHOULD be handled by a dedicated importer or equivalent collaborator.
- **FR-032**: Proxy pairing creation, polling, cancellation, stale-response protection, and approval handling SHOULD be handled by a pairing coordinator or equivalent collaborator.
- **FR-033**: Device/app metadata lookup SHOULD be wrapped behind an injectable provider if it remains needed by pairing or setup flows.
- **FR-034**: Channel source import branching SHOULD remain testable and SHOULD NOT be embedded only in composable code.
- **FR-035**: Extracted IPTV setup workflows MUST preserve current status mapping for success, login failure, fetch failure, empty channels, cancelled pairing, expired pairing, and rejected pairing.

### Transient Messages and Effects

- **FR-036**: New or refactored UI code SHOULD prefer explicit UI effects or injected message dispatch over direct calls to a global singleton snackbar.
- **FR-037**: Existing `SnackBarManager` usage MAY remain temporarily, but new dependencies on global UI state SHOULD be avoided.
- **FR-038**: Transient messages MUST remain visible above all relevant screens and overlays where current behavior expects global feedback.
- **FR-039**: User-visible message strings SHOULD be localized through resources rather than hardcoded in ViewModels.

### Navigation and Startup

- **FR-040**: Navigation route definitions SHOULD evolve toward typed route objects or another safer structure when screen contracts are refactored.
- **FR-041**: Repeated back-stack checks SHOULD be reduced with small helpers or clearer route contracts where practical.
- **FR-042**: Startup logic SHOULD avoid blocking the main thread with `runBlocking`; a root loading route, root ViewModel, or asynchronous redirect SHOULD be used when this area is refactored.
- **FR-043**: Navigation cleanup MUST preserve first-run/no-channel routing and existing settings/playback entry points.

### Testing and Verification

- **FR-044**: Refactor work MUST preserve existing unit and instrumented test behavior unless the spec or follow-up plan explicitly changes behavior.
- **FR-045**: New pure logic extracted from ViewModels SHOULD receive unit tests.
- **FR-046**: New shared UI primitives SHOULD be exercised through affected screen tests or focused Compose UI tests where practical.
- **FR-047**: High-risk D-pad focus flows SHOULD have automated coverage or a documented manual verification checklist.
- **FR-048**: Each broad refactor slice MUST run relevant unit tests before completion.
- **FR-049**: Broad UI or architecture changes SHOULD run Detekt/lint before merge when feasible.

## Key Entities

- **Route Composable**: A composable that owns ViewModel lookup, lifecycle-aware state collection, navigation callbacks, and one-shot effects.
- **Content Composable**: A mostly stateless composable that renders a screen from state and event callbacks.
- **ATV UI Primitive**: A reusable app-specific Compose component for TV-focused controls such as buttons, rows, dialogs, fields, and overlay panels.
- **Focus Coordinator**: A small feature-local abstraction or helper set that makes D-pad focus targets and transitions explicit.
- **Playback Overlay Controller**: A collaborator that owns playback overlay visibility, dismissal order, and auto-hide timers.
- **Number Pad Input Controller**: A pure or near-pure collaborator that owns channel-number input rules and validation.
- **Playback EPG Coordinator**: A collaborator that owns banner/panel EPG loading, debounce, cancellation, and state mapping.
- **Proxy Pairing Coordinator**: A collaborator that owns pairing session creation, polling, cancellation, stale-response protection, and approved-token handling.
- **Device Info Provider**: An injectable wrapper for platform metadata such as device name, package name, and app version.

## Success Criteria

- **SC-001**: `IptvSettingsScreen.kt`, `SettingsScreen.kt`, and `ChannelManagementScreen.kt` are materially smaller and organized around route/content/component boundaries.
- **SC-002**: Repeated TV button, row, dialog, and text-field styling is represented by shared app primitives instead of repeated surface configuration in every screen.
- **SC-003**: Existing playback, settings, IPTV setup, channel management, import, and EPG ViewModel tests pass after refactor slices.
- **SC-004**: Playback D-pad flows for channel switching, channel list, EPG panel, number pad, settings menu, error overlay, and back behavior remain equivalent to current behavior or are documented as intentional changes.
- **SC-005**: At least one high-risk focus path has automated Compose UI coverage or a written manual verification checklist before focus-heavy playback changes merge.
- **SC-006**: `PlaybackViewModel` no longer directly owns all playback, overlay, number pad, and EPG responsibilities in one class.
- **SC-007**: `IptvSettingsViewModel` delegates proxy pairing and demo playlist/platform workflow details to smaller collaborators or equivalent application services.
- **SC-008**: No user-facing feature is removed as part of this refactor unless a separate spec or explicit requirement says so.

## Assumptions

- The current app behavior is the behavioral baseline unless this spec explicitly calls for a small cleanup or polish change.
- Compose for TV remains the UI technology.
- Hilt remains the dependency injection framework.
- Existing domain/data package boundaries remain broadly valid.
- The refactor can be delivered in multiple small PRs or commits over multiple days.
- It is acceptable for some old patterns, such as `SnackBarManager`, to remain during migration as long as new code moves toward clearer effects.

## Out of Scope

- Rewriting the app from scratch.
- Replacing Compose for TV with another UI framework.
- Redesigning the visual identity or app information architecture from first principles.
- Adding new channel source types.
- Changing IPTV, proxy, CTC, EPG, or playback protocols.
- Changing database schema solely for this refactor unless a specific cleanup requires it and is separately justified.
- Removing existing user-facing features.
- Building a generic design system intended for reuse outside this app.

## Dependencies

- **Existing UI implementation**: The refactor starts from current Compose screens and components.
- **Existing tests**: Current domain/data/ViewModel tests provide regression protection.
- **`009-architecture-ui-refactor/research.md`**: Source research that identifies current maintainability issues, risks, and recommended sequence.
- **Compose UI test framework**: Needed if automated focus tests are added or expanded.
- **Detekt and Android Lint**: Used to validate broad cleanup where feasible.

## Technical Decisions

- **Incremental refactor over rewrite**: The app has a good foundation and should be improved in slices.
- **UI primitives first**: Shared UI components reduce repetition and make later screen splits easier.
- **Start with IPTV settings**: It is large and visible but less risky than playback because it is less coupled to Media3 and overlay focus choreography.
- **Keep focus explicit**: Android TV focus behavior is product-critical; abstractions should clarify focus transitions, not hide them behind generic magic.
- **Feature-local coordinators first**: Extract local controllers/coordinators before introducing broad app-wide architecture changes.
- **Behavior-preserving extraction first**: Move code mechanically before making intentional behavior or visual changes.

