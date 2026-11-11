package com.stonefive.chalkak.navigation

import com.stonefive.chalkak.domain.model.NotificationFailure
import java.net.URI

/**
 * 알림(알림함 항목·푸시)을 눌렀을 때 열 곳. 실제 라우트·백스택 처리는 `ChalkakNavHost`가 이 값을 보고 한다.
 * NavController 없이 단위 테스트할 수 있게 이동 결정만 따로 뺐다.
 */
internal sealed interface NotificationTarget {
    /** 오늘의 주제 알림. 홈으로 간다. */
    data object TodayHome : NotificationTarget

    /** 게시물 승인·좋아요 달성·AI 리뷰 알림. [openAiReviewResult]면 AI 리뷰 결과를 열어 둔다. */
    data class Post(
        val postId: String,
        val openAiReviewResult: Boolean,
    ) : NotificationTarget

    /** 게시물 반려·공지. 알림 상세를 연다. */
    data class NotificationDetail(val notificationId: String) : NotificationTarget

    /** 고객센터 답변 알림. 문의 상세를 연다. */
    data class FeedbackInquiry(val feedbackId: String) : NotificationTarget

    /** 고객센터(내 문의 목록). 답변 푸시로 문의 상세를 열 때 그 아래에 둔다. */
    data object FeedbackList : NotificationTarget

    /** 열 곳을 알 수 없으면 알림함으로 간다. */
    data object NotificationList : NotificationTarget

    /** 기록 탭. 화면 푸시(`chalkak://screen/record`)로만 연다. */
    data object RecordTab : NotificationTarget

    /** 설정 탭. 화면 푸시로 열거나 알림 설정 아래에 둔다. */
    data object SettingsTab : NotificationTarget

    /** 알림 설정. 화면 푸시로 열 때 [SettingsTab] 위에 쌓는다. */
    data object NotificationSettings : NotificationTarget
}

/**
 * 화면 푸시의 URI로 열 곳을 정한다. 앱이 만든 알림(개발자 도구의 테스트 알림)이 쓴다.
 *
 * 예: `chalkak://screen/notification-settings` → [NotificationTarget.NotificationSettings].
 * scheme·authority·경로가 허용 목록과 정확히 같아야 하고, 사용자 정보·포트·쿼리·조각이 붙거나 형식이 다르면 null이다.
 */
internal fun screenPushTargetOf(uri: String): NotificationTarget? {
    val parsed = runCatching { URI(uri) }.getOrNull() ?: return null
    if (parsed.scheme != SCREEN_PUSH_SCHEME || parsed.rawAuthority != SCREEN_PUSH_HOST) return null
    if (parsed.rawQuery != null || parsed.rawFragment != null) return null
    return SCREEN_PUSH_TARGETS[parsed.rawPath]
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

    (type == POST_APPROVED || type == POST_LIKE_MILESTONE) &&
        sourceType == POST_SOURCE_TYPE && sourceId != null && isNotificationPostId(sourceId) ->
        NotificationTarget.Post(postId = sourceId, openAiReviewResult = false)

    (type == AI_REVIEW_COMPLETED || type == AI_REVIEW_FAILED) &&
        sourceType == POST_SOURCE_TYPE && sourceId != null && isNotificationPostId(sourceId) ->
        NotificationTarget.Post(postId = sourceId, openAiReviewResult = true)

    (type == POST_REJECTED || type == NOTICE) && !notificationId.isNullOrBlank() ->
        NotificationTarget.NotificationDetail(notificationId)

    type == FEEDBACK_ANSWERED && sourceType == FEEDBACK_SOURCE_TYPE && !sourceId.isNullOrBlank() ->
        NotificationTarget.FeedbackInquiry(feedbackId = sourceId)

    else -> NotificationTarget.NotificationList
}

/**
 * 푸시를 눌렀을 때 [target]까지 차례로 쌓을 화면들. `ChalkakNavHost`가 앞에서부터 순서대로 연다.
 *
 * - 답변 알림: [NotificationTarget.FeedbackList] → [NotificationTarget.FeedbackInquiry].
 *   상세에서 뒤로 가면 고객센터 목록(첫 페이지부터 새로 불러와 "답변 완료"), 한 번 더 뒤로 가면 푸시 전 화면이다.
 * - 알림 설정: [NotificationTarget.SettingsTab] → [NotificationTarget.NotificationSettings].
 *   앱 안에서 설정 탭을 거쳐 들어갈 때와 같아서, 뒤로 가면 설정 탭, 한 번 더 뒤로 가면 홈이다.
 * - 그 밖: [target] 하나.
 *
 * 알림함 항목을 눌렀을 때는 이 함수를 쓰지 않고 [notificationTargetOf] 하나만 연다(뒤로 가면 알림함).
 */
internal fun pushNotificationRouteOf(target: NotificationTarget): List<NotificationTarget> = when (target) {
    is NotificationTarget.FeedbackInquiry -> listOf(NotificationTarget.FeedbackList, target)
    NotificationTarget.NotificationSettings -> listOf(NotificationTarget.SettingsTab, target)
    else -> listOf(target)
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

// API 경로에 그대로 들어가는 값이라 UUID 형식만 받는다.
internal fun isNotificationPostId(value: String): Boolean = NOTIFICATION_POST_ID_PATTERN.matches(value)

private val NOTIFICATION_POST_ID_PATTERN = Regex("[0-9a-fA-F]{8}(-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}")
private const val SCREEN_PUSH_SCHEME = "chalkak"
private const val SCREEN_PUSH_HOST = "screen"
private val SCREEN_PUSH_TARGETS = mapOf(
    "/notifications" to NotificationTarget.NotificationList,
    "/notification-settings" to NotificationTarget.NotificationSettings,
    "/record" to NotificationTarget.RecordTab,
    "/settings" to NotificationTarget.SettingsTab,
)
internal const val TOPIC = "TOPIC"
private const val POST_APPROVED = "POST_APPROVED"
private const val POST_LIKE_MILESTONE = "POST_LIKE_MILESTONE"
private const val POST_REJECTED = "POST_REJECTED"
private const val NOTICE = "NOTICE"
private const val POST_SOURCE_TYPE = "POST"
private const val AI_REVIEW_COMPLETED = "AI_REVIEW_COMPLETED"
private const val AI_REVIEW_FAILED = "AI_REVIEW_FAILED"
private const val FEEDBACK_ANSWERED = "FEEDBACK_ANSWERED"
private const val FEEDBACK_SOURCE_TYPE = "FEEDBACK"
private const val READ_SAVE_FAILED_MESSAGE = "읽음 상태를 저장하지 못했어요. 네트워크 연결을 확인해 주세요."
private const val NOTIFICATION_EXPIRED_MESSAGE = "알림이 만료되었거나 현재 계정에서 확인할 수 없어요."
