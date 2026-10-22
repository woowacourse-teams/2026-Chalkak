package com.stonefive.chalkak.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBar
import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBarHostState
import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBarItem
import com.stonefive.chalkak.core.designsystem.component.bottombar.LocalBottomBarHostState

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
internal fun MainBottomBarLayout(
    hostState: ChalkakBottomBarHostState,
    selectedItem: ChalkakBottomBarItem?,
    onItemSelected: (ChalkakBottomBarItem) -> Unit,
    onAddClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable SharedTransitionScope.() -> Unit,
) {
    CompositionLocalProvider(LocalBottomBarHostState provides hostState) {
        Box(modifier.fillMaxSize()) {
            SharedTransitionLayout(modifier = Modifier.fillMaxSize(), content = content)
            if (selectedItem != null) {
                val scrollState = hostState.stateFor(selectedItem)
                ChalkakBottomBar(
                    selectedItem = selectedItem,
                    onItemSelected = onItemSelected,
                    onAddClick = onAddClick,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .onSizeChanged { hostState.updateHeight(it.height) }
                        .graphicsLayer { translationY = scrollState.offset }
                        .testTag("main_bottom_bar"),
                )
            }
        }
    }
}
