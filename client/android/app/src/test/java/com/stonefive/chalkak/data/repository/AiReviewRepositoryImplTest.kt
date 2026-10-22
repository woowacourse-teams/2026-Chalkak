package com.stonefive.chalkak.data.repository

import com.stonefive.chalkak.data.remote.ApiError
import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.aireview.AiReviewDataSource
import com.stonefive.chalkak.data.remote.aireview.model.AiReviewCriterionResponse
import com.stonefive.chalkak.data.remote.aireview.model.AiReviewFeedbackResponse
import com.stonefive.chalkak.data.remote.aireview.model.AiReviewResponse
import com.stonefive.chalkak.data.remote.aireview.model.AiReviewResultResponse
import com.stonefive.chalkak.domain.model.AiReview
import com.stonefive.chalkak.domain.repository.AiReviewFailure
import com.stonefive.chalkak.domain.repository.AiReviewResponseResult
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiReviewRepositoryImplTest {
    @Test
    fun `완료 결과의 모든 필드를 domain으로 매핑한다`() = runTest {
        val repository = repositoryWith(
            ApiResult.Success(
                AiReviewResponse(
                    status = "COMPLETED",
                    requestedAt = "requested",
                    completedAt = "completed",
                    result = completedResult,
                ),
            ),
        )
        val result = repository.getReview("post-id") as AiReviewResponseResult.Success
        assertEquals(AiReview.Status.COMPLETED, result.review.status)
        assertEquals("requested", result.review.requestedAt)
        assertEquals("completed", result.review.completedAt)
        assertEquals(
            7.5,
            result.review.result
                ?.overallScore,
        )
        assertEquals(
            "COMPOSITION",
            result.review.result
                ?.criteria
                ?.single()
                ?.key,
        )
        assertEquals(
            "구도",
            result.review.result
                ?.criteria
                ?.single()
                ?.label,
        )
        assertEquals(
            7.5,
            result.review.result
                ?.criteria
                ?.single()
                ?.score,
        )
        assertEquals(
            true,
            result.review.result
                ?.distinctivePerspective,
        )
        assertEquals(
            "요약",
            result.review.result
                ?.feedback
                ?.summary,
        )
        assertEquals(
            "강점",
            result.review.result
                ?.feedback
                ?.strength,
        )
        assertEquals(
            "아쉬움",
            result.review.result
                ?.feedback
                ?.weakness,
        )
        assertEquals(
            "제안",
            result.review.result
                ?.feedback
                ?.suggestion,
        )
    }

    @Test
    fun `미완료 상태는 결과 없이 매핑하고 canRequestAgain 기본값은 false다`() = runTest {
        val repository = repositoryWith(ApiResult.Success(AiReviewResponse(status = "NONE")))
        val result = repository.getReview("post-id") as AiReviewResponseResult.Success
        assertEquals(AiReview.Status.NONE, result.review.status)
        assertEquals(null, result.review.result)
        assertEquals(false, result.review.canRequestAgain)
    }

    @Test
    fun `알 수 없는 상태와 상태에 맞지 않는 결과는 InvalidResponse다`() = runTest {
        val unknown = repositoryWith(ApiResult.Success(AiReviewResponse(status = "UNKNOWN")))
        val completedWithoutResult = repositoryWith(ApiResult.Success(AiReviewResponse(status = "COMPLETED")))
        val processingWithResult =
            repositoryWith(ApiResult.Success(AiReviewResponse(status = "PROCESSING", result = completedResult)))
        assertInvalidResponse(unknown.getReview("post-id"))
        assertInvalidResponse(completedWithoutResult.getReview("post-id"))
        assertInvalidResponse(processingWithResult.getReview("post-id"))
    }

    @Test
    fun `HTTP 오류의 상태 코드와 서버 메시지를 보존한다`() = runTest {
        listOf(400, 401, 403, 404).forEach { statusCode ->
            val repository = repositoryWith(ApiResult.Failure(ApiError.Http(statusCode, "BUSINESS_ERROR", "안내 문구")))
            val result = repository.getReview("post-id") as AiReviewResponseResult.Failure
            assertEquals(AiReviewFailure.Http(statusCode, "BUSINESS_ERROR", "안내 문구"), result.error)
        }
    }

    @Test
    fun `네트워크와 역직렬화 오류를 보존한다`() = runTest {
        assertEquals(
            AiReviewFailure.Network,
            (
                repositoryWith(
                    ApiResult.Failure(ApiError.Network),
                ).getReview("post-id") as AiReviewResponseResult.Failure
                ).error,
        )
        assertEquals(
            AiReviewFailure.InvalidResponse,
            (
                repositoryWith(
                    ApiResult.Failure(ApiError.InvalidResponse),
                ).getReview("post-id") as AiReviewResponseResult.Failure
                ).error,
        )
    }

    private fun repositoryWith(result: ApiResult<AiReviewResponse>) =
        AiReviewRepositoryImpl(FakeAiReviewDataSource(result))

    private fun assertInvalidResponse(result: AiReviewResponseResult) {
        assertTrue(result is AiReviewResponseResult.Failure)
        assertEquals(AiReviewFailure.InvalidResponse, (result as AiReviewResponseResult.Failure).error)
    }

    private class FakeAiReviewDataSource(private val result: ApiResult<AiReviewResponse>) : AiReviewDataSource {
        override suspend fun getReview(postId: String) = result
        override suspend fun requestReview(postId: String) = result
    }

    private companion object {
        val completedResult = AiReviewResultResponse(
            overallScore = 7.5,
            criteria = listOf(AiReviewCriterionResponse("COMPOSITION", "구도", 7.5)),
            distinctivePerspective = true,
            feedback = AiReviewFeedbackResponse("요약", "강점", "아쉬움", "제안"),
        )
    }
}
