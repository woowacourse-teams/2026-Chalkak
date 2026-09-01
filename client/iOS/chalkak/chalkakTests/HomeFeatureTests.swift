import CoreGraphics
import Foundation
import SwiftUI
import Testing
import UIKit
@testable import chalkak

@MainActor
struct HomeViewStateTests {
    @Test("기본 홈 상태는 로딩으로 시작한다")
    func defaultsToLoadingStatus() {
        let state = HomeViewState()

        #expect(state.contentStatus == .loading)
        #expect(state.photos.isEmpty)
        #expect(state.selectedSort == .latest)
    }

    @Test("초기 에러는 사용자에게 보여줄 문구를 제공한다")
    func exposesInitialErrorMessage() {
        #expect(HomeInitialError.network.message == "네트워크 연결을 확인해 주세요")
        #expect(HomeInitialError.topicNotFound.message == "오늘의 주제가 아직 준비되지 않았어요")
    }

    @Test("좋아요 가능 여부의 기본값은 활성화 상태다")
    func enablesLikesByDefault() {
        let state = HomeViewState()

        #expect(state.areLikesEnabled)
    }
}

struct HomeScreenTests {
    @MainActor
    @Test("콘텐츠 홈 화면은 iPhone 폭에서 렌더링된다")
    func rendersContentAtIPhoneWidth() {
        let viewModel = HomeViewModel(initialState: HomePreviewData.contentState)
        let view = HomeScreen(
            viewModel: viewModel
        )
        .chalkakTheme(.light)
        let hostingController = UIHostingController(
            rootView: view.frame(width: 402, height: 874)
        )

        let size = hostingController.sizeThatFits(
            in: CGSize(width: 402, height: 874)
        )

        #expect(size.width == 402)
        #expect(size.height == 874)
    }

    @MainActor
    @Test("바텀바 선택 액션은 선택한 항목을 보존한다")
    func preservesSelectedBottomBarActionItem() async {
        let viewModel = HomeViewModel(initialState: HomePreviewData.contentState)

        await viewModel.selectBottomBarItem(.display)

        #expect(viewModel.event == .navigateToBottomBar(.display))
    }
}

struct HomeViewModelBehaviorTests {
    @MainActor
    @Test("비로그인 사용자가 좋아요를 누르면 게스트 안내 이벤트를 발행한다")
    func publishesGuestLikeEventWhenUnauthenticatedUserLikesPhoto() async {
        let viewModel = HomeViewModel(
            initialState: HomeViewState(
                contentStatus: .content,
                photos: [Self.photo(id: "photo-1")],
                areLikesEnabled: true
            ),
            isAuthenticated: { false }
        )

        await viewModel.toggleLike(photoID: "photo-1")

        #expect(viewModel.event == .showGuestLikeMessage)
        #expect(viewModel.viewState.likedPhotoIDs.isEmpty)
    }

    @MainActor
    @Test("같은 사진의 좋아요 요청은 진행 중에 중복 실행하지 않는다")
    func preventsConcurrentLikeRequestsForSamePhoto() async {
        let request = SuspendedLikeRequest()
        let viewModel = HomeViewModel(
            initialState: HomeViewState(
                contentStatus: .content,
                photos: [Self.photo(id: "photo-1")],
                areLikesEnabled: true
            ),
            isAuthenticated: { true },
            likeHandler: { photoID, isLiked in
                await request.handle(photoID: photoID, isLiked: isLiked)
            }
        )

        let firstTap = Task { await viewModel.toggleLike(photoID: "photo-1") }
        await request.waitUntilStarted()
        let secondTap = Task { await viewModel.toggleLike(photoID: "photo-1") }
        await secondTap.value

        #expect(request.callCount == 1)

        request.complete()
        await firstTap.value

        #expect(viewModel.viewState.likedPhotoIDs == ["photo-1"])
        #expect(viewModel.viewState.photos.first?.likeCount == 2)
    }

    @MainActor
    @Test("새로고침은 랜덤 정렬 결과로 콘텐츠를 교체한다")
    func refreshReplacesContentWithRandomSortResult() async {
        let viewModel = HomeViewModel(
            initialState: HomePreviewData.contentState,
            refreshHandler: { _ in
                .success(
                    HomeViewState(
                        contentStatus: .content,
                        topic: "새 주제",
                        photos: [Self.photo(id: "new-photo")]
                    )
                )
            }
        )

        await viewModel.refresh()

        #expect(viewModel.viewState.selectedSort == .random)
        #expect(viewModel.viewState.topic == "새 주제")
        #expect(viewModel.viewState.photos.map(\.id) == ["new-photo"])
    }

    @MainActor
    @Test("끝 임계값 도달 시 다음 페이지를 한 번 추가한다")
    func appendsNextPageWhenEndThresholdIsReached() async {
        let viewModel = HomeViewModel(
            initialState: HomeViewState(
                contentStatus: .content,
                photos: [Self.photo(id: "photo-1")],
                currentPage: 0,
                hasNext: true
            ),
            nextPageHandler: { _ in
                .success(
                    HomePage(
                        photos: [Self.photo(id: "photo-2")],
                        likedPhotoIDs: ["photo-2"],
                        currentPage: 1,
                        hasNext: false,
                        randomSeed: nil
                    )
                )
            }
        )

        await viewModel.didReachEndThreshold(true)

        #expect(viewModel.viewState.photos.map(\.id) == ["photo-1", "photo-2"])
        #expect(viewModel.viewState.likedPhotoIDs == ["photo-2"])
        #expect(!viewModel.viewState.hasNext)
    }

    private static func photo(id: String) -> HomePhoto {
        HomePhoto(
            id: id,
            imageSource: .asset("preview_photo"),
            signatureSource: .asset("preview_signature"),
            contentDescription: "테스트 사진",
            title: "테스트 제목",
            likeCount: 1
        )
    }
}

@MainActor
private final class SuspendedLikeRequest {
    private(set) var callCount = 0
    private var didStart = false
    private var startWaiters: [CheckedContinuation<Void, Never>] = []
    private var requestContinuation: CheckedContinuation<Void, Never>?

    func waitUntilStarted() async {
        guard !didStart else { return }
        await withCheckedContinuation { continuation in
            startWaiters.append(continuation)
        }
    }

    func handle(photoID: HomePhoto.ID, isLiked: Bool) async -> Result<HomeLikeUpdate, HomeInitialError> {
        callCount += 1
        didStart = true
        startWaiters.forEach { $0.resume() }
        startWaiters.removeAll()

        await withCheckedContinuation { continuation in
            requestContinuation = continuation
        }
        return .success(
            HomeLikeUpdate(
                photoID: photoID,
                isLiked: isLiked,
                likeCount: 2
            )
        )
    }

    func complete() {
        requestContinuation?.resume()
        requestContinuation = nil
    }
}

@MainActor
@Suite(.serialized)
struct HomeAPIClientTests {
    @Test("홈 API는 주제의 기준 날짜로 게시물을 조회하고 응답을 화면 상태로 변환한다")
    func fetchesTopicThenPosts() async throws {
        let recorder = RequestRecorder()
        let client = makeClient { request in
            await recorder.append(request)
            switch request.url?.path {
            case "/api/v1/topics":
                return Self.response(
                    for: request,
                    body: #"{"id":"topic-id","title":"반짝임","topicDate":"2026-09-01"}"#
                )
            case "/api/v1/posts":
                return Self.response(
                    for: request,
                    body: #"{"currentPage":1,"pageSize":20,"hasNext":false,"randomSeed":null,"posts":[{"id":"post-1","originalImageUrl":"https://example.com/photo.webp","thumbnailImageUrl":"https://example.com/thumb.webp","signatureOriginalImageUrl":"https://example.com/signature.png","signatureThumbnailImageUrl":"https://example.com/signature-thumb.png","title":"빛","submittedAt":"2026-09-01T00:00:00Z","likeCount":2,"isLiked":true,"isMine":false}]}"#
                )
            default:
                return Self.response(for: request, statusCode: 404, body: "{}")
            }
        }

        let state = try await client.fetchHome(
            date: Self.date(year: 2026, month: 9, day: 1),
            sort: .latest
        )

        #expect(state.topic == "반짝임")
        #expect(state.photos.map(\.id) == ["post-1"])
        #expect(state.likedPhotoIDs == ["post-1"])
        let requests = await recorder.requests
        #expect(requests.count == 2)
        #expect(requests[0].url?.query?.contains("date=2026-09-01") == true)
        #expect(requests[1].url?.query?.contains("sort=recent") == true)
        #expect(requests[1].url?.query?.contains("pageSize=20") == true)
    }

    @Test("좋아요 API는 토큰과 HTTP 메서드를 전달한다")
    func sendsAuthenticatedLikeRequest() async throws {
        let recorder = RequestRecorder()
        let client = makeClient(accessToken: "access-token") { request in
            await recorder.append(request)
            return Self.response(
                for: request,
                body: #"{"postId":"post-1","likeCount":3,"isLiked":true}"#
            )
        }

        let update = try await client.updateLike(photoID: "post-1", isLiked: true)

        #expect(update.likeCount == 3)
        let request = try #require(await recorder.requests.first)
        #expect(request.httpMethod == "PUT")
        #expect(request.url?.path == "/api/v1/posts/post-1/likes")
        #expect(request.value(forHTTPHeaderField: "Authorization") == "Bearer access-token")
    }

    private func makeClient(
        accessToken: String? = nil,
        handler: @escaping MockURLProtocol.Handler
    ) -> HomeAPIClient {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [MockURLProtocol.self]
        MockURLProtocol.handler = handler
        return HomeAPIClient(
            configuration: HomeAPIConfiguration(
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

    private static func date(year: Int, month: Int, day: Int) -> Date {
        Calendar(identifier: .gregorian).date(
            from: DateComponents(year: year, month: month, day: day)
        )!
    }
}

private actor RequestRecorder {
    private(set) var requests: [URLRequest] = []

    func append(_ request: URLRequest) {
        requests.append(request)
    }
}

private final class MockURLProtocol: URLProtocol, @unchecked Sendable {
    typealias Handler = @Sendable (URLRequest) async throws -> (HTTPURLResponse, Data)

    nonisolated(unsafe) static var handler: Handler?

    override class func canInit(with request: URLRequest) -> Bool { true }

    override class func canonicalRequest(for request: URLRequest) -> URLRequest { request }

    override func startLoading() {
        guard let handler = Self.handler else {
            client?.urlProtocol(self, didFailWithError: URLError(.badServerResponse))
            return
        }
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
