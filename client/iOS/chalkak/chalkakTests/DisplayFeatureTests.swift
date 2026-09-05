import Foundation
import Testing
@testable import chalkak

@MainActor
struct DisplayViewStateTests {
    @Test("기본 상태는 로딩이며 날짜 이동을 막는다")
    func defaultsToLoading() {
        let state = DisplayViewState()

        #expect(state.contentStatus == .loading)
        #expect(!state.canGoPrevious)
        #expect(!state.canGoNext)
        #expect(state.photos.isEmpty)
    }
}

@MainActor
@Suite(.serialized)
struct DisplayViewModelTests {
    @Test("topics의 canonical 날짜로 최신 상태를 확정한다")
    func appliesCanonicalLatestDate() async {
        let requestedDate = Self.date(2026, 9, 2)
        let canonicalDate = Self.date(2026, 9, 1)
        let recorder = DisplayRequestRecorder()
        let viewModel = DisplayViewModel(
            dateProvider: { canonicalDate },
            firstPageHandler: { date, sort in
                recorder.firstPages.append((date, sort))
                return .success(Self.content(date: canonicalDate, topic: "반짝임"))
            }
        )

        await viewModel.load()

        #expect(viewModel.viewState.selectedDate == canonicalDate)
        #expect(viewModel.viewState.contentStatus == .latest)
        #expect(viewModel.viewState.selectedSort == .latest)
        #expect(recorder.firstPages.map(\.1) == [.latest])
        #expect(requestedDate > canonicalDate)
    }

    @Test("과거 전시는 popular 고정이며 최신으로 돌아오면 기존 정렬을 복원한다")
    func fixesArchiveSortAndRestoresLatestSort() async {
        let latestDate = Self.date(2026, 9, 2)
        let recorder = DisplayRequestRecorder()
        let viewModel = DisplayViewModel(
            dateProvider: { latestDate },
            firstPageHandler: { date, sort in
                recorder.firstPages.append((date, sort))
                return .success(Self.content(date: date))
            }
        )
        await viewModel.load()

        await viewModel.selectSort(.random)
        await viewModel.moveToPreviousDate()

        #expect(viewModel.viewState.contentStatus == .archive)
        #expect(viewModel.viewState.featuredPhotos.count == 1)
        #expect(recorder.firstPages.last?.1 == .popular)

        await viewModel.moveToNextDate()

        #expect(viewModel.viewState.contentStatus == .latest)
        #expect(viewModel.viewState.selectedSort == .random)
        #expect(recorder.firstPages.last?.1 == .random)
    }

    @Test("이전 날짜 404만 최초 전시일 경계로 기록하고 성공 콘텐츠를 복원한다")
    func recordsOnlyPreviousNotFoundAsBoundary() async {
        let latestDate = Self.date(2026, 9, 2)
        var shouldFail = false
        let viewModel = DisplayViewModel(
            dateProvider: { latestDate },
            firstPageHandler: { date, _ in
                shouldFail ? .failure(.topicNotFound) : .success(Self.content(date: date))
            }
        )
        await viewModel.load()
        let previousPhotos = viewModel.viewState.photos
        shouldFail = true

        await viewModel.moveToPreviousDate()

        #expect(viewModel.viewState.selectedDate == latestDate)
        #expect(viewModel.viewState.earliestDate == latestDate)
        #expect(viewModel.viewState.photos == previousPhotos)
        #expect(viewModel.event == .showFailure(.topicNotFound))
        #expect(!viewModel.viewState.canGoPrevious)
    }

    @Test("네트워크 날짜 실패는 경계를 만들지 않고 성공 콘텐츠를 복원한다")
    func restoresContentWithoutBoundaryForNetworkFailure() async {
        let latestDate = Self.date(2026, 9, 2)
        var shouldFail = false
        let viewModel = DisplayViewModel(
            dateProvider: { latestDate },
            firstPageHandler: { date, _ in
                shouldFail ? .failure(.network) : .success(Self.content(date: date))
            }
        )
        await viewModel.load()
        shouldFail = true

        await viewModel.moveToPreviousDate()

        #expect(viewModel.viewState.selectedDate == latestDate)
        #expect(viewModel.viewState.earliestDate == nil)
        #expect(viewModel.viewState.contentStatus == .latest)
        #expect(viewModel.event == .showFailure(.network))
        #expect(viewModel.viewState.canGoPrevious)
    }

    @Test("초기 실패는 요청 날짜를 유지한 오류 상태가 된다")
    func exposesInitialFailure() async {
        let latestDate = Self.date(2026, 9, 2)
        let archiveDate = Self.date(2026, 8, 31)
        let viewModel = DisplayViewModel(
            initialDate: archiveDate,
            dateProvider: { latestDate },
            firstPageHandler: { _, _ in .failure(.server) }
        )

        await viewModel.load()

        #expect(viewModel.viewState.selectedDate == archiveDate)
        #expect(viewModel.viewState.contentStatus == .error(.server))
    }

    @Test("랜덤 다음 페이지는 시드를 재사용하고 ID를 페이지 경계에서 제거한다")
    func reusesRandomSeedAndDeduplicatesPageBoundary() async {
        let latestDate = Self.date(2026, 9, 2)
        let recorder = DisplayRequestRecorder()
        let firstPhoto = Self.photo(id: "photo-1")
        let viewModel = DisplayViewModel(
            dateProvider: { latestDate },
            firstPageHandler: { date, sort in
                .success(
                    Self.content(
                        date: date,
                        page: DisplayPage(
                            photos: [firstPhoto],
                            currentPage: 1,
                            hasNext: true,
                            randomSeed: sort == .random ? "seed-1" : nil
                        )
                    )
                )
            },
            nextPageHandler: { request in
                recorder.nextPages.append(
                    (request.topicDate, request.sort, request.page, request.randomSeed)
                )
                return .success(
                    DisplayPage(
                        photos: [firstPhoto, Self.photo(id: "photo-2")],
                        currentPage: 2,
                        hasNext: false,
                        randomSeed: request.randomSeed
                    )
                )
            }
        )
        await viewModel.load()
        await viewModel.selectSort(.random)

        await viewModel.didReachEndThreshold(true)

        #expect(viewModel.viewState.photos.map(\.id) == ["photo-1", "photo-2"])
        #expect(viewModel.viewState.currentPage == 2)
        #expect(recorder.nextPages.count == 1)
        #expect(recorder.nextPages.first?.1 == .random)
        #expect(recorder.nextPages.first?.2 == 2)
        #expect(recorder.nextPages.first?.3 == "seed-1")
    }

    @Test("이전 generation의 느린 응답은 최신 상태를 덮지 않는다")
    func ignoresStaleFirstPageResponse() async {
        let firstDate = Self.date(2026, 9, 1)
        let secondDate = Self.date(2026, 9, 2)
        let dateBox = DisplayDateBox(firstDate)
        let gate = DisplayFirstPageGate()
        let viewModel = DisplayViewModel(
            dateProvider: { dateBox.value },
            firstPageHandler: { date, _ in await gate.request(date: date) }
        )

        let oldLoad = Task { await viewModel.load() }
        await gate.waitForRequestCount(1)
        dateBox.value = secondDate
        let latestLoad = Task { await viewModel.load() }
        await gate.waitForRequestCount(2)
        gate.completeRequest(at: 1, with: .success(Self.content(date: secondDate, topic: "새 전시")))
        await latestLoad.value
        gate.completeRequest(at: 0, with: .success(Self.content(date: firstDate, topic: "오래된 전시")))
        await oldLoad.value

        #expect(viewModel.viewState.selectedDate == secondDate)
        #expect(viewModel.viewState.topic == "새 전시")
    }

    private static func content(
        date: Date,
        topic: String = "주제",
        page: DisplayPage? = nil
    ) -> DisplayContent {
        DisplayContent(
            topicDate: date,
            topic: topic,
            page: page ?? DisplayPage(
                photos: [photo(id: "photo-1")],
                currentPage: 1,
                hasNext: false,
                randomSeed: nil
            )
        )
    }

    private static func photo(id: String) -> DisplayPhoto {
        DisplayPhoto(
            id: id,
            originalImageSource: .remote(URL(string: "https://example.com/\(id).webp")),
            thumbnailImageSource: .remote(URL(string: "https://example.com/\(id)-thumb.webp")),
            signatureOriginalImageSource: .remote(URL(string: "https://example.com/signature.png")),
            signatureThumbnailImageSource: .remote(URL(string: "https://example.com/signature-thumb.png")),
            contentDescription: "작품 이미지",
            title: "작품",
            likeCount: 7
        )
    }

    private static func date(_ year: Int, _ month: Int, _ day: Int) -> Date {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Asia/Seoul")!
        return calendar.date(from: DateComponents(year: year, month: month, day: day))!
    }
}

@MainActor
@Suite(.serialized)
struct DisplayAPIClientTests {
    @Test("topics의 canonical 날짜로 posts를 pageSize 20과 요청하고 이미지 4종 및 likeCount를 매핑한다")
    func fetchesCanonicalTopicAndMapsDisplayFields() async throws {
        let recorder = DisplayURLRequestRecorder()
        let client = Self.makeClient { request in
            await recorder.append(request)
            if request.url?.path == "/api/v1/topics" {
                return Self.response(
                    for: request,
                    body: #"{"title":"반짝임","topicDate":"2026-09-01"}"#
                )
            }
            return Self.response(
                for: request,
                body: #"{"currentPage":1,"pageSize":20,"hasNext":false,"randomSeed":null,"posts":[{"id":"post-1","originalImageUrl":"https://example.com/original.webp","thumbnailImageUrl":"https://example.com/thumb.webp","signatureOriginalImageUrl":"https://example.com/signature.png","signatureThumbnailImageUrl":"https://example.com/signature-thumb.png","title":"빛","likeCount":9,"isLiked":true}]}"#
            )
        }

        let content = try await client.fetchDisplay(
            date: Self.date(2026, 9, 2),
            sort: .latest
        )

        #expect(content.topic == "반짝임")
        #expect(content.page.photos.first?.likeCount == 9)
        #expect(content.page.photos.first?.thumbnailImageSource == .remote(URL(string: "https://example.com/thumb.webp")))
        let requests = await recorder.requests
        #expect(requests.count == 2)
        #expect(requests[0].url?.query?.contains("date=2026-09-02") == true)
        #expect(requests[1].url?.query?.contains("topicDate=2026-09-01") == true)
        #expect(requests[1].url?.query?.contains("sort=recent") == true)
        #expect(requests[1].url?.query?.contains("pageSize=20") == true)
        #expect(requests.allSatisfy { $0.value(forHTTPHeaderField: "Authorization") == nil })
    }

    @Test("random 다음 페이지는 seed를 전달한다")
    func sendsRandomSeedOnNextPage() async throws {
        let recorder = DisplayURLRequestRecorder()
        let client = Self.makeClient { request in
            await recorder.append(request)
            return Self.response(
                for: request,
                body: #"{"currentPage":2,"hasNext":false,"randomSeed":"seed-1","posts":[]}"#
            )
        }

        _ = try await client.fetchPage(
            DisplayPageRequest(
                topicDate: Self.date(2026, 9, 1),
                sort: .random,
                page: 2,
                randomSeed: "seed-1"
            )
        )

        let request = try #require(await recorder.requests.first)
        #expect(request.url?.query?.contains("page=2") == true)
        #expect(request.url?.query?.contains("sort=random") == true)
        #expect(request.url?.query?.contains("randomSeed=seed-1") == true)
    }

    @Test("404와 seed 없는 random 연속 페이지를 구분한다")
    func mapsNotFoundAndRejectsMissingRandomSeed() async {
        let notFoundClient = Self.makeClient { request in
            Self.response(for: request, statusCode: 404, body: "{}")
        }
        do {
            _ = try await notFoundClient.fetchDisplay(date: Self.date(2026, 9, 1), sort: .latest)
            Issue.record("404 요청이 성공하면 안 됩니다")
        } catch let error as DisplayAPIError {
            #expect(error.displayError == .topicNotFound)
        } catch {
            Issue.record("예상하지 못한 오류: \(error)")
        }

        let missingSeedClient = Self.makeClient { request in
            Self.response(
                for: request,
                body: #"{"currentPage":1,"hasNext":true,"randomSeed":null,"posts":[]}"#
            )
        }
        do {
            _ = try await missingSeedClient.fetchPage(
                DisplayPageRequest(
                    topicDate: Self.date(2026, 9, 1),
                    sort: .random,
                    page: 1,
                    randomSeed: nil
                )
            )
            Issue.record("seed 없는 random 연속 페이지가 성공하면 안 됩니다")
        } catch let error as DisplayAPIError {
            #expect(error == .invalidResponse)
        } catch {
            Issue.record("예상하지 못한 오류: \(error)")
        }
    }

    private static func makeClient(
        handler: @escaping DisplayMockURLProtocol.Handler
    ) -> DisplayAPIClient {
        let configuration = URLSessionConfiguration.ephemeral
        configuration.protocolClasses = [DisplayMockURLProtocol.self]
        DisplayMockURLProtocol.handler = handler
        return DisplayAPIClient(
            configuration: DisplayAPIConfiguration(
                baseURL: URL(string: "https://example.com/api/v1/")!
            ),
            session: URLSession(configuration: configuration)
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

    private static func date(_ year: Int, _ month: Int, _ day: Int) -> Date {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Asia/Seoul")!
        return calendar.date(from: DateComponents(year: year, month: month, day: day))!
    }
}

@MainActor
private final class DisplayRequestRecorder {
    var firstPages: [(Date, DisplaySort)] = []
    var nextPages: [(Date, DisplaySort, Int, String?)] = []
}

@MainActor
private final class DisplayDateBox {
    var value: Date

    init(_ value: Date) {
        self.value = value
    }
}

@MainActor
private final class DisplayFirstPageGate {
    private var requests: [Date] = []
    private var continuations: [CheckedContinuation<Result<DisplayContent, DisplayError>, Never>] = []
    private var waiters: [(Int, CheckedContinuation<Void, Never>)] = []

    func request(date: Date) async -> Result<DisplayContent, DisplayError> {
        requests.append(date)
        resumeSatisfiedWaiters()
        return await withCheckedContinuation { continuation in
            continuations.append(continuation)
        }
    }

    func waitForRequestCount(_ count: Int) async {
        guard requests.count < count else { return }
        await withCheckedContinuation { continuation in
            waiters.append((count, continuation))
        }
    }

    func completeRequest(
        at index: Int,
        with result: Result<DisplayContent, DisplayError>
    ) {
        continuations[index].resume(returning: result)
    }

    private func resumeSatisfiedWaiters() {
        let satisfied = waiters.filter { requests.count >= $0.0 }
        waiters.removeAll { requests.count >= $0.0 }
        satisfied.forEach { $0.1.resume() }
    }
}

private actor DisplayURLRequestRecorder {
    private(set) var requests: [URLRequest] = []

    func append(_ request: URLRequest) {
        requests.append(request)
    }
}

private final class DisplayMockURLProtocol: URLProtocol, @unchecked Sendable {
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
