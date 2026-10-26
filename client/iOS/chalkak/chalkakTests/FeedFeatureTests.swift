import SwiftUI
import Foundation
import CoreGraphics
import Testing
@testable import chalkak

struct FeedBackSwipeTrackingTests {
    @Test("뒤로 드래그 중 상하 움직임에 섞인 가로 흔들림은 축소 진행도를 바꾸지 않는다")
    func verticalMovementDoesNotChangeDistance() {
        var swipe = FeedBackSwipeTracking()
        swipe.update(to: CGSize(width: 60, height: 0))
        for translation in [
            CGSize(width: 63, height: 20),
            CGSize(width: 59, height: 45),
            CGSize(width: 62, height: 10),
            CGSize(width: 58, height: -20)
        ] {
            swipe.update(to: translation)
            #expect(swipe.distance == 60)
        }
        // 가로 드래그를 다시 시작해도 무시했던 가로 흔들림이 뒤늦게 반영되지 않는다.
        swipe.update(to: CGSize(width: 78, height: -18))
        #expect(swipe.distance == 80)
    }

    @Test("느린 가로 드래그와 반대 방향 이동은 계속 반영한다")
    func horizontalMovementCanContinueAndReverse() {
        var swipe = FeedBackSwipeTracking()
        for x in stride(from: CGFloat(0.5), through: 20, by: 0.5) {
            swipe.update(to: CGSize(width: x, height: x / 10))
        }
        #expect(swipe.distance == 20)
        swipe.update(to: CGSize(width: 5, height: 2))
        #expect(swipe.distance == 5)
        swipe.update(to: CGSize(width: -10, height: 2))
        #expect(swipe.distance == 0)
    }

    @Test("세로 방향으로 손을 떼는 속도는 뒤로가기 완료 거리로 계산하지 않는다")
    func verticalReleaseDoesNotProjectHorizontalNoise() {
        var swipe = FeedBackSwipeTracking()
        swipe.update(to: CGSize(width: 80, height: 0))
        swipe.update(to: CGSize(width: 100, height: 100))
        #expect(swipe.projectedDistance(to: CGSize(width: 160, height: 300)) == 80)
        #expect(swipe.projectedDistance(to: CGSize(width: 160, height: 102)) == 140)
    }

    @Test("세로 방향으로 손을 떼는 속도는 이어받을 가로 속도로 쓰지 않는다")
    func verticalReleaseHasNoHorizontalVelocity() {
        let swipe = FeedBackSwipeTracking()
        #expect(swipe.horizontalVelocity(CGSize(width: 600, height: 100)) == 600)
        #expect(swipe.horizontalVelocity(CGSize(width: -300, height: 50)) == -300)
        #expect(swipe.horizontalVelocity(CGSize(width: 200, height: -500)) == 0)
    }
}

struct FeedBackSwipeReleaseTests {
    @Test("카드에 가까울수록 마무리 시간이 짧고 최대 0.3초 안에 끝난다")
    func finishesSoonerNearTarget() {
        let far = FeedBackSwipeRelease.timing(from: 0.8, to: 0, velocity: 0, duration: 0.3)
        let near = FeedBackSwipeRelease.timing(from: 0.05, to: 0, velocity: 0, duration: 0.3)
        #expect(near.duration < far.duration)
        #expect(near.duration >= 0.08)
        #expect(far.duration <= 0.3)
    }

    @Test("일반적인 놓기 속도를 곡선 시작 속도로 이어받는다")
    func preservesReleaseVelocity() {
        let timing = FeedBackSwipeRelease.timing(from: 0.5, to: 0, velocity: -2, duration: 0.3)
        let actualVelocity = -0.5 * timing.initialSlope / timing.duration
        #expect(abs(actualVelocity - (-2)) < 0.0001)
    }

    @Test("빠른 놓기와 역방향 놓기 모두 목표를 지나치거나 되돌아가지 않는다")
    func releaseCurveIsMonotonic() {
        for target: CGFloat in [0, 1] {
            for velocity: CGFloat in [-20, -2, 0, 2, 20] {
                let timing = FeedBackSwipeRelease.timing(from: 0.5, to: target, velocity: velocity, duration: 0.3)
                let controlY = timing.initialSlope / 3
                var previous = 0.0
                for step in 0...100 {
                    let t = Double(step) / 100
                    let y = 3 * (1 - t) * (1 - t) * t * controlY + 3 * (1 - t) * t * t + t * t * t
                    #expect(y >= previous - 0.000001)
                    #expect(y >= 0 && y <= 1)
                    previous = y
                }
                #expect(previous == 1)
            }
        }
    }

    @Test("이미 카드에 도착했으면 추가 애니메이션을 기다리지 않는다")
    func noDelayAtTarget() {
        let timing = FeedBackSwipeRelease.timing(from: 0, to: 0, velocity: -3, duration: 0.3)
        #expect(timing.duration == 0)
        #expect(timing.initialSlope == 0)
    }
}

@MainActor
struct FeedViewStateTests {
    @Test("기본 상태는 로딩이며 좋아요가 비활성화된다")
    func defaultsToLoading() {
        let state = FeedViewState()

        #expect(state.contentStatus == .loading)
        #expect(state.content == nil)
        #expect(!state.isLikeEnabled)
    }
}

@MainActor
@Suite(.serialized)
struct FeedViewModelTests {
    @Test("상세 조회 중 완료된 좋아요의 성공 응답을 상세 응답이 덮어쓰지 않는다")
    func detailDoesNotOverwriteInFlightLike() async throws {
        let gate = AsyncGate()
        defer { gate.release() }
        // 낙관적 값(11)·좋아요 성공 응답 값(42)·상세 응답 값(10)을 모두 다르게 둬서
        // '좋아요 성공 응답이 반영된 뒤 상세가 덮어쓰지 않는지'를 명확히 검증한다.
        let viewModel = FeedViewModel(
            postID: "post-1",
            seed: Self.content(isLiked: false, likeCount: 10),
            isLikeConfirmed: true,
            detailHandler: { _ in
                await gate.waitForRelease()
                // 상세 응답은 좋아요 이전 상태(미선택/10)를 담고 있다.
                return .success(Self.content(isLiked: false, likeCount: 10, topic: "갱신됨"))
            },
            likeHandler: { _, isLiked in
                .success(FeedLikeUpdate(postID: "post-1", isLiked: isLiked, likeCount: 42))
            }
        )

        let loadTask = Task { await viewModel.load() }
        await gate.waitUntilEntered()

        // 상세 조회가 진행 중인 사이 좋아요를 누른다(낙관적: 선택됨/11).
        viewModel.toggleLike()
        #expect(viewModel.viewState.content?.post.isLiked == true)
        #expect(viewModel.viewState.content?.post.likeCount == 11)

        // 좋아요 성공 응답(선택됨/42)이 반영될 때까지 기다린 뒤에야 상세 응답을 푼다.
        try await waitUntil { viewModel.viewState.content?.post.likeCount == 42 }
        #expect(viewModel.viewState.content?.post.isLiked == true)
        #expect(viewModel.viewState.content?.post.likeCount == 42)

        // 상세 응답이 도착해도 좋아요 성공 값(선택됨/42)이 유지되어야 한다.
        gate.release()
        await loadTask.value

        #expect(viewModel.viewState.contentStatus == .loaded)
        #expect(viewModel.viewState.content?.post.isLiked == true)
        #expect(viewModel.viewState.content?.post.likeCount == 42)
        #expect(viewModel.viewState.content?.topic == "갱신됨")
    }

    @Test("상세 응답에 없는 작성자 ID와 사인 썸네일은 전시 시드 값을 유지한다")
    func detailKeepsSeedAuthorAndSignatureThumbnail() async {
        var seed = Self.content(isLiked: false, likeCount: 3)
        seed.post.authorID = "author-1"
        seed.post.signatureThumbnailImageSource = .asset("preview_signature_thumbnail")
        let viewModel = FeedViewModel(
            postID: "post-1",
            seed: seed,
            isLikeConfirmed: true,
            detailHandler: { _ in .success(Self.content(isLiked: false, likeCount: 3)) }
        )

        await viewModel.load()

        #expect(viewModel.viewState.content?.post.authorID == "author-1")
        #expect(viewModel.viewState.content?.post.signatureThumbnailImageSource == .asset("preview_signature_thumbnail"))
    }

    @Test("피드에서 사인을 누르면 작성자 ID와 사인 썸네일로 작가 사진 목록을 연다")
    func signatureTapOpensAuthorPosts() {
        var seed = Self.content(isLiked: false, likeCount: 3)
        seed.post.authorID = "author-1"
        seed.post.signatureThumbnailImageSource = .asset("preview_signature_thumbnail")
        let viewModel = FeedViewModel(postID: "post-1", seed: seed)

        #expect(viewModel.signatureTapAction() == .openAuthorPosts(
            AuthorPostsTarget(
                authorID: "author-1",
                signatureImageSource: .asset("preview_signature_thumbnail"),
                isMine: false
            )
        ))
    }

    @Test("상세에서 연 피드는 썸네일이 없어 원본 사인을, 내 게시물이면 내 목록으로 연다")
    func signatureTapFallsBackToOriginalSignature() {
        var seed = Self.content(isLiked: false, likeCount: 3, isOwnedByCurrentUser: true)
        seed.post.authorID = "me"
        let viewModel = FeedViewModel(postID: "post-1", seed: seed)

        #expect(viewModel.signatureTapAction() == .openAuthorPosts(
            AuthorPostsTarget(authorID: "me", signatureImageSource: .asset("preview_signature"), isMine: true)
        ))
    }

    @Test("작가 사진 목록에서 연 피드의 같은 작가 사인은 목록으로 돌아가기다")
    func signatureTapFromAuthorPostsReturns() {
        var seed = Self.content(isLiked: false, likeCount: 3)
        seed.post.authorID = "author-1"
        let fromList = FeedViewModel(postID: "post-1", seed: seed, sourceAuthorID: "author-1")
        let fromOtherList = FeedViewModel(postID: "post-1", seed: seed, sourceAuthorID: "author-2")

        #expect(fromList.signatureTapAction() == .returnToAuthorPosts)
        #expect(fromOtherList.signatureTapAction() == .openAuthorPosts(
            AuthorPostsTarget(authorID: "author-1", signatureImageSource: .asset("preview_signature"), isMine: false)
        ))
    }

    @Test("작성자 ID를 모르면 사인을 눌러도 아무것도 하지 않는다")
    func signatureTapWithoutAuthorDoesNothing() {
        let withoutAuthor = FeedViewModel(postID: "post-1", seed: Self.content(isLiked: false, likeCount: 3))
        let loading = FeedViewModel(postID: "post-1")

        #expect(withoutAuthor.signatureTapAction() == nil)
        #expect(loading.signatureTapAction() == nil)
    }

    @Test("목록에서 여는 FeedTarget의 출발 작가 ID가 피드 뷰모델까지 전달된다")
    func feedTargetPassesSourceAuthor() {
        var seed = Self.content(isLiked: false, likeCount: 3)
        seed.post.authorID = "author-1"
        var target = FeedTarget(seed: seed, isLikeConfirmed: true, zoomSource: .authorGrid("post-1"))
        target.sourceAuthorID = "author-1"

        let viewModel = FeedViewModel(target: target, apiClient: FeedAPIClient())

        #expect(target.zoomSource?.viewport == .authorPosts)
        #expect(viewModel.signatureTapAction() == .returnToAuthorPosts)
    }

    @Test("전시 시드는 상세 확정 전까지 좋아요를 막고, 확정 후 허용한다")
    func displaySeedGatesLikeUntilLoaded() async {
        let viewModel = FeedViewModel(
            postID: "post-1",
            seed: Self.content(isLiked: false, likeCount: 3),
            isLikeConfirmed: false,
            detailHandler: { _ in .success(Self.content(isLiked: true, likeCount: 5)) }
        )

        #expect(!viewModel.viewState.isLikeEnabled)

        // 비활성 상태에서는 좋아요가 반영되지 않는다.
        viewModel.toggleLike()
        #expect(viewModel.viewState.content?.post.isLiked == false)
        #expect(viewModel.viewState.content?.post.likeCount == 3)

        await viewModel.load()

        #expect(viewModel.viewState.isLikeEnabled)
        #expect(viewModel.viewState.content?.post.isLiked == true)
        #expect(viewModel.viewState.content?.post.likeCount == 5)
    }

    @Test("내 게시물만 삭제 요청을 보내고 완료 상태를 기록한다")
    func deletesOnlyOwnedPost() async throws {
        var requestedPostID: String?
        let ownedViewModel = FeedViewModel(
            postID: "post-1",
            seed: Self.content(isLiked: false, likeCount: 3, isOwnedByCurrentUser: true),
            isLikeConfirmed: true,
            deleteHandler: { postID in
                requestedPostID = postID
                return .success(())
            }
        )

        ownedViewModel.deletePost()
        try await waitUntil { ownedViewModel.viewState.deletedPostID == "post-1" }

        #expect(requestedPostID == "post-1")
        #expect(ownedViewModel.viewState.deletedPostID == "post-1")
        #expect(!ownedViewModel.viewState.isDeleting)

        let otherViewModel = FeedViewModel(
            postID: "post-2",
            seed: Self.content(isLiked: false, likeCount: 3, isOwnedByCurrentUser: false),
            isLikeConfirmed: true,
            deleteHandler: { _ in
                Issue.record("다른 사용자의 게시물은 삭제 요청을 보내면 안 됩니다")
                return .success(())
            }
        )

        otherViewModel.deletePost()

        #expect(otherViewModel.viewState.deletedPostID == nil)
        #expect(!otherViewModel.viewState.isDeleting)
    }

    @Test("삭제 실패를 사용자에게 알리고 다시 시도할 수 있다")
    func deleteFailurePublishesMessageAndAllowsRetry() async throws {
        var attemptCount = 0
        let viewModel = FeedViewModel(
            postID: "post-1",
            seed: Self.content(isLiked: false, likeCount: 3, isOwnedByCurrentUser: true),
            isLikeConfirmed: true,
            deleteHandler: { _ in
                attemptCount += 1
                return attemptCount == 1 ? .failure(.network) : .success(())
            }
        )

        viewModel.deletePost()
        try await waitUntil { viewModel.event == .showDeleteFailure(.network) }

        #expect(!viewModel.viewState.isDeleting)
        #expect(viewModel.viewState.deletedPostID == nil)
        #expect(viewModel.event == .showDeleteFailure(.network))

        viewModel.consumeEvent()
        #expect(viewModel.event == nil)

        viewModel.deletePost()
        try await waitUntil { viewModel.viewState.deletedPostID == "post-1" }

        #expect(attemptCount == 2)
        #expect(!viewModel.viewState.isDeleting)
        #expect(viewModel.viewState.deletedPostID == "post-1")
    }

    @Test("내 게시물 제목 수정은 공백을 제거하고 완료 상태를 반영한다")
    func updatesOnlyOwnedPostTitle() async throws {
        var requestedTitle: String?
        let viewModel = FeedViewModel(
            postID: "post-1",
            seed: Self.content(isLiked: false, likeCount: 3, isOwnedByCurrentUser: true),
            isLikeConfirmed: true,
            updateTitleHandler: { postID, title in
                #expect(postID == "post-1")
                requestedTitle = title
                return .success(FeedTitleUpdate(postID: postID, title: title))
            }
        )

        viewModel.updatePostTitle("  수정한 제목  ")
        try await waitUntil { viewModel.event == .showTitleUpdateSuccess }

        #expect(requestedTitle == "수정한 제목")
        #expect(viewModel.viewState.content?.post.title == "수정한 제목")
        #expect(!viewModel.viewState.isUpdatingTitle)

        let otherViewModel = FeedViewModel(
            postID: "post-2",
            seed: Self.content(isLiked: false, likeCount: 3, isOwnedByCurrentUser: false),
            isLikeConfirmed: true,
            updateTitleHandler: { _, _ in
                Issue.record("다른 사용자의 게시물은 제목 수정 요청을 보내면 안 됩니다")
                return .success(FeedTitleUpdate(postID: "post-2", title: "수정"))
            }
        )

        otherViewModel.updatePostTitle("수정 시도")

        #expect(otherViewModel.viewState.content?.post.title == "제목")
        #expect(otherViewModel.event == nil)
    }

    @Test("지난 주제의 내 게시물은 제목 수정 요청을 보내지 않는다")
    func refusesTitleUpdateForPastTopic() async {
        var didRequestUpdate = false
        let viewModel = FeedViewModel(
            postID: "post-1",
            seed: Self.content(
                isLiked: false,
                likeCount: 3,
                isOwnedByCurrentUser: true,
                topicDate: Date(timeIntervalSinceNow: -172_800)
            ),
            isLikeConfirmed: true,
            updateTitleHandler: { _, _ in
                didRequestUpdate = true
                return .success(FeedTitleUpdate(postID: "post-1", title: "수정"))
            }
        )

        viewModel.updatePostTitle("수정 시도")
        await Task.yield()

        #expect(!didRequestUpdate)
        #expect(!viewModel.viewState.isUpdatingTitle)
    }

    @Test("상세 응답이 늦게 도착해도 조회 중 수정한 제목을 유지한다")
    func detailLoadPreservesTitleUpdatedWhileRefreshing() async throws {
        let gate = AsyncGate()
        defer { gate.release() }
        let viewModel = FeedViewModel(
            postID: "post-1",
            seed: Self.content(isLiked: false, likeCount: 3, isOwnedByCurrentUser: true),
            isLikeConfirmed: true,
            detailHandler: { _ in
                await gate.waitForRelease()
                var detail = Self.content(isLiked: false, likeCount: 3, isOwnedByCurrentUser: true)
                detail.post.title = "상세 응답 제목"
                return .success(detail)
            },
            updateTitleHandler: { postID, title in
                .success(FeedTitleUpdate(postID: postID, title: title))
            }
        )

        let loadTask = Task { await viewModel.load() }
        await gate.waitUntilEntered()

        viewModel.updatePostTitle("수정한 제목")
        try await waitUntil { viewModel.viewState.titleUpdateVersion == 1 }

        #expect(viewModel.viewState.content?.post.title == "수정한 제목")

        gate.release()
        await loadTask.value

        #expect(viewModel.viewState.content?.post.title == "수정한 제목")
    }

    @Test("기록에서 들어온 FeedTarget은 상세 응답 후에도 내 게시물로 유지된다")
    func recordTargetKeepsOwnershipAfterDetailLoad() async {
        let target = FeedTarget(postID: "post-1", isOwnedByCurrentUser: true)
        let viewModel = FeedViewModel(
            postID: target.id,
            isOwnedByCurrentUser: target.isOwnedByCurrentUser,
            detailHandler: { _ in
                .success(Self.content(isLiked: false, likeCount: 3, isOwnedByCurrentUser: false))
            }
        )

        await viewModel.load()

        #expect(viewModel.viewState.content?.post.isOwnedByCurrentUser == true)
    }

    private static func content(
        isLiked: Bool,
        likeCount: Int,
        topic: String = "하늘",
        isOwnedByCurrentUser: Bool = false,
        topicDate: Date = Date()
    ) -> FeedContent {
        FeedContent(
            dateLabel: "8월 3일의 주제",
            topicDate: topicDate,
            topic: topic,
            post: FeedPost(
                id: "post-1",
                originalImageSource: .asset("preview_photo"),
                signatureImageSource: .asset("preview_signature"),
                contentDescription: "설명",
                title: "제목",
                likeCount: likeCount,
                isLiked: isLiked,
                isOwnedByCurrentUser: isOwnedByCurrentUser
            )
        )
    }
}

/// 핸들러의 진입과 완료 시점을 테스트에서 제어하기 위한 게이트.
@MainActor
private final class AsyncGate {
    private var releaseContinuation: CheckedContinuation<Void, Never>?
    private var enteredContinuation: CheckedContinuation<Void, Never>?
    private var isReleased = false
    private var hasEntered = false

    /// 핸들러가 호출됐음을 알리고, release()가 불릴 때까지 대기한다.
    func waitForRelease() async {
        hasEntered = true
        enteredContinuation?.resume()
        enteredContinuation = nil
        guard !isReleased else { return }
        await withCheckedContinuation { releaseContinuation = $0 }
    }

    /// 핸들러가 호출될 때까지 대기한다.
    func waitUntilEntered() async {
        guard !hasEntered else { return }
        await withCheckedContinuation { enteredContinuation = $0 }
    }

    func release() {
        isReleased = true
        releaseContinuation?.resume()
        releaseContinuation = nil
    }
}

@MainActor
struct FeedZoomCropTests {
    @Test("등록이 누락된 그리드 사진도 탭 좌표와 가림 영역을 즉시 전달한다")
    func tappedPhotoSuppliesSourceWithoutPriorRegistration() {
        let registry = FeedZoomRegistry()
        let sourceID = FeedZoomSource.displayGrid("selected-photo")
        let viewport = CGRect(x: 0, y: 200, width: 400, height: 600)
        registry.updateViewport(viewport, for: .display)
        registry.bottomBarFrame = CGRect(x: 20, y: 720, width: 360, height: 60)
        #expect(registry.source(for: sourceID) == nil)
        let frame = CGRect(x: 20, y: 600, width: 180, height: 240)
        let selected = registry.update(.init(frame: frame, cornerRadius: 8), for: sourceID)
        registry.remove(sourceID)
        #expect(selected.frame == frame)
        #expect(selected.visibleBounds == CGRect(x: 0, y: 200, width: 400, height: 520))
    }

    @Test("출발 viewport가 없는 기록 사진도 이동 경로에서 잘리지 않는다")
    func viewportFallbackIncludesSourcePhoto() {
        let rect = CGRect(x: 0, y: 0, width: 400, height: 300)
        let canvas = CGRect(x: 0, y: 200, width: 400, height: 300)
        let source = CGRect(x: 20, y: 700, width: 100, height: 100)
        let clip = FeedZoomViewportClip(
            progress: 0, source: nil, destination: canvas, canvas: canvas, sourcePhotoFrame: source
        )
        #expect(clip.path(in: rect).boundingRect == rect.union(source.offsetBy(dx: 0, dy: -200)))
    }

    @Test("출발 셀이 사라져도 선택 순간의 전환 좌표가 유지된다")
    func selectedTargetKeepsSourceAfterCellDisappears() {
        let registry = FeedZoomRegistry()
        let sourceID = FeedZoomSource.displayGrid("photo-4")
        let frame = CGRect(x: 220, y: 450, width: 180, height: 240)
        registry.update(.init(frame: frame, cornerRadius: 8), for: sourceID)
        var target = FeedTarget(seed: FeedPreviewData.content, isLikeConfirmed: true, zoomSource: sourceID)
        target.transitionSource = registry.source(for: sourceID)
        registry.remove(sourceID)
        #expect(registry.source(for: sourceID) == nil)
        #expect(target.transitionSource?.frame == frame)
    }

    @Test("화면 영역 측정 전에도 클립 구조를 유지하며 사진 전체를 포함한다")
    func viewportClipBeforeMeasurementIncludesPhoto() {
        let rect = CGRect(x: 0, y: 0, width: 400, height: 300)
        let clip = FeedZoomViewportClip(progress: 0, source: rect, destination: nil, canvas: rect)
        #expect(clip.path(in: rect).boundingRect == rect)
    }

    @Test("전환 클립을 해제한 핀치 사진이 잘리지 않는다")
    func viewportClipIncludesScaledAndPannedPhoto() {
        let rect = CGRect(x: 0, y: 0, width: 400, height: 300)
        let clip = FeedZoomViewportClip(
            progress: 1, source: nil, destination: rect, canvas: rect,
            contentScale: 4, contentOffset: CGSize(width: 800, height: -600)
        )
        let photo = CGRect(x: 200, y: -1050, width: 1600, height: 1200)
        #expect(clip.path(in: rect).boundingRect == rect.union(photo))
    }

    @Test("그리드 재배치 중 이전 셀이 사라져도 새 출발 위치를 유지한다")
    func replacedSourceKeepsRegistration() {
        let registry = FeedZoomRegistry()
        let oldID = UUID()
        let newID = UUID()
        let source = FeedZoomSource.displayGrid("photo")
        let oldFrame = CGRect(x: 20, y: 300, width: 180, height: 240)
        let newFrame = CGRect(x: 220, y: 300, width: 180, height: 240)
        registry.update(.init(frame: oldFrame, cornerRadius: 8, registrationID: oldID), for: source)
        registry.update(.init(frame: newFrame, cornerRadius: 8, registrationID: newID), for: source)
        registry.remove(source, registrationID: oldID)
        #expect(registry.source(for: source)?.frame == newFrame)
        registry.remove(source, registrationID: newID)
        #expect(registry.source(for: source) == nil)
    }

    @Test("정사각형에서 세로·가로 사진까지 크롭 경계가 연속적으로 이동한다", arguments: [CGFloat(536), CGFloat(268), CGFloat(50)])
    func cropBoundsMoveContinuously(destinationHeight: CGFloat) {
        let source = FeedZoomRegistry.Source(frame: CGRect(x: 25, y: 600, width: 82, height: 82), cornerRadius: 8)
        let destination = CGRect(x: 0, y: 180, width: 402, height: destinationHeight)
        for progress in stride(from: CGFloat(0), through: 1, by: 0.1) {
            let geometry = FeedZoomCropGeometry(progress: progress, source: source, destination: destination)
            let width = geometry.size.width * geometry.scale
            let height = geometry.size.height * geometry.scale
            #expect(abs(width - (82 + (402 - 82) * progress)) < 0.001)
            #expect(abs(height - (82 + (destinationHeight - 82) * progress)) < 0.001)
            #expect(abs(geometry.cornerRadius * geometry.scale - 8 * (1 - progress)) < 0.001)
            #expect(abs(destination.midX + geometry.offset.width - (source.frame.midX + (destination.midX - source.frame.midX) * progress)) < 0.001)
            #expect(abs(destination.midY + geometry.offset.height - (source.frame.midY + (destination.midY - source.frame.midY) * progress)) < 0.001)
        }
    }
}

struct FeedScreenReturnGeometryTests {
    private let canvas = CGRect(x: 0, y: 60, width: 390, height: 740)
    private let photo = CGRect(x: 0, y: 220, width: 390, height: 260)
    private let insets = EdgeInsets(top: 60, leading: 0, bottom: 34, trailing: 0)

    @Test("평상시와 취소 완료 시 안전 영역까지 덮는다")
    func coversSafeAreaAtRest() {
        let source = FeedZoomRegistry.Source(frame: CGRect(x: 210, y: 650, width: 160, height: 200), cornerRadius: 12)
        let geometry = FeedScreenReturnGeometry(progress: 1, source: source, photo: photo, canvas: canvas, safeAreaInsets: insets)
        #expect(geometry.scale == 1)
        #expect(geometry.offset == .zero)
        #expect(geometry.cornerRadius == 0)
        #expect(geometry.clip == CGRect(x: 0, y: -60, width: 390, height: 834))
    }

    @Test("길게 끌어도 창의 크기와 전체 화면 영역이 유지된다")
    func holdsWindowDuringDrag() {
        let source = FeedZoomRegistry.Source(frame: CGRect(x: 210, y: 650, width: 160, height: 200), cornerRadius: 12)
        for distance: CGFloat in [100, 200, 400] {
            let geometry = FeedScreenReturnGeometry(progress: 1, dragOffset: distance, source: source, photo: photo, canvas: canvas, safeAreaInsets: insets)
            #expect(abs(geometry.scale - 0.96) < 0.0001)
            #expect(geometry.offset.width == distance)
            #expect(geometry.clip == CGRect(x: 0, y: -60, width: 390, height: 834))
        }
    }

    @Test("움직이기 시작하면 라운드를 빠르게 드러내고 펼쳐지면 사라진다")
    func roundsEarlyDuringMotion() {
        let source = FeedZoomRegistry.Source(frame: CGRect(x: 210, y: 650, width: 160, height: 200), cornerRadius: 12)
        let dragged = FeedScreenReturnGeometry(progress: 1, dragOffset: 12, source: source, photo: photo, canvas: canvas)
        #expect(abs(dragged.cornerRadius * dragged.scale - ChalkakShapes.sheet) < 0.0001)
        let moving = FeedScreenReturnGeometry(progress: 0.94, source: source, photo: photo, canvas: canvas)
        #expect(moving.cornerRadius * moving.scale > 15)
        let expanded = FeedScreenReturnGeometry(progress: 1, source: source, photo: photo, canvas: canvas)
        #expect(expanded.cornerRadius == 0)
    }

    @Test("카드 비율과 위치가 달라도 정확한 카드 영역으로 접힌다")
    func arrivesAtCard() {
        for frame in [CGRect(x: 210, y: 650, width: 160, height: 200), CGRect(x: 20, y: 180, width: 160, height: 90)] {
            let source = FeedZoomRegistry.Source(frame: frame, cornerRadius: 12)
            let geometry = FeedScreenReturnGeometry(progress: 0, source: source, photo: photo, canvas: canvas, safeAreaInsets: insets)
            let actual = CGRect(
                x: canvas.midX + (geometry.clip.minX - canvas.width / 2) * geometry.scale + geometry.offset.width,
                y: canvas.midY + (geometry.clip.minY - canvas.height / 2) * geometry.scale + geometry.offset.height,
                width: geometry.clip.width * geometry.scale, height: geometry.clip.height * geometry.scale
            )
            #expect(abs(actual.minX - frame.minX) < 0.0001)
            #expect(abs(actual.minY - frame.minY) < 0.0001)
            #expect(abs(actual.width - frame.width) < 0.0001)
            #expect(abs(actual.height - frame.height) < 0.0001)
            #expect(abs(geometry.cornerRadius * geometry.scale - 12) < 0.0001)
        }
    }
}
