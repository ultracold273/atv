package com.example.atv

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.atv.data.local.db.ChannelDao
import com.example.atv.testing.E2eDatabaseSeeder
import com.example.atv.testing.E2eFixtures
import com.example.atv.testing.robots.PlaybackRobot
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import javax.inject.Inject

@HiltAndroidTest
@RunWith(AndroidJUnit4::class)
class PlaybackSmokeTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    @Inject lateinit var channelDao: ChannelDao

    private lateinit var playback: PlaybackRobot

    @Before
    fun setUp() {
        hiltRule.inject()
        playback = PlaybackRobot(composeRule)
        runBlocking {
            E2eDatabaseSeeder(channelDao).seedChannels(E2eFixtures.playbackChannels)
        }
    }

    @Test
    fun seededChannels_launchPlayback_switchChannelAndOpenChannelList() {
        ActivityScenario.launch(MainActivity::class.java).use { _: ActivityScenario<MainActivity> ->
            playback
                .assertPlaybackVisible()
                .assertChannelInfoVisible("News One")
                .waitForChannelInfoHidden()
                .pressDpadDown()
                .assertChannelInfoVisible("Sports Two")
                .waitForChannelInfoHidden()
                .pressDpadLeft()
                .assertChannelListVisible()
        }
    }
}
