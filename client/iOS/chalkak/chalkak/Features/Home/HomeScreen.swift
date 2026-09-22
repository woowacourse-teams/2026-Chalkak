import SwiftUI

struct HomeScreen: View {
    @Environment(\.chalkakTheme) private var theme
    @Bindable var viewModel: HomeViewModel
    var onOpenPhotoUpload: () -> Void = {}
    var onNavigateToBottomBar: (ChalkakBottomBarItem) -> Void = { _ in }

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
                HomeContent(viewModel: viewModel)
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
    @State private var scrollPosition = ScrollPosition()

    private func scrollToTop() {
        Task { @MainActor in
            await Task.yield()
            guard !Task.isCancelled else { return }
            withAnimation(.snappy) {
                scrollPosition.scrollTo(id: HomeScrollTarget.top, anchor: .top)
            }
        }
    }

    var body: some View {
        VStack(spacing: 0) {
            // 고정된 상단 바를 스크롤 뷰 밖에 배치해, 배경이 당겨서 새로고침 인디케이터를 가리지 않게 한다.
            HomeTopBar()
                .padding(.horizontal, theme.spacing.screenHorizontal)
                .background(theme.colors.background.opacity(HomeMetrics.topBarOpacity))
                .homeBottomDivider()
                .allowsHitTesting(false)

            ScrollView {
                VStack(spacing: 0) {
                    HomeTopic(
                        topicDate: viewModel.viewState.topicDate,
                        topic: viewModel.viewState.topic
                    )
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, theme.spacing.screenHorizontal)
                    .homeBottomDivider()
                    .id(HomeScrollTarget.top)

                    if viewModel.viewState.photos.isEmpty {
                        HomeEmptyContent()
                            .frame(maxWidth: .infinity)
                            .padding(.top, HomeMetrics.emptyTopPadding)
                    } else {
                        HomePhotoList(
                            photos: viewModel.viewState.photos,
                            likedPhotoIDs: viewModel.viewState.likedPhotoIDs,
                            areLikesEnabled: viewModel.viewState.areLikesEnabled,
                            imageReloadGeneration: viewModel.imageReloadGeneration,
                            onLike: { photoID in
                                Task { await viewModel.toggleLike(photoID: photoID) }
                            },
                            onEndThreshold: { isReached in
                                Task { await viewModel.didReachEndThreshold(isReached) }
                            }
                        )
                    }
                }
                .scrollTargetLayout()
            }
            // 이미지 비율이나 페이지 추가로 셀 높이가 바뀌어도 현재 스크롤 기준점을 유지하고,
            // 최상단 이동도 같은 ScrollPosition을 사용해 API 간 충돌을 피한다.
            .scrollPosition($scrollPosition, anchor: .top)
            .onChange(of: viewModel.scrollToTopRequestID) { _, _ in
                scrollToTop()
            }
            .refreshable {
                // SwiftUI가 새로고침 컨트롤 작업을 취소하더라도, 사용자가 시작한
                // 네트워크 갱신은 별도 작업으로 끝까지 완료되도록 한다.
                let refreshTask = Task { await viewModel.refresh() }
                await refreshTask.value
            }
            .background(theme.colors.background)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background(theme.colors.background)
    }
}

private enum HomeScrollTarget {
    static let top = "home-scroll-top"
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
