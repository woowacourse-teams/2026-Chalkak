import Foundation
import Testing
@testable import chalkak

@MainActor
struct HomeAIReviewTests {
    @Test("첫 GET의 NONE 상태로 요청 가능 여부를 결정한다")
    func initialStatus() async {
        let repository = ReviewRepositoryProbe(fetches: [.value(Self.review(.none))])
        let sleep = ReviewSleepProbe()
        let model = Self.model(repository, sleep: sleep)
        let observer = Task { await model.observeAIReview(postID: "post") }
        await sleep.waitForCalls(1)
        #expect(model.viewState.aiReview.review?.status == AIReviewStatus.none)
        #expect(!model.viewState.aiReview.isLoading)
        #expect(await repository.fetchIDs == ["post"])
        #expect(await repository.requestIDs.isEmpty)
        observer.cancel()
        await observer.value
    }

    @Test("POST 응답의 PROCESSING에서 GET 폴링으로 완료된다")
    func requestThenPoll() async {
        let repository = ReviewRepositoryProbe(
            fetches: [.value(Self.review(.none)), .value(Self.review(.completed))],
            requests: [.value(Self.review(.processing))]
        )
        let sleep = ReviewSleepProbe()
        let model = Self.model(repository, sleep: sleep)
        let observer = Task { await model.observeAIReview(postID: "post") }
        await sleep.waitForCalls(1)
        await model.requestAIReview()
        #expect(model.viewState.aiReview.review?.status == .processing)
        await sleep.advance()
        await sleep.waitForCalls(2)
        #expect(model.viewState.aiReview.review?.status == .completed)
        #expect(await repository.fetchIDs == ["post", "post"])
        #expect(await repository.requestIDs == ["post"])
        // Terminal states must not issue additional GET requests.
        await sleep.advance()
        await sleep.waitForCalls(3)
        #expect(await repository.fetchIDs.count == 2)
        observer.cancel()
        await observer.value
    }

    @Test("실패 리뷰의 재요청은 서버 canRequestAgain 값으로 제한한다", arguments: [false, true])
    func retryPermission(canRetry: Bool) async {
        let repository = ReviewRepositoryProbe(
            fetches: [.value(Self.review(.failed, canRequestAgain: canRetry))],
            requests: [.value(Self.review(.processing))]
        )
        let sleep = ReviewSleepProbe()
        let model = Self.model(repository, sleep: sleep)
        let observer = Task { await model.observeAIReview(postID: "post") }
        await sleep.waitForCalls(1)
        await model.requestAIReview()
        #expect(await repository.requestIDs.count == (canRetry ? 1 : 0))
        #expect(model.viewState.aiReview.review?.status == (canRetry ? .processing : .failed))
        observer.cancel()
        await observer.value
    }

    @Test("POST 진행 중 중복 탭은 추가 POST를 보내지 않는다")
    func duplicateRequest() async {
        let gate = ReviewResponseGate()
        let repository = ReviewRepositoryProbe(
            fetches: [.value(Self.review(.none))], requests: [.suspended(gate)]
        )
        let sleep = ReviewSleepProbe()
        let model = Self.model(repository, sleep: sleep)
        let observer = Task { await model.observeAIReview(postID: "post") }
        await sleep.waitForCalls(1)
        let request = Task { await model.requestAIReview() }
        await gate.waitUntilStarted()
        #expect(model.viewState.aiReview.isSubmitting)
        await model.requestAIReview()
        #expect(await repository.requestIDs == ["post"])
        await gate.complete(Self.review(.processing))
        await request.value
        #expect(!model.viewState.aiReview.isSubmitting)
        observer.cancel()
        await observer.value
    }

    @Test("400 메시지를 그대로 유지하며 자동 재요청하지 않는다")
    func businessError() async {
        let message = "더 이상 AI 리뷰를 다시 요청할 수 없습니다."
        let repository = ReviewRepositoryProbe(
            fetches: [.value(Self.review(.failed, canRequestAgain: true))],
            requests: [.failure(.http(statusCode: 400, message: message))]
        )
        let sleep = ReviewSleepProbe()
        let model = Self.model(repository, sleep: sleep)
        let observer = Task { await model.observeAIReview(postID: "post") }
        await sleep.waitForCalls(1)
        await model.requestAIReview()
        await sleep.advance()
        await sleep.waitForCalls(2)
        #expect(model.viewState.aiReview.message == message)
        #expect(model.viewState.aiReview.review?.status == .failed)
        #expect(await repository.requestIDs == ["post"])
        #expect(await repository.fetchIDs == ["post"])
        observer.cancel()
        await observer.value
    }

    @Test("POST 응답 유실은 GET으로 확인하며 POST를 자동 반복하지 않는다")
    func lostPostResponse() async {
        let repository = ReviewRepositoryProbe(
            fetches: [.value(Self.review(.none)), .value(Self.review(.processing))],
            requests: [.failure(.network)]
        )
        let sleep = ReviewSleepProbe()
        let model = Self.model(repository, sleep: sleep)
        let observer = Task { await model.observeAIReview(postID: "post") }
        await sleep.waitForCalls(1)
        await model.requestAIReview()
        #expect(model.viewState.aiReview.review?.status == .processing)
        #expect(await repository.fetchIDs == ["post", "post"])
        #expect(await repository.requestIDs == ["post"])
        #expect(!model.viewState.aiReview.isSubmitting)
        observer.cancel()
        await observer.value
    }

    @Test("5분 제한은 화면 진입 시각부터 계산하며 시간 초과도 PROCESSING이다")
    func pollingTimeout() async {
        let clock = ReviewClock()
        let oldRequest = AIReview(status: .processing, requestedAt: "2000-01-01T00:00:00Z", completedAt: nil, canRequestAgain: false)
        let repository = ReviewRepositoryProbe(fetches: [.value(oldRequest), .value(oldRequest)])
        let sleep = ReviewSleepProbe()
        let model = Self.model(repository, sleep: sleep, now: { clock.now })
        let observer = Task { await model.observeAIReview(postID: "post") }
        await sleep.waitForCalls(1)
        #expect(!model.viewState.aiReview.pollingTimedOut)
        clock.now = clock.now.addingTimeInterval(299)
        await sleep.advance()
        await sleep.waitForCalls(2)
        #expect(!model.viewState.aiReview.pollingTimedOut)
        clock.now = clock.now.addingTimeInterval(1)
        await sleep.advance()
        await sleep.waitForCalls(3)
        #expect(model.viewState.aiReview.pollingTimedOut)
        #expect(model.viewState.aiReview.review?.status == .processing)
        #expect(await repository.fetchIDs.count == 2)
        observer.cancel()
        await observer.value
    }

    @Test("화면 작업 취소 후 도착한 GET은 상태를 갱신하지 않는다")
    func cancelledObservation() async {
        let gate = ReviewResponseGate()
        let repository = ReviewRepositoryProbe(fetches: [.suspended(gate)])
        let sleep = ReviewSleepProbe()
        let model = Self.model(repository, sleep: sleep)
        let observer = Task { await model.observeAIReview(postID: "post") }
        await gate.waitUntilStarted()
        observer.cancel()
        await gate.complete(Self.review(.completed))
        await observer.value
        #expect(model.viewState.aiReview.review == nil)
        #expect(!model.viewState.aiReview.isLoading)
        #expect(await sleep.callCount == 0)
    }

    @Test("화면 작업 취소 후 별도 POST 작업의 늦은 완료 응답도 무시한다")
    func cancelledObservationIgnoresInFlightPost() async {
        let gate = ReviewResponseGate()
        let repository = ReviewRepositoryProbe(
            fetches: [.value(Self.review(.none))],
            requests: [.suspended(gate)]
        )
        let sleep = ReviewSleepProbe()
        let model = Self.model(repository, sleep: sleep)
        let observer = Task { await model.observeAIReview(postID: "post") }
        await sleep.waitForCalls(1)
        let request = Task { await model.requestAIReview() }
        await gate.waitUntilStarted()
        observer.cancel()
        await gate.complete(Self.review(.completed))
        await request.value
        await observer.value
        #expect(model.viewState.aiReview.review?.status == AIReviewStatus.none)
        #expect(model.viewState.aiReview.review?.result == nil)
        #expect(!model.viewState.aiReview.isSubmitting)
        #expect(await repository.requestIDs == ["post"])
    }

    @Test("게시물 변경 이후 이전 GET 응답은 새 게시물 상태를 덮어쓰지 않는다")
    func switchedPost() async {
        let gate = ReviewResponseGate()
        let repository = ReviewRepositoryProbe(
            fetches: [.suspended(gate), .value(Self.review(.none))],
            requests: [.value(Self.review(.processing))]
        )
        let sleep = ReviewSleepProbe()
        let model = Self.model(repository, sleep: sleep)
        let previous = Task { await model.observeAIReview(postID: "old") }
        await gate.waitUntilStarted()
        let current = Task { await model.observeAIReview(postID: "new") }
        await sleep.waitForCalls(1)
        await gate.complete(Self.review(.completed))
        await previous.value
        #expect(model.viewState.aiReview.review?.status == AIReviewStatus.none)
        #expect(await repository.fetchIDs == ["old", "new"])
        await model.requestAIReview()
        #expect(await repository.requestIDs == ["new"])
        current.cancel()
        await current.value
    }

    private static func review(_ status: AIReviewStatus, canRequestAgain: Bool = false) -> AIReview {
        AIReview(status: status, requestedAt: nil, completedAt: nil, canRequestAgain: canRequestAgain,
                 result: status == .completed ? HomePreviewData.aiReviewResult : nil)
    }

    private static func model(
        _ repository: ReviewRepositoryProbe,
        sleep: ReviewSleepProbe,
        now: @escaping @MainActor () -> Date = { Date(timeIntervalSince1970: 1_000) }
    ) -> HomeViewModel {
        HomeViewModel(
            initialState: HomePreviewData.contentState,
            loader: HomePreviewData.loader,
            isAuthenticated: { true },
            aiReviewRepository: repository,
            aiReviewNow: now,
            aiReviewSleep: { _ in try await sleep.sleep() }
        )
    }
}

@MainActor
private final class ReviewClock {
    var now = Date(timeIntervalSince1970: 1_000)
}

private actor ReviewRepositoryProbe: AIReviewRepository {
    enum Response: Sendable {
        case value(AIReview)
        case failure(AIReviewError)
        case suspended(ReviewResponseGate)

        func resolve() async throws -> AIReview {
            switch self {
            case .value(let review): return review
            case .failure(let error): throw error
            case .suspended(let gate): return await gate.load()
            }
        }
    }

    private var fetches: [Response]
    private var requests: [Response]
    private(set) var fetchIDs: [String] = []
    private(set) var requestIDs: [String] = []

    init(fetches: [Response], requests: [Response] = []) {
        self.fetches = fetches
        self.requests = requests
    }

    func fetchReview(postID: String) async throws -> AIReview {
        fetchIDs.append(postID)
        guard !fetches.isEmpty else { throw AIReviewError.invalidResponse }
        return try await fetches.removeFirst().resolve()
    }

    func requestReview(postID: String) async throws -> AIReview {
        requestIDs.append(postID)
        guard !requests.isEmpty else { throw AIReviewError.invalidResponse }
        return try await requests.removeFirst().resolve()
    }
}

/// Deliberately ignores cancellation to test responses from an already cancelled request.
private actor ReviewResponseGate {
    private var started = false
    private var startWaiters: [CheckedContinuation<Void, Never>] = []
    private var response: CheckedContinuation<AIReview, Never>?

    func load() async -> AIReview {
        await withCheckedContinuation { continuation in
            response = continuation
            started = true
            startWaiters.forEach { $0.resume() }
            startWaiters.removeAll()
        }
    }

    func waitUntilStarted() async {
        guard !started else { return }
        await withCheckedContinuation { startWaiters.append($0) }
    }

    func complete(_ review: AIReview) {
        response?.resume(returning: review)
        response = nil
    }
}

/// Each sleep is a checkpoint after the preceding state mutation, without real delays.
private actor ReviewSleepProbe {
    private(set) var callCount = 0
    private var sleepers: [UUID: CheckedContinuation<Void, Error>] = [:]
    private var waiters: [(Int, CheckedContinuation<Void, Never>)] = []

    func sleep() async throws {
        let id = UUID()
        try await withTaskCancellationHandler {
            try Task.checkCancellation()
            try await withCheckedThrowingContinuation { continuation in
                sleepers[id] = continuation
                callCount += 1
                let ready = waiters.filter { $0.0 <= callCount }
                waiters.removeAll { $0.0 <= callCount }
                ready.forEach { $0.1.resume() }
            }
        } onCancel: {
            Task { await self.cancel(id) }
        }
    }

    func waitForCalls(_ count: Int) async {
        guard callCount < count else { return }
        await withCheckedContinuation { waiters.append((count, $0)) }
    }

    func advance() {
        let pending = Array(sleepers.values)
        sleepers.removeAll()
        pending.forEach { $0.resume() }
    }

    private func cancel(_ id: UUID) {
        sleepers.removeValue(forKey: id)?.resume(throwing: CancellationError())
    }
}
