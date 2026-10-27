import Foundation
import Observation

struct NotificationItem: Identifiable, Equatable, Sendable {
    let id: String
    let type: String
    let sourceType: String?
    let sourceID: String?
    let title: String
    let body: String
    let thumbnailURL: URL?
    var readAt: Date?
    let createdAt: Date

    var destination: PushNotificationTap? {
        switch type {
        case "POST_APPROVED":
            guard sourceType == "POST", let sourceID, UUID(uuidString: sourceID) != nil else { return nil }
            return .postApproved(postID: sourceID, notificationID: id)
        case "AI_REVIEW_COMPLETED", "AI_REVIEW_FAILED":
            guard sourceType == "POST", let sourceID, UUID(uuidString: sourceID) != nil else { return nil }
            return .aiReview(postID: sourceID, notificationID: id)
        case "POST_REJECTED":
            return .postRejected(notificationID: id)
        case "NOTICE":
            return .notice(notificationID: id)
        default:
            return nil
        }
    }
}

struct NotificationPage: Sendable {
    let currentPage: Int
    let pageSize: Int
    let hasNext: Bool
    let items: [NotificationItem]
}

enum NotificationInboxStatus { case loading, content, failed }

@MainActor
@Observable
final class NotificationInboxViewModel {
    typealias PageLoader = @MainActor (Int) async throws -> NotificationPage
    typealias UnreadLoader = @MainActor () async throws -> Bool
    typealias ReadAction = @MainActor (String) async throws -> Void
    typealias ReadAllAction = @MainActor () async throws -> Void

    private(set) var items: [NotificationItem] = []
    private(set) var status: NotificationInboxStatus = .loading
    private(set) var hasUnread = false
    private(set) var hasNext = false
    private(set) var isLoadingMore = false
    private(set) var isMarkingAllRead = false
    private(set) var message: String?
    private var isRefreshing = false
    /// 미읽음 조회가 실패해도 불러온 목록에 읽지 않은 알림이 있으면 모두 읽음을 허용한다.
    var canMarkAllRead: Bool {
        (hasUnread || items.contains { $0.readAt == nil }) && !isMarkingAllRead
    }
    private var currentPage = 0
    private var generation = 0
    private var unreadGeneration = 0
    private var readingIDs: Set<String> = []
    private let loadPage: PageLoader
    private let loadUnread: UnreadLoader
    private let markRead: ReadAction
    private let markAllReadAction: ReadAllAction

    init(
        loadPage: @escaping PageLoader,
        loadUnread: @escaping UnreadLoader,
        markRead: @escaping ReadAction,
        markAllRead: @escaping ReadAllAction
    ) {
        self.loadPage = loadPage
        self.loadUnread = loadUnread
        self.markRead = markRead
        self.markAllReadAction = markAllRead
    }

    func refreshUnread() async {
        unreadGeneration += 1
        let requestGeneration = unreadGeneration
        do {
            let value = try await loadUnread()
            guard unreadGeneration == requestGeneration, !Task.isCancelled else { return }
            hasUnread = value
        } catch { /* 조회 실패로 기존 미읽음 표시를 지우지 않는다. */ }
    }

    func refresh() async {
        generation += 1
        let requestGeneration = generation
        isRefreshing = true
        defer { if generation == requestGeneration { isRefreshing = false } }
        isLoadingMore = false
        message = nil
        if items.isEmpty { status = .loading }
        do {
            let page = try await loadPage(1)
            guard generation == requestGeneration, !Task.isCancelled else { return }
            apply(page, replacing: true)
            status = .content
        } catch is CancellationError {
            return
        } catch {
            guard generation == requestGeneration, !Task.isCancelled else { return }
            if items.isEmpty { status = .failed }
            else { message = "알림을 새로고침하지 못했어요. 다시 시도해 주세요." }
        }
        await refreshUnread()
    }

    func loadNextPage() async {
        guard status == .content, hasNext, !isLoadingMore, !isRefreshing else { return }
        let requestGeneration = generation
        isLoadingMore = true
        message = nil
        do {
            let page = try await loadPage(currentPage + 1)
            guard generation == requestGeneration, !Task.isCancelled else { return }
            apply(page, replacing: false)
        } catch is CancellationError {
            // 취소한 추가 조회는 오류 메시지를 표시하지 않는다.
        } catch {
            guard generation == requestGeneration, !Task.isCancelled else { return }
            message = "다음 알림을 불러오지 못했어요. 다시 시도해 주세요."
        }
        if generation == requestGeneration { isLoadingMore = false }
    }

    /// 조회만으로 읽음 처리하지 않는다. 서버에서 찾을 수 없는 알림은 이동하지 않는다.
    func readForNavigation(_ notificationID: String) async -> Bool {
        message = nil
        guard readingIDs.insert(notificationID).inserted else { return false }
        defer { readingIDs.remove(notificationID) }
        do {
            try await markRead(notificationID)
            guard !Task.isCancelled else { return false }
            // 서버의 최초 읽은 시각은 다음 조회에서 받으며 이미 읽은 항목의 시각은 유지한다.
            if let index = items.firstIndex(where: { $0.id == notificationID }), items[index].readAt == nil {
                items[index].readAt = Date()
            }
            await refreshUnread()
            return true
        } catch NotificationAPIError.notFound {
            await refresh()
            message = "더 이상 확인할 수 없는 알림이에요."
            return false
        } catch NotificationAPIError.unauthorized {
            return false
        } catch is CancellationError {
            return false
        } catch {
            message = "읽음 처리하지 못했어요. 다시 열어 주세요."
            return true
        }
    }

    /// 아직 불러오지 않은 페이지까지 서버에서 읽음 처리하므로 목록은 다시 조회하지 않고 로컬만 갱신한다.
    func markAllRead() async {
        guard canMarkAllRead else { return }
        isMarkingAllRead = true
        defer { isMarkingAllRead = false }
        message = nil
        do {
            try await markAllReadAction()
            guard !Task.isCancelled else { return }
            let now = Date()
            for index in items.indices where items[index].readAt == nil {
                items[index].readAt = now
            }
            // 요청 전에 시작된 미읽음 조회가 결과를 덮어쓰지 않게 한다.
            unreadGeneration += 1
            hasUnread = false
        } catch NotificationAPIError.unauthorized {
            return
        } catch is CancellationError {
            return
        } catch {
            message = "모두 읽음 처리하지 못했어요. 다시 시도해 주세요."
        }
    }

    private func apply(_ page: NotificationPage, replacing: Bool) {
        var seen = Set(replacing ? [] : items.map(\.id))
        let unique = page.items.filter { seen.insert($0.id).inserted }
        items = replacing ? unique : items + unique
        currentPage = page.currentPage
        hasNext = page.hasNext
    }
}
