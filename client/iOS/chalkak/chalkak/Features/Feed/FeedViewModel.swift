import Foundation
import Observation

@MainActor
@Observable
final class FeedViewModel {
    typealias DetailHandler = @MainActor @Sendable (String) async -> Result<FeedContent, FeedError>
    typealias LikeHandler = @MainActor @Sendable (String, Bool) async -> Result<FeedLikeUpdate, FeedError>

    private(set) var viewState: FeedViewState

    private let postID: String
    private let detailHandler: DetailHandler
    private let likeHandler: LikeHandler
    private var likeGeneration = 0
    // 직전 좋아요 요청. 다음 요청은 이 태스크가 끝난 뒤에 보내 직렬화한다.
    private var likeTask: Task<Void, Never>?

    init(
        postID: String,
        seed: FeedContent? = nil,
        isLikeConfirmed: Bool = false,
        initialState: FeedViewState? = nil,
        detailHandler: @escaping DetailHandler = { _ in .failure(.generic) },
        likeHandler: @escaping LikeHandler = { _, _ in .failure(.generic) }
    ) {
        self.postID = postID
        if let initialState {
            self.viewState = initialState
        } else if let seed {
            self.viewState = FeedViewState(
                contentStatus: .loaded,
                content: seed,
                isLikeEnabled: isLikeConfirmed
            )
        } else {
            self.viewState = FeedViewState(contentStatus: .loading, content: nil)
        }
        self.detailHandler = detailHandler
        self.likeHandler = likeHandler
    }

    convenience init(target: FeedTarget, apiClient: FeedAPIClient) {
        self.init(
            postID: target.id,
            seed: target.seed,
            isLikeConfirmed: target.isLikeConfirmed,
            detailHandler: { postID in
                await feedResult { try await apiClient.fetchPostDetail(postID: postID) }
            },
            likeHandler: { postID, isLiked in
                await feedResult { try await apiClient.updateLike(postID: postID, isLiked: isLiked) }
            }
        )
    }

    func load() async {
        if viewState.content == nil {
            viewState.contentStatus = .loading
        }
        let likeGenerationAtStart = likeGeneration

        switch await detailHandler(postID) {
        case let .success(content):
            var merged = content
            // 상세 요청 중 좋아요가 토글됐다면 낙관적 좋아요 상태를 상세 응답으로 덮어쓰지 않는다.
            if likeGeneration != likeGenerationAtStart, let current = viewState.content {
                merged.post.isLiked = current.post.isLiked
                merged.post.likeCount = current.post.likeCount
            }
            viewState.content = merged
            viewState.contentStatus = .loaded
            // 상세 조회로 실제 좋아요 값이 확정되면 좋아요 동작을 허용한다.
            viewState.isLikeEnabled = true
        case let .failure(error):
            // 이미 시드된 콘텐츠가 있으면 갱신 실패는 무시하고 그대로 보여준다.
            if viewState.content == nil {
                viewState.contentStatus = .error(error)
            }
        }
    }

    func retry() async {
        await load()
    }

    func toggleLike() {
        guard viewState.isLikeEnabled, let current = viewState.content else { return }

        let liked = !current.post.isLiked
        var optimistic = current
        optimistic.post.isLiked = liked
        optimistic.post.likeCount = max(0, current.post.likeCount + (liked ? 1 : -1))
        viewState.content = optimistic

        likeGeneration += 1
        let generation = likeGeneration
        let rollback = current
        let previousTask = likeTask
        likeTask = Task { [weak self] in
            // 직전 좋아요 요청이 끝난 뒤 보내 동시 PUT/DELETE를 막는다(직렬화).
            await previousTask?.value
            guard let self else { return }

            let result = await self.likeHandler(self.postID, liked)
            // 최신 의도가 아니면 UI에 반영하지 않는다.
            guard generation == self.likeGeneration else { return }

            switch result {
            case let .success(update):
                guard var latest = self.viewState.content else { return }
                latest.post.isLiked = update.isLiked
                latest.post.likeCount = update.likeCount
                self.viewState.content = latest
            case .failure:
                self.viewState.content = rollback
            }
        }
    }
}

@MainActor
private func feedResult<Value: Sendable>(
    _ operation: () async throws -> Value
) async -> Result<Value, FeedError> {
    do {
        return .success(try await operation())
    } catch let error as FeedAPIError {
        return .failure(error.feedError)
    } catch is CancellationError {
        return .failure(.generic)
    } catch {
        return .failure(.generic)
    }
}
