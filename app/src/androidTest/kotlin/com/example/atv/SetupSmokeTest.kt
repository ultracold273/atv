package com.example.atv

import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.atv.data.local.db.ChannelDao
import com.example.atv.testing.E2eDatabaseSeeder
import com.example.atv.testing.robots.SetupRobot
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
class SetupSmokeTest {
    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createEmptyComposeRule()

    @Inject lateinit var channelDao: ChannelDao

    private lateinit var setup: SetupRobot

    @Before
    fun setUp() {
        hiltRule.inject()
        setup = SetupRobot(composeRule)
        runBlocking {
            E2eDatabaseSeeder(channelDao).seedEmpty()
        }
    }

    @Test
    fun emptyChannels_launchesSetupScreen() {
        ActivityScenario.launch(MainActivity::class.java).use { _: ActivityScenario<MainActivity> ->
            setup
                .assertSetupVisible()
                .assertEmptySetupContentVisible()
        }
    }

    @Test
    fun emptyChannels_channelSourceActionOpensChannelSourceScreen() {
        ActivityScenario.launch(MainActivity::class.java).use { _: ActivityScenario<MainActivity> ->
            setup
                .assertSetupVisible()
                .openChannelSource()
                .assertChannelSourceVisible()
        }
    }
}
