package com.stonefive.chalkak.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.stonefive.chalkak.ChalkakApplication
import com.stonefive.chalkak.domain.model.AiReview
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
import com.stonefive.chalkak.domain.repository.AiReviewFailure
import com.stonefive.chalkak.domain.repository.AiReviewRepository
import com.stonefive.chalkak.domain.repository.AiReviewResponseResult
import com.stonefive.chalkak.domain.repository.BannerRepository
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
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class HomeViewModel(
    private val repository: PostRepository,
    private val uploadEntryRepository: PhotoUploadEntryRepository,
    private val sessionState: StateFlow<UserSessionState>,
    private val bannerRepository: BannerRepository,
    private val aiReviewRepository: AiReviewRepository,
    private val dateProvider: () -> LocalDate = { LocalDate.now(ZoneId.of("Asia/Seoul")) },
) : ViewModel() {
    private val _uiState = MutableStateFlow(HomeUiState(date = dateProvider()))
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()
    private var loadJob: Job? = null
    private var feedbackJob: Job? = null
    private var isFeedbackVisible = false
    private var isFeedbackSubmitting = false
    private var feedbackGeneration = 0
    private var feedbackOwnerUserId: String? = null
    private var bannerLoadMoreJob: Job? = null
    private var hasBeenPresented = false
    private val isPreloadingEnabled = MutableStateFlow(false)
    private val _isReadyForInitialDisplay = MutableStateFlow(false)
    val isReadyForInitialDisplay: StateFlow<Boolean> = _isReadyForInitialDisplay.asStateFlow()

    init {
        viewModelScope.launch {
            combine(sessionState, isPreloadingEnabled) { session, enabled -> session to enabled }
                .collectLatest { (session, enabled) ->
                    if (!enabled) return@collectLatest
                    when {
                        session.canLoadHome() -> loadHome(reset = true)

                        session == UserSessionState.SignedOut -> {
                            loadJob?.cancel()
                            bannerLoadMoreJob?.cancel()
                            resetState(HomeUiState(date = dateProvider()))
                        }

                        else -> {
                            loadJob?.cancel()
                            bannerLoadMoreJob?.cancel()
                            resetState(HomeUiState(date = dateProvider()))
                        }
                    }
                }
        }
    }

    fun startPreloading() {
        if (isPreloadingEnabled.value) return
        isPreloadingEnabled.value = true
        viewModelScope.launch {
            withTimeoutOrNull(HOME_PRELOAD_MAX_WAIT_MS) {
                val session = sessionState.first { it != UserSessionState.Loading }
                if (session.canLoadHome()) {
                    uiState.first { it.isInitialLoadComplete }
                }
            }
            _isReadyForInitialDisplay.value = true
        }
    }

    fun refresh() {
        if (!sessionState.value.canLoadHome() || loadJob?.isActive == true) return
        loadHome(reset = false, showRefreshIndicator = true)
    }

    fun onResume() {
        isFeedbackVisible = true
        val isFirstPresentation = !hasBeenPresented
        hasBeenPresented = true
        if (!sessionState.value.canLoadHome()) return
        if (_uiState.value.date != dateProvider()) {
            loadHome(reset = true)
        } else if (!isFirstPresentation && loadJob?.isActive != true) {
            loadHome(reset = false)
        } else if (loadJob?.isActive != true) {
            syncFeedback()
        }
    }

    fun onPause() {
        isFeedbackVisible = false
        stopFeedback()
    }

    fun dismissFeedbackMessage() {
        _uiState.update { it.copy(feedbackMessage = null) }
    }

    fun startFeedback() {
        val state = _uiState.value
        val postId = feedbackPostId() ?: return
        val userId = (sessionState.value as? UserSessionState.Authenticated)?.userId ?: return
        if (!isFeedbackVisible || state.feedbackPhase != HomeFeedbackPhase.Ready || isFeedbackSubmitting ||
            feedbackOwnerUserId != userId
        ) {
            return
        }
        // POST 응답 유실 시에도 다시 누를 수 없도록, 서버 상태를 확인할 때까지 분석 중으로 둔다.
        _uiState.update { it.copy(feedbackPhase = HomeFeedbackPhase.Processing, feedbackMessage = null) }
        launchFeedback(postId, requestReview = true)
    }

    private fun feedbackPostId(): String? = (_uiState.value.hero as? HomeHeroState.Photo)
        ?.takeUnless { it.isPending }
        ?.postId

    private fun stopFeedback() {
        feedbackGeneration++
        feedbackOwnerUserId = null
        feedbackJob?.cancel()
        feedbackJob = null
        isFeedbackSubmitting = false
    }

    private fun syncFeedback() {
        if (!isFeedbackVisible || isFeedbackSubmitting) return
        val postId = feedbackPostId() ?: return
        if (_uiState.value.feedbackPhase == HomeFeedbackPhase.Ready) {
            _uiState.update { it.copy(feedbackPhase = HomeFeedbackPhase.Hidden) }
        }
        launchFeedback(postId, requestReview = false)
    }

    private fun launchFeedback(
        postId: String,
        requestReview: Boolean,
    ) {
        val userId = (sessionState.value as? UserSessionState.Authenticated)?.userId ?: return
        stopFeedback()
        val generation = feedbackGeneration
        isFeedbackSubmitting = requestReview
        feedbackJob = viewModelScope.launch {
            try {
                // 요청/진입 시점부터 경과시간을 재므로 최초 requestedAt 및 기기 시각 변경과 무관하다.
                withTimeoutOrNull(FEEDBACK_POLL_WINDOW_MS) {
                    var response = feedbackRequest {
                        if (requestReview) {
                            aiReviewRepository.requestReview(
                                postId,
                            )
                        } else {
                            aiReviewRepository.getReview(postId)
                        }
                    }
                    ensureFeedbackCurrent(postId, userId, generation)
                    feedbackOwnerUserId = userId
                    isFeedbackSubmitting = false
                    if (requestReview && response is AiReviewResponseResult.Failure) {
                        val error = response.error
                        if (error is AiReviewFailure.Http) {
                            val phase = if (error.statusCode ==
                                404
                            ) {
                                HomeFeedbackPhase.Hidden
                            } else {
                                HomeFeedbackPhase.Ready
                            }
                            _uiState.update {
                                it.copy(
                                    feedbackPhase = phase,
                                    feedbackMessage = error.message ?: feedbackErrorMessage(error),
                                )
                            }
                            return@withTimeoutOrNull
                        }
                        // 접수 여부를 모르는 POST는 재전송하지 않고 GET으로 실제 상태만 확인한다.
                        _uiState.update { it.copy(feedbackMessage = feedbackErrorMessage(error)) }
                        response = feedbackRequest { aiReviewRepository.getReview(postId) }
                        ensureFeedbackCurrent(postId, userId, generation)
                    }
                    while (true) {
                        when (val current = response) {
                            is AiReviewResponseResult.Success -> {
                                showFeedback(current.review)
                                if (current.review.status != AiReview.Status.PROCESSING) return@withTimeoutOrNull
                            }

                            is AiReviewResponseResult.Failure -> {
                                val postMissing = (current.error as? AiReviewFailure.Http)?.statusCode == 404
                                _uiState.update {
                                    it.copy(
                                        feedbackPhase = if (postMissing) HomeFeedbackPhase.Hidden else it.feedbackPhase,
                                        feedbackMessage = feedbackErrorMessage(current.error),
                                    )
                                }
                                return@withTimeoutOrNull
                            }
                        }
                        delay(FEEDBACK_POLL_INTERVAL_MS)
                        response = feedbackRequest { aiReviewRepository.getReview(postId) }
                        ensureFeedbackCurrent(postId, userId, generation)
                    }
                }
                // 5分超過では分析中表示を保ち、追加UIやPOSTは行わない。
            } finally {
                if (generation == feedbackGeneration) isFeedbackSubmitting = false
            }
        }
    }

    private suspend fun ensureFeedbackCurrent(
        postId: String,
        userId: String,
        generation: Int,
    ) {
        currentCoroutineContext().ensureActive()
        if (!isFeedbackVisible || generation != feedbackGeneration || feedbackPostId() != postId ||
            (sessionState.value as? UserSessionState.Authenticated)?.userId != userId
        ) {
            throw CancellationException("AI review target changed")
        }
    }

    private fun showFeedback(review: AiReview) {
        _uiState.update {
            it.copy(
                feedbackPhase = when (review.status) {
                    AiReview.Status.NONE -> HomeFeedbackPhase.Ready

                    AiReview.Status.PROCESSING -> HomeFeedbackPhase.Processing

                    AiReview.Status.COMPLETED -> HomeFeedbackPhase.Completed

                    // 이번 범위에는 실패·재요청 UI를 추가하지 않는다.
                    AiReview.Status.FAILED -> HomeFeedbackPhase.Hidden
                },
                feedbackResult = review.result,
                feedbackMessage = null,
            )
        }
    }

    private suspend fun feedbackRequest(block: suspend () -> AiReviewResponseResult): AiReviewResponseResult =
        block().also { currentCoroutineContext().ensureActive() }

    private fun feedbackErrorMessage(error: AiReviewFailure): String = when (error) {
        AiReviewFailure.Network -> "네트워크 연결을 확인해 주세요."

        AiReviewFailure.InvalidResponse -> "AI 피드백 정보를 불러오지 못했어요."

        is AiReviewFailure.Http -> when (error.statusCode) {
            401 -> "다시 로그인해 주세요."
            403 -> "이용이 정지된 회원은 AI 리뷰를 요청할 수 없습니다."
            404 -> "AI 피드백을 확인할 수 없는 게시물이에요."
            else -> "AI 피드백 정보를 불러오지 못했어요."
        }
    }

    private fun resetState(state: HomeUiState) {
        stopFeedback()
        _uiState.value = state
    }

    private fun loadHome(
        reset: Boolean,
        showRefreshIndicator: Boolean = false,
    ) {
        loadJob?.cancel()
        bannerLoadMoreJob?.cancel()
        val date = dateProvider()
        val authenticated = sessionState.value is UserSessionState.Authenticated
        val previous = _uiState.value
        if (reset || previous.date != date) {
            resetState(HomeUiState(date = date, isAuthenticated = authenticated))
        } else {
            _uiState.value = previous.copy(
                isRefreshing = showRefreshIndicator,
                isAuthenticated = authenticated,
                bannerStatus = HomeSectionStatus.Loading,
                isLoadingMoreBanners = false,
                bannerLoadMoreError = false,
            )
        }
        loadJob = viewModelScope.launch {
            coroutineScope {
                launch { loadTodayRanking(date) }
                launch { loadYesterdayRanking(date.minusDays(1)) }
                launch { loadPersonalContent(date, authenticated) }
                launch { loadBanner() }
            }
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    private suspend fun loadBanner() {
        val result = request { bannerRepository.getBanners(page = 1, pageSize = BANNER_PAGE_SIZE) }
        _uiState.update { state ->
            when (result) {
                is HomeResult.Success -> state.copy(
                    banners = result.value.banners
                        .distinctBy { it.id },
                    bannerStatus = HomeSectionStatus.Ready,
                    bannerCurrentPage = result.value.currentPage,
                    bannerHasNext = result.value.hasNext && result.value.banners
                        .isNotEmpty(),
                )

                is HomeResult.Failure -> state.copy(bannerStatus = HomeSectionStatus.Error)
            }
        }
    }

    fun loadMoreBanners() {
        val state = _uiState.value
        if (!sessionState.value.canLoadHome() ||
            state.bannerStatus != HomeSectionStatus.Ready ||
            !state.bannerHasNext || state.isRefreshing || state.isLoadingMoreBanners
        ) {
            return
        }
        val nextPage = state.bannerCurrentPage + 1
        _uiState.update { it.copy(isLoadingMoreBanners = true, bannerLoadMoreError = false) }
        bannerLoadMoreJob = viewModelScope.launch {
            val result = request { bannerRepository.getBanners(page = nextPage, pageSize = BANNER_PAGE_SIZE) }
            _uiState.update { current ->
                when (result) {
                    is HomeResult.Success -> current.copy(
                        banners = (current.banners + result.value.banners).distinctBy { it.id },
                        bannerCurrentPage = result.value.currentPage,
                        bannerHasNext = result.value.hasNext && result.value.banners
                            .isNotEmpty() &&
                            result.value.currentPage >= nextPage,
                        isLoadingMoreBanners = false,
                    )

                    is HomeResult.Failure -> current.copy(
                        isLoadingMoreBanners = false,
                        bannerLoadMoreError = true,
                    )
                }
            }
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
                    topicStatus = if (result.reason == HomeFailure.TopicNotFound) {
                        HomeSectionStatus.Ready
                    } else {
                        HomeSectionStatus.Error
                    },
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
        val previousPostId = feedbackPostId()
        val nextPostId = (hero as? HomeHeroState.Photo)?.takeUnless { it.isPending }?.postId
        if (previousPostId != nextPostId) {
            stopFeedback()
            _uiState.update {
                it.copy(feedbackPhase = HomeFeedbackPhase.Hidden, feedbackResult = null, feedbackMessage = null)
            }
        }
        _uiState.update { it.copy(hero = hero) }
        syncFeedback()
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
                postId = record.postId,
            )

            // The calendar already proves ownership and provides a usable image.
            is HomeResult.Failure -> HomeHeroState.Photo(imageUrl = record.thumbnailImageUrl, postId = record.postId)
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
                HomeHeroState.NoOpenTopic
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
        private const val HOME_PRELOAD_MAX_WAIT_MS = 700L
        internal const val FEEDBACK_POLL_INTERVAL_MS = 3_000L
        internal const val FEEDBACK_POLL_WINDOW_MS = 300_000L
        private const val BANNER_PAGE_SIZE = 10

        val Factory = viewModelFactory {
            initializer {
                val application = this[APPLICATION_KEY] as ChalkakApplication
                HomeViewModel(
                    repository = application.appContainer.postRepository,
                    uploadEntryRepository = application.appContainer.photoUploadEntryRepository,
                    sessionState = application.appContainer.authRepository.sessionState,
                    bannerRepository = application.appContainer.bannerRepository,
                    aiReviewRepository = application.appContainer.aiReviewRepository,
                )
            }
        }
    }
}

private fun UserSessionState.canLoadHome(): Boolean =
    this == UserSessionState.Guest || this is UserSessionState.Authenticated
