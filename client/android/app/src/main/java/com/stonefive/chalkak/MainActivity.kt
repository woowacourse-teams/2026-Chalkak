package com.stonefive.chalkak

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import com.stonefive.chalkak.feature.display.DisplayRoute
import com.stonefive.chalkak.feature.feed.FeedContentState
import com.stonefive.chalkak.feature.feed.FeedRoute

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
                var selectedFeed by remember {
                    mutableStateOf<FeedContentState.Success?>(null)
                }

                if (selectedFeed == null) {
                    DisplayRoute(
                        onOpenPhotoUpload = {},
                        onNavigateToBottomBar = {},
                        onOpenFeed = { post, dateLabel, topic ->
                            selectedFeed = FeedContentState.Success(
                                dateLabel = dateLabel,
                                topic = topic,
                                post = post,
                                isLiked = false,
                            )
                        },
                    )
                } else {
                    BackHandler { selectedFeed = null }

                    FeedRoute(
                        initialContent = selectedFeed,
                        onNavigateBack = { selectedFeed = null },
                    )
                }
            }
        }
    }
}
