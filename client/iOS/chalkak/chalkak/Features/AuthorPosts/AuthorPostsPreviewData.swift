import Foundation

enum AuthorPostsPreviewData {
    static let loadedState = AuthorPostsViewState(
        contentStatus: .loaded,
        posts: posts,
        currentPage: 1,
        hasNext: true
    )

    static let emptyState = AuthorPostsViewState(contentStatus: .loaded)

    static let notFoundState = AuthorPostsViewState(contentStatus: .error(.authorNotFound))

    static let errorState = AuthorPostsViewState(contentStatus: .error(.network))

    @MainActor
    static func viewModel(state: AuthorPostsViewState, isMine: Bool = false) -> AuthorPostsViewModel {
        AuthorPostsViewModel(
            target: AuthorPostsTarget(
                authorID: "preview-author",
                signatureImageSource: .asset("preview_signature"),
                isMine: isMine
            ),
            initialState: state
        )
    }

    private static let posts: [AuthorPost] = (1...6).map { index in
        AuthorPost(
            id: "preview-\(index)",
            originalImageSource: .asset("preview_photo"),
            thumbnailImageSource: .asset("preview_photo"),
            topic: "반짝이는 순간",
            topicDate: Date(timeIntervalSince1970: 1_790_000_000),
            contentDescription: "미리보기 작가 사진 \(index)"
        )
    }
}
