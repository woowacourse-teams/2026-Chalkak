package com.stonefive.chalkak.feature.developertools

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme

class DeveloperToolsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ChalkakTheme {
                DeveloperToolsRoute(onBackClick = ::finish)
            }
        }
    }
}
