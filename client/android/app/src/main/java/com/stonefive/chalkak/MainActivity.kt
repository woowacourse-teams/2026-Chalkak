package com.stonefive.chalkak

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.feature.display.DisplayRoute
import com.stonefive.chalkak.feature.upload.PhotoUploadRoute

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.light(
                scrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT,
            ),
            navigationBarStyle = SystemBarStyle.light(
                scrim = android.graphics.Color.TRANSPARENT,
                darkScrim = android.graphics.Color.TRANSPARENT,
            ),
        )
        setContent {
            ChalkakTheme {
                ChalkakApp()
            }
        }
    }
}

@Composable
private fun ChalkakApp() {
    var showPhotoUpload by rememberSaveable { mutableStateOf(false) }

    if (showPhotoUpload) {
        PhotoUploadRoute(
            onBack = { showPhotoUpload = false },
            onSubmitted = { showPhotoUpload = false },
        )
    } else {
        DisplayRoute(
            onOpenPhotoUpload = { showPhotoUpload = true },
            onNavigateToBottomBar = {},
        )
    }
}
