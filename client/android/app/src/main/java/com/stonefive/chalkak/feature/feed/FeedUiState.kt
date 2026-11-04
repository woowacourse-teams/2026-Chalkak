package com.stonefive.chalkak.feature.feed

import com.stonefive.chalkak.core.ui.UiMessage
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.feature.aireview.AiReviewUiState

data class FeedUiState(
    val showSignatureHint: Boolean = false,
    val content: FeedContentState.Success? = null,
    // 오늘 주제의 게시물만 수정할 수 있다(POST-010의 주제 OPEN 기간을 앱에서는 오늘로 본다).
    val isPostEditable: Boolean = false,
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isDeleting: Boolean = false,
    val isSavingPostEdit: Boolean = false,
    val deleteSuccessPostId: String? = null,
    val errorMessage: String? = null,
    val pendingMessage: UiMessage? = null,
    val aiReview: AiReviewUiState = AiReviewUiState(),
    val postEdit: FeedPostEditState? = null,
)

/** 수정 화면이 열려 있는 동안의 입력값. null이면 수정 화면이 닫혀 있다. */
data class FeedPostEditState(
    val title: String,
    val description: String,
)

sealed interface FeedContentState {
    data class Success(
        val dateLabel: String,
        val topic: String,
        val post: Post,
        val isLiked: Boolean,
        val topicDate: java.time.LocalDate? = null,
    ) : FeedContentState
}
