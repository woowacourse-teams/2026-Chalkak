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
 * 내 문의 목록의 항목. 답변 내용은 없고 답변이 있는지만 안다.
 * [createdAt]은 서버가 준 UTC ISO-8601 문자열 그대로다(예: "2026-11-03T06:00:00Z").
 *
 * @property hasAnswer 지금 답변이 있으면 true, 없으면(미답변·답변 삭제) false.
 *   서버가 이 필드를 주지 않으면(아직 배포 전) null이고, 화면은 답변 상태를 숨긴다.
 */
data class FeedbackInquiry(
    val id: String,
    val content: String,
    val createdAt: String,
    val hasAnswer: Boolean? = null,
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
