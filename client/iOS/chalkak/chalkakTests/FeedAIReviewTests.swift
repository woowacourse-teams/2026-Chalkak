import Foundation
import Testing
@testable import chalkak

@MainActor
struct FeedAIReviewTests {
    @Test("피드 진입은 상태만 조회하고 분석 시작은 명시적인 요청에만 수행한다")
    func getBeforeExplicitRequest() async {
        let repository = FeedReviewProbe(status: .none)
        let model = FeedAIReviewViewModel(postID: "post", repository: repository, sleep: { _ in
            try await Task.sleep(for: .seconds(3600))
        })
        let observer = Task { await model.observe() }
        await wait { !model.isLoading }
        #expect(model.canRequest)
        #expect(await repository.requestCount == 0)
        await model.request()
        #expect(model.review?.status == .processing)
        #expect(model.buttonTitle == "AI 분석중")
        #expect(await repository.requestCount == 1)
        observer.cancel()
        await observer.value
    }

    @Test("실패 후 재요청은 서버가 허용한 경우만 가능하다")
    func retryPermission() async {
        let repository = FeedReviewProbe(status: .failed, canRequestAgain: false)
        let model = FeedAIReviewViewModel(postID: "post", repository: repository, sleep: { _ in
            try await Task.sleep(for: .seconds(3600))
        })
        let observer = Task { await model.observe() }
        await wait { !model.isLoading }
        #expect(!model.canRequest)
        await model.request()
        #expect(await repository.requestCount == 0)
        observer.cancel()
        await observer.value
    }

    @Test("재진입 시 완료 결과를 GET으로 갱신하고 자동 요청하지 않는다")
    func reentryFetchesCompletion() async {
        let repository = FeedReviewProbe(status: .processing)
        let model = FeedAIReviewViewModel(postID: "post", repository: repository, sleep: { _ in
            try await Task.sleep(for: .seconds(3600))
        })
        let first = Task { await model.observe() }
        await wait { !model.isLoading }
        first.cancel()
        await first.value
        await repository.setStatus(.completed)
        let second = Task { await model.observe() }
        await wait { model.review?.status == .completed }
        #expect(model.buttonTitle == "AI 피드백 보기")
        #expect(await repository.fetchCount == 2)
        #expect(await repository.requestCount == 0)
        second.cancel()
        await second.value
    }

    @Test("요청 응답을 기다리는 동안 중복 POST를 차단한다")
    func preventsConcurrentRequests() async {
        let repository = SuspendedFeedReviewProbe(suspendsFetch: false)
        let model = FeedAIReviewViewModel(postID: "post", repository: repository, sleep: { _ in
            try await Task.sleep(for: .seconds(3600))
        })
        let observer = Task { await model.observe() }
        await wait { !model.isLoading }
        let request = Task { await model.request() }
        for _ in 0..<1000 {
            if await repository.isRequestWaiting { break }
            await Task.yield()
        }
        #expect(await repository.isRequestWaiting)
        await model.request()
        #expect(await repository.requestCount == 1)
        await repository.finishRequest()
        await request.value
        #expect(model.review?.status == .processing)
        observer.cancel()
        await observer.value
    }

    @Test("화면을 나간 뒤 도착한 GET 응답을 무시한다")
    func discardsCancelledFetch() async {
        let repository = SuspendedFeedReviewProbe(suspendsFetch: true)
        let model = FeedAIReviewViewModel(postID: "post", repository: repository)
        let observer = Task { await model.observe() }
        for _ in 0..<1000 {
            if await repository.isFetchWaiting { break }
            await Task.yield()
        }
        #expect(await repository.isFetchWaiting)
        observer.cancel()
        await repository.finishFetch()
        await observer.value
        #expect(model.review == nil)
        #expect(!model.isLoading)
    }

    @Test("5분 이후 폴링 GET을 멈추고 재진입 시 GET을 재개한다")
    func timeoutAndReentry() async {
        let clock = FeedReviewTestClock()
        let repository = FeedReviewProbe(status: .processing)
        let sleeper = TimeoutFeedReviewSleep(clock: clock)
        let model = FeedAIReviewViewModel(postID: "post", repository: repository, now: { clock.date }, sleep: {
            try await sleeper.sleep($0)
        })
        let first = Task { await model.observe() }
        await wait { model.pollingTimedOut }
        #expect(await repository.fetchCount == 1)
        #expect(await sleeper.firstDuration == .seconds(3))
        first.cancel()
        await first.value
        let second = Task { await model.observe() }
        for _ in 0..<1000 {
            if await repository.fetchCount >= 2 { break }
            await Task.yield()
        }
        await wait { !model.isLoading }
        #expect(await repository.fetchCount == 2)
        #expect(!model.pollingTimedOut)
        second.cancel()
        await second.value
    }

    @Test("수동 갱신 이후 도착한 이전 폴링 응답은 완료 결과를 덮어쓰지 않는다")
    func latestStartedFetchWins() async {
        let repository = ReverseResponseFeedReviewProbe()
        let sleeper = ControlledFeedReviewSleep()
        let model = FeedAIReviewViewModel(postID: "post", repository: repository, sleep: {
            try await sleeper.sleep($0)
        })
        let observer = Task { await model.observe() }
        for _ in 0..<1000 {
            if await sleeper.isWaiting { break }
            await Task.yield()
        }
        #expect(await sleeper.isWaiting)
        await sleeper.advance()
        for _ in 0..<1000 {
            if await repository.fetchCount >= 2 { break }
            await Task.yield()
        }
        #expect(await repository.fetchCount == 2)
        let refresh = Task { await model.refresh() }
        for _ in 0..<1000 {
            if await repository.fetchCount >= 3 { break }
            await Task.yield()
        }
        #expect(await repository.fetchCount == 3)
        await repository.finishFetch(number: 3, status: .completed)
        await refresh.value
        #expect(model.review?.status == .completed)
        await repository.finishFetch(number: 2, status: .processing)
        for _ in 0..<1000 {
            if await sleeper.callCount >= 2 { break }
            await Task.yield()
        }
        #expect(await sleeper.callCount >= 2)
        #expect(model.review?.status == .completed)
        observer.cancel()
        await observer.value
    }

    @Test("이전 날짜는 완료된 결과만 표시하고 새 분석이나 재요청을 허용하지 않는다", arguments: [AIReviewStatus.none, .processing, .failed, .completed])
    func pastPostOnlyShowsExistingResult(status: AIReviewStatus) async {
        let repository = FeedReviewProbe(status: status, canRequestAgain: true)
        let model = FeedAIReviewViewModel(
            postID: "post", repository: repository, topicDate: .distantPast,
            sleep: { _ in try await Task.sleep(for: .seconds(3600)) }
        )
        #expect(!model.isButtonVisible)
        let observer = Task { await model.observe() }
        await wait { !model.isLoading }
        #expect(model.isButtonVisible == (status == .completed))
        #expect(!model.canRequest)
        await model.request()
        #expect(await repository.requestCount == 0)
        observer.cancel()
        await observer.value
    }

    private func wait(_ condition: () -> Bool) async {
        for _ in 0..<1000 {
            if condition() { return }
            await Task.yield()
        }
        Issue.record("AI 상태 갱신 시간 초과")
    }
}

private actor FeedReviewProbe: AIReviewRepository {
    var status: AIReviewStatus
    let canRequestAgain: Bool
    private(set) var requestCount = 0
    private(set) var fetchCount = 0

    init(status: AIReviewStatus, canRequestAgain: Bool = true) {
        self.status = status
        self.canRequestAgain = canRequestAgain
    }

    func setStatus(_ status: AIReviewStatus) { self.status = status }
    func fetchReview(postID: String) async throws -> AIReview {
        fetchCount += 1
        return review()
    }
    func requestReview(postID: String) async throws -> AIReview {
        requestCount += 1
        status = .processing
        return review()
    }
    private func review() -> AIReview {
        AIReview(status: status, requestedAt: nil, completedAt: nil, canRequestAgain: canRequestAgain)
    }
}

private actor SuspendedFeedReviewProbe: AIReviewRepository {
    let suspendsFetch: Bool
    private var fetchContinuation: CheckedContinuation<AIReview, Never>?
    private var requestContinuation: CheckedContinuation<AIReview, Never>?
    private(set) var requestCount = 0
    var isFetchWaiting: Bool { fetchContinuation != nil }
    var isRequestWaiting: Bool { requestContinuation != nil }

    init(suspendsFetch: Bool) { self.suspendsFetch = suspendsFetch }
    func fetchReview(postID: String) async throws -> AIReview {
        if suspendsFetch {
            return await withCheckedContinuation { fetchContinuation = $0 }
        }
        return AIReview(status: .none, requestedAt: nil, completedAt: nil, canRequestAgain: true)
    }
    func requestReview(postID: String) async throws -> AIReview {
        requestCount += 1
        return await withCheckedContinuation { requestContinuation = $0 }
    }
    func finishFetch() {
        fetchContinuation?.resume(returning: AIReview(status: .completed, requestedAt: nil, completedAt: nil, canRequestAgain: false))
        fetchContinuation = nil
    }
    func finishRequest() {
        requestContinuation?.resume(returning: AIReview(status: .processing, requestedAt: nil, completedAt: nil, canRequestAgain: false))
        requestContinuation = nil
    }
}

@MainActor
private final class FeedReviewTestClock {
    var date = Date(timeIntervalSince1970: 0)
}

private actor TimeoutFeedReviewSleep {
    let clock: FeedReviewTestClock
    private(set) var firstDuration: Duration?
    init(clock: FeedReviewTestClock) { self.clock = clock }
    func sleep(_ duration: Duration) async throws {
        if firstDuration == nil {
            firstDuration = duration
            await MainActor.run { clock.date = clock.date.addingTimeInterval(301) }
        } else {
            try await Task.sleep(for: .seconds(3600))
        }
    }
}

private actor ReverseResponseFeedReviewProbe: AIReviewRepository {
    private(set) var fetchCount = 0
    private var pending: [Int: CheckedContinuation<AIReview, Never>] = [:]
    func fetchReview(postID: String) async throws -> AIReview {
        fetchCount += 1
        let number = fetchCount
        if number == 1 {
            return AIReview(status: .processing, requestedAt: nil, completedAt: nil, canRequestAgain: false)
        }
        return await withCheckedContinuation { pending[number] = $0 }
    }
    func requestReview(postID: String) async throws -> AIReview { fatalError("Unexpected POST") }
    func finishFetch(number: Int, status: AIReviewStatus) {
        pending.removeValue(forKey: number)?.resume(returning: AIReview(
            status: status, requestedAt: nil, completedAt: nil, canRequestAgain: false
        ))
    }
}

private actor ControlledFeedReviewSleep {
    private var pending: CheckedContinuation<Void, Never>?
    private(set) var callCount = 0
    var isWaiting: Bool { pending != nil }
    func sleep(_ duration: Duration) async throws {
        callCount += 1
        if callCount == 1 {
            await withCheckedContinuation { pending = $0 }
        } else {
            try await Task.sleep(for: .seconds(3600))
        }
    }
    func advance() {
        pending?.resume()
        pending = nil
    }
}
