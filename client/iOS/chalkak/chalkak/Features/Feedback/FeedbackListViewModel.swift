import Foundation
import Observation

/// 고객센터의 내 문의 목록. 1페이지부터 20건씩 불러오고, 상세에서 누른 삭제도 여기서 보낸다.
/// 상세가 푸시로 바로 열려 목록 화면이 없을 때도 같은 인스턴스로 삭제·목록 갱신을 한다.
@MainActor
@Observable
final class FeedbackListViewModel {
    /// 페이지 번호(1부터)를 받아 그 페이지를 돌려준다. 예: `{ try await client.fetchFeedbacks(page: $0) }`
    typealias PageLoader = @MainActor (Int) async throws -> FeedbackPage
    /// 문의 UUID를 받아 삭제한다. 204면 그냥 끝나고, 404면 `FeedbackAPIError.notFound`를 던진다.
    typealias DeleteAction = @MainActor (String) async throws -> Void

    private(set) var viewState = FeedbackListViewState()

    private var currentPage = 0
    /// 새로고침이 겹치면 마지막 요청의 응답만 반영한다.
    private var generation = 0
    private let loadPage: PageLoader
    private let deleteFeedback: DeleteAction

    init(loadPage: @escaping PageLoader, deleteFeedback: @escaping DeleteAction) {
        self.loadPage = loadPage
        self.deleteFeedback = deleteFeedback
    }

    /// 1페이지부터 다시 불러와 목록을 바꾼다. 이미 목록이 있으면 실패해도 그 목록을 그대로 둔다.
    func refresh() async {
        generation += 1
        let requestGeneration = generation
        viewState.isLoadingMore = false
        viewState.loadMoreFailed = false
        if viewState.items.isEmpty { viewState.status = .loading }
        do {
            let page = try await loadPage(1)
            guard generation == requestGeneration, !Task.isCancelled else { return }
            apply(page, replacing: true)
        } catch is CancellationError {
            return
        } catch {
            guard generation == requestGeneration, !Task.isCancelled else { return }
            if viewState.items.isEmpty { viewState.status = .failed }
        }
    }

    /// `hasNext`일 때 다음 페이지를 붙인다. 이미 있는 문의 ID는 다시 넣지 않는다.
    func loadNextPage() async {
        guard viewState.status == .content, viewState.hasNext, !viewState.isLoadingMore else { return }
        let requestGeneration = generation
        viewState.isLoadingMore = true
        viewState.loadMoreFailed = false
        defer { if generation == requestGeneration { viewState.isLoadingMore = false } }
        do {
            let page = try await loadPage(currentPage + 1)
            guard generation == requestGeneration, !Task.isCancelled else { return }
            apply(page, replacing: false)
        } catch is CancellationError {
            return
        } catch {
            guard generation == requestGeneration, !Task.isCancelled else { return }
            viewState.loadMoreFailed = true
        }
    }

    /// 새 문의를 보낸 뒤 맨 위에 보이도록 1페이지부터 다시 구성한다.
    func feedbackSubmitted() async {
        await refresh()
    }

    func isDeleting(_ feedbackID: String) -> Bool {
        viewState.deletingIDs.contains(feedbackID)
    }

    /// 삭제를 보내고 결과를 돌려준다. 성공·404 모두 목록을 1페이지부터 다시 불러온다.
    func delete(_ feedbackID: String) async -> FeedbackDeleteResult {
        guard viewState.deletingIDs.insert(feedbackID).inserted else { return .ignored }
        defer { viewState.deletingIDs.remove(feedbackID) }
        do {
            try await deleteFeedback(feedbackID)
            remove(feedbackID)
            await refresh()
            return .deleted
        } catch FeedbackAPIError.notFound {
            remove(feedbackID)
            await refresh()
            return .notFound(message: FeedbackMessages.notFound)
        } catch {
            return .failed(message: Self.deleteFailedMessage)
        }
    }

    private func remove(_ feedbackID: String) {
        viewState.items.removeAll { $0.id == feedbackID }
        if viewState.items.isEmpty, !viewState.hasNext, viewState.status == .content {
            viewState.status = .empty
        }
    }

    private func apply(_ page: FeedbackPage, replacing: Bool) {
        var seen = Set(replacing ? [] : viewState.items.map(\.id))
        let unique = page.items.filter { seen.insert($0.id).inserted }
        viewState.items = replacing ? unique : viewState.items + unique
        viewState.hasNext = page.hasNext
        currentPage = page.currentPage
        viewState.status = viewState.items.isEmpty ? .empty : .content
    }

    private static let deleteFailedMessage = "문의를 삭제하지 못했어요. 다시 시도해 주세요."
}
