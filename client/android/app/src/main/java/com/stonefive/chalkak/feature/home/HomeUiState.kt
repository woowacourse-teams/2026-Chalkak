package com.stonefive.chalkak.feature.home

import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.domain.model.PostCalendarItem
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
    val isRefreshing: Boolean = false,
    val isAuthenticated: Boolean = false,
)

enum class HomeSectionStatus {
    Loading,
    Ready,
    Error,
}

sealed interface HomeHeroState {
    data object Loading : HomeHeroState

    data object Empty : HomeHeroState

    data object Processing : HomeHeroState

    data object Error : HomeHeroState

    data class Photo(
        val imageUrl: String,
        val thumbnailUrl: String = imageUrl,
        val signatureUrl: String? = null,
        val signatureThumbnailUrl: String? = null,
        val isPending: Boolean = false,
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
