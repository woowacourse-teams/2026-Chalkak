package com.stonefive.chalkak.feature.display

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stonefive.chalkak.ChalkakApplication
import com.stonefive.chalkak.core.ui.UiMessage
import com.stonefive.chalkak.domain.model.HomeFailure
import com.stonefive.chalkak.domain.model.HomeLike
import com.stonefive.chalkak.domain.model.HomeQuery
import com.stonefive.chalkak.domain.model.HomeResult
import com.stonefive.chalkak.domain.model.Post
import com.stonefive.chalkak.domain.model.PostContent
import com.stonefive.chalkak.domain.model.PostPage
import com.stonefive.chalkak.domain.model.PostSort
import com.stonefive.chalkak.domain.repository.PostRepository
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class DisplayViewModel(
    private val repository: PostRepository,
    private val initialDate: LocalDate? = null,
    private val dateProvider: () -> LocalDate = { LocalDate.now(KST) },
) : ViewModel() {
    private val _uiState = MutableStateFlow(DisplayUiState())
    val uiState: StateFlow<DisplayUiState> = _uiState.asStateFlow()

    private var latestLoadGeneration = 0
    private var selectedSort = PostSort.LATEST
    private var loadedDate: LocalDate? = null
    private var isEndThresholdReached = false
    private var isRevalidating = false
    private var hasBeenPresented = false
    private var nextPageJob: Job? = null
    private var nextMessageId = 0L
    private var likeRevision = 0
    private val displayCache = mutableMapOf<DisplayCacheKey, DisplayCacheEntry>()
    private val pendingLikePhotoIds = mutableSetOf<String>()
    private val likeUpdates = mutableMapOf<String, DisplayLikeUpdate>()

    init {
        loadDisplay(date = initialDate)
    }

    fun moveToPreviousDate() {
        val state = _uiState.value
        if (state.content is DisplayContentState.Loading) return
        val selectedDate = state.selectedDate ?: return
        val targetDate = state.earliestDate
            ?.let { selectedDate.minusDays(1).coerceAtLeast(it) }
            ?: selectedDate.minusDays(1)
        if (targetDate == selectedDate) return

        startDateChange(targetDate, state)
    }

    fun moveToNextDate() {
        val state = _uiState.value
        if (state.content is DisplayContentState.Loading) return
        val selectedDate = state.selectedDate ?: return
        val latestDate = state.latestDate ?: return
        val targetDate = selectedDate.plusDays(1).coerceAtMost(latestDate)
        if (targetDate == selectedDate) return

        startDateChange(targetDate, state)
    }

    fun selectSort(sort: PostSort) {
        val state = _uiState.value
        val latestContent = state.content as? DisplayContentState.Latest ?: return
        if (sort == latestContent.selectedSort) return
        val selectedDate = state.selectedDate ?: return

        selectedSort = sort
        val cachedState = displayCache[DisplayCacheKey(selectedDate, sort)]?.state?.copy(
            latestDate = state.latestDate,
            earliestDate = state.earliestDate,
            pendingMessage = null,
        )
        val visibleState = cachedState ?: state.copy(
            content = latestContent.copy(selectedSort = sort),
            isLoadingNext = false,
        )
        _uiState.value = visibleState
        loadDisplay(
            date = selectedDate,
            previousState = cachedState ?: state,
            keepsContentVisible = true,
        )
    }

    fun retry() {
        loadDisplay(_uiState.value.selectedDate)
    }

    fun onResume() {
        if (!hasBeenPresented) {
            hasBeenPresented = true
            return
        }
        revalidate()
    }

    fun revalidate() {
        val state = _uiState.value
        if (state.content is DisplayContentState.Loading || isRevalidating) return

        val selectedDate = state.selectedDate ?: return
        loadDisplay(
            date = selectedDate,
            previousState = state,
            keepsContentVisible = true,
        )
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

    fun updateFeaturedPage(page: Int) {
        _uiState.update { state ->
            val archive = state.content as? DisplayContentState.Archive ?: return@update state
            val lastPage = archive.featuredPhotos.lastIndex
                .coerceAtLeast(0)
            state.copy(
                content = archive.copy(featuredPage = page.coerceIn(0, lastPage)),
            )
        }
    }

    fun updateEndThreshold(isReached: Boolean) {
        if (!isReached) {
            isEndThresholdReached = false
            return
        }
        if (isEndThresholdReached) return

        isEndThresholdReached = true
        loadNextPage()
    }

    fun updateLike(photoId: String) {
        if (!pendingLikePhotoIds.add(photoId)) return

        val previousState = _uiState.value
        val previousPhoto = previousState.photos.find { it.id == photoId }
        if (previousPhoto == null) {
            pendingLikePhotoIds.remove(photoId)
            return
        }

        val wasLiked = photoId in previousState.likedPhotoIds
        val isLiked = !wasLiked
        applyLikeState(
            photoId = photoId,
            likedPhotoIds = if (isLiked) {
                previousState.likedPhotoIds + photoId
            } else {
                previousState.likedPhotoIds - photoId
            },
        ) { photo ->
            photo.copy(
                likeCount = (photo.likeCount + if (isLiked) 1 else -1).coerceAtLeast(0),
                isLiked = isLiked,
            )
        }

        viewModelScope.launch {
            try {
                val result = try {
                    repository.updateLike(photoId, isLiked)
                } catch (cancellation: CancellationException) {
                    throw cancellation
                } catch (_: Exception) {
                    HomeResult.Failure(HomeFailure.Network)
                }

                when (result) {
                    is HomeResult.Success -> {
                        likeUpdates[photoId] = DisplayLikeUpdate(
                            revision = ++likeRevision,
                            value = result.value,
                        )
                        applyLikeState(
                            photoId = photoId,
                            likedPhotoIds = if (result.value.isLiked) {
                                _uiState.value.likedPhotoIds + photoId
                            } else {
                                _uiState.value.likedPhotoIds - photoId
                            },
                        ) { photo ->
                            photo.copy(
                                likeCount = result.value.likeCount,
                                isLiked = result.value.isLiked,
                            )
                        }
                    }

                    is HomeResult.Failure -> restoreLike(photoId, previousPhoto, wasLiked)
                }
            } finally {
                pendingLikePhotoIds.remove(photoId)
            }
        }
    }

    private fun startDateChange(
        targetDate: LocalDate,
        previousState: DisplayUiState,
    ) {
        _uiState.update {
            it.copy(
                selectedDate = targetDate,
                content = DisplayContentState.Loading,
            )
        }
        loadDisplay(targetDate, previousState = previousState)
    }

    private fun loadDisplay(
        date: LocalDate?,
        sort: PostSort = selectedSort,
        previousState: DisplayUiState? = null,
        keepsContentVisible: Boolean = false,
    ) {
        val latestDate = dateProvider()
        val requestedDate = date ?: latestDate
        val requestedSort = if (requestedDate < latestDate) PostSort.POPULAR else sort
        val generation = ++latestLoadGeneration
        isRevalidating = keepsContentVisible
        nextPageJob?.cancel()
        nextPageJob = null
        isEndThresholdReached = false

        viewModelScope.launch {
            if (keepsContentVisible) {
                _uiState.update { it.copy(latestDate = latestDate, isLoadingNext = false) }
            } else {
                _uiState.update {
                    it.copy(
                        selectedDate = requestedDate,
                        latestDate = latestDate,
                        content = DisplayContentState.Loading,
                    )
                }
            }
            val requestLikeRevision = likeRevision
            val result = try {
                repository.getPostContent(
                    HomeQuery(
                        date = requestedDate,
                        sort = requestedSort,
                        page = HomeQuery.FIRST_PAGE,
                    ),
                )
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                HomeResult.Failure(HomeFailure.Network)
            }
            if (generation != latestLoadGeneration) return@launch

            when (result) {
                is HomeResult.Success -> applyFirstPage(
                    postContent = result.value,
                    latestDate = latestDate,
                    requestedSort = requestedSort,
                    requestLikeRevision = requestLikeRevision,
                )

                is HomeResult.Failure -> if (previousState != null) {
                    val isPreviousDateRequest = result.reason == HomeFailure.TopicNotFound &&
                        requireNotNull(date) < requireNotNull(previousState.selectedDate)
                    selectedSort = (previousState.content as? DisplayContentState.Latest)
                        ?.selectedSort
                        ?: selectedSort
                    val pendingMessage = nextToast(DISPLAY_ERROR_MESSAGE)
                    val restoredState = previousState.copy(
                        selectedDate = previousState.selectedDate,
                        topic = previousState.topic,
                        content = previousState.content,
                        likedPhotoIds = previousState.likedPhotoIds,
                        currentPage = previousState.currentPage,
                        hasNext = previousState.hasNext,
                        randomSeed = previousState.randomSeed,
                        isLoadingNext = false,
                        earliestDate = if (isPreviousDateRequest) {
                            previousState.selectedDate
                        } else {
                            previousState.earliestDate
                        },
                        pendingMessage = pendingMessage,
                    )
                    _uiState.update { restoredState.withAppliedLikeUpdatesAfter(requestLikeRevision) }
                } else {
                    _uiState.update {
                        it.copy(
                            content = DisplayContentState.Error(
                                message = DISPLAY_ERROR_MESSAGE,
                            ),
                        )
                    }
                }
            }
            isRevalidating = false
        }
    }

    private fun applyFirstPage(
        postContent: PostContent,
        latestDate: LocalDate,
        requestedSort: PostSort,
        requestLikeRevision: Int,
    ) {
        val earliestDate = _uiState.value.earliestDate
        loadedDate = postContent.topicDate
        val cacheKey = DisplayCacheKey(postContent.topicDate, requestedSort)
        val cachedEntry = displayCache[cacheKey]
        val canReuseCachedTail = requestedSort != PostSort.RANDOM ||
            (
                !postContent.randomSeed.isNullOrBlank() &&
                    cachedEntry?.state?.randomSeed == postContent.randomSeed
                )
        val freshPhotoIds = postContent.photos.mapTo(mutableSetOf(), Post::id)
        val cachedFirstPagePhotoIds = cachedEntry
            ?.firstPagePhotoIds
            .orEmpty()
            .takeIf { canReuseCachedTail }
            .orEmpty()
        val cachedTail = if (canReuseCachedTail) {
            cachedEntry
                ?.state
                ?.photos
                .orEmpty()
                .filter { it.id !in cachedFirstPagePhotoIds && it.id !in freshPhotoIds }
        } else {
            emptyList()
        }
        val mergedPhotos = postContent.photos + cachedTail
        val cachedTailIds = cachedTail.mapTo(mutableSetOf(), Post::id)
        val mergedLikedPhotoIds = postContent.likedPhotoIds +
            cachedEntry
                ?.state
                ?.likedPhotoIds
                .orEmpty()
                .intersect(cachedTailIds)
        val content = if (postContent.topicDate < latestDate) {
            DisplayContentState.Archive(
                photos = mergedPhotos,
                featuredPhotos = mergedPhotos.take(FEATURED_PHOTO_COUNT),
            )
        } else {
            DisplayContentState.Latest(
                photos = mergedPhotos,
                selectedSort = selectedSort,
            )
        }
        val newState = DisplayUiState(
            selectedDate = postContent.topicDate,
            latestDate = latestDate,
            earliestDate = earliestDate,
            topic = postContent.topic,
            content = content,
            likedPhotoIds = mergedLikedPhotoIds,
            currentPage = cachedEntry
                ?.state
                ?.currentPage
                ?.takeIf { cachedTail.isNotEmpty() }
                ?: postContent.currentPage,
            hasNext = cachedEntry
                ?.state
                ?.hasNext
                ?.takeIf { cachedTail.isNotEmpty() }
                ?: postContent.hasNext,
            randomSeed = postContent.randomSeed,
        ).withAppliedLikeUpdatesAfter(requestLikeRevision)
        _uiState.value = newState
        displayCache[cacheKey] = DisplayCacheEntry(
            state = newState,
            firstPagePhotoIds = freshPhotoIds,
        )
    }

    private fun loadNextPage() {
        val state = _uiState.value
        val date = loadedDate ?: return
        if (state.content !is DisplayContentState.Latest &&
            state.content !is DisplayContentState.Archive
        ) {
            return
        }
        if (isRevalidating || !state.hasNext || state.isLoadingNext || nextPageJob != null) return

        val sort = when (val content = state.content) {
            is DisplayContentState.Latest -> content.selectedSort
            is DisplayContentState.Archive -> PostSort.POPULAR
            else -> return
        }
        val randomSeed = state.randomSeed.takeIf { sort == PostSort.RANDOM }
        if (sort == PostSort.RANDOM && randomSeed.isNullOrBlank()) {
            _uiState.update { it.copy(hasNext = false) }
            return
        }

        val generation = latestLoadGeneration
        val query = HomeQuery(
            date = date,
            sort = sort,
            page = state.currentPage + 1,
            randomSeed = randomSeed,
        )
        _uiState.update { it.copy(isLoadingNext = true) }
        val job = viewModelScope.launch {
            val requestLikeRevision = likeRevision
            val result = try {
                repository.getPostPage(query)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                HomeResult.Failure(HomeFailure.Network)
            }
            if (generation != latestLoadGeneration) return@launch

            when (result) {
                is HomeResult.Success -> appendPage(result.value, requestLikeRevision)
                is HomeResult.Failure -> _uiState.update { it.copy(isLoadingNext = false) }
            }
        }
        nextPageJob = job
        job.invokeOnCompletion {
            if (nextPageJob === job) nextPageJob = null
        }
    }

    private fun appendPage(
        page: PostPage,
        requestLikeRevision: Int,
    ) {
        _uiState.update { state ->
            val content = state.content
            val existingIds = when (content) {
                is DisplayContentState.Latest -> content.photos.mapTo(mutableSetOf(), Post::id)
                is DisplayContentState.Archive -> content.photos.mapTo(mutableSetOf(), Post::id)
                else -> return@update state.copy(isLoadingNext = false)
            }
            val newPhotos = page.photos.filter { existingIds.add(it.id) }
            val newPhotoIds = newPhotos.mapTo(mutableSetOf(), Post::id)
            val updatedContent = when (content) {
                is DisplayContentState.Latest -> content.copy(photos = content.photos + newPhotos)
                is DisplayContentState.Archive -> content.copy(photos = content.photos + newPhotos)
                else -> return@update state.copy(isLoadingNext = false)
            }
            state
                .copy(
                    content = updatedContent,
                    likedPhotoIds = (state.likedPhotoIds - newPhotoIds) +
                        page.likedPhotoIds.intersect(newPhotoIds),
                    currentPage = page.currentPage,
                    hasNext = page.hasNext,
                    randomSeed = if (state.content is DisplayContentState.Latest &&
                        state.content.selectedSort == PostSort.RANDOM
                    ) {
                        state.randomSeed ?: page.randomSeed
                    } else {
                        null
                    },
                    isLoadingNext = false,
                ).withAppliedLikeUpdatesAfter(requestLikeRevision)
        }
        cacheCurrentState()
    }

    private fun restoreLike(
        photoId: String,
        previousPhoto: Post,
        wasLiked: Boolean,
    ) {
        likeUpdates[photoId] = DisplayLikeUpdate(
            revision = ++likeRevision,
            value = HomeLike(
                likeCount = previousPhoto.likeCount,
                isLiked = previousPhoto.isLiked,
            ),
        )
        applyLikeState(
            photoId = photoId,
            likedPhotoIds = if (wasLiked) {
                _uiState.value.likedPhotoIds + photoId
            } else {
                _uiState.value.likedPhotoIds - photoId
            },
        ) { photo ->
            photo.copy(
                likeCount = previousPhoto.likeCount,
                isLiked = previousPhoto.isLiked,
            )
        }
        _uiState.update { state ->
            state.copy(pendingMessage = nextToast(LIKE_ERROR_MESSAGE))
        }
    }

    private fun applyLikeState(
        photoId: String,
        likedPhotoIds: Set<String>,
        updatePhoto: (Post) -> Post,
    ) {
        _uiState.update { state ->
            state.withUpdatedPhoto(
                photoId = photoId,
                likedPhotoIds = likedPhotoIds,
                updatePhoto = updatePhoto,
            )
        }
        displayCache.replaceAll { _, entry ->
            val cacheLikedPhotoIds = if (entry.state.hasPhoto(photoId)) {
                if (photoId in likedPhotoIds) {
                    entry.state.likedPhotoIds + photoId
                } else {
                    entry.state.likedPhotoIds - photoId
                }
            } else {
                entry.state.likedPhotoIds
            }
            entry.copy(
                state = entry.state.withUpdatedPhoto(
                    photoId = photoId,
                    likedPhotoIds = cacheLikedPhotoIds,
                    updatePhoto = updatePhoto,
                ),
            )
        }
        cacheCurrentState()
    }

    private fun DisplayUiState.withAppliedLikeUpdatesAfter(revision: Int): DisplayUiState {
        val updates = likeUpdates.filterValues { it.revision > revision }
        if (updates.isEmpty()) return this

        return withUpdatedPhotos(
            likedPhotoIds = updates.entries.fold(likedPhotoIds) { updatedIds, (photoId, update) ->
                if (update.value.isLiked) {
                    updatedIds + photoId
                } else {
                    updatedIds - photoId
                }
            },
        ) { photo ->
            val update = updates[photo.id]?.value ?: return@withUpdatedPhotos photo
            photo.copy(
                likeCount = update.likeCount,
                isLiked = update.isLiked,
            )
        }
    }

    private fun cacheCurrentState() {
        val state = _uiState.value
        val date = state.selectedDate ?: return
        val sort = when (val content = state.content) {
            is DisplayContentState.Latest -> content.selectedSort
            is DisplayContentState.Archive -> PostSort.POPULAR
            else -> return
        }
        val key = DisplayCacheKey(date, sort)
        displayCache[key] = DisplayCacheEntry(
            state = state.copy(pendingMessage = null),
            firstPagePhotoIds = displayCache[key]?.firstPagePhotoIds
                ?: state.photos.mapTo(mutableSetOf(), Post::id),
        )
    }

    companion object {
        fun factory(initialDate: LocalDate? = null) = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as ChalkakApplication
                DisplayViewModel(
                    repository = application.appContainer.postRepository,
                    initialDate = initialDate,
                )
            }
        }

        val Factory = factory()

        private val KST: ZoneId = ZoneId.of("Asia/Seoul")
        private const val FEATURED_PHOTO_COUNT = 5
    }

    private fun nextToast(text: String): UiMessage.Toast = UiMessage.Toast(
        id = nextMessageId++,
        text = text,
    )
}

private val DisplayUiState.photos: List<Post>
    get() = when (val content = content) {
        is DisplayContentState.Latest -> content.photos
        is DisplayContentState.Archive -> content.photos
        else -> emptyList()
    }

private fun DisplayUiState.hasPhoto(photoId: String): Boolean = photos.any { it.id == photoId }

private fun DisplayUiState.withUpdatedPhoto(
    photoId: String,
    likedPhotoIds: Set<String>,
    updatePhoto: (Post) -> Post,
): DisplayUiState {
    if (photos.none { it.id == photoId }) return this

    fun List<Post>.updated(): List<Post> = map { photo ->
        if (photo.id == photoId) updatePhoto(photo) else photo
    }

    val updatedContent = when (val currentContent = content) {
        is DisplayContentState.Latest -> currentContent.copy(
            photos = currentContent.photos.updated(),
        )

        is DisplayContentState.Archive -> currentContent.copy(
            photos = currentContent.photos.updated(),
            featuredPhotos = currentContent.featuredPhotos.updated(),
        )

        else -> content
    }
    return copy(
        content = updatedContent,
        likedPhotoIds = likedPhotoIds,
    )
}

private fun DisplayUiState.withUpdatedPhotos(
    likedPhotoIds: Set<String>,
    updatePhoto: (Post) -> Post,
): DisplayUiState {
    fun List<Post>.updated(): List<Post> = map(updatePhoto)

    val updatedContent = when (val currentContent = content) {
        is DisplayContentState.Latest -> currentContent.copy(
            photos = currentContent.photos.updated(),
        )

        is DisplayContentState.Archive -> currentContent.copy(
            photos = currentContent.photos.updated(),
            featuredPhotos = currentContent.featuredPhotos.updated(),
        )

        else -> content
    }
    return copy(
        content = updatedContent,
        likedPhotoIds = likedPhotoIds,
    )
}

private data class DisplayLikeUpdate(
    val revision: Int,
    val value: HomeLike,
)

private data class DisplayCacheKey(
    val date: LocalDate,
    val sort: PostSort,
)

private data class DisplayCacheEntry(
    val state: DisplayUiState,
    val firstPagePhotoIds: Set<String>,
)

private const val DISPLAY_ERROR_MESSAGE = "전시를 불러오지 못했어요"
private const val LIKE_ERROR_MESSAGE = "좋아요를 반영하지 못했어요"
