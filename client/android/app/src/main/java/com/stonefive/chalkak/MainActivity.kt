package com.stonefive.chalkak

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBarItem
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.feature.display.DisplayRoute
import com.stonefive.chalkak.feature.record.RecordRoute

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
                var currentScreen by rememberSaveable {
                    mutableStateOf(AppScreen.DISPLAY.name)
                }

                when (currentScreen) {
                    AppScreen.DISPLAY.name -> DisplayRoute(
                        onOpenPhotoUpload = {},
                        onNavigateToBottomBar = { item ->
                            if (item == ChalkakBottomBarItem.RECORD) {
                                currentScreen = AppScreen.RECORD.name
                            }
                        },
                    )

                    AppScreen.RECORD.name -> RecordRoute(
                        onOpenPhotoUpload = {},
                        onNavigateToBottomBar = { item ->
                            if (item == ChalkakBottomBarItem.DISPLAY) {
                                currentScreen = AppScreen.DISPLAY.name
                            }
                        },
                    )
                }
            }
        }
    }
}

private enum class AppScreen {
    DISPLAY,
    RECORD,
}
