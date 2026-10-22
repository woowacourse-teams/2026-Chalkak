import Foundation
import Observation

@MainActor
@Observable
final class RecordViewModel {
    typealias MonthProvider = @MainActor @Sendable () -> RecordMonth
    typealias CalendarHandler = @MainActor @Sendable (RecordMonth) async -> Result<RecordCalendar, RecordError>
    typealias MonthListHandler = @MainActor @Sendable () async -> Result<[RecordMonth], RecordError>

    private(set) var viewState: RecordViewState
    private(set) var event: RecordEvent?

    private let monthProvider: MonthProvider
    private let calendarHandler: CalendarHandler
    private let monthListHandler: MonthListHandler
    private var generation = 0
    private var isRevalidating = false
    private var shouldRetryMonthList = false
    // 한 번 받은 달의 기록. 같은 달로 돌아오면 빈 화면·로딩 없이 바로 보여주고 뒤에서 최신 값으로 교체한다.
    // 이 뷰모델과 수명이 같아서 로그아웃 등으로 뷰모델을 새로 만들면 함께 비워진다.
    private var calendarCache: [RecordMonth: RecordCalendar] = [:]

    init(
        initialState: RecordViewState? = nil,
        monthProvider: @escaping MonthProvider = { .current() },
        monthListHandler: @escaping MonthListHandler = { .success([]) },
        calendarHandler: @escaping CalendarHandler = { _ in .failure(.generic) }
    ) {
        let latestMonth = monthProvider()
        self.viewState = initialState ?? RecordViewState(month: latestMonth, latestMonth: latestMonth)
        self.monthProvider = monthProvider
        self.calendarHandler = calendarHandler
        self.monthListHandler = monthListHandler
    }

    convenience init(
        monthProvider: @escaping MonthProvider = { .current() },
        apiClient: RecordAPIClient
    ) {
        self.init(
            monthProvider: monthProvider,
            monthListHandler: {
                do {
                    return .success(try await apiClient.fetchCalendarMonths())
                } catch is CancellationError {
                    return .failure(.generic)
                } catch let error as RecordError {
                    return .failure(error)
                } catch {
                    return .failure(.generic)
                }
            },
            calendarHandler: { month in
                do {
                    return .success(try await apiClient.fetchCalendar(month: month))
                } catch is CancellationError {
                    return .failure(.generic)
                } catch let error as RecordError {
                    return .failure(error)
                } catch {
                    return .failure(.generic)
                }
            }
        )
    }

    func load() async {
        generation += 1
        let requestGeneration = generation
        switch await monthListHandler() {
        case let .success(months):
            guard requestGeneration == generation else { return }
            shouldRetryMonthList = false
            let availableMonths = monthsIncludingCurrent(months)
            let initialMonth = monthProvider()
            viewState.availableMonths = availableMonths
            await loadCalendar(month: initialMonth)
        case .failure:
            guard requestGeneration == generation else { return }
            shouldRetryMonthList = true
            viewState.availableMonths = monthsIncludingCurrent([])
            await loadCalendar(month: viewState.month)
        }
    }

    func moveToPreviousMonth() async {
        guard viewState.canGoPrevious else { return }
        guard let previousMonth = viewState.availableMonths.filter({ $0 < viewState.month }).max() else { return }
        await loadCalendar(month: previousMonth)
    }

    func moveToNextMonth() async {
        guard viewState.canGoNext else { return }
        guard let nextMonth = viewState.availableMonths.filter({ $0 > viewState.month }).min() else { return }
        await loadCalendar(month: nextMonth)
    }

    func selectDate(_ date: Date) {
        guard viewState.posts.contains(where: { $0.topicDate == date }) else { return }
        viewState.selectedDate = date
    }

    func retry() async {
        if shouldRetryMonthList || viewState.availableMonths.isEmpty {
            await load()
        } else {
            await loadCalendar(month: viewState.month)
        }
    }

    /// 현재 달력 데이터를 유지한 채 같은 월을 최신 데이터로 갱신한다.
    func revalidate() async {
        guard viewState.contentStatus != .loading, !isRevalidating else { return }

        isRevalidating = true
        defer { isRevalidating = false }

        generation += 1
        let requestGeneration = generation
        switch await monthListHandler() {
        case let .success(months):
            guard requestGeneration == generation else { return }
            shouldRetryMonthList = false
            viewState.availableMonths = monthsIncludingCurrent(months)
        case let .failure(error):
            guard requestGeneration == generation else { return }
            shouldRetryMonthList = true
            viewState.availableMonths = monthsIncludingCurrent(viewState.availableMonths)
            event = .showToast(error.message)
        }

        let previous = viewState.contentStatus == .loaded ? viewState : nil
        await loadCalendar(month: viewState.month, preserving: previous)
    }

    func onCalendarImageSaved(_ saved: Bool) {
        event = .showToast(saved ? "달력을 이미지로 저장했어요" : "이미지 저장에 실패했어요")
    }

    func onPhotoLibraryPermissionDenied() {
        event = .showToast("이미지 저장 권한이 필요해요")
    }

    func consumeEvent() {
        event = nil
    }

    func removeDeletedPost(_ postID: RecordPost.ID) {
        // 다른 달로 갔다 돌아왔을 때 캐시에서 지운 게시물이 다시 보이지 않게 한다.
        calendarCache = calendarCache.mapValues { calendar in
            RecordCalendar(month: calendar.month, posts: calendar.posts.filter { $0.postId != postID })
        }
        viewState.posts.removeAll { $0.postId == postID }
        if viewState.month != monthProvider(),
           !viewState.posts.contains(where: { viewState.month.contains($0.topicDate) }) {
            viewState.availableMonths.removeAll { $0 == viewState.month }
        }
        viewState.availableMonths = monthsIncludingCurrent(viewState.availableMonths)
        if viewState.selectedPost == nil {
            viewState.selectedDate = viewState.posts.max { $0.topicDate < $1.topicDate }?.topicDate
        }
    }

    /// `month`의 기록을 받아 화면에 반영한다.
    /// - Parameter previousState: 요청하는 동안 유지할 화면. nil이고 `month`를 캐시해 뒀으면 캐시로 만든 화면을 쓴다.
    ///   유지할 화면이 있으면 실패해도 그 화면을 남기고 토스트만 띄우며, 없으면 로딩 화면에서 시작해 실패 시 오류 화면을 보여준다.
    private func loadCalendar(
        month: RecordMonth,
        preserving previousState: RecordViewState? = nil
    ) async {
        generation += 1
        let requestGeneration = generation
        let latestMonth = max(viewState.latestMonth, monthProvider())
        let availableMonths = monthsIncludingCurrent(viewState.availableMonths)
        viewState.availableMonths = availableMonths

        let previousState = previousState ?? calendarCache[month].map {
            loadedState(
                for: $0,
                latestMonth: latestMonth,
                availableMonths: availableMonths,
                selectedDate: nil
            )
        }

        if let previousState {
            viewState = previousState
            viewState.latestMonth = latestMonth
        } else {
            viewState = RecordViewState(
                contentStatus: .loading,
                month: month,
                latestMonth: latestMonth,
                availableMonths: availableMonths,
                posts: [],
                selectedDate: nil
            )
        }

        let selectedDateBeforeRequest = viewState.selectedDate
        let result = await calendarHandler(month)
        // 그사이 다른 달로 넘어갔어도 받은 기록은 다음에 돌아올 때 쓰도록 남긴다.
        if case let .success(calendar) = result {
            calendarCache[month] = calendar
        }
        guard requestGeneration == generation else { return }

        switch result {
        case let .success(calendar):
            // 요청하는 동안 사용자가 직접 고른 날짜만 유지한다. 그대로면 새로 올라온 최근 사진을 고르게 둔다.
            let userSelectedDate = viewState.selectedDate != selectedDateBeforeRequest ? viewState.selectedDate : nil
            viewState = loadedState(
                for: calendar,
                latestMonth: latestMonth,
                availableMonths: availableMonths,
                selectedDate: userSelectedDate
            )
        case let .failure(error):
            if var previousState {
                previousState.latestMonth = latestMonth
                previousState.availableMonths = availableMonths
                viewState = previousState
                event = .showToast(error.message)
            } else {
                viewState = RecordViewState(
                    contentStatus: .error(error),
                    month: month,
                    latestMonth: latestMonth,
                    availableMonths: availableMonths,
                    posts: [],
                    selectedDate: nil
                )
            }
        }
    }

    /// 받은 기록으로 화면 상태를 만든다.
    /// - Parameter selectedDate: 이 날짜의 게시물이 남아 있으면 선택을 유지하고, nil이거나 없으면 가장 최근 날짜를 고른다.
    private func loadedState(
        for calendar: RecordCalendar,
        latestMonth: RecordMonth,
        availableMonths: [RecordMonth],
        selectedDate: Date?
    ) -> RecordViewState {
        let keepsSelection = selectedDate.map { date in
            calendar.posts.contains { $0.topicDate == date }
        } ?? false
        return RecordViewState(
            contentStatus: .loaded,
            month: calendar.month,
            latestMonth: latestMonth,
            availableMonths: availableMonths,
            posts: calendar.posts,
            selectedDate: keepsSelection
                ? selectedDate
                : calendar.posts.max { $0.topicDate < $1.topicDate }?.topicDate
        )
    }

    private func monthsIncludingCurrent(_ months: [RecordMonth]) -> [RecordMonth] {
        Array(Set(months + [monthProvider()])).sorted(by: >)
    }
}
