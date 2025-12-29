# Tasks: ATV - Android TV IPTV Player

**Input**: Design documents from `/specs/001-iptv-player/`
**Prerequisites**: plan.md ✓, spec.md ✓, research.md ✓, data-model.md ✓, quickstart.md ✓
**Generated**: 2025-12-29

## Format: `[ID] [P?] [Story?] Description`

- **[P]**: Can run in parallel (different files, no dependencies on incomplete tasks)
- **[Story]**: Which user story this task belongs to (US1, US2, etc.) - only in User Story phases

## Path Conventions

Based on plan.md structure:
- **Source**: `app/src/main/kotlin/com/example/atv/`
- **Resources**: `app/src/main/res/`
- **Tests**: `app/src/test/kotlin/com/example/atv/`
- **Config**: Root level (`build.gradle.kts`, `gradle/libs.versions.toml`)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Project initialization and basic structure - required before any feature work

- [x] T001 Create Android TV project structure with Gradle Kotlin DSL in `build.gradle.kts` and `app/build.gradle.kts`
- [x] T002 [P] Configure version catalog with all dependencies in `gradle/libs.versions.toml`
- [x] T003 [P] Set up Hilt dependency injection with `AtvApplication.kt` in `app/src/main/kotlin/com/example/atv/AtvApplication.kt`
- [x] T004 [P] Configure AndroidManifest.xml for TV with leanback launcher in `app/src/main/AndroidManifest.xml`
- [x] T005 Create Compose for TV theme and typography in `app/src/main/kotlin/com/example/atv/ui/theme/Theme.kt`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Core infrastructure that MUST be complete before ANY user story can be implemented

- [x] T006 Create domain models (Channel, Playlist, PlaybackState, UserPreferences) in `app/src/main/kotlin/com/example/atv/domain/model/`
- [x] T007 [P] Set up Room database with AtvDatabase and migrations in `app/src/main/kotlin/com/example/atv/data/local/db/AtvDatabase.kt`
- [x] T008 [P] Create ChannelEntity and ChannelDao for Room in `app/src/main/kotlin/com/example/atv/data/local/db/ChannelDao.kt`
- [x] T009 [P] Set up DataStore for UserPreferences in `app/src/main/kotlin/com/example/atv/data/local/datastore/UserPreferencesDataStore.kt`
- [x] T010 Create M3U8Parser to parse playlist files in `app/src/main/kotlin/com/example/atv/data/parser/M3U8Parser.kt`
- [x] T011 [P] Create ChannelRepository interface in `app/src/main/kotlin/com/example/atv/domain/repository/ChannelRepository.kt`
- [x] T012 Create ChannelRepositoryImpl with Room integration in `app/src/main/kotlin/com/example/atv/data/repository/ChannelRepositoryImpl.kt`
- [x] T013 [P] Create PreferencesRepository interface in `app/src/main/kotlin/com/example/atv/domain/repository/PreferencesRepository.kt`
- [x] T014 Create PreferencesRepositoryImpl with DataStore in `app/src/main/kotlin/com/example/atv/data/repository/PreferencesRepositoryImpl.kt`
- [x] T015 Create Hilt modules (AppModule, DatabaseModule, PlayerModule) in `app/src/main/kotlin/com/example/atv/di/`
- [x] T016 Set up Navigation Compose graph in `app/src/main/kotlin/com/example/atv/ui/navigation/AtvNavGraph.kt`
- [x] T017 Create MainActivity as single-activity Compose host in `app/src/main/kotlin/com/example/atv/MainActivity.kt`
- [x] T018 Create D-pad key handler modifier extension in `app/src/main/kotlin/com/example/atv/ui/util/KeyEventExtensions.kt`

---

## Phase 3: User Story 1 - Watch IPTV Channel (P1)

**Story Goal**: Launch app and immediately start watching an IPTV channel
**Independent Test**: Launch app with pre-loaded playlist, verify video plays on first channel within 5 seconds

- [x] T019 [US1] Create AtvPlayer wrapper with ExoPlayer and state flow in `app/src/main/kotlin/com/example/atv/player/AtvPlayer.kt`
- [x] T020 [US1] Create PlayerState sealed class with Playing/Paused/Buffering/Error states in `app/src/main/kotlin/com/example/atv/player/PlayerState.kt`
- [x] T021 [US1] Create PlaybackViewModel with player and channel state in `app/src/main/kotlin/com/example/atv/ui/screens/playback/PlaybackViewModel.kt`
- [x] T022 [US1] Create PlaybackScreen with full-screen video PlayerView in `app/src/main/kotlin/com/example/atv/ui/screens/playback/PlaybackScreen.kt`
- [x] T023 [US1] Implement auto-play first channel (or last-watched) on launch in PlaybackViewModel
- [x] T024 [US1] Create BufferingOverlay with spinner on video (last frame visible) in `app/src/main/kotlin/com/example/atv/ui/components/BufferingOverlay.kt`

---

## Phase 4: User Story 4 - Load M3U8 Playlist File (P1)

**Story Goal**: Load IPTV playlist from local M3U8 file
**Independent Test**: Select M3U8 file from storage, verify channels appear and playback begins

- [x] T025 [US4] Create SetupScreen with welcome message and "Browse Files" button in `app/src/main/kotlin/com/example/atv/ui/screens/setup/SetupScreen.kt`
- [x] T026 [US4] Create SetupViewModel with file selection state in `app/src/main/kotlin/com/example/atv/ui/screens/setup/SetupViewModel.kt`
- [x] T027 [US4] Integrate Storage Access Framework (SAF) file picker for M3U8 selection in SetupScreen
- [x] T028 [US4] Create LoadPlaylistUseCase to parse file and save to Room in `app/src/main/kotlin/com/example/atv/domain/usecase/LoadPlaylistUseCase.kt`
- [x] T029 [US4] Implement playlist persistence with file path storage in PreferencesRepository
- [x] T030 [US4] Implement auto-refresh playlist from original file on app restart in LoadPlaylistUseCase
- [x] T031 [US4] Handle invalid/corrupted file with error message in SetupScreen

---

## Phase 5: User Story 2 - Switch Channels with Remote (P1)

**Story Goal**: Switch between channels using TV remote's UP/DOWN buttons
**Independent Test**: Load playlist with multiple channels, use UP/DOWN to navigate, verify switching within 3 seconds

- [x] T032 [US2] Create SwitchChannelUseCase for next/previous channel logic in `app/src/main/kotlin/com/example/atv/domain/usecase/SwitchChannelUseCase.kt`
- [x] T033 [US2] Implement UP/DOWN key handling in PlaybackScreen for channel switching
- [x] T034 [US2] Implement wrap-around logic (first↔last channel) in SwitchChannelUseCase
- [x] T035 [US2] Create ChannelInfoOverlay showing channel number and name in `app/src/main/kotlin/com/example/atv/ui/components/ChannelInfoOverlay.kt`
- [x] T036 [US2] Implement 3-second auto-hide timer for ChannelInfoOverlay
- [x] T037 [US2] Save last-watched channel to preferences on each switch

---

## Phase 6: User Story 6 - Browse Channel List (P2)

**Story Goal**: View full channel list overlay while watching to browse and select channels
**Independent Test**: Press LEFT during playback, verify list appears with current channel focused, select a channel

- [x] T038 [US6] Create ChannelListOverlay as side overlay component in `app/src/main/kotlin/com/example/atv/ui/components/ChannelListOverlay.kt`
- [x] T039 [US6] Implement D-pad UP/DOWN navigation with focus changing in ChannelListOverlay
- [x] T040 [US6] Implement auto-focus on currently playing channel when overlay opens
- [x] T041 [US6] Display channel number, name, and category (group-title) in list items
- [x] T042 [US6] Highlight currently playing channel with "now playing" visual indicator
- [x] T043 [US6] Implement channel selection via SELECT/OK button (switch and close)
- [x] T044 [US6] Implement close on BACK or LEFT button press
- [x] T045 [US6] Implement 10-second auto-hide timer for ChannelListOverlay
- [x] T046 [US6] Ensure smooth scrolling performance with 1000+ channels using LazyColumn

---

## Phase 7: User Story 3 - Jump to Channel by Number (P2)

**Story Goal**: Jump directly to channel by entering number on on-screen number pad
**Independent Test**: Press OK, enter channel number, verify navigation to that channel

- [x] T047 [US3] Create NumberPadOverlay with 0-9 digit grid in `app/src/main/kotlin/com/example/atv/ui/components/NumberPadOverlay.kt`
- [x] T048 [US3] Implement D-pad navigation (UP/DOWN/LEFT/RIGHT) between number pad buttons
- [x] T049 [US3] Create backspace button to delete last entered digit
- [x] T050 [US3] Implement 3-digit maximum input limit with visual display
- [x] T051 [US3] Create "Go" button to confirm channel selection
- [x] T052 [US3] Implement channel validation and navigation on confirm
- [x] T053 [US3] Show error message for invalid/out-of-range channel numbers
- [x] T054 [US3] Implement close on BACK button press without changing channel
- [x] T055 [US3] Implement 10-second auto-close timer for NumberPadOverlay

---

## Phase 8: User Story 7 - Access Settings Menu (P2)

**Story Goal**: Access settings menu to manage playlists using remote
**Independent Test**: Press MENU during playback, navigate options, verify playlist management works

- [x] T056 [US7] Create SettingsMenu overlay component in `app/src/main/kotlin/com/example/atv/ui/components/SettingsMenu.kt`
- [x] T057 [US7] Create SettingsScreen with menu options in `app/src/main/kotlin/com/example/atv/ui/screens/settings/SettingsScreen.kt`
- [x] T058 [US7] Create SettingsViewModel for settings state in `app/src/main/kotlin/com/example/atv/ui/screens/settings/SettingsViewModel.kt`
- [x] T059 [US7] Implement MENU button (or long-press BACK) to show settings overlay
- [x] T060 [US7] Implement UP/DOWN navigation between menu options
- [x] T061 [US7] Add "Load New Playlist" option to trigger file picker
- [x] T062 [US7] Add "Clear Playlist" option with confirmation dialog
- [x] T063 [US7] Implement 30-second auto-close timer for settings menu

---

## Phase 9: User Story 5 - Manually Add/Edit Channels (P3)

**Story Goal**: Manually add or edit individual channel entries
**Independent Test**: Add new channel with name/URL, verify it appears in list and plays correctly

- [x] T064 [US5] Create ChannelManagementScreen with channel list in `app/src/main/kotlin/com/example/atv/ui/screens/channelmanagement/ChannelManagementScreen.kt`
- [x] T065 [US5] Create ChannelManagementViewModel in `app/src/main/kotlin/com/example/atv/ui/screens/channelmanagement/ChannelManagementViewModel.kt`
- [x] T066 [US5] Create AddChannelForm with name and URL input fields in `app/src/main/kotlin/com/example/atv/ui/components/AddChannelForm.kt`
- [x] T067 [US5] Implement channel addition to end of list via ChannelRepository
- [x] T068 [US5] Create EditChannelForm for modifying existing channel in `app/src/main/kotlin/com/example/atv/ui/components/EditChannelForm.kt`
- [x] T069 [US5] Implement channel edit with persistence via ChannelRepository
- [x] T070 [US5] Implement channel deletion with confirmation dialog
- [x] T071 [US5] Add navigation from Settings to Channel Management screen

---

## Phase 10: Polish & Cross-Cutting Concerns

**Purpose**: Error handling, edge cases, and quality improvements

- [x] T072 Create ErrorOverlay with "Retry" and "Next Channel" buttons in `app/src/main/kotlin/com/example/atv/ui/components/ErrorOverlay.kt`
- [x] T073 Implement stream failure detection and error overlay display in PlaybackViewModel
- [ ] T074 Implement network connectivity monitoring in AtvPlayer
- [ ] T075 Implement auto-retry with subtle reconnecting indicator on network restore
- [ ] T076 Handle "No channels found" case in SetupScreen with prompt to load different playlist
- [ ] T077 Implement playback pause on app background, resume on foreground
- [ ] T078 Handle storage permission denial with clear message and grant option
- [ ] T079 Validate smooth scrolling performance with 1000+ channel playlist (no frame drops)
- [ ] T080 Validate channel switch timing (<3 seconds) with performance tests
- [ ] T081 Validate app launch to playback timing (<5 seconds) with performance tests
- [ ] T082 Validate memory usage (<100MB) during normal playback

---

## Dependencies Summary

```
Phase 1 (Setup): T001-T005 - No dependencies
Phase 2 (Foundation): T006-T018 - Depends on Phase 1
Phase 3 (US1 - Watch): T019-T024 - Depends on Phase 2
Phase 4 (US4 - Load): T025-T031 - Depends on Phase 2
Phase 5 (US2 - Switch): T032-T037 - Depends on T019-T024 (playback working)
Phase 6 (US6 - Browse): T038-T046 - Depends on T019-T024 (playback working)
Phase 7 (US3 - Jump): T047-T055 - Depends on T019-T024 (playback working)
Phase 8 (US7 - Settings): T056-T063 - Depends on T025-T031 (playlist loading working)
Phase 9 (US5 - Edit): T064-T071 - Depends on T056-T063 (settings working)
Phase 10 (Polish): T072-T082 - Depends on all user stories
```

## Parallel Execution Opportunities

**Within Phase 1:**
- T002, T003, T004 can run in parallel after T001

**Within Phase 2:**
- T007, T008, T009 can run in parallel after T006
- T011, T013 can run in parallel

**Within Phase 3-4:**
- US1 (T019-T024) and US4 (T025-T031) can run in parallel

**Within Phase 5-7:**
- US2 (T032-T037), US6 (T038-T046), US3 (T047-T055) can run in parallel after US1 completes

---

## Implementation Strategy

**MVP Scope (Recommended)**: Complete Phases 1-5 (US1 + US4 + US2)
- Delivers: Watch channels, load playlist, switch channels
- Total Tasks: 37 tasks (T001-T037)

**Full Feature Scope**: Complete all phases
- Total Tasks: 82 tasks

**Suggested Increments:**
1. **Increment 1** (Core): Phases 1-4 → Can watch IPTV with loaded playlist
2. **Increment 2** (Navigation): Phase 5 → Can switch channels with remote
3. **Increment 3** (Discovery): Phases 6-7 → Can browse list and jump to channel
4. **Increment 4** (Management): Phases 8-9 → Can manage playlists and channels
5. **Increment 5** (Quality): Phase 10 → Production-ready with error handling
