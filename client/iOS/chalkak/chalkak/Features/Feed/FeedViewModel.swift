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

    init(
        postID: String,
        seed: FeedContent? = nil,
        initialState: FeedViewState? = nil,
        detailHandler: @escaping DetailHandler = { _ in .failure(.generic) },
        likeHandler: @escaping LikeHandler = { _, _ in .failure(.generic) }
    ) {
        self.postID = postID
        if let initialState {
            self.viewState = initialState
        } else if let seed {
            self.viewState = FeedViewState(contentStatus: .loaded, content: seed)
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

        switch await detailHandler(postID) {
        case let .success(content):
            viewState.content = content
            viewState.contentStatus = .loaded
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

    func toggleLike() async {
        guard let current = viewState.content else { return }

        let liked = !current.post.isLiked
        var optimistic = current
        optimistic.post.isLiked = liked
        optimistic.post.likeCount = max(0, current.post.likeCount + (liked ? 1 : -1))
        viewState.content = optimistic

        likeGeneration += 1
        let generation = likeGeneration
        let result = await likeHandler(postID, liked)
        guard generation == likeGeneration else { return }

        switch result {
        case let .success(update):
            guard var latest = viewState.content else { return }
            latest.post.isLiked = update.isLiked
            latest.post.likeCount = update.likeCount
            viewState.content = latest
        case .failure:
            viewState.content = current
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
