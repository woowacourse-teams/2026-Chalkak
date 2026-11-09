import Foundation

/// 좋아요 알림 도입 전 수신 의사를 이어받도록 회원별로 한 번 맞춘다.
/// 기존 알림을 모두 끈 회원에게 좋아요 푸시만 켜진 채로 남지 않게 한다.
enum LikePushMigration {
    static func run(
        userID: String,
        defaults: UserDefaults = .standard,
        load: () async throws -> NotificationSettings,
        save: (Bool) async throws -> Void
    ) async {
        let key = "notifications.likePushMigrated.\(userID)"
        guard !defaults.bool(forKey: key) else { return }
        do {
            let settings = try await load()
            let enabled = settings.topicPushEnabled
                || settings.moderationPushEnabled
                || settings.aiReviewPushEnabled
            try Task.checkCancellation()
            if settings.likePushEnabled != enabled { try await save(enabled) }
            defaults.set(true, forKey: key)
        } catch {
            // 완료로 기록하지 않아 다음 진입에서 다시 시도한다.
        }
    }
}
