package com.stonefive.chalkak.domain.model

enum class FeedbackSubmissionFailure {
    INVALID_CONTENT,
    UNAUTHORIZED,
    NETWORK,
    UNKNOWN,
}

/**
 * 문의 접수 실패.
 * [serverMessage]는 400 응답의 `message`다(예: 접수 제한 안내). 없으면 null이고, 화면은 기본 문구를 쓴다.
 */
class FeedbackSubmissionException(
    val reason: FeedbackSubmissionFailure,
    val serverMessage: String? = null,
) : Exception()
