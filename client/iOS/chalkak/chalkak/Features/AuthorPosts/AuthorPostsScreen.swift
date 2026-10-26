import SwiftUI

/// 한 작가가 공개한 사진을 전시와 같은 2열 그리드로 모아 본다.
/// 탑바(뒤로 가기 + 작가 사인)는 고정이고, 그리드 위 설명은 그리드와 함께 스크롤된다.
/// 사진을 누르면 `onSelectPost`로 피드를 연다. 화면 전환은 호출하는 쪽이 맡는다.
struct AuthorPostsScreen: View {
    @Environment(\.chalkakTheme) private var theme
    @Environment(\.chalkakShowToast) private var showToast
    var viewModel: AuthorPostsViewModel
    var onBack: () -> Void = {}
    var onSelectPost: (FeedTarget) -> Void = { _ in }

    var body: some View {
        VStack(spacing: 0) {
            AuthorPostsTopBar(
                signatureImageSource: viewModel.viewState.showsAuthorIdentity
                    ? viewModel.target.signatureImageSource
                    : nil,
                isMine: viewModel.target.isMine,
                onBack: onBack
            )
            .padding(.horizontal, theme.spacing.lg)
            .padding(.vertical, theme.spacing.md)

            content
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(theme.colors.background)
        .task(id: ObjectIdentifier(viewModel)) {
            // 목록에서 연 피드를 닫고 돌아올 때는 이미 받은 목록과 스크롤 위치를 유지한다.
            guard viewModel.viewState.contentStatus == .loading else { return }
            await viewModel.load()
        }
        .onChange(of: viewModel.event) { _, event in
            handle(event)
        }
    }

    @ViewBuilder
    private var content: some View {
        switch viewModel.viewState.contentStatus {
        case .loading:
            centered {
                ProgressView()
                    .tint(theme.colors.actionPrimary)
                    .accessibilityIdentifier("author-posts-loading")
            }
        case .error(.authorNotFound):
            centered {
                Text(AuthorPostsError.authorNotFound.message)
                    .font(theme.typography.body)
                    .foregroundStyle(theme.colors.textSecondary)
                    .multilineTextAlignment(.center)
                    .accessibilityIdentifier("author-posts-not-found")
            }
        case let .error(reason):
            centered { errorView(reason) }
        case .loaded:
            loadedContent
        }
    }

    private var loadedContent: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                Text(viewModel.target.isMine ? "작가님이 공개한 시선들이에요" : "한 작가님의 시선을 따라가 보세요")
                    .font(theme.typography.subheadline)
                    .foregroundStyle(theme.colors.textInactive)
                    .fixedSize(horizontal: false, vertical: true)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.top, theme.spacing.sm)
                    .padding(.bottom, theme.spacing.lg)

                if viewModel.viewState.isEmpty {
                    emptyContent
                } else {
                    DisplayPhotoGrid(
                        photos: viewModel.viewState.posts.map(gridPhoto),
                        isLoadingNext: viewModel.viewState.isLoadingNext,
                        onEndThreshold: { isReached in
                            Task { await viewModel.didReachEndThreshold(isReached) }
                        },
                        onSelect: { photo, ratio, source in
                            onSelectPost(feedTarget(for: photo, ratio: ratio, transitionSource: source))
                        },
                        zoomSource: FeedZoomSource.authorGrid,
                        showsSignature: false,
                        showsLikeBadge: false
                    )
                }
            }
            .padding(.horizontal, theme.spacing.screenHorizontal)
            .padding(.bottom, theme.spacing.xxl)
        }
        .feedZoomViewport(.authorPosts)
    }

    private var emptyContent: some View {
        VStack(spacing: theme.spacing.sm) {
            Image(systemName: "photo.on.rectangle.angled")
                .font(.system(size: Metrics.emptyIconSize))
                .foregroundStyle(theme.colors.iconSecondary)
                .accessibilityHidden(true)

            Text("아직 공개된 사진이 없어요")
                .font(theme.typography.body)
                .foregroundStyle(theme.colors.textSecondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.top, Metrics.emptyTopPadding)
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("author-posts-empty")
    }

    private func errorView(_ reason: AuthorPostsError) -> some View {
        VStack(spacing: theme.spacing.xl) {
            Text(reason.message)
                .font(theme.typography.body)
                .foregroundStyle(theme.colors.textSecondary)
                .multilineTextAlignment(.center)

            Button {
                Task { await viewModel.retry() }
            } label: {
                Text("다시 시도")
                    .font(theme.typography.body)
                    .foregroundStyle(theme.colors.actionPrimary)
                    .frame(minHeight: Metrics.minimumTouchSize)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
        }
        .padding(.horizontal, theme.spacing.screenHorizontal)
        .accessibilityIdentifier("author-posts-error")
    }

    // 전시 그리드를 그대로 쓰기 위해 전시 카드 모델로 바꾼다.
    // 목록 응답에는 사인·좋아요가 없어 사인은 탑바와 같은 작가 사인으로, 좋아요는 숨긴 채 0으로 둔다.
    private func gridPhoto(_ post: AuthorPost) -> DisplayPhoto {
        DisplayPhoto(
            id: post.id,
            originalImageSource: post.originalImageSource,
            thumbnailImageSource: post.thumbnailImageSource,
            signatureOriginalImageSource: viewModel.target.signatureImageSource,
            signatureThumbnailImageSource: viewModel.target.signatureImageSource,
            contentDescription: post.contentDescription,
            title: nil,
            likeCount: 0,
            isLiked: false,
            isOwnedByCurrentUser: viewModel.target.isMine,
            authorID: viewModel.target.authorID
        )
    }

    // 목록에는 제목·좋아요가 없어 시드 없이 열고 피드가 상세를 조회한다.
    private func feedTarget(
        for photo: DisplayPhoto,
        ratio: CGFloat?,
        transitionSource: FeedZoomRegistry.Source
    ) -> FeedTarget {
        var target = FeedTarget(
            postID: photo.id,
            isOwnedByCurrentUser: viewModel.target.isMine,
            zoomSource: .authorGrid(photo.id),
            placeholder: FeedPhotoPlaceholder(
                imageSource: photo.thumbnailImageSource,
                signatureImageSource: viewModel.target.signatureImageSource,
                heightToWidthRatio: ratio
            )
        )
        target.transitionSource = transitionSource
        return target
    }

    private func handle(_ event: AuthorPostsEvent?) {
        guard let event else { return }
        switch event {
        case .nextPageFailed:
            showToast(AuthorPostsError.generic.message)
        }
        viewModel.consumeEvent()
    }

    private func centered(@ViewBuilder _ inner: () -> some View) -> some View {
        VStack {
            Spacer(minLength: 0)
            inner()
            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }
}

private enum Metrics {
    static let emptyIconSize: CGFloat = 34
    static let emptyTopPadding: CGFloat = 96
    static let minimumTouchSize: CGFloat = 44
}

#Preview("Author Posts") {
    AuthorPostsScreen(viewModel: AuthorPostsPreviewData.viewModel(state: AuthorPostsPreviewData.loadedState))
        .chalkakTheme(.light)
}

#Preview("Author Posts Mine Empty") {
    AuthorPostsScreen(viewModel: AuthorPostsPreviewData.viewModel(state: AuthorPostsPreviewData.emptyState, isMine: true))
        .chalkakTheme(.light)
}

#Preview("Author Posts Not Found") {
    AuthorPostsScreen(viewModel: AuthorPostsPreviewData.viewModel(state: AuthorPostsPreviewData.notFoundState))
        .chalkakTheme(.light)
}

#Preview("Author Posts Error") {
    AuthorPostsScreen(viewModel: AuthorPostsPreviewData.viewModel(state: AuthorPostsPreviewData.errorState))
        .chalkakTheme(.light)
}
