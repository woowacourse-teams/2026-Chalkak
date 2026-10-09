package com.stonefive.chalkak.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stonefive.chalkak.ChalkakApplication
import com.stonefive.chalkak.domain.model.HomeFailure
import com.stonefive.chalkak.domain.model.HomeQuery
import com.stonefive.chalkak.domain.model.HomeResult
import com.stonefive.chalkak.domain.model.PostCalendarItem
import com.stonefive.chalkak.domain.model.PostSort
import com.stonefive.chalkak.domain.model.PostStatus
import com.stonefive.chalkak.domain.model.TodayPostModerationStatus
import com.stonefive.chalkak.domain.model.TodayPostStatusFailure
import com.stonefive.chalkak.domain.model.TodayPostStatusResult
import com.stonefive.chalkak.domain.model.UserSessionState
import com.stonefive.chalkak.domain.repository.PhotoUploadEntryRepository
import com.stonefive.chalkak.domain.repository.PostRepository
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: PostRepository,
    private val uploadEntryRepository: PhotoUploadEntryRepository,
    private val sessionState: StateFlow<UserSessionState>,
    private val dateProvider: () -> LocalDate = { LocalDate.now(ZoneId.of("Asia/Seoul")) },
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState(date = dateProvider()))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var hasBeenPresented = false

    init {
        viewModelScope.launch {
            sessionState.collectLatest { session ->
                if (session != UserSessionState.Loading) loadHome(reset = true)
            }
        }
    }

    fun refresh() {
        if (sessionState.value == UserSessionState.Loading || loadJob?.isActive == true) return
        loadHome(reset = false, showRefreshIndicator = true)
    }

    fun onResume() {
        if (!hasBeenPresented) {
            hasBeenPresented = true
            return
        }
        // Returning from upload/record revalidates ownership, moderation and the KST date.
        if (sessionState.value != UserSessionState.Loading && loadJob?.isActive != true) {
            loadHome(reset = false)
        }
    }

    private fun loadHome(
        reset: Boolean,
        showRefreshIndicator: Boolean = false,
    ) {
        loadJob?.cancel()
        val date = dateProvider()
        val authenticated = sessionState.value is UserSessionState.Authenticated
        val previous = _uiState.value
        _uiState.value = if (reset || previous.date != date) {
            HomeUiState(date = date, isAuthenticated = authenticated)
        } else {
            previous.copy(isRefreshing = showRefreshIndicator, isAuthenticated = authenticated)
        }
        loadJob = viewModelScope.launch {
            coroutineScope {
                launch { loadTodayRanking(date) }
                launch { loadYesterdayRanking(date.minusDays(1)) }
                launch { loadPersonalContent(date, authenticated) }
            }
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    private suspend fun loadTodayRanking(date: LocalDate) {
        val result = request {
            repository.getPostContent(HomeQuery(date, PostSort.POPULAR, HomeQuery.FIRST_PAGE))
        }
        _uiState.update { state ->
            when (result) {
                is HomeResult.Success -> state.copy(
                    topic = result.value.topic,
                    topicStatus = HomeSectionStatus.Ready,
                    trending = HomeRankingState(HomeSectionStatus.Ready, result.value.photos),
                )

                is HomeResult.Failure -> state.copy(
                    topic = "",
                    topicStatus = HomeSectionStatus.Error,
                    trending = HomeRankingState(
                        status = if (result.reason == HomeFailure.TopicNotFound) {
                            HomeSectionStatus.Ready
                        } else {
                            HomeSectionStatus.Error
                        },
                    ),
                )
            }
        }
    }

    private suspend fun loadYesterdayRanking(date: LocalDate) {
        val result = request {
            repository.getPostPage(HomeQuery(date, PostSort.POPULAR, HomeQuery.FIRST_PAGE))
        }
        _uiState.update { state ->
            state.copy(
                yesterday = when (result) {
                    is HomeResult.Success -> HomeRankingState(HomeSectionStatus.Ready, result.value.photos)

                    is HomeResult.Failure -> HomeRankingState(
                        status = if (result.reason == HomeFailure.Http(404)) {
                            HomeSectionStatus.Ready
                        } else {
                            HomeSectionStatus.Error
                        },
                    )
                },
            )
        }
    }

    private suspend fun loadPersonalContent(
        date: LocalDate,
        authenticated: Boolean,
    ) {
        if (!authenticated) {
            _uiState.update { it.copy(hero = HomeHeroState.Empty, recordsStatus = HomeSectionStatus.Ready) }
            return
        }
        val dates = homeWeekDates(date)
        val months = dates.map(YearMonth::from).distinct()
        val results = coroutineScope {
            months
                .map { month -> async { request { repository.getPostCalendar(month) } } }
                .map { it.await() }
        }
        val records = results.flatMap { result ->
            when (result) {
                is HomeResult.Success -> result.value.posts
                is HomeResult.Failure -> emptyList()
            }
        }
        val hasFailure = results.any { it is HomeResult.Failure }
        _uiState.update {
            it.copy(
                week = dates.map { day -> HomeWeekDay(day, records.firstOrNull { post -> post.topicDate == day }) },
                recordsStatus = if (hasFailure) HomeSectionStatus.Error else HomeSectionStatus.Ready,
            )
        }
        val todayRecord = records.firstOrNull { it.topicDate == date }
        val hero = if (todayRecord != null) {
            loadHero(todayRecord)
        } else if (hasFailure) {
            HomeHeroState.Error
        } else {
            loadMissingHero(date)
        }
        _uiState.update { it.copy(hero = hero) }
    }

    private suspend fun loadHero(record: PostCalendarItem): HomeHeroState {
        if (record.status == PostStatus.PENDING) {
            return HomeHeroState.Photo(imageUrl = record.thumbnailImageUrl, isPending = true)
        }
        return when (val detail = request { repository.getPostDetail(record.postId) }) {
            is HomeResult.Success -> HomeHeroState.Photo(
                imageUrl = detail.value.post.originalImageUrl,
                thumbnailUrl = record.thumbnailImageUrl,
                signatureUrl = detail.value.post.signatureOriginalImageUrl,
                signatureThumbnailUrl = detail.value.post.signatureThumbnailImageUrl,
            )

            // The calendar already proves ownership and provides a usable image.
            is HomeResult.Failure -> HomeHeroState.Photo(imageUrl = record.thumbnailImageUrl)
        }
    }

    private suspend fun loadMissingHero(date: LocalDate): HomeHeroState {
        val result = try {
            uploadEntryRepository.getTodayPostStatus().also { currentCoroutineContext().ensureActive() }
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            return HomeHeroState.Error
        }
        return when (result) {
            is TodayPostStatusResult.Success -> when {
                result.value.topicDate != date || !result.value.isPosted -> HomeHeroState.Empty

                result.value.moderationStatus == TodayPostModerationStatus.VALIDATING -> HomeHeroState.Processing

                // A posted result without a calendar image is not an empty upload slot.
                else -> HomeHeroState.Error
            }

            is TodayPostStatusResult.Failure -> if (result.reason == TodayPostStatusFailure.NoOpenTopic) {
                HomeHeroState.Empty
            } else {
                HomeHeroState.Error
            }
        }
    }

    private suspend fun <T> request(block: suspend () -> HomeResult<T>): HomeResult<T> = try {
        block().also { currentCoroutineContext().ensureActive() }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        HomeResult.Failure(HomeFailure.Network)
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as ChalkakApplication
                HomeViewModel(
                    repository = application.appContainer.postRepository,
                    uploadEntryRepository = application.appContainer.photoUploadEntryRepository,
                    sessionState = application.appContainer.authRepository.sessionState,
                )
            }
        }
    }
}
