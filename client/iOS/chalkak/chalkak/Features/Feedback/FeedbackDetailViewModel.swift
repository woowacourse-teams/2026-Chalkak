import Foundation
import Observation

/// 문의 한 건의 내용과 답변을 불러온다. 설정의 고객센터 목록과 답변 알림(알림함·푸시) 양쪽에서 연다.
@MainActor
@Observable
final class FeedbackDetailViewModel {
    /// 이 화면의 문의를 불러온다. 예: `{ try await client.fetchFeedback(id: feedbackID) }`
    typealias DetailLoader = @MainActor () async throws -> FeedbackDetail

    /// 문의 UUID. 삭제 버튼이 이 값으로 목록 ViewModel의 삭제를 부른다.
    let feedbackID: String
    private(set) var viewState = FeedbackDetailViewState()
    private(set) var event: FeedbackDetailEvent?

    private let loadDetail: DetailLoader

    init(feedbackID: String, loadDetail: @escaping DetailLoader) {
        self.feedbackID = feedbackID
        self.loadDetail = loadDetail
    }

    func load() async {
        viewState.status = .loading
        do {
            viewState.detail = try await loadDetail()
            viewState.status = .content
        } catch is CancellationError {
            return
        } catch FeedbackAPIError.notFound {
            // 삭제된 문의의 옛 푸시처럼 목록에 남아 있을 수 있어 목록도 다시 불러 달라고 알린다.
            viewState.detail = nil
            viewState.status = .notFound
            event = .listRefreshRequested
        } catch {
            viewState.detail = nil
            viewState.status = .failed
        }
    }

    func consumeEvent() {
        event = nil
    }
}
