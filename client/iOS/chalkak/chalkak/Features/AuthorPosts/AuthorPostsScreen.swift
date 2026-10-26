import SwiftUI

/// 한 작가가 공개한 사진을 전시와 같은 2열 그리드로 모아 본다.
/// 탑바의 뒤로 가기는 고정이고, 작가 사인은 처음에 크게 보였다가 스크롤하면 탑바 가운데로 줄어든다.
/// 피드에서 누른 사인의 위치(`AuthorPostsTarget.signatureTransitionSource`)를 받으면 피드의 사진 전환과 같은 구조로,
/// 화면 하나가 그 사인 자리에서 펼쳐진다. 닫을 때(뒤로 가기·백 스와이프)는 접히기 시작하자마자 화면이 투명해지고
/// 흰색으로 바뀐 사인만 피드 사인 자리로 접혀 돌아간다.
/// 탑바 사인이 피드 사인 자리에 겹치도록 화면 전체를 축소·이동한다(`FeedScreenReturnEffect`).
/// 사진을 누르면 `onSelectPost`로 피드를 연다.
struct AuthorPostsScreen: View {
    @Environment(\.chalkakTheme) private var theme
    @Environment(\.chalkakShowToast) private var showToast
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.feedZoomRegistry) private var zoomRegistry
    var viewModel: AuthorPostsViewModel
    // 닫는 전환이 끝난 뒤 호출된다. 호출부는 애니메이션 없이 화면을 치우면 된다.
    var onBack: () -> Void = {}
    var onSelectPost: (FeedTarget) -> Void = { _ in }

    // 화면 펼침 진행도(0: 피드 사인 자리로 접힘, 1: 전체 화면).
    @State private var progress: CGFloat = 0
    // 펼칠 출발 위치가 없을 때(동작 줄이기 등)만 쓰는 페이드 진행도.
    @State private var contentProgress: CGFloat = 0
    @State private var dragOffset: CGFloat = 0
    // 펼치고 접는 기준인 탑바 사인 위치. 전환 중에는 변환된 좌표가 들어오지 않게 고정한다.
    @State private var signatureFrame: CGRect?
    @State private var transitionViewport: CGRect?
    @State private var transitionSafeAreaInsets = EdgeInsets()
    @State private var isTransitioning = true
    @State private var hasPreparedOpening = false
    @State private var hasStartedOpening = false
    @State private var hasLaunchedOpening = false
    @State private var isBackSwiping = false
    @State private var backSwipeTracking = FeedBackSwipeTracking()
    @State private var isClosing = false
    @State private var scrollOffset: CGFloat = 0

    var body: some View {
        VStack(spacing: 0) {
            // 탑바 자리. 탑바는 펼친 사인이 아래 콘텐츠 위로 넘쳐 그려지도록 overlay로 올린다.
            Color.clear.frame(height: AuthorPostsTopBar.height)
            content
                .opacity(surfaceOpacity)
        }
        .overlay(alignment: .top) {
            AuthorPostsTopBar(
                signatureImageSource: viewModel.viewState.showsAuthorIdentity
                    ? viewModel.target.signatureImageSource
                    : nil,
                isMine: viewModel.target.isMine,
                collapseProgress: collapseProgress,
                signatureColor: theme.colors.textOnImage.mix(with: theme.colors.iconPrimary, by: surfaceOpacity),
                chromeOpacity: surfaceOpacity,
                onBack: { close() },
                onSignatureFrameChange: { frame in
                    if !isTransitioning || !hasStartedOpening { signatureFrame = frame }
                    startOpening()
                }
            )
            .background(theme.colors.background.opacity(surfaceOpacity))
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(theme.colors.background.opacity(surfaceOpacity))
        .opacity(usesScreenTransition ? 1 : contentProgress)
        .onGeometryChange(for: CGRect.self) { $0.frame(in: .global) } action: {
            if !isTransitioning || transitionViewport == nil {
                transitionViewport = $0
                startOpening()
            }
        }
        .onGeometryChange(for: EdgeInsets.self) { $0.safeAreaInsets } action: {
            if !hasStartedOpening || !isTransitioning { transitionSafeAreaInsets = $0 }
        }
        .modifier(FeedScreenReturnEffect(
            progress: usesScreenTransition ? progress : 1,
            dragOffset: dragOffset,
            source: usesScreenTransition && hasStartedOpening ? transitionSource : nil,
            photo: signatureFrame,
            canvas: transitionViewport,
            safeAreaInsets: transitionSafeAreaInsets,
            // 배경이 상태 바·홈 인디케이터 영역까지 그려지므로, 그 영역도 펼치는 동안 함께 덮는다.
            clipsRoundedCornersToSafeArea: false
        ))
        // 측정 전 전체 화면이 순간 노출되지 않도록 레이아웃은 유지하고 표시만 기다린다.
        .opacity(usesScreenTransition && !hasStartedOpening ? 0 : 1)
        .animation(nil, value: hasStartedOpening)
        .background {
            // 접힌 시작 상태를 한 화면 갱신 동안 보여 준 뒤 펼친다. 피드 사진 전환과 같은 순서다.
            FeedOpeningFrameGate(isEnabled: hasPreparedOpening && !hasStartedOpening) {
                guard !hasStartedOpening, !isClosing else { return }
                hasStartedOpening = true
            }
            .allowsHitTesting(false)
            .accessibilityHidden(true)
        }
        .background {
            FeedOpeningFrameGate(isEnabled: hasStartedOpening && !hasLaunchedOpening) {
                guard !hasLaunchedOpening, !isClosing else { return }
                hasLaunchedOpening = true
                open()
            }
            .allowsHitTesting(false)
            .accessibilityHidden(true)
        }
        .simultaneousGesture(backSwipeGesture)
        .allowsHitTesting(!isClosing)
        // 접힌 사인이 보이는 순간부터 피드의 사인을 숨겨, 반투명한 동안 사인이 두 개로 겹쳐 보이지 않게 한다.
        .onChange(of: hasStartedOpening) { _, hasStarted in
            if hasStarted, usesScreenTransition { zoomRegistry?.hidesFeedSignature = true }
        }
        .onDisappear {
            zoomRegistry?.hidesFeedSignature = false
        }
        .task {
            // 탑바 사인 위치를 받지 못하면(사인이 그려지지 않는 경우) 펼침 없이 페이드로 연다.
            try? await Task.sleep(for: Metrics.signatureFrameTimeout)
            guard !hasPreparedOpening, !isClosing else { return }
            hasPreparedOpening = true
            hasStartedOpening = true
        }
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

    // MARK: - 펼침 전환

    // 펼칠 출발 위치. 피드 사인 칸은 직각이라 모서리 반지름이 없다.
    private var transitionSource: FeedZoomRegistry.Source? {
        guard !reduceMotion, let frame = viewModel.target.signatureTransitionSource else { return nil }
        return FeedZoomRegistry.Source(frame: frame, cornerRadius: 0)
    }

    // 펼치는 동안 배경·콘텐츠의 불투명도. 접힌 상태(0)에서는 투명해서 피드 사진 위에 흰 사인만 보이고,
    // 펼쳐지기 시작하자마자(진행도 `openFadeEnd`까지) 화면 배경과 아이콘 색 사인으로 바뀐다.
    // 닫을 때는 접히기 시작하자마자(진행도 `closeFadeStart` ~ `closeFadeEnd`) 투명해지고, 흰 사인만 피드 위에서 접힌다.
    // 예) 열 때 진행도 0 → 0, 0.175 → 0.5, 0.35 이상 → 1 / 닫을 때 1 → 1, 0.55 → 0.5, 0.1 이하 → 0
    private var surfaceOpacity: CGFloat {
        guard usesScreenTransition else { return 1 }
        let (transparentAt, opaqueAt) = isClosing
            ? (Metrics.closeFadeEnd, Metrics.closeFadeStart)
            : (0, Metrics.openFadeEnd)
        return min(max((progress - transparentAt) / (opaqueAt - transparentAt), 0), 1)
    }

    // 사인 위치를 끝내 받지 못한 대체 경로에서는 펼치지 않고 페이드한다.
    private var usesScreenTransition: Bool {
        transitionSource != nil && signatureFrame != nil
    }

    private func startOpening() {
        guard !hasPreparedOpening, !isClosing else { return }
        if transitionSource != nil {
            guard signatureFrame != nil, transitionViewport != nil else { return }
        }
        hasPreparedOpening = true
        if !usesScreenTransition { hasStartedOpening = true }
    }

    // releaseVelocity: 백 스와이프를 놓은 순간의 진행도 속도(초당). 없으면 멈춘 상태에서 출발한다.
    private func open(releaseVelocity: CGFloat? = nil) {
        isTransitioning = true
        withAnimation(screenAnimation(to: 1, releaseVelocity: releaseVelocity)) {
            progress = 1
            dragOffset = 0
        } completion: {
            guard !isClosing else { return }
            isTransitioning = false
            zoomRegistry?.hidesFeedSignature = false
        }
        withAnimation(Metrics.contentAnimation) {
            contentProgress = 1
        }
    }

    private func close(releaseVelocity: CGFloat? = nil) {
        guard !isClosing else { return }
        isClosing = true
        isTransitioning = true
        guard usesScreenTransition else {
            withAnimation(Metrics.contentAnimation) {
                contentProgress = 0
            } completion: {
                onBack()
            }
            return
        }
        // 흰 사인이 돌아가는 동안 제자리의 피드 사인을 숨겨 두 개로 보이지 않게 한다.
        zoomRegistry?.hidesFeedSignature = true
        withAnimation(screenAnimation(to: 0, releaseVelocity: releaseVelocity)) {
            progress = 0
            dragOffset = 0
        } completion: {
            zoomRegistry?.hidesFeedSignature = false
            onBack()
        }
    }

    // 버튼으로 열고 닫을 때는 Android와 같은 곡선을 쓴다.
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

    // 화면 왼쪽 가장자리에서 시작한 스와이프로 닫는다. 끄는 동안 화면이 살짝 따라오고, 놓으면 사인만 피드 사인 자리로 접힌다.
    private var backSwipeGesture: some Gesture {
        DragGesture(minimumDistance: Metrics.backSwipeMinimumDistance)
            .onChanged { value in
                guard !isClosing else { return }
                if !isBackSwiping {
                    guard !isTransitioning,
                          value.startLocation.x <= Metrics.backSwipeEdgeWidth,
                          value.translation.width > abs(value.translation.height) else { return }
                    isBackSwiping = true
                    isTransitioning = true
                    backSwipeTracking = FeedBackSwipeTracking()
                }
                backSwipeTracking.update(to: value.translation)
                if usesScreenTransition {
                    // 길게 끌어도 창 크기를 유지하고, 손을 놓은 뒤 사인 자리로 접는다.
                    progress = 1
                    dragOffset = backSwipeTracking.distance * Metrics.backSwipeDragRatio
                } else {
                    contentProgress = 1 - min(backSwipeTracking.distance / Metrics.backSwipeFullDistance, 1)
                }
            }
            .onEnded { value in
                guard isBackSwiping else { return }
                isBackSwiping = false
                guard !isClosing else { return }
                backSwipeTracking.update(to: value.translation)
                // 거리가 늘면 진행도는 줄어든다. 끝까지 끈 뒤에는 진행도가 0에 멈춰 있어 속도도 없다.
                let releaseVelocity = backSwipeTracking.distance < Metrics.backSwipeFullDistance
                    ? -backSwipeTracking.horizontalVelocity(value.velocity) / Metrics.backSwipeFullDistance
                    : 0
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
        .onScrollGeometryChange(for: CGFloat.self) { $0.contentOffset.y + $0.contentInsets.top } action: { _, offset in
            scrollOffset = offset
        }
        // 펼치고 접는 동안 스크롤로 사인 크기가 바뀌면 접힐 자리가 어긋난다.
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
        // 이 피드에서 같은 작가의 사인을 누르면 목록을 새로 열지 않고, 뒤로 가기처럼 이 카드로 접혀 돌아온다.
        target.sourceAuthorID = viewModel.target.authorID
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
    // 펼친 사인 아래 끝과 설명 사이를 sm만큼 띄운 거리. 이만큼 스크롤하면 사인이 다 접힌다.
    // 탑바 + 이 거리 + 설명 + lg가 전시 화면에서 필터를 뺀 헤더 높이와 같다.
    static let collapseDistance: CGFloat = AuthorPostsTopBar.expandedSignatureBottom - AuthorPostsTopBar.height + ChalkakSpacing.sm
    // 피드 사진 전환과 같은 값. Android PhotoTransition의 boundsTransform: tween(300, FastOutSlowInEasing).
    static let screenAnimation: Animation = .timingCurve(0.4, 0, 0.2, 1, duration: 0.3)
    static let screenReleaseDuration: TimeInterval = 0.3
    // 펼칠 때 배경이 다 불투명해지는 진행도.
    static let openFadeEnd: CGFloat = 0.35
    // 닫을 때 배경이 투명해지기 시작·끝나는 진행도.
    static let closeFadeStart: CGFloat = 1
    static let closeFadeEnd: CGFloat = 0.1
    // 펼칠 수 없을 때의 대체 페이드. Android NavHost 기본 화면 전환: fadeIn/fadeOut(tween(700)).
    static let contentAnimation: Animation = .timingCurve(0.4, 0, 0.2, 1, duration: 0.7)
    static let signatureFrameTimeout: Duration = .milliseconds(150)
    // 백 스와이프 값도 피드와 같다.
    static let backSwipeEdgeWidth: CGFloat = 24
    static let backSwipeMinimumDistance: CGFloat = 12
    static let backSwipeFullDistance: CGFloat = 300
    static let backSwipeCloseDistance: CGFloat = 120
    static let backSwipeDragRatio: CGFloat = 0.65
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
