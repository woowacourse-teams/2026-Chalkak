package com.stonefive.chalkak.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stonefive.chalkak.ChalkakApplication
import com.stonefive.chalkak.domain.model.HomeFailure
import com.stonefive.chalkak.domain.model.HomeQuery
import com.stonefive.chalkak.domain.model.HomeResult
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.domain.model.PostDetail
import com.stonefive.chalkak.domain.model.PostSort
import com.stonefive.chalkak.domain.repository.PostRepository
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FeedViewModel(
    private val repository: PostRepository,
    private val initialContent: FeedContentState.Success? = null,
    private val postId: String? = initialContent?.post?.id,
    private val isOwnedByCurrentUser: Boolean = initialContent?.post?.isOwnedByCurrentUser == true,
    private val dateProvider: () -> LocalDate = { LocalDate.now(KST) },
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        FeedUiState(
            content = initialContent,
            isLoading = initialContent == null,
            isRefreshing = initialContent != null && postId != null,
        ),
    )
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    private var latestLikeGeneration = 0

    init {
        if (postId != null) {
            loadPostDetail(postId)
        } else if (initialContent == null) {
            loadFeed()
        }
    }

    fun retry() {
        if (postId != null) {
            loadPostDetail(postId)
        } else {
            loadFeed()
        }
    }

    fun onLikeClicked() {
        val content = _uiState.value.content ?: return
        val liked = !content.isLiked
        val previousPost = content.post
        val optimisticPost = previousPost.copy(
            likeCount = previousPost.likeCount + if (liked) 1 else -1,
            isLiked = liked,
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
            when (result) {
                is HomeResult.Success -> if (generation == latestLikeGeneration) {
                    _uiState.update { state ->
                        val currentContent = state.content ?: return@update state
                        state.copy(
                            content = currentContent.copy(
                                post = currentContent.post.copy(
                                    likeCount = result.value.likeCount,
                                    isLiked = result.value.isLiked,
                                ),
                                isLiked = result.value.isLiked,
                            ),
                        )
                    }
                }

                is HomeResult.Failure -> rollbackLike(generation, content)
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
        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val result = try {
                repository.getPostContent(
                    HomeQuery(
                        date = dateProvider(),
                        sort = PostSort.LATEST,
                        page = HomeQuery.FIRST_PAGE,
                    ),
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "피드를 불러오지 못했어요",
                    )
                }
                return@launch
            }
            val content = (result as? HomeResult.Success)?.value
            val post = content?.photos?.firstOrNull()
            if (content == null || post == null) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = "피드를 불러오지 못했어요",
                    )
                }
                return@launch
            }

            _uiState.value = FeedUiState(
                isLoading = false,
                content = FeedContentState.Success(
                    dateLabel = content.topicDate.toFeedDateLabel(),
                    topic = content.topic,
                    post = post,
                    isLiked = post.isLiked,
                ),
            )
        }
    }

    private fun loadPostDetail(postId: String) {
        val likeGenerationAtRequestStart = latestLikeGeneration
        _uiState.update {
            it.copy(
                isLoading = it.content == null,
                isRefreshing = it.content != null,
                errorMessage = null,
            )
        }
        viewModelScope.launch {
            val result = try {
                repository.getPostDetail(postId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                HomeResult.Failure(HomeFailure.Network)
            }

            when (result) {
                is HomeResult.Success -> applyPostDetail(result.value, likeGenerationAtRequestStart)

                is HomeResult.Failure -> _uiState.update {
                    it.copy(
                        content = if (result.reason.isPostNotFound()) null else it.content,
                        isLoading = false,
                        isRefreshing = false,
                        errorMessage = result.reason.toFeedErrorMessage(),
                    )
                }
            }
        }
    }

    private fun applyPostDetail(
        detail: PostDetail,
        likeGenerationAtRequestStart: Int,
    ) {
        val previousContent = _uiState.value.content
        val previousPost = previousContent?.post
        val shouldPreserveCurrentLike =
            previousPost != null && likeGenerationAtRequestStart != latestLikeGeneration
        val currentLikePost = previousPost?.takeIf { shouldPreserveCurrentLike }
        val updatedPost = detail.post.copy(
            likeCount = currentLikePost?.likeCount ?: detail.post.likeCount,
            isLiked = currentLikePost?.isLiked ?: detail.post.isLiked,
            signatureThumbnailImageUrl = detail.post.signatureThumbnailImageUrl
                ?: previousPost?.signatureThumbnailImageUrl,
            submittedAt = detail.post.submittedAt ?: previousPost?.submittedAt,
            isOwnedByCurrentUser = previousPost?.isOwnedByCurrentUser ?: isOwnedByCurrentUser,
        )
        val updatedContent = FeedContentState.Success(
            dateLabel = detail.topicDate.toFeedDateLabel(),
            topic = detail.topic,
            post = updatedPost,
            isLiked = updatedPost.isLiked,
        )
        _uiState.update { state ->
            state.copy(
                content = if (state.content == updatedContent) state.content else updatedContent,
                isLoading = false,
                isRefreshing = false,
                errorMessage = null,
            )
        }
    }

    private fun HomeFailure.toFeedErrorMessage(): String = when (this) {
        is HomeFailure.Http -> if (statusCode == 404) {
            "게시물을 찾을 수 없어요"
        } else {
            "게시물을 불러오지 못했어요"
        }

        HomeFailure.InvalidResponse -> "게시물 정보를 불러오지 못했어요"

        else -> "게시물을 불러오지 못했어요"
    }

    private fun HomeFailure.isPostNotFound(): Boolean = this is HomeFailure.Http && statusCode == 404

    private fun LocalDate.toFeedDateLabel(): String = "${monthValue}월 ${dayOfMonth}일의 주제"

    companion object {
        fun factory(
            postId: String? = null,
            initialContent: FeedContentState.Success? = null,
            isOwnedByCurrentUser: Boolean = initialContent?.post?.isOwnedByCurrentUser == true,
        ) = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as ChalkakApplication
                FeedViewModel(
                    repository = application.appContainer.postRepository,
                    initialContent = initialContent,
                    postId = postId,
                    isOwnedByCurrentUser = isOwnedByCurrentUser,
                )
            }
        }

        val Factory = factory()

        private val KST: ZoneId = ZoneId.of("Asia/Seoul")
    }
}
