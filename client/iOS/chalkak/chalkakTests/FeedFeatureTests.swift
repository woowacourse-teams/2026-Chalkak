import Foundation
import Testing
@testable import chalkak

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
    @Test("상세 조회 중 완료된 좋아요를 상세 응답이 덮어쓰지 않는다")
    func detailDoesNotOverwriteInFlightLike() async {
        let gate = AsyncGate()
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
                .success(FeedLikeUpdate(postID: "post-1", isLiked: isLiked, likeCount: 11))
            }
        )

        let loadTask = Task { await viewModel.load() }
        await gate.waitUntilEntered()

        // 상세 조회가 진행 중인 사이 좋아요를 누른다.
        viewModel.toggleLike()
        #expect(viewModel.viewState.content?.post.isLiked == true)
        #expect(viewModel.viewState.content?.post.likeCount == 11)

        // 상세 응답 도착 후에도 좋아요 상태가 유지되어야 한다.
        gate.release()
        await loadTask.value

        #expect(viewModel.viewState.contentStatus == .loaded)
        #expect(viewModel.viewState.content?.post.isLiked == true)
        #expect(viewModel.viewState.content?.post.likeCount == 11)
        #expect(viewModel.viewState.content?.topic == "갱신됨")
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

    private static func content(
        isLiked: Bool,
        likeCount: Int,
        topic: String = "하늘"
    ) -> FeedContent {
        FeedContent(
            dateLabel: "8월 3일의 주제",
            topic: topic,
            post: FeedPost(
                id: "post-1",
                originalImageSource: .asset("preview_photo"),
                signatureImageSource: .asset("preview_signature"),
                contentDescription: "설명",
                title: "제목",
                likeCount: likeCount,
                isLiked: isLiked
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
