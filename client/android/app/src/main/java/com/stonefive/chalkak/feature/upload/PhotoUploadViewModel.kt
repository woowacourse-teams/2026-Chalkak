package com.stonefive.chalkak.feature.upload

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stonefive.chalkak.ChalkakApplication
import com.stonefive.chalkak.domain.model.PostCreationFailure
import com.stonefive.chalkak.domain.model.PostCreationResult
import com.stonefive.chalkak.domain.repository.PostRepository
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PhotoUploadViewModel(
    private val postRepository: PostRepository,
    private val topicDate: LocalDate,
) : ViewModel() {
    private val _uiState = MutableStateFlow(PhotoUploadUiState())
    val uiState: StateFlow<PhotoUploadUiState> = _uiState.asStateFlow()

    private val _uiEvent = Channel<PhotoUploadUiEvent>(Channel.BUFFERED)
    val uiEvent = _uiEvent.receiveAsFlow()

    fun onAction(action: PhotoUploadUiAction) {
        if (_uiState.value.isSubmitting && action != PhotoUploadUiAction.BackClicked) return

        when (action) {
            PhotoUploadUiAction.BackClicked -> sendUiEvent(PhotoUploadUiEvent.NavigateBack)

            PhotoUploadUiAction.GalleryClicked -> sendUiEvent(PhotoUploadUiEvent.OpenGallery)

            PhotoUploadUiAction.CameraClicked -> sendUiEvent(PhotoUploadUiEvent.OpenCamera)

            is PhotoUploadUiAction.CaptionChanged -> {
                _uiState.update { it.copy(caption = action.caption, errorMessage = null) }
            }

            PhotoUploadUiAction.SubmitClicked -> submit()
        }
    }

    fun onImageSelected(image: String) {
        if (_uiState.value.isSubmitting) return
        _uiState.update { it.copy(selectedImage = image, errorMessage = null) }
    }

    fun reset() {
        _uiState.value = PhotoUploadUiState()
    }

    private fun sendUiEvent(event: PhotoUploadUiEvent) {
        _uiEvent.trySend(event)
    }

    private fun submit() {
        val state = _uiState.value
        val imageUri = state.selectedImage ?: return
        if (state.isSubmitting) return
        val caption = state.caption

        _uiState.update {
            it.copy(
                isSubmitting = true,
                errorMessage = null,
            )
        }

        viewModelScope.launch {
            try {
                when (val result = postRepository.createPost(imageUri, caption, topicDate)) {
                    is PostCreationResult.Success -> {
                        _uiState.update {
                            it.copy(
                                isSubmitting = false,
                                completedSubmission = PhotoUploadSubmission(
                                    imageModel = imageUri,
                                    caption = caption,
                                    content = result.value.toSuccessContent(),
                                ),
                            )
                        }
                    }

                    is PostCreationResult.Failure -> handleFailure(result.reason)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isSubmitting = false,
                        errorMessage = GENERIC_ERROR_MESSAGE,
                    )
                }
            }
        }
    }

    private fun handleFailure(failure: PostCreationFailure) {
        if (failure == PostCreationFailure.ReauthenticationRequired) {
            _uiState.update { it.copy(isSubmitting = false) }
            sendUiEvent(PhotoUploadUiEvent.ReauthenticationRequired)
            return
        }

        _uiState.update {
            it.copy(
                isSubmitting = false,
                errorMessage = failure.toMessage(),
            )
        }
    }

    private fun com.stonefive.chalkak.domain.model.PostCreation.toSuccessContent() = PhotoUploadSuccessContent(
        dateLabel = topicDate.toSuccessDateLabel(),
        topic = topic,
        moderationStatus = moderationStatus.name,
    )

    private fun PostCreationFailure.toMessage(): String = when (this) {
        PostCreationFailure.NetworkUnavailable -> "네트워크 연결을 확인해 주세요."
        PostCreationFailure.ImagePreparationFailed -> "사진을 준비하지 못했어요. 다시 시도해 주세요."
        PostCreationFailure.UploadRejected -> "사진을 업로드하지 못했어요. 다시 시도해 주세요."
        PostCreationFailure.PostCreationRejected -> "전시를 완료하지 못했어요. 다시 시도해 주세요."
        PostCreationFailure.AlreadySubmitted -> "이미 이 주제에 전시한 사진이 있어요."
        PostCreationFailure.TopicNotOpen -> "주제가 변경되어 전시할 수 없어요."
        PostCreationFailure.InvalidResponse -> GENERIC_ERROR_MESSAGE
        PostCreationFailure.ReauthenticationRequired -> error("Handled before message mapping")
    }

    companion object {
        fun factory(topicDate: LocalDate) = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as ChalkakApplication
                PhotoUploadViewModel(
                    postRepository = application.appContainer.postRepository,
                    topicDate = topicDate,
                )
            }
        }

        private const val GENERIC_ERROR_MESSAGE = "전시를 완료하지 못했어요. 다시 시도해 주세요."
    }
}

private fun LocalDate.toSuccessDateLabel(): String = "%04d. %02d. %02d".format(year, monthValue, dayOfMonth)
