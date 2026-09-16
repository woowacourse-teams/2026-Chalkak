package com.stonefive.chalkak.feature.feedback

import com.stonefive.chalkak.core.ui.UiMessage

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
            normalizedContent.length <= FEEDBACK_MAX_CONTENT_LENGTH
}
