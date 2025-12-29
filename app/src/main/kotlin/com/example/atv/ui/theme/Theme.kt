package com.example.atv.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme

/**
 * Color palette for the ATV app.
 * Using Catppuccin Mocha colors for dark TV theme.
 */
object AtvColors {
    // Background colors
    val Background = Color(0xFF1E1E2E)
    val Surface = Color(0xFF313244)
    val SurfaceVariant = Color(0xFF45475A)
    
    // Primary colors
    val Primary = Color(0xFF89B4FA)
    val PrimaryContainer = Color(0xFF45475A)
    val OnPrimary = Color(0xFF1E1E2E)
    
    // Secondary colors
    val Secondary = Color(0xFFA6E3A1)
    val SecondaryContainer = Color(0xFF45475A)
    val OnSecondary = Color(0xFF1E1E2E)
    
    // Accent colors
    val Accent = Color(0xFFF5C2E7)
    val Error = Color(0xFFF38BA8)
    val Warning = Color(0xFFFAB387)
    
    // Text colors
    val OnBackground = Color(0xFFCDD6F4)
    val OnSurface = Color(0xFFCDD6F4)
    val OnSurfaceVariant = Color(0xFFA6ADC8)
    
    // Border/outline
    val Outline = Color(0xFF6C7086)
    
    // Focus indicator
    val FocusRing = Color(0xFF89B4FA)
}

/**
 * TV Material 3 dark color scheme.
 */
private val DarkColorScheme = darkColorScheme(
    primary = AtvColors.Primary,
    onPrimary = AtvColors.OnPrimary,
    primaryContainer = AtvColors.PrimaryContainer,
    secondary = AtvColors.Secondary,
    onSecondary = AtvColors.OnSecondary,
    secondaryContainer = AtvColors.SecondaryContainer,
    background = AtvColors.Background,
    onBackground = AtvColors.OnBackground,
    surface = AtvColors.Surface,
    onSurface = AtvColors.OnSurface,
    surfaceVariant = AtvColors.SurfaceVariant,
    onSurfaceVariant = AtvColors.OnSurfaceVariant,
    error = AtvColors.Error,
    border = AtvColors.Outline
)

/**
 * ATV app theme.
 */
@Composable
fun AtvTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}
