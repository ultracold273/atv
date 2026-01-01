package com.example.atv.ui.components

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Singleton manager for app-wide snack bar messages.
 * Can be called from anywhere (Activity, ViewModel, etc.) to show messages.
 */
object SnackBarManager {
    
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()
    
    /**
     * Show a snack bar message.
     * The message will be automatically dismissed after a timeout by the UI.
     */
    fun show(message: String) {
        _message.value = message
    }
    
    /**
     * Dismiss the current snack bar message.
     */
    fun dismiss() {
        _message.value = null
    }
}
