import Foundation
import Testing
@testable import chalkak

@MainActor
struct LegacyDailyReminderCleanupTests {
    @Test("시간 알림 예약과 표시 기록만 제거하고 다른 알림과 설정은 보존한다")
    func removesOnlyLegacyReminder() throws {
        let name = "LegacyDailyReminderCleanupTests.\(UUID().uuidString)"
        let defaults = try #require(UserDefaults(suiteName: name))
        defer { defaults.removePersistentDomain(forName: name) }
        defaults.set(true, forKey: "notificationSetup.hasCompleted")
        defaults.set(7, forKey: "notificationSetup.hour")
        defaults.set(25, forKey: "notificationSetup.minute")
        defaults.set("keep", forKey: "unrelated")
        var pending: [String] = []
        var delivered: [String] = []
        LegacyDailyReminderCleanup.run(defaults: defaults,
            cancelPending: { pending = $0 }, cancelDelivered: { delivered = $0 })
        #expect(pending == ["daily-topic-reminder"])
        #expect(delivered == ["daily-topic-reminder"])
        #expect(defaults.object(forKey: "notificationSetup.hasCompleted") == nil)
        #expect(defaults.object(forKey: "notificationSetup.hour") == nil)
        #expect(defaults.object(forKey: "notificationSetup.minute") == nil)
        #expect(defaults.string(forKey: "unrelated") == "keep")
    }

    @Test("이전 예약이 없어도 반복 정리할 수 있다")
    func cleanupIsRepeatable() throws {
        let name = "LegacyDailyReminderCleanupTests.\(UUID().uuidString)"
        let defaults = try #require(UserDefaults(suiteName: name))
        defer { defaults.removePersistentDomain(forName: name) }
        var cancellations = 0
        for _ in 0..<2 {
            LegacyDailyReminderCleanup.run(defaults: defaults,
                cancelPending: { _ in cancellations += 1 }, cancelDelivered: { _ in })
        }
        #expect(cancellations == 2)
    }
}
