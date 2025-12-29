package com.example.atv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
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
}
