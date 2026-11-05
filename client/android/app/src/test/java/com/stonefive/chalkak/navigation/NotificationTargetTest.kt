package com.stonefive.chalkak.navigation

import com.stonefive.chalkak.domain.model.NotificationFailure
import org.junit.Assert.assertEquals
import org.junit.Test

class NotificationTargetTest {
    @Test
    fun `답변 알림은 문의 상세로 이동 대상을 만든다`() {
        // Given  type=FEEDBACK_ANSWERED, sourceType=FEEDBACK, sourceId=F, notificationId=N (알림함 항목·푸시 공통)
        // When   이동 대상을 정한다
        val target = notificationTargetOf(
            type = "FEEDBACK_ANSWERED",
            sourceType = "FEEDBACK",
            sourceId = "F",
            notificationId = "N",
        )

        // Then   문의 상세(F)
        assertEquals(NotificationTarget.FeedbackInquiry(feedbackId = "F"), target)
    }

    @Test
    fun `답변 알림의 sourceType이 FEEDBACK이 아니거나 sourceId가 비면 알림함으로 간다`() {
        // Given  sourceType=POST, 또는 sourceId=""
        // When   이동 대상을 정한다
        val wrongSourceType = notificationTargetOf(
            type = "FEEDBACK_ANSWERED",
            sourceType = "POST",
            sourceId = "F",
            notificationId = "N",
        )
        val blankSourceId = notificationTargetOf(
            type = "FEEDBACK_ANSWERED",
            sourceType = "FEEDBACK",
            sourceId = "",
            notificationId = "N",
        )

        // Then   알림함
        assertEquals(NotificationTarget.NotificationList, wrongSourceType)
        assertEquals(NotificationTarget.NotificationList, blankSourceId)
    }

    @Test
    fun `답변 푸시는 읽음 처리가 404여도 문의 상세로 이동한다`() {
        // Given  답변 푸시(F, N), 읽음 처리 요청이 404(문의 삭제로 알림 행이 지워짐)
        val notFound = NotificationFailure.Http(statusCode = 404, errorCode = null)

        // When   푸시를 처리한다
        val feedbackPush = pushReadFailureHandlingOf(
            type = "FEEDBACK_ANSWERED",
            sourceType = "FEEDBACK",
            sourceId = "F",
            notificationId = "N",
            failure = notFound,
        )
        val postPush = pushReadFailureHandlingOf(
            type = "POST_APPROVED",
            sourceType = "POST",
            sourceId = "P",
            notificationId = "N",
            failure = notFound,
        )

        // Then   문의 상세(F)로 이동(상세의 404 안내는 TC 8). 다른 종류 알림의 404는 기존대로 만료 안내 + 알림함
        assertEquals(
            PushReadFailureHandling(message = null, target = NotificationTarget.FeedbackInquiry(feedbackId = "F")),
            feedbackPush,
        )
        assertEquals(
            PushReadFailureHandling(
                message = "알림이 만료되었거나 현재 계정에서 확인할 수 없어요.",
                target = NotificationTarget.NotificationList,
            ),
            postPush,
        )
    }

    @Test
    fun `답변 푸시는 고객센터 목록을 거쳐 문의 상세를 연다`() {
        // Given  답변 푸시(F, N), 읽음 처리 성공
        val feedbackTarget = notificationTargetOf(
            type = "FEEDBACK_ANSWERED",
            sourceType = "FEEDBACK",
            sourceId = "F",
            notificationId = "N",
        )
        val postTarget = notificationTargetOf(
            type = "POST_APPROVED",
            sourceType = "POST",
            sourceId = POST_ID,
            notificationId = "N",
        )

        // When   푸시 이동 경로를 정한다
        val feedbackPushRoute = pushNotificationRouteOf(feedbackTarget)
        val postPushRoute = pushNotificationRouteOf(postTarget)

        // Then   고객센터 목록 → 문의 상세(F) 순서. 알림함 항목에서 누르면 문의 상세(F) 하나, 다른 종류 푸시는 기존 그대로
        assertEquals(
            listOf(NotificationTarget.FeedbackList, NotificationTarget.FeedbackInquiry(feedbackId = "F")),
            feedbackPushRoute,
        )
        assertEquals(NotificationTarget.FeedbackInquiry(feedbackId = "F"), feedbackTarget)
        assertEquals(
            listOf(NotificationTarget.Post(postId = POST_ID, openAiReviewResult = false)),
            postPushRoute,
        )
    }
}

// 게시물 알림은 UUID 형식 sourceId만 이동한다(#972).
private const val POST_ID = "0198f6c1-62ba-7d30-8b12-0f733b6570d4"
