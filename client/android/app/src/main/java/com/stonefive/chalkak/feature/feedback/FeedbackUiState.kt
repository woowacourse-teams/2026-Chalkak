package com.stonefive.chalkak.feature.feedback

import com.stonefive.chalkak.core.ui.UiMessage

/** 문의 내용 최대 길이. 서버와 같이 Unicode code point로 센다(이모지 1개 = 1자). */
const val FEEDBACK_MAX_CONTENT_LENGTH = 1_000

data class FeedbackUiState(
    val content: String = "",
    val isSubmitting: Boolean = false,
    val pendingMessage: UiMessage? = null,
) {
    private val normalizedContent: String
        get() = content.trim()

    val canSubmit: Boolean
        get() = !isSubmitting &&
            normalizedContent.isNotEmpty() &&
            normalizedContent.codePointCount(0, normalizedContent.length) <= FEEDBACK_MAX_CONTENT_LENGTH
}
