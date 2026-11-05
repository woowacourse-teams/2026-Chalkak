import Foundation
import Observation

@MainActor
@Observable
final class FeedViewModel {
    // Avoid the isolated-deinit back-deployment crash on iOS 26.2 and older.
    // No actor-bound cleanup is needed. See swiftlang/swift#88036.
    nonisolated deinit {}

    typealias DetailHandler = @MainActor @Sendable (String) async -> Result<FeedContent, FeedError>
    typealias LikeHandler = @MainActor @Sendable (String, Bool) async -> Result<FeedLikeUpdate, FeedError>
    typealias DeleteHandler = @MainActor @Sendable (String) async -> Result<Void, FeedError>
    typealias UpdatePostHandler = @MainActor @Sendable (String, String?, String?) async -> Result<FeedPostUpdate, FeedError>

    private(set) var viewState: FeedViewState
    private(set) var event: FeedEvent?
    let aiReview: FeedAIReviewViewModel?

    private let postID: String
    private let isOwnedByCurrentUser: Bool
    private let sourceAuthorID: String?
    private let detailHandler: DetailHandler
    private let likeHandler: LikeHandler
    private let deleteHandler: DeleteHandler
    private let updatePostHandler: UpdatePostHandler
    private var likeGeneration = 0
    // 상세를 받기 전에 다른 화면에서 도착한 좋아요. 상세가 도착하면 그 위에 덮어쓴다.
    private var pendingLikeUpdate: FeedLikeUpdate?
    // 직전 좋아요 요청. 다음 요청은 이 태스크가 끝난 뒤에 보내 직렬화한다.
    private var likeTask: Task<Void, Never>?

    init(
        postID: String,
        seed: FeedContent? = nil,
        isLikeConfirmed: Bool = false,
        isOwnedByCurrentUser: Bool = false,
        sourceAuthorID: String? = nil,
        initialState: FeedViewState? = nil,
        detailHandler: @escaping DetailHandler = { _ in .failure(.generic) },
        likeHandler: @escaping LikeHandler = { _, _ in .failure(.generic) },
        deleteHandler: @escaping DeleteHandler = { _ in .failure(.generic) },
        updatePostHandler: @escaping UpdatePostHandler = { _, _, _ in .failure(.generic) },
        aiReviewRepository: (any AIReviewRepository)? = nil
    ) {
        self.aiReview = aiReviewRepository.map {
            FeedAIReviewViewModel(
                postID: postID,
                repository: $0,
                topicDate: (initialState?.content ?? seed)?.topicDate ?? .distantPast
            )
        }
        self.postID = postID
        self.isOwnedByCurrentUser = isOwnedByCurrentUser
        self.sourceAuthorID = sourceAuthorID
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
        self.deleteHandler = deleteHandler
        self.updatePostHandler = updatePostHandler
    }

    convenience init(target: FeedTarget, apiClient: FeedAPIClient, aiReviewRepository: (any AIReviewRepository)? = nil) {
        self.init(
            postID: target.id,
            seed: target.seed,
            isLikeConfirmed: target.isLikeConfirmed,
            isOwnedByCurrentUser: target.isOwnedByCurrentUser,
            sourceAuthorID: target.sourceAuthorID,
            detailHandler: { postID in
                await feedResult { try await apiClient.fetchPostDetail(postID: postID) }
            },
            likeHandler: { postID, isLiked in
                await feedResult { try await apiClient.updateLike(postID: postID, isLiked: isLiked) }
            },
            deleteHandler: { postID in
                await feedResult { try await apiClient.deletePost(postID: postID) }
            },
            updatePostHandler: { postID, title, description in
                await feedResult {
                    try await apiClient.updatePost(postID: postID, title: title, description: description)
                }
            },
            aiReviewRepository: aiReviewRepository
        )
    }

    func load() async {
        if viewState.content == nil {
            viewState.contentStatus = .loading
        }
        let likeGenerationAtStart = likeGeneration
        let postUpdateVersionAtStart = viewState.postUpdateVersion

        switch await detailHandler(postID) {
        case let .success(content):
            var merged = content
            // 상세 요청 중 좋아요가 토글됐다면 낙관적 좋아요 상태를 상세 응답으로 덮어쓰지 않는다.
            if likeGeneration != likeGenerationAtStart, let current = viewState.content {
                merged.post.isLiked = current.post.isLiked
                merged.post.likeCount = current.post.likeCount
            }
            // 상세 요청 중 수정을 저장했다면 늦게 온 상세 응답이 수정 결과를 덮어쓰지 않게 한다.
            if viewState.postUpdateVersion != postUpdateVersionAtStart,
               let current = viewState.content {
                merged.post.title = current.post.title
                merged.post.description = current.post.description
            }
            if let current = viewState.content, current.post.isOwnedByCurrentUser {
                merged.post.isOwnedByCurrentUser = true
            }
            // 상세 응답에 작성자 ID가 없으면(서버 반영 전) 전시 시드에서 받은 값을 유지한다.
            if let current = viewState.content {
                merged.post.authorID = merged.post.authorID ?? current.post.authorID
            }
            if isOwnedByCurrentUser {
                merged.post.isOwnedByCurrentUser = true
            }
            // 상세 요청이 좋아요 반영 전에 처리됐을 수 있어, 그 사이 받은 좋아요 결과를 우선한다.
            if let pending = pendingLikeUpdate {
                merged.post.isLiked = pending.isLiked
                merged.post.likeCount = pending.likeCount
                pendingLikeUpdate = nil
            }
            aiReview?.updateTopicDate(merged.topicDate)
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

    /// 사인을 눌렀을 때 할 일. 작성자 ID를 아직 모르면 nil이라 아무것도 하지 않는다.
    /// 작가 사진 목록에서 연 피드의 사인은 항상 그 작가라서, 목록을 다시 쌓지 않고 돌아가게 한다.
    /// 예) 목록(작가 A) → 피드(A의 사진) → 사인 탭 → `.returnToAuthorPosts`
    func signatureTapAction() -> FeedSignatureTapAction? {
        guard let post = viewState.content?.post, let authorID = post.authorID else { return nil }
        if authorID == sourceAuthorID { return .returnToAuthorPosts }
        return .openAuthorPosts(
            AuthorPostsTarget(
                authorID: authorID,
                // 탑바에서 크게 그리므로 썸네일이 아닌 원본을 쓴다. 피드가 이미 받아 둔 이미지라 바로 그려진다.
                signatureImageSource: post.signatureImageSource,
                isMine: post.isOwnedByCurrentUser
            )
        )
    }

    /// 다른 화면에서 바뀐 좋아요를 반영한다. 다른 게시물의 값이면 무시한다.
    /// 진행 중인 상세 조회가 이 값을 덮어쓰지 않도록 좋아요 세대를 올린다.
    /// 예) 작가 목록 카드에서 좋아요를 누르고 응답 전에 같은 사진 상세를 열면, 상세 도착 전후 어느 쪽이든 카드 결과로 맞춘다.
    func applyLikeUpdate(_ update: FeedLikeUpdate) {
        guard update.postID == postID else { return }
        guard var content = viewState.content else {
            pendingLikeUpdate = update
            return
        }
        guard content.post.isLiked != update.isLiked || content.post.likeCount != update.likeCount else { return }
        likeGeneration += 1
        content.post.isLiked = update.isLiked
        content.post.likeCount = update.likeCount
        viewState.content = content
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
                self.event = .showLikeFailure
            }
        }
    }

    func deletePost() {
        guard let current = viewState.content,
              current.post.isOwnedByCurrentUser,
              !viewState.isDeleting,
              !viewState.isSavingPostEdit
        else { return }

        viewState.isDeleting = true
        viewState.deletedPostID = nil

        Task { [weak self] in
            guard let self else { return }
            switch await self.deleteHandler(self.postID) {
            case .success:
                self.viewState.isDeleting = false
                self.viewState.deletedPostID = self.postID
            case let .failure(error):
                self.viewState.isDeleting = false
                self.event = .showDeleteFailure(error)
            }
        }
    }

    /// 상단 '수정'을 누르면 지금 제목·설명을 채운 수정 입력을 연다. 비어 있던 값은 빈 칸으로 시작한다.
    func startPostEdit() {
        guard let current = viewState.content,
              current.post.isOwnedByCurrentUser,
              FeedDateLabel.isToday(current.topicDate)
        else { return }
        viewState.postEdit = FeedPostEditState(
            title: current.post.title ?? "",
            description: current.post.description ?? ""
        )
    }

    func updatePostEditTitle(_ title: String) {
        viewState.postEdit?.title = title
    }

    func updatePostEditDescription(_ description: String) {
        viewState.postEdit?.description = description
    }

    /// 수정 화면을 저장하지 않고 닫는다. 저장 중에는 응답을 받아야 하므로 닫지 않는다.
    func cancelPostEdit() {
        guard !viewState.isSavingPostEdit else { return }
        viewState.postEdit = nil
    }

    /// 수정 입력의 제목·설명을 한 요청으로 보낸다. 서버는 둘을 함께 교체하므로 고치지 않은 값도 같이 보낸다(POST-010).
    func savePostEdit() {
        guard let edit = viewState.postEdit,
              let current = viewState.content,
              current.post.isOwnedByCurrentUser,
              FeedDateLabel.isToday(current.topicDate),
              !viewState.isDeleting,
              !viewState.isSavingPostEdit
        else { return }

        let trimmedTitle = edit.title.trimmingCharacters(in: .whitespacesAndNewlines)
        let normalizedTitle: String? = trimmedTitle.isEmpty ? nil : trimmedTitle
        viewState.isSavingPostEdit = true

        Task { [weak self] in
            guard let self else { return }
            switch await self.updatePostHandler(self.postID, normalizedTitle, edit.description) {
            case let .success(update):
                guard var latest = self.viewState.content else {
                    self.viewState.isSavingPostEdit = false
                    return
                }
                latest.post.title = update.title
                latest.post.description = update.description
                self.viewState.content = latest
                self.viewState.postEdit = nil
                self.viewState.isSavingPostEdit = false
                self.viewState.postUpdateVersion += 1
                self.event = .showPostUpdateSuccess
            case let .failure(error):
                self.viewState.isSavingPostEdit = false
                self.event = .showPostUpdateFailure(error)
            }
        }
    }

    func consumeEvent() {
        event = nil
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
