import Foundation

enum FeedPreviewData {
    static let content = FeedContent(
        dateLabel: "8월 3일의 주제",
        topic: "하늘하늘하늘",
        post: FeedPost(
            id: "preview",
            originalImageSource: .asset("preview_photo"),
            signatureImageSource: .asset("preview_signature"),
            contentDescription: "노을이 진 하늘과 전신주",
            title: "안녕하세요 감사합니다.",
            likeCount: 24,
            isLiked: false
        )
    )

    static let loadedState = FeedViewState(contentStatus: .loaded, content: content)

    static let loadingState = FeedViewState(contentStatus: .loading, content: nil)

    static let errorState = FeedViewState(contentStatus: .error(.generic), content: nil)

    static func viewModel(state: FeedViewState) -> FeedViewModel {
        FeedViewModel(postID: content.post.id, initialState: state)
    }
}
