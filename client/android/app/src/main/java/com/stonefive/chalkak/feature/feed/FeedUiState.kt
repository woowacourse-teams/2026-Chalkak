package com.stonefive.chalkak.feature.feed

import com.stonefive.chalkak.domain.model.Post

data class FeedUiState(val content: FeedContentState = FeedContentState.Loading)

sealed interface FeedContentState {
    data object Loading : FeedContentState

    data class Success(
        val dateLabel: String,
        val topic: String,
        val post: Post,
        val isLiked: Boolean,
    ) : FeedContentState

    data class Error(val message: String) : FeedContentState
}
