package com.stonefive.chalkak.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.testTag
import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBar
import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBarHostState
import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBarItem
import com.stonefive.chalkak.core.designsystem.component.bottombar.LocalBottomBarHostState
import com.stonefive.chalkak.core.designsystem.component.image.PHOTO_CONTENT_FADE_DURATION_MILLIS
import com.stonefive.chalkak.core.designsystem.component.image.PHOTO_TRANSITION_DURATION_MILLIS

/**
 * 탭 화면(홈·전시·기록·설정) 위에 하단바 하나를 겹쳐 그린다.
 *
 * - [selectedItem]이 탭이면 하단바를 보이고, 탭이 아닌 화면(피드 등)이면 `null`이 들어와 하단바를 숨긴다.
 * - 탭끼리 이동할 때는 하단바를 그대로 두고 선택 항목만 바꾼다(#751, 화면마다 하단바를 그리면 전환 중 깜빡였다).
 * - 탭이 아닌 화면으로 갈 때는 화면 전환과 함께 서서히 사라지고, 돌아올 때 서서히 나타난다.
 *   첫 프레임에 바로 없애면 화면은 아직 넘어가는 중인데 하단바만 툭 사라져 보였다(#1061).
 */
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
    LaunchedEffect(hostState, selectedItem) {
        if (selectedItem == ChalkakBottomBarItem.HOME) {
            hostState.stateFor(ChalkakBottomBarItem.HOME).reset()
        }
    }
    // 사라지는 동안에도 떠나기 직전 탭의 하단바를 그대로 그리기 위해 마지막 탭을 기억한다.
    var lastSelectedItem by remember { mutableStateOf(selectedItem) }
    SideEffect {
        if (selectedItem != null) lastSelectedItem = selectedItem
    }

    CompositionLocalProvider(LocalBottomBarHostState provides hostState) {
        Box(modifier.fillMaxSize()) {
            SharedTransitionLayout(modifier = Modifier.fillMaxSize(), content = content)
            AnimatedVisibility(
                visible = selectedItem != null,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth(),
                enter = fadeIn(tween(BOTTOM_BAR_ENTER_DURATION_MILLIS)),
                exit = fadeOut(tween(BOTTOM_BAR_EXIT_DURATION_MILLIS)),
            ) {
                val shownItem = selectedItem ?: lastSelectedItem ?: return@AnimatedVisibility
                val scrollState = hostState.stateFor(shownItem)
                // 사라지는 중인 하단바는 이미 떠난 탭의 것이라 눌러도 아무 일도 하지 않는다.
                val isLeaving = selectedItem == null
                ChalkakBottomBar(
                    selectedItem = shownItem,
                    onItemSelected = { item -> if (!isLeaving) onItemSelected(item) },
                    onAddClick = { if (!isLeaving) onAddClick() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .onSizeChanged { hostState.updateHeight(it.height) }
                        .graphicsLayer { translationY = scrollState.offset }
                        .testTag("main_bottom_bar"),
                )
            }
        }
    }
}

// 탭이 아닌 화면은 기본 전환(서서히 나타나기)으로 들어오므로 떠나는 탭 화면과 같은 시간에 맞춘다.
private const val BOTTOM_BAR_EXIT_DURATION_MILLIS = PHOTO_CONTENT_FADE_DURATION_MILLIS

// 피드에서 돌아올 때 탭 화면이 나타나는 시간(popEnterTransition)에 맞춘다.
private const val BOTTOM_BAR_ENTER_DURATION_MILLIS = PHOTO_TRANSITION_DURATION_MILLIS
