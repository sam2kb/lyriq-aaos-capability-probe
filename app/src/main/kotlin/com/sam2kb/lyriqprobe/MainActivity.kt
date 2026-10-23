package com.sam2kb.lyriqprobe

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import com.sam2kb.lyriqprobe.ui.ProbeApp
import com.sam2kb.lyriqprobe.ui.theme.LyriqProbeTheme

class MainActivity : ComponentActivity() {
    private val viewModel: ProbeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        setContent {
            LyriqProbeTheme {
                ProbeApp(viewModel)
            }
        }
    }
}
