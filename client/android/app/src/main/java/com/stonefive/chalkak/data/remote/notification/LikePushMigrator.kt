package com.stonefive.chalkak.data.remote.notification

import com.stonefive.chalkak.domain.model.NotificationPushSettingsUpdate
import com.stonefive.chalkak.domain.model.NotificationResult
import com.stonefive.chalkak.domain.repository.LikePushMigrationRepository
import com.stonefive.chalkak.domain.repository.NotificationRepository

/** 좋아요 알림 도입 전 수신 의사를 이어받도록 회원별로 한 번 맞춘다. */
class LikePushMigrator(
    private val notificationRepository: NotificationRepository,
    private val migrationRepository: LikePushMigrationRepository,
) {
    suspend fun run(userId: String) {
        if (migrationRepository.isCompleted(userId)) return

        // 실패하면 완료로 기록하지 않아 다음 실행에서 다시 시도한다.
        val settings = when (val result = notificationRepository.getPushSettings()) {
            is NotificationResult.Success -> result.value
            is NotificationResult.Failure -> return
        }
        val enabled = settings.topicPushEnabled ||
            settings.moderationPushEnabled ||
            settings.aiReviewPushEnabled
        if (settings.likePushEnabled != enabled) {
            val update = NotificationPushSettingsUpdate(likePushEnabled = enabled)
            if (notificationRepository.updatePushSettings(update) is NotificationResult.Failure) return
        }
        migrationRepository.complete(userId)
    }
}
