import Foundation

protocol NotificationSetupStoring {
    var hasCompletedSetup: Bool { get }
    var savedTime: DateComponents? { get }
    func completeSetup(time: DateComponents?)
}

struct NotificationSetupStore: NotificationSetupStoring {
    private enum Key {
        static let hasCompletedSetup = "notificationSetup.hasCompleted"
        static let hour = "notificationSetup.hour"
        static let minute = "notificationSetup.minute"
    }

    private let defaults: UserDefaults

    init(defaults: UserDefaults = .standard) {
        self.defaults = defaults
    }

    var hasCompletedSetup: Bool {
        defaults.bool(forKey: Key.hasCompletedSetup)
    }

    var savedTime: DateComponents? {
        guard defaults.object(forKey: Key.hour) != nil,
              defaults.object(forKey: Key.minute) != nil else {
            return nil
        }
        return DateComponents(
            hour: defaults.integer(forKey: Key.hour),
            minute: defaults.integer(forKey: Key.minute)
        )
    }

    func completeSetup(time: DateComponents?) {
        if let hour = time?.hour, let minute = time?.minute {
            defaults.set(hour, forKey: Key.hour)
            defaults.set(minute, forKey: Key.minute)
        } else {
            defaults.removeObject(forKey: Key.hour)
            defaults.removeObject(forKey: Key.minute)
        }
        defaults.set(true, forKey: Key.hasCompletedSetup)
    }
}
