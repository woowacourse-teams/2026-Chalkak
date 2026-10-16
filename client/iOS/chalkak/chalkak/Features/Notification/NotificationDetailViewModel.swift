import Foundation
import Observation

@MainActor
@Observable
final class NotificationDetailViewModel {
    typealias DetailLoader = @MainActor @Sendable () async throws -> NotificationDetail

    private(set) var viewState: NotificationDetailViewState

    private let loadDetail: DetailLoader

    init(
        initialState: NotificationDetailViewState = NotificationDetailViewState(),
        loadDetail: @escaping DetailLoader
    ) {
        viewState = initialState
        self.loadDetail = loadDetail
    }

    func load() async {
        viewState.status = .loading
        do {
            viewState.detail = try await loadDetail()
            viewState.status = .content
        } catch is CancellationError {
            return
        } catch NotificationAPIError.notFound {
            // 다른 계정의 알림, 삭제된 게시물의 알림, 보관 기한이 지난 알림은 찾을 수 없는 것으로 온다.
            viewState.detail = nil
            viewState.status = .notFound
        } catch {
            viewState.detail = nil
            viewState.status = .failed
        }
    }
}
