package com.stonefive.chalkak.feature.upload

import androidx.compose.runtime.Immutable

@Immutable
data class PhotoUploadUiState(
    val selectedImage: String? = null,
    val signatureModel: String? = null,
    val caption: String = "",
    val isCameraAvailable: Boolean = true,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
    val completedSubmission: PhotoUploadSubmission? = null,
) {
    val canSubmit: Boolean
        get() = selectedImage != null && !isSubmitting && completedSubmission == null
}

@Immutable
data class PhotoUploadSuccessContent(
    val dateLabel: String,
    val topic: String,
    val moderationStatus: String,
)
