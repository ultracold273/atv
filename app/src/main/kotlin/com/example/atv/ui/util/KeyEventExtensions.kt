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
 * 
 * Callbacks return Boolean to indicate if the event was consumed.
 * Return true to consume the event, false to let it propagate.
 */
fun Modifier.handleDPadKeyEvents(
    onUp: (() -> Boolean)? = null,
    onDown: (() -> Boolean)? = null,
    onLeft: (() -> Boolean)? = null,
    onRight: (() -> Boolean)? = null,
    onCenter: (() -> Boolean)? = null,
    onBack: (() -> Boolean)? = null,
    onMenu: (() -> Boolean)? = null
): Modifier = this.onKeyEvent { event ->
    if (event.type == KeyEventType.KeyDown) {
        when (event.key) {
            Key.DirectionUp -> {
                onUp?.invoke() ?: false
            }
            Key.DirectionDown -> {
                onDown?.invoke() ?: false
            }
            Key.DirectionLeft -> {
                onLeft?.invoke() ?: false
            }
            Key.DirectionRight -> {
                onRight?.invoke() ?: false
            }
            Key.DirectionCenter, Key.Enter -> {
                onCenter?.invoke() ?: false
            }
            Key.Back, Key.Escape -> {
                onBack?.invoke() ?: false
            }
            Key.Menu -> {
                onMenu?.invoke() ?: false
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
