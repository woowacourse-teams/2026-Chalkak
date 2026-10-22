import Foundation
import Observation

@MainActor
@Observable
final class FeedAIReviewViewModel {
    private(set) var review: AIReview?
    private(set) var isLoading = true
    private(set) var isSubmitting = false
    private(set) var isUnavailable = false
    private(set) var pollingTimedOut = false
    private(set) var message: String?

    private var topicDate: Date
    private let postID: String
    private let repository: any AIReviewRepository
    private let now: @MainActor () -> Date
    private let sleep: @Sendable (Duration) async throws -> Void
    private var generation = 0
    private var revision = 0
    private var fetchSequence = 0
    private var deadline: Date?
    private var isObserving = false

    init(
        postID: String,
        repository: any AIReviewRepository,
        topicDate: Date = Date(),
        now: @escaping @MainActor () -> Date = { Date() },
        sleep: @escaping @Sendable (Duration) async throws -> Void = { try await Task.sleep(for: $0) }
    ) {
        self.topicDate = topicDate
        self.postID = postID
        self.repository = repository
        self.now = now
        self.sleep = sleep
    }

    var isButtonVisible: Bool {
        !isUnavailable && (FeedDateLabel.isToday(topicDate, now: now()) || review?.status == .completed)
    }

    func updateTopicDate(_ date: Date) {
        topicDate = date
    }

    var canRequest: Bool {
        FeedDateLabel.isToday(topicDate, now: now()) && !isLoading && !isSubmitting && !isUnavailable &&
        (review?.status == AIReviewStatus.none || (review?.status == .failed && review?.canRequestAgain == true))
    }

    var buttonTitle: String {
        if isLoading { return "AI 상태 확인중" }
        if isSubmitting { return "AI 요청중" }
        switch review?.status {
        case .processing: return "AI 분석중"
        case .completed: return "AI 피드백 보기"
        case .failed: return "AI 피드백 다시 확인"
        case .none?: return "AI 피드백 받기"
        case nil: return "AI 상태 다시 확인"
        }
    }

    // The screen owns this task: leaving or backgrounding cancels it, reentry starts with GET.
    func observe() async {
        generation += 1
        let session = generation
        isObserving = true
        isLoading = true
        isUnavailable = false
        pollingTimedOut = false
        deadline = now().addingTimeInterval(300)
        await fetch(session: session)
        while current(session) {
            do { try await sleep(.seconds(3)) } catch { break }
            guard current(session) else { break }
            guard review?.status == .processing, !isLoading, !isSubmitting, !isUnavailable else { continue }
            guard let deadline, now() < deadline else {
                pollingTimedOut = true
                continue
            }
            await fetch(session: session, showsError: false)
        }
        if generation == session {
            isObserving = false
            isLoading = false
            isSubmitting = false
            generation += 1
        }
    }

    func refresh() async {
        guard isObserving, !isLoading, !isSubmitting else { return }
        isLoading = true
        isUnavailable = false
        pollingTimedOut = false
        deadline = now().addingTimeInterval(300)
        await fetch(session: generation)
    }

    func request() async {
        guard canRequest, isObserving else { return }
        let session = generation
        revision += 1
        isSubmitting = true
        message = nil
        pollingTimedOut = false
        deadline = now().addingTimeInterval(300)
        do {
            let response = try await repository.requestReview(postID: postID)
            guard current(session) else { return }
            review = response
        } catch {
            guard current(session) else { return }
            handle(error)
            // Resolve a potentially accepted request with GET; never retry POST automatically.
            switch error as? AIReviewError {
            case .network?, .invalidResponse?, .http(statusCode: 409, message: _)?:
                await fetch(session: session)
            default: break
            }
        }
        guard current(session) else { return }
        isSubmitting = false
    }

    private func current(_ session: Int) -> Bool {
        generation == session && !Task.isCancelled && isObserving
    }

    private func fetch(session: Int, showsError: Bool = true) async {
        let startRevision = revision
        fetchSequence += 1
        let sequence = fetchSequence
        do {
            let response = try await repository.fetchReview(postID: postID)
            guard current(session), startRevision == revision, sequence == fetchSequence else { return }
            review = response
            message = nil
        } catch {
            guard current(session), startRevision == revision, sequence == fetchSequence else { return }
            if showsError { handle(error) }
            else if case .http(404, _) = error as? AIReviewError { isUnavailable = true }
        }
        guard current(session), startRevision == revision, sequence == fetchSequence else { return }
        isLoading = false
    }

    private func handle(_ error: Error) {
        guard !(error is CancellationError) else { return }
        if case .http(404, _) = error as? AIReviewError {
            isUnavailable = true
            message = "AI 피드백을 확인할 수 없는 게시물이에요."
        } else {
            switch error as? AIReviewError {
            case let .http(statusCode, serverMessage):
                message = serverMessage ?? (statusCode == 403
                    ? "이용이 제한되어 AI 피드백을 요청할 수 없어요."
                    : "AI 피드백을 불러오지 못했어요. 잠시 후 다시 확인해 주세요.")
            case .unauthorized?:
                message = "로그인이 필요합니다."
            default:
                message = "AI 피드백을 불러오지 못했어요. 잠시 후 다시 확인해 주세요."
            }
        }
    }
}
