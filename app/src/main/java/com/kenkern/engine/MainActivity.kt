package com.kenkern.engine

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.kenkern.engine.ui.KenkernAppRoot
import com.kenkern.engine.ui.theme.KenkernTheme

/**
 * Activity única da KENKERN. Toda a UI é Jetpack Compose (Material 3);
 * o viewport 3D é um GLSurfaceView embutido via AndroidView.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KenkernTheme {
                KenkernAppRoot()
            }
        }
    }
}
