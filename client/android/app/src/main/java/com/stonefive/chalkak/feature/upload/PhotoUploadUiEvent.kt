package com.stonefive.chalkak.feature.upload

sealed interface PhotoUploadUiEvent {
    data object NavigateBack : PhotoUploadUiEvent

    data object OpenGallery : PhotoUploadUiEvent

    data object OpenCamera : PhotoUploadUiEvent

    data object PhotoSubmitted : PhotoUploadUiEvent
}
