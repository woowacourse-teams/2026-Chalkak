package com.stonefive.chalkak.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stonefive.chalkak.ChalkakApplication
import com.stonefive.chalkak.domain.repository.HomeRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(private val repository: HomeRepository) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _uiEvent = Channel<HomeUiEvent>(Channel.BUFFERED)
    val uiEvent = _uiEvent.receiveAsFlow()

    init {
        loadHome()
    }

    fun onAction(action: HomeUiAction) {
        when (action) {
            HomeUiAction.AddClicked -> sendUiEvent(HomeUiEvent.OpenPhotoUpload)

            is HomeUiAction.BottomBarSelected -> {
                _uiState.update { it.reduce(action) }
                sendUiEvent(HomeUiEvent.NavigateToBottomBar(action.item))
            }

            is HomeUiAction.SortSelected -> {
                _uiState.update { it.reduce(action) }
                loadHome()
            }

            is HomeUiAction.LikeClicked -> updateLike(action.photoId)

            is HomeUiAction.StoryClicked -> _uiState.update { it.reduce(action) }
        }
    }

    private fun loadHome() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            runCatching { repository.getHome(_uiState.value.selectedSort) }
                .onSuccess { content ->
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            dateLabel = content.dateLabel,
                            topic = content.topic,
                            photos = content.photos,
                            likedPhotoIds = content.likedPhotoIds,
                        )
                    }
                }.onFailure {
                    _uiState.update { it.copy(isLoading = false) }
                    _uiEvent.send(HomeUiEvent.LoadFailed)
                }
        }
    }

    private fun updateLike(photoId: String) {
        val previousState = _uiState.value
        if (previousState.photos.none { it.id == photoId }) return
        val nextState = previousState.reduce(HomeUiAction.LikeClicked(photoId))
        _uiState.value = nextState

        viewModelScope.launch {
            runCatching {
                repository.updateLike(
                    photoId = photoId,
                    isLiked = photoId in nextState.likedPhotoIds,
                )
            }.onSuccess { likeCount ->
                _uiState.update { state ->
                    state.copy(
                        photos = state.photos.map { photo ->
                            if (photo.id == photoId) photo.copy(likeCount = likeCount) else photo
                        },
                    )
                }
            }.onFailure {
                _uiState.value = previousState
                _uiEvent.send(HomeUiEvent.LikeUpdateFailed)
            }
        }
    }

    private fun sendUiEvent(event: HomeUiEvent) {
        viewModelScope.launch { _uiEvent.send(event) }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as ChalkakApplication
                HomeViewModel(
                    repository = application.appContainer.homeRepository,
                )
            }
        }
    }
}
