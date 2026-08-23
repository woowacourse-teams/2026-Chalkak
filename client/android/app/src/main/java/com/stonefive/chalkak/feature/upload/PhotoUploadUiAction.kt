package com.stonefive.chalkak.feature.upload

sealed interface PhotoUploadUiAction {
    data object BackClicked : PhotoUploadUiAction

    data object GalleryClicked : PhotoUploadUiAction

    data object CameraClicked : PhotoUploadUiAction

    data class CaptionChanged(val caption: String) : PhotoUploadUiAction

    data object SubmitClicked : PhotoUploadUiAction
}
