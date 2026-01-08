package com.example.atv

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * E2E test for playlist loading flow.
 * 
 * Test scenarios:
 * - App starts on setup screen when no playlist loaded
 * - Loading a valid playlist populates channel list
 * - Invalid playlist shows error message
 * 
 * Run locally with: ./studio-gradlew connectedAndroidTest
 * Requires Android TV emulator (API 29+)
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class PlaylistLoadingTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        hiltRule.inject()
    }

    @Test
    fun appStartsOnSetupScreenWhenNoPlaylistLoaded() {
        // Given: Fresh app install with no saved playlist
        
        // Then: Setup screen should be displayed
        composeTestRule
            .onNodeWithText("Load Playlist", substring = true, ignoreCase = true)
            .assertIsDisplayed()
    }

    @Test
    fun showsWelcomeMessageOnSetupScreen() {
        // Given: App on setup screen
        
        // Then: Welcome message should be visible
        composeTestRule
            .onNodeWithText("Welcome", substring = true, ignoreCase = true)
            .assertIsDisplayed()
    }

    @Test
    fun browseFilesButtonIsVisible() {
        // Given: App on setup screen
        
        // Then: Browse files button should be present
        composeTestRule
            .onNodeWithText("Browse", substring = true, ignoreCase = true)
            .assertIsDisplayed()
    }

    @Test
    fun loadPlaylistShowsChannelsAfterValidFile() {
        // Note: This test requires a mock file picker response
        // In a real E2E test, you would inject a test playlist file
        // or use a fake file provider
        
        // Given: User taps browse files
        // When: Valid M3U8 file is selected
        // Then: Channels appear and playback screen is shown
        
        // This is a placeholder - actual implementation requires
        // either UI Automator for file picker or a mock content provider
    }

    @Test
    fun showsErrorForInvalidPlaylist() {
        // Note: This test requires ability to inject invalid file
        
        // Given: User selects a file
        // When: File is not valid M3U8 format
        // Then: Error message is displayed
        
        // Placeholder for actual implementation
    }
}
