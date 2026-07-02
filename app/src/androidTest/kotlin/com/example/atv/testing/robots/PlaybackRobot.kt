package com.example.atv.testing.robots

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.example.atv.ui.testing.UiTestTags

class PlaybackRobot(
    private val composeRule: ComposeTestRule,
) {
    fun assertPlaybackVisible(): PlaybackRobot = apply {
        waitUntilTagDisplayed(UiTestTags.PlaybackScreen)
        composeRule.onNodeWithTag(UiTestTags.PlaybackScreen).assertIsDisplayed()
    }

    fun assertChannelInfoVisible(channelName: String): PlaybackRobot = apply {
        waitUntilTagDisplayed(UiTestTags.ChannelInfoOverlay)
        waitUntilTextDisplayed(channelName)
        composeRule.onNodeWithTag(UiTestTags.ChannelInfoOverlay).assertIsDisplayed()
        composeRule.onNodeWithText(channelName).assertIsDisplayed()
    }

    fun waitForChannelInfoHidden(): PlaybackRobot = apply {
        composeRule.waitUntil(timeoutMillis = 4_000) {
            runCatching {
                composeRule.onNodeWithTag(UiTestTags.ChannelInfoOverlay).assertDoesNotExist()
                true
            }.getOrDefault(false)
        }
    }

    @OptIn(ExperimentalTestApi::class)
    fun pressDpadDown(): PlaybackRobot = apply {
        composeRule.onNodeWithTag(UiTestTags.PlaybackScreen).performKeyInput {
            pressKey(Key.DirectionDown)
        }
    }

    @OptIn(ExperimentalTestApi::class)
    fun pressDpadLeft(): PlaybackRobot = apply {
        composeRule.onNodeWithTag(UiTestTags.PlaybackScreen).performKeyInput {
            pressKey(Key.DirectionLeft)
        }
    }

    fun assertChannelListVisible(): PlaybackRobot = apply {
        waitUntilTagDisplayed(UiTestTags.ChannelListOverlay)
        waitUntilTextDisplayed("Channels")
        composeRule.onNodeWithTag(UiTestTags.ChannelListOverlay).assertIsDisplayed()
        composeRule.onNodeWithText("Channels").assertIsDisplayed()
    }

    private fun waitUntilTagDisplayed(tag: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(tag).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
    }

    private fun waitUntilTextDisplayed(text: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithText(text).assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
    }
}
