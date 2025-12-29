package com.example.atv.ui.util

import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type

/**
 * Extension modifier for handling D-pad key events on TV.
 * 
 * Key mapping:
 * - UP: Previous channel
 * - DOWN: Next channel
 * - LEFT: Show channel list
 * - CENTER/OK: Show number pad
 * - BACK: Dismiss overlay / Navigate back
 * - MENU: Show settings
 */
fun Modifier.handleDPadKeyEvents(
    onUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
    onLeft: (() -> Unit)? = null,
    onRight: (() -> Unit)? = null,
    onCenter: (() -> Unit)? = null,
    onBack: (() -> Unit)? = null,
    onMenu: (() -> Unit)? = null
): Modifier = this.onKeyEvent { event ->
    if (event.type == KeyEventType.KeyDown) {
        when (event.key) {
            Key.DirectionUp -> {
                onUp?.invoke()
                onUp != null
            }
            Key.DirectionDown -> {
                onDown?.invoke()
                onDown != null
            }
            Key.DirectionLeft -> {
                onLeft?.invoke()
                onLeft != null
            }
            Key.DirectionRight -> {
                onRight?.invoke()
                onRight != null
            }
            Key.DirectionCenter, Key.Enter -> {
                onCenter?.invoke()
                onCenter != null
            }
            Key.Back, Key.Escape -> {
                onBack?.invoke()
                onBack != null
            }
            Key.Menu -> {
                onMenu?.invoke()
                onMenu != null
            }
            else -> false
        }
    } else {
        false
    }
}

/**
 * Check if a key event is a D-pad navigation key.
 */
fun androidx.compose.ui.input.key.KeyEvent.isDPadKey(): Boolean {
    return key == Key.DirectionUp ||
           key == Key.DirectionDown ||
           key == Key.DirectionLeft ||
           key == Key.DirectionRight ||
           key == Key.DirectionCenter
}
