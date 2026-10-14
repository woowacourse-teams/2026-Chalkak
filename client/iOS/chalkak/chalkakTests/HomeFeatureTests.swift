import Foundation
import Testing
@testable import chalkak

@MainActor
struct HomeViewStateTests {
    @Test("주는 일요일부터 토요일까지 계산하며 연말 경계를 넘는다")
    func weekDatesCrossYearBoundaryFromSunday() {
        let dates = HomeViewState.weekDates(Self.date(year: 2027, month: 1, day: 1))

        #expect(dates.map(Self.dayKey) == [
            "2026-12-27",
            "2026-12-28",
            "2026-12-29",
            "2026-12-30",
            "2026-12-31",
            "2027-01-01",
            "2027-01-02"
        ])
        #expect(dates.map(\.homeWeekdayText) == ["일", "월", "화", "수", "목", "금", "토"])
    }

    @Test("서울 자정의 요일은 UTC 전날이 아닌 주제 날짜와 일치한다")
    func weekdayLabelUsesKSTAtMidnight() {
        let friday = Self.date(year: 2027, month: 1, day: 1)
        var utcCalendar = Calendar(identifier: .gregorian)
        utcCalendar.timeZone = TimeZone(secondsFromGMT: 0)!

        #expect(utcCalendar.component(.weekday, from: friday) == 5)
        #expect(friday.homeWeekdayText == "금")
        #expect(friday.homeFullDateText == "2027년 1월 1일")
        #expect(friday.addingTimeInterval(-1).homeWeekdayText == "목")
    }

    @Test("Asia Seoul 기준 날짜 변경은 홈 상태 날짜를 바꾼다")
    func usesKSTStartOfDayForStateDate() {
        let beforeMidnightKST = RecordMonth.calendar.date(
            from: DateComponents(
                year: 2026,
                month: 12,
                day: 16,
                hour: 23,
                minute: 59,
                second: 59
            )
        )!
        let afterMidnightKST = RecordMonth.calendar.date(byAdding: .second, value: 1, to: beforeMidnightKST)!

        #expect(Self.dayKey(HomeViewState(date: beforeMidnightKST).date) == "2026-12-16")
        #expect(Self.dayKey(HomeViewState(date: afterMidnightKST).date) == "2026-12-17")
    }

    private static func date(year: Int, month: Int, day: Int) -> Date {
        RecordMonth.calendar.date(from: DateComponents(year: year, month: month, day: day))!
    }

    private static func dayKey(_ date: Date) -> String {
        let components = RecordMonth.calendar.dateComponents([.year, .month, .day], from: date)
        return String(format: "%04d-%02d-%02d", components.year!, components.month!, components.day!)
    }
}

@MainActor
struct HomeViewModelTests {
    @Test("인기 섹션은 서버 순서를 유지하며 상위 5개만 노출한다")
    func keepsServerOrderAndLimitsPopularSections() async {
        let today = Self.date(year: 2026, month: 9, day: 7)
        let loader = HomeLoaderProbe(
            todayResult: .success(Self.displayContent(date: today, photoIDs: ["t1", "t2", "t3", "t4", "t5", "t6"])),
            rankingResult: .success(["y1", "y2", "y3", "y4", "y5", "y6"].map(Self.displayPhoto))
        )
        let viewModel = Self.viewModel(date: today, loader: loader.loader)

        await viewModel.resume()

        #expect(viewModel.viewState.trending.status == .ready)
        #expect(viewModel.viewState.trending.photos.map(\.id) == ["t1", "t2", "t3", "t4", "t5"])
        #expect(viewModel.viewState.yesterday.status == .ready)
        #expect(viewModel.viewState.yesterday.photos.map(\.id) == ["y1", "y2", "y3", "y4", "y5"])
    }

    @Test("비로그인 사용자는 개인 기록 API를 호출하지 않는다")
    func guestDoesNotCallPrivateAPIs() async {
        let today = Self.date(year: 2026, month: 9, day: 7)
        let loader = HomeLoaderProbe(
            todayResult: .success(Self.displayContent(date: today)),
            rankingResult: .success([])
        )
        let viewModel = Self.viewModel(date: today, isAuthenticated: false, loader: loader.loader)

        await viewModel.resume()

        #expect(viewModel.viewState.isAuthenticated == false)
        #expect(viewModel.viewState.hero == .empty)
        #expect(viewModel.viewState.recordsStatus == .ready)
        #expect(loader.calendarRequests.isEmpty)
        #expect(loader.detailRequests.isEmpty)
        #expect(loader.postStatusCallCount == 0)
    }

    @Test("PENDING 기록은 상세 API 없이 썸네일과 대기 상태로 표시한다")
    func pendingRecordSkipsDetail() async {
        let today = Self.date(year: 2026, month: 9, day: 7)
        let record = Self.record(id: "pending-post", date: today, status: .pending)
        let loader = HomeLoaderProbe(
            todayResult: .success(Self.displayContent(date: today)),
            rankingResult: .success([]),
            calendars: [RecordMonth.current(now: today): RecordCalendar(month: RecordMonth.current(now: today), posts: [record])]
        )
        let viewModel = Self.viewModel(date: today, loader: loader.loader)

        await viewModel.resume()

        guard case let .photo(photo) = viewModel.viewState.hero else {
            Issue.record("PENDING 기록이 사진 히어로로 표시되지 않았습니다")
            return
        }
        #expect(photo.isPending)
        #expect(photo.imageSource == record.thumbnailImageSource)
        #expect(photo.signatureSource == nil)
        #expect(loader.detailRequests.isEmpty)
    }

    @Test("APPROVED 상세 조회 실패는 달력 썸네일로 폴백한다")
    func approvedDetailFailureFallsBackToCalendarThumbnail() async {
        let today = Self.date(year: 2026, month: 9, day: 7)
        let record = Self.record(id: "approved-post", date: today, status: .approved)
        let loader = HomeLoaderProbe(
            todayResult: .success(Self.displayContent(date: today)),
            rankingResult: .success([]),
            calendars: [RecordMonth.current(now: today): RecordCalendar(month: RecordMonth.current(now: today), posts: [record])],
            detailResult: .failure(TestError.expected)
        )
        let viewModel = Self.viewModel(date: today, loader: loader.loader)

        await viewModel.resume()

        guard case let .photo(photo) = viewModel.viewState.hero else {
            Issue.record("APPROVED 기록이 사진 히어로로 표시되지 않았습니다")
            return
        }
        #expect(photo.isPending == false)
        #expect(photo.imageSource == record.thumbnailImageSource)
        #expect(photo.signatureSource == nil)
        #expect(loader.detailRequests == ["approved-post"])
    }

    @Test("달력에 없지만 오늘 업로드가 VALIDATING이면 처리 중으로 표시한다")
    func validatingTodayPostStatusShowsProcessing() async {
        let today = Self.date(year: 2026, month: 9, day: 7)
        let loader = HomeLoaderProbe(
            todayResult: .success(Self.displayContent(date: today)),
            rankingResult: .success([]),
            calendars: [RecordMonth.current(now: today): RecordCalendar(month: RecordMonth.current(now: today), posts: [])],
            postStatusResult: .success(
                PhotoUploadTodayPostStatus(
                    topicDate: today,
                    isPosted: true,
                    postID: "validating-post",
                    moderationStatus: .validating
                )
            )
        )
        let viewModel = Self.viewModel(date: today, loader: loader.loader)

        await viewModel.resume()

        #expect(viewModel.viewState.hero == .processing)
        #expect(loader.postStatusCallCount == 1)
    }

    @Test("오늘 주제가 없다는 404는 참여 불가 상태로 표시하고 일반 오류와 구분한다")
    func postStatus404ShowsNoOpenTopic() async {
        let today = Self.date(year: 2026, month: 9, day: 7)
        let loader = HomeLoaderProbe(
            todayResult: .success(Self.displayContent(date: today)),
            rankingResult: .success([]),
            calendars: [RecordMonth.current(now: today): RecordCalendar(month: RecordMonth.current(now: today), posts: [])],
            postStatusResult: .failure(PhotoUploadAPIError.http(statusCode: 404, message: nil))
        )
        let viewModel = Self.viewModel(date: today, loader: loader.loader)

        await viewModel.resume()

        #expect(viewModel.viewState.hero == .noOpenTopic)
    }

    @Test("오늘 주제 조회 404는 주제와 오늘 인기 섹션을 빈 ready 상태로 둔다")
    func todayTopic404KeepsPublicSectionsReady() async {
        let today = Self.date(year: 2026, month: 9, day: 7)
        let loader = HomeLoaderProbe(
            todayResult: .failure(DisplayAPIError.http(404)),
            rankingResult: .success([])
        )
        let viewModel = Self.viewModel(date: today, isAuthenticated: false, loader: loader.loader)

        await viewModel.resume()

        #expect(viewModel.viewState.topic == "")
        #expect(viewModel.viewState.topicStatus == .ready)
        #expect(viewModel.viewState.trending.status == .ready)
        #expect(viewModel.viewState.trending.photos.isEmpty)
    }

    @Test("랭킹 실패는 다른 섹션 로딩 결과를 유지한다")
    func rankingFailureDoesNotClearOtherSections() async {
        let today = Self.date(year: 2026, month: 9, day: 7)
        let loader = HomeLoaderProbe(
            todayResult: .success(Self.displayContent(date: today, photoIDs: ["today-popular"])),
            rankingResult: .failure(TestError.expected)
        )
        let viewModel = Self.viewModel(date: today, isAuthenticated: false, loader: loader.loader)

        await viewModel.resume()

        #expect(viewModel.viewState.topic == "오늘의 주제")
        #expect(viewModel.viewState.topicStatus == .ready)
        #expect(viewModel.viewState.trending.photos.map(\.id) == ["today-popular"])
        #expect(viewModel.viewState.yesterday.status == .error)
    }

    @Test("취소된 로딩 결과는 현재 상태를 덮어쓰지 않는다")
    func cancelledLoadDoesNotApplyStaleResult() async {
        let today = Self.date(year: 2026, month: 9, day: 7)
        let gate = SuspendedTodayResult()
        let loader = HomeLoaderProbe(
            todayHandler: { _ in try await gate.load() },
            rankingResult: .success([])
        )
        let initialState = HomeViewState(date: today)
        let viewModel = Self.viewModel(date: today, initialState: initialState, isAuthenticated: false, loader: loader.loader)

        let loadTask = Task { await viewModel.resume() }
        await gate.waitUntilStarted()
        viewModel.cancel()
        await gate.complete(with: .success(Self.displayContent(date: today, topic: "늦은 주제", photoIDs: ["late"])))
        await loadTask.value

        #expect(viewModel.viewState.topic == "")
        #expect(viewModel.viewState.trending.photos.isEmpty)
        #expect(viewModel.viewState.isRefreshing == false)
    }

    private static func viewModel(
        date: Date,
        initialState: HomeViewState? = nil,
        isAuthenticated: Bool = true,
        loader: HomeDataLoader
    ) -> HomeViewModel {
        HomeViewModel(
            initialState: initialState ?? HomeViewState(),
            loader: loader,
            dateProvider: { date },
            isAuthenticated: { isAuthenticated }
        )
    }

    private static func date(year: Int, month: Int, day: Int) -> Date {
        RecordMonth.calendar.date(from: DateComponents(year: year, month: month, day: day))!
    }

    fileprivate static func displayContent(
        date: Date,
        topic: String = "오늘의 주제",
        photoIDs: [String] = []
    ) -> DisplayContent {
        DisplayContent(
            topicDate: date,
            topic: topic,
            page: DisplayPage(
                photos: photoIDs.map(displayPhoto),
                currentPage: 1,
                hasNext: false,
                randomSeed: nil
            )
        )
    }

    private static func displayPhoto(id: String) -> DisplayPhoto {
        DisplayPhoto(
            id: id,
            originalImageSource: .remote(URL(string: "https://example.com/\(id).webp")!),
            thumbnailImageSource: .remote(URL(string: "https://example.com/\(id)-thumb.webp")!),
            signatureOriginalImageSource: .remote(URL(string: "https://example.com/\(id)-signature.png")!),
            signatureThumbnailImageSource: .remote(URL(string: "https://example.com/\(id)-signature-thumb.png")!),
            contentDescription: "테스트 사진 \(id)",
            title: id,
            likeCount: 1,
            isLiked: false,
            isOwnedByCurrentUser: false
        )
    }

    private static func record(id: String, date: Date, status: RecordPostStatus) -> RecordPost {
        RecordPost(
            postId: id,
            topicDate: date,
            thumbnailImageSource: .remote(URL(string: "https://example.com/\(id)-thumb.webp")!),
            status: status
        )
    }
}

@MainActor
struct DisplayViewModelHomeRoutingTests {
    @Test("홈에서 인기 더보기로 진입하면 첫 페이지를 popular 정렬로 요청한다")
    func initialPopularSortIsUsedForFirstPageLoad() async {
        let today = Self.date(year: 2026, month: 9, day: 7)
        let recorder = DisplayFirstPageRecorder(content: HomeViewModelTests.displayContent(date: today))
        let viewModel = DisplayViewModel(
            initialDate: today,
            initialSort: .popular,
            dateProvider: { today },
            firstPageHandler: { date, sort, _ in
                await recorder.load(date: date, sort: sort)
            }
        )

        await viewModel.load()

        #expect(await recorder.requests == [
            DisplayFirstPageRequest(date: today, sort: "popular")
        ])
        #expect(Self.sortKey(viewModel.viewState.selectedSort) == "popular")
    }

    private static func date(year: Int, month: Int, day: Int) -> Date {
        RecordMonth.calendar.date(from: DateComponents(year: year, month: month, day: day))!
    }

    nonisolated fileprivate static func sortKey(_ sort: DisplaySort) -> String {
        switch sort {
        case .latest:
            "latest"
        case .popular:
            "popular"
        case .random:
            "random"
        }
    }
}

private enum TestError: Error {
    case expected
}

private struct DisplayFirstPageRequest: Equatable, Sendable {
    let date: Date
    let sort: String
}

private actor DisplayFirstPageRecorder {
    private(set) var requests: [DisplayFirstPageRequest] = []
    private let content: DisplayContent

    init(content: DisplayContent) {
        self.content = content
    }

    func load(date: Date, sort: DisplaySort) -> Result<DisplayContent, DisplayError> {
        requests.append(DisplayFirstPageRequest(date: date, sort: DisplayViewModelHomeRoutingTests.sortKey(sort)))
        return .success(content)
    }
}

@MainActor
private final class HomeLoaderProbe {
    private var todayHandler: @Sendable (Date) async throws -> DisplayContent
    private var rankingResult: Result<[DisplayPhoto], Error>
    private var calendars: [RecordMonth: RecordCalendar]
    private var detailResult: Result<FeedContent, Error>
    private var postStatusResult: Result<PhotoUploadTodayPostStatus, Error>
    private(set) var calendarRequests: [RecordMonth] = []
    private(set) var detailRequests: [String] = []
    private(set) var postStatusCallCount = 0

    init(
        todayHandler: @escaping @Sendable (Date) async throws -> DisplayContent,
        rankingResult: Result<[DisplayPhoto], Error>,
        calendars: [RecordMonth: RecordCalendar]? = nil,
        detailResult: Result<FeedContent, Error> = .failure(TestError.expected),
        postStatusResult: Result<PhotoUploadTodayPostStatus, Error> = .failure(TestError.expected)
    ) {
        self.todayHandler = todayHandler
        self.rankingResult = rankingResult
        self.calendars = calendars ?? [:]
        self.detailResult = detailResult
        self.postStatusResult = postStatusResult
    }

    convenience init(
        todayResult: Result<DisplayContent, Error>,
        rankingResult: Result<[DisplayPhoto], Error>,
        calendars: [RecordMonth: RecordCalendar]? = nil,
        detailResult: Result<FeedContent, Error> = .failure(TestError.expected),
        postStatusResult: Result<PhotoUploadTodayPostStatus, Error> = .failure(TestError.expected)
    ) {
        self.init(
            todayHandler: { _ in try todayResult.get() },
            rankingResult: rankingResult,
            calendars: calendars,
            detailResult: detailResult,
            postStatusResult: postStatusResult
        )
    }

    nonisolated var loader: HomeDataLoader {
        HomeDataLoader(
            today: { date in try await self.loadToday(date) },
            ranking: { _ in try await self.loadRanking() },
            calendar: { month in
                try await self.loadCalendar(month)
            },
            detail: { postID in
                try await self.loadDetail(postID)
            },
            postStatus: {
                try await self.loadPostStatus()
            }
        )
    }

    private func loadToday(_ date: Date) async throws -> DisplayContent {
        try await todayHandler(date)
    }

    private func loadRanking() throws -> [DisplayPhoto] {
        try rankingResult.get()
    }

    private func loadCalendar(_ month: RecordMonth) throws -> RecordCalendar {
        calendarRequests.append(month)
        guard let calendar = calendars[month] else { throw TestError.expected }
        return calendar
    }

    private func loadDetail(_ postID: String) throws -> FeedContent {
        detailRequests.append(postID)
        return try detailResult.get()
    }

    private func loadPostStatus() throws -> PhotoUploadTodayPostStatus {
        postStatusCallCount += 1
        return try postStatusResult.get()
    }
}

private actor SuspendedTodayResult {
    private var didStart = false
    private var startWaiters: [CheckedContinuation<Void, Never>] = []
    private var resultContinuation: CheckedContinuation<DisplayContent, Error>?

    func waitUntilStarted() async {
        guard !didStart else { return }
        await withCheckedContinuation { continuation in
            startWaiters.append(continuation)
        }
    }

    func load() async throws -> DisplayContent {
        didStart = true
        startWaiters.forEach { $0.resume() }
        startWaiters.removeAll()
        return try await withCheckedThrowingContinuation { continuation in
            resultContinuation = continuation
        }
    }

    func complete(with result: Result<DisplayContent, Error>) {
        resultContinuation?.resume(with: result)
        resultContinuation = nil
    }
}
