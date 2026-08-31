package com.stonefive.chalkak.feature.home

import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.domain.model.PostSort
import java.time.LocalDate

data class HomeUiState(
    val contentStatus: HomeContentStatus = HomeContentStatus.Loading,
    val topicDate: LocalDate? = null,
    val topic: String = "",
    val photos: List<Post> = emptyList(),
    val selectedSort: PostSort = PostSort.LATEST,
    val likedPhotoIds: Set<String> = emptySet(),
    val currentPage: Int = 0,
    val hasNext: Boolean = false,
    val randomSeed: String? = null,
    val isLoadingNext: Boolean = false,
    val isRefreshing: Boolean = false,
    val areLikesEnabled: Boolean = true,
)

sealed interface HomeContentStatus {
    data object Loading : HomeContentStatus

    data class Error(val reason: HomeInitialError) : HomeContentStatus

    data object Content : HomeContentStatus
}

enum class HomeInitialError {
    TopicNotFound,
    Unauthorized,
    Network,
    InvalidResponse,
    Client,
    Server,
    Generic,
}
