# Implementation Plan: ATV - Android TV IPTV Player

**Branch**: `001-iptv-player` | **Date**: 2025-12-29 | **Spec**: [spec.md](spec.md)
**Input**: Feature specification from `/specs/001-iptv-player/spec.md`

## Summary

Build an Android TV application that plays IPTV streams from M3U8 playlist files. The app provides a TV-optimized interface navigable entirely by remote control, supporting channel switching (UP/DOWN), channel list browsing (LEFT) with auto-focus on current channel, and direct channel selection via on-screen number pad (OK) with full D-pad navigation and backspace support. Features include buffering indicator overlays, automatic network reconnection, and smart playlist refresh that preserves playback state. Uses Compose for TV for modern declarative UI and Media3 ExoPlayer for streaming playback.

## Technical Context

**Language/Version**: Kotlin 1.9+ with Kotlin DSL for Gradle  
**Primary Dependencies**: Media3 ExoPlayer, Compose for TV (tv-foundation, tv-material), Hilt, Room, DataStore  
**Storage**: Room (SQLite) for playlist/channels, DataStore for preferences  
**Testing**: JUnit5 + MockK for unit tests, Compose UI Testing for UI tests  
**Target Platform**: Android TV, API 29+ (Android 10)  
**Project Type**: Mobile (Android TV single-module app)  
**Performance Goals**: Channel switch <3s, app launch to playback <5s, 60fps UI  
**Constraints**: <100MB memory, <200ms remote response, support 1000+ channels  
**Scale/Scope**: Single user, local file only, ~7 screens

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Evidence |
|-----------|--------|----------|
| I. Code Quality | ✅ PASS | Clean Architecture planned: domain/data/ui layers, Hilt for DI |
| II. Security Baseline | ⚠️ DEFERRED | Input validation deferred per MVP scope; documented in Out of Scope |
| III. Best Practice | ✅ PASS | MVVM + Repository pattern, Coroutines/Flow for async |
| IV. Testing Standards | ✅ PASS | Unit tests for parsers/repos, UI tests for navigation flows |
| V. UX Consistency | ✅ PASS | Single remote mapping, consistent overlay behaviors, auto-hide timers |
| VI. Performance | ✅ PASS | Targets defined: <3s channel switch, <100MB memory, 1000+ channels |

**Deferred Violations (Justified)**:
- Security validation deferred to post-MVP per explicit Out of Scope decision
- Accessibility (TalkBack) deferred per Out of Scope

## Project Structure

### Documentation (this feature)

```text
specs/001-iptv-player/
├── plan.md              # This file
├── research.md          # Phase 0: Technology research
├── data-model.md        # Phase 1: Entity definitions, DB schema, M3U8 format
├── quickstart.md        # Phase 1: Build/run instructions, dependencies
├── tasks.md             # Phase 2: Implementation tasks (generate via /speckit.tasks)
└── checklists/
    └── requirements.md  # Quality checklist for FRs
```

### Key Documentation Links

| Need | Document | Section |
|------|----------|---------|
| How to build/run | [quickstart.md](quickstart.md) | Full document |
| Domain entity definitions | [data-model.md](data-model.md) | [#domain-entities](data-model.md#domain-entities) |
| Room database schema | [data-model.md](data-model.md) | [#database-schema](data-model.md#database-schema) |
| M3U8 parsing rules | [data-model.md](data-model.md) | [#m3u8-format-specification](data-model.md#m3u8-format-specification) |
| UI state models | [data-model.md](data-model.md) | [#state-models](data-model.md#state-models) |
| ExoPlayer setup | [research.md](research.md) | [#media3-exoplayer](research.md#media3-exoplayer) |
| Compose for TV patterns | [research.md](research.md) | [#compose-for-tv](research.md#compose-for-tv) |
| Focus management | [research.md](research.md) | [#focus-management](research.md#focus-management) |
| Hilt DI setup | [research.md](research.md) | [#hilt-setup](research.md#hilt-setup) |
| Remote key handling | [research.md](research.md) | [#android-tv-remote-handling](research.md#android-tv-remote-handling) |
| Functional requirements | [spec.md](spec.md) | #functional-requirements |
| User stories | [spec.md](spec.md) | #user-scenarios--testing-mandatory |
| Success criteria | [spec.md](spec.md) | #success-criteria-mandatory |

### Source Code (repository root)

```text
app/
├── build.gradle.kts
└── src/
    ├── main/
    │   ├── AndroidManifest.xml
    │   ├── kotlin/com/example/atv/
    │   │   ├── AtvApplication.kt           # Hilt application
    │   │   ├── MainActivity.kt             # Single activity, Compose host
    │   │   ├── di/                         # Hilt modules
    │   │   │   ├── AppModule.kt
    │   │   │   ├── DatabaseModule.kt
    │   │   │   └── PlayerModule.kt
    │   │   ├── domain/                     # Business logic (pure Kotlin)
    │   │   │   ├── model/
    │   │   │   │   ├── Channel.kt
    │   │   │   │   ├── Playlist.kt
    │   │   │   │   └── PlaybackState.kt
    │   │   │   ├── repository/
    │   │   │   │   ├── ChannelRepository.kt
    │   │   │   │   └── PreferencesRepository.kt
    │   │   │   └── usecase/
    │   │   │       ├── LoadPlaylistUseCase.kt
    │   │   │       ├── SwitchChannelUseCase.kt
    │   │   │       └── ParseM3U8UseCase.kt
    │   │   ├── data/                       # Data layer implementation
    │   │   │   ├── local/
    │   │   │   │   ├── db/
    │   │   │   │   │   ├── AtvDatabase.kt
    │   │   │   │   │   ├── ChannelDao.kt
    │   │   │   │   │   └── ChannelEntity.kt
    │   │   │   │   └── datastore/
    │   │   │   │       └── UserPreferencesDataStore.kt
    │   │   │   ├── parser/
    │   │   │   │   └── M3U8Parser.kt
    │   │   │   └── repository/
    │   │   │       ├── ChannelRepositoryImpl.kt
    │   │   │       └── PreferencesRepositoryImpl.kt
    │   │   ├── player/                     # Media3 ExoPlayer wrapper
    │   │   │   ├── AtvPlayer.kt
    │   │   │   └── PlayerState.kt
    │   │   └── ui/                         # Compose for TV UI
    │   │       ├── theme/
    │   │       │   ├── Theme.kt
    │   │       │   └── Typography.kt
    │   │       ├── navigation/
    │   │       │   └── AtvNavGraph.kt
    │   │       ├── screens/
    │   │       │   ├── playback/
    │   │       │   │   ├── PlaybackScreen.kt
    │   │       │   │   └── PlaybackViewModel.kt
    │   │       │   ├── setup/
    │   │       │   │   ├── SetupScreen.kt
    │   │       │   │   └── SetupViewModel.kt
    │   │       │   ├── settings/
    │   │       │   │   ├── SettingsScreen.kt
    │   │       │   │   └── SettingsViewModel.kt
    │   │       │   └── channelmanagement/
    │   │       │       ├── ChannelManagementScreen.kt
    │   │       │       └── ChannelManagementViewModel.kt
    │   │       └── components/
    │   │           ├── ChannelInfoOverlay.kt
    │   │           ├── ChannelListOverlay.kt
    │   │           ├── NumberPadOverlay.kt
    │   │           ├── ErrorOverlay.kt
    │   │           └── SettingsMenu.kt
    │   └── res/
    │       ├── values/
    │       └── drawable/
    └── test/
        └── kotlin/com/example/atv/
            ├── domain/usecase/
            │   └── ParseM3U8UseCaseTest.kt
            ├── data/parser/
            │   └── M3U8ParserTest.kt
            └── data/repository/
                └── ChannelRepositoryTest.kt

build.gradle.kts                            # Root build file
settings.gradle.kts                         # Project settings
gradle/
└── libs.versions.toml                      # Version catalog
```

**Structure Decision**: Single Android module (`app/`) following standard Android project layout. Clean Architecture layers (domain/data/ui) organized as packages within the module. This keeps build simple while maintaining separation of concerns.

## Cross-Reference Map

| Component | Implementation Details | Research Reference |
|-----------|----------------------|-------------------|
| M3U8 Parsing | [data-model.md#m3u8-format-specification](data-model.md#m3u8-format-specification) | [research.md#m3u8-parsing](research.md#m3u8-parsing) |
| Video Playback | `player/AtvPlayer.kt` | [research.md#media3-exoplayer](research.md#media3-exoplayer) |
| TV Navigation | `ui/screens/playback/PlaybackScreen.kt` | [research.md#compose-for-tv](research.md#compose-for-tv) |
| Focus Handling | `ui/components/*.kt` | [research.md#focus-management](research.md#focus-management) |
| Database | [data-model.md#database-schema](data-model.md#database-schema) | [research.md#room-database](research.md#room-database) |
| Preferences | [data-model.md#domain-entities](data-model.md#domain-entities) (UserPreferences) | [research.md#datastore](research.md#datastore) |
| Dependency Injection | `di/*.kt` | [research.md#hilt-setup](research.md#hilt-setup) |
| Remote Handling | `ui/util/KeyEventExtensions.kt` | [research.md#android-tv-remote-handling](research.md#android-tv-remote-handling) |

---

## Implementation Phases

### Phase 1: Project Foundation (Tasks 1-5)

| # | Task | FRs | Dependencies | Reference |
|---|------|-----|--------------|-----------|
| 1 | **Project Setup** - Create Android TV project with Gradle, version catalog, Hilt setup | - | None | [quickstart.md](quickstart.md) (gradle config) |
| 2 | **Domain Models** - Create Channel, Playlist, PlaybackState, UserPreferences | - | Task 1 | [data-model.md#domain-entities](data-model.md#domain-entities) |
| 3 | **Database Layer** - Room setup with ChannelEntity, ChannelDao, AtvDatabase | FR-015 | Task 2 | [data-model.md#database-schema](data-model.md#database-schema), [research.md#room-database](research.md#room-database) |
| 4 | **DataStore Setup** - UserPreferencesDataStore for preferences | FR-004 | Task 2 | [research.md#datastore](research.md#datastore) |

### Phase 2: Foundational & Core Playback (Tasks 6-18)

| # | Task | FRs | Dependencies | Reference |
|---|------|-----|--------------|-----------|
| 5 | **M3U8 Parser** - Parse M3U8 files to Channel list | FR-013 | Task 2 | [data-model.md#m3u8-format-specification](data-model.md#m3u8-format-specification), [research.md#m3u8-parsing](research.md#m3u8-parsing) |
| 6 | **Channel Repository** - ChannelRepository interface + impl with Room | FR-015 | Tasks 3, 5 | [data-model.md#database-schema](data-model.md#database-schema) |
| 7 | **AtvPlayer Wrapper** - ExoPlayer wrapper with state flow, buffering state exposure | FR-001, FR-002, FR-005 | Task 1 | [research.md#media3-exoplayer](research.md#media3-exoplayer) |
| 8 | **Playback ViewModel** - PlaybackViewModel with player + channel state | FR-003 | Tasks 6, 7 | [data-model.md#state-models](data-model.md#state-models) |

### Phase 3: User Story 1 - Watch IPTV (Tasks 19-24)

| # | Task | FRs | Dependencies | Reference |
|---|------|-----|--------------|-----------|
| 9 | **TV Theme** - Compose for TV theme, typography | - | Task 1 | [research.md#compose-for-tv](research.md#compose-for-tv) |
| 10 | **Navigation Graph** - AtvNavGraph with screen routes | - | Task 9 | [quickstart.md](quickstart.md) (project structure) |
| 11 | **Playback Screen** - Full-screen video with PlayerView | FR-029 | Tasks 8, 9, 10 | [research.md#compose-for-tv](research.md#compose-for-tv) |
| 12 | **D-pad Key Handler** - Modifier extension for remote input | FR-006 | Task 9 | [research.md#android-tv-remote-handling](research.md#android-tv-remote-handling) |

### Phase 4: User Stories 4 & 2 - Load & Switch (Tasks 25-37)

| # | Task | FRs | Dependencies | Reference |
|---|------|-----|--------------|-----------|
| 13 | **Channel Switching** - UP/DOWN button handling | FR-007 | Tasks 11, 12 | [spec.md](spec.md) (US-002) |
| 14 | **Channel Info Overlay** - Show channel number/name on switch | FR-027, FR-028 | Task 13 | [research.md#focus-management](research.md#focus-management) |
| 15 | **Channel List Overlay** - LEFT button shows scrollable list with D-pad navigation, auto-focus on current channel | FR-012, FR-020-026 | Tasks 11, 12 | [data-model.md#state-models](data-model.md#state-models) (PlaybackUiState) |
| 16 | **Number Pad Overlay** - OK button shows digit input with D-pad navigation, backspace button, 3-digit max | FR-008, FR-009, FR-010, FR-011 | Tasks 11, 12 | [spec.md](spec.md) (US-003) |
| 17 | **Auto-hide Logic** - Timers for overlay auto-dismiss | FR-028 | Tasks 14, 15, 16 | [spec.md](spec.md) (3s/10s timers) |

### Phase 5: User Stories 6 & 3 - Browse & Jump (Tasks 38-55)

| # | Task | FRs | Dependencies | Reference |
|---|------|-----|--------------|-----------|
| 18 | **Setup Screen** - First-launch "Browse Files" UI with instructions | FR-014 | Tasks 9, 10 | [spec.md](spec.md) (US-004) |
| 19 | **File Picker Integration** - SAF file picker for M3U8 | FR-014 | Task 18 | Android Storage Access Framework |
| 20 | **Load Playlist UseCase** - Parse file → save to Room | FR-013, FR-015 | Tasks 5, 6, 19 | [data-model.md#m3u8-format-specification](data-model.md#m3u8-format-specification) |
| 21 | **Playlist Refresh** - Auto-refresh on app restart, resume on last-watched channel if exists | FR-016 | Tasks 4, 20 | [spec.md](spec.md) (Clarifications) |
| 22 | **Settings Screen** - Menu with playlist management | FR-030-032 | Tasks 10, 20 | [spec.md](spec.md) (US-007) |

### Phase 6: User Stories 7 & 5 - Settings & Edit (Tasks 56-71)

| # | Task | FRs | Dependencies | Reference |
|---|------|-----|--------------|-----------|
| 23 | **Channel Management Screen** - List view with actions | FR-017-019 | Tasks 6, 10 | [spec.md](spec.md) (US-005) |
| 24 | **Add Channel Form** - Name + URL input | FR-017 | Task 23 | [data-model.md#domain-entities](data-model.md#domain-entities) (Channel) |
| 25 | **Edit/Delete Channel** - Modify or remove entries | FR-018, FR-019 | Task 23 | [spec.md](spec.md) (US-005) |

### Phase 7: Error Handling & Polish (Tasks 72-82)

| # | Task | FRs | Dependencies | Reference |
|---|------|-----|--------------|-----------|
| 26 | **Error Overlay** - Stream failure with Retry/Next | - | Task 11 | [spec.md](spec.md) (Edge Cases) |
| 27 | **Loading States** - Buffering spinner overlay on video (last frame visible), auto-hide on resume | FR-005 | Tasks 7, 11 | [data-model.md#state-models](data-model.md#state-models) (PlaybackState) |
| 28 | **Network Reconnection** - Auto-retry with subtle indicator, resume playback on reconnect | - | Tasks 7, 11 | [spec.md](spec.md) (Clarifications - Session 2025-12-29) |
| 29 | **Session Persistence** - Save/restore last channel | FR-004 | Tasks 4, 8 | [spec.md](spec.md) (US-001 AS-3) |
| 30 | **Performance Testing** - Validate 1000+ channels, <3s switch | - | All | [spec.md](spec.md) (Success Criteria) |

---

## Task Dependency Graph

```
Phase 1 (Foundation)
    1 ─────────┬─────────┬─────────┐
               │         │         │
               v         v         v
               2         9 ────> 10
               │
    ┌──────────┼──────────┐
    │          │          │
    v          v          v
    3          4          5
    │                     │
    └─────────┬───────────┘
              │
              v
Phase 2 (Core Playback)
              6 <──────── 5
              │
              v
         7 ──> 8
              │
              v
Phase 3 (UI Shell)
         11 <── 9, 10
              │
              v
         12 (D-pad)
              │
              v
Phase 4 (Navigation)
    13 ──> 14
    15, 16, 17
              │
              v
Phase 5 (Playlist)
    18 ──> 19 ──> 20 ──> 21
              │
              v
         22 (Settings)
              │
              v
Phase 6 (Channel Mgmt)
    23 ──> 24, 25
              │
              v
Phase 7 (Polish)
    26, 27, 28, 29, 30
```

---

## Complexity Tracking

> No violations requiring justification. All patterns are standard Android best practices.
