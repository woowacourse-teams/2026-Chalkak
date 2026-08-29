package com.stonefive.chalkak.feature.display

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
    private var nextPageJob: Job? = null

    init {
        loadDisplay(date = initialDate)
    }

    fun moveToPreviousDate() {
        val state = _uiState.value
        val selectedDate = state.selectedDate ?: return
        val targetDate = state.earliestDate
            ?.let { selectedDate.minusDays(1).coerceAtLeast(it) }
            ?: selectedDate.minusDays(1)
        if (targetDate == selectedDate) return

        startDateChange(targetDate, state)
    }

    fun moveToNextDate() {
        val state = _uiState.value
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

        selectedSort = sort
        _uiState.update { it.copy(content = DisplayContentState.Loading) }
        loadDisplay(state.selectedDate)
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
    ) {
        val latestDate = dateProvider()
        val requestedDate = date ?: latestDate
        val requestedSort = if (requestedDate < latestDate) PostSort.POPULAR else sort
        val generation = ++latestLoadGeneration
        nextPageJob?.cancel()
        nextPageJob = null
        isEndThresholdReached = false

        viewModelScope.launch {
            _uiState.update { it.copy(content = DisplayContentState.Loading) }
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
                is HomeResult.Success -> applyFirstPage(result.value, latestDate)

                is HomeResult.Failure -> if (
                    result.reason == HomeFailure.TopicNotFound && previousState != null
                ) {
                    val isPreviousDateRequest = requireNotNull(date) < requireNotNull(previousState.selectedDate)
                    _uiState.update {
                        it.copy(
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
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            content = DisplayContentState.Error(
                                message = "전시를 불러오지 못했어요",
                            ),
                        )
                    }
                }
            }
        }
    }

    private fun applyFirstPage(
        postContent: PostContent,
        latestDate: LocalDate,
    ) {
        val earliestDate = _uiState.value.earliestDate
        loadedDate = postContent.topicDate
        val content = if (postContent.topicDate < latestDate) {
            DisplayContentState.Archive(
                photos = postContent.photos,
                featuredPhotos = postContent.photos.take(FEATURED_PHOTO_COUNT),
            )
        } else {
            DisplayContentState.Latest(
                photos = postContent.photos,
                selectedSort = selectedSort,
            )
        }
        _uiState.value = DisplayUiState(
            selectedDate = postContent.topicDate,
            latestDate = latestDate,
            earliestDate = earliestDate,
            topic = postContent.topic,
            content = content,
            likedPhotoIds = postContent.likedPhotoIds,
            currentPage = postContent.currentPage,
            hasNext = postContent.hasNext,
            randomSeed = postContent.randomSeed,
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
        if (!state.hasNext || state.isLoadingNext || nextPageJob != null) return

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
        nextPageJob = viewModelScope.launch {
            val result = try {
                repository.getPostPage(query)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (_: Exception) {
                HomeResult.Failure(HomeFailure.Network)
            }
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
            state.copy(
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
            )
        }
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
}
