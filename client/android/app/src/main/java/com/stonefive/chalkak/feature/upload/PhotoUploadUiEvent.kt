package com.stonefive.chalkak.feature.upload

sealed interface PhotoUploadUiEvent {
    data object NavigateBack : PhotoUploadUiEvent

    data object OpenGallery : PhotoUploadUiEvent

    data object OpenCamera : PhotoUploadUiEvent

    data class NavigateToSuccess(
        val imageModel: String,
        val caption: String,
        val content: PhotoUploadSuccessContent,
    ) : PhotoUploadUiEvent
}
