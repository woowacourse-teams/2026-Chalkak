package com.stonefive.chalkak.navigation

import com.stonefive.chalkak.domain.model.NotificationFailure

/**
 * 알림(알림함 항목·푸시)을 눌렀을 때 열 곳. 실제 라우트·백스택 처리는 `ChalkakNavHost`가 이 값을 보고 한다.
 * NavController 없이 단위 테스트할 수 있게 이동 결정만 따로 뺐다.
 */
internal sealed interface NotificationTarget {
    /** 오늘의 주제 알림. 홈으로 간다. */
    data object TodayHome : NotificationTarget

    /** 게시물 승인·AI 리뷰 알림. [openAiReviewResult]면 AI 리뷰 결과를 열어 둔다. */
    data class Post(
        val postId: String,
        val openAiReviewResult: Boolean,
    ) : NotificationTarget

    /** 게시물 반려·공지. 알림 상세를 연다. */
    data class NotificationDetail(val notificationId: String) : NotificationTarget

    /** 고객센터 답변 알림. 문의 상세를 연다. */
    data class FeedbackInquiry(val feedbackId: String) : NotificationTarget

    /** 열 곳을 알 수 없으면 알림함으로 간다. */
    data object NotificationList : NotificationTarget
}

/**
 * 알림의 종류·출처로 열 곳을 정한다.
 *
 * 예: `type=FEEDBACK_ANSWERED, sourceType=FEEDBACK, sourceId=F` → [NotificationTarget.FeedbackInquiry] (F).
 * 출처가 맞지 않거나 sourceId가 비면 [NotificationTarget.NotificationList].
 */
internal fun notificationTargetOf(
    type: String?,
    sourceType: String?,
    sourceId: String?,
    notificationId: String?,
): NotificationTarget = when {
    type == TOPIC -> NotificationTarget.TodayHome

    type == POST_APPROVED && sourceType == POST_SOURCE_TYPE && !sourceId.isNullOrBlank() ->
        NotificationTarget.Post(postId = sourceId, openAiReviewResult = false)

    (type == AI_REVIEW_COMPLETED || type == AI_REVIEW_FAILED) &&
        sourceType == POST_SOURCE_TYPE && !sourceId.isNullOrBlank() ->
        NotificationTarget.Post(postId = sourceId, openAiReviewResult = true)

    (type == POST_REJECTED || type == NOTICE) && !notificationId.isNullOrBlank() ->
        NotificationTarget.NotificationDetail(notificationId)

    type == FEEDBACK_ANSWERED && sourceType == FEEDBACK_SOURCE_TYPE && !sourceId.isNullOrBlank() ->
        NotificationTarget.FeedbackInquiry(feedbackId = sourceId)

    else -> NotificationTarget.NotificationList
}

/**
 * 푸시를 눌러 읽음 처리가 실패했을 때 띄울 토스트([message], 없으면 null)와 열 곳([target], 없으면 그대로 둠).
 */
internal data class PushReadFailureHandling(
    val message: String?,
    val target: NotificationTarget?,
)

/**
 * 푸시 읽음 처리 실패를 어떻게 다룰지 정한다.
 *
 * - 네트워크 실패: 읽음 저장 실패만 안내하고 알림 대상은 연다.
 * - 답변 알림의 404: 문의가 삭제되면 서버가 알림 행을 지우므로, 만료 안내 없이 문의 상세를 연다.
 *   문의도 없으면 상세가 "문의를 찾을 수 없어요"를 안내한다.
 * - 그 밖: 만료 안내. 404면 알림함으로 간다.
 */
internal fun pushReadFailureHandlingOf(
    type: String?,
    sourceType: String?,
    sourceId: String?,
    notificationId: String?,
    failure: NotificationFailure,
): PushReadFailureHandling {
    val target = notificationTargetOf(type, sourceType, sourceId, notificationId)
    val isNotFound = (failure as? NotificationFailure.Http)?.statusCode == 404
    return when {
        failure == NotificationFailure.Network -> PushReadFailureHandling(READ_SAVE_FAILED_MESSAGE, target)
        isNotFound && target is NotificationTarget.FeedbackInquiry -> PushReadFailureHandling(null, target)
        isNotFound -> PushReadFailureHandling(NOTIFICATION_EXPIRED_MESSAGE, NotificationTarget.NotificationList)
        else -> PushReadFailureHandling(NOTIFICATION_EXPIRED_MESSAGE, null)
    }
}

internal const val TOPIC = "TOPIC"
private const val POST_APPROVED = "POST_APPROVED"
private const val POST_REJECTED = "POST_REJECTED"
private const val NOTICE = "NOTICE"
private const val POST_SOURCE_TYPE = "POST"
private const val AI_REVIEW_COMPLETED = "AI_REVIEW_COMPLETED"
private const val AI_REVIEW_FAILED = "AI_REVIEW_FAILED"
private const val FEEDBACK_ANSWERED = "FEEDBACK_ANSWERED"
private const val FEEDBACK_SOURCE_TYPE = "FEEDBACK"
private const val READ_SAVE_FAILED_MESSAGE = "읽음 상태를 저장하지 못했어요. 네트워크 연결을 확인해 주세요."
private const val NOTIFICATION_EXPIRED_MESSAGE = "알림이 만료되었거나 현재 계정에서 확인할 수 없어요."
