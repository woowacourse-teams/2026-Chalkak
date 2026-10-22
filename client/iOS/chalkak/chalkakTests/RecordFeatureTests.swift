import Foundation
import Testing
@testable import chalkak

@MainActor
@Suite(.serialized)
struct RecordViewModelTests {
    @Test("탭 재검증은 기존 기록을 유지하고 성공한 최신 값으로 교체한다")
    func revalidationKeepsCachedContentUntilUpdated() async {
        let month = RecordMonth(year: 2026, month: 9)
        let cachedPost = Self.post(id: "cached-post", day: 1, month: month)
        let updatedPost = Self.post(id: "updated-post", day: 2, month: month)
        let gate = RecordCalendarGate()
        let viewModel = RecordViewModel(
            initialState: RecordViewState(
                contentStatus: .loaded,
                month: month,
                latestMonth: month,
                posts: [cachedPost],
                selectedDate: cachedPost.topicDate
            ),
            monthProvider: { month },
            calendarHandler: { month in await gate.request(month: month) }
        )

        let revalidation = Task { await viewModel.revalidate() }
        await gate.waitForRequest()

        #expect(viewModel.viewState.contentStatus == .loaded)
        #expect(viewModel.viewState.posts == [cachedPost])
        #expect(viewModel.viewState.selectedDate == cachedPost.topicDate)

        gate.complete(with: .success(RecordCalendar(month: month, posts: [updatedPost])))
        await revalidation.value

        #expect(viewModel.viewState.posts == [updatedPost])
        #expect(viewModel.viewState.selectedDate == updatedPost.topicDate)
    }

    @Test("탭 재검증 실패는 기존 기록을 유지한다")
    func failedRevalidationKeepsCachedContent() async {
        let month = RecordMonth(year: 2026, month: 9)
        let cachedPost = Self.post(id: "cached-post", day: 1, month: month)
        let viewModel = RecordViewModel(
            initialState: RecordViewState(
                contentStatus: .loaded,
                month: month,
                latestMonth: month,
                posts: [cachedPost],
                selectedDate: cachedPost.topicDate
            ),
            monthProvider: { month },
            calendarHandler: { _ in .failure(.network) }
        )

        await viewModel.revalidate()

        #expect(viewModel.viewState.contentStatus == .loaded)
        #expect(viewModel.viewState.posts == [cachedPost])
        #expect(viewModel.event == .showToast(RecordError.network.message))
    }

    @Test("현재 달은 월 목록 응답에 없어도 활성 연월에 포함한다")
    func includesCurrentMonthWhenMonthListOmitsIt() async {
        let currentMonth = RecordMonth(year: 2026, month: 9)
        let previousMonth = currentMonth.adding(months: -2)
        let viewModel = RecordViewModel(
            monthProvider: { currentMonth },
            monthListHandler: { .success([previousMonth]) },
            calendarHandler: { month in .success(RecordCalendar(month: month, posts: [])) }
        )

        await viewModel.load()

        #expect(viewModel.viewState.availableMonths == [currentMonth, previousMonth])
        #expect(viewModel.viewState.canGoPrevious)

        await viewModel.moveToPreviousMonth()
        #expect(viewModel.viewState.month == previousMonth)
        #expect(viewModel.viewState.canGoNext)

        await viewModel.moveToNextMonth()
        #expect(viewModel.viewState.month == currentMonth)
    }

    @Test("현재 달의 마지막 게시물을 지워도 현재 달은 활성 연월에 남는다")
    func deletingLastPostKeepsCurrentMonthActive() {
        let currentMonth = RecordMonth(year: 2026, month: 9)
        let currentPost = Self.post(id: "current-post", day: 1, month: currentMonth)
        let viewModel = RecordViewModel(
            initialState: RecordViewState(
                contentStatus: .loaded,
                month: currentMonth,
                latestMonth: currentMonth,
                availableMonths: [currentMonth],
                posts: [currentPost],
                selectedDate: currentPost.topicDate
            ),
            monthProvider: { currentMonth }
        )

        viewModel.removeDeletedPost(currentPost.postId)

        #expect(viewModel.viewState.availableMonths == [currentMonth])
        #expect(viewModel.viewState.posts.isEmpty)
    }

    @Test("홈에서 선택한 이전 월 날짜를 최신 사진보다 우선 선택하고 재검증에도 유지한다")
    func weeklyRecordOpensRequestedMonthAndDate() async {
        let current = RecordMonth(year: 2026, month: 10)
        let previous = current.adding(months: -1)
        let selected = Self.post(id: "selected", day: 29, month: previous)
        let latest = Self.post(id: "latest", day: 30, month: previous)
        let model = RecordViewModel(
            initialDate: selected.topicDate.addingTimeInterval(3600),
            monthProvider: { current },
            monthListHandler: { .success([previous]) },
            calendarHandler: { month in
                #expect(month == previous)
                return .success(RecordCalendar(month: month, posts: [selected, latest]))
            }
        )
        await model.load()
        #expect(model.viewState.month == previous)
        #expect(model.viewState.selectedPost == selected)
        await model.revalidate()
        #expect(model.viewState.selectedPost == selected)
    }

    @Test("주간 기록의 선택 날짜가 삭제됐다면 다른 날짜의 사진을 대신 열지 않는다")
    func missingRequestedDateDoesNotOpenAnotherPhoto() async {
        let month = RecordMonth(year: 2026, month: 10)
        let other = Self.post(id: "other", day: 2, month: month)
        let model = RecordViewModel(
            initialDate: month.date(day: 1),
            monthProvider: { month },
            calendarHandler: { .success(RecordCalendar(month: $0, posts: [other])) }
        )
        await model.load()
        #expect(model.viewState.selectedPost == nil)
    }

    @Test("주간 기록 조회 실패 후 재시도에도 요청 날짜를 선택한다")
    func retryKeepsRequestedDate() async {
        let month = RecordMonth(year: 2026, month: 10)
        let selected = Self.post(id: "selected", day: 1, month: month)
        var shouldFail = true
        let model = RecordViewModel(
            initialDate: selected.topicDate,
            monthProvider: { month },
            calendarHandler: { month in
                if shouldFail { return .failure(.network) }
                return .success(RecordCalendar(month: month, posts: [selected]))
            }
        )
        await model.load()
        shouldFail = false
        await model.retry()
        #expect(model.viewState.selectedPost == selected)
    }

    private static func post(id: String, day: Int, month: RecordMonth) -> RecordPost {
        RecordPost(
            postId: id,
            topicDate: month.date(day: day),
            thumbnailImageSource: .remote(URL(string: "https://example.com/\(id).webp")),
            status: .approved
        )
    }
}

@MainActor
private final class RecordCalendarGate {
    private var continuation: CheckedContinuation<Result<RecordCalendar, RecordError>, Never>?
    private var waiter: CheckedContinuation<Void, Never>?

    func request(month: RecordMonth) async -> Result<RecordCalendar, RecordError> {
        waiter?.resume()
        waiter = nil
        return await withCheckedContinuation { continuation in
            self.continuation = continuation
        }
    }

    func waitForRequest() async {
        guard continuation == nil else { return }
        await withCheckedContinuation { continuation in
            waiter = continuation
        }
    }

    func complete(with result: Result<RecordCalendar, RecordError>) {
        continuation?.resume(returning: result)
        continuation = nil
    }
}
