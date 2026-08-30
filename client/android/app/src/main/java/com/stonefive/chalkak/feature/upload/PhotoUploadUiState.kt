package com.stonefive.chalkak.feature.upload

import androidx.compose.runtime.Immutable

@Immutable
data class PhotoUploadUiState(
    val selectedImage: String? = null,
    val caption: String = "",
    val isCameraAvailable: Boolean = true,
    val isTopicLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val completedSubmission: PhotoUploadSubmission? = null,
) {
    val canSubmit: Boolean
        get() = selectedImage != null && !isTopicLoading && !isSubmitting && completedSubmission == null
}

@Immutable
data class PhotoUploadSuccessContent(
    val dateLabel: String,
    val topic: String,
    val moderationStatus: String,
)
