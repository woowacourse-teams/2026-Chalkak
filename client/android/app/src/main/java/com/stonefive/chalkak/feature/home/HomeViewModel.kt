package com.stonefive.chalkak.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stonefive.chalkak.ChalkakApplication
import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBarItem
import com.stonefive.chalkak.domain.model.PostSort
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

    private var latestLoadGeneration = 0
    private var homeContentRevision = 0
    private val latestLikeGenerationByPhotoId = mutableMapOf<String, Int>()

    init {
        loadHome(PostSort.LATEST)
    }

    fun onAction(action: HomeUiAction) {
        when (action) {
            HomeUiAction.AddClicked -> sendUiEvent(HomeUiEvent.OpenPhotoUpload)

            is HomeUiAction.BottomBarSelected -> {
                if (action.item == ChalkakBottomBarItem.TODAY) {
                    onHomeSelected()
                } else {
                    sendUiEvent(HomeUiEvent.NavigateToBottomBar(action.item))
                }
            }

            is HomeUiAction.LikeClicked -> updateLike(action.photoId)
        }
    }

    fun onHomeSelected() {
        loadHome(PostSort.RANDOM)
    }

    private fun loadHome(requestedSort: PostSort) {
        _uiState.update { it.copy(selectedSort = requestedSort) }
        val generation = ++latestLoadGeneration

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            runCatching { repository.getHome(requestedSort) }
                .onSuccess { content ->
                    if (generation != latestLoadGeneration) return@onSuccess

                    homeContentRevision++
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
                    if (generation != latestLoadGeneration) return@onFailure

                    _uiState.update { it.copy(isLoading = false) }
                }
        }
    }

    private fun updateLike(photoId: String) {
        val previousState = _uiState.value
        val previousPhoto = previousState.photos.find { it.id == photoId } ?: return
        val wasLiked = photoId in previousState.likedPhotoIds
        val liked = !wasLiked
        val nextState = previousState.copy(
            photos = previousState.photos.map { photo ->
                if (photo.id == photoId) {
                    photo.copy(likeCount = photo.likeCount + if (liked) 1 else -1)
                } else {
                    photo
                }
            },
            likedPhotoIds = if (liked) {
                previousState.likedPhotoIds + photoId
            } else {
                previousState.likedPhotoIds - photoId
            },
        )
        val generation = latestLikeGenerationByPhotoId.getOrDefault(photoId, 0) + 1
        val requestedHomeContentRevision = homeContentRevision
        latestLikeGenerationByPhotoId[photoId] = generation
        _uiState.value = nextState

        viewModelScope.launch {
            runCatching {
                repository.updateLike(
                    photoId = photoId,
                    isLiked = photoId in nextState.likedPhotoIds,
                )
            }.onSuccess { likeCount ->
                if (
                    latestLikeGenerationByPhotoId[photoId] != generation ||
                    homeContentRevision != requestedHomeContentRevision
                ) {
                    return@onSuccess
                }

                _uiState.update { state ->
                    state.copy(
                        photos = state.photos.map { photo ->
                            if (photo.id == photoId) photo.copy(likeCount = likeCount) else photo
                        },
                    )
                }
            }.onFailure {
                if (
                    latestLikeGenerationByPhotoId[photoId] != generation ||
                    homeContentRevision != requestedHomeContentRevision
                ) {
                    return@onFailure
                }

                _uiState.update { state ->
                    if (state.photos.none { it.id == photoId }) return@update state

                    state.copy(
                        photos = state.photos.map { photo ->
                            if (photo.id == photoId) {
                                photo.copy(likeCount = previousPhoto.likeCount)
                            } else {
                                photo
                            }
                        },
                        likedPhotoIds = if (wasLiked) {
                            state.likedPhotoIds + photoId
                        } else {
                            state.likedPhotoIds - photoId
                        },
                    )
                }
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
