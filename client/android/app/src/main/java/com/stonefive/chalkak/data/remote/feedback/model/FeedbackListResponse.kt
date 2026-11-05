package com.stonefive.chalkak.data.remote.feedback.model

import kotlinx.serialization.Serializable

/** `GET feedbacks?page=&pageSize=` 응답. 항목에는 답변 내용 없이 답변 여부(`hasAnswer`)만 있다. */
@Serializable
data class FeedbackListResponse(
    val currentPage: Int,
    val pageSize: Int,
    val hasNext: Boolean,
    val feedbacks: List<FeedbackSummaryResponse>,
)

@Serializable
data class FeedbackSummaryResponse(
    val feedbackId: String,
    val content: String,
    val createdAt: String,
    // 백엔드 #970에서 추가. 이 필드가 없는 서버(배포 전)도 읽을 수 있게 null을 기본값으로 둔다.
    val hasAnswer: Boolean? = null,
)
