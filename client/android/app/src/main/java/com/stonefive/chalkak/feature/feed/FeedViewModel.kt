package com.stonefive.chalkak.feature.feed

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stonefive.chalkak.ChalkakApplication
import com.stonefive.chalkak.core.ui.UiMessage
import com.stonefive.chalkak.domain.model.HomeFailure
import com.stonefive.chalkak.domain.model.HomeQuery
import com.stonefive.chalkak.domain.model.HomeResult
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.domain.model.PostDetail
import com.stonefive.chalkak.domain.model.PostLikeUpdate
import com.stonefive.chalkak.domain.model.PostSort
import com.stonefive.chalkak.domain.model.UserSessionState
import com.stonefive.chalkak.domain.repository.AiReviewRepository
import com.stonefive.chalkak.domain.repository.PostRepository
import com.stonefive.chalkak.domain.repository.SignatureHintRepository
import com.stonefive.chalkak.feature.aireview.AiReviewSession
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class FeedViewModel(
    private val repository: PostRepository,
    private val aiReviewRepository: AiReviewRepository,
    private val sessionState: StateFlow<UserSessionState>,
    private val initialContent: FeedContentState.Success? = null,
    private val postId: String? = initialContent?.post?.id,
    private val isOwnedByCurrentUser: Boolean = initialContent?.post?.isOwnedByCurrentUser == true,
    private val signatureHintRepository: SignatureHintRepository? = null,
    private val dateProvider: () -> LocalDate = { LocalDate.now(KST) },
) : ViewModel() {
    private val _uiState = MutableStateFlow(
        FeedUiState(
            content = initialContent,
            isTitleEditable = initialContent?.topicDate == dateProvider(),
            isLoading = initialContent == null,
            isRefreshing = initialContent != null && postId != null,
        ),
    )
    val uiState: StateFlow<FeedUiState> = _uiState.asStateFlow()

    private var hintAttempted = false
    private var signatureClicked = false

    /**
     * 사진 상세에서 사인이 화면에 처음 보일 때 첫 안내 바텀시트를 띄운다. 내 사진이어도 띄운다.
     * 시트는 시간이 지나도 닫히지 않고 사용자가 닫아야 하며([dismissSignatureHint]), 한 번 닫으면 다시 띄우지 않는다.
     */
    fun onSignatureVisible() {
        val post = _uiState.value.content
            ?.post ?: return
        if (hintAttempted || post.authorId.isBlank()) return
        val repository = signatureHintRepository ?: return
        hintAttempted = true
        viewModelScope.launch {
            val completed = try {
                repository.isCompleted()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                true
            }
            if (!completed && !signatureClicked) {
                _uiState.update { it.copy(showSignatureHint = true) }
            }
        }
    }

    /** 첫 안내 바텀시트를 쓸어내리거나 바깥을 눌러 닫았을 때 호출한다. 이후로는 다시 띄우지 않는다. */
    fun dismissSignatureHint() {
        _uiState.update { it.copy(showSignatureHint = false) }
        completeSignatureHint()
    }

    fun onSignatureClicked(): Post? {
        signatureClicked = true
        _uiState.update { it.copy(showSignatureHint = false) }
        val post = _uiState.value.content
            ?.post ?: return null
        if (currentUserId() == null) {
            _uiState.update { it.copy(pendingMessage = nextToast("다른 사진도 보려면 로그인이 필요해요")) }
            return null
        }
        return post.takeIf { it.authorId.isNotBlank() }?.also { completeSignatureHint() }
    }

    private fun completeSignatureHint() {
        val repository = signatureHintRepository ?: return
        viewModelScope.launch {
            try {
                repository.complete()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                // 저장에 실패하면 다음 상세 진입에서 안내가 한 번 더 보일 뿐이다.
            }
        }
    }

    private var syncedLikeRevision = 0
    private var syncedLike: Pair<Int, PostLikeUpdate>? = null
    private var latestLikeGeneration = 0
    private var nextMessageId = 0L

    private var contentOwnerUserId = currentUserId()
    private val aiReviewSession = AiReviewSession(
        scope = viewModelScope,
        repository = aiReviewRepository,
        currentUserId = ::currentUserId,
        onStateChanged = { review -> _uiState.update { it.copy(aiReview = review) } },
        canRequestReview = { isTodayTopic(_uiState.value.content) },
    )

    init {
        viewModelScope.launch(start = CoroutineStart.UNDISPATCHED) {
            repository.likeUpdates.collect { update ->
                if (update.postId != (
                        _uiState.value.content
                            ?.post
                            ?.id ?: postId
                        )
                ) {
                    return@collect
                }
                syncedLike = ++syncedLikeRevision to update
                // 동기화된 서버 상태가 더 최신이므로 이 화면에서 진행 중인 좋아요의 성공 반영·롤백을 무효화한다.
                latestLikeGeneration++
                _uiState.update { state ->
                    val content = state.content ?: return@update state
                    state.copy(
                        content = content.copy(
                            post = content.post.copy(likeCount = update.likeCount, isLiked = update.isLiked),
                            isLiked = update.isLiked,
                        ),
                    )
                }
            }
        }
        syncFeedbackTarget()
        val initialSession = sessionState.value
        viewModelScope.launch {
            var previousSession = initialSession
            sessionState.collect { session ->
                if (session != previousSession) {
                    previousSession = session
                    syncFeedbackTarget()
                }
            }
        }

        if (postId != null) {
            loadPostDetail(postId)
        } else if (initialContent == null) {
            loadFeed()
        }
    }

    fun onResume() {
        syncFeedbackTarget()
        aiReviewSession.onResume()
    }

    fun onPause() = aiReviewSession.onPause()

    fun startFeedback() {
        syncFeedbackTarget(refresh = false)
        if (!_uiState.value.isDeleting) aiReviewSession.startReview()
    }

    fun dismissFeedbackMessage() = aiReviewSession.dismissMessage()

    private fun currentUserId(): String? = (sessionState.value as? UserSessionState.Authenticated)?.userId

    private fun isTodayTopic(content: FeedContentState.Success?): Boolean =
        content != null && content.topicDate == dateProvider()

    private fun syncFeedbackTarget(refresh: Boolean = true) {
        val state = _uiState.value
        val target = state.content
            ?.post
            ?.takeIf {
                it.isOwnedByCurrentUser && currentUserId() != null &&
                    contentOwnerUserId == currentUserId() && state.deleteSuccessPostId == null
            }?.id
        aiReviewSession.setTarget(target)
        if (refresh) aiReviewSession.refresh()
    }

    fun retry() {
        if (postId != null) {
            loadPostDetail(postId)
        } else {
            loadFeed()
        }
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

    fun deletePost() {
        val state = _uiState.value
        val post = state.content?.post ?: return
        if (!post.isOwnedByCurrentUser || state.isDeleting || state.isUpdatingTitle) return

        _uiState.update {
            it.copy(
                isDeleting = true,
                deleteSuccessPostId = null,
            )
        }
        viewModelScope.launch {
            val result = try {
                repository.deletePost(post.id)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                HomeResult.Failure(HomeFailure.Network)
            }

            when (result) {
                is HomeResult.Success -> {
                    aiReviewSession.setTarget(null)
                    _uiState.update {
                        it.copy(
                            isDeleting = false,
                            deleteSuccessPostId = post.id,
                        )
                    }
                }

                is HomeResult.Failure -> _uiState.update {
                    it.copy(
                        isDeleting = false,
                        pendingMessage = nextSnackbar(result.reason.toDeleteErrorMessage()),
                    )
                }
            }
        }
    }

    fun updatePostTitle(title: String?) {
        val state = _uiState.value
        val post = state.content?.post ?: return
        if (
            !post.isOwnedByCurrentUser ||
            !state.isTitleEditable ||
            state.isDeleting ||
            state.isUpdatingTitle ||
            state.isRefreshing
        ) {
            return
        }

        _uiState.update {
            it.copy(
                isUpdatingTitle = true,
                titleUpdateSuccessPostId = null,
            )
        }
        viewModelScope.launch {
            val result = try {
                repository.updatePostTitle(post.id, title)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                HomeResult.Failure(HomeFailure.Network)
            }

            when (result) {
                is HomeResult.Success -> {
                    _uiState.update { currentState ->
                        val currentContent = currentState.content ?: return@update currentState.copy(
                            isUpdatingTitle = false,
                        )
                        val updatedTitle = result.value.title
                        val updatedPost = currentContent.post.copy(
                            title = updatedTitle,
                            contentDescription = updatedTitle.toContentDescription(),
                        )
                        currentState.copy(
                            isUpdatingTitle = false,
                            titleUpdateSuccessPostId = post.id,
                            content = currentContent.copy(
                                post = updatedPost,
                            ),
                            pendingMessage = nextToast("제목을 수정했어요"),
                        )
                    }
                }

                is HomeResult.Failure -> _uiState.update {
                    it.copy(
                        isUpdatingTitle = false,
                        pendingMessage = nextToast(result.reason.toTitleUpdateErrorMessage()),
                    )
                }
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
        val requestUserId = currentUserId()
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

            contentOwnerUserId = requestUserId
            _uiState.value = FeedUiState(
                isLoading = false,
                content = FeedContentState.Success(
                    dateLabel = content.topicDate.toFeedDateLabel(),
                    topic = content.topic,
                    post = post,
                    isLiked = post.isLiked,
                    topicDate = content.topicDate,
                ),
                isTitleEditable = content.topicDate == dateProvider(),
            )
            syncFeedbackTarget()
        }
    }

    private fun loadPostDetail(postId: String) {
        val requestUserId = currentUserId()
        val likeGenerationAtRequestStart = latestLikeGeneration
        val syncRevisionAtRequestStart = syncedLikeRevision
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
                is HomeResult.Success -> applyPostDetail(
                    result.value,
                    likeGenerationAtRequestStart,
                    syncRevisionAtRequestStart,
                    requestUserId,
                )

                is HomeResult.Failure -> handleDetailFailure(result.reason)
            }
        }
    }

    private fun handleDetailFailure(failure: HomeFailure) {
        if (failure.isPostNotFound()) aiReviewSession.setTarget(null)
        val current = _uiState.value
        if (current.content != null && !failure.isPostNotFound()) {
            val pendingMessage = nextToast(failure.toFeedErrorMessage())
            _uiState.update {
                it.copy(
                    isLoading = false,
                    isRefreshing = false,
                    errorMessage = null,
                    pendingMessage = pendingMessage,
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                content = if (failure.isPostNotFound()) null else it.content,
                isLoading = false,
                isRefreshing = false,
                errorMessage = failure.toFeedErrorMessage(),
            )
        }
    }

    private fun applyPostDetail(
        detail: PostDetail,
        likeGenerationAtRequestStart: Int,
        syncRevisionAtRequestStart: Int,
        requestUserId: String?,
    ) {
        val previousContent = _uiState.value.content
        val previousPost = previousContent?.post
        val shouldPreserveCurrentLike =
            previousPost != null && likeGenerationAtRequestStart != latestLikeGeneration
        val currentLikePost = previousPost?.takeIf { shouldPreserveCurrentLike }
        val newerSyncedLike = syncedLike
            ?.takeIf {
                it.first > syncRevisionAtRequestStart && it.second.postId == detail.post.id
            }?.second
        val updatedPost = detail.post.copy(
            likeCount = newerSyncedLike?.likeCount ?: currentLikePost?.likeCount ?: detail.post.likeCount,
            isLiked = newerSyncedLike?.isLiked ?: currentLikePost?.isLiked ?: detail.post.isLiked,
            signatureThumbnailImageUrl = detail.post.signatureThumbnailImageUrl
                ?: previousPost?.signatureThumbnailImageUrl,
            submittedAt = detail.post.submittedAt ?: previousPost?.submittedAt,
            isOwnedByCurrentUser = detail.post.isOwnedByCurrentUser ||
                previousPost?.isOwnedByCurrentUser == true ||
                isOwnedByCurrentUser,
        )
        contentOwnerUserId = requestUserId
        val updatedContent = FeedContentState.Success(
            dateLabel = detail.topicDate.toFeedDateLabel(),
            topic = detail.topic,
            post = updatedPost,
            isLiked = updatedPost.isLiked,
            topicDate = detail.topicDate,
        )
        _uiState.update { state ->
            state.copy(
                content = if (state.content == updatedContent) state.content else updatedContent,
                isTitleEditable = detail.topicDate == dateProvider(),
                isLoading = false,
                isRefreshing = false,
                errorMessage = null,
            )
        }
        syncFeedbackTarget()
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

    private fun HomeFailure.toDeleteErrorMessage(): String = when (this) {
        is HomeFailure.Http -> when (statusCode) {
            400 -> "삭제할 수 없는 상태의 게시물이에요"
            403 -> "본인이 작성한 게시물만 삭제할 수 있어요"
            404 -> "게시물을 찾을 수 없어요"
            else -> "게시물을 삭제하지 못했어요"
        }

        HomeFailure.Unauthorized -> "로그인이 필요해요"

        HomeFailure.Network -> "네트워크 연결을 확인해 주세요"

        else -> "게시물을 삭제하지 못했어요"
    }

    private fun HomeFailure.toTitleUpdateErrorMessage(): String = when (this) {
        is HomeFailure.Http -> when (statusCode) {
            400 -> "수정할 수 없는 상태이거나 주제 참여 기간이 끝났어요"
            403 -> "본인의 게시물만 수정할 수 있어요"
            404 -> "게시물을 찾을 수 없어요"
            else -> "게시물 제목을 수정하지 못했어요"
        }

        HomeFailure.Unauthorized -> "로그인이 필요해요"

        HomeFailure.Network -> "네트워크 연결을 확인해 주세요"

        else -> "게시물 제목을 수정하지 못했어요"
    }

    private fun HomeFailure.isPostNotFound(): Boolean = this is HomeFailure.Http && statusCode == 404

    private fun LocalDate.toFeedDateLabel(): String = "${monthValue}월 ${dayOfMonth}일의 주제"

    private fun String?.toContentDescription(): String = this
        ?.takeIf(String::isNotBlank)
        ?.let { "작품 이미지: $it" }
        ?: "무제 작품 이미지"

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
                    signatureHintRepository = application.appContainer.signatureHintRepository,
                    aiReviewRepository = application.appContainer.aiReviewRepository,
                    sessionState = application.appContainer.authRepository.sessionState,
                    initialContent = initialContent,
                    postId = postId,
                    isOwnedByCurrentUser = isOwnedByCurrentUser,
                )
            }
        }

        val Factory = factory()

        private val KST: ZoneId = ZoneId.of("Asia/Seoul")
    }

    private fun nextToast(text: String): UiMessage.Toast = UiMessage.Toast(
        id = nextMessageId++,
        text = text,
    )

    private fun nextSnackbar(text: String): UiMessage.Snackbar = UiMessage.Snackbar(
        id = nextMessageId++,
        text = text,
    )
}
