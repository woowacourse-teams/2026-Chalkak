package com.stonefive.chalkak.feature.home

import com.stonefive.chalkak.domain.model.AiReviewContent
import com.stonefive.chalkak.domain.model.Banner
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.domain.model.PostCalendarItem
import com.stonefive.chalkak.feature.aireview.AiReviewPhase
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters

data class HomeUiState(
    val date: LocalDate,
    val topic: String = "",
    val topicStatus: HomeSectionStatus = HomeSectionStatus.Loading,
    val hero: HomeHeroState = HomeHeroState.Loading,
    val week: List<HomeWeekDay> = homeWeekDates(date).map(::HomeWeekDay),
    val recordsStatus: HomeSectionStatus = HomeSectionStatus.Loading,
    val yesterday: HomeRankingState = HomeRankingState(),
    val trending: HomeRankingState = HomeRankingState(),
    val banners: List<Banner> = emptyList(),
    val bannerStatus: HomeSectionStatus = HomeSectionStatus.Loading,
    val bannerCurrentPage: Int = 0,
    val bannerHasNext: Boolean = false,
    val isLoadingMoreBanners: Boolean = false,
    val bannerLoadMoreError: Boolean = false,
    val isRefreshing: Boolean = false,
    val isAuthenticated: Boolean = false,
    val feedbackPhase: HomeFeedbackPhase = HomeFeedbackPhase.Hidden,
    val feedbackResult: AiReviewContent? = null,
    val feedbackMessage: String? = null,
)

val HomeUiState.isInitialLoadComplete: Boolean
    get() = topicStatus != HomeSectionStatus.Loading &&
        hero != HomeHeroState.Loading &&
        recordsStatus != HomeSectionStatus.Loading &&
        yesterday.status != HomeSectionStatus.Loading &&
        trending.status != HomeSectionStatus.Loading

// 피드백 카드는 오늘 올린 사진이 검수를 통과(승인)했을 때만 보인다. 검수 대기 사진과 사진이 없는 상태에서는 숨긴다.
val HomeUiState.showsFeedback: Boolean
    get() = (hero as? HomeHeroState.Photo)?.isPending == false && feedbackPhase != HomeFeedbackPhase.Hidden

// FAILED 및 조회 전 상태는 별도 UI를 추가하지 않고 카드를 숨긴다.
typealias HomeFeedbackPhase = AiReviewPhase

enum class HomeSectionStatus {
    Loading,
    Ready,
    Error,
}

sealed interface HomeHeroState {
    data object Loading : HomeHeroState

    data object Empty : HomeHeroState

    data object NoOpenTopic : HomeHeroState

    data object Processing : HomeHeroState

    data object Error : HomeHeroState

    data class Photo(
        val imageUrl: String,
        val thumbnailUrl: String = imageUrl,
        val signatureUrl: String? = null,
        val signatureThumbnailUrl: String? = null,
        val isPending: Boolean = false,
        val postId: String? = null,
    ) : HomeHeroState
}

data class HomeWeekDay(
    val date: LocalDate,
    val post: PostCalendarItem? = null,
)

data class HomeRankingState(
    val status: HomeSectionStatus = HomeSectionStatus.Loading,
    val photos: List<Post> = emptyList(),
)

fun homeWeekDates(date: LocalDate): List<LocalDate> {
    val sunday = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
    return List(7) { sunday.plusDays(it.toLong()) }
}
