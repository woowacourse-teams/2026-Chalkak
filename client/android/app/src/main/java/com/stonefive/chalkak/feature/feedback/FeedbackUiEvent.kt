package com.stonefive.chalkak.feature.feedback

sealed interface FeedbackUiEvent {
    data object Submitted : FeedbackUiEvent

    data object ReauthenticationRequired : FeedbackUiEvent
}
