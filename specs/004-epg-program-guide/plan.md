# EPG Program Guide Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Wire up the EPG feature surfaces (banner now-playing/up-next + side-by-side EPG panel + settings toggle) end-to-end with an `EpgProvider` abstraction backed by a CTC reverse-engineered provider, per [spec.md](spec.md).

**Architecture:** Domain interface (`EpgProvider`) consumed by `PlaybackViewModel`; one provider implementation (`CtcEpgProvider`) ports the relevant slice of `~/Documents/itv-reverse/iptv_client.py` to Kotlin. New `data/epg/` package holds the provider + auth client. `Program` model parses upstream timestamp strings into `java.time.Instant` at the boundary. UI extends existing `ChannelInfoOverlay` and `ChannelListOverlay`; a new `EpgPanel` composable renders the side panel.

**Tech Stack:** Kotlin, Jetpack Compose for TV, Hilt, OkHttp (new explicit dep), java.time (no new dep), JUnit 5 + MockK + Turbine + MockWebServer (new test dep).

**Spec coverage:** All 32 FRs, 8 SCs, 10 edge cases, and 3 user stories from [spec.md](spec.md) map to tasks below. The 004/005 split means `EpgProvider.isConfigured` is hard-wired to `false` in `CtcEpgProvider` for 004; 005 will flip it.

---

## Status legend

The plan is split into 4 phases. Phase 1 (this file) covers domain models and dependencies. Phase 2 covers the CTC provider (auth + parsers + provider). Phase 3 covers UI. Phase 4 wires everything via Hilt and adds final integration tests. **This first commit only contains Phase 1.** Phases 2–4 will be appended incrementally as the work progresses; each appended phase is independently committable.

---

## Phase 1: Domain models & dependencies

### Task 1: Add OkHttp + MockWebServer dependencies

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add version + library entries to `libs.versions.toml`**

In `gradle/libs.versions.toml`, add under `[versions]`:

```toml
# Networking
okhttp = "4.12.0"
```

Add under `[libraries]`:

```toml
# Networking
okhttp = { module = "com.squareup.okhttp3:okhttp", version.ref = "okhttp" }
okhttp-mockwebserver = { module = "com.squareup.okhttp3:mockwebserver", version.ref = "okhttp" }
```

- [ ] **Step 2: Reference the new libs in `app/build.gradle.kts`**

In the `dependencies {` block, add:

```kotlin
implementation(libs.okhttp)
testImplementation(libs.okhttp.mockwebserver)
```

- [ ] **Step 3: Verify the build succeeds**

Run: `./studio-gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "build(004): add okhttp and mockwebserver dependencies"
```

---

### Task 2: Add `Program` domain model

**Files:**
- Create: `app/src/main/kotlin/com/example/atv/domain/model/Program.kt`
- Test: `app/src/test/kotlin/com/example/atv/domain/model/ProgramTest.kt`

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/com/example/atv/domain/model/ProgramTest.kt`:

```kotlin
package com.example.atv.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class ProgramTest {

    private fun sample(
        start: String = "2026-06-07T08:00:00Z",
        end: String = "2026-06-07T09:00:00Z"
    ) = Program(
        code = "p1",
        name = "News",
        start = Instant.parse(start),
        end = Instant.parse(end),
        isLive = true,
        isReplayable = false
    )

    @Test
    fun `airsAt returns true when given instant is within start-end`() {
        assertTrue(sample().airsAt(Instant.parse("2026-06-07T08:30:00Z")))
    }

    @Test
    fun `airsAt is inclusive of start and exclusive of end`() {
        val p = sample()
        assertTrue(p.airsAt(Instant.parse("2026-06-07T08:00:00Z")))
        assertFalse(p.airsAt(Instant.parse("2026-06-07T09:00:00Z")))
    }

    @Test
    fun `airsAt returns false outside window`() {
        val p = sample()
        assertFalse(p.airsAt(Instant.parse("2026-06-07T07:59:59Z")))
        assertFalse(p.airsAt(Instant.parse("2026-06-07T09:30:00Z")))
    }

    @Test
    fun `progress is 0 at start, half at midpoint, 1 at end`() {
        val p = sample(end = "2026-06-07T10:00:00Z")
        assertEquals(0.0f, p.progress(Instant.parse("2026-06-07T08:00:00Z")))
        assertEquals(0.5f, p.progress(Instant.parse("2026-06-07T09:00:00Z")))
        assertEquals(1.0f, p.progress(Instant.parse("2026-06-07T10:00:00Z")))
    }

    @Test
    fun `progress is clamped to 0 and 1`() {
        val p = sample(end = "2026-06-07T10:00:00Z")
        assertEquals(0.0f, p.progress(Instant.parse("2026-06-07T07:00:00Z")))
        assertEquals(1.0f, p.progress(Instant.parse("2026-06-07T11:00:00Z")))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./studio-gradlew test --tests "com.example.atv.domain.model.ProgramTest"`
Expected: FAIL — `Program` class does not exist.

- [ ] **Step 3: Create the Program model**

Create `app/src/main/kotlin/com/example/atv/domain/model/Program.kt`:

```kotlin
package com.example.atv.domain.model

import java.time.Instant

data class Program(
    val code: String,
    val name: String,
    val start: Instant,
    val end: Instant,
    val isLive: Boolean,
    val isReplayable: Boolean
) {
    fun airsAt(now: Instant): Boolean = !now.isBefore(start) && now.isBefore(end)

    fun progress(now: Instant): Float {
        val total = end.toEpochMilli() - start.toEpochMilli()
        if (total <= 0L) return 0f
        val elapsed = now.toEpochMilli() - start.toEpochMilli()
        return (elapsed.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./studio-gradlew test --tests "com.example.atv.domain.model.ProgramTest"`
Expected: PASS, 5 tests.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/kotlin/com/example/atv/domain/model/Program.kt \
        app/src/test/kotlin/com/example/atv/domain/model/ProgramTest.kt
git commit -m "feat(004): add Program domain model with airsAt/progress helpers"
```

---

### Task 3: Add `EpgProvider` interface

**Files:**
- Create: `app/src/main/kotlin/com/example/atv/domain/repository/EpgProvider.kt`

- [ ] **Step 1: Create the interface**

Create `app/src/main/kotlin/com/example/atv/domain/repository/EpgProvider.kt`:

```kotlin
package com.example.atv.domain.repository

import com.example.atv.domain.model.Program
import kotlinx.coroutines.flow.StateFlow

/**
 * Source of program-guide data, abstracted from the operator-specific protocol.
 *
 * `isConfigured` is false until provider-specific credentials are available.
 * UI must hide all EPG surfaces when this is false, regardless of the user
 * "Show program guide" toggle. In spec 004 alone, this is permanently false
 * (no login UI ships); spec 005 will populate credentials and flip it true.
 */
interface EpgProvider {
    val isConfigured: StateFlow<Boolean>

    /**
     * Fetch the program list for a channel on a given date offset.
     *
     * @param channelCode opaque per-provider channel identifier
     * @param dateOffset -1 = yesterday, 0 = today, +1 = tomorrow
     */
    suspend fun fetchPrograms(channelCode: String, dateOffset: Int): Result<List<Program>>
}
```

- [ ] **Step 2: Verify compilation**

Run: `./studio-gradlew compileDebugKotlin`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 3: Commit**

```bash
git add app/src/main/kotlin/com/example/atv/domain/repository/EpgProvider.kt
git commit -m "feat(004): add EpgProvider interface"
```

---

### Task 4: Add `epgEnabled` to UserPreferences and DataStore

**Files:**
- Modify: `app/src/main/kotlin/com/example/atv/domain/model/UserPreferences.kt`
- Modify: `app/src/main/kotlin/com/example/atv/data/local/datastore/UserPreferencesDataStore.kt`
- Modify: `app/src/main/kotlin/com/example/atv/domain/repository/PreferencesRepository.kt`
- Modify: `app/src/main/kotlin/com/example/atv/data/repository/PreferencesRepositoryImpl.kt`

- [ ] **Step 1: Extend `UserPreferences`**

Replace the body of `app/src/main/kotlin/com/example/atv/domain/model/UserPreferences.kt` with:

```kotlin
package com.example.atv.domain.model

/**
 * User settings persisted in DataStore.
 */
data class UserPreferences(
    val lastChannelNumber: Int = 1,
    val playlistFilePath: String? = null,
    val autoPlayOnLaunch: Boolean = true,
    val epgEnabled: Boolean = false
) {
    init {
        require(lastChannelNumber >= 1) { "Last channel number must be >= 1" }
    }

    val hasPlaylist: Boolean get() = playlistFilePath != null
}
```

- [ ] **Step 2: Extend `UserPreferencesDataStore`**

In `app/src/main/kotlin/com/example/atv/data/local/datastore/UserPreferencesDataStore.kt`:

Inside the `Keys` object, add a new key after `AUTO_PLAY_ON_LAUNCH`:

```kotlin
val EPG_ENABLED = booleanPreferencesKey("epg_enabled")
```

Update the `userPreferences` Flow's mapping to include `epgEnabled`:

```kotlin
val userPreferences: Flow<UserPreferences> = context.dataStore.data
    .map { preferences ->
        UserPreferences(
            lastChannelNumber = preferences[Keys.LAST_CHANNEL_NUMBER] ?: 1,
            playlistFilePath = preferences[Keys.PLAYLIST_FILE_PATH],
            autoPlayOnLaunch = preferences[Keys.AUTO_PLAY_ON_LAUNCH] ?: true,
            epgEnabled = preferences[Keys.EPG_ENABLED] ?: false
        )
    }
```

After `setAutoPlayOnLaunch`, add a setter:

```kotlin
suspend fun setEpgEnabled(enabled: Boolean) {
    context.dataStore.edit { preferences ->
        preferences[Keys.EPG_ENABLED] = enabled
    }
}
```

- [ ] **Step 3: Extend the repository interface**

In `app/src/main/kotlin/com/example/atv/domain/repository/PreferencesRepository.kt`, after `setAutoPlayOnLaunch`, add:

```kotlin
/**
 * Update EPG-enabled setting.
 */
suspend fun setEpgEnabled(enabled: Boolean)
```

- [ ] **Step 4: Implement in the repository**

In `app/src/main/kotlin/com/example/atv/data/repository/PreferencesRepositoryImpl.kt`, after the `setAutoPlayOnLaunch` override, add:

```kotlin
override suspend fun setEpgEnabled(enabled: Boolean) {
    dataStore.setEpgEnabled(enabled)
}
```

- [ ] **Step 5: Verify build + existing tests**

Run: `./studio-gradlew compileDebugKotlin test`
Expected: `BUILD SUCCESSFUL`; existing tests pass.

- [ ] **Step 6: Commit**

```bash
git add app/src/main/kotlin/com/example/atv/domain/model/UserPreferences.kt \
        app/src/main/kotlin/com/example/atv/data/local/datastore/UserPreferencesDataStore.kt \
        app/src/main/kotlin/com/example/atv/domain/repository/PreferencesRepository.kt \
        app/src/main/kotlin/com/example/atv/data/repository/PreferencesRepositoryImpl.kt
git commit -m "feat(004): persist epgEnabled preference (default false)"
```

---

### Task 5: Add EPG-related localized strings

**Files:**
- Modify: `app/src/main/res/values/strings.xml`
- Modify: `app/src/main/res/values-zh/strings.xml`

- [ ] **Step 1: Add English strings**

In `app/src/main/res/values/strings.xml`, before `</resources>`, append:

```xml
<!-- EPG Program Guide -->
<string name="epg_setting_title">Show program guide</string>
<string name="epg_setting_subtitle">Display now-playing and program schedules</string>
<string name="epg_date_yesterday">Yesterday</string>
<string name="epg_date_today">Today</string>
<string name="epg_date_tomorrow">Tomorrow</string>
<string name="epg_now_playing_label">Now</string>
<string name="epg_up_next_label">Next</string>
<string name="epg_unavailable_for_channel">EPG not available for this channel</string>
<string name="epg_no_programs">No programs scheduled for this date</string>
<string name="epg_load_error">Unable to load programs</string>
<string name="epg_loading">Loading guide…</string>
```

- [ ] **Step 2: Add Chinese strings**

In `app/src/main/res/values-zh/strings.xml`, before `</resources>`, append:

```xml
<!-- EPG Program Guide -->
<string name="epg_setting_title">显示节目指南</string>
<string name="epg_setting_subtitle">显示正在播放的节目和节目时间表</string>
<string name="epg_date_yesterday">昨天</string>
<string name="epg_date_today">今天</string>
<string name="epg_date_tomorrow">明天</string>
<string name="epg_now_playing_label">正在播放</string>
<string name="epg_up_next_label">即将播放</string>
<string name="epg_unavailable_for_channel">该频道暂无节目信息</string>
<string name="epg_no_programs">该日期暂无节目安排</string>
<string name="epg_load_error">无法加载节目信息</string>
<string name="epg_loading">正在加载节目指南…</string>
```

- [ ] **Step 3: Verify build**

Run: `./studio-gradlew assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: Commit**

```bash
git add app/src/main/res/values/strings.xml app/src/main/res/values-zh/strings.xml
git commit -m "feat(004): add EPG localized strings (en, zh)"
```

---

## Phases 2–4 (deferred — to be appended)

The remaining phases will be authored as separate appends to this file, each adding ~5 tasks. Splitting was necessary because the full plan exceeds a single message budget; each phase is independently committable and consistent with the structure above.

- **Phase 2: CTC provider** — `CtcAuthenticator` (3DES + golden fixtures), `CtcResponseParsers`, `CtcAuthClient` (6-step login over MockWebServer), `CtcEpgProvider` (cache, isConfigured=false in 004, fetch wiring).
- **Phase 3: UI surfaces** — `EpgPanelState`, modify `PlaybackUiState`, debounced focus + cancellation in `PlaybackViewModel`, modify `ChannelInfoOverlay` for bottom block, new `EpgPanel`, modify `ChannelListOverlay` for side-by-side, wire `PlaybackScreen`.
- **Phase 4: Settings + Hilt + integration** — toggle row in `SettingsScreen`, `SettingsViewModel.setEpgEnabled`, `EpgModule` Hilt bindings, end-to-end smoke test, manual verification checklist.

When you reach the end of Phase 1 and want to continue, ask the planner to append Phase 2 to this file.
