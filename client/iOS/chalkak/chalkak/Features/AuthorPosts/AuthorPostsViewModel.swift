import Foundation
import Observation

/// 작가 사진 목록의 페이지 이어받기를 맡는다.
/// 진입·다시 시도는 1페이지부터 받고, 그리드 끝에 가까워지면 `currentPage + 1`을 받아 이어 붙인다.
/// 조회 중 승인·삭제로 페이지 경계가 밀려 같은 사진이 다시 올 수 있어 id로 중복을 걸러낸다(POST-012).
@MainActor
@Observable
final class AuthorPostsViewModel {
    // Avoid the isolated-deinit back-deployment crash on iOS 26.2 and older.
    // No actor-bound cleanup is needed. See swiftlang/swift#88036.
    nonisolated deinit {}

    typealias PageHandler = @MainActor @Sendable (_ page: Int) async -> Result<AuthorPostsPage, AuthorPostsError>
    typealias LikeHandler = @MainActor @Sendable (_ postID: String, _ isLiked: Bool) async -> Result<FeedLikeUpdate, AuthorPostsError>

    let target: AuthorPostsTarget
    private(set) var viewState: AuthorPostsViewState
    private(set) var event: AuthorPostsEvent?
    // 좋아요 요청 중인 사진. 응답 전까지 같은 카드의 좋아요를 다시 누르지 못하게 한다.
    private(set) var likingPostIDs: Set<AuthorPost.ID> = []

    private let pageHandler: PageHandler
    private let likeHandler: LikeHandler
    // 다시 시도로 1페이지를 새로 받는 동안 도착한 이전 요청의 응답을 버리기 위한 번호.
    private var generation = 0
    private var isEndThresholdReached = false

    init(
        target: AuthorPostsTarget,
        initialState: AuthorPostsViewState = AuthorPostsViewState(),
        pageHandler: @escaping PageHandler = { _ in .failure(.generic) },
        likeHandler: @escaping LikeHandler = { _, _ in .failure(.generic) }
    ) {
        self.target = target
        self.viewState = initialState
        self.pageHandler = pageHandler
        self.likeHandler = likeHandler
    }

    convenience init(target: AuthorPostsTarget, apiClient: AuthorPostsAPIClient, likeClient: FeedAPIClient) {
        let authorID = target.authorID
        self.init(
            target: target,
            pageHandler: { page in
                do {
                    return .success(try await apiClient.fetchPage(authorID: authorID, page: page))
                } catch let error as AuthorPostsAPIError {
                    return .failure(error.authorPostsError)
                } catch {
                    return .failure(.generic)
                }
            },
            likeHandler: { postID, isLiked in
                do {
                    return .success(try await likeClient.updateLike(postID: postID, isLiked: isLiked))
                } catch {
                    return .failure(.generic)
                }
            }
        )
    }

    func load() async {
        generation += 1
        let requestGeneration = generation
        viewState = AuthorPostsViewState(contentStatus: .loading)
        isEndThresholdReached = false

        let result = await pageHandler(1)
        guard requestGeneration == generation else { return }

        switch result {
        case let .success(page):
            viewState.posts = Self.uniquePosts(page.posts)
            viewState.currentPage = page.currentPage
            viewState.hasNext = page.hasNext
            viewState.contentStatus = .loaded
        case let .failure(error):
            viewState.contentStatus = .error(error)
        }
    }

    func retry() async {
        await load()
    }

    func didReachEndThreshold(_ isReached: Bool) async {
        guard isReached else {
            isEndThresholdReached = false
            return
        }
        guard !isEndThresholdReached else { return }
        isEndThresholdReached = true
        await loadNextPage()
    }

    /// 카드의 좋아요를 등록·취소한다. 전시 카드처럼 응답을 받은 뒤 반영하고, 반영한 값을 돌려준다.
    /// 실패하면 카드를 그대로 두고 `.likeFailed`를 알린다.
    @discardableResult
    func toggleLike(postID: AuthorPost.ID) async -> FeedLikeUpdate? {
        guard !likingPostIDs.contains(postID),
              let isLiked = viewState.posts.first(where: { $0.id == postID })?.isLiked
        else { return nil }
        likingPostIDs.insert(postID)
        defer { likingPostIDs.remove(postID) }

        switch await likeHandler(postID, !isLiked) {
        case let .success(update):
            guard update.postID == postID, update.likeCount >= 0 else {
                event = .likeFailed
                return nil
            }
            applyLike(update)
            return update
        case .failure:
            event = .likeFailed
            return nil
        }
    }

    /// 다른 화면(목록에서 연 피드)에서 바뀐 좋아요를 카드에 맞춘다. 목록에 없는 사진이면 무시한다.
    func applyLike(_ update: FeedLikeUpdate) {
        guard let index = viewState.posts.firstIndex(where: { $0.id == update.postID }) else { return }
        viewState.posts[index].isLiked = update.isLiked
        viewState.posts[index].likeCount = update.likeCount
    }

    /// 목록에서 연 피드에서 내 사진을 삭제했을 때 그리드에서 뺀다.
    func removePost(_ postID: AuthorPost.ID) {
        viewState.posts.removeAll { $0.id == postID }
    }

    func consumeEvent() {
        event = nil
    }

    private func loadNextPage() async {
        guard viewState.contentStatus == .loaded,
              viewState.hasNext,
              !viewState.isLoadingNext
        else {
            isEndThresholdReached = false
            return
        }

        let requestGeneration = generation
        viewState.isLoadingNext = true
        let result = await pageHandler(viewState.currentPage + 1)
        guard requestGeneration == generation else { return }
        viewState.isLoadingNext = false

        switch result {
        case let .success(page):
            var existingIDs = Set(viewState.posts.map(\.id))
            viewState.posts.append(contentsOf: page.posts.filter { existingIDs.insert($0.id).inserted })
            viewState.currentPage = page.currentPage
            viewState.hasNext = page.hasNext
        case .failure:
            // 다음에 끝에 다시 닿으면 같은 페이지를 다시 요청한다.
            isEndThresholdReached = false
            event = .nextPageFailed
        }
    }

    private static func uniquePosts(_ posts: [AuthorPost]) -> [AuthorPost] {
        var seen = Set<AuthorPost.ID>()
        return posts.filter { seen.insert($0.id).inserted }
    }
}
