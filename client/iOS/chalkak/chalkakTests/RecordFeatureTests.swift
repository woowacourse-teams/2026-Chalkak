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

    @Test("고른 달로 이동하고, 기록 없는 달이나 지금 달은 다시 불러오지 않는다")
    func movesToPickedMonthOnlyWhenAvailable() async {
        let currentMonth = RecordMonth(year: 2026, month: 10)
        let recordedMonth = RecordMonth(year: 2026, month: 9)
        var requests: [RecordMonth] = []
        let viewModel = RecordViewModel(
            monthProvider: { currentMonth },
            monthListHandler: { .success([recordedMonth]) },
            calendarHandler: { month in
                requests.append(month)
                return .success(RecordCalendar(month: month, posts: []))
            }
        )
        await viewModel.load()
        #expect(viewModel.viewState.canPickMonth)

        await viewModel.moveToMonth(recordedMonth)
        #expect(viewModel.viewState.month == recordedMonth)

        await viewModel.moveToMonth(RecordMonth(year: 2026, month: 3))
        await viewModel.moveToMonth(recordedMonth)
        #expect(viewModel.viewState.month == recordedMonth)
        #expect(requests == [currentMonth, recordedMonth])
    }

    @Test("연도를 바꾸면 그 해의 기록 달 중 가장 가까운 달을 고른다")
    func closestMonthInYear() {
        let months = [
            RecordMonth(year: 2026, month: 9),
            RecordMonth(year: 2026, month: 10),
            RecordMonth(year: 2027, month: 1),
            RecordMonth(year: 2027, month: 4),
        ]

        #expect(RecordMonthPickerSheet.closestMonth(in: months, year: 2026, preferredMonth: 3) == RecordMonth(year: 2026, month: 9))
        #expect(RecordMonthPickerSheet.closestMonth(in: months, year: 2027, preferredMonth: 10) == RecordMonth(year: 2027, month: 4))
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

    @Test("화면을 벗어나 첫 조회가 취소되면 오류 대신 로딩으로 남고 다시 나타날 때 불러온다")
    func cancelledLoadStaysLoadingAndReloads() async {
        let month = RecordMonth(year: 2026, month: 10)
        let post = Self.post(id: "post", day: 1, month: month)
        let gate = RecordCalendarGate()
        let viewModel = RecordViewModel(
            monthProvider: { month },
            calendarHandler: { month in await gate.request(month: month) }
        )

        let loading = Task { await viewModel.load() }
        await gate.waitForRequest()
        loading.cancel()
        // 실제 API 클라이언트는 취소되면 CancellationError를 일반 실패로 돌려준다.
        gate.complete(with: .failure(.generic))
        await loading.value

        #expect(viewModel.viewState.contentStatus == .loading)
        #expect(viewModel.viewState.errorMessage == nil)

        let reloading = Task { await viewModel.load() }
        await gate.waitForRequest()
        gate.complete(with: .success(RecordCalendar(month: month, posts: [post])))
        await reloading.value

        #expect(viewModel.viewState.contentStatus == .loaded)
        #expect(viewModel.viewState.posts == [post])
    }

    @Test("기록을 본 뒤 주간 기록에서 열면 같은 뷰모델로 그 달과 날짜를 연다")
    func openingDateAfterLoadMovesToRequestedMonthAndDate() async {
        let current = RecordMonth(year: 2026, month: 10)
        let previous = current.adding(months: -1)
        let currentPost = Self.post(id: "current", day: 1, month: current)
        let selected = Self.post(id: "selected", day: 29, month: previous)
        let latest = Self.post(id: "latest", day: 30, month: previous)
        let model = RecordViewModel(
            monthProvider: { current },
            monthListHandler: { .success([previous]) },
            calendarHandler: { month in
                .success(RecordCalendar(month: month, posts: month == current ? [currentPost] : [selected, latest]))
            }
        )
        await model.load()
        #expect(model.viewState.selectedPost == currentPost)

        await model.open(date: selected.topicDate.addingTimeInterval(3600))

        #expect(model.viewState.month == previous)
        #expect(model.viewState.selectedPost == selected)
    }

    @Test("본 적 있는 달을 주간 기록에서 열면 응답 전에도 캐시한 기록에서 고른 날짜를 보여준다")
    func openingCachedMonthShowsRequestedDateUntilUpdated() async {
        let currentMonth = RecordMonth(year: 2026, month: 9)
        let previousMonth = currentMonth.adding(months: -1)
        let requested = Self.post(id: "requested", day: 1, month: currentMonth)
        let latest = Self.post(id: "latest", day: 2, month: currentMonth)
        let gate = RecordCalendarGate()
        let viewModel = await Self.viewModelVisitedPreviousMonth(
            currentMonth: currentMonth,
            currentPosts: [requested, latest],
            previousPosts: [Self.post(id: "previous-post", day: 1, month: previousMonth)],
            gate: gate
        )

        let opening = Task { await viewModel.open(date: requested.topicDate) }
        await gate.waitForRequest()

        #expect(viewModel.viewState.contentStatus == .loaded)
        #expect(viewModel.viewState.month == currentMonth)
        #expect(viewModel.viewState.selectedPost == requested)

        gate.complete(with: .success(RecordCalendar(month: currentMonth, posts: [requested, latest])))
        await opening.value

        #expect(viewModel.viewState.selectedPost == requested)
    }

    @Test("주간 기록에서 열어 불러오는 중이면 화면이 나타나도 같은 요청을 다시 보내지 않는다")
    func appearingWhileOpeningDoesNotLoadAgain() async {
        let month = RecordMonth(year: 2026, month: 10)
        let post = Self.post(id: "post", day: 1, month: month)
        let gate = RecordCalendarGate()
        var monthListRequestCount = 0
        let viewModel = RecordViewModel(
            monthProvider: { month },
            monthListHandler: {
                monthListRequestCount += 1
                return .success([month])
            },
            calendarHandler: { month in await gate.request(month: month) }
        )

        let opening = Task { await viewModel.open(date: post.topicDate) }
        await gate.waitForRequest()
        await viewModel.loadIfNeeded()
        gate.complete(with: .success(RecordCalendar(month: month, posts: [post])))
        await opening.value

        #expect(monthListRequestCount == 1)
        #expect(viewModel.viewState.selectedPost == post)
    }

    @Test("본 적 있는 달로 돌아오면 응답 전에도 캐시한 기록을 보여주고 최신 값으로 교체한다")
    func returningToCachedMonthShowsCachedContentUntilUpdated() async {
        let currentMonth = RecordMonth(year: 2026, month: 9)
        let previousMonth = currentMonth.adding(months: -1)
        let cachedPost = Self.post(id: "cached-post", day: 1, month: currentMonth)
        let newPost = Self.post(id: "new-post", day: 2, month: currentMonth)
        let gate = RecordCalendarGate()
        let viewModel = await Self.viewModelVisitedPreviousMonth(
            currentMonth: currentMonth,
            currentPosts: [cachedPost],
            previousPosts: [Self.post(id: "previous-post", day: 1, month: previousMonth)],
            gate: gate
        )

        let moving = Task { await viewModel.moveToNextMonth() }
        await gate.waitForRequest()

        #expect(viewModel.viewState.contentStatus == .loaded)
        #expect(viewModel.viewState.month == currentMonth)
        #expect(viewModel.viewState.posts == [cachedPost])
        #expect(viewModel.viewState.selectedDate == cachedPost.topicDate)

        gate.complete(with: .success(RecordCalendar(month: currentMonth, posts: [cachedPost, newPost])))
        await moving.value

        #expect(viewModel.viewState.posts == [cachedPost, newPost])
        #expect(viewModel.viewState.selectedDate == newPost.topicDate)
    }

    @Test("캐시한 달의 재조회가 실패하면 캐시한 기록을 유지하고 토스트를 띄운다")
    func failedRefreshOfCachedMonthKeepsCachedContent() async {
        let currentMonth = RecordMonth(year: 2026, month: 9)
        let previousMonth = currentMonth.adding(months: -1)
        let cachedPost = Self.post(id: "cached-post", day: 1, month: currentMonth)
        let gate = RecordCalendarGate()
        let viewModel = await Self.viewModelVisitedPreviousMonth(
            currentMonth: currentMonth,
            currentPosts: [cachedPost],
            previousPosts: [Self.post(id: "previous-post", day: 1, month: previousMonth)],
            gate: gate
        )

        let moving = Task { await viewModel.moveToNextMonth() }
        await gate.waitForRequest()
        gate.complete(with: .failure(.network))
        await moving.value

        #expect(viewModel.viewState.contentStatus == .loaded)
        #expect(viewModel.viewState.posts == [cachedPost])
        #expect(viewModel.event == .showToast(RecordError.network.message))
    }

    @Test("처음 가는 달은 기존 기록을 비우고 로딩한다")
    func movingToUncachedMonthStartsLoading() async {
        let currentMonth = RecordMonth(year: 2026, month: 9)
        let previousMonth = currentMonth.adding(months: -1)
        let currentPost = Self.post(id: "current-post", day: 1, month: currentMonth)
        let gate = RecordCalendarGate()
        let viewModel = RecordViewModel(
            monthProvider: { currentMonth },
            monthListHandler: { .success([previousMonth]) },
            calendarHandler: { month in await gate.request(month: month) }
        )
        let loading = Task { await viewModel.load() }
        await gate.waitForRequest()
        gate.complete(with: .success(RecordCalendar(month: currentMonth, posts: [currentPost])))
        await loading.value

        let moving = Task { await viewModel.moveToPreviousMonth() }
        await gate.waitForRequest()

        #expect(viewModel.viewState.contentStatus == .loading)
        #expect(viewModel.viewState.month == previousMonth)
        #expect(viewModel.viewState.posts.isEmpty)

        gate.complete(with: .success(RecordCalendar(month: previousMonth, posts: [])))
        await moving.value
    }

    @Test("삭제한 게시물은 캐시한 달로 돌아와도 다시 보이지 않는다")
    func deletedPostIsRemovedFromCachedMonths() async {
        let currentMonth = RecordMonth(year: 2026, month: 9)
        let previousMonth = currentMonth.adding(months: -1)
        let deletedPost = Self.post(id: "deleted-post", day: 1, month: currentMonth)
        let remainingPost = Self.post(id: "remaining-post", day: 2, month: currentMonth)
        let gate = RecordCalendarGate()
        let viewModel = await Self.viewModelVisitedPreviousMonth(
            currentMonth: currentMonth,
            currentPosts: [deletedPost, remainingPost],
            previousPosts: [Self.post(id: "previous-post", day: 1, month: previousMonth)],
            gate: gate
        )

        viewModel.removeDeletedPost(deletedPost.postId)
        let moving = Task { await viewModel.moveToNextMonth() }
        await gate.waitForRequest()

        #expect(viewModel.viewState.posts == [remainingPost])

        gate.complete(with: .success(RecordCalendar(month: currentMonth, posts: [remainingPost])))
        await moving.value
    }

    @Test("캐시를 보여주는 동안 사용자가 고른 날짜는 최신 값으로 교체돼도 유지한다")
    func refreshKeepsDateSelectedWhileShowingCache() async {
        let currentMonth = RecordMonth(year: 2026, month: 9)
        let previousMonth = currentMonth.adding(months: -1)
        let olderPost = Self.post(id: "older-post", day: 1, month: currentMonth)
        let latestPost = Self.post(id: "latest-post", day: 2, month: currentMonth)
        let gate = RecordCalendarGate()
        let viewModel = await Self.viewModelVisitedPreviousMonth(
            currentMonth: currentMonth,
            currentPosts: [olderPost, latestPost],
            previousPosts: [Self.post(id: "previous-post", day: 1, month: previousMonth)],
            gate: gate
        )

        let moving = Task { await viewModel.moveToNextMonth() }
        await gate.waitForRequest()
        viewModel.selectDate(olderPost.topicDate)
        gate.complete(with: .success(RecordCalendar(month: currentMonth, posts: [olderPost, latestPost])))
        await moving.value

        #expect(viewModel.viewState.selectedDate == olderPost.topicDate)
    }

    /// 현재 달을 불러온 뒤 이전 달로 이동한 뷰모델을 만든다. 현재 달 기록은 캐시에 남아 있다.
    private static func viewModelVisitedPreviousMonth(
        currentMonth: RecordMonth,
        currentPosts: [RecordPost],
        previousPosts: [RecordPost],
        gate: RecordCalendarGate
    ) async -> RecordViewModel {
        let previousMonth = currentMonth.adding(months: -1)
        let viewModel = RecordViewModel(
            monthProvider: { currentMonth },
            monthListHandler: { .success([currentMonth, previousMonth]) },
            calendarHandler: { month in await gate.request(month: month) }
        )

        let loading = Task { await viewModel.load() }
        await gate.waitForRequest()
        gate.complete(with: .success(RecordCalendar(month: currentMonth, posts: currentPosts)))
        await loading.value

        let moving = Task { await viewModel.moveToPreviousMonth() }
        await gate.waitForRequest()
        gate.complete(with: .success(RecordCalendar(month: previousMonth, posts: previousPosts)))
        await moving.value

        return viewModel
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
