package com.stonefive.chalkak.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stonefive.chalkak.ChalkakApplication
import com.stonefive.chalkak.domain.model.HomeQuery
import com.stonefive.chalkak.domain.model.HomeResult
import com.stonefive.chalkak.domain.model.PostSort
import com.stonefive.chalkak.domain.repository.HomeRepository
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FeedViewModel(
    private val repository: HomeRepository,
    private val initialContent: FeedContentState.Success? = null,
    private val dateProvider: () -> LocalDate = { LocalDate.now(KST) },
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
            val result = try {
                repository.updateLike(
                    photoId = previousPost.id,
                    isLiked = liked,
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                rollbackLike(generation, content)
                return@launch
            }
            if (result is HomeResult.Failure) {
                rollbackLike(generation, content)
            }
        }
    }

    private fun rollbackLike(
        generation: Int,
        content: FeedContentState.Success,
    ) {
        if (generation != latestLikeGeneration) return
        _uiState.update { it.copy(content = content) }
    }

    private fun loadFeed() {
        viewModelScope.launch {
            val result = try {
                repository.getHome(
                    HomeQuery(
                        date = dateProvider(),
                        sort = PostSort.LATEST,
                        page = HomeQuery.FIRST_PAGE,
                    ),
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                return@launch
            }
            val content = (result as? HomeResult.Success)?.value ?: return@launch
            val post = content.photos.firstOrNull() ?: return@launch

            _uiState.value = FeedUiState(
                content = FeedContentState.Success(
                    dateLabel = content.topicDate.toFeedDateLabel(),
                    topic = content.topic,
                    post = post,
                    isLiked = post.id in content.likedPhotoIds,
                ),
            )
        }
    }

    private fun LocalDate.toFeedDateLabel(): String = "${monthValue}월 ${dayOfMonth}일의 주제"

    companion object {
        fun factory(initialContent: FeedContentState.Success? = null) = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as ChalkakApplication
                FeedViewModel(
                    repository = application.appContainer.feedRepository,
                    initialContent = initialContent,
                )
            }
        }

        val Factory = factory()

        private val KST: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
