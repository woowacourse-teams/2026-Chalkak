import Foundation
import Observation

@MainActor
@Observable
final class FeedbackViewModel {
    typealias SubmitHandler = @MainActor @Sendable (String) async throws -> Void

    private(set) var viewState: FeedbackViewState
    private(set) var event: FeedbackEvent?

    private let submitHandler: SubmitHandler

    init(
        initialState: FeedbackViewState? = nil,
        submitFeedback: @escaping SubmitHandler = { _ in
            throw FeedbackAPIError.configuration
        }
    ) {
        viewState = initialState ?? FeedbackViewState()
        submitHandler = submitFeedback
    }

    func updateContent(_ content: String) {
        viewState.content = content
    }

    func submit() async {
        guard viewState.canSubmit else { return }

        viewState.isSubmitting = true
        do {
            try await submitHandler(viewState.normalizedContent)
            viewState.isSubmitting = false
            publish(.submitted)
        } catch let error as FeedbackAPIError {
            viewState.isSubmitting = false
            handle(error)
        } catch is CancellationError {
            viewState.isSubmitting = false
        } catch let error as URLError where error.code != .cancelled {
            viewState.isSubmitting = false
            publish(.showMessage(Self.uncertainSubmissionMessage))
        } catch {
            viewState.isSubmitting = false
            publish(.showMessage(Self.genericErrorMessage))
        }
    }

    func consumeEvent() {
        event = nil
    }

    private func handle(_ error: FeedbackAPIError) {
        switch error {
        case .unauthorized:
            publish(.reauthenticationRequired)
        case .invalidContent:
            publish(.showMessage(Self.invalidContentMessage))
        case let .http(statusCode, message):
            if statusCode == 401 {
                publish(.reauthenticationRequired)
            } else if statusCode == 400 {
                publish(.showMessage(message ?? Self.invalidContentMessage))
            } else {
                publish(.showMessage(Self.genericErrorMessage))
            }
        case .network:
            // 타임아웃처럼 서버에 저장됐는지 알 수 없어 자동으로 다시 보내지 않고 목록 확인을 안내한다.
            publish(.showMessage(Self.uncertainSubmissionMessage))
        case .configuration, .invalidResponse, .notFound:
            publish(.showMessage(Self.genericErrorMessage))
        }
    }

    private func publish(_ event: FeedbackEvent) {
        self.event = event
    }

    private static let invalidContentMessage = "피드백은 1자 이상 1000자 이하로 입력해 주세요."
    private static let uncertainSubmissionMessage = "보내지 못했을 수 있어요. 내 문의에서 확인한 뒤 다시 보내 주세요."
    private static let genericErrorMessage = "피드백을 보내지 못했어요. 다시 시도해 주세요."
}
