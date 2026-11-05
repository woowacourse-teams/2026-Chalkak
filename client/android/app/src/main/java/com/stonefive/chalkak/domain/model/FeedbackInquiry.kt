package com.stonefive.chalkak.domain.model

/**
 * 고객센터 "내 문의" 목록의 한 페이지.
 * [currentPage]는 1부터 시작하고, [hasNext]가 true면 `currentPage + 1`을 이어서 요청한다.
 */
data class FeedbackInquiryPage(
    val currentPage: Int,
    val hasNext: Boolean,
    val inquiries: List<FeedbackInquiry>,
)

/**
 * 내 문의 목록의 항목. 목록 응답에는 답변 정보가 없다.
 * [createdAt]은 서버가 준 UTC ISO-8601 문자열 그대로다(예: "2026-11-03T06:00:00Z").
 */
data class FeedbackInquiry(
    val id: String,
    val content: String,
    val createdAt: String,
)

/**
 * 문의 상세. 답변이 없거나 관리자가 답변을 지웠으면 [answer]가 null이다.
 */
data class FeedbackInquiryDetail(
    val id: String,
    val content: String,
    val createdAt: String,
    val answer: FeedbackAnswer?,
)

/** 문의에 달린 현재 답변. [answeredAt]은 UTC ISO-8601 문자열이다. */
data class FeedbackAnswer(
    val content: String,
    val answeredAt: String,
)
