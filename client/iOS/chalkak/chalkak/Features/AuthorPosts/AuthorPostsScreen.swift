import SwiftUI

/// 한 작가가 공개한 사진을 전시와 같은 2열 그리드로 모아 본다.
/// 탑바의 뒤로 가기는 고정이고, 작가 사인은 처음에 크게 보였다가 스크롤하면 탑바 가운데로 줄어든다.
/// push 화면처럼 오른쪽에서 밀려 들어오고, 닫을 때(뒤로 가기·왼쪽 가장자리 백 스와이프)는 오른쪽으로 밀려 나간다.
/// 배경과 콘텐츠를 한 덩어리로 옮겨, 늦게 그려지는 사진도 화면과 함께 움직인다.
/// 사진을 누르면 `onSelectPost`로 피드를 연다.
struct AuthorPostsScreen: View {
    @Environment(\.chalkakTheme) private var theme
    @Environment(\.chalkakShowToast) private var showToast
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    var viewModel: AuthorPostsViewModel
    // 닫는 전환이 끝난 뒤 호출된다. 호출부는 애니메이션 없이 화면을 치우면 된다.
    var onBack: () -> Void = {}
    var onSelectPost: (FeedTarget) -> Void = { _ in }
    // 카드에서 좋아요를 바꿨을 때. 이 목록을 연 피드도 같은 사진이면 맞출 수 있게 알린다.
    var onLikeChange: (FeedLikeUpdate) -> Void = { _ in }

    // 화면이 들어온 정도(0: 오른쪽 밖, 1: 제자리).
    @State private var progress: CGFloat = 0
    // 백 스와이프 거리를 진행도로 바꾸는 기준.
    @State private var screenWidth: CGFloat = 0
    @State private var isTransitioning = true
    @State private var isBackSwiping = false
    @State private var backSwipeTracking = FeedBackSwipeTracking()
    @State private var isClosing = false
    @State private var scrollOffset: CGFloat = 0

    var body: some View {
        VStack(spacing: 0) {
            // 탑바 자리. 탑바는 펼친 사인이 아래 콘텐츠 위로 넘쳐 그려지도록 overlay로 올린다.
            Color.clear.frame(height: AuthorPostsTopBar.height)
            content
        }
        .overlay(alignment: .top) {
            AuthorPostsTopBar(
                signatureImageSource: viewModel.viewState.showsAuthorIdentity
                    ? viewModel.target.signatureImageSource
                    : nil,
                isMine: viewModel.target.isMine,
                collapseProgress: collapseProgress,
                onBack: { close() }
            )
            .background(theme.colors.background)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(theme.colors.background)
        .onGeometryChange(for: CGFloat.self) { $0.size.width } action: { screenWidth = $0 }
        // 움직이는 동안 아래 피드와 경계가 보이도록 왼쪽 끝에 그림자를 둔다.
        .shadow(color: .black.opacity(progress < 1 ? Metrics.shadowOpacity : 0), radius: Metrics.shadowRadius)
        // 동작 줄이기에서는 밀지 않고 페이드한다.
        .modifier(AuthorPostsSlideEffect(progress: reduceMotion ? 1 : progress))
        .opacity(reduceMotion ? progress : 1)
        .background {
            // push처럼 아래 피드를 살짝 어둡게 한다. 화면을 따라 움직이지 않게 슬라이드 바깥에 둔다.
            Color.black.opacity(Metrics.dimOpacity * progress)
                .ignoresSafeArea()
                .allowsHitTesting(false)
                .accessibilityHidden(true)
        }
        .simultaneousGesture(backSwipeGesture)
        .allowsHitTesting(!isClosing)
        .onAppear { open() }
        .task(id: ObjectIdentifier(viewModel)) {
            // 목록에서 연 피드를 닫고 돌아올 때는 이미 받은 목록과 스크롤 위치를 유지한다.
            guard viewModel.viewState.contentStatus == .loading else { return }
            await viewModel.load()
        }
        .onChange(of: viewModel.event) { _, event in
            handle(event)
        }
    }

    // 스크롤한 만큼 사인을 접는다. 그리드 위 설명이 탑바 아래로 들어가는 순간 다 접힌다.
    private var collapseProgress: CGFloat {
        min(max(scrollOffset / Metrics.collapseDistance, 0), 1)
    }

    // MARK: - 화면 전환

    // releaseVelocity: 백 스와이프를 놓은 순간의 진행도 속도(초당). 없으면 멈춘 상태에서 출발한다.
    private func open(releaseVelocity: CGFloat? = nil) {
        isTransitioning = true
        withAnimation(screenAnimation(to: 1, releaseVelocity: releaseVelocity)) {
            progress = 1
        } completion: {
            guard !isClosing else { return }
            isTransitioning = false
        }
    }

    private func close(releaseVelocity: CGFloat? = nil) {
        guard !isClosing else { return }
        isClosing = true
        isTransitioning = true
        withAnimation(screenAnimation(to: 0, releaseVelocity: releaseVelocity)) {
            progress = 0
        } completion: {
            onBack()
        }
    }

    // 스와이프를 놓았을 때는 그 속도를 이어받아야 화면이 멈칫했다가 다시 출발하지 않는다.
    private func screenAnimation(to target: CGFloat, releaseVelocity: CGFloat?) -> Animation {
        guard let releaseVelocity else { return Metrics.screenAnimation }
        return FeedBackSwipeRelease.animation(
            from: progress,
            to: target,
            velocity: releaseVelocity,
            duration: Metrics.screenReleaseDuration
        )
    }

    // 화면 왼쪽 가장자리에서 시작한 스와이프로 닫는다. 끄는 만큼 화면이 오른쪽으로 따라온다.
    // 화면이 손을 따라 움직이므로 위치는 화면 밖(global) 기준으로 잰다.
    private var backSwipeGesture: some Gesture {
        DragGesture(minimumDistance: Metrics.backSwipeMinimumDistance, coordinateSpace: .global)
            .onChanged { value in
                guard !isClosing, screenWidth > 0 else { return }
                if !isBackSwiping {
                    guard !isTransitioning,
                          value.startLocation.x <= Metrics.backSwipeEdgeWidth,
                          value.translation.width > abs(value.translation.height) else { return }
                    isBackSwiping = true
                    isTransitioning = true
                    backSwipeTracking = FeedBackSwipeTracking()
                }
                backSwipeTracking.update(to: value.translation)
                progress = 1 - min(backSwipeTracking.distance / screenWidth, 1)
            }
            .onEnded { value in
                guard isBackSwiping else { return }
                isBackSwiping = false
                guard !isClosing else { return }
                backSwipeTracking.update(to: value.translation)
                // 오른쪽으로 밀수록 진행도는 줄어든다.
                let releaseVelocity = -backSwipeTracking.horizontalVelocity(value.velocity) / screenWidth
                if backSwipeTracking.projectedDistance(to: value.predictedEndTranslation) >= Metrics.backSwipeCloseDistance {
                    close(releaseVelocity: releaseVelocity)
                } else {
                    open(releaseVelocity: releaseVelocity)
                }
            }
    }

    // MARK: - 콘텐츠

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
            VStack(spacing: 0) {
                // 처음에는 펼친 사인이 이 자리까지 걸쳐 있다. 스크롤하면 설명과 함께 탑바 아래로 들어간다.
                Text(viewModel.target.isMine ? "작가님이 공개한 시선들이에요" : "이 작가님의 시선을 감상해 보세요")
                    .font(theme.typography.subheadline)
                    .foregroundStyle(theme.colors.textInactive)
                    .fixedSize(horizontal: false, vertical: true)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.top, Metrics.collapseDistance)
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
                        likingPhotoIDs: viewModel.likingPostIDs,
                        onLike: { photo in
                            Task {
                                if let update = await viewModel.toggleLike(postID: photo.id) { onLikeChange(update) }
                            }
                        },
                        onSelect: { photo, ratio, source in
                            onSelectPost(feedTarget(for: photo, ratio: ratio, transitionSource: source))
                        },
                        zoomSource: FeedZoomSource.authorGrid,
                        showsSignature: false,
                        showsLikeBadge: viewModel.viewState.showsLikes
                    )
                }
            }
            .padding(.horizontal, theme.spacing.screenHorizontal)
            .padding(.bottom, theme.spacing.xxl)
        }
        .onScrollGeometryChange(for: CGFloat.self) { $0.contentOffset.y + $0.contentInsets.top } action: { _, offset in
            scrollOffset = offset
        }
        // 밀려 들어오고 나가는 동안(백 스와이프 포함)에는 그리드가 세로로 움직이지 않게 한다.
        .scrollDisabled(isTransitioning)
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
    // 목록 응답에는 사인이 없어 탑바와 같은 작가 사인을 넣는다. 좋아요가 없는 응답이면 배지를 숨긴 채 0으로 둔다.
    private func gridPhoto(_ post: AuthorPost) -> DisplayPhoto {
        DisplayPhoto(
            id: post.id,
            originalImageSource: post.originalImageSource,
            thumbnailImageSource: post.thumbnailImageSource,
            signatureOriginalImageSource: viewModel.target.signatureImageSource,
            signatureThumbnailImageSource: viewModel.target.signatureImageSource,
            contentDescription: post.contentDescription,
            title: nil,
            likeCount: post.likeCount ?? 0,
            isLiked: post.isLiked ?? false,
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
        // 이 피드에서 같은 작가의 사인을 누르면 목록을 새로 열지 않고, 뒤로 가기처럼 이 카드로 접혀 돌아온다.
        target.sourceAuthorID = viewModel.target.authorID
        return target
    }

    private func handle(_ event: AuthorPostsEvent?) {
        guard let event else { return }
        switch event {
        case .nextPageFailed:
            showToast(AuthorPostsError.generic.message)
        case .likeFailed:
            showToast("좋아요를 반영하지 못했어요")
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
    // 펼친 사인 아래 끝과 설명 사이를 sm만큼 띄운 거리. 이만큼 스크롤하면 사인이 다 접힌다.
    // 탑바 + 이 거리 + 설명 + lg가 전시 화면에서 필터를 뺀 헤더 높이와 같다.
    static let collapseDistance: CGFloat = AuthorPostsTopBar.expandedSignatureBottom - AuthorPostsTopBar.height + ChalkakSpacing.sm
    // 시스템 push와 비슷한 감속 곡선.
    static let screenAnimation: Animation = .smooth(duration: 0.35)
    static let screenReleaseDuration: TimeInterval = 0.3
    static let shadowOpacity: CGFloat = 0.12
    static let shadowRadius: CGFloat = 8
    // 화면이 다 들어왔을 때 아래 피드를 어둡게 하는 정도.
    static let dimOpacity: CGFloat = 0.1
    // 백 스와이프 값은 피드와 같다.
    static let backSwipeEdgeWidth: CGFloat = 24
    static let backSwipeMinimumDistance: CGFloat = 12
    static let backSwipeCloseDistance: CGFloat = 120
    static let emptyIconSize: CGFloat = 34
    static let emptyTopPadding: CGFloat = 96
    static let minimumTouchSize: CGFloat = 44
}

/// 화면을 자기 폭만큼 오른쪽으로 민다. 진행도 0이면 화면 밖, 1이면 제자리다.
/// 첫 화면부터 실제 폭을 쓰므로, 폭을 재기 전에 제자리에 한 번 그려지는 일이 없다.
private struct AuthorPostsSlideEffect: GeometryEffect {
    var progress: CGFloat

    var animatableData: CGFloat {
        get { progress }
        set { progress = newValue }
    }

    func effectValue(size: CGSize) -> ProjectionTransform {
        ProjectionTransform(CGAffineTransform(translationX: (1 - progress) * size.width, y: 0))
    }
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
