import Foundation
import Observation

/// 사용자가 누른 푸시가 가리키는 화면.
/// 서버가 FCM `data`에 넣은 값은 iOS에서 APNs `userInfo` 최상위에 문자열로 전달된다.
nonisolated enum PushNotificationTap: Equatable, Sendable {
    case topic
    case postApproved(postID: String, notificationID: String)
    case postRejected(notificationID: String)
    case notice(notificationID: String)
    case aiReview(postID: String, notificationID: String)

    /// 필드가 빠졌거나 알 수 없는 종류면 nil을 반환해 화면 이동 없이 앱만 연다.
    init?(userInfo: [AnyHashable: Any]) {
        guard let type = userInfo[Key.type] as? String else { return nil }
        if type == NotificationType.topic {
            self = .topic
            return
        }
        guard let notificationID = Self.identifier(userInfo[Key.notificationID]) else { return nil }

        switch type {
        case NotificationType.postApproved:
            guard let postID = Self.identifier(userInfo[Key.sourceID]) else { return nil }
            self = .postApproved(postID: postID, notificationID: notificationID)
        case NotificationType.aiReviewCompleted, NotificationType.aiReviewFailed:
            guard userInfo[Key.sourceType] as? String == "POST",
                  let postID = Self.identifier(userInfo[Key.sourceID]) else { return nil }
            self = .aiReview(postID: postID, notificationID: notificationID)
        case NotificationType.postRejected:
            self = .postRejected(notificationID: notificationID)
        case NotificationType.notice:
            self = .notice(notificationID: notificationID)
        default:
            return nil
        }
    }

    var notificationID: String? {
        switch self {
        case .topic:
            nil
        case let .postApproved(_, notificationID), let .postRejected(notificationID),
             let .notice(notificationID), let .aiReview(_, notificationID):
            notificationID
        }
    }

    // API 경로에 그대로 들어가는 값이라 UUID 형식만 받는다.
    private static func identifier(_ value: Any?) -> String? {
        guard let value = value as? String, UUID(uuidString: value) != nil else {
            return nil
        }
        return value
    }

    private enum Key {
        static let type = "type"
        static let notificationID = "notificationId"
        static let sourceID = "sourceId"
        static let sourceType = "sourceType"
    }

    private enum NotificationType {
        static let topic = "TOPIC"
        static let postApproved = "POST_APPROVED"
        static let postRejected = "POST_REJECTED"
        static let notice = "NOTICE"
        static let aiReviewCompleted = "AI_REVIEW_COMPLETED"
        static let aiReviewFailed = "AI_REVIEW_FAILED"
    }
}

/// 누른 푸시를 화면이 준비될 때까지 보관한다.
/// 앱이 푸시로 새로 실행되면 화면이 뜨기 전에 클릭이 먼저 전달될 수 있다.
@Observable
final class PushTapRouter {
    static let shared = PushTapRouter()

    private(set) var pendingTap: PushNotificationTap?

    func receive(_ tap: PushNotificationTap) {
        pendingTap = tap
    }

    func consume() -> PushNotificationTap? {
        defer { pendingTap = nil }
        return pendingTap
    }
}
