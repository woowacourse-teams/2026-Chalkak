import Foundation
import UserNotifications

extension Notification.Name {
    static let dailyReminderNotificationTapped = Notification.Name(
        "stonefive.chalkak.daily-reminder-notification-tapped"
    )
}

@MainActor
protocol DailyNotificationScheduling {
    func requestAuthorization() async throws -> Bool
    func scheduleDaily(at time: DateComponents) async throws
}

struct UserNotificationDailyScheduler: DailyNotificationScheduling {
    static let requestIdentifier = "daily-topic-reminder"

    private let center: UNUserNotificationCenter

    init(center: UNUserNotificationCenter = .current()) {
        self.center = center
    }

    func requestAuthorization() async throws -> Bool {
        try await center.requestAuthorization(options: [.alert, .sound])
    }

    func scheduleDaily(at time: DateComponents) async throws {
        let content = UNMutableNotificationContent()
        content.title = "오늘의 주제가 도착했어요"
        content.body = "오늘의 주제를 확인하고 사진을 남겨보세요."
        content.sound = .default

        let trigger = UNCalendarNotificationTrigger(
            dateMatching: DateComponents(hour: time.hour, minute: time.minute),
            repeats: true
        )
        let request = UNNotificationRequest(
            identifier: Self.requestIdentifier,
            content: content,
            trigger: trigger
        )

        try await center.add(request)
    }
}
