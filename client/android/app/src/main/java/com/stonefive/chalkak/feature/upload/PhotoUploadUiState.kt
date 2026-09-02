package com.stonefive.chalkak.feature.upload

import androidx.compose.runtime.Immutable
import com.stonefive.chalkak.core.ui.UiMessage
import java.time.LocalDate

@Immutable
data class PhotoUploadUiState(
    val selectedImage: String? = null,
    val imagePreparationStatus: ImagePreparationStatus = ImagePreparationStatus.Idle,
    val caption: String = "",
    val topicTitle: String? = null,
    val isCameraAvailable: Boolean = true,
    val isTopicLoading: Boolean = false,
    val isSubmitting: Boolean = false,
    val topicErrorMessage: String? = null,
    val completedSubmission: PhotoUploadSubmission? = null,
    val pendingMessage: UiMessage? = null,
) {
    val canSubmit: Boolean
        get() = selectedImage != null &&
            !isTopicLoading &&
            !isSubmitting &&
            topicErrorMessage == null &&
            completedSubmission == null
}

enum class ImagePreparationStatus {
    Idle,
    Preparing,
    Ready,
    Failed,
}

@Immutable
data class PhotoUploadSuccessContent(
    val date: LocalDate,
    val topic: String,
    val moderationStatus: String,
)
