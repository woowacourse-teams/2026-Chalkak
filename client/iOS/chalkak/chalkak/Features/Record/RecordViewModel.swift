import Foundation
import Observation

@MainActor
@Observable
final class RecordViewModel {
    typealias MonthProvider = @MainActor @Sendable () -> RecordMonth
    typealias CalendarHandler = @MainActor @Sendable (RecordMonth) async -> Result<RecordCalendar, RecordError>

    private(set) var viewState: RecordViewState
    private(set) var event: RecordEvent?

    private let monthProvider: MonthProvider
    private let calendarHandler: CalendarHandler
    private var generation = 0

    init(
        initialState: RecordViewState? = nil,
        monthProvider: @escaping MonthProvider = { .current() },
        calendarHandler: @escaping CalendarHandler = { _ in .failure(.generic) }
    ) {
        let latestMonth = monthProvider()
        self.viewState = initialState ?? RecordViewState(month: latestMonth, latestMonth: latestMonth)
        self.monthProvider = monthProvider
        self.calendarHandler = calendarHandler
    }

    convenience init(
        monthProvider: @escaping MonthProvider = { .current() },
        apiClient: RecordAPIClient
    ) {
        self.init(
            monthProvider: monthProvider,
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
        await loadCalendar(month: viewState.month)
    }

    func moveToPreviousMonth() async {
        guard viewState.canGoPrevious else { return }
        await loadCalendar(month: viewState.month.adding(months: -1))
    }

    func moveToNextMonth() async {
        guard viewState.canGoNext else { return }
        await loadCalendar(month: viewState.month.adding(months: 1))
    }

    func selectDate(_ date: Date) {
        guard viewState.posts.contains(where: { $0.topicDate == date }) else { return }
        viewState.selectedDate = date
    }

    func retry() async {
        await loadCalendar(month: viewState.month)
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

    private func loadCalendar(month: RecordMonth) async {
        generation += 1
        let requestGeneration = generation
        let latestMonth = max(viewState.latestMonth, monthProvider())

        viewState = RecordViewState(
            contentStatus: .loading,
            month: month,
            latestMonth: latestMonth,
            posts: [],
            selectedDate: nil
        )

        let result = await calendarHandler(month)
        guard requestGeneration == generation else { return }

        switch result {
        case let .success(calendar):
            viewState = RecordViewState(
                contentStatus: .loaded,
                month: calendar.month,
                latestMonth: latestMonth,
                posts: calendar.posts,
                selectedDate: calendar.posts.first?.topicDate
            )
        case let .failure(error):
            viewState = RecordViewState(
                contentStatus: .error(error),
                month: month,
                latestMonth: latestMonth,
                posts: [],
                selectedDate: nil
            )
        }
    }
}
