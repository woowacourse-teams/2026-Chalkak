package com.stonefive.chalkak.feature.feed

import com.stonefive.chalkak.domain.model.Post

data class FeedUiState(val content: FeedContentState.Success? = null)

sealed interface FeedContentState {
    data class Success(
        val dateLabel: String,
        val topic: String,
        val post: Post,
        val isLiked: Boolean,
    ) : FeedContentState
}
