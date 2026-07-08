package com.example.atv.testing.robots

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
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

    @OptIn(ExperimentalTestApi::class)
    fun pressOk(): PlaybackRobot = apply {
        composeRule.onNodeWithTag(UiTestTags.PlaybackScreen).performKeyInput {
            pressKey(Key.DirectionCenter)
        }
    }

    @OptIn(ExperimentalTestApi::class)
    fun pressMenu(): PlaybackRobot = apply {
        composeRule.onNodeWithTag(UiTestTags.PlaybackScreen).performKeyInput {
            pressKey(Key.Menu)
        }
    }

    fun assertChannelListVisible(): PlaybackRobot = apply {
        waitUntilTagDisplayed(UiTestTags.ChannelListOverlay)
        waitUntilTextDisplayed("Channels")
        composeRule.onNodeWithTag(UiTestTags.ChannelListOverlay).assertIsDisplayed()
        composeRule.onNodeWithText("Channels").assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    fun moveFocusDownAndSelectFromChannelList(
        fromChannelNumber: Int = 1,
        toChannelNumber: Int = 2,
    ): PlaybackRobot = apply {
        waitUntilTagFocused("${UiTestTags.ChannelListItemPrefix}-$fromChannelNumber")
        composeRule.onNodeWithTag("${UiTestTags.ChannelListItemPrefix}-$fromChannelNumber").performKeyInput {
            pressKey(Key.DirectionDown)
        }
        waitUntilTagFocused("${UiTestTags.ChannelListItemPrefix}-$toChannelNumber")
        composeRule.onNodeWithTag("${UiTestTags.ChannelListItemPrefix}-$toChannelNumber").performKeyInput {
            pressKey(Key.Enter)
        }
    }

    fun assertNumberPadVisible(): PlaybackRobot = apply {
        waitUntilTagDisplayed(UiTestTags.NumberPadOverlay)
        waitUntilTextDisplayed("Enter Channel Number")
        composeRule.onNodeWithTag(UiTestTags.NumberPadOverlay).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    fun enterChannelNumber(channelNumber: Int): PlaybackRobot = apply {
        val input = channelNumber.toString()
        val focusedButtonTag = "${UiTestTags.NumberPadButtonPrefix}-5"
        waitUntilTagFocused(focusedButtonTag)
        composeRule.onNodeWithTag(focusedButtonTag).performKeyInput {
            input.forEach { digit -> pressKey(digit.toNumberKey()) }
        }
        waitUntilNumberPadInputDisplayed(input)
        composeRule.onNodeWithTag(focusedButtonTag).performKeyInput {
            pressKey(Key.DirectionCenter)
        }
        waitUntilTagGone(UiTestTags.NumberPadOverlay)
    }

    fun assertSettingsMenuVisible(): PlaybackRobot = apply {
        waitUntilTagDisplayed(UiTestTags.SettingsMenu)
        waitUntilTextDisplayed("Quick Menu")
        composeRule.onNodeWithTag(UiTestTags.SettingsMenu).assertIsDisplayed()
        composeRule.onNodeWithTag("${UiTestTags.SettingsMenuItemPrefix}-channel-source").assertIsDisplayed()
        composeRule.onNodeWithTag("${UiTestTags.SettingsMenuItemPrefix}-manage-channels").assertIsDisplayed()
        composeRule.onNodeWithTag("${UiTestTags.SettingsMenuItemPrefix}-all-settings").assertIsDisplayed()
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

    private fun waitUntilNumberPadInputDisplayed(input: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule
                    .onNodeWithTag(UiTestTags.NumberPadInput)
                    .assertTextEquals(input)
                    .assertIsDisplayed()
                true
            }.getOrDefault(false)
        }
    }

    private fun waitUntilTagGone(tag: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(tag).assertDoesNotExist()
                true
            }.getOrDefault(false)
        }
    }

    private fun waitUntilTagFocused(tag: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(tag).assertIsFocused()
                true
            }.getOrDefault(false)
        }
    }

    private fun Char.toNumberKey(): Key = when (this) {
        '0' -> Key.Zero
        '1' -> Key.One
        '2' -> Key.Two
        '3' -> Key.Three
        '4' -> Key.Four
        '5' -> Key.Five
        '6' -> Key.Six
        '7' -> Key.Seven
        '8' -> Key.Eight
        '9' -> Key.Nine
        else -> error("Not a number key: $this")
    }

}
