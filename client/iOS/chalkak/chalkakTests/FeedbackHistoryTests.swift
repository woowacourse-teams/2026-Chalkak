import Foundation
import Testing
@testable import chalkak

@MainActor
struct FeedbackListViewModelTests {
    @Test("첫 페이지가 빈 목록이면 빈 화면 상태가 된다")
    func emptyFirstPageShowsEmptyState() async {
        // Given `feedbacks: []`, `hasNext=false` 응답
        // When  목록을 불러온다
        // Then  상태가 빈 목록(empty)
        let repository = FakeFeedbackRepository(pages: [
            .success(Self.page(1, hasNext: false, items: [])),
        ])
        let viewModel = Self.viewModel(repository)

        await viewModel.refresh()

        #expect(viewModel.viewState.status == .empty)
        #expect(viewModel.viewState.items.isEmpty)
    }

    @Test("다음 페이지가 있으면 이어 붙이고 겹친 문의는 한 번만 둔다")
    func nextPageAppendsUniqueFeedbacks() async {
        // Given 1페이지 A, B (`hasNext=true`), 2페이지 B, C
        // When  다음 페이지를 불러온다
        // Then  `page=2`를 요청하고 목록이 A, B, C
        let repository = FakeFeedbackRepository(pages: [
            .success(Self.page(1, hasNext: true, items: [Self.item(ID.a), Self.item(ID.b)])),
            .success(Self.page(2, hasNext: false, items: [Self.item(ID.b), Self.item(ID.c)])),
        ])
        let viewModel = Self.viewModel(repository)

        await viewModel.refresh()
        await viewModel.loadNextPage()

        #expect(repository.requestedPages == [1, 2])
        #expect(viewModel.viewState.items.map(\.id) == [ID.a, ID.b, ID.c])
        #expect(viewModel.viewState.status == .content)
        #expect(!viewModel.viewState.hasNext)
    }

    @Test("첫 페이지 조회가 실패하면 실패 상태가 된다")
    func failedFirstPageShowsFailedState() async {
        // Given 목록 요청이 500
        // When  목록을 불러온다
        // Then  상태가 실패(failed), 목록은 비어 있음
        let repository = FakeFeedbackRepository(pages: [
            .failure(FeedbackAPIError.http(statusCode: 500, message: nil)),
        ])
        let viewModel = Self.viewModel(repository)

        await viewModel.refresh()

        #expect(viewModel.viewState.status == .failed)
        #expect(viewModel.viewState.items.isEmpty)
    }

    @Test("삭제 204 빈 본문을 성공으로 처리하고 목록에서 뺀다")
    func deleteSuccessRemovesFeedback() async {
        // Given 목록 A, B, C, B 삭제 요청이 본문 없는 204
        // When  B를 삭제한다
        // Then  `DELETE feedbacks/{B}`, 목록이 A, C
        let repository = FakeFeedbackRepository(pages: [
            .success(Self.page(1, hasNext: false, items: [Self.item(ID.a), Self.item(ID.b), Self.item(ID.c)])),
            .success(Self.page(1, hasNext: false, items: [Self.item(ID.a), Self.item(ID.c)])),
        ])
        let viewModel = Self.viewModel(repository)
        await viewModel.refresh()

        let result = await viewModel.delete(ID.b)

        #expect(result == .deleted)
        #expect(repository.deletedIDs == [ID.b])
        #expect(viewModel.viewState.items.map(\.id) == [ID.a, ID.c])
    }

    @Test("삭제에서 404를 받으면 찾을 수 없음 안내 후 목록을 다시 불러온다")
    func deleteNotFoundRefreshesFirstPage() async {
        // Given 삭제 요청이 404
        // When  삭제한다
        // Then  "문의를 찾을 수 없어요" 메시지, 목록 1페이지 재조회
        let repository = FakeFeedbackRepository(
            pages: [
                .success(Self.page(1, hasNext: false, items: [Self.item(ID.a), Self.item(ID.b), Self.item(ID.c)])),
                .success(Self.page(1, hasNext: false, items: [Self.item(ID.a), Self.item(ID.c)])),
            ],
            deleteResults: [.failure(FeedbackAPIError.notFound)]
        )
        let viewModel = Self.viewModel(repository)
        await viewModel.refresh()

        let result = await viewModel.delete(ID.b)

        #expect(result == .notFound(message: "문의를 찾을 수 없어요"))
        #expect(repository.requestedPages == [1, 1])
        #expect(viewModel.viewState.items.map(\.id) == [ID.a, ID.c])
    }

    @Test("삭제 중 다시 눌러도 삭제 요청은 한 번이다")
    func deleteWhileDeletingSendsOnce() async throws {
        // Given 첫 삭제 요청이 아직 응답하지 않음
        // When  삭제를 한 번 더 누른다
        // Then  삭제 핸들러 호출 1회
        let repository = FakeFeedbackRepository(pages: [
            .success(Self.page(1, hasNext: false, items: [Self.item(ID.a), Self.item(ID.b)])),
            .success(Self.page(1, hasNext: false, items: [Self.item(ID.a)])),
        ])
        repository.holdsDelete = true
        let viewModel = Self.viewModel(repository)
        await viewModel.refresh()

        let firstDelete = Task { await viewModel.delete(ID.b) }
        try await waitUntil { repository.deletedIDs.count == 1 }
        let secondResult = await viewModel.delete(ID.b)
        repository.resumeHeldDelete()
        let firstResult = await firstDelete.value

        #expect(repository.deletedIDs == [ID.b])
        #expect(secondResult == .ignored)
        #expect(firstResult == .deleted)
    }

    @Test("제출에 성공하면 목록을 첫 페이지부터 다시 불러온다")
    func submittedFeedbackReloadsFirstPage() async {
        // Given 목록이 2페이지까지 불러와진 상태
        // When  제출 성공(201)
        // Then  `page=1`을 요청하고 목록을 새 응답으로 바꿈
        let repository = FakeFeedbackRepository(pages: [
            .success(Self.page(1, hasNext: true, items: [Self.item(ID.a), Self.item(ID.b)])),
            .success(Self.page(2, hasNext: false, items: [Self.item(ID.c)])),
            .success(Self.page(1, hasNext: true, items: [Self.item(ID.new), Self.item(ID.a)])),
        ])
        let viewModel = Self.viewModel(repository)
        await viewModel.refresh()
        await viewModel.loadNextPage()

        await viewModel.feedbackSubmitted()

        #expect(repository.requestedPages == [1, 2, 1])
        #expect(viewModel.viewState.items.map(\.id) == [ID.new, ID.a])
        #expect(viewModel.viewState.hasNext)
    }

    private static func viewModel(_ repository: FakeFeedbackRepository) -> FeedbackListViewModel {
        FeedbackListViewModel(
            loadPage: { try await repository.load(page: $0) },
            deleteFeedback: { try await repository.delete(id: $0) }
        )
    }

    private static func page(_ page: Int, hasNext: Bool, items: [FeedbackSummary]) -> FeedbackPage {
        FeedbackPage(currentPage: page, pageSize: 20, hasNext: hasNext, items: items)
    }

    private static func item(_ id: String) -> FeedbackSummary {
        FeedbackSummary(
            id: id,
            content: "문의 \(id)",
            createdAt: Date(timeIntervalSince1970: 1_793_000_000)
        )
    }

    private enum ID {
        static let a = "019a0020-0000-7000-8000-00000000000a"
        static let b = "019a0020-0000-7000-8000-00000000000b"
        static let c = "019a0020-0000-7000-8000-00000000000c"
        static let new = "019a0020-0000-7000-8000-0000000000ff"
    }
}

@MainActor
struct FeedbackDetailViewModelTests {
    @Test("상세에서 404를 받으면 찾을 수 없음 안내와 목록 갱신을 요청한다")
    func notFoundShowsMessageAndRequestsListRefresh() async {
        // Given 상세 요청이 404 / BUSINESS_ERROR
        // When  상세를 불러온다
        // Then  "문의를 찾을 수 없어요" 메시지, 목록 갱신 이벤트
        let viewModel = FeedbackDetailViewModel(
            feedbackID: "019a0020-0000-7000-8000-00000000000a",
            loadDetail: { throw FeedbackAPIError.notFound }
        )

        await viewModel.load()

        #expect(viewModel.viewState.status == .notFound)
        #expect(viewModel.viewState.message == "문의를 찾을 수 없어요")
        #expect(viewModel.viewState.detail == nil)
        #expect(viewModel.event == .listRefreshRequested)
    }
}

/// 목록 조회 결과를 차례로 돌려주고, 삭제 요청을 기록한다.
/// `holdsDelete`를 켜면 첫 삭제가 `resumeHeldDelete()`를 부를 때까지 응답하지 않는다.
@MainActor
private final class FakeFeedbackRepository {
    private var pages: [Result<FeedbackPage, Error>]
    private var deleteResults: [Result<Void, Error>]
    private(set) var requestedPages: [Int] = []
    private(set) var deletedIDs: [String] = []
    var holdsDelete = false
    private var heldDelete: CheckedContinuation<Void, Never>?

    init(
        pages: [Result<FeedbackPage, Error>],
        deleteResults: [Result<Void, Error>] = []
    ) {
        self.pages = pages
        self.deleteResults = deleteResults
    }

    func load(page: Int) async throws -> FeedbackPage {
        requestedPages.append(page)
        guard !pages.isEmpty else { throw FeedbackAPIError.network }
        return try pages.removeFirst().get()
    }

    func delete(id: String) async throws {
        deletedIDs.append(id)
        if holdsDelete {
            holdsDelete = false
            await withCheckedContinuation { heldDelete = $0 }
        }
        guard !deleteResults.isEmpty else { return }
        try deleteResults.removeFirst().get()
    }

    func resumeHeldDelete() {
        heldDelete?.resume()
        heldDelete = nil
    }
}
