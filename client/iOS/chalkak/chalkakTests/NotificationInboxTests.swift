import Foundation
import Testing
@testable import chalkak

@MainActor
struct NotificationInboxViewModelTests {
    @Test("새로고침과 다음 페이지 조회는 중복 알림을 제거하고 페이지를 이어 붙인다")
    func refreshAndNextPageAppendUniqueNotifications() async {
        let loader = NotificationInboxPageLoader(results: [
            .success(Self.page(1, hasNext: true, items: [Self.item(id: Self.notificationID(1))])),
            .success(Self.page(2, hasNext: false, items: [
                Self.item(id: Self.notificationID(1)),
                Self.item(id: Self.notificationID(2)),
            ])),
        ])
        let viewModel = Self.viewModel(pageLoader: loader)

        await viewModel.refresh()
        await viewModel.loadNextPage()

        #expect(viewModel.status == .content)
        #expect(viewModel.items.map(\.id) == [Self.notificationID(1), Self.notificationID(2)])
        #expect(!viewModel.hasNext)
        #expect(loader.requestedPages == [1, 2])
    }

    @Test("다음 페이지 실패는 기존 목록과 페이지를 유지하고 재시도할 수 있다")
    func failedNextPagePreservesDataAndCanRetry() async {
        let loader = NotificationInboxPageLoader(results: [
            .success(Self.page(1, hasNext: true, items: [Self.item(id: Self.notificationID(1))])),
            .failure(NotificationAPIError.network),
            .success(Self.page(2, hasNext: false, items: [Self.item(id: Self.notificationID(2))])),
        ])
        let viewModel = Self.viewModel(pageLoader: loader)

        await viewModel.refresh()
        await viewModel.loadNextPage()

        #expect(viewModel.items.map(\.id) == [Self.notificationID(1)])
        #expect(viewModel.hasNext)
        #expect(viewModel.message == "다음 알림을 불러오지 못했어요. 다시 시도해 주세요.")

        await viewModel.loadNextPage()

        #expect(viewModel.items.map(\.id) == [Self.notificationID(1), Self.notificationID(2)])
        #expect(!viewModel.hasNext)
        #expect(loader.requestedPages == [1, 2, 2])
    }

    @Test("찾을 수 없는 알림의 읽음 실패는 목록을 새로고침하고 이동을 막는다")
    func notFoundReadRefreshesAndPreventsNavigation() async {
        let loader = NotificationInboxPageLoader(results: [
            .success(Self.page(1, hasNext: false, items: [Self.item(id: Self.notificationID(2))])),
        ])
        let readAction = NotificationReadAction(results: [.failure(NotificationAPIError.notFound)])
        let viewModel = Self.viewModel(
            pageLoader: loader,
            unreadLoader: NotificationUnreadLoader(results: [.success(false)]),
            readAction: readAction
        )

        let shouldNavigate = await viewModel.readForNavigation(Self.notificationID(1))

        #expect(!shouldNavigate)
        #expect(viewModel.items.map(\.id) == [Self.notificationID(2)])
        #expect(viewModel.message == "더 이상 확인할 수 없는 알림이에요.")
        #expect(!viewModel.hasUnread)
        #expect(loader.requestedPages == [1])
        #expect(readAction.requestedIDs == [Self.notificationID(1)])
    }

    @Test("네트워크 읽음 실패는 읽지 않은 상태를 보존하되 이동은 허용한다")
    func networkReadFailurePreservesUnreadAndAllowsNavigation() async {
        let unreadItem = Self.item(id: Self.notificationID(1), readAt: nil)
        let loader = NotificationInboxPageLoader(results: [
            .success(Self.page(1, hasNext: false, items: [unreadItem])),
        ])
        let viewModel = Self.viewModel(
            pageLoader: loader,
            unreadLoader: NotificationUnreadLoader(results: [.success(true)]),
            readAction: NotificationReadAction(results: [.failure(NotificationAPIError.network)])
        )

        await viewModel.refresh()
        let shouldNavigate = await viewModel.readForNavigation(Self.notificationID(1))

        #expect(shouldNavigate)
        #expect(viewModel.items.first?.readAt == nil)
        #expect(viewModel.hasUnread)
        #expect(viewModel.message == "읽음 처리하지 못했어요. 다시 열어 주세요.")
    }

    @Test("이미 읽은 알림의 반복 읽음은 기존 읽은 시각을 유지한다")
    func repeatedReadKeepsExistingReadTimestamp() async {
        let readAt = Date(timeIntervalSince1970: 1_790_000_000)
        let readItem = Self.item(id: Self.notificationID(1), readAt: readAt)
        let loader = NotificationInboxPageLoader(results: [
            .success(Self.page(1, hasNext: false, items: [readItem])),
        ])
        let viewModel = Self.viewModel(
            pageLoader: loader,
            unreadLoader: NotificationUnreadLoader(results: [.success(false), .success(false)]),
            readAction: NotificationReadAction(results: [.success(())])
        )

        await viewModel.refresh()
        let shouldNavigate = await viewModel.readForNavigation(Self.notificationID(1))

        #expect(shouldNavigate)
        #expect(viewModel.items.first?.readAt == readAt)
    }

    @Test("겹친 새로고침은 늦게 끝난 이전 응답을 버리고 다음 페이지 기준을 유지한다")
    func overlappingRefreshDiscardsOldCompletionAndPage() async {
        let loader = SuspendedNotificationInboxPageLoader()
        let viewModel = Self.viewModel(pageLoader: loader)

        let firstRefresh = Task { await viewModel.refresh() }
        await loader.waitForRequestCount(1)
        let secondRefresh = Task { await viewModel.refresh() }
        await loader.waitForRequestCount(2)

        loader.completeRequest(
            at: 1,
            with: .success(Self.page(1, hasNext: true, items: [Self.item(id: Self.notificationID(2))]))
        )
        await secondRefresh.value
        loader.completeRequest(
            at: 0,
            with: .success(Self.page(10, hasNext: true, items: [Self.item(id: Self.notificationID(10))]))
        )
        await firstRefresh.value

        #expect(viewModel.items.map(\.id) == [Self.notificationID(2)])
        #expect(viewModel.hasNext)

        loader.completeImmediately(
            .success(Self.page(2, hasNext: false, items: [Self.item(id: Self.notificationID(3))]))
        )
        await viewModel.loadNextPage()

        #expect(loader.requestedPages == [1, 1, 2])
        #expect(viewModel.items.map(\.id) == [Self.notificationID(2), Self.notificationID(3)])
    }

    private static func viewModel(pageLoader: NotificationInboxPageLoading) -> NotificationInboxViewModel {
        viewModel(
            pageLoader: pageLoader,
            unreadLoader: NotificationUnreadLoader(results: [.success(false)]),
            readAction: NotificationReadAction(results: [])
        )
    }

    private static func viewModel(
        pageLoader: NotificationInboxPageLoading,
        unreadLoader: NotificationUnreadLoading,
        readAction: NotificationReading
    ) -> NotificationInboxViewModel {
        NotificationInboxViewModel(
            loadPage: { try await pageLoader.load(page: $0) },
            loadUnread: { try await unreadLoader.load() },
            markRead: { try await readAction.markRead(notificationID: $0) }
        )
    }

    private static func page(_ page: Int, hasNext: Bool, items: [NotificationItem]) -> NotificationPage {
        NotificationPage(currentPage: page, pageSize: 20, hasNext: hasNext, items: items)
    }

    private static func item(id: String, readAt: Date? = nil) -> NotificationItem {
        NotificationItem(
            id: id,
            type: "POST_APPROVED",
            sourceType: "POST",
            sourceID: notificationID(100),
            title: "제목 \(id)",
            body: "본문",
            thumbnailURL: URL(string: "https://example.com/\(id).webp"),
            readAt: readAt,
            createdAt: Date(timeIntervalSince1970: 1_780_000_000)
        )
    }

    private static func notificationID(_ value: Int) -> String {
        String(format: "019a0010-0000-7000-8000-%012d", value)
    }
}

@Suite(.serialized)
struct NotificationAPIClientTests {
    @Test("목록 조회는 페이지 쿼리를 보내고 UTC 날짜와 null 필드를 디코딩한다")
    func fetchPageSendsQueryAndDecodesDates() async throws {
        let recorder = NotificationAPIRequestRecorder()
        NotificationAPIMockURLProtocol.install { request in
            await recorder.append(request)
            return Self.response(
                for: request,
                body: """
                {
                  "currentPage": 2,
                  "pageSize": 20,
                  "hasNext": true,
                  "notifications": [
                    {
                      "id": "019a0010-0000-7000-8000-000000000001",
                      "type": "POST_APPROVED",
                      "sourceType": "POST",
                      "sourceId": "019a0010-0000-7000-8000-000000000101",
                      "title": "승인",
                      "body": "본문",
                      "thumbnailImageUrl": null,
                      "readAt": null,
                      "createdAt": "2026-10-11T06:36:15.450Z"
                    },
                    {
                      "id": "019a0010-0000-7000-8000-000000000002",
                      "type": "POST_REJECTED",
                      "sourceType": null,
                      "sourceId": null,
                      "title": "반려",
                      "body": "본문",
                      "thumbnailImageUrl": "https://example.com/thumb.webp",
                      "readAt": "2026-10-11T06:40:15Z",
                      "createdAt": "2026-10-11T06:35:15Z"
                    }
                  ]
                }
                """
            )
        }
        defer { NotificationAPIMockURLProtocol.uninstall() }

        let page = try await Self.client().fetchPage(page: 2)

        #expect(page.currentPage == 2)
        #expect(page.hasNext)
        #expect(page.items.count == 2)
        #expect(page.items.first?.thumbnailURL == nil)
        #expect(page.items.first?.readAt == nil)
        #expect(page.items.first?.destination == .postApproved(
            postID: "019a0010-0000-7000-8000-000000000101",
            notificationID: "019a0010-0000-7000-8000-000000000001"
        ))
        #expect(page.items.last?.thumbnailURL == URL(string: "https://example.com/thumb.webp"))
        #expect(page.items.last?.readAt == ISO8601DateFormatter.chalkakTestDate("2026-10-11T06:40:15Z"))

        let request = try #require(await recorder.firstRequest)
        #expect(request.httpMethod == "GET")
        #expect(request.url?.path == "/api/v1/notifications")
        let url = try #require(request.url)
        let components = try #require(URLComponents(url: url, resolvingAgainstBaseURL: false))
        #expect(components.queryItems?.contains(URLQueryItem(name: "page", value: "2")) == true)
        #expect(components.queryItems?.contains(URLQueryItem(name: "pageSize", value: "20")) == true)
        #expect(request.value(forHTTPHeaderField: "Authorization") == "Bearer access-token")
    }

    @Test("미읽음 상태 조회는 unread-status 응답을 반환한다")
    func fetchUnreadStatus() async throws {
        let recorder = NotificationAPIRequestRecorder()
        NotificationAPIMockURLProtocol.install { request in
            await recorder.append(request)
            return Self.response(for: request, body: #"{"hasUnread":true}"#)
        }
        defer { NotificationAPIMockURLProtocol.uninstall() }

        let hasUnread = try await Self.client().fetchUnreadStatus()

        #expect(hasUnread)
        #expect(await recorder.firstRequest?.url?.path == "/api/v1/notifications/unread-status")
    }

    @Test("전체 읽음은 read-all로 PATCH 204를 보낸다")
    func markAllReadSendsPatch() async throws {
        let recorder = NotificationAPIRequestRecorder()
        NotificationAPIMockURLProtocol.install { request in
            await recorder.append(request)
            return Self.response(for: request, statusCode: 204, body: "")
        }
        defer { NotificationAPIMockURLProtocol.uninstall() }

        try await Self.client().markAllRead()

        let request = try #require(await recorder.firstRequest)
        #expect(request.httpMethod == "PATCH")
        #expect(request.url?.path == "/api/v1/notifications/read-all")
    }

    @Test("단건 읽음 404는 notFound로 매핑한다")
    func markReadMapsNotFound() async {
        NotificationAPIMockURLProtocol.install { request in
            Self.response(for: request, statusCode: 404, body: #"{"errorCode":"NOT_FOUND","message":"없음"}"#)
        }
        defer { NotificationAPIMockURLProtocol.uninstall() }

        await #expect(throws: NotificationAPIError.notFound) {
            try await Self.client().markRead(notificationID: "019a0010-0000-7000-8000-000000000001")
        }
    }

    private static func client(accessToken: String = "access-token") -> NotificationAPIClient {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [NotificationAPIMockURLProtocol.self]
        return NotificationAPIClient(
            baseURL: URL(string: "https://example.com/api/v1")!,
            session: URLSession(configuration: configuration),
            accessTokenProvider: { accessToken }
        )
    }

    private static func response(
        for request: URLRequest,
        statusCode: Int = 200,
        body: String
    ) -> (HTTPURLResponse, Data) {
        let response = HTTPURLResponse(
            url: request.url!,
            statusCode: statusCode,
            httpVersion: nil,
            headerFields: ["Content-Type": "application/json"]
        )!
        return (response, Data(body.utf8))
    }
}

@MainActor
private protocol NotificationInboxPageLoading: AnyObject {
    var requestedPages: [Int] { get async }

    func load(page: Int) async throws -> NotificationPage
}

@MainActor
private protocol NotificationUnreadLoading: AnyObject {
    func load() async throws -> Bool
}

@MainActor
private protocol NotificationReading: AnyObject {
    var requestedIDs: [String] { get async }

    func markRead(notificationID: String) async throws
}

@MainActor
private final class NotificationInboxPageLoader: NotificationInboxPageLoading {
    private var results: [Result<NotificationPage, Error>]
    private(set) var requestedPages: [Int] = []

    init(results: [Result<NotificationPage, Error>]) {
        self.results = results
    }

    func load(page: Int) async throws -> NotificationPage {
        requestedPages.append(page)
        guard !results.isEmpty else { throw NotificationAPIError.network }
        return try results.removeFirst().get()
    }
}

@MainActor
private final class SuspendedNotificationInboxPageLoader: NotificationInboxPageLoading {
    private struct PendingRequest {
        let page: Int
        let continuation: CheckedContinuation<NotificationPage, Error>
    }

    private var pendingRequests: [PendingRequest] = []
    private var waiters: [(Int, CheckedContinuation<Void, Never>)] = []
    private var immediateResults: [Result<NotificationPage, Error>] = []
    private(set) var requestedPages: [Int] = []

    func load(page: Int) async throws -> NotificationPage {
        requestedPages.append(page)
        resumeReadyWaiters()
        if !immediateResults.isEmpty {
            return try immediateResults.removeFirst().get()
        }
        return try await withCheckedThrowingContinuation { continuation in
            pendingRequests.append(PendingRequest(page: page, continuation: continuation))
            resumeReadyWaiters()
        }
    }

    func waitForRequestCount(_ count: Int) async {
        guard requestedPages.count < count else { return }
        await withCheckedContinuation { continuation in
            waiters.append((count, continuation))
        }
    }

    func completeRequest(at index: Int, with result: Result<NotificationPage, Error>) {
        let request = pendingRequests.remove(at: index)
        request.continuation.resume(with: result)
    }

    func completeImmediately(_ result: Result<NotificationPage, Error>) {
        immediateResults.append(result)
    }

    private func resumeReadyWaiters() {
        let ready = waiters.filter { requestedPages.count >= $0.0 }
        waiters.removeAll { requestedPages.count >= $0.0 }
        ready.forEach { $0.1.resume() }
    }
}

@MainActor
private final class NotificationUnreadLoader: NotificationUnreadLoading {
    private var results: [Result<Bool, Error>]

    init(results: [Result<Bool, Error>]) {
        self.results = results
    }

    func load() async throws -> Bool {
        guard !results.isEmpty else { return false }
        return try results.removeFirst().get()
    }
}

@MainActor
private final class NotificationReadAction: NotificationReading {
    private var results: [Result<Void, Error>]
    private(set) var requestedIDs: [String] = []

    init(results: [Result<Void, Error>]) {
        self.results = results
    }

    func markRead(notificationID: String) async throws {
        requestedIDs.append(notificationID)
        guard !results.isEmpty else { return }
        try results.removeFirst().get()
    }
}

private actor NotificationAPIRequestRecorder {
    private(set) var requests: [URLRequest] = []

    var firstRequest: URLRequest? { requests.first }

    func append(_ request: URLRequest) {
        requests.append(request)
    }
}

private final class NotificationAPIMockURLProtocol: URLProtocol, @unchecked Sendable {
    typealias Handler = @Sendable (URLRequest) async -> (HTTPURLResponse, Data)

    nonisolated(unsafe) private static var handler: Handler?

    static func install(handler: @escaping Handler) {
        self.handler = handler
    }

    static func uninstall() {
        handler = nil
    }

    override class func canInit(with request: URLRequest) -> Bool { true }

    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        guard let handler = Self.handler else {
            client?.urlProtocol(self, didFailWithError: URLError(.badServerResponse))
            return
        }

        Task {
            let (response, data) = await handler(request)
            client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
            client?.urlProtocol(self, didLoad: data)
            client?.urlProtocolDidFinishLoading(self)
        }
    }

    override func stopLoading() {}
}

private extension ISO8601DateFormatter {
    static func chalkakTestDate(_ value: String) -> Date? {
        let formatter = ISO8601DateFormatter()
        formatter.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        if let date = formatter.date(from: value) { return date }
        formatter.formatOptions = [.withInternetDateTime]
        return formatter.date(from: value)
    }
}
