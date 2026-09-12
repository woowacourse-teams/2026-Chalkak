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
