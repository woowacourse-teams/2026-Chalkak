import Foundation

enum NotificationRoute: Hashable {
    case inbox
    /// 홈 상단 설정 버튼으로 여는 설정 화면(#1021). 알림 설정·고객센터는 이 위에 쌓인다.
    case appSettings
    /// 설정 → 알림 설정.
    case settings
    case approved(String)
    case aiReview(String)
    case likeMilestone(String)
    case rejected(NotificationDetailTarget)
    case notice(NotificationDetailTarget)
    /// 설정 → 고객센터의 내 문의 목록.
    case customerSupport
    /// 문의 UUID로 여는 문의 상세. 고객센터 목록과 답변 알림(알림함·푸시)에서 연다.
    case feedbackDetail(String)
}

/// 답변 푸시가 붙인 목록·상세 구간만 관리한다. 기존 화면과 overlay는 그대로 보존한다.
struct FeedbackPushNavigation {
    private(set) var returnDepth: Int?
    var hidesPreviousOverlays: Bool { returnDepth != nil }

    mutating func open(feedbackID: String, path: [NotificationRoute]) -> [NotificationRoute] {
        if returnDepth == nil { returnDepth = path.count }
        let previous = Array(path.prefix(returnDepth ?? path.count))
        return previous + [.customerSupport, .feedbackDetail(feedbackID)]
    }

    mutating func pathChanged(_ path: [NotificationRoute]) {
        if let returnDepth, path.count <= returnDepth { self.returnDepth = nil }
    }

    mutating func reset() { returnDepth = nil }

    func missingDetail(path: [NotificationRoute], feedbackID: String) -> (path: [NotificationRoute], message: String)? {
        guard let returnDepth, path.count == returnDepth + 2,
              path.last == .feedbackDetail(feedbackID),
              path[returnDepth] == .customerSupport else { return nil }
        return (Array(path.dropLast()), FeedbackMessages.notFound)
    }

    static func permitsNavigation(cancelled: Bool, sameAccount: Bool, authenticated: Bool, mainRoute: Bool, readAllowed: Bool) -> Bool {
        !cancelled && sameAccount && authenticated && mainRoute && readAllowed
    }

    /// 문의 외 푸시는 기존처럼 이전 경로를 비우고, 알림함에서 열 때만 알림함을 남긴다.
    static func destinationPath(for tap: PushNotificationTap, fromInbox: Bool) -> [NotificationRoute] {
        let prefix: [NotificationRoute] = fromInbox ? [.inbox] : []
        switch tap {
        case .topic: return []
        case let .postApproved(postID, _): return prefix + [.approved(postID)]
        case let .aiReview(postID, _): return prefix + [.aiReview(postID)]
        case let .likeMilestone(postID, _): return prefix + [.likeMilestone(postID)]
        case let .postRejected(id): return prefix + [.rejected(NotificationDetailTarget(id: id))]
        case let .notice(id): return prefix + [.notice(NotificationDetailTarget(id: id))]
        case let .feedbackAnswered(feedbackID, _): return prefix + [.feedbackDetail(feedbackID)]
        }
    }

    static func inboxPath(feedbackID: String) -> [NotificationRoute] {
        [.inbox, .feedbackDetail(feedbackID)]
    }
}
