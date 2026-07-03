package com.example.atv.testing.robots

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
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
