package com.stonefive.chalkak.domain.repository

import com.stonefive.chalkak.domain.model.FeedbackInquiryDetail
import com.stonefive.chalkak.domain.model.FeedbackInquiryPage

interface FeedbackRepository {
    /** 문의를 접수한다. 실패하면 [com.stonefive.chalkak.domain.model.FeedbackSubmissionException]을 던진다. */
    suspend fun submitFeedback(content: String)

    /**
     * 내 문의 목록의 [page]번째 페이지(1부터)를 20건 단위로 불러온다.
     * 실패하면 [com.stonefive.chalkak.domain.model.FeedbackInquiryException]을 던진다.
     */
    suspend fun getFeedbacks(page: Int): FeedbackInquiryPage

    /** 문의 상세를 불러온다. 실패하면 [com.stonefive.chalkak.domain.model.FeedbackInquiryException]을 던진다. */
    suspend fun getFeedbackDetail(feedbackId: String): FeedbackInquiryDetail

    /** 문의를 삭제한다. 실패하면 [com.stonefive.chalkak.domain.model.FeedbackInquiryException]을 던진다. */
    suspend fun deleteFeedback(feedbackId: String)
}
