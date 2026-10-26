import Foundation
import Testing
@testable import chalkak

@MainActor
@Suite(.serialized)
struct AuthorPostsAPIClientTests {
    @Test("작가 ID 경로와 page·pageSize 20으로 요청하고, 썸네일이 없으면 원본을 쓴다")
    func requestsAuthorPageAndFallsBackToOriginal() async throws {
        let recorder = AuthorPostsRequestRecorder()
        let client = Self.makeClient(accessToken: "token") { request in
            await recorder.append(request)
            return Self.response(
                for: request,
                body: #"{"currentPage":2,"pageSize":20,"hasNext":true,"posts":[{"id":"post-1","originalImageUrl":"https://example.com/original-1.webp","thumbnailImageUrl":"https://example.com/thumb-1.webp","topic":{"id":7,"title":"하늘","topicDate":"2026-10-19"}},{"id":"post-2","originalImageUrl":"https://example.com/original-2.webp","thumbnailImageUrl":null,"topic":{"id":6,"title":"바다","topicDate":"2026-10-18"}}]}"#
            )
        }

        let page = try await client.fetchPage(authorID: "author-1", page: 2)

        #expect(page.currentPage == 2)
        #expect(page.hasNext)
        #expect(page.posts.map(\.id) == ["post-1", "post-2"])
        #expect(page.posts[0].thumbnailImageSource == .remote(URL(string: "https://example.com/thumb-1.webp")))
        #expect(page.posts[1].thumbnailImageSource == .remote(URL(string: "https://example.com/original-2.webp")))
        #expect(page.posts[1].topic == "바다")
        let request = try #require(await recorder.requests.first)
        #expect(request.url?.path == "/api/v1/users/author-1/posts")
        #expect(request.url?.query?.contains("page=2") == true)
        #expect(request.url?.query?.contains("pageSize=20") == true)
        #expect(request.value(forHTTPHeaderField: "Authorization") == "Bearer token")
    }

    @Test("공개 사진이 없는 작가는 200 빈 목록으로 받는다")
    func decodesEmptyPage() async throws {
        let client = Self.makeClient { request in
            Self.response(for: request, body: #"{"currentPage":1,"pageSize":20,"hasNext":false,"posts":[]}"#)
        }

        let page = try await client.fetchPage(authorID: "author-1", page: 1)

        #expect(page.posts.isEmpty)
        #expect(!page.hasNext)
    }

    @Test("없거나 탈퇴한 작가(404)는 authorNotFound, 401은 unauthorized로 바꾼다")
    func mapsNotFoundAndUnauthorized() async throws {
        let notFoundClient = Self.makeClient { request in
            Self.response(for: request, statusCode: 404, body: #"{"message":"not found"}"#)
        }
        await #expect(throws: AuthorPostsAPIError.http(404)) {
            try await notFoundClient.fetchPage(authorID: "author-1", page: 1)
        }

        let unauthorizedClient = Self.makeClient { request in
            Self.response(for: request, statusCode: 401, body: #"{}"#)
        }
        await #expect(throws: AuthorPostsAPIError.http(401)) {
            try await unauthorizedClient.fetchPage(authorID: "author-1", page: 1)
        }

        #expect(AuthorPostsAPIError.http(404).authorPostsError == .authorNotFound)
        #expect(AuthorPostsAPIError.http(401).authorPostsError == .unauthorized)
        #expect(AuthorPostsAPIError.http(500).authorPostsError == .generic)
        #expect(AuthorPostsAPIError.network.authorPostsError == .network)
    }

    private static func makeClient(
        accessToken: String? = nil,
        handler: @escaping AuthorPostsMockURLProtocol.Handler
    ) -> AuthorPostsAPIClient {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [AuthorPostsMockURLProtocol.self]
        AuthorPostsMockURLProtocol.handler = handler
        return AuthorPostsAPIClient(
            configuration: AuthorPostsAPIConfiguration(
                baseURL: URL(string: "https://example.com/api/v1/")!
            ),
            session: URLSession(configuration: configuration),
            accessTokenProvider: { accessToken }
        )
    }

    nonisolated private static func response(
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
@Suite(.serialized)
struct AuthorPostsViewModelTests {
    @Test("1페이지를 받고 끝에 닿으면 다음 페이지를 이어 붙이며 중복 사진은 한 번만 둔다")
    func appendsNextPageWithoutDuplicates() async {
        let recorder = AuthorPostsPageRecorder()
        let viewModel = AuthorPostsViewModel(target: Self.target) { page in
            recorder.pages.append(page)
            switch page {
            case 1:
                return .success(AuthorPostsPage(posts: [Self.post("a"), Self.post("b")], currentPage: 1, hasNext: true))
            default:
                // 조회 중 새 사진이 승인되어 b가 다음 페이지로 밀려 다시 온 경우.
                return .success(AuthorPostsPage(posts: [Self.post("b"), Self.post("c")], currentPage: 2, hasNext: false))
            }
        }

        await viewModel.load()
        await viewModel.didReachEndThreshold(true)

        #expect(recorder.pages == [1, 2])
        #expect(viewModel.viewState.posts.map(\.id) == ["a", "b", "c"])
        #expect(viewModel.viewState.currentPage == 2)
        #expect(!viewModel.viewState.hasNext)
        #expect(!viewModel.viewState.isLoadingNext)

        // 마지막 페이지 뒤로는 더 요청하지 않는다.
        await viewModel.didReachEndThreshold(false)
        await viewModel.didReachEndThreshold(true)
        #expect(recorder.pages == [1, 2])
    }

    @Test("빈 목록은 loaded 상태의 빈 화면이다")
    func emptyPageIsLoadedAndEmpty() async {
        let viewModel = AuthorPostsViewModel(target: Self.target) { _ in
            .success(AuthorPostsPage(posts: [], currentPage: 1, hasNext: false))
        }

        await viewModel.load()

        #expect(viewModel.viewState.contentStatus == .loaded)
        #expect(viewModel.viewState.isEmpty)
        #expect(viewModel.viewState.showsAuthorIdentity)
    }

    @Test("404면 작가 정보를 숨기는 상태가 되고, 실패 문구를 구분한다")
    func notFoundHidesAuthorIdentity() async {
        let viewModel = AuthorPostsViewModel(target: Self.target) { _ in .failure(.authorNotFound) }

        await viewModel.load()

        #expect(viewModel.viewState.contentStatus == .error(.authorNotFound))
        #expect(!viewModel.viewState.showsAuthorIdentity)
        #expect(AuthorPostsError.authorNotFound.message == "더 이상 사진을 볼 수 없어요")
        #expect(AuthorPostsError.unauthorized.message == "사진을 불러오지 못했어요")
        #expect(AuthorPostsError.network.message == "사진을 불러오지 못했어요")
    }

    @Test("다시 시도는 1페이지부터 새로 받는다")
    func retryReloadsFromFirstPage() async {
        let recorder = AuthorPostsPageRecorder()
        let viewModel = AuthorPostsViewModel(target: Self.target) { page in
            recorder.pages.append(page)
            if recorder.pages.count == 1 { return .failure(.network) }
            return .success(AuthorPostsPage(posts: [Self.post("a")], currentPage: page, hasNext: false))
        }

        await viewModel.load()
        #expect(viewModel.viewState.contentStatus == .error(.network))

        await viewModel.retry()

        #expect(recorder.pages == [1, 1])
        #expect(viewModel.viewState.contentStatus == .loaded)
        #expect(viewModel.viewState.posts.map(\.id) == ["a"])
    }

    @Test("다음 페이지 실패는 받은 사진을 두고 알림을 보낸 뒤, 다시 끝에 닿으면 같은 페이지를 요청한다")
    func nextPageFailureKeepsPostsAndRetriesSamePage() async {
        let recorder = AuthorPostsPageRecorder()
        let viewModel = AuthorPostsViewModel(target: Self.target) { page in
            recorder.pages.append(page)
            if page == 1 {
                return .success(AuthorPostsPage(posts: [Self.post("a")], currentPage: 1, hasNext: true))
            }
            if recorder.pages.count == 2 { return .failure(.network) }
            return .success(AuthorPostsPage(posts: [Self.post("b")], currentPage: 2, hasNext: false))
        }

        await viewModel.load()
        await viewModel.didReachEndThreshold(true)

        #expect(viewModel.event == .nextPageFailed)
        #expect(viewModel.viewState.posts.map(\.id) == ["a"])
        #expect(viewModel.viewState.contentStatus == .loaded)

        viewModel.consumeEvent()
        await viewModel.didReachEndThreshold(true)

        #expect(recorder.pages == [1, 2, 2])
        #expect(viewModel.viewState.posts.map(\.id) == ["a", "b"])
    }

    @Test("목록에서 연 피드에서 삭제한 사진을 그리드에서 뺀다")
    func removesDeletedPost() async {
        let viewModel = AuthorPostsViewModel(target: Self.target) { _ in
            .success(AuthorPostsPage(posts: [Self.post("a"), Self.post("b")], currentPage: 1, hasNext: false))
        }
        await viewModel.load()

        viewModel.removePost("a")

        #expect(viewModel.viewState.posts.map(\.id) == ["b"])
    }

    private static let target = AuthorPostsTarget(
        authorID: "author-1",
        signatureImageSource: .asset("preview_signature"),
        isMine: false
    )

    private static func post(_ id: String) -> AuthorPost {
        AuthorPost(
            id: id,
            originalImageSource: .asset("preview_photo"),
            thumbnailImageSource: .asset("preview_photo"),
            topic: "하늘",
            topicDate: Date(timeIntervalSince1970: 0),
            contentDescription: "설명"
        )
    }
}

@MainActor
private final class AuthorPostsPageRecorder {
    var pages: [Int] = []
}

private actor AuthorPostsRequestRecorder {
    private(set) var requests: [URLRequest] = []

    func append(_ request: URLRequest) {
        requests.append(request)
    }
}

private final class AuthorPostsMockURLProtocol: URLProtocol, @unchecked Sendable {
    typealias Handler = @Sendable (URLRequest) async throws -> (HTTPURLResponse, Data)

    nonisolated(unsafe) static var handler: Handler?

    override class func canInit(with request: URLRequest) -> Bool {
        true
    }

    override class func canonicalRequest(for request: URLRequest) -> URLRequest {
        request
    }

    override func startLoading() {
        guard let handler = Self.handler else {
            client?.urlProtocol(self, didFailWithError: URLError(.badServerResponse))
            return
        }
        let request = request
        Task {
            do {
                let (response, data) = try await handler(request)
                client?.urlProtocol(self, didReceive: response, cacheStoragePolicy: .notAllowed)
                client?.urlProtocol(self, didLoad: data)
                client?.urlProtocolDidFinishLoading(self)
            } catch {
                client?.urlProtocol(self, didFailWithError: error)
            }
        }
    }

    override func stopLoading() {}
}
