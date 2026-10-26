package com.stonefive.chalkak.feature.authorposts

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stonefive.chalkak.ChalkakApplication
import com.stonefive.chalkak.core.ui.UiMessage
import com.stonefive.chalkak.domain.model.AuthorPost
import com.stonefive.chalkak.domain.model.HomeFailure
import com.stonefive.chalkak.domain.model.HomeResult
import com.stonefive.chalkak.domain.model.PostLikeUpdate
import com.stonefive.chalkak.domain.repository.PostRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class AuthorPostsViewModel(
    private val repository: PostRepository,
    private val authorId: String,
) : ViewModel() {
    private val _uiState = MutableStateFlow(AuthorPostsUiState())
    val uiState = _uiState.asStateFlow()
    private val deletedPostIds = mutableSetOf<String>()
    private var isNearEnd = false
    private val pendingLikePostIds = mutableSetOf<String>()
    private val latestLikeUpdates = mutableMapOf<String, PostLikeUpdate>()
    private val likeRevisions = mutableMapOf<String, Int>()
    private var nextMessageId = 0L
    private var openedPostId: String? = null
    private var openedPostRefreshJob: Job? = null

    init {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            repository.likeUpdates.collect(::applyLikeUpdate)
        }
        loadPage(1)
    }

    fun retry() {
        val state = _uiState.value
        if (state.isLoading || state.isLoadingNext || state.isAuthorUnavailable || state.isLoginRequired) return
        loadPage(if (state.currentPage == 0) 1 else state.currentPage + 1)
    }

    fun onEndThresholdChanged(isNearEnd: Boolean) {
        this.isNearEnd = isNearEnd
        val state = _uiState.value
        if (!isNearEnd || !state.hasNext || state.hasError || state.isLoading || state.isLoadingNext ||
            state.isAuthorUnavailable || state.isLoginRequired
        ) {
            return
        }
        loadPage(state.currentPage + 1)
    }

    /** 목록에서 사진 상세를 열 때 호출한다. 상세에서 돌아오면 [onResume]이 이 사진의 최신 좋아요를 다시 받는다. */
    fun onPostOpened(postId: String) {
        openedPostId = postId
    }

    /**
     * 화면이 다시 보일 때 호출한다. 상세에서 돌아온 경우 연 사진 하나만 상세 API로 다시 받아 좋아요 수·여부를 갱신한다.
     * 예: 목록 1개 → 다른 사람이 좋아요 → 상세 2개 → 돌아오면 목록도 2개.
     * 다시 받는 사이 좋아요를 누르거나 다른 화면에서 동기화된 값이 있으면 그 값이 더 최신이라 덮어쓰지 않는다.
     */
    fun onResume() {
        val postId = openedPostId ?: return
        openedPostId = null
        if (postId in deletedPostIds) return
        openedPostRefreshJob?.cancel()
        val revisionAtRequest = likeRevisions[postId]
        openedPostRefreshJob = viewModelScope.launch {
            val result = try {
                repository.getPostDetail(postId)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                return@launch
            }
            val post = (result as? HomeResult.Success)?.value?.post ?: return@launch
            if (likeRevisions[postId] != revisionAtRequest || postId in pendingLikePostIds) return@launch
            applyLikeUpdate(PostLikeUpdate(postId, post.likeCount, post.isLiked))
        }
    }

    fun onPostDeleted(postId: String) {
        deletedPostIds += postId
        _uiState.update { it.copy(posts = it.posts.filterNot { post -> post.id == postId }) }
    }

    fun updateLike(postId: String) {
        val state = _uiState.value
        if (state.isAuthorUnavailable || state.isLoginRequired || !pendingLikePostIds.add(postId)) return
        val previousPost = state.posts.find { it.id == postId }
        if (previousPost == null) {
            pendingLikePostIds.remove(postId)
            return
        }
        val isLiked = !previousPost.isLiked
        applyLikeUpdate(
            PostLikeUpdate(
                postId = postId,
                likeCount = (previousPost.likeCount.toLong() + if (isLiked) 1 else -1)
                    .coerceIn(0, Int.MAX_VALUE.toLong())
                    .toInt(),
                isLiked = isLiked,
            ),
        )
        val optimisticRevision = likeRevisions[postId]
        viewModelScope.launch {
            try {
                val result = try {
                    repository.updateLike(postId, isLiked)
                } catch (cancellation: CancellationException) {
                    restoreLike(previousPost, optimisticRevision)
                    throw cancellation
                } catch (_: Exception) {
                    HomeResult.Failure(HomeFailure.Network)
                }
                when (result) {
                    is HomeResult.Success -> if (likeRevisions[postId] == optimisticRevision) {
                        applyLikeUpdate(PostLikeUpdate(postId, result.value.likeCount, result.value.isLiked))
                    }

                    is HomeResult.Failure -> {
                        restoreLike(previousPost, optimisticRevision)
                        val isUnauthorized = result.reason == HomeFailure.Unauthorized ||
                            result.reason == HomeFailure.Http(401)
                        _uiState.update {
                            it.copy(
                                isLoginRequired = it.isLoginRequired || isUnauthorized,
                                pendingMessage = if (isUnauthorized) {
                                    it.pendingMessage
                                } else {
                                    UiMessage.Toast(
                                        id = nextMessageId++,
                                        text = "좋아요를 변경하지 못했어요. 다시 시도해주세요",
                                    )
                                },
                            )
                        }
                    }
                }
            } finally {
                pendingLikePostIds.remove(postId)
            }
        }
    }

    fun onMessageShown(messageId: Long) {
        _uiState.update { state ->
            if (state.pendingMessage?.id == messageId) state.copy(pendingMessage = null) else state
        }
    }

    private fun restoreLike(
        previousPost: AuthorPost,
        optimisticRevision: Int?,
    ) {
        if (likeRevisions[previousPost.id] == optimisticRevision) {
            applyLikeUpdate(PostLikeUpdate(previousPost.id, previousPost.likeCount, previousPost.isLiked))
        }
    }

    private fun applyLikeUpdate(update: PostLikeUpdate) {
        if (update.postId in deletedPostIds) return
        likeRevisions[update.postId] = (likeRevisions[update.postId] ?: 0) + 1
        latestLikeUpdates[update.postId] = update
        _uiState.update { state ->
            state.copy(posts = state.posts.map { post -> post.withLatestLike() })
        }
    }

    private fun AuthorPost.withLatestLike(): AuthorPost {
        val update = latestLikeUpdates[id] ?: return this
        return copy(likeCount = update.likeCount, isLiked = update.isLiked)
    }

    private fun loadPage(page: Int) {
        _uiState.update {
            if (page == 1) {
                AuthorPostsUiState(pageSize = it.pageSize)
            } else {
                it.copy(isLoadingNext = true, hasError = false)
            }
        }
        viewModelScope.launch {
            val result = repository.getAuthorPosts(authorId, page, _uiState.value.pageSize)
            val previousPostCount = _uiState.value.posts.size
            when (result) {
                is HomeResult.Success -> _uiState.update { state ->
                    val response = result.value
                    state.copy(
                        posts = (if (page == 1) response.posts else state.posts + response.posts)
                            .filterNot { it.id in deletedPostIds }
                            .distinctBy { it.id }
                            .map { it.withLatestLike() },
                        isLoading = false,
                        isLoadingNext = false,
                        currentPage = response.currentPage,
                        pageSize = response.pageSize,
                        hasNext = response.hasNext && response.posts.isNotEmpty(),
                        hasError = false,
                    )
                }

                is HomeResult.Failure -> _uiState.update { state ->
                    val isUnavailable = result.reason == HomeFailure.Http(404)
                    val isUnauthorized =
                        result.reason == HomeFailure.Unauthorized || result.reason == HomeFailure.Http(401)
                    state.copy(
                        posts = if (isUnavailable || isUnauthorized) emptyList() else state.posts,
                        isLoading = false,
                        isLoadingNext = false,
                        hasError = !isUnavailable && !isUnauthorized,
                        isAuthorUnavailable = isUnavailable,
                        isLoginRequired = isUnauthorized,
                        hasNext = state.hasNext && !isUnavailable && !isUnauthorized,
                    )
                }
            }
            if (result is HomeResult.Success && page > 1 &&
                _uiState.value.posts.size == previousPostCount
            ) {
                onEndThresholdChanged(isNearEnd)
            }
        }
    }

    companion object {
        fun factory(authorId: String) = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as ChalkakApplication
                AuthorPostsViewModel(application.appContainer.postRepository, authorId)
            }
        }
    }
}
