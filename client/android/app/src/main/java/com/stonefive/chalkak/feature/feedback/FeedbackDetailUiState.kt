package com.stonefive.chalkak.feature.feedback

import com.stonefive.chalkak.core.ui.UiMessage

/** 문의 상세 화면이 지금 어떤 결과에 있는지. Route가 [NotFound]·[Deleted]를 보고 목록으로 돌아간다. */
sealed interface FeedbackDetailStatus {
    data object Loading : FeedbackDetailStatus

    /** 문의를 불러왔다. 내용은 [FeedbackDetailUiState.detail]에 있다. */
    data object Content : FeedbackDetailStatus

    /** 상세·삭제에서 404. 타인·삭제·없는 문의를 구분하지 않는다. [message]를 안내한 뒤 목록을 새로고침한다. */
    data class NotFound(val message: String) : FeedbackDetailStatus

    /** 404·401이 아닌 이유로 상세를 불러오지 못했다. 다시 시도 버튼을 보여 준다. */
    data object Failed : FeedbackDetailStatus

    /** 삭제 204를 받았다. 목록으로 돌아가 새로고침한다. */
    data object Deleted : FeedbackDetailStatus

    /** 401. 로그인 화면으로 보낸다. */
    data object ReauthenticationRequired : FeedbackDetailStatus
}

/**
 * 문의 상세 화면 상태.
 *
 * @property isDeleting 삭제 요청이 응답을 기다리는 중. 삭제 버튼을 막는다.
 * @property pendingMessage 삭제가 404·401이 아닌 이유로 실패했을 때의 토스트.
 */
data class FeedbackDetailUiState(
    val status: FeedbackDetailStatus = FeedbackDetailStatus.Loading,
    val detail: FeedbackDetailContentUiState? = null,
    val isDeleting: Boolean = false,
    val pendingMessage: UiMessage? = null,
)

/**
 * 화면에 그릴 문의 한 건. 시각은 "M월 d일 HH:mm"(Asia/Seoul) 문자열이다.
 * 답변이 없으면 [answerContent]·[answeredAtText]가 둘 다 null이다.
 */
data class FeedbackDetailContentUiState(
    val content: String,
    val createdAtText: String,
    val answerContent: String? = null,
    val answeredAtText: String? = null,
)
