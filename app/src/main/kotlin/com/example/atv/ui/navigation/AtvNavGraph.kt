@file:Suppress("MatchingDeclarationName") // Groups the Routes table with the AtvNavGraph composable

package com.example.atv.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.atv.ui.components.AppSnackBar
import com.example.atv.domain.model.ChannelSourceMode
import com.example.atv.ui.screens.channelmanagement.ChannelManagementScreen
import com.example.atv.ui.screens.iptv.IptvSettingsScreen
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
    const val IPTV_SETTINGS = "iptv_settings"
    const val CHANNEL_SOURCE_M3U8 = "channel_source_m3u8"
}

/**
 * Main navigation graph for the app.
 */
@Composable
fun AtvNavGraph(
    navController: NavHostController = rememberNavController(),
    startDestination: String = Routes.SETUP
) {
    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = startDestination
        ) {
        composable(Routes.SETUP) {
            SetupScreen(
                onOpenChannelSource = {
                    navController.navigate(Routes.CHANNEL_SOURCE_M3U8)
                },
                onBack = {
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                    }
                }
            )
        }
        
        composable(Routes.PLAYBACK) {
            PlaybackScreen(
                onNavigateToSetup = {
                    navController.navigate(Routes.CHANNEL_SOURCE_M3U8)
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
                onNavigateToChannelSource = {
                    navController.navigate(Routes.IPTV_SETTINGS)
                },
                onManageChannels = {
                    navController.navigate(Routes.CHANNEL_MANAGEMENT)
                }
            )
        }

        composable(Routes.IPTV_SETTINGS) {
            IptvSettingsScreen(
                onBack = {
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                        true
                    } else {
                        false
                    }
                }
            )
        }

        composable(Routes.CHANNEL_SOURCE_M3U8) {
            IptvSettingsScreen(
                initialMode = ChannelSourceMode.M3U8,
                onBack = {
                    if (navController.previousBackStackEntry != null) {
                        navController.popBackStack()
                        true
                    } else {
                        false
                    }
                }
            )
        }
    }
    
        // App-wide snack bar overlay (displays above all screens)
        AppSnackBar()
    }
}
