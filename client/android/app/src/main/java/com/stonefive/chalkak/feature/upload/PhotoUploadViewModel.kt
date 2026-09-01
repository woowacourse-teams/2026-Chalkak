package com.stonefive.chalkak.feature.upload

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stonefive.chalkak.ChalkakApplication
import com.stonefive.chalkak.core.ui.UiMessage
import com.stonefive.chalkak.domain.model.PostCreationFailure
import com.stonefive.chalkak.domain.model.PostCreationResult
import com.stonefive.chalkak.domain.model.PostCreationTopicResult
import com.stonefive.chalkak.domain.model.PostImagePreparation
import com.stonefive.chalkak.domain.model.PostImagePreparationResult
import com.stonefive.chalkak.domain.model.Topic
import com.stonefive.chalkak.domain.repository.PostCreationRepository
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PhotoUploadViewModel(
    private val postCreationRepository: PostCreationRepository,
    private val topicDate: LocalDate,
) : ViewModel() {
    private var creationTopic: Topic? = null
    private var preparedImage: PostImagePreparation? = null
    private var imageGeneration = 0L
    private var preparationJob: Job? = null
    private var submissionJob: Job? = null
    private var nextMessageId = 0L
    private val _uiState = MutableStateFlow(
        PhotoUploadUiState(
            topicTitle = postCreationRepository.getCachedCreationTopic(topicDate)?.title,
        ),
    )
    val uiState: StateFlow<PhotoUploadUiState> = _uiState.asStateFlow()

    private val _uiEvent = Channel<PhotoUploadUiEvent>(Channel.BUFFERED)
    val uiEvent = _uiEvent.receiveAsFlow()

    init {
        loadCreationTopic()
    }

    fun onAction(action: PhotoUploadUiAction) {
        if (_uiState.value.isSubmitting && action != PhotoUploadUiAction.BackClicked) return

        when (action) {
            PhotoUploadUiAction.BackClicked -> sendUiEvent(PhotoUploadUiEvent.NavigateBack)

            PhotoUploadUiAction.GalleryClicked -> sendUiEvent(PhotoUploadUiEvent.OpenGallery)

            PhotoUploadUiAction.CameraClicked -> sendUiEvent(PhotoUploadUiEvent.OpenCamera)

            is PhotoUploadUiAction.CaptionChanged -> {
                _uiState.update { it.copy(caption = action.caption) }
            }

            PhotoUploadUiAction.SubmitClicked -> submit()
        }
    }

    fun onImageSelected(image: String) {
        if (_uiState.value.isSubmitting) return
        replaceSelectedImage(image)
    }

    fun retryTopicLoad() {
        loadCreationTopic()
    }

    fun onMessageShown(messageId: Long) {
        _uiState.update { state ->
            if (state.pendingMessage?.id == messageId) {
                state.copy(pendingMessage = null)
            } else {
                state
            }
        }
    }

    fun reset() {
        val topicTitle = _uiState.value.topicTitle
        clearImageWork()
        _uiState.value = PhotoUploadUiState(topicTitle = topicTitle)
    }

    override fun onCleared() {
        clearImageWork()
        super.onCleared()
    }

    private fun sendUiEvent(event: PhotoUploadUiEvent) {
        _uiEvent.trySend(event)
    }

    private fun submit() {
        val state = _uiState.value
        if (!state.canSubmit) return
        val imageUri = state.selectedImage ?: return
        val topic = creationTopic
        if (topic == null) {
            loadCreationTopic(submitAfterLoad = true)
            return
        }
        val caption = state.caption

        _uiState.update {
            it.copy(
                isSubmitting = true,
            )
        }

        preparedImage?.let { preparation ->
            submitPreparedImage(preparation, caption, topic)
            return
        }
        if (preparationJob?.isActive != true) {
            startImagePreparation(imageUri, imageGeneration)
        }
    }

    private fun replaceSelectedImage(image: String) {
        imageGeneration++
        preparationJob?.cancel()
        discardPreparedImage()
        _uiState.update {
            it.copy(
                selectedImage = image,
                imagePreparationStatus = ImagePreparationStatus.Preparing,
            )
        }
        startImagePreparation(image, imageGeneration)
    }

    private fun startImagePreparation(
        imageUri: String,
        generation: Long,
    ) {
        _uiState.update {
            it.copy(
                imagePreparationStatus = ImagePreparationStatus.Preparing,
            )
        }
        preparationJob = viewModelScope.launch {
            try {
                when (val result = postCreationRepository.prepareImage(imageUri)) {
                    is PostImagePreparationResult.Success -> {
                        if (generation != imageGeneration || _uiState.value.selectedImage != imageUri) {
                            postCreationRepository.discardPreparedImage(result.value)
                            return@launch
                        }
                        preparedImage = result.value
                        _uiState.update {
                            it.copy(imagePreparationStatus = ImagePreparationStatus.Ready)
                        }
                        if (_uiState.value.isSubmitting) {
                            val topic = creationTopic
                            if (topic == null) {
                                loadCreationTopic(submitAfterLoad = true)
                            } else {
                                submitPreparedImage(result.value, _uiState.value.caption, topic)
                            }
                        }
                    }

                    is PostImagePreparationResult.Failure -> {
                        if (generation == imageGeneration) {
                            _uiState.update {
                                it.copy(imagePreparationStatus = ImagePreparationStatus.Failed)
                            }
                            handleTransientFailure(result.reason)
                        }
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (generation == imageGeneration) {
                    val pendingMessage = nextToast(GENERIC_ERROR_MESSAGE)
                    _uiState.update {
                        it.copy(
                            imagePreparationStatus = ImagePreparationStatus.Failed,
                            isSubmitting = false,
                            pendingMessage = pendingMessage,
                        )
                    }
                }
            }
        }
    }

    private fun submitPreparedImage(
        preparation: PostImagePreparation,
        caption: String,
        topic: Topic,
    ) {
        if (preparedImage != preparation || submissionJob?.isActive == true) return
        val imageUri = _uiState.value.selectedImage ?: return
        preparedImage = null
        submissionJob = viewModelScope.launch {
            try {
                when (val result = postCreationRepository.createPost(preparation, caption, topic)) {
                    is PostCreationResult.Success -> {
                        _uiState.update {
                            it.copy(
                                imagePreparationStatus = ImagePreparationStatus.Idle,
                                isSubmitting = false,
                                completedSubmission = PhotoUploadSubmission(
                                    imageModel = imageUri,
                                    caption = caption,
                                    content = result.value.toSuccessContent(),
                                ),
                            )
                        }
                    }

                    is PostCreationResult.Failure -> {
                        _uiState.update {
                            it.copy(imagePreparationStatus = ImagePreparationStatus.Failed)
                        }
                        handleTransientFailure(result.reason)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                val pendingMessage = nextToast(GENERIC_ERROR_MESSAGE)
                _uiState.update {
                    it.copy(
                        imagePreparationStatus = ImagePreparationStatus.Failed,
                        isSubmitting = false,
                        pendingMessage = pendingMessage,
                    )
                }
            }
        }
    }

    private fun discardPreparedImage() {
        preparedImage?.let(postCreationRepository::discardPreparedImage)
        preparedImage = null
    }

    private fun clearImageWork() {
        imageGeneration++
        preparationJob?.cancel()
        preparationJob = null
        submissionJob?.cancel()
        submissionJob = null
        discardPreparedImage()
    }

    private fun loadCreationTopic(submitAfterLoad: Boolean = false) {
        if (_uiState.value.isTopicLoading) return
        _uiState.update { it.copy(isTopicLoading = true, topicErrorMessage = null) }

        viewModelScope.launch {
            try {
                when (val result = postCreationRepository.getCreationTopic(topicDate)) {
                    is PostCreationTopicResult.Success -> {
                        creationTopic = result.value
                        _uiState.update { state ->
                            state.copy(
                                isTopicLoading = false,
                                topicErrorMessage = null,
                                topicTitle = if (state.topicTitle == result.value.title) {
                                    state.topicTitle
                                } else {
                                    result.value.title
                                },
                            )
                        }
                        if (submitAfterLoad) submit()
                    }

                    is PostCreationTopicResult.Failure -> handleTopicFailure(result.reason)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isTopicLoading = false,
                        topicErrorMessage = GENERIC_ERROR_MESSAGE,
                    )
                }
            }
        }
    }

    private fun handleTransientFailure(failure: PostCreationFailure) {
        if (failure == PostCreationFailure.ReauthenticationRequired) {
            _uiState.update { it.copy(isSubmitting = false) }
            sendUiEvent(PhotoUploadUiEvent.ReauthenticationRequired)
            return
        }

        val pendingMessage = nextToast(failure.toMessage())
        _uiState.update {
            it.copy(
                isSubmitting = false,
                pendingMessage = pendingMessage,
            )
        }
    }

    private fun handleTopicFailure(failure: PostCreationFailure) {
        if (failure == PostCreationFailure.ReauthenticationRequired) {
            _uiState.update { it.copy(isTopicLoading = false, isSubmitting = false) }
            sendUiEvent(PhotoUploadUiEvent.ReauthenticationRequired)
            return
        }

        _uiState.update {
            it.copy(
                isTopicLoading = false,
                isSubmitting = false,
                topicErrorMessage = failure.toMessage(),
            )
        }
    }

    private fun com.stonefive.chalkak.domain.model.PostCreation.toSuccessContent() = PhotoUploadSuccessContent(
        date = topic.date,
        topic = topic.title,
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
                    postCreationRepository = application.appContainer.postCreationRepository,
                    topicDate = topicDate,
                )
            }
        }

        private const val GENERIC_ERROR_MESSAGE = "전시를 완료하지 못했어요. 다시 시도해 주세요."
    }

    private fun nextToast(text: String): UiMessage.Toast = UiMessage.Toast(
        id = nextMessageId++,
        text = text,
    )
}
