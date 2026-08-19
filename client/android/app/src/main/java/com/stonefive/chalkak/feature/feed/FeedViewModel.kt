package com.stonefive.chalkak.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stonefive.chalkak.ChalkakApplication
import com.stonefive.chalkak.domain.model.PostSort
import com.stonefive.chalkak.domain.repository.HomeRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FeedViewModel(
    private val repository: HomeRepository,
    private val initialContent: FeedContentState.Success? = null,
) : ViewModel() {
    private val _uiState = MutableStateFlow(FeedUiState())
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    private var latestLikeGeneration = 0

    init {
        if (initialContent == null) {
            loadFeed()
        } else {
            _uiState.value = FeedUiState(content = initialContent)
        }
    }

    fun onLikeClicked() {
        val content = _uiState.value.content ?: return
        val liked = !content.isLiked
        val previousPost = content.post
        val optimisticPost = previousPost.copy(
            likeCount = previousPost.likeCount + if (liked) 1 else -1,
        )
        val generation = ++latestLikeGeneration

        _uiState.update {
            it.copy(
                content = content.copy(
                    post = optimisticPost,
                    isLiked = liked,
                ),
            )
        }

        // TODO(like): API 연동 시 동일 게시물 좋아요 요청을 직렬화(또는 디바운스)하고
        //  역순 응답을 무시하도록 보강한다. 현재 generation은 UI 롤백만 막고 서버 쓰기 순서는 보장하지 않는다.
        viewModelScope.launch {
            try {
                // 서버 반영만 수행하고 카운트는 낙관적 값을 그대로 유지한다.
                // (Home Mock은 Display 등 다른 소스 게시물의 원래 카운트를 몰라 반환값을 신뢰할 수 없다.)
                repository.updateLike(
                    photoId = previousPost.id,
                    isLiked = liked,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                if (generation != latestLikeGeneration) return@launch

                _uiState.update {
                    it.copy(
                        content = content,
                    )
                }
            }
        }
    }

    private fun loadFeed() {
        viewModelScope.launch {
            try {
                val content = repository.getHome(PostSort.LATEST)
                val post = content.photos.firstOrNull()
                    ?: error("피드 게시물이 없습니다")

                _uiState.value = FeedUiState(
                    content = FeedContentState.Success(
                        dateLabel = content.dateLabel.toFeedDateLabel(),
                        topic = content.topic,
                        post = post,
                        isLiked = post.id in content.likedPhotoIds,
                    ),
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                return@launch
            }
        }
    }

    private fun String.toFeedDateLabel(): String = if (contains(" ·")) {
        "${substringBefore(" ·")}의 주제"
    } else {
        this
    }

    companion object {
        fun factory(initialContent: FeedContentState.Success? = null) = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as ChalkakApplication
                FeedViewModel(
                    repository = application.appContainer.homeRepository,
                    initialContent = initialContent,
                )
            }
        }

        val Factory = factory()
    }
}
