import Foundation

enum NotificationTimeOption: String, CaseIterable, Identifiable, Equatable {
    case morning
    case noon
    case evening
    case custom

    var id: String { rawValue }

    var title: String {
        switch self {
        case .morning: "아침 8:00"
        case .noon: "점심 12:00"
        case .evening: "저녁 18:00"
        case .custom: "직접 설정하기"
        }
    }

    var context: String? {
        switch self {
        case .morning: "출근길에"
        case .noon: "잠깐 쉴 때"
        case .evening: "해 질 무렵"
        case .custom: nil
        }
    }

    var presetTime: DateComponents? {
        switch self {
        case .morning: DateComponents(hour: 8, minute: 0)
        case .noon: DateComponents(hour: 12, minute: 0)
        case .evening: DateComponents(hour: 18, minute: 0)
        case .custom: nil
        }
    }
}

struct NotificationSetupViewState: Equatable {
    var selectedOption: NotificationTimeOption = .evening
    var customTime = DateComponents(hour: 18, minute: 0)
    var isSaving = false

    var selectedTime: DateComponents {
        selectedOption.presetTime ?? customTime
    }
}

enum NotificationSetupEvent: Equatable {
    case completed
    case showMessage(String)
}
