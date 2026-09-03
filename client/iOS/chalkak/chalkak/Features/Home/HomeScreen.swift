import SwiftUI

struct HomeScreen: View {
    @Environment(\.chalkakTheme) private var theme
    @Bindable var viewModel: HomeViewModel
    var onOpenPhotoUpload: () -> Void = {}
    var onNavigateToBottomBar: (ChalkakBottomBarItem) -> Void = { _ in }
    var onSelectPhoto: (FeedTarget) -> Void = { _ in }

    @State private var message: String?
    @State private var messageDismissTask: Task<Void, Never>?

    var body: some View {
        Group {
            switch viewModel.viewState.contentStatus {
            case .loading:
                HomeInitialStatus(
                    status: viewModel.viewState.contentStatus,
                    onRetry: { Task { await viewModel.retry() } }
                )
            case .error:
                HomeInitialStatus(
                    status: viewModel.viewState.contentStatus,
                    onRetry: { Task { await viewModel.retry() } }
                )
            case .content:
                HomeContent(viewModel: viewModel, onSelectPhoto: onSelectPhoto)
            }
        }
        .background(theme.colors.background)
        .safeAreaInset(edge: .bottom, spacing: 0) {
            ChalkakBottomBar(
                selectedItem: .today,
                onSelect: { item in
                    Task { await viewModel.selectBottomBarItem(item) }
                },
                onAdd: { viewModel.openPhotoUpload() }
            )
        }
        .overlay(alignment: .bottom) {
            if let message {
                Text(message)
                    .font(theme.typography.subheadline)
                    .foregroundStyle(theme.colors.onActionPrimary)
                    .padding(.horizontal, theme.spacing.lg)
                    .padding(.vertical, theme.spacing.md)
                    .background(theme.colors.actionPrimary, in: Capsule())
                    .padding(.bottom, HomeMetrics.messageBottomPadding)
                    .transition(.move(edge: .bottom).combined(with: .opacity))
                    .accessibilityLabel(message)
            }
        }
        .onChange(of: viewModel.event) { _, event in
            handle(event)
        }
        .onDisappear {
            messageDismissTask?.cancel()
        }
    }

    private func handle(_ event: HomeEvent?) {
        guard let event else { return }

        switch event {
        case .openPhotoUpload:
            onOpenPhotoUpload()
        case .showGuestLikeMessage:
            showMessage("로그인 후 좋아요를 누를 수 있어요")
        case let .showRefreshFailure(reason):
            showMessage(reason.message)
        case let .navigateToBottomBar(item):
            onNavigateToBottomBar(item)
        }
        viewModel.consumeEvent()
    }

    private func showMessage(_ text: String) {
        messageDismissTask?.cancel()
        withAnimation(.snappy) {
            message = text
        }
        messageDismissTask = Task {
            try? await Task.sleep(for: .seconds(2.5))
            guard !Task.isCancelled else { return }
            withAnimation(.snappy) {
                message = nil
            }
        }
    }
}

private struct HomeInitialStatus: View {
    @Environment(\.chalkakTheme) private var theme
    let status: HomeContentStatus
    let onRetry: () -> Void

    var body: some View {
        VStack(spacing: 0) {
            HomeTopBar()
                .padding(.horizontal, theme.spacing.screenHorizontal)
                .homeBottomDivider()

            Spacer(minLength: 0)

            switch status {
            case .loading:
                ProgressView()
                    .tint(theme.colors.actionPrimary)
                    .accessibilityIdentifier("home-loading")
            case let .error(reason):
                VStack(spacing: theme.spacing.xl) {
                    Text(reason.message)
                        .font(theme.typography.body)
                        .foregroundStyle(theme.colors.textSecondary)
                        .multilineTextAlignment(.center)

                    Button(action: onRetry) {
                        Image(systemName: "arrow.clockwise")
                            .font(.system(size: Metrics.retryIconSize, weight: .semibold))
                            .foregroundStyle(theme.colors.onActionPrimary)
                            .frame(width: Metrics.iconButtonSize, height: Metrics.iconButtonSize)
                            .background(theme.colors.actionPrimary, in: Circle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("홈 새로고침")
                }
                .padding(.horizontal, theme.spacing.screenHorizontal)
                .accessibilityIdentifier("home-initial-error")
            case .content:
                EmptyView()
            }

            Spacer(minLength: 0)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(theme.colors.background)
    }
}

private struct HomeContent: View {
    @Environment(\.chalkakTheme) private var theme
    @Bindable var viewModel: HomeViewModel
    var onSelectPhoto: (FeedTarget) -> Void = { _ in }
    @State private var showsScrollToTop = false
    @State private var accumulatedScroll: CGFloat = 0

    private func feedTarget(for photo: HomePhoto) -> FeedTarget {
        let state = viewModel.viewState
        let dateLabel = state.topicDate.map(FeedDateLabel.make(from:)) ?? ""
        return FeedTarget(
            seed: FeedContent(
                dateLabel: dateLabel,
                topic: state.topic,
                post: FeedPost(
                    id: photo.id,
                    originalImageSource: photo.imageSource,
                    signatureImageSource: photo.signatureSource,
                    contentDescription: photo.contentDescription,
                    title: photo.title,
                    likeCount: photo.likeCount,
                    isLiked: state.likedPhotoIDs.contains(photo.id)
                )
            ),
            // 홈은 실제 좋아요 값을 알고 있어 즉시 좋아요를 허용한다.
            isLikeConfirmed: true
        )
    }

    // 아래로 스크롤하면 버튼이 나타나고 위로 스크롤하면 사라진다.
    // 절대 위치가 아닌 스크롤 방향으로 판단해 safe area inset 영향에서 자유롭다.
    private func updateScrollToTop(from oldDistance: CGFloat, to newDistance: CGFloat) {
        // 아래로 스크롤할수록 delta가 커지도록 부호를 맞춘다.
        let delta = oldDistance - newDistance // 양수면 아래로, 음수면 위로 스크롤한 것이다.
        guard delta != 0 else { return }

        // 스크롤 방향이 바뀌면 누적값을 초기화한다.
        if (delta > 0) != (accumulatedScroll > 0) {
            accumulatedScroll = 0
        }
        accumulatedScroll += delta

        if accumulatedScroll >= HomeMetrics.scrollToTopToggleThreshold {
            setShowsScrollToTop(true)
        } else if accumulatedScroll <= -HomeMetrics.scrollToTopToggleThreshold {
            setShowsScrollToTop(false)
        }
    }

    private func setShowsScrollToTop(_ value: Bool) {
        guard showsScrollToTop != value else { return }
        withAnimation(.snappy) {
            showsScrollToTop = value
        }
    }

    var body: some View {
        ScrollViewReader { proxy in
            ScrollView {
                Color.clear
                    .frame(height: 0)
                    .id(HomeMetrics.scrollTopID)

                HomeTopic(
                    topicDate: viewModel.viewState.topicDate,
                    topic: viewModel.viewState.topic
                )
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, theme.spacing.screenHorizontal)
                .padding(.top, HomeTopBarMetrics.height)
                .homeBottomDivider()

                if viewModel.viewState.photos.isEmpty {
                    HomeEmptyContent()
                        .frame(maxWidth: .infinity)
                        .padding(.top, HomeMetrics.emptyTopPadding)
                } else {
                    HomePhotoList(
                        photos: viewModel.viewState.photos,
                        likedPhotoIDs: viewModel.viewState.likedPhotoIDs,
                        isLoadingNext: viewModel.viewState.isLoadingNext,
                        areLikesEnabled: viewModel.viewState.areLikesEnabled,
                        onLike: { photoID in
                            Task { await viewModel.toggleLike(photoID: photoID) }
                        },
                        onEndThreshold: { isReached in
                            Task { await viewModel.didReachEndThreshold(isReached) }
                        },
                        onSelect: { photo in onSelectPhoto(feedTarget(for: photo)) }
                    )
                }
            }
            .overlay(alignment: .top) {
                HomeTopBar()
                    .padding(.horizontal, theme.spacing.screenHorizontal)
                    .background(theme.colors.background.opacity(HomeMetrics.topBarOpacity))
                    .homeBottomDivider()
            }
            .overlay(alignment: .bottomTrailing) {
                if showsScrollToTop {
                    Button {
                        withAnimation(.snappy) {
                            proxy.scrollTo(HomeMetrics.scrollTopID, anchor: .top)
                        }
                    } label: {
                        Image(systemName: "arrow.up")
                            .font(.system(size: HomeMetrics.scrollButtonIconSize, weight: .semibold))
                            .foregroundStyle(theme.colors.iconPrimary)
                            .frame(
                                width: HomeMetrics.scrollButtonSize,
                                height: HomeMetrics.scrollButtonSize
                            )
                            .glassEffect(.regular.interactive(), in: .circle)
                    }
                    .buttonStyle(.plain)
                    .padding(.trailing, theme.spacing.xl)
                    .padding(.bottom, theme.spacing.lg)
                    .accessibilityLabel("맨 위로 이동")
                    .transition(.scale.combined(with: .opacity))
                }
            }
            .onScrollGeometryChange(for: CGFloat.self) { geometry in
                // 콘텐츠 최상단을 0으로 맞춰, safe area inset과 무관한 스크롤 거리를 얻는다.
                geometry.contentOffset.y + geometry.contentInsets.top
            } action: { oldDistance, newDistance in
                updateScrollToTop(from: oldDistance, to: newDistance)
            }
            .refreshable {
                await viewModel.refresh()
            }
            .safeAreaInset(edge: .top, spacing: 0) {
                Color.clear.frame(height: HomeTopBarMetrics.height)
            }
            .background(theme.colors.background)
        }
    }
}

private struct HomeEmptyContent: View {
    @Environment(\.chalkakTheme) private var theme

    var body: some View {
        VStack(spacing: theme.spacing.sm) {
            Image(systemName: "photo.on.rectangle.angled")
                .font(.system(size: HomeMetrics.emptyIconSize))
                .foregroundStyle(theme.colors.iconSecondary)
                .accessibilityHidden(true)

            Text("아직 올라온 사진이 없어요")
                .font(theme.typography.body)
                .foregroundStyle(theme.colors.textSecondary)

            Text("첫 번째 사진을 올려보세요")
                .font(theme.typography.subheadline)
                .foregroundStyle(theme.colors.textMuted)
        }
        .accessibilityElement(children: .combine)
        .accessibilityIdentifier("home-empty")
    }
}

private enum Metrics {
    static let retryIconSize: CGFloat = 20
    static let iconButtonSize: CGFloat = 48
}

private enum HomeMetrics {
    static let topBarOpacity = 0.96
    static let scrollTopID = "home-scroll-top"
    // 같은 방향으로 이만큼 누적 스크롤하면 버튼을 토글한다.
    static let scrollToTopToggleThreshold: CGFloat = 12
    static let scrollButtonSize: CGFloat = 48
    static let scrollButtonIconSize: CGFloat = 20
    static let emptyTopPadding: CGFloat = 144
    static let emptyIconSize: CGFloat = 34
    static let messageBottomPadding: CGFloat = 88
}

#Preview("Home") {
    HomeScreen(
        viewModel: HomeViewModel(initialState: HomePreviewData.contentState)
    )
    .chalkakTheme(.light)
}

#Preview("Home Loading") {
    HomeScreen(
        viewModel: HomeViewModel(initialState: HomePreviewData.loadingState)
    )
    .chalkakTheme(.light)
}

#Preview("Home Error") {
    HomeScreen(
        viewModel: HomeViewModel(initialState: HomePreviewData.errorState)
    )
    .chalkakTheme(.light)
}
