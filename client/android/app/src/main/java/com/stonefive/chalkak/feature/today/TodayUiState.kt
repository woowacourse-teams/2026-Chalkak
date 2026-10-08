package com.stonefive.chalkak.feature.today

import com.stonefive.chalkak.core.ui.UiMessage
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.domain.model.PostSort
import java.time.LocalDate

data class TodayUiState(
    val contentStatus: TodayContentStatus = TodayContentStatus.Loading,
    val topicDate: LocalDate? = null,
    val topic: String = "",
    val photos: List<Post> = emptyList(),
    val contentRevision: Int = 0,
    val selectedSort: PostSort = PostSort.LATEST,
    val likedPhotoIds: Set<String> = emptySet(),
    val currentPage: Int = 0,
    val hasNext: Boolean = false,
    val randomSeed: String? = null,
    val isLoadingNext: Boolean = false,
    val isRefreshing: Boolean = false,
    val areLikesEnabled: Boolean = true,
    val pendingMessage: UiMessage? = null,
)

sealed interface TodayContentStatus {
    data object Loading : TodayContentStatus

    data class Error(val reason: TodayInitialError) : TodayContentStatus

    data object Content : TodayContentStatus
}

enum class TodayInitialError {
    TopicNotFound,
    Unauthorized,
    Network,
    InvalidResponse,
    Client,
    Server,
    Generic,
}
