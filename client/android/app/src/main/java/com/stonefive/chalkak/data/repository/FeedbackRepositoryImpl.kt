package com.stonefive.chalkak.data.repository

import com.stonefive.chalkak.data.remote.ApiError
import com.stonefive.chalkak.data.remote.ApiResult
import com.stonefive.chalkak.data.remote.feedback.FeedbackDataSource
import com.stonefive.chalkak.data.remote.feedback.model.FeedbackDetailResponse
import com.stonefive.chalkak.data.remote.feedback.model.FeedbackListResponse
import com.stonefive.chalkak.data.remote.feedback.model.FeedbackSummaryResponse
import com.stonefive.chalkak.domain.model.FeedbackAnswer
import com.stonefive.chalkak.domain.model.FeedbackInquiry
import com.stonefive.chalkak.domain.model.FeedbackInquiryDetail
import com.stonefive.chalkak.domain.model.FeedbackInquiryException
import com.stonefive.chalkak.domain.model.FeedbackInquiryFailure
import com.stonefive.chalkak.domain.model.FeedbackInquiryPage
import com.stonefive.chalkak.domain.model.FeedbackSubmissionException
import com.stonefive.chalkak.domain.model.FeedbackSubmissionFailure
import com.stonefive.chalkak.domain.repository.FeedbackRepository

class FeedbackRepositoryImpl(private val feedbackDataSource: FeedbackDataSource) : FeedbackRepository {
    override suspend fun submitFeedback(content: String) {
        val normalizedContent = content.trim()
        // 서버와 같이 Unicode code point로 센다. 이모지 1개는 UTF-16 2자지만 1자다.
        val contentLength = normalizedContent.codePointCount(0, normalizedContent.length)
        if (contentLength == 0 || contentLength > MAX_CONTENT_LENGTH) {
            throw FeedbackSubmissionException(FeedbackSubmissionFailure.INVALID_CONTENT)
        }

        when (val result = feedbackDataSource.submitFeedback(normalizedContent)) {
            is ApiResult.Success -> Unit
            is ApiResult.Failure -> throw result.error.toSubmissionException()
        }
    }

    override suspend fun getFeedbacks(page: Int): FeedbackInquiryPage = feedbackDataSource
        .getFeedbacks(page = page, pageSize = PAGE_SIZE)
        .getOrThrow()
        .toDomain()

    override suspend fun getFeedbackDetail(feedbackId: String): FeedbackInquiryDetail = feedbackDataSource
        .getFeedbackDetail(feedbackId)
        .getOrThrow()
        .toDomain()

    override suspend fun deleteFeedback(feedbackId: String) {
        feedbackDataSource.deleteFeedback(feedbackId).getOrThrow()
    }

    private fun ApiError.toSubmissionException(): FeedbackSubmissionException = when (this) {
        ApiError.Network -> FeedbackSubmissionException(FeedbackSubmissionFailure.NETWORK)

        ApiError.InvalidResponse -> FeedbackSubmissionException(FeedbackSubmissionFailure.UNKNOWN)

        // 400은 내용 오류와 접수 제한(1분 1건·24시간 5건)이 같은 코드라 서버 message를 함께 넘긴다.
        is ApiError.Http -> when (statusCode) {
            400 -> FeedbackSubmissionException(FeedbackSubmissionFailure.INVALID_CONTENT, serverMessage = message)
            401 -> FeedbackSubmissionException(FeedbackSubmissionFailure.UNAUTHORIZED)
            else -> FeedbackSubmissionException(FeedbackSubmissionFailure.UNKNOWN)
        }
    }

    private fun <T> ApiResult<T>.getOrThrow(): T = when (this) {
        is ApiResult.Success -> value
        is ApiResult.Failure -> throw FeedbackInquiryException(error.toInquiryFailure())
    }

    private fun ApiError.toInquiryFailure(): FeedbackInquiryFailure = when (this) {
        ApiError.Network -> FeedbackInquiryFailure.NETWORK

        ApiError.InvalidResponse -> FeedbackInquiryFailure.UNKNOWN

        is ApiError.Http -> when (statusCode) {
            401 -> FeedbackInquiryFailure.UNAUTHORIZED
            404 -> FeedbackInquiryFailure.NOT_FOUND
            else -> FeedbackInquiryFailure.UNKNOWN
        }
    }

    private companion object {
        const val MAX_CONTENT_LENGTH = 1_000
        const val PAGE_SIZE = 20
    }
}

private fun FeedbackListResponse.toDomain(): FeedbackInquiryPage = FeedbackInquiryPage(
    currentPage = currentPage,
    hasNext = hasNext,
    inquiries = feedbacks.map(FeedbackSummaryResponse::toDomain),
)

private fun FeedbackSummaryResponse.toDomain(): FeedbackInquiry = FeedbackInquiry(
    id = feedbackId,
    content = content,
    createdAt = createdAt,
)

// 답변 내용과 시각이 모두 있을 때만 답변으로 본다. 미답변·답변 삭제는 둘 다 null이다.
private fun FeedbackDetailResponse.toDomain(): FeedbackInquiryDetail = FeedbackInquiryDetail(
    id = feedbackId,
    content = content,
    createdAt = createdAt,
    answer = if (answerContent != null && answeredAt != null) {
        FeedbackAnswer(content = answerContent, answeredAt = answeredAt)
    } else {
        null
    },
)
