# Tasks: ATV - Android TV IPTV Player

**Input**: Design documents from `/specs/001-iptv-player/`
**Prerequisites**: plan.md ✅, spec.md ✅, research.md ✅, data-model.md ✅, quickstart.md ✅

**Tests**: Tests are NOT included in this task list (not explicitly requested in spec). Add test tasks if TDD approach is desired.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2)
- Setup/Foundational phases have no story label

## Path Conventions

- **Android Project**: `app/src/main/kotlin/com/example/atv/`
- **Tests**: `app/src/test/kotlin/com/example/atv/`
- **Resources**: `app/src/main/res/`
- **Build**: Root `build.gradle.kts`, `gradle/libs.versions.toml`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization, Gradle configuration, Hilt setup

**Reference**: [quickstart.md](quickstart.md) (full document)

- [ ] T001 Create Android TV project with Gradle Kotlin DSL in `build.gradle.kts` and `settings.gradle.kts`
- [ ] T002 [P] Configure version catalog with all dependencies in `gradle/libs.versions.toml`
- [ ] T003 [P] Create `AtvApplication.kt` with Hilt `@HiltAndroidApp` annotation in `app/src/main/kotlin/com/example/atv/AtvApplication.kt`
- [ ] T004 [P] Configure `AndroidManifest.xml` with Leanback launcher, internet permission, and banner in `app/src/main/AndroidManifest.xml`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Domain models, database layer, and core infrastructure that ALL user stories depend on

**⚠️ CRITICAL**: No user story work can begin until this phase is complete

**References**: 
- [data-model.md#domain-entities](data-model.md#domain-entities)
- [data-model.md#database-schema](data-model.md#database-schema)
- [research.md#room-database](research.md#room-database)
- [research.md#datastore](research.md#datastore)

- [ ] T005 [P] Create `Channel` domain model in `app/src/main/kotlin/com/example/atv/domain/model/Channel.kt`
- [ ] T006 [P] Create `Playlist` domain model in `app/src/main/kotlin/com/example/atv/domain/model/Playlist.kt`
- [ ] T007 [P] Create `PlaybackState` domain model in `app/src/main/kotlin/com/example/atv/domain/model/PlaybackState.kt`
- [ ] T008 [P] Create `UserPreferences` domain model in `app/src/main/kotlin/com/example/atv/domain/model/UserPreferences.kt`
- [ ] T009 Create `ChannelEntity` Room entity in `app/src/main/kotlin/com/example/atv/data/local/db/ChannelEntity.kt`
- [ ] T010 Create `ChannelDao` Room DAO in `app/src/main/kotlin/com/example/atv/data/local/db/ChannelDao.kt`
- [ ] T011 Create `AtvDatabase` Room database in `app/src/main/kotlin/com/example/atv/data/local/db/AtvDatabase.kt`
- [ ] T012 Create `UserPreferencesDataStore` in `app/src/main/kotlin/com/example/atv/data/local/datastore/UserPreferencesDataStore.kt`
- [ ] T013 [P] Create `AppModule` Hilt module in `app/src/main/kotlin/com/example/atv/di/AppModule.kt`
- [ ] T014 [P] Create `DatabaseModule` Hilt module in `app/src/main/kotlin/com/example/atv/di/DatabaseModule.kt`
- [ ] T015 Create Compose for TV theme in `app/src/main/kotlin/com/example/atv/ui/theme/Theme.kt`
- [ ] T016 [P] Create typography definitions in `app/src/main/kotlin/com/example/atv/ui/theme/Typography.kt`

**Checkpoint**: Foundation ready - user story implementation can now begin

---

## Phase 3: User Story 1 - Watch IPTV Channel (Priority: P1) 🎯 MVP

**Goal**: Launch app and immediately start watching an IPTV channel with full-screen video

**Spec Reference**: [spec.md](spec.md) (US-001) - FR-001, FR-002, FR-003

**Independent Test**: Launch app with pre-loaded M3U8 playlist → video plays on first channel within 5 seconds

**References**:
- [research.md#media3-exoplayer](research.md#media3-exoplayer)
- [data-model.md#state-models](data-model.md#state-models)

### Implementation for User Story 1

- [ ] T017 [US1] Create `AtvPlayer` ExoPlayer wrapper with state flow in `app/src/main/kotlin/com/example/atv/player/AtvPlayer.kt`
- [ ] T018 [US1] Create `PlayerState` sealed class in `app/src/main/kotlin/com/example/atv/player/PlayerState.kt`
- [ ] T019 [US1] Create `PlayerModule` Hilt module in `app/src/main/kotlin/com/example/atv/di/PlayerModule.kt`
- [ ] T020 [US1] Create `ChannelRepository` interface in `app/src/main/kotlin/com/example/atv/domain/repository/ChannelRepository.kt`
- [ ] T021 [US1] Create `ChannelRepositoryImpl` in `app/src/main/kotlin/com/example/atv/data/repository/ChannelRepositoryImpl.kt`
- [ ] T022 [US1] Create `PlaybackViewModel` with player and channel state in `app/src/main/kotlin/com/example/atv/ui/screens/playback/PlaybackViewModel.kt`
- [ ] T023 [US1] Create `AtvNavGraph` navigation graph in `app/src/main/kotlin/com/example/atv/ui/navigation/AtvNavGraph.kt`
- [ ] T024 [US1] Create `PlaybackScreen` with full-screen PlayerView in `app/src/main/kotlin/com/example/atv/ui/screens/playback/PlaybackScreen.kt`
- [ ] T025 [US1] Create `MainActivity` as Compose host in `app/src/main/kotlin/com/example/atv/MainActivity.kt`

**Checkpoint**: User Story 1 complete - app launches and plays first channel in full-screen mode

---

## Phase 4: User Story 4 - Load M3U8 Playlist File (Priority: P1) 🎯 MVP

**Goal**: Load IPTV playlist from local M3U8 file on first launch

**Spec Reference**: [spec.md](spec.md) (US-004) - FR-009, FR-010, FR-011, FR-012

**Independent Test**: Select M3U8 file from device storage → channels parsed and stored → playback begins

**References**:
- [data-model.md#m3u8-format-specification](data-model.md#m3u8-format-specification)
- [research.md#m3u8-parsing](research.md#m3u8-parsing)

### Implementation for User Story 4

- [ ] T026 [US4] Create `M3U8Parser` in `app/src/main/kotlin/com/example/atv/data/parser/M3U8Parser.kt`
- [ ] T027 [US4] Create `ParseM3U8UseCase` in `app/src/main/kotlin/com/example/atv/domain/usecase/ParseM3U8UseCase.kt`
- [ ] T028 [US4] Create `LoadPlaylistUseCase` in `app/src/main/kotlin/com/example/atv/domain/usecase/LoadPlaylistUseCase.kt`
- [ ] T029 [US4] Create `SetupViewModel` in `app/src/main/kotlin/com/example/atv/ui/screens/setup/SetupViewModel.kt`
- [ ] T030 [US4] Create `SetupScreen` with file browser button in `app/src/main/kotlin/com/example/atv/ui/screens/setup/SetupScreen.kt`
- [ ] T031 [US4] Integrate Storage Access Framework for file picker in `SetupScreen.kt`
- [ ] T032 [US4] Implement playlist auto-refresh on app restart in `LoadPlaylistUseCase.kt`

**Checkpoint**: User Story 4 complete - users can load M3U8 files and channels persist across restarts

---

## Phase 5: User Story 2 - Switch Channels with Remote (Priority: P1) 🎯 MVP

**Goal**: Switch between channels using UP/DOWN buttons on TV remote

**Spec Reference**: [spec.md](spec.md) (US-002) - FR-005, FR-006, FR-021, FR-022

**Independent Test**: Press UP/DOWN during playback → channel switches within 3 seconds with info overlay

**References**:
- [research.md#android-tv-remote-handling](research.md#android-tv-remote-handling)
- [research.md#focus-management](research.md#focus-management)

### Implementation for User Story 2

- [ ] T033 [US2] Create D-pad key handler Modifier extension in `app/src/main/kotlin/com/example/atv/ui/util/KeyEventExtensions.kt`
- [ ] T034 [US2] Create `SwitchChannelUseCase` in `app/src/main/kotlin/com/example/atv/domain/usecase/SwitchChannelUseCase.kt`
- [ ] T035 [US2] Add channel switching logic to `PlaybackViewModel` (UP/DOWN handling)
- [ ] T036 [US2] Create `ChannelInfoOverlay` composable in `app/src/main/kotlin/com/example/atv/ui/components/ChannelInfoOverlay.kt`
- [ ] T037 [US2] Integrate D-pad handler into `PlaybackScreen` for UP/DOWN events
- [ ] T038 [US2] Add auto-hide timer (3 seconds) for `ChannelInfoOverlay`

**Checkpoint**: User Story 2 complete - UP/DOWN switching works with channel info display

---

## Phase 6: User Story 6 - Browse Channel List (Priority: P2)

**Goal**: View full channel list overlay during playback and select channels

**Spec Reference**: [spec.md](spec.md) (US-006) - FR-008, FR-016, FR-017, FR-018, FR-019, FR-020

**Independent Test**: Press LEFT → channel list appears → navigate and select → channel switches

**References**:
- [data-model.md#state-models](data-model.md#state-models) (PlaybackUiState)
- [research.md#compose-for-tv](research.md#compose-for-tv)

### Implementation for User Story 6

- [ ] T039 [US6] Create `ChannelListOverlay` composable in `app/src/main/kotlin/com/example/atv/ui/components/ChannelListOverlay.kt`
- [ ] T040 [US6] Add channel list state to `PlaybackViewModel` (visibility, highlighted channel)
- [ ] T041 [US6] Integrate LEFT button handler in `PlaybackScreen` to show channel list
- [ ] T042 [US6] Implement smooth scrolling for large lists (LazyColumn with TV focus)
- [ ] T043 [US6] Add "now playing" highlight indicator in channel list
- [ ] T044 [US6] Add auto-hide timer (10 seconds) for `ChannelListOverlay`

**Checkpoint**: User Story 6 complete - channel list browsing works with smooth scrolling

---

## Phase 7: User Story 3 - Jump to Channel by Number (Priority: P2)

**Goal**: Jump to specific channel using on-screen number pad

**Spec Reference**: [spec.md](spec.md) (US-003) - FR-007

**Independent Test**: Press OK → number pad appears → enter digits → confirm → channel switches

### Implementation for User Story 3

- [ ] T045 [US3] Create `NumberPadOverlay` composable in `app/src/main/kotlin/com/example/atv/ui/components/NumberPadOverlay.kt`
- [ ] T046 [US3] Add number pad state to `PlaybackViewModel` (visibility, entered digits)
- [ ] T047 [US3] Integrate OK button handler in `PlaybackScreen` to show number pad
- [ ] T048 [US3] Implement digit navigation and selection with D-pad
- [ ] T049 [US3] Add validation for channel number range
- [ ] T050 [US3] Add error message for invalid channel numbers
- [ ] T051 [US3] Add auto-hide timer (10 seconds) for `NumberPadOverlay`

**Checkpoint**: User Story 3 complete - direct channel access via number pad works

---

## Phase 8: User Story 7 - Access Settings Menu (Priority: P2)

**Goal**: Access settings menu to manage playlists and preferences

**Spec Reference**: [spec.md](spec.md) (US-007) - FR-024, FR-025, FR-026

**Independent Test**: Press MENU → settings overlay appears → navigate options → manage playlist

### Implementation for User Story 7

- [ ] T052 [US7] Create `SettingsMenu` composable in `app/src/main/kotlin/com/example/atv/ui/components/SettingsMenu.kt`
- [ ] T053 [US7] Create `SettingsViewModel` in `app/src/main/kotlin/com/example/atv/ui/screens/settings/SettingsViewModel.kt`
- [ ] T054 [US7] Create `SettingsScreen` in `app/src/main/kotlin/com/example/atv/ui/screens/settings/SettingsScreen.kt`
- [ ] T055 [US7] Add MENU button / long-press BACK handler in `PlaybackScreen`
- [ ] T056 [US7] Implement playlist management options (load new, clear)
- [ ] T057 [US7] Create `PreferencesRepository` interface in `app/src/main/kotlin/com/example/atv/domain/repository/PreferencesRepository.kt`
- [ ] T058 [US7] Create `PreferencesRepositoryImpl` in `app/src/main/kotlin/com/example/atv/data/repository/PreferencesRepositoryImpl.kt`
- [ ] T059 [US7] Add auto-hide timer (30 seconds) for settings menu

**Checkpoint**: User Story 7 complete - settings access and playlist management work

---

## Phase 9: User Story 5 - Manually Add/Edit Channels (Priority: P3)

**Goal**: Add, edit, or delete individual channel entries

**Spec Reference**: [spec.md](spec.md) (US-005) - FR-013, FR-014, FR-015

**Independent Test**: Open channel management → add new channel → verify it plays correctly

**References**:
- [data-model.md#domain-entities](data-model.md#domain-entities) (Channel)

### Implementation for User Story 5

- [ ] T060 [US5] Create `ChannelManagementViewModel` in `app/src/main/kotlin/com/example/atv/ui/screens/channelmanagement/ChannelManagementViewModel.kt`
- [ ] T061 [US5] Create `ChannelManagementScreen` in `app/src/main/kotlin/com/example/atv/ui/screens/channelmanagement/ChannelManagementScreen.kt`
- [ ] T062 [US5] Create Add Channel form with name and URL fields
- [ ] T063 [US5] Implement Edit Channel functionality with pre-filled form
- [ ] T064 [US5] Implement Delete Channel with confirmation dialog
- [ ] T065 [US5] Add navigation from Settings to Channel Management

**Checkpoint**: User Story 5 complete - full CRUD operations on channels work

---

## Phase 10: Polish & Cross-Cutting Concerns

**Purpose**: Error handling, loading states, session persistence, performance validation

**Reference**: [spec.md](spec.md) (Edge Cases, Success Criteria)

- [ ] T066 Create `ErrorOverlay` with Retry/Next buttons in `app/src/main/kotlin/com/example/atv/ui/components/ErrorOverlay.kt`
- [ ] T067 Add error handling to `PlaybackViewModel` for stream failures
- [ ] T068 Create loading/buffering indicator in `PlaybackScreen`
- [ ] T069 Implement last-watched channel persistence (save on switch, restore on launch)
- [ ] T070 Add network connectivity monitoring and "No Connection" message
- [ ] T071 Validate performance: test with 1000+ channel playlist
- [ ] T072 Validate performance: measure channel switch time (<3s target)
- [ ] T073 Validate performance: measure app launch to playback time (<5s target)
- [ ] T074 Run quickstart.md validation checklist
- [ ] T075 Code cleanup and documentation

---

## Dependencies & Execution Order

### Phase Dependencies

```
Phase 1 (Setup)           → No dependencies - start immediately
        ↓
Phase 2 (Foundational)    → Depends on Phase 1 - BLOCKS all user stories
        ↓
Phase 3-9 (User Stories)  → All depend on Phase 2 completion
        ↓
Phase 10 (Polish)         → Depends on desired user stories being complete
```

### User Story Dependencies

| User Story | Priority | Can Start After | Notes |
|------------|----------|-----------------|-------|
| US1 - Watch Channel | P1 | Phase 2 | Core playback - no story dependencies |
| US4 - Load Playlist | P1 | Phase 2 | File loading - can parallel with US1 |
| US2 - Switch Channels | P1 | US1 complete | Needs playback infrastructure |
| US6 - Channel List | P2 | US2 complete | Builds on channel switching |
| US3 - Number Pad | P2 | US2 complete | Alternative navigation |
| US7 - Settings | P2 | US4 complete | Needs playlist management |
| US5 - Edit Channels | P3 | US4, US7 complete | Needs channel list and settings |

### Within Each User Story

1. Models and use cases first
2. ViewModel next
3. UI composables last
4. Integration and testing after UI

### Parallel Opportunities

**Phase 1 (All parallel)**:
```
T001 (project setup)
├── T002 [P] (version catalog)
├── T003 [P] (AtvApplication)
└── T004 [P] (AndroidManifest)
```

**Phase 2 (Domain models parallel, then DB)**:
```
T005-T008 [P] (domain models - all parallel)
        ↓
T009-T012 (database layer - sequential)
T013-T016 [P] (DI modules + theme - all parallel)
```

**User Stories (Can be parallelized across developers)**:
```
After Phase 2:
├── Developer A: US1 → US2 → US6
├── Developer B: US4 → US7 → US5
└── Developer C: US3 (after US2 infrastructure exists)
```

---

## Parallel Example: Foundation Setup

```bash
# After T001 completes, launch all parallel tasks:
T002: "Configure version catalog in gradle/libs.versions.toml"
T003: "Create AtvApplication with Hilt in AtvApplication.kt"
T004: "Configure AndroidManifest.xml with Leanback launcher"
```

## Parallel Example: Domain Models

```bash
# All domain models can be created in parallel:
T005: "Create Channel model in domain/model/Channel.kt"
T006: "Create Playlist model in domain/model/Playlist.kt"
T007: "Create PlaybackState model in domain/model/PlaybackState.kt"
T008: "Create UserPreferences model in domain/model/UserPreferences.kt"
```

---

## Implementation Strategy

### MVP First (User Stories 1, 4, 2)

1. ✅ Complete Phase 1: Setup
2. ✅ Complete Phase 2: Foundational (CRITICAL - blocks all stories)
3. ✅ Complete Phase 3: US1 - Watch Channel
4. ✅ Complete Phase 4: US4 - Load Playlist
5. ✅ Complete Phase 5: US2 - Switch Channels
6. **STOP and VALIDATE**: Test core experience
   - Can load M3U8 file?
   - Does first channel auto-play?
   - Does UP/DOWN switch channels?
   - Does channel info overlay appear?
7. Deploy/demo if ready - **This is the MVP!**

### Incremental Delivery

| Increment | User Stories | Value Delivered |
|-----------|--------------|-----------------|
| MVP | US1, US4, US2 | Basic IPTV viewing with remote control |
| v1.1 | + US6 | Visual channel browsing |
| v1.2 | + US3, US7 | Quick channel access + settings |
| v1.3 | + US5 | Channel customization |
| v2.0 | + Polish | Production-ready with error handling |

### Parallel Team Strategy

With multiple developers:

1. **Together**: Complete Setup + Foundational (Phase 1-2)
2. **After Foundation**:
   - Developer A: US1 (playback) → US2 (switching) → US6 (list)
   - Developer B: US4 (playlist) → US7 (settings) → US5 (edit)
   - Developer C: US3 (number pad) after US2 basics exist
3. **Together**: Phase 10 (Polish)

---

## Summary

| Metric | Value |
|--------|-------|
| **Total Tasks** | 75 |
| **Setup Phase** | 4 tasks |
| **Foundational Phase** | 12 tasks |
| **US1 - Watch Channel** | 9 tasks |
| **US4 - Load Playlist** | 7 tasks |
| **US2 - Switch Channels** | 6 tasks |
| **US6 - Channel List** | 6 tasks |
| **US3 - Number Pad** | 7 tasks |
| **US7 - Settings** | 8 tasks |
| **US5 - Edit Channels** | 6 tasks |
| **Polish Phase** | 10 tasks |
| **Parallel Tasks (marked [P])** | 14 tasks |
| **MVP Scope** | US1 + US4 + US2 (38 tasks) |

---

## Notes

- [P] tasks = different files, no dependencies on pending tasks
- [USx] label maps task to specific user story for traceability
- Each user story is independently completable and testable
- Commit after each task or logical group
- Stop at any checkpoint to validate story independently
- Cross-reference [plan.md](plan.md) for detailed implementation phases
- Cross-reference [research.md](research.md) for code patterns
- Cross-reference [data-model.md](data-model.md) for entity definitions
