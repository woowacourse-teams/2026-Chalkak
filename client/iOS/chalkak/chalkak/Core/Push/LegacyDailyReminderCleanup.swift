import Foundation
import UserNotifications

/// 서버 기반 알림으로 전환할 때 이전 버전이 예약한 시간 알림만 정리한다.
/// 서버에서 받은 푸시나 다른 기능의 알림은 지우지 않는다.
enum LegacyDailyReminderCleanup {
    static func run(
        defaults: UserDefaults = .standard,
        cancelPending: ([String]) -> Void = {
            UNUserNotificationCenter.current().removePendingNotificationRequests(withIdentifiers: $0)
        },
        cancelDelivered: ([String]) -> Void = {
            UNUserNotificationCenter.current().removeDeliveredNotifications(withIdentifiers: $0)
        }
    ) {
        let identifiers = ["daily-topic-reminder"]
        cancelPending(identifiers)
        cancelDelivered(identifiers)
        for key in ["notificationSetup.hasCompleted", "notificationSetup.hour", "notificationSetup.minute"] {
            defaults.removeObject(forKey: key)
        }
    }
}
