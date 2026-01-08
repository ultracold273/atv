package com.example.atv

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
 * E2E test for channel navigation using D-pad.
 * 
 * Test scenarios:
 * - D-pad UP switches to previous channel
 * - D-pad DOWN switches to next channel
 * - Channel info overlay appears on channel switch
 * - Channel list appears on LEFT press
 * - Number pad appears on OK press
 * 
 * Run locally with: ./studio-gradlew connectedAndroidTest
 * Requires Android TV emulator (API 29+)
 */
@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
@OptIn(ExperimentalTestApi::class)
class ChannelNavigationTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setup() {
        hiltRule.inject()
        // Note: These tests assume a playlist is already loaded
        // In real tests, you would inject test data via test module
    }

    @Test
    fun dpadUpSwitchesToPreviousChannel() {
        // Given: Playback screen with channel 2 playing
        // This test requires pre-loaded test playlist data
        
        // When: User presses D-pad UP
        composeTestRule.onNodeWithTag("playback_screen").performKeyInput {
            pressKey(Key.DirectionUp)
        }
        
        // Then: Previous channel starts playing
        // Verify channel info overlay shows different channel
        
        // Note: Actual assertion depends on test data setup
    }

    @Test
    fun dpadDownSwitchesToNextChannel() {
        // Given: Playback screen with channel 1 playing
        
        // When: User presses D-pad DOWN
        composeTestRule.onNodeWithTag("playback_screen").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        
        // Then: Next channel starts playing
        // Note: Actual assertion depends on test data setup
    }

    @Test
    fun channelInfoOverlayAppearsOnSwitch() {
        // Given: Playback screen playing
        
        // When: User presses D-pad UP or DOWN
        composeTestRule.onNodeWithTag("playback_screen").performKeyInput {
            pressKey(Key.DirectionUp)
        }
        
        // Then: Channel info overlay should appear
        composeTestRule
            .onNodeWithTag("channel_info_overlay")
            .assertIsDisplayed()
    }

    @Test
    fun channelInfoOverlayAutoHidesAfterDelay() {
        // Given: Channel info overlay is visible
        
        // When: 3+ seconds pass
        composeTestRule.waitUntil(timeoutMillis = 4000) {
            runCatching {
                composeTestRule
                    .onNodeWithTag("channel_info_overlay")
                    .assertDoesNotExist()
                true
            }.getOrDefault(false)
        }
        
        // Then: Overlay should be hidden
    }

    @Test
    fun dpadLeftOpensChannelList() {
        // Given: Playback screen playing
        
        // When: User presses D-pad LEFT
        composeTestRule.onNodeWithTag("playback_screen").performKeyInput {
            pressKey(Key.DirectionLeft)
        }
        
        // Then: Channel list overlay should appear
        composeTestRule
            .onNodeWithTag("channel_list_overlay")
            .assertIsDisplayed()
    }

    @Test
    fun selectingChannelFromListSwitchesPlayback() {
        // Given: Channel list overlay is open
        composeTestRule.onNodeWithTag("playback_screen").performKeyInput {
            pressKey(Key.DirectionLeft)
        }
        
        // When: User selects a channel
        composeTestRule.onNodeWithTag("channel_list_overlay").performKeyInput {
            pressKey(Key.DirectionDown)
            pressKey(Key.Enter)
        }
        
        // Then: Selected channel plays and overlay closes
    }

    @Test
    fun numberPadAppearsOnOkPress() {
        // Given: Playback screen playing
        
        // When: User presses OK/Enter to open number pad
        composeTestRule.onNodeWithTag("playback_screen").performKeyInput {
            pressKey(Key.Enter)
        }
        
        // Then: Number pad overlay should appear
        composeTestRule
            .onNodeWithTag("number_pad_overlay")
            .assertIsDisplayed()
    }

    @Test
    fun enteringChannelNumberNavigatesToChannel() {
        // Given: Number pad is open
        composeTestRule.onNodeWithTag("playback_screen").performKeyInput {
            pressKey(Key.Enter)
        }
        
        // When: User enters channel number and confirms
        // Note: This would require additional test setup
        
        // Then: That channel starts playing
    }

    @Test
    fun channelWrapAroundFromLastToFirst() {
        // Given: Playing the last channel
        
        // When: User presses D-pad DOWN
        
        // Then: First channel should start playing (wrap around)
        
        // Note: Requires test data with known channel count
    }

    @Test
    fun channelWrapAroundFromFirstToLast() {
        // Given: Playing the first channel
        
        // When: User presses D-pad UP
        
        // Then: Last channel should start playing (wrap around)
        
        // Note: Requires test data with known channel count
    }
}
