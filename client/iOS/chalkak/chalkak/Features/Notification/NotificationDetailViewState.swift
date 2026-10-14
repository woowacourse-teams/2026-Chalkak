import Foundation

struct NotificationDetailViewState: Equatable {
    var status: NotificationDetailStatus = .loading
    var detail: NotificationDetail?
}

enum NotificationDetailStatus: Equatable {
    case loading
    case content
    case notFound
    case failed
}

struct NotificationDetail: Equatable, Sendable {
    let id: String
    let title: String
    let body: String
    let originalImageURL: URL?
    let rejectionReason: String?
}

/// 푸시로 진입하는 알림 상세의 내비게이션 대상.
struct NotificationDetailTarget: Hashable, Identifiable {
    let id: String
}
