package com.stonefive.chalkak.data.repository

import com.stonefive.chalkak.data.remote.ApiError
import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.feedback.FeedbackDataSource
import com.stonefive.chalkak.data.remote.feedback.model.FeedbackSubmissionResponse
import com.stonefive.chalkak.domain.model.FeedbackSubmissionException
import com.stonefive.chalkak.domain.model.FeedbackSubmissionFailure
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeedbackRepositoryImplTest {
    private val feedbackDataSource = FakeFeedbackDataSource()
    private val repository = FeedbackRepositoryImpl(feedbackDataSource)

    @Test
    fun `내용의 공백을 제거한 뒤 제출한다`() = runTest {
        repository.submitFeedback("  피드백 내용  ")

        assertEquals("피드백 내용", feedbackDataSource.submittedContent)
    }

    @Test
    fun `빈 내용이면 잘못된 내용 오류를 던진다`() = runTest {
        val error = runCatching { repository.submitFeedback("   ") }.exceptionOrNull()

        assertEquals(
            FeedbackSubmissionFailure.INVALID_CONTENT,
            (error as FeedbackSubmissionException).reason,
        )
        assertNull(feedbackDataSource.submittedContent)
    }

    @Test
    fun `최대 길이를 초과하면 잘못된 내용 오류를 던진다`() = runTest {
        val error = runCatching { repository.submitFeedback("가".repeat(1_001)) }.exceptionOrNull()

        assertEquals(
            FeedbackSubmissionFailure.INVALID_CONTENT,
            (error as FeedbackSubmissionException).reason,
        )
        assertNull(feedbackDataSource.submittedContent)
    }

    @Test
    fun `401 응답을 인증 오류로 변환한다`() = runTest {
        feedbackDataSource.result = ApiResult.Failure(ApiError.Http(401, null))

        val error = runCatching { repository.submitFeedback("피드백") }.exceptionOrNull()

        assertEquals(
            FeedbackSubmissionFailure.UNAUTHORIZED,
            (error as FeedbackSubmissionException).reason,
        )
    }

    @Test
    fun `400 응답을 잘못된 내용 오류로 변환한다`() = runTest {
        feedbackDataSource.result = ApiResult.Failure(ApiError.Http(400, "INVALID"))

        val error = runCatching { repository.submitFeedback("피드백") }.exceptionOrNull()

        assertEquals(
            FeedbackSubmissionFailure.INVALID_CONTENT,
            (error as FeedbackSubmissionException).reason,
        )
    }

    @Test
    fun `네트워크 오류를 네트워크 실패로 변환한다`() = runTest {
        feedbackDataSource.result = ApiResult.Failure(ApiError.Network)

        val error = runCatching { repository.submitFeedback("피드백") }.exceptionOrNull()

        assertEquals(
            FeedbackSubmissionFailure.NETWORK,
            (error as FeedbackSubmissionException).reason,
        )
    }
}

private class FakeFeedbackDataSource : FeedbackDataSource {
    var submittedContent: String? = null
    var result: ApiResult<FeedbackSubmissionResponse> = ApiResult.Success(
        FeedbackSubmissionResponse(
            feedbackId = "feedback-id",
            createdAt = "2026-09-16T00:00:00Z",
        ),
    )

    override suspend fun submitFeedback(content: String): ApiResult<FeedbackSubmissionResponse> {
        submittedContent = content
        return result
    }
}
