package com.example.atv.testing.robots

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsFocused
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performKeyInput
import androidx.compose.ui.test.pressKey
import com.example.atv.ui.testing.UiTestTags

class SetupRobot(
    private val composeRule: ComposeTestRule,
) {
    private companion object {
        const val AppTitle = "ATV"
        const val SetupPrompt = "Select a channel source to get started"
        const val ChannelSourceAction = "Channel Source"
    }

    fun assertSetupVisible(): SetupRobot = apply {
        waitUntilTagDisplayed(UiTestTags.SetupScreen)
        composeRule.onNodeWithTag(UiTestTags.SetupScreen).assertIsDisplayed()
    }

    fun assertEmptySetupContentVisible(): SetupRobot = apply {
        waitUntilTextDisplayed(AppTitle)
        composeRule.onNodeWithText(AppTitle).assertIsDisplayed()
        composeRule.onNodeWithText(SetupPrompt).assertIsDisplayed()
        composeRule.onNodeWithText(ChannelSourceAction).assertIsDisplayed()
    }

    @OptIn(ExperimentalTestApi::class)
    fun openChannelSource(): SetupRobot = apply {
        waitUntilTagFocused(UiTestTags.SetupChannelSourceButton)
        composeRule.onNodeWithTag(UiTestTags.SetupChannelSourceButton).performKeyInput {
            pressKey(Key.DirectionCenter)
        }
    }

    fun assertChannelSourceVisible(): SetupRobot = apply {
        waitUntilTagDisplayed(UiTestTags.IptvSettingsScreen)
        waitUntilTextDisplayed("Playlist URL")
        composeRule.onNodeWithTag(UiTestTags.IptvSettingsScreen).assertIsDisplayed()
        composeRule.onNodeWithText("M3U8").assertIsDisplayed()
        composeRule.onNodeWithText("Playlist URL").assertIsDisplayed()
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

    private fun waitUntilTagFocused(tag: String) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching {
                composeRule.onNodeWithTag(tag).assertIsFocused()
                true
            }.getOrDefault(false)
        }
    }
}
