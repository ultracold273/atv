package com.example.atv.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import com.example.atv.ui.theme.AtvColors
import com.example.atv.ui.theme.AtvTypography
import kotlinx.coroutines.delay

/**
 * App-wide snack bar overlay that displays messages from SnackBarManager.
 * Should be placed at the root of the app's UI hierarchy.
 */
@Composable
fun AppSnackBar(
    modifier: Modifier = Modifier,
    autoDismissMs: Long = 2500L
) {
    val message by SnackBarManager.message.collectAsStateWithLifecycle()
    
    // Auto-dismiss after timeout
    LaunchedEffect(message) {
        if (message != null) {
            delay(autoDismissMs)
            SnackBarManager.dismiss()
        }
    }
    
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        AnimatedVisibility(
            visible = message != null,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it }
        ) {
            Box(
                modifier = Modifier
                    .padding(bottom = 48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(AtvColors.Surface.copy(alpha = 0.95f))
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                Text(
                    text = message ?: "",
                    style = AtvTypography.bodyLarge,
                    color = AtvColors.OnSurface,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}
