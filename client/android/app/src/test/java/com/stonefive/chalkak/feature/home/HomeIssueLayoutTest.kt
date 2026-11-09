package com.stonefive.chalkak.feature.home

import com.stonefive.chalkak.domain.model.Banner
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeIssueLayoutTest {
    @Test
    fun `issue is shown while loading or with banners and hidden otherwise`() {
        assertTrue(state(bannerStatus = HomeSectionStatus.Loading).showsIssue)
        assertTrue(state(bannerCount = 1).showsIssue)
        assertFalse(state().showsIssue)
        assertFalse(state(bannerStatus = HomeSectionStatus.Error).showsIssue)
    }

    @Test
    fun `carousel adds a trailing page only while loading more or after failure`() {
        assertEquals(3, state(bannerCount = 3).issueCarouselPageCount)
        assertEquals(4, state(bannerCount = 3).copy(isLoadingMoreBanners = true).issueCarouselPageCount)
        assertEquals(4, state(bannerCount = 3).copy(bannerLoadMoreError = true).issueCarouselPageCount)
    }

    @Test
    fun `more banners load only near the end`() {
        val state = state(bannerCount = 10, bannerHasNext = true)

        assertFalse(state.shouldLoadMoreBanners(lastVisibleIssueIndex = null))
        assertFalse(state.shouldLoadMoreBanners(lastVisibleIssueIndex = 6))
        assertTrue(state.shouldLoadMoreBanners(lastVisibleIssueIndex = 7))
    }

    @Test
    fun `more banners do not load when another load cannot start`() {
        val state = state(bannerCount = 10, bannerHasNext = true)

        assertFalse(state.copy(bannerHasNext = false).shouldLoadMoreBanners(9))
        assertFalse(state.copy(isLoadingMoreBanners = true).shouldLoadMoreBanners(9))
        assertFalse(state.copy(bannerLoadMoreError = true).shouldLoadMoreBanners(9))
        assertFalse(state.copy(isRefreshing = true).shouldLoadMoreBanners(9))
        assertFalse(state.copy(bannerStatus = HomeSectionStatus.Loading).shouldLoadMoreBanners(9))
    }

    private fun state(
        bannerCount: Int = 0,
        bannerStatus: HomeSectionStatus = HomeSectionStatus.Ready,
        bannerHasNext: Boolean = false,
    ) = HomeUiState(
        date = LocalDate.of(2026, 8, 3),
        banners = List(bannerCount) { index ->
            Banner(
                id = "banner-$index",
                title = "이슈 $index",
                subtitle = "부제",
                webViewUrl = "https://example.com/issue",
                thumbnailUrl = "https://example.com/banner.jpg",
                createdAt = "2026-10-20T08:08:11.001Z",
            )
        },
        bannerStatus = bannerStatus,
        bannerHasNext = bannerHasNext,
    )
}
