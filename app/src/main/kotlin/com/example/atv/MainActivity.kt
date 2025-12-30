package com.example.atv

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.addCallback
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.tv.material3.Surface
import com.example.atv.ui.navigation.AtvNavGraph
import com.example.atv.ui.theme.AtvColors
import com.example.atv.ui.theme.AtvTheme
import dagger.hilt.android.AndroidEntryPoint

/**
 * Main activity for the ATV app.
 * Single activity hosting the Compose UI.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    private var lastBackPressTime = 0L
    
    companion object {
        private const val DOUBLE_BACK_EXIT_DELAY_MS = 2000L
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Handle double-back to exit
        onBackPressedDispatcher.addCallback(this) {
            val currentTime = System.currentTimeMillis()
            if (currentTime - lastBackPressTime < DOUBLE_BACK_EXIT_DELAY_MS) {
                // Double back detected - exit app
                finish()
            } else {
                // First back press - show toast hint
                lastBackPressTime = currentTime
                Toast.makeText(
                    this@MainActivity,
                    "Press back again to exit",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
        
        setContent {
            AtvTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    colors = androidx.tv.material3.SurfaceDefaults.colors(
                        containerColor = AtvColors.Background
                    )
                ) {
                    AtvNavGraph()
                }
            }
        }
    }
    
    /**
     * Called when the user is leaving the activity as a result of user choice.
     * For example, when HOME button is pressed.
     * Gracefully close the app instead of keeping it in background.
     */
    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        finish()
    }
}
