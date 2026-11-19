package com.stonefive.chalkak.navigation

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.height
import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBarHostState
import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBarItem
import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakScreenBottomBar
import com.stonefive.chalkak.core.designsystem.theme.ChalkakTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

@OptIn(ExperimentalSharedTransitionApi::class)
class MainBottomBarLayoutTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun sharedBarKeepsOtherTabOffsetButRestoresHomeWhenReturning() {
        val hostState = ChalkakBottomBarHostState()
        val selectedItem = mutableStateOf(ChalkakBottomBarItem.HOME)
        composeRule.setContent {
            ChalkakTheme {
                MainBottomBarLayout(
                    hostState = hostState,
                    selectedItem = selectedItem.value,
                    onItemSelected = { selectedItem.value = it },
                    onAddClick = {},
                ) {}
            }
        }
        composeRule.waitForIdle()
        val bar = composeRule.onNodeWithTag("main_bottom_bar")
        val restingBounds = bar.getUnclippedBoundsInRoot()
        val homeState = hostState.stateFor(ChalkakBottomBarItem.HOME)
        val settingsState = hostState.stateFor(ChalkakBottomBarItem.SETTINGS)
        assertTrue(homeState.height > 0)

        composeRule.runOnIdle {
            homeState.offset = homeState.height / 4f
            settingsState.offset = settingsState.height / 10f
        }
        composeRule.waitForIdle()
        assertEquals(
            restingBounds.top.value + restingBounds.height.value / 4f,
            bar
                .getUnclippedBoundsInRoot()
                .top.value,
            0.5f,
        )

        composeRule.runOnIdle { selectedItem.value = ChalkakBottomBarItem.SETTINGS }
        composeRule.waitForIdle()
        assertEquals(
            restingBounds.top.value + restingBounds.height.value / 10f,
            bar
                .getUnclippedBoundsInRoot()
                .top.value,
            0.5f,
        )

        composeRule.runOnIdle { selectedItem.value = ChalkakBottomBarItem.HOME }
        composeRule.waitForIdle()
        assertEquals(
            restingBounds.top.value,
            bar
                .getUnclippedBoundsInRoot()
                .top.value,
            0.5f,
        )
    }

    @Test
    fun returningFromRecordRevealsHomeBarAndClearsHiddenTarget() {
        val hostState = ChalkakBottomBarHostState()
        val selectedItem = mutableStateOf(ChalkakBottomBarItem.HOME)
        composeRule.setContent {
            ChalkakTheme {
                MainBottomBarLayout(
                    hostState = hostState,
                    selectedItem = selectedItem.value,
                    onItemSelected = { selectedItem.value = it },
                    onAddClick = {},
                ) {}
            }
        }
        composeRule.waitForIdle()
        val bar = composeRule.onNodeWithTag("main_bottom_bar")
        val restingBounds = bar.getUnclippedBoundsInRoot()
        val homeState = hostState.stateFor(ChalkakBottomBarItem.HOME)
        composeRule.runOnIdle {
            homeState.restoreState(homeState.height.toFloat(), homeState.height, isTargetHidden = true)
            selectedItem.value = ChalkakBottomBarItem.RECORD
        }
        composeRule.waitForIdle()
        composeRule.runOnIdle { selectedItem.value = ChalkakBottomBarItem.HOME }
        composeRule.waitForIdle()

        assertEquals(0f, homeState.offset)
        assertEquals(false, homeState.isTargetHidden)
        assertEquals(
            restingBounds.top.value,
            bar
                .getUnclippedBoundsInRoot()
                .top.value,
            0.5f,
        )
    }

    @Test
    fun hostedScreenReservesTheMeasuredBarHeightAndFollowsHeightUpdates() {
        val hostState = ChalkakBottomBarHostState()
        composeRule.setContent {
            ChalkakTheme {
                MainBottomBarLayout(
                    hostState = hostState,
                    selectedItem = ChalkakBottomBarItem.SETTINGS,
                    onItemSelected = {},
                    onAddClick = {},
                ) {
                    Column(Modifier.fillMaxSize()) {
                        Spacer(Modifier.weight(1f))
                        ChalkakScreenBottomBar(
                            selectedItem = ChalkakBottomBarItem.SETTINGS,
                            onItemSelected = {},
                            onAddClick = {},
                            modifier = Modifier.fillMaxWidth().testTag("screen_bottom_bar_spacer"),
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
        val barHeight = composeRule
            .onNodeWithTag("main_bottom_bar")
            .getUnclippedBoundsInRoot()
            .height.value
        val spacer = composeRule.onNodeWithTag("screen_bottom_bar_spacer")
        assertTrue(barHeight > 0f)
        assertEquals(
            barHeight,
            spacer
                .getUnclippedBoundsInRoot()
                .height.value,
            0.5f,
        )
        val measuredHeight = hostState.stateFor(ChalkakBottomBarItem.SETTINGS).height
        ChalkakBottomBarItem.entries.forEach { item ->
            assertEquals(measuredHeight, hostState.stateFor(item).height)
        }

        val updatedHeight = measuredHeight * 2
        composeRule.runOnIdle { hostState.updateHeight(updatedHeight) }
        composeRule.waitForIdle()
        assertEquals(
            barHeight * 2,
            spacer
                .getUnclippedBoundsInRoot()
                .height.value,
            0.5f,
        )
        ChalkakBottomBarItem.entries.forEach { item ->
            assertEquals(updatedHeight, hostState.stateFor(item).height)
        }
    }

    @Test
    fun leavingTabsFadesTheBarOutWithTheScreenInsteadOfRemovingItAtOnce() {
        // Given  홈 탭에서 하단바가 보인다
        val selectedItem = mutableStateOf<ChalkakBottomBarItem?>(ChalkakBottomBarItem.HOME)
        setBarContent(selectedItem)
        composeRule.mainClock.autoAdvance = false

        // When   사진을 눌러 탭이 아닌 화면(피드)으로 간다
        composeRule.runOnIdle { selectedItem.value = null }
        composeRule.mainClock.advanceTimeBy(BAR_TRANSITION_PROBE_MILLIS)

        // Then   전환 도중에는 하단바가 남아 있고, 전환이 끝나면 사라진다
        composeRule.onNodeWithTag("main_bottom_bar").assertExists()
        composeRule.mainClock.advanceTimeBy(BAR_TRANSITION_END_MILLIS)
        composeRule.onNodeWithTag("main_bottom_bar").assertDoesNotExist()
    }

    @Test
    fun tabTapWhileTheBarIsLeavingIsIgnored() {
        // Given  홈 탭에서 탭이 아닌 화면으로 가는 중이라 하단바가 사라지고 있다
        val selectedItem = mutableStateOf<ChalkakBottomBarItem?>(ChalkakBottomBarItem.HOME)
        val tappedItems = mutableListOf<ChalkakBottomBarItem>()
        setBarContent(selectedItem, onItemSelected = { tappedItems += it })
        composeRule.mainClock.autoAdvance = false
        composeRule.runOnIdle { selectedItem.value = null }
        composeRule.mainClock.advanceTimeBy(BAR_TRANSITION_PROBE_MILLIS)

        // When   사라지는 하단바의 "전시"를 누른다
        composeRule.onNodeWithText("전시").performClick()

        // Then   탭 이동이 일어나지 않는다
        assertEquals(emptyList<ChalkakBottomBarItem>(), tappedItems)
    }

    @Test
    fun movingBetweenTabsKeepsTheBarWithoutTransition() {
        // Given  홈 탭에서 하단바가 보인다
        val selectedItem = mutableStateOf<ChalkakBottomBarItem?>(ChalkakBottomBarItem.HOME)
        setBarContent(selectedItem)
        composeRule.mainClock.autoAdvance = false

        // When   하단바로 전시 탭으로 간다
        composeRule.runOnIdle { selectedItem.value = ChalkakBottomBarItem.DISPLAY }
        composeRule.mainClock.advanceTimeByFrame()

        // Then   다음 프레임에 하단바가 그대로 있고 전시가 선택돼 있다
        composeRule.onNodeWithTag("main_bottom_bar").assertExists()
        composeRule.onNode(hasText("전시") and hasClickAction()).assertIsSelected()
    }

    private fun setBarContent(
        selectedItem: MutableState<ChalkakBottomBarItem?>,
        onItemSelected: (ChalkakBottomBarItem) -> Unit = {},
    ) {
        composeRule.setContent {
            ChalkakTheme {
                MainBottomBarLayout(
                    hostState = remember { ChalkakBottomBarHostState() },
                    selectedItem = selectedItem.value,
                    onItemSelected = onItemSelected,
                    onAddClick = {},
                ) {}
            }
        }
        composeRule.waitForIdle()
    }

    private companion object {
        const val BAR_TRANSITION_PROBE_MILLIS = 100L
        const val BAR_TRANSITION_END_MILLIS = 1_000L
    }
}
