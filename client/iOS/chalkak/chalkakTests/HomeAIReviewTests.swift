import Foundation
import Testing
@testable import chalkak

@MainActor
struct HomeAIReviewTests {
    @Test("홈 복귀 시 같은 게시물의 완료 결과를 재조회 없이 유지한다")
    func completedReviewSurvivesReentry() async {
        let repository = ReviewRepositoryProbe(fetches: [.value(Self.review(.completed))])
        let sleep = ReviewSleepProbe()
        let model = Self.model(repository, sleep: sleep)
        let observer = Task { await model.observeAIReview(postID: "post") }
        await sleep.waitForCalls(1)
        observer.cancel()
        await observer.value

        await model.observeAIReview(postID: "post")

        #expect(model.viewState.aiReview.review == Self.review(.completed))
        #expect(!model.viewState.aiReview.isLoading)
        #expect(await repository.fetchIDs == ["post"])
        #expect(await sleep.callCount == 1)
    }

    @Test("홈 복귀 시 처리 중 결과를 표시한 채 상태 조회를 재개한다")
    func processingReviewSurvivesReentry() async {
        let gate = ReviewResponseGate()
        let repository = ReviewRepositoryProbe(fetches: [
            .value(Self.review(.processing)), .suspended(gate)
        ])
        let sleep = ReviewSleepProbe()
        let model = Self.model(repository, sleep: sleep)
        let observer = Task { await model.observeAIReview(postID: "post") }
        await sleep.waitForCalls(1)
        observer.cancel()
        await observer.value

        let resumed = Task { await model.observeAIReview(postID: "post") }
        await gate.waitUntilStarted()
        #expect(model.viewState.aiReview.review?.status == .processing)
        #expect(!model.viewState.aiReview.isLoading)
        await gate.complete(Self.review(.completed))
        await sleep.waitForCalls(2)
        #expect(model.viewState.aiReview.review?.status == .completed)
        #expect(await repository.fetchIDs == ["post", "post"])
        resumed.cancel()
        await resumed.value
    }

    @Test("게시물이 달라지면 이전 완료 결과를 지우고 새 결과를 조회한다")
    func completedReviewDoesNotLeakToAnotherPost() async {
        let gate = ReviewResponseGate()
        let repository = ReviewRepositoryProbe(fetches: [
            .value(Self.review(.completed)), .suspended(gate)
        ])
        let sleep = ReviewSleepProbe()
        let model = Self.model(repository, sleep: sleep)
        let observer = Task { await model.observeAIReview(postID: "old") }
        await sleep.waitForCalls(1)
        observer.cancel()
        await observer.value

        let current = Task { await model.observeAIReview(postID: "new") }
        await gate.waitUntilStarted()
        #expect(model.viewState.aiReview.review == nil)
        #expect(model.viewState.aiReview.isLoading)
        await gate.complete(Self.review(.none))
        await sleep.waitForCalls(2)
        #expect(model.viewState.aiReview.review?.status == AIReviewStatus.none)
        #expect(await repository.fetchIDs == ["old", "new"])
        current.cancel()
        await current.value
    }

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

    @Test("반복 폴링 실패는 메시지 없이 재시도하고 복구 후 완료된다")
    func pollingFailuresAreSilent() async {
        let repository = ReviewRepositoryProbe(fetches: [
            .value(Self.review(.processing)),
            .failure(.network),
            .failure(.http(statusCode: 500, message: "서버 오류")),
            .value(Self.review(.completed))
        ])
        let sleep = ReviewSleepProbe()
        let model = Self.model(repository, sleep: sleep)
        let observer = Task { await model.observeAIReview(postID: "post") }
        await sleep.waitForCalls(1)
        for checkpoint in 2...3 {
            await sleep.advance()
            await sleep.waitForCalls(checkpoint)
            #expect(model.viewState.aiReview.message == nil)
            #expect(model.viewState.aiReview.review?.status == .processing)
            #expect(!model.viewState.aiReview.isUnavailable)
            model.dismissAIReviewMessage()
        }
        await sleep.advance()
        await sleep.waitForCalls(4)
        #expect(model.viewState.aiReview.review?.status == .completed)
        #expect(model.viewState.aiReview.message == nil)
        #expect(await repository.fetchIDs.count == 4)
        observer.cancel()
        await observer.value
    }

    @Test("첫 조회 실패는 오류 메시지를 표시한다")
    func initialFailureShowsMessage() async {
        let repository = ReviewRepositoryProbe(fetches: [.failure(.network)])
        let sleep = ReviewSleepProbe()
        let model = Self.model(repository, sleep: sleep)
        let observer = Task { await model.observeAIReview(postID: "post") }
        await sleep.waitForCalls(1)
        #expect(model.viewState.aiReview.message != nil)
        #expect(!model.viewState.aiReview.isLoading)
        observer.cancel()
        await observer.value
    }

    @Test("폴링 중 404는 메시지 없이 조회 불가 상태로 바꾸고 추가 조회를 막는다")
    func pollingNotFoundStopsFetching() async {
        let repository = ReviewRepositoryProbe(fetches: [
            .value(Self.review(.processing)),
            .failure(.http(statusCode: 404, message: nil))
        ])
        let sleep = ReviewSleepProbe()
        let model = Self.model(repository, sleep: sleep)
        let observer = Task { await model.observeAIReview(postID: "post") }
        await sleep.waitForCalls(1)
        await sleep.advance()
        await sleep.waitForCalls(2)
        #expect(model.viewState.aiReview.isUnavailable)
        #expect(model.viewState.aiReview.message == nil)
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

    @Test("오늘 사진을 올리기 전이면 업로드 전 안내 카드를 보여 준다")
    func beforeUploadShowsGuidanceCard() async {
        // Given 로그인, AI 리뷰 켜짐, 오늘 올린 사진 없음
        // When  홈을 불러온다
        // Then  피드백 카드가 업로드 전 안내 상태이고 AI 리뷰 조회는 0번
        let repository = ReviewRepositoryProbe(fetches: [])
        let hero = HeroLoaderProbe()
        let model = Self.homeModel(hero, repository: repository)

        await model.resume()

        #expect(model.viewState.hero == .empty)
        #expect(model.feedbackPhase == .beforeUpload)
        #expect(model.feedbackPostID == nil)
        #expect(await repository.fetchIDs.isEmpty)
    }

    @Test("오늘 사진이 승인 대기면 승인 대기 안내 카드를 보여 준다")
    func pendingPhotoShowsAwaitingApprovalCard() async {
        // Given 로그인, AI 리뷰 켜짐, 오늘 사진이 PENDING
        // When  홈을 불러온다
        // Then  피드백 카드가 승인 대기 안내 상태이고 AI 리뷰 조회는 0번
        let repository = ReviewRepositoryProbe(fetches: [])
        let hero = HeroLoaderProbe()
        hero.todayStatus = .pending
        let model = Self.homeModel(hero, repository: repository)

        await model.resume()

        #expect(model.feedbackPhase == .awaitingApproval)
        #expect(model.feedbackPostID == nil)
        #expect(await repository.fetchIDs.isEmpty)
    }

    @Test("업로드 직후 검사 중이어도 승인 대기 안내 카드를 보여 준다")
    func validatingPhotoShowsAwaitingApprovalCard() async {
        // Given 로그인, AI 리뷰 켜짐, 오늘 사진이 VALIDATING
        // When  홈을 불러온다
        // Then  피드백 카드가 승인 대기 안내 상태
        let repository = ReviewRepositoryProbe(fetches: [])
        let hero = HeroLoaderProbe()
        hero.postStatus = .success(PhotoUploadTodayPostStatus(
            topicDate: Self.heroDate, isPosted: true, postID: "post", moderationStatus: .validating
        ))
        let model = Self.homeModel(hero, repository: repository)

        await model.resume()

        #expect(model.viewState.hero == .processing)
        #expect(model.feedbackPhase == .awaitingApproval)
        #expect(model.feedbackPostID == nil)
        #expect(await repository.fetchIDs.isEmpty)
    }

    @Test("안내 카드에서 피드백 시작을 보내도 AI 리뷰를 요청하지 않는다")
    func guidanceCardDoesNotRequestReview() async {
        // Given 피드백 카드가 업로드 전 안내 상태
        // When  피드백 시작을 보낸다
        // Then  AI 리뷰 요청(POST)은 0번이고 카드는 업로드 전 안내 상태 그대로
        let repository = ReviewRepositoryProbe(fetches: [], requests: [.value(Self.review(.processing))])
        let hero = HeroLoaderProbe()
        let model = Self.homeModel(hero, repository: repository)
        await model.resume()
        #expect(model.feedbackPhase == .beforeUpload)

        #expect(model.feedbackTapAction == .none)
        await model.requestAIReview()

        #expect(await repository.requestIDs.isEmpty)
        #expect(model.feedbackPhase == .beforeUpload)
    }

    @Test("승인되면 안내 대신 피드백 받기 카드로 바뀐다")
    func approvalSwitchesGuidanceToReadyCard() async {
        // Given 오늘 사진이 PENDING이라 승인 대기 안내가 보이는 상태
        // When  사진이 승인된 뒤 홈을 새로고침하고 AI 리뷰 조회가 NONE
        // Then  피드백 카드가 "AI에게 피드백 받아보기"(요청 가능) 상태
        let repository = ReviewRepositoryProbe(fetches: [.value(Self.review(.none))])
        let sleep = ReviewSleepProbe()
        let hero = HeroLoaderProbe()
        hero.todayStatus = .pending
        let model = Self.homeModel(hero, repository: repository, sleep: sleep)
        await model.resume()
        #expect(model.feedbackPhase == .awaitingApproval)

        hero.todayStatus = .approved
        await model.refresh()
        #expect(model.feedbackPostID == HeroLoaderProbe.postID)
        let observer = Task { await model.observeAIReview(postID: HeroLoaderProbe.postID) }
        await sleep.waitForCalls(1)

        #expect(model.feedbackPhase == .ready)
        #expect(model.feedbackTapAction == .presentStartSheet)
        #expect(await repository.fetchIDs == [HeroLoaderProbe.postID])
        observer.cancel()
        await observer.value
    }

    @Test("비로그인이거나 AI 리뷰가 꺼져 있으면 사진 올리기 전에도 카드를 숨긴다")
    func guestOrDisabledReviewHidesCard() async {
        // Given 오늘 올린 사진 없음, (a) 비로그인 / (b) AI 리뷰 꺼짐
        // When  홈을 불러온다
        // Then  피드백 카드가 숨김
        let guest = Self.homeModel(HeroLoaderProbe(), repository: ReviewRepositoryProbe(fetches: []), isAuthenticated: false)
        await guest.resume()
        #expect(guest.viewState.hero == .loginRequired)
        #expect(guest.feedbackPhase == nil)

        let disabled = Self.homeModel(HeroLoaderProbe(), repository: nil)
        await disabled.resume()
        #expect(disabled.viewState.hero == .empty)
        #expect(disabled.feedbackPhase == nil)
    }

    @Test("반려·주제 없음·불러오기 실패에는 카드를 숨긴다")
    func rejectedNoTopicOrErrorHidesCard() async {
        // Given 오늘 히어로가 (a) 반려(iOS만) / (b) 열린 주제 없음 / (c) 오늘 상태 조회 실패
        // When  홈을 불러온다
        // Then  피드백 카드가 숨김
        var rejectedState = HomeViewState(date: Self.heroDate)
        rejectedState.isAuthenticated = true
        for canResubmit in [true, false] {
            rejectedState.hero = .rejected(canResubmit: canResubmit)
            // 반려 히어로는 홈 로딩이 만들지 않고 외부에서 정해 주므로, 그 상태 그대로 본다.
            let rejected = HomeViewModel(
                initialState: rejectedState,
                loader: HeroLoaderProbe().loader,
                isAuthenticated: { true },
                aiReviewRepository: ReviewRepositoryProbe(fetches: [])
            )
            #expect(rejected.feedbackPhase == nil)
        }

        let noTopicLoader = HeroLoaderProbe()
        noTopicLoader.postStatus = .failure(PhotoUploadAPIError.http(statusCode: 404, message: nil))
        let noTopic = Self.homeModel(noTopicLoader, repository: ReviewRepositoryProbe(fetches: []))
        await noTopic.resume()
        #expect(noTopic.viewState.hero == .noOpenTopic)
        #expect(noTopic.feedbackPhase == nil)

        let errorLoader = HeroLoaderProbe()
        errorLoader.postStatus = .failure(AIReviewError.network)
        let failed = Self.homeModel(errorLoader, repository: ReviewRepositoryProbe(fetches: []))
        await failed.resume()
        #expect(failed.viewState.hero == .error)
        #expect(failed.feedbackPhase == nil)
    }

    @Test("시간 초과 뒤 업로드 전 안내로 바뀌면 시간 초과 문구를 보여 주지 않는다")
    func guidanceCardHidesStaleTimeout() async {
        // Given 승인된 오늘 사진의 AI 리뷰가 5분 넘게 PROCESSING이라 시간 초과 문구가 보이는 상태
        // When  홈을 새로고침했더니 오늘 올린 사진이 없다(날짜가 바뀜 등)
        // Then  카드는 업로드 전 안내이고 시간 초과 문구는 보이지 않는다
        let clock = ReviewClock()
        let repository = ReviewRepositoryProbe(fetches: [.value(Self.review(.processing))])
        let sleep = ReviewSleepProbe()
        let hero = HeroLoaderProbe()
        hero.todayStatus = .approved
        let model = Self.homeModel(hero, repository: repository, sleep: sleep, now: { clock.now })
        await model.resume()
        let observer = Task { await model.observeAIReview(postID: HeroLoaderProbe.postID) }
        await sleep.waitForCalls(1)
        clock.now = clock.now.addingTimeInterval(300)
        await sleep.advance()
        await sleep.waitForCalls(2)
        #expect(model.feedbackPhase == .processing)
        #expect(model.showsFeedbackPollingTimeout)
        observer.cancel()
        await observer.value

        hero.todayStatus = nil
        await model.refresh()

        #expect(model.feedbackPhase == .beforeUpload)
        #expect(!model.showsFeedbackPollingTimeout)
    }

    /// 안내 카드 TC의 오늘 날짜. 한 주(9/6–9/12)가 한 달 안에 있어 달력 조회가 한 번이다.
    private static let heroDate = RecordMonth.calendar.date(from: DateComponents(year: 2026, month: 9, day: 9))!

    /// `HeroLoaderProbe`로 오늘 히어로를 정해 홈을 불러오는 모델. `repository`가 nil이면 AI 리뷰 꺼짐이다.
    private static func homeModel(
        _ hero: HeroLoaderProbe,
        repository: ReviewRepositoryProbe?,
        isAuthenticated: Bool = true,
        sleep: ReviewSleepProbe = ReviewSleepProbe(),
        now: @escaping @MainActor () -> Date = { Date(timeIntervalSince1970: 1_000) }
    ) -> HomeViewModel {
        let today = heroDate
        return HomeViewModel(
            initialState: HomeViewState(date: today),
            loader: hero.loader,
            dateProvider: { today },
            isAuthenticated: { isAuthenticated },
            aiReviewRepository: repository,
            aiReviewNow: now,
            aiReviewSleep: { _ in try await sleep.sleep() }
        )
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

/// 홈 로딩이 오늘 히어로를 어떤 상태로 만들지 정하는 가짜 로더.
/// - `todayStatus`가 nil이면 달력에 오늘 기록이 없고, `postStatus`로 업로드 전(.empty)·검사 중(.processing)·주제 없음·실패를 정한다.
/// - `todayStatus`가 있으면 달력에 그 상태의 오늘 기록(`postID`)이 있다. 상세 조회는 실패시켜 달력 썸네일로 표시한다.
@MainActor
private final class HeroLoaderProbe {
    static let postID = "today-post"
    var todayStatus: RecordPostStatus?
    var postStatus: Result<PhotoUploadTodayPostStatus, Error> = .success(PhotoUploadTodayPostStatus(
        topicDate: Date(timeIntervalSince1970: 0), isPosted: false, postID: nil, moderationStatus: nil
    ))

    nonisolated var loader: HomeDataLoader {
        HomeDataLoader(
            today: { date in
                DisplayContent(topicDate: date, topic: "주제", page: DisplayPage(photos: [], currentPage: 1, hasNext: false, randomSeed: nil))
            },
            ranking: { _ in [] },
            calendar: { month in await self.calendar(month) },
            detail: { _ in throw AIReviewError.network },
            banners: { page, pageSize in BannerPage(currentPage: page, pageSize: pageSize, hasNext: false, banners: []) },
            postStatus: { try await self.postStatus.get() }
        )
    }

    private func calendar(_ month: RecordMonth) -> RecordCalendar {
        guard let todayStatus else { return RecordCalendar(month: month, posts: []) }
        let date = RecordMonth.calendar.date(from: DateComponents(year: 2026, month: 9, day: 9))!
        return RecordCalendar(month: month, posts: [RecordPost(
            postId: Self.postID,
            topicDate: date,
            thumbnailImageSource: .remote(URL(string: "https://example.com/today-thumb.webp")!),
            status: todayStatus
        )])
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
