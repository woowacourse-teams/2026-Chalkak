package com.stonefive.chalkak.feature.aireview

import com.stonefive.chalkak.domain.model.AiReview
import com.stonefive.chalkak.domain.repository.AiReviewFailure
import com.stonefive.chalkak.domain.repository.AiReviewRepository
import com.stonefive.chalkak.domain.repository.AiReviewResponseResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class AiReviewSession(
    private val scope: CoroutineScope,
    private val repository: AiReviewRepository,
    private val currentUserId: () -> String?,
    private val onStateChanged: (AiReviewUiState) -> Unit = {},
    private val canRequestReview: () -> Boolean = { true },
) {
    private val _state = MutableStateFlow(AiReviewUiState())
    val state: StateFlow<AiReviewUiState> = _state.asStateFlow()

    private var targetPostId: String? = null
    private var targetUserId: String? = null
    private var visible = false
    private var submitting = false
    private var generation = 0
    private var job: Job? = null

    fun setTarget(postId: String?) {
        val userId = currentUserId()
        if (targetPostId == postId && targetUserId == userId) return
        cancelWork()
        targetPostId = postId
        targetUserId = userId
        updateState(AiReviewUiState())
    }

    fun onResume() {
        visible = true
        resetIfUserChanged()
        refresh()
    }

    fun onPause() {
        visible = false
        cancelWork()
        if (_state.value.message != null) updateState(_state.value.copy(message = null))
    }

    fun refresh() {
        resetIfUserChanged()
        if (!visible || submitting || job?.isActive == true || targetPostId == null) return
        if (_state.value.phase == AiReviewPhase.Ready) {
            updateState(_state.value.copy(phase = AiReviewPhase.Hidden))
        }
        launchReview(requestReview = false)
    }

    fun startReview() {
        resetIfUserChanged()
        if (!visible || submitting || job?.isActive == true || !_state.value.canStartReview()) return
        if (!canRequestReview()) {
            updateState(_state.value.copy(phase = AiReviewPhase.Hidden))
            return
        }
        val postId = targetPostId ?: return
        val userId = currentUserId() ?: return
        if (targetUserId != userId) return
        val startPhase = _state.value.phase
        updateState(_state.value.copy(phase = AiReviewPhase.Processing, message = null))
        launchReview(requestReview = true, startPhase = startPhase)
    }

    fun dismissMessage() {
        updateState(_state.value.copy(message = null))
    }

    private fun launchReview(
        requestReview: Boolean,
        startPhase: AiReviewPhase = AiReviewPhase.Ready,
    ) {
        val postId = targetPostId ?: return
        val userId = currentUserId() ?: return
        cancelWork()
        val requestGeneration = generation
        submitting = requestReview
        job = scope.launch {
            try {
                var failureNotified = false
                withTimeoutOrNull(POLL_WINDOW_MS) {
                    var response = if (requestReview) {
                        repository.requestReview(postId)
                    } else {
                        repository.getReview(postId)
                    }
                    ensureCurrent(postId, userId, requestGeneration)
                    submitting = false
                    if (requestReview && response is AiReviewResponseResult.Failure) {
                        when (val error = response.error) {
                            is AiReviewFailure.Http -> {
                                updateState(
                                    _state.value.copy(
                                        phase = if (error.statusCode == 404) {
                                            AiReviewPhase.Hidden
                                        } else {
                                            startPhase
                                        },
                                        message = error.message ?: errorMessage(error),
                                    ),
                                )
                                return@withTimeoutOrNull
                            }

                            else -> {
                                updateState(_state.value.copy(message = errorMessage(error)))
                                failureNotified = true
                                response = repository.getReview(postId)
                                ensureCurrent(postId, userId, requestGeneration)
                            }
                        }
                    }
                    while (true) {
                        when (response) {
                            is AiReviewResponseResult.Success -> {
                                failureNotified = false
                                show(response.review)
                                if (response.review.status != AiReview.Status.PROCESSING) {
                                    return@withTimeoutOrNull
                                }
                            }

                            is AiReviewResponseResult.Failure -> {
                                val isMissing = (response.error as? AiReviewFailure.Http)?.statusCode == 404
                                val keepPolling = _state.value.phase == AiReviewPhase.Processing &&
                                    response.error.isTransient()
                                if (!keepPolling || !failureNotified) {
                                    updateState(
                                        _state.value.copy(
                                            phase = if (isMissing) AiReviewPhase.Hidden else _state.value.phase,
                                            message = errorMessage(response.error),
                                        ),
                                    )
                                }
                                if (!keepPolling) return@withTimeoutOrNull
                                failureNotified = true
                            }
                        }
                        delay(POLL_INTERVAL_MS)
                        response = repository.getReview(postId)
                        ensureCurrent(postId, userId, requestGeneration)
                    }
                }
            } finally {
                if (requestGeneration == generation) submitting = false
            }
        }
    }

    private fun show(review: AiReview) {
        val phase = when (review.status) {
            AiReview.Status.NONE -> if (canRequestReview()) AiReviewPhase.Ready else AiReviewPhase.Hidden
            AiReview.Status.PROCESSING -> AiReviewPhase.Processing
            AiReview.Status.COMPLETED -> AiReviewPhase.Completed
            AiReview.Status.FAILED -> if (canRequestReview()) AiReviewPhase.Failed else AiReviewPhase.Hidden
        }
        updateState(
            AiReviewUiState(
                phase = phase,
                result = review.result,
                status = review.status,
                canRequestAgain = review.canRequestAgain,
            ),
        )
    }

    private fun AiReviewUiState.canStartReview(): Boolean =
        phase == AiReviewPhase.Ready || (phase == AiReviewPhase.Failed && canRequestAgain)

    private suspend fun ensureCurrent(
        postId: String,
        userId: String,
        requestGeneration: Int,
    ) {
        currentCoroutineContext().ensureActive()
        if (!visible || requestGeneration != generation || targetPostId != postId || currentUserId() != userId) {
            throw CancellationException("AI review target changed")
        }
    }

    private fun resetIfUserChanged() {
        if (targetUserId != currentUserId()) {
            cancelWork()
            targetUserId = currentUserId()
            updateState(AiReviewUiState())
        }
    }

    private fun cancelWork() {
        generation++
        job?.cancel()
        job = null
        submitting = false
    }

    private fun updateState(newState: AiReviewUiState) {
        _state.value = newState
        onStateChanged(newState)
    }

    private fun AiReviewFailure.isTransient(): Boolean =
        this == AiReviewFailure.Network || (this is AiReviewFailure.Http && statusCode >= 500)

    private fun errorMessage(error: AiReviewFailure): String = when (error) {
        AiReviewFailure.Network -> "네트워크 연결을 확인해 주세요."

        AiReviewFailure.InvalidResponse -> "AI 피드백 정보를 불러오지 못했어요."

        is AiReviewFailure.Http -> when (error.statusCode) {
            401 -> "다시 로그인해 주세요."
            403 -> "이용이 정지된 회원은 AI 리뷰를 요청할 수 없습니다."
            404 -> "AI 피드백을 확인할 수 없는 게시물이에요."
            else -> "AI 피드백 정보를 불러오지 못했어요."
        }
    }

    companion object {
        const val POLL_INTERVAL_MS = 3_000L
        const val POLL_WINDOW_MS = 300_000L
    }
}
