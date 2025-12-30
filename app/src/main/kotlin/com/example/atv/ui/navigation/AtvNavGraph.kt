package com.example.atv.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.atv.ui.screens.channelmanagement.ChannelManagementScreen
import com.example.atv.ui.screens.playback.PlaybackScreen
import com.example.atv.ui.screens.settings.SettingsScreen
import com.example.atv.ui.screens.setup.SetupScreen

/**
 * Navigation routes for the app.
 */
object Routes {
    const val SETUP = "setup"
    const val PLAYBACK = "playback"
    const val CHANNEL_MANAGEMENT = "channel_management"
    const val SETTINGS = "settings"
}

/**
 * Main navigation graph for the app.
 */
@Composable
fun AtvNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Routes.SETUP
) {
    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Routes.SETUP) {
            SetupScreen(
                onPlaylistLoaded = {
                    navController.navigate(Routes.PLAYBACK) {
                        popUpTo(Routes.SETUP) { inclusive = true }
                    }
                },
                onBack = {
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    }
                },
                fromStartup = navController.previousBackStackEntry == null
            )
        }
        
        composable(Routes.PLAYBACK) {
            PlaybackScreen(
                onNavigateToSetup = {
                    navController.navigate(Routes.SETUP)
                },
                onNavigateToChannelManagement = {
                    navController.navigate(Routes.CHANNEL_MANAGEMENT)
                },
                onNavigateToSettings = {
                    navController.navigate(Routes.SETTINGS)
                }
            )
        }
        
        composable(Routes.CHANNEL_MANAGEMENT) {
            ChannelManagementScreen(
                onBack = {
                    navController.popBackStack()
                }
            )
        }
        
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = {
                    navController.popBackStack()
                },
                onLoadNewPlaylist = {
                    navController.navigate(Routes.SETUP)
                },
                onManageChannels = {
                    navController.navigate(Routes.CHANNEL_MANAGEMENT)
                }
            )
        }
    }
}
