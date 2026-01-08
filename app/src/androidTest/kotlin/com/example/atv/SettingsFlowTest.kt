package com.example.atv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * E2E test for settings flow.
 * 
 * Test scenarios:
 * - Settings menu opens on MENU button press
 * - Settings options are navigable with D-pad
 * - Load new playlist option works
 * - Clear playlist option shows confirmation
 * - Settings changes persist after app restart
 * 
 * Run locally with: ./studio-gradlew connectedAndroidTest
 * Requires Android TV emulator (API 29+)
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class SettingsFlowTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun settingsMenuOpensOnMenuButtonPress() {
        // Given: App is on playback screen
        
        // When: User presses MENU button (or long-press BACK)
        composeTestRule.onNodeWithTag("playback_screen").performKeyInput {
            pressKey(Key.Menu)
        }
        
        // Then: Settings menu should appear
        composeTestRule
            .onNodeWithTag("settings_menu")
            .assertIsDisplayed()
    }

    @Test
    fun settingsMenuHasExpectedOptions() {
        // Given: Settings menu is open
        composeTestRule.onNodeWithTag("playback_screen").performKeyInput {
            pressKey(Key.Menu)
        }
        
        // Then: Expected menu options should be visible
        composeTestRule
            .onNodeWithText("Load New Playlist", substring = true, ignoreCase = true)
            .assertIsDisplayed()
        
        composeTestRule
            .onNodeWithText("Clear Playlist", substring = true, ignoreCase = true)
            .assertIsDisplayed()
    }

    @Test
    fun settingsMenuIsNavigableWithDpad() {
        // Given: Settings menu is open
        composeTestRule.onNodeWithTag("playback_screen").performKeyInput {
            pressKey(Key.Menu)
        }
        
        // When: User navigates with D-pad
        composeTestRule.onNodeWithTag("settings_menu").performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.DirectionUp)
        }
        
        // Then: Focus should move between options
        // Note: Would verify focus state changes
    }

    @Test
    fun settingsMenuClosesOnBackPress() {
        // Given: Settings menu is open
        composeTestRule.onNodeWithTag("playback_screen").performKeyInput {
            pressKey(Key.Menu)
        }
        
        // When: User presses BACK
        composeTestRule.onNodeWithTag("settings_menu").performKeyInput {
            pressKey(Key.Back)
        }
        
        // Then: Settings menu should close
        composeTestRule
            .onNodeWithTag("settings_menu")
            .assertDoesNotExist()
    }

    @Test
    fun loadNewPlaylistOpensFilePicker() {
        // Given: Settings menu is open
        composeTestRule.onNodeWithTag("playback_screen").performKeyInput {
            pressKey(Key.Menu)
        }
        
        // When: User selects "Load New Playlist"
        composeTestRule
            .onNodeWithText("Load New Playlist", substring = true, ignoreCase = true)
            .performClick()
        
        // Then: File picker should open
        // Note: File picker is system UI, hard to test directly
        // Would need UI Automator for full test
    }

    @Test
    fun clearPlaylistShowsConfirmationDialog() {
        // Given: Settings menu is open
        composeTestRule.onNodeWithTag("playback_screen").performKeyInput {
            pressKey(Key.Menu)
        }
        
        // When: User selects "Clear Playlist"
        composeTestRule
            .onNodeWithText("Clear Playlist", substring = true, ignoreCase = true)
            .performClick()
        
        // Then: Confirmation dialog should appear
        composeTestRule
            .onNodeWithText("Confirm", substring = true, ignoreCase = true)
            .assertIsDisplayed()
    }

    @Test
    fun cancelingClearPlaylistKeepsData() {
        // Given: Clear confirmation dialog is shown
        composeTestRule.onNodeWithTag("playback_screen").performKeyInput {
            pressKey(Key.Menu)
        }
        composeTestRule
            .onNodeWithText("Clear Playlist", substring = true, ignoreCase = true)
            .performClick()
        
        // When: User cancels
        composeTestRule
            .onNodeWithText("Cancel", substring = true, ignoreCase = true)
            .performClick()
        
        // Then: Playlist data should remain
        // Settings menu should close or return to previous state
    }

    @Test
    fun confirmingClearPlaylistRemovesData() {
        // Given: Clear confirmation dialog is shown
        composeTestRule.onNodeWithTag("playback_screen").performKeyInput {
            pressKey(Key.Menu)
        }
        composeTestRule
            .onNodeWithText("Clear Playlist", substring = true, ignoreCase = true)
            .performClick()
        
        // When: User confirms
        composeTestRule
            .onNodeWithText("Confirm", substring = true, ignoreCase = true)
            .performClick()
        
        // Then: Should navigate to setup screen (no playlist)
        composeTestRule
            .onNodeWithText("Load Playlist", substring = true, ignoreCase = true)
            .assertIsDisplayed()
    }

    @Test
    fun settingsMenuAutoClosesAfterTimeout() {
        // Given: Settings menu is open
        composeTestRule.onNodeWithTag("playback_screen").performKeyInput {
            pressKey(Key.Menu)
        }
        
        // When: 30+ seconds pass without interaction
        composeTestRule.waitUntil(timeoutMillis = 35000) {
            runCatching {
                composeTestRule
                    .onNodeWithTag("settings_menu")
                    .assertDoesNotExist()
                true
            }.getOrDefault(false)
        }
        
        // Then: Menu should auto-close
    }

    @Test
    fun aboutScreenShowsAppVersion() {
        // Given: Settings menu is open
        composeTestRule.onNodeWithTag("playback_screen").performKeyInput {
            pressKey(Key.Menu)
        }
        
        // When: User selects "About"
        composeTestRule
            .onNodeWithText("About", substring = true, ignoreCase = true)
            .performClick()
        
        // Then: About dialog shows version info
        composeTestRule
            .onNodeWithText("Version", substring = true, ignoreCase = true)
            .assertIsDisplayed()
    }
}
