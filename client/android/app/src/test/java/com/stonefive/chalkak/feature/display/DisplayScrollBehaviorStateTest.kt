package com.stonefive.chalkak.feature.display

import androidx.compose.foundation.lazy.staggeredgrid.LazyStaggeredGridState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import com.stonefive.chalkak.core.designsystem.scroll.BottomBarScrollState
import kotlinx.coroutines.test.TestScope
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 전시 화면 위쪽 영역(날짜 헤더·정렬 필터)이 끌기를 어떻게 나눠 쓰는지 본다(#988).
 *
 * LazyStaggeredGridState()는 측정 전이라 canScrollBackward == false, 즉 목록이 맨 위인 상태다.
 * 헤더·필터를 접힌 상태로 만들 때는 화면과 같이 위로 미는 끌기(onPreScroll, 음수)를 먼저 보낸다.
 */
class DisplayScrollBehaviorStateTest {
    @Test
    fun `목록이 맨 위이고 헤더·필터가 접혀 있으면 아래로 끌기를 필터와 헤더를 펼치는 데 먼저 쓴다`() {
        // Given 헤더 높이 200·필터 높이 100, 헤더 -200·필터 -100(모두 접힘), 목록이 맨 위
        val state = displayScrollState(hasFilter = true, headerHeight = 200, filterHeight = 100)
        state.dragDown(-300f)

        // When  사용자가 아래로 120 끈다(스크롤 전 단계)
        val consumed = state.dragDown(120f)

        // Then  소비한 양 120, 필터 0, 헤더 -180
        assertEquals(120f, consumed.y)
        assertEquals(0f, state.filterOffset)
        assertEquals(-180f, state.headerOffset)
    }

    @Test
    fun `펼치고 남은 끌기는 소비하지 않고 넘긴다`() {
        // Given 헤더 높이 200, 헤더 -50·필터 0, 목록이 맨 위
        val state = displayScrollState(hasFilter = true, headerHeight = 200, filterHeight = 100)
        state.dragDown(-50f)

        // When  사용자가 아래로 120 끈다
        val consumed = state.dragDown(120f)

        // Then  소비한 양 50, 헤더 0 (남은 70은 새로고침으로 넘어감)
        assertEquals(50f, consumed.y)
        assertEquals(0f, state.headerOffset)
    }

    @Test
    fun `헤더·필터가 모두 펼쳐진 맨 위에서는 끌기를 소비하지 않는다`() {
        // Given 헤더 0·필터 0, 목록이 맨 위
        val state = displayScrollState(hasFilter = true, headerHeight = 200, filterHeight = 100)

        // When  사용자가 아래로 120 끈다
        val consumed = state.dragDown(120f)

        // Then  소비한 양 0, 헤더 0, 필터 0
        assertEquals(0f, consumed.y)
        assertEquals(0f, state.headerOffset)
        assertEquals(0f, state.filterOffset)
    }

    @Test
    fun `지난 날짜 전시(필터 없음)에서는 헤더만 펼친다`() {
        // Given 필터 없음, 헤더 높이 200, 헤더 -200, 목록이 맨 위
        val state = displayScrollState(hasFilter = false, headerHeight = 200, filterHeight = 0)
        state.dragDown(-200f)

        // When  사용자가 아래로 120 끈다
        val consumed = state.dragDown(120f)

        // Then  소비한 양 120, 헤더 -80, 필터 0
        assertEquals(120f, consumed.y)
        assertEquals(-80f, state.headerOffset)
        assertEquals(0f, state.filterOffset)
    }

    private fun displayScrollState(
        hasFilter: Boolean,
        headerHeight: Int,
        filterHeight: Int,
    ): DisplayScrollBehaviorState = DisplayScrollBehaviorState(
        gridState = LazyStaggeredGridState(),
        settleScope = TestScope(),
        scrollToTopToggleThresholdPx = 100f,
        hasFilter = hasFilter,
        bottomBarState = BottomBarScrollState(),
    ).apply {
        this.headerHeight = headerHeight
        this.filterHeight = filterHeight
    }

    /** 사용자 손가락 끌기의 스크롤 전 단계. 양수는 아래로 끌기(위쪽 내용 보기), 음수는 위로 밀기. */
    private fun DisplayScrollBehaviorState.dragDown(deltaY: Float): Offset = nestedScrollConnection.onPreScroll(
        available = Offset(0f, deltaY),
        source = NestedScrollSource.UserInput,
    )
}
