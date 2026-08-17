package com.stonefive.chalkak.feature.upload

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PhotoUploadViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(PhotoUploadUiState())
    val uiState: StateFlow<PhotoUploadUiState> = _uiState.asStateFlow()

    private val _uiEvent = Channel<PhotoUploadUiEvent>(Channel.BUFFERED)
    val uiEvent = _uiEvent.receiveAsFlow()

    fun onAction(action: PhotoUploadUiAction) {
        when (action) {
            PhotoUploadUiAction.BackClicked -> sendUiEvent(PhotoUploadUiEvent.NavigateBack)

            PhotoUploadUiAction.GalleryClicked -> sendUiEvent(PhotoUploadUiEvent.OpenGallery)

            PhotoUploadUiAction.CameraClicked -> sendUiEvent(PhotoUploadUiEvent.OpenCamera)

            is PhotoUploadUiAction.CaptionChanged -> {
                _uiState.update { it.copy(caption = action.caption) }
            }

            PhotoUploadUiAction.SubmitClicked -> {
                if (_uiState.value.canSubmit) {
                    sendUiEvent(PhotoUploadUiEvent.PhotoSubmitted)
                }
            }
        }
    }

    fun onImageSelected(image: Uri) {
        _uiState.update { it.copy(selectedImage = image) }
    }

    fun reset() {
        _uiState.value = PhotoUploadUiState()
    }

    private fun sendUiEvent(event: PhotoUploadUiEvent) {
        viewModelScope.launch { _uiEvent.send(event) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { PhotoUploadViewModel() }
        }
    }
}
