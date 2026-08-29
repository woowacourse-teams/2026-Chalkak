package com.stonefive.chalkak.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stonefive.chalkak.ChalkakApplication
import com.stonefive.chalkak.core.designsystem.component.bottombar.ChalkakBottomBarItem
import com.stonefive.chalkak.domain.model.HomeFailure
import com.stonefive.chalkak.domain.model.HomeQuery
import com.stonefive.chalkak.domain.model.HomeResult
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.domain.model.PostContent
import com.stonefive.chalkak.domain.model.PostPage
import com.stonefive.chalkak.domain.model.PostSort
import com.stonefive.chalkak.domain.model.UserSessionState
import com.stonefive.chalkak.domain.repository.PostRepository
import java.time.LocalDate
import java.time.ZoneId
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: PostRepository,
    private val sessionState: StateFlow<UserSessionState>,
    private val dateProvider: () -> LocalDate,
    private val launchContext: CoroutineContext = EmptyCoroutineContext,
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _uiEvent = Channel<HomeUiEvent>(Channel.BUFFERED)
    val uiEvent = _uiEvent.receiveAsFlow()

    private var latestLoadGeneration = 0
    private var homeContentRevision = 0
    private var loadedDate: LocalDate? = null
    private var isEndThresholdReached = false
    private var isFirstPageRequestPending = false
    private var firstPageJob: Job? = null
    private var nextPageJob: Job? = null
    private val latestLikeGenerationByPhotoId = mutableMapOf<String, Int>()
    private val likeJobsByPhotoId = mutableMapOf<String, MutableSet<Job>>()

    init {
        loadHome()
    }

    fun onAction(action: HomeUiAction) {
        when (action) {
            HomeUiAction.AddClicked -> sendUiEvent(HomeUiEvent.OpenPhotoUpload)

            HomeUiAction.RetryClicked -> loadHome()

            HomeUiAction.RefreshRequested -> refreshHome()

            is HomeUiAction.BottomBarSelected -> {
                if (action.item == ChalkakBottomBarItem.TODAY) {
                    refreshHome()
                } else {
                    sendUiEvent(HomeUiEvent.NavigateToBottomBar(action.item))
                }
            }

            is HomeUiAction.EndThresholdChanged -> updateEndThreshold(action.isReached)

            is HomeUiAction.LikeClicked -> updateLike(action.photoId)
        }
    }

    private fun loadHome() {
        requestFirstPage(
            requestedSort = _uiState.value.selectedSort,
            mode = FirstPageMode.Load,
        )
    }

    private fun refreshHome() {
        val previousState = _uiState.value
        if (previousState.contentStatus == HomeContentStatus.Loading || previousState.isRefreshing) return

        requestFirstPage(
            requestedSort = PostSort.RANDOM,
            mode = FirstPageMode.Refresh,
        )
    }

    private fun requestFirstPage(
        requestedSort: PostSort,
        mode: FirstPageMode,
    ) {
        val requestedDate = dateProvider()
        val generation = ++latestLoadGeneration
        val refreshRevision = _uiState.value.refreshRevision
        val preservesContent = mode == FirstPageMode.Refresh &&
            _uiState.value.contentStatus == HomeContentStatus.Content

        firstPageJob?.cancel()
        nextPageJob?.cancel()
        nextPageJob = null
        isEndThresholdReached = false
        isFirstPageRequestPending = true

        if (preservesContent) {
            _uiState.update {
                it.copy(
                    isLoadingNext = false,
                    isRefreshing = true,
                    areLikesEnabled = false,
                )
            }
        } else {
            _uiState.value = HomeUiState(
                contentStatus = HomeContentStatus.Loading,
                selectedSort = requestedSort,
                refreshRevision = refreshRevision,
                areLikesEnabled = false,
            )
        }

        firstPageJob = viewModelScope.launch(launchContext) {
            awaitLikeMutations()
            if (generation != latestLoadGeneration) return@launch

            val result = repository.getPostContent(
                HomeQuery(
                    date = requestedDate,
                    sort = requestedSort,
                    page = HomeQuery.FIRST_PAGE,
                ),
            )
            if (generation != latestLoadGeneration) return@launch

            isFirstPageRequestPending = false
            firstPageJob = null
            when (result) {
                is HomeResult.Success -> applyFirstPage(
                    content = result.value,
                    requestedSort = requestedSort,
                    refreshRevision = refreshRevision + if (mode == FirstPageMode.Refresh) 1 else 0,
                )

                is HomeResult.Failure -> {
                    val reason = result.reason.toInitialError()
                    if (preservesContent) {
                        _uiState.update {
                            it.copy(
                                isRefreshing = false,
                                isLoadingNext = false,
                                areLikesEnabled = true,
                            )
                        }
                        sendUiEvent(HomeUiEvent.ShowRefreshFailure(reason))
                    } else {
                        _uiState.value = HomeUiState(
                            contentStatus = HomeContentStatus.Error(reason),
                            selectedSort = requestedSort,
                            refreshRevision = refreshRevision,
                        )
                    }
                }
            }
        }
    }

    private fun applyFirstPage(
        content: PostContent,
        requestedSort: PostSort,
        refreshRevision: Int,
    ) {
        loadedDate = content.topicDate
        homeContentRevision++
        _uiState.value = HomeUiState(
            contentStatus = HomeContentStatus.Content,
            topicDate = content.topicDate,
            topic = content.topic,
            photos = content.photos,
            selectedSort = requestedSort,
            likedPhotoIds = content.likedPhotoIds,
            currentPage = content.currentPage,
            hasNext = content.hasNext,
            randomSeed = content.randomSeed,
            refreshRevision = refreshRevision,
        )
    }

    private suspend fun awaitLikeMutations() {
        while (true) {
            val jobs = likeJobsByPhotoId.values.flatMap { it.toList() }
            if (jobs.isEmpty()) return
            jobs.joinAll()
        }
    }

    private fun updateEndThreshold(isReached: Boolean) {
        if (!isReached) {
            isEndThresholdReached = false
            return
        }
        if (isFirstPageRequestPending) return
        if (isEndThresholdReached) return

        isEndThresholdReached = true
        loadNextPage()
    }

    private fun loadNextPage() {
        val state = _uiState.value
        val date = loadedDate ?: return
        if (
            state.contentStatus != HomeContentStatus.Content ||
            isFirstPageRequestPending ||
            !state.hasNext ||
            state.isLoadingNext
        ) {
            return
        }

        val requestedPage = state.currentPage + 1
        val generation = latestLoadGeneration
        val randomSeed = state.randomSeed.takeIf { state.selectedSort == PostSort.RANDOM }
        if (state.selectedSort == PostSort.RANDOM && randomSeed.isNullOrBlank()) {
            _uiState.update { it.copy(hasNext = false, isLoadingNext = false) }
            return
        }
        val query = HomeQuery(
            date = date,
            sort = state.selectedSort,
            page = requestedPage,
            randomSeed = randomSeed,
        )
        _uiState.update { it.copy(isLoadingNext = true) }

        nextPageJob = viewModelScope.launch(launchContext) {
            val result = repository.getPostPage(query)
            if (generation != latestLoadGeneration) return@launch

            when (result) {
                is HomeResult.Success -> appendPage(result.value)
                is HomeResult.Failure -> _uiState.update { it.copy(isLoadingNext = false) }
            }
            nextPageJob = null
        }
    }

    private fun appendPage(page: PostPage) {
        _uiState.update { state ->
            val existingIds = state.photos.mapTo(mutableSetOf(), Post::id)
            val newPhotos = page.photos.filter { existingIds.add(it.id) }
            val newPhotoIds = newPhotos.mapTo(mutableSetOf(), Post::id)
            state.copy(
                photos = state.photos + newPhotos,
                likedPhotoIds = (state.likedPhotoIds - newPhotoIds) +
                    page.likedPhotoIds.intersect(newPhotoIds),
                currentPage = page.currentPage,
                hasNext = page.hasNext,
                randomSeed = if (state.selectedSort == PostSort.RANDOM) {
                    state.randomSeed ?: page.randomSeed
                } else {
                    null
                },
                isLoadingNext = false,
            )
        }
    }

    private fun updateLike(photoId: String) {
        if (isFirstPageRequestPending) return

        if (sessionState.value !is UserSessionState.Authenticated) {
            sendUiEvent(HomeUiEvent.ShowGuestLikeMessage)
            return
        }

        val previousState = _uiState.value
        val previousPhoto = previousState.photos.find { it.id == photoId } ?: return
        val wasLiked = photoId in previousState.likedPhotoIds
        val isLiked = !wasLiked
        val generation = latestLikeGenerationByPhotoId.getOrDefault(photoId, 0) + 1
        val requestedContentRevision = homeContentRevision
        latestLikeGenerationByPhotoId[photoId] = generation
        _uiState.value = previousState.copy(
            photos = previousState.photos.map { photo ->
                if (photo.id == photoId) {
                    photo.copy(
                        likeCount = photo.likeCount + if (isLiked) 1 else -1,
                        isLiked = isLiked,
                    )
                } else {
                    photo
                }
            },
            likedPhotoIds = if (isLiked) {
                previousState.likedPhotoIds + photoId
            } else {
                previousState.likedPhotoIds - photoId
            },
        )

        val job = viewModelScope.launch(
            context = launchContext,
            start = CoroutineStart.LAZY,
        ) {
            try {
                val result = repository.updateLike(photoId, isLiked)
                if (
                    latestLikeGenerationByPhotoId[photoId] != generation ||
                    homeContentRevision != requestedContentRevision
                ) {
                    return@launch
                }

                when (result) {
                    is HomeResult.Success -> _uiState.update { state ->
                        state.copy(
                            photos = state.photos.map { photo ->
                                if (photo.id == photoId) {
                                    photo.copy(
                                        likeCount = result.value.likeCount,
                                        isLiked = result.value.isLiked,
                                    )
                                } else {
                                    photo
                                }
                            },
                            likedPhotoIds = if (result.value.isLiked) {
                                state.likedPhotoIds + photoId
                            } else {
                                state.likedPhotoIds - photoId
                            },
                        )
                    }

                    is HomeResult.Failure -> restoreLike(photoId, previousPhoto, wasLiked)
                }
            } finally {
                removeLikeJob(photoId, currentCoroutineContext().job)
            }
        }
        likeJobsByPhotoId.getOrPut(photoId, ::mutableSetOf).add(job)
        job.start()
    }

    private fun removeLikeJob(
        photoId: String,
        job: Job,
    ) {
        likeJobsByPhotoId[photoId]?.let { jobs ->
            jobs.remove(job)
            if (jobs.isEmpty()) likeJobsByPhotoId.remove(photoId)
        }
    }

    private fun restoreLike(
        photoId: String,
        previousPhoto: Post,
        wasLiked: Boolean,
    ) {
        _uiState.update { state ->
            if (state.photos.none { it.id == photoId }) return@update state

            state.copy(
                photos = state.photos.map { photo ->
                    if (photo.id == photoId) previousPhoto else photo
                },
                likedPhotoIds = if (wasLiked) {
                    state.likedPhotoIds + photoId
                } else {
                    state.likedPhotoIds - photoId
                },
            )
        }
    }

    private fun sendUiEvent(event: HomeUiEvent) {
        viewModelScope.launch { _uiEvent.send(event) }
    }

    private fun HomeFailure.toInitialError(): HomeInitialError = when (this) {
        HomeFailure.TopicNotFound -> HomeInitialError.TopicNotFound

        HomeFailure.Unauthorized -> HomeInitialError.Unauthorized

        HomeFailure.Network -> HomeInitialError.Network

        HomeFailure.InvalidResponse -> HomeInitialError.InvalidResponse

        is HomeFailure.Http -> when (statusCode) {
            in 400..499 -> HomeInitialError.Client
            in 500..599 -> HomeInitialError.Server
            else -> HomeInitialError.Generic
        }
    }

    companion object {
        private val KST: ZoneId = ZoneId.of("Asia/Seoul")

        val Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as ChalkakApplication
                HomeViewModel(
                    repository = application.appContainer.postRepository,
                    sessionState = application.appContainer.authRepository.sessionState,
                    dateProvider = { LocalDate.now(KST) },
                )
            }
        }
    }

    private enum class FirstPageMode {
        Load,
        Refresh,
    }
}
