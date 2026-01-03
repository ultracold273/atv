# Feature Specification: ATV - Android TV IPTV Player

**Feature Branch**: `001-iptv-player`  
**Created**: 2025-12-26  
**Status**: Draft  
**Input**: User description: "Develop ATV, an android TV app that plays stream IPTV on a TV. It should have simple operation logic using the remote controllers, like switch channels, mute/unmute, volume up/down, and even jump to a specific channel directly (even the controller does not have digital buttons). The TV source shall be stored in a file that can be uploaded directly or by manually input or changed. Currently we shall at least accept the playlist in the m3u8 format."

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Watch IPTV Channel (Priority: P1)

As a TV viewer, I want to launch the ATV app and immediately start watching an IPTV channel so that I can enjoy live TV content with minimal effort.

**Why this priority**: This is the core value proposition - without video playback, the app has no purpose. Users expect to see content immediately upon launch.

**Independent Test**: Can be fully tested by launching the app with a pre-loaded M3U8 playlist and verifying video plays on the first channel. Delivers the fundamental value of watching IPTV.

**Acceptance Scenarios**:

1. **Given** the app has a valid playlist loaded, **When** I launch the app, **Then** the first channel begins playing within 5 seconds
2. **Given** a channel is playing, **When** I view the screen, **Then** the video fills the entire screen with no UI overlays blocking content
3. **Given** a channel is playing, **When** the stream has audio, **Then** audio plays through the TV speakers at the last-used volume level

---

### User Story 2 - Switch Channels with Remote (Priority: P1)

As a TV viewer, I want to switch between channels using my TV remote's directional buttons so that I can browse available content easily.

**Why this priority**: Channel switching is essential for an IPTV app - users need to navigate between available streams. This directly impacts usability.

**Independent Test**: Can be tested by loading a playlist with multiple channels and using UP/DOWN buttons to navigate. Delivers the ability to access all available content.

**Acceptance Scenarios**:

1. **Given** a channel is playing, **When** I press the DOWN button on the remote, **Then** the app switches to the next channel in the list within 3 seconds
2. **Given** a channel is playing, **When** I press the UP button on the remote, **Then** the app switches to the previous channel in the list within 3 seconds
3. **Given** I am on the last channel, **When** I press DOWN, **Then** the app wraps around to the first channel
4. **Given** I am on the first channel, **When** I press UP, **Then** the app wraps around to the last channel
5. **Given** I am switching channels, **When** the new channel loads, **Then** a brief channel info overlay shows (channel number, name) and auto-hides after 3 seconds

---

### User Story 3 - Jump to Channel by Number (Priority: P2)

As a TV viewer, I want to jump directly to a specific channel by entering its number so that I can quickly access my favorite channels without scrolling through the entire list.

**Why this priority**: Direct channel access significantly improves UX for users with many channels. Since the remote may not have number keys, an on-screen number pad is required.

**Independent Test**: Can be tested by invoking the number input UI and entering a channel number to verify direct navigation. Delivers quick channel access.

**Acceptance Scenarios**:

1. **Given** a channel is playing, **When** I press the SELECT/OK button, **Then** an on-screen number pad appears
2. **Given** the number pad is visible, **When** I navigate and select digits using directional buttons, **Then** the entered number is displayed
3. **Given** I have entered a valid channel number, **When** I confirm the entry (press OK on "Go" button), **Then** the app switches to that channel
4. **Given** I have entered an invalid channel number (out of range), **When** I confirm, **Then** an error message appears and I remain on current channel
5. **Given** the number pad is visible, **When** I press BACK, **Then** the number pad closes without changing channels
6. **Given** the number pad is visible, **When** no input for 10 seconds, **Then** the number pad auto-closes

---

### User Story 4 - Load M3U8 Playlist File (Priority: P1)

As a TV viewer, I want to load my IPTV playlist from a local M3U8 file so that I can watch my subscribed channels.

**Why this priority**: Without a playlist, there are no channels to watch. This is foundational for the app to function.

**Independent Test**: Can be tested by selecting an M3U8 file from device storage and verifying channels appear in the channel list. Delivers the ability to configure content sources.

**Acceptance Scenarios**:

1. **Given** the app has no playlist, **When** I launch the app, **Then** the app shows a setup screen with a "Browse Files" button
2. **Given** I am on the setup screen, **When** I select "Browse Files", **Then** I can browse device storage to select an M3U8 file
3. **Given** I select a valid M3U8 file, **When** the file is parsed successfully, **Then** channels become available and playback begins
4. **Given** I select an invalid or corrupted file, **When** parsing fails, **Then** an appropriate error message is shown and I remain on setup screen
5. **Given** the app has a playlist loaded, **When** I restart the app, **Then** the playlist is automatically refreshed from the original file

---

### User Story 5 - Manually Add/Edit Channels (Priority: P3)

As a TV viewer, I want to manually add or edit individual channel entries so that I can customize my channel list or add channels not in my playlist file.

**Why this priority**: Manual editing provides flexibility for power users but is not essential for basic functionality.

**Independent Test**: Can be tested by adding a new channel with name and URL, then verifying it appears in the channel list and plays correctly.

**Acceptance Scenarios**:

1. **Given** I am in the channel management screen, **When** I select "Add Channel", **Then** a form appears for channel name and stream URL
2. **Given** I am adding a channel, **When** I enter valid name and URL and confirm, **Then** the channel is added to the end of the list
3. **Given** I am viewing the channel list, **When** I select a channel and choose "Edit", **Then** I can modify the channel name and URL
4. **Given** I am editing a channel, **When** I save changes, **Then** the updated information is persisted
5. **Given** I am viewing the channel list, **When** I select a channel and choose "Delete", **Then** I am asked to confirm, and upon confirmation the channel is removed

---

### User Story 6 - Browse Channel List (Priority: P2)

As a TV viewer, I want to view a full channel list overlay while watching so that I can browse all available channels and quickly select one to watch.

**Why this priority**: Browsing channels visually is essential for discovery and navigation, especially when users don't know the channel number. This complements UP/DOWN switching for a complete navigation experience.

**Independent Test**: Can be tested by opening the channel list during playback and selecting a channel. Delivers visual channel browsing and selection.

**Acceptance Scenarios**:

1. **Given** a channel is playing, **When** I press the LEFT button or dedicated Guide button, **Then** a channel list overlay appears on the side of the screen
2. **Given** the channel list is visible, **When** I use UP/DOWN buttons, **Then** I can scroll through all available channels
3. **Given** a channel is highlighted in the list, **When** I view it, **Then** I can see the channel number, name, and optionally logo/category
4. **Given** a channel is highlighted in the list, **When** I press SELECT/OK, **Then** the app switches to that channel and the list closes
5. **Given** the channel list is visible, **When** I press BACK or LEFT again, **Then** the channel list closes without changing channels
6. **Given** the channel list is visible, **When** the current playing channel is in view, **Then** it is visually highlighted/marked as "now playing"
7. **Given** the channel list is visible, **When** no input for 10 seconds, **Then** the list auto-hides
8. **Given** I have many channels (100+), **When** I scroll the list, **Then** scrolling is smooth with no lag or frame drops

---

### User Story 7 - Access Settings Menu (Priority: P2)

As a TV viewer, I want to access a settings menu to manage playlists and preferences using my remote so that I can configure the app without needing a keyboard.

**Why this priority**: Settings access is needed for playlist management and app configuration, making it essential for ongoing use.

**Independent Test**: Can be tested by pressing MENU/BACK from the main screen and navigating through settings options using directional buttons.

**Acceptance Scenarios**:

1. **Given** a channel is playing, **When** I press the MENU button (or long-press BACK), **Then** a settings menu appears as an overlay
2. **Given** the settings menu is open, **When** I use UP/DOWN buttons, **Then** I can navigate between menu options
3. **Given** a menu option is highlighted, **When** I press SELECT/OK, **Then** I enter that settings section
4. **Given** I am in any settings screen, **When** I press BACK, **Then** I return to the previous screen or close settings
5. **Given** settings menu is open, **When** no input for 30 seconds, **Then** the menu auto-closes and returns to playback

---

### User Story 8 - Multi-lingual Support (Priority: P2)

As a Chinese-speaking TV viewer, I want the app interface to display in Chinese so that I can easily understand and navigate all features.

**Why this priority**: Multi-lingual support expands the user base significantly. English and Chinese cover a large portion of potential users. This enhances usability for non-English speakers.

**Independent Test**: Can be tested by changing device language to Chinese and verifying all UI elements display correctly translated text. Delivers accessibility for Chinese-speaking users.

**Acceptance Scenarios**:

1. **Given** my TV is set to Chinese language, **When** I launch the app, **Then** all UI text (menus, buttons, labels) is displayed in Chinese
2. **Given** my TV is set to English language, **When** I launch the app, **Then** all UI text is displayed in English
3. **Given** my TV is set to French (unsupported language), **When** I launch the app, **Then** all UI text falls back to English
4. **Given** I am viewing any screen (playback, settings, setup), **When** I view the UI, **Then** all text is consistent in the same language
5. **Given** an error occurs, **When** an error message is displayed, **Then** the error text is in the device's language (or English fallback)
6. **Given** I change my device language setting, **When** I restart the app, **Then** the app UI reflects the new language

---

### Edge Cases

- What happens when the stream URL becomes unavailable during playback? → Show error overlay with "Retry" and "Next Channel" buttons; stay on current channel until user decides
- What happens when network connectivity is lost? → Show "No Connection" message with subtle reconnecting indicator; auto-retry in background and resume playback automatically when connection is restored
- What happens when M3U8 file contains no valid channels? → Show "No channels found" message and prompt to load different playlist
- How does the app behave with very large playlists (1000+ channels)? → Channels should load progressively; scrolling/navigation remains responsive
- What happens when the app is backgrounded during playback? → Playback pauses; resumes when app returns to foreground
- What happens if a channel has no audio track? → Play video silently without error
- What happens if user tries to access storage and permission is denied? → Show clear message explaining permission requirement with option to grant

## Requirements *(mandatory)*

### Functional Requirements

**Playback**
- **FR-001**: App MUST play IPTV streams from M3U8 playlist URLs
- **FR-002**: App MUST support common streaming protocols (HLS, MPEG-DASH, RTSP)
- **FR-003**: App MUST auto-play the first channel (or last-watched channel) on launch
- **FR-004**: App MUST maintain playback state (volume, last channel) between sessions
- **FR-005**: App MUST display a loading spinner overlay on top of video (with last frame visible) during buffering, auto-hiding when playback resumes

**Remote Control Navigation**
- **FR-006**: App MUST respond to Android TV remote D-pad inputs (UP, DOWN, LEFT, RIGHT, SELECT/OK, BACK)
- **FR-007**: App MUST support channel switching via UP/DOWN buttons during playback
- **FR-008**: App MUST provide on-screen number pad for direct channel selection (activated by SELECT/OK button)
- **FR-009**: App MUST support D-pad navigation (UP/DOWN/LEFT/RIGHT) on the number pad to move focus between digit buttons
- **FR-010**: App MUST provide a backspace button on the number pad to delete the last entered digit
- **FR-011**: App MUST limit channel number input to 3 digits maximum (supporting up to 999 channels)
- **FR-012**: App MUST provide channel list overlay accessible via LEFT button or Guide button during playback

**Playlist Management**
- **FR-013**: App MUST parse and load M3U8/M3U playlist format
- **FR-014**: App MUST support loading playlist from local file (device storage) only
- **FR-015**: App MUST persist loaded playlist locally
- **FR-016**: App MUST auto-refresh playlist from original file on app restart, resuming on last-watched channel if it still exists (otherwise first channel)
- **FR-017**: App MUST allow manual addition of individual channels (name + stream URL)
- **FR-018**: App MUST allow editing of existing channel entries
- **FR-019**: App MUST allow deletion of channels from playlist

**Channel List UI**
- **FR-020**: App MUST display scrollable channel list as side overlay during playback
- **FR-021**: App MUST show channel number, name, and category (if available from M3U8 group-title) in channel list - display only, no filtering
- **FR-022**: App MUST highlight the currently playing channel in the list
- **FR-023**: App MUST support smooth scrolling through large channel lists (1000+ channels)
- **FR-024**: App MUST allow channel selection from list via SELECT/OK button
- **FR-025**: App MUST support D-pad UP/DOWN navigation to scroll through the channel list with focus changing accordingly
- **FR-026**: App MUST set default focus to the currently playing channel when the channel list overlay is opened

**User Interface**
- **FR-027**: App MUST display channel info overlay (number, name) when switching channels
- **FR-028**: App MUST auto-hide overlays after brief display (3 seconds for info)
- **FR-029**: App MUST be fully navigable using TV remote (no touch/mouse required)

**Settings**
- **FR-030**: App MUST provide settings menu accessible via MENU button or long-press BACK
- **FR-031**: App MUST allow playlist management from settings
- **FR-032**: App MUST allow clearing/resetting playlist data

**Internationalization (i18n)**
- **FR-033**: App MUST support English and Chinese (Simplified) languages
- **FR-034**: App MUST automatically detect and use the device's system language preference
- **FR-035**: App MUST display all user-facing text (labels, buttons, messages, errors) in the selected language
- **FR-036**: App MUST fall back to English when system language is not English or Chinese
- **FR-037**: App MUST use Android resource-based localization (`strings.xml`) for all UI strings

**Observability**
- **FR-038**: App MUST log errors and critical events in production builds
- **FR-039**: App MUST enable verbose/debug logging only in debug builds
- **FR-040**: App MUST NOT log sensitive user data (file paths with usernames, stream URLs with credentials)

### Key Entities

- **Channel**: Represents a single IPTV stream - has name, number (position in list), stream URL, optional logo URL, optional group/category
- **Playlist**: The single active playlist - has source (file path or URL), name, list of channels, last updated timestamp. Loading a new playlist replaces the existing one.
- **PlaybackState**: Current viewing state - current channel, volume level, mute status, playback position (if applicable)
- **UserPreferences**: Persisted settings - last watched channel, preferred volume, auto-play on launch setting

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Users can start watching a channel within 5 seconds of launching the app (with pre-loaded playlist)
- **SC-002**: Users can switch channels in under 3 seconds using remote navigation
- **SC-003**: Users can load an M3U8 playlist (up to 500 channels) within 10 seconds
- **SC-004**: App responds to remote button presses within 200ms
- **SC-005**: 95% of users can navigate core functions (play, switch channel, adjust volume) without instructions
- **SC-006**: App remains responsive (no frame drops, no input lag) with playlists up to 1000 channels
- **SC-007**: Users can complete playlist setup (upload file or enter URL) in under 2 minutes on first use
- **SC-008**: App uses less than 100MB of memory during normal playback
- **SC-009**: 100% of user-facing UI text is localized (no hardcoded strings in code)
- **SC-010**: Chinese-speaking users can complete all tasks without encountering English-only text

## Clarifications

### Session 2025-12-26

- Q: Button mapping for MVP? → A: UP/DOWN for channels, LEFT for channel list, OK for number pad. No in-app volume or mute control.
- Q: Multiple playlists or single playlist? → A: Single playlist only - loading a new playlist replaces the current one
- Q: Support channel group filtering from M3U8 group-title? → A: Display category label in channel list but no filtering/grouping UI in MVP
- Q: What happens when stream fails during playback? → A: Show error overlay and stay on channel - user decides to retry or switch via overlay buttons
- Q: First launch playlist setup - file or URL? → A: Local file only for MVP; URL input deferred to future release
- Q: Playlist refresh mechanism? → A: Auto-refresh from original file on app restart

### Session 2025-12-29

- Q: Stream buffering visual feedback? → A: Loading spinner overlay on top of video (last frame visible), auto-hides when playback resumes
- Q: Network reconnection behavior? → A: Auto-retry in background with subtle indicator, resume playback automatically when successful
- Q: Maximum channel number input length? → A: 3 digits (supports up to 999 channels)
- Q: App startup without prior playlist? → A: Welcome/setup screen with clear "Browse Files" button and brief instructions
- Q: Playlist file change detection on restart? → A: Silently refresh, resume on last-watched channel if it still exists (otherwise first channel)

### Session 2026-01-02

- Q: Which languages to support? → A: English (default) and Chinese Simplified; more languages can be added later
- Q: In-app language selector? → A: No - follow device system language setting (standard Android behavior)
- Q: Chinese variant (Simplified vs Traditional)? → A: Simplified Chinese (zh) for initial release; covers mainland China audience
- Q: Channel names from M3U8 - translate them? → A: No - channel names come from playlist data and remain as-is; only UI chrome is translated
- Q: Implementation approach? → A: Android resource-based localization using `strings.xml` and `stringResource()` in Compose
- Q: Observability & logging strategy? → A: Minimal logging - errors and critical events only in production; verbose logging in debug builds

## Assumptions

- Target Android TV devices running Android 10 (API 29) or higher
- Users have access to valid M3U8 playlist files (copied to device storage)
- TV remotes follow standard Android TV button conventions (D-pad, OK, Back, Menu)
- Network connectivity is available for stream playback (no offline video caching required)
- Initial release focuses on live streams (no DVR/time-shift functionality)
- Volume control is handled by TV/system; app only provides mute toggle

## Out of Scope (MVP)

The following features are explicitly deferred to future releases:

- **Remote URL playlist loading** - MVP supports local files only; URL input requires on-screen keyboard complexity
- **In-app volume control** - Use system/TV volume; reduces button mapping complexity
- **Channel reordering** - Users cannot change channel order; order follows M3U8 file
- **Favorites/Quick access** - No way to mark or quickly access favorite channels
- **Accessibility (TalkBack)** - Screen reader support deferred
- **Crash recovery** - No automatic resume of last channel after crash
- **URL/Input validation** - Basic file parsing only; robust security validation deferred

## Technical Decisions

The following technical choices have been made for this project:

### Platform & Language
- **Target SDK**: Latest Android SDK (API 35)
- **Minimum SDK**: Android 10 (API 29) for TV compatibility
- **Language**: Kotlin (100% - including Gradle build scripts using Kotlin DSL)

### Official Jetpack Libraries (Preferred)
- **Media Playback**: [Media3 ExoPlayer](https://developer.android.com/media/media3/exoplayer) - official Google media player for streaming
- **UI Framework**: [Compose for TV](https://developer.android.com/training/tv/playback/compose) - modern declarative UI for Android TV
  - `androidx.tv:tv-foundation` - TV-specific foundation components (focus handling, scrolling)
  - `androidx.tv:tv-material` - TV Material Design components (cards, lists, navigation)
- **Navigation**: [Navigation Compose](https://developer.android.com/develop/ui/compose/navigation) - type-safe Compose navigation
- **Data Persistence**: [Room](https://developer.android.com/training/data-storage/room) - for playlist/channel storage
- **Preferences**: [DataStore](https://developer.android.com/topic/libraries/architecture/datastore) - for user preferences
- **Lifecycle**: [Lifecycle Components](https://developer.android.com/topic/libraries/architecture/lifecycle) - for lifecycle-aware components
- **ViewModel**: [ViewModel + Compose](https://developer.android.com/topic/libraries/architecture/viewmodel/viewmodel-compose) - for UI state management

### Infrastructure Recommendations
- **Logging**: [Timber](https://github.com/JakeWharton/timber) - lightweight logging wrapper (de facto Android standard)
- **Dependency Injection**: [Hilt](https://developer.android.com/training/dependency-injection/hilt-android) - official DI solution built on Dagger
- **Networking**: [OkHttp](https://square.github.io/okhttp/) - for playlist URL fetching (Media3 uses this internally)
- **Coroutines**: [Kotlin Coroutines](https://developer.android.com/kotlin/coroutines) + [Flow](https://developer.android.com/kotlin/flow) - for async operations and reactive state
- **Testing**: [JUnit5](https://junit.org/junit5/) + [MockK](https://mockk.io/) + [Compose UI Testing](https://developer.android.com/develop/ui/compose/testing) for UI tests
- **Telemetry** (optional): [Firebase Analytics](https://firebase.google.com/products/analytics) or disable for privacy-focused release

### Build System
- **Gradle**: Kotlin DSL (`build.gradle.kts`)
- **Version Catalog**: `libs.versions.toml` for centralized dependency management
- **Build Features**: Compose enabled with Kotlin compiler plugin
